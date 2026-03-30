package com.example.safesphere.sync;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.safesphere.Prefs;
import com.example.safesphere.analytics.AnalyticsDao;
import com.example.safesphere.analytics.AnalyticsDatabase;
import com.example.safesphere.analytics.AnalyticsEvent;
import com.example.safesphere.revocation.RevocationHandler;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import com.example.safesphere.network.NetworkConfig;

/**
 * WorkManager Worker — syncs queued analytics events to the backend.
 * Runs every 15 minutes when network is available.
 * Handles: batching, idempotency, retry with exponential backoff, revocation detection.
 */
public class AnalyticsSyncWorker extends Worker {

    private static final String TAG = "AnalyticsSyncWorker";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final int BATCH_SIZE = 100;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(NetworkConfig.CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
            .readTimeout(NetworkConfig.READ_TIMEOUT_SEC, TimeUnit.SECONDS)
            .writeTimeout(NetworkConfig.WRITE_TIMEOUT_SEC, TimeUnit.SECONDS)
            .build();

    public AnalyticsSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        String userId = Prefs.getUserId(ctx);

        if (userId == null || userId.isEmpty()) {
            Log.d(TAG, "No user ID — skipping sync");
            return Result.success();
        }

        AnalyticsDao dao = AnalyticsDatabase.getInstance(ctx).analyticsDao();
        long now = System.currentTimeMillis();
        List<AnalyticsEvent> pending = dao.getPendingEvents(now);

        if (pending.isEmpty()) {
            Log.d(TAG, "No pending events");
            return Result.success();
        }

        Log.d(TAG, "Syncing " + pending.size() + " events");

        try {
            // Build batch JSON
            JSONObject body = new JSONObject();
            body.put("user_id", userId);
            body.put("batch_id", java.util.UUID.randomUUID().toString());
            body.put("app_version", getAppVersion(ctx));
            body.put("android_version", String.valueOf(android.os.Build.VERSION.SDK_INT));

            JSONArray eventsArr = new JSONArray();
            List<String> sentIds = new ArrayList<>();
            for (int i = 0; i < Math.min(pending.size(), BATCH_SIZE); i++) {
                AnalyticsEvent ev = pending.get(i);
                JSONObject evObj = new JSONObject();
                evObj.put("event_id", ev.eventId);
                evObj.put("session_id", ev.sessionId != null ? ev.sessionId : JSONObject.NULL);
                evObj.put("event_type", ev.eventType);
                evObj.put("schema_version", ev.schemaVersion);
                evObj.put("client_ts", new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",
                        java.util.Locale.US).format(new java.util.Date(ev.clientTsMs)));
                // Include payload as JSON object
                try {
                    evObj.put("payload", new JSONObject(ev.payloadJson != null ? ev.payloadJson : "{}"));
                } catch (Exception e) {
                    evObj.put("payload", new JSONObject());
                }
                eventsArr.put(evObj);
                sentIds.add(ev.eventId);
            }
            body.put("events", eventsArr);

            String url = NetworkConfig.BASE_URL + "analytics/ingest";
            Request request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(body.toString(), JSON))
                    .build();

            try (Response response = client.newCall(request).execute()) {
                int code = response.code();

                if (code == 200 && response.body() != null) {
                    String responseBody = response.body().string();
                    JSONObject json = new JSONObject(responseBody);

                    // Mark accepted + duplicates as synced
                    List<String> accepted = new ArrayList<>();
                    if (json.has("accepted")) {
                        JSONArray arr = json.getJSONArray("accepted");
                        for (int i = 0; i < arr.length(); i++) accepted.add(arr.getString(i));
                    }
                    if (json.has("duplicates")) {
                        JSONArray arr = json.getJSONArray("duplicates");
                        for (int i = 0; i < arr.length(); i++) accepted.add(arr.getString(i));
                    }
                    if (!accepted.isEmpty()) dao.markSynced(accepted);

                    // Check revocation piggybacked on response
                    if (json.has("revocation_check")) {
                        JSONObject revCheck = json.getJSONObject("revocation_check");
                        boolean isRevoked = revCheck.optBoolean("is_revoked", false);
                        if (isRevoked) {
                            String msg = revCheck.optString("message", null);
                            int version = revCheck.optInt("revocation_version", 0);
                            RevocationHandler.handleRevocation(ctx, version, msg);
                            return Result.success();
                        }
                    }

                    // Check pending admin messages
                    if (json.has("pending_messages")) {
                        JSONArray msgs = json.getJSONArray("pending_messages");
                        for (int i = 0; i < msgs.length(); i++) {
                            JSONObject msg = msgs.getJSONObject(i);
                            Prefs.addPendingAdminMessage(ctx,
                                    msg.optString("subject"),
                                    msg.optString("body"),
                                    msg.optBoolean("is_critical", false));
                        }
                    }

                    Log.d(TAG, "Synced " + accepted.size() + " events");
                    return Result.success();

                } else if (code == 403) {
                    // User revoked
                    if (response.body() != null) {
                        try {
                            JSONObject json = new JSONObject(response.body().string());
                            if (json.has("revocation_check")) {
                                JSONObject r = json.getJSONObject("revocation_check");
                                RevocationHandler.handleRevocation(ctx,
                                        r.optInt("revocation_version", 1),
                                        r.optString("message", null));
                            }
                        } catch (Exception ignored) {}
                    }
                    return Result.success();

                } else if (code == 429) {
                    // Rate limited — exponential backoff
                    scheduleRetry(dao, sentIds, pending);
                    return Result.retry();

                } else {
                    // Server error — retry with backoff
                    scheduleRetry(dao, sentIds, pending);
                    return Result.retry();
                }
            }

        } catch (IOException e) {
            Log.e(TAG, "Network error: " + e.getMessage());
            return Result.retry();
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error: " + e.getMessage());
            return Result.retry();
        }
    }

    private void scheduleRetry(AnalyticsDao dao, List<String> ids, List<AnalyticsEvent> events) {
        // Find max retry count in batch to calculate uniform backoff
        int maxRetry = 0;
        for (AnalyticsEvent ev : events) {
            if (ids.contains(ev.eventId)) maxRetry = Math.max(maxRetry, ev.retryCount);
        }
        long backoffMs = Math.min(
                (long) (60_000 * Math.pow(2, maxRetry)),
                15 * 60 * 1000L
        );
        dao.markRetry(ids, System.currentTimeMillis() + backoffMs);
    }

    private String getAppVersion(Context ctx) {
        try {
            return ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "unknown";
        }
    }
}
