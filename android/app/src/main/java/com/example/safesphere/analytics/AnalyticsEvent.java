package com.example.safesphere.analytics;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Room entity representing a queued analytics event.
 * Survives app kill, process death, and device reboot.
 */
@Entity(tableName = "analytics_events")
public class AnalyticsEvent {

    @PrimaryKey
    @NonNull
    public String eventId = "";       // UUID v4, client-generated — idempotency key

    public String userId;             // from Prefs.getUserId()
    public String sessionId;          // per-emergency session UUID
    public String eventType;          // e.g. "trigger_source"
    public int schemaVersion = 1;
    public long clientTsMs;           // System.currentTimeMillis()
    public String payloadJson;        // Gson-serialized Map<String,Object>
    public String appVersion;
    public long createdAtMs;

    // Sync tracking
    public boolean synced = false;
    public int retryCount = 0;
    public long nextRetryMs = 0;      // epoch ms — 0 means "ready now"
    public boolean sentToServer = false;
}
