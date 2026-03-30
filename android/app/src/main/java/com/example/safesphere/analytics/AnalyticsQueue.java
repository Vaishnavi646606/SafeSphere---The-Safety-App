package com.example.safesphere.analytics;

import android.content.Context;
import com.google.gson.Gson;
import com.example.safesphere.Prefs;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import com.example.safesphere.sync.AnalyticsSyncWorker;

/**
 * Public facade for enqueueing analytics events.
 * Thread-safe: safe to call from any thread including foreground services.
 * NEVER blocks the calling thread — all DB writes are async.
 */
public class AnalyticsQueue {

    private static volatile AnalyticsQueue instance;
    private final AnalyticsDao dao;
    private final Context appContext;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Gson GSON = new Gson();

    /** Max events before LRU eviction of non-critical events. */
    private static final int MAX_QUEUE_SIZE = 10_000;
    private static final int EVICT_COUNT = 500;
    /** Trigger eager sync once this many events are pending. */
    private static final int EAGER_SYNC_THRESHOLD = 20;

    private AnalyticsQueue(Context context) {
        this.appContext = context.getApplicationContext();
        this.dao = AnalyticsDatabase.getInstance(appContext).analyticsDao();
    }

    public static AnalyticsQueue get(Context context) {
        if (instance == null) {
            synchronized (AnalyticsQueue.class) {
                if (instance == null) {
                    instance = new AnalyticsQueue(context);
                }
            }
        }
        return instance;
    }

    /**
     * Enqueue an analytics event. Async — returns immediately.
     *
     * @param eventType  One of EventType.* constants
     * @param sessionId  Current session UUID (may be null for non-session events)
     * @param payload    Optional key-value payload (null-safe)
     */
    public void enqueue(String eventType, String sessionId, Map<String, Object> payload) {
        executor.execute(() -> {
            // Backpressure: evict oldest non-critical events if queue is too large
            int pending = dao.countPending();
            if (pending >= MAX_QUEUE_SIZE) {
                dao.evictOldestNonCritical(EVICT_COUNT);
            }

            AnalyticsEvent event = new AnalyticsEvent();
            event.eventId = UUID.randomUUID().toString();
            event.userId = Prefs.getUserId(appContext);
            event.sessionId = sessionId;
            event.eventType = eventType;
            event.clientTsMs = System.currentTimeMillis();
            event.createdAtMs = event.clientTsMs;
            event.payloadJson = payload != null ? GSON.toJson(payload) : "{}";
            event.appVersion = getAppVersion();
            dao.insertEvent(event);

            // Housekeeping: delete synced events older than 7 days
            long sevenDaysAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000);
            dao.deleteSyncedOlderThan(sevenDaysAgo);

            // Eager sync if threshold met
            if (dao.countPending() >= EAGER_SYNC_THRESHOLD) {
                triggerEagerSync();
            }
        });
    }

    /** Convenience overload with no payload. */
    public void enqueue(String eventType, String sessionId) {
        enqueue(eventType, sessionId, null);
    }

    /** Convenience overload for non-session events. */
    public void enqueue(String eventType) {
        enqueue(eventType, null, null);
    }

    private void triggerEagerSync() {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(AnalyticsSyncWorker.class)
                .setConstraints(constraints)
                .addTag("eager_sync")
                .build();
        WorkManager.getInstance(appContext).enqueue(req);
    }

    private String getAppVersion() {
        try {
            return appContext.getPackageManager()
                    .getPackageInfo(appContext.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "unknown";
        }
    }
}
