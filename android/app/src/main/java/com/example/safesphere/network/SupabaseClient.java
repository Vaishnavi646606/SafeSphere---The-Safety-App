package com.example.safesphere.network;

import android.content.Context;
import android.util.Log;
import com.example.safesphere.BuildConfig;
import com.google.gson.JsonObject;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Supabase REST API client using OkHttp.
 * 
 * ⚠️ IMPORTANT: This is NON-BLOCKING and thread-safe.
 * All network calls are made on a background thread via ExecutorService in callers.
 * 
 * Usage:
 *   SupabaseClient client = SupabaseClient.getInstance();
 *   client.insertEmergencyEvent(eventData, callback);
 */
public class SupabaseClient {
    private static final String TAG = "SupabaseClient";
    private static final long LIVE_LINK_TTL_MS = 24L * 60L * 60L * 1000L;
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final int CONNECT_TIMEOUT_SEC = 15;
    private static final int READ_TIMEOUT_SEC = 30;
    private static final int WRITE_TIMEOUT_SEC = 30;
    
    private static SupabaseClient instance;
    private final OkHttpClient httpClient;
    private final String supabaseUrl;
    private final String supabaseAnonKey;
    
    private SupabaseClient(Context context) {
        Context appContext = context != null ? context.getApplicationContext() : null;
        this.supabaseUrl = BuildConfig.SUPABASE_URL;
        this.supabaseAnonKey = BuildConfig.SUPABASE_ANON_KEY;

        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
                .writeTimeout(WRITE_TIMEOUT_SEC, TimeUnit.SECONDS)
                .build();
    }
    
    public static SupabaseClient getInstance() {
        return getInstance(null);
    }

    public static SupabaseClient getInstance(Context context) {
        if (instance == null) {
            synchronized (SupabaseClient.class) {
                if (instance == null) {
                    instance = new SupabaseClient(context);
                }
            }
        }
        return instance;
    }

    /**
     * Inserts one row in any Supabase table using PostgREST.
     */
    public SupabaseResponse insertRow(String table, JSONObject data) {
        return insertRow(table, data, "return=minimal");
    }

    public SupabaseResponse insertRowReturningRepresentation(String table, JSONObject data) {
        return insertRow(table, data, "return=representation");
    }

    private SupabaseResponse insertRow(String table, JSONObject data, String preferHeader) {
        try {
            String url = supabaseUrl + "/rest/v1/" + table;
            RequestBody body = RequestBody.create(data.toString(), JSON);
            Request request = baseRequestBuilder(url)
                    .header("Prefer", preferHeader)
                    .post(body)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                return new SupabaseResponse(response.code(), response.isSuccessful(),
                        response.body() != null ? response.body().string() : "");
            }
        } catch (Exception e) {
            Log.e(TAG, "insertRow failed for table=" + table, e);
            return new SupabaseResponse(0, false, e.getMessage());
        }
    }

    /**
     * Updates row(s) in any Supabase table using one equality filter.
     */
    public SupabaseResponse updateRow(String table, String filterColumn, String filterValue, JSONObject data) {
        return updateRow(table, filterColumn, filterValue, data, "return=minimal");
    }

    public SupabaseResponse updateRowReturningRepresentation(String table, String filterColumn, String filterValue, JSONObject data) {
        return updateRow(table, filterColumn, filterValue, data, "return=representation");
    }

    private SupabaseResponse updateRow(String table, String filterColumn, String filterValue, JSONObject data, String preferHeader) {
        try {
            String encodedValue = URLEncoder.encode(filterValue, StandardCharsets.UTF_8.name());
            String url = supabaseUrl + "/rest/v1/" + table + "?" + filterColumn + "=eq." + encodedValue;
            RequestBody body = RequestBody.create(data.toString(), JSON);
            Request request = baseRequestBuilder(url)
                    .header("Prefer", preferHeader)
                    .patch(body)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                return new SupabaseResponse(response.code(), response.isSuccessful(),
                        response.body() != null ? response.body().string() : "");
            }
        } catch (Exception e) {
            Log.e(TAG, "updateRow failed for table=" + table + " filter=" + filterColumn, e);
            return new SupabaseResponse(0, false, e.getMessage());
        }
    }

    private SupabaseResponse getRows(String table, String query) {
        try {
            String url = supabaseUrl + "/rest/v1/" + table + (query == null || query.isEmpty() ? "" : ("?" + query));
            Request request = baseRequestBuilder(url)
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                return new SupabaseResponse(response.code(), response.isSuccessful(),
                        response.body() != null ? response.body().string() : "");
            }
        } catch (Exception e) {
            Log.e(TAG, "getRows failed for table=" + table, e);
            return new SupabaseResponse(0, false, e.getMessage());
        }
    }

    public List<PendingMessageData> fetchPendingMessages(String userId, int limit) {
        List<PendingMessageData> out = new ArrayList<>();
        if (userId == null || userId.trim().isEmpty()) {
            return out;
        }

        try {
            String encodedUserId = URLEncoder.encode(userId.trim(), StandardCharsets.UTF_8.name());
            String query = "select=id,message_id,created_at"
                    + "&user_id=eq." + encodedUserId
                    + "&status=eq.pending"
                    + "&order=created_at.asc"
                    + "&limit=" + Math.max(1, limit);

            SupabaseResponse response = getRows("pending_messages", query);
            if (!response.success) {
                Log.w(TAG, "fetchPendingMessages failed: " + response.message);
                return out;
            }

            JSONArray rows = new JSONArray(response.message);
            for (int i = 0; i < rows.length(); i++) {
                JSONObject item = rows.optJSONObject(i);
                if (item == null) continue;

                PendingMessageData pm = new PendingMessageData();
                pm.id = item.optString("id", null);
                pm.messageId = item.optString("message_id", null);
                pm.createdAt = item.optString("created_at", null);

                if (pm.id != null && pm.messageId != null) {
                    out.add(pm);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "fetchPendingMessages parse error", e);
        }

        return out;
    }

    public AdminMessageData fetchAdminMessageById(String messageId) {
        if (messageId == null || messageId.trim().isEmpty()) {
            return null;
        }

        try {
            String encodedId = URLEncoder.encode(messageId.trim(), StandardCharsets.UTF_8.name());
            String query = "select=id,subject,body,is_critical,created_at&id=eq." + encodedId + "&limit=1";
            SupabaseResponse response = getRows("admin_messages", query);
            if (!response.success) {
                Log.w(TAG, "fetchAdminMessageById failed: " + response.message);
                return null;
            }

            JSONArray rows = new JSONArray(response.message);
            if (rows.length() == 0) {
                return null;
            }

            JSONObject item = rows.optJSONObject(0);
            if (item == null) {
                return null;
            }

            AdminMessageData message = new AdminMessageData();
            message.id = item.optString("id", null);
            message.subject = item.optString("subject", "Admin Notice");
            message.body = item.optString("body", "");
            message.isCritical = item.optBoolean("is_critical", false);
            message.createdAt = item.optString("created_at", null);
            return message;
        } catch (Exception e) {
            Log.e(TAG, "fetchAdminMessageById parse error", e);
            return null;
        }
    }

    public SupabaseResponse markPendingMessageDelivered(String pendingMessageId) {
        JSONObject patch = new JSONObject();
        try {
            patch.put("status", "delivered");
            patch.put("delivered_at", nowIsoUtc());
        } catch (Exception e) {
            return new SupabaseResponse(0, false, e.getMessage());
        }
        return updateRow("pending_messages", "id", pendingMessageId, patch);
    }

    public String getActiveUserIdByPhoneHash(String phoneHash) {
        JSONObject user = getUserByPhone(phoneHash);
        return user == null ? null : user.optString("id", null);
    }

    public JSONObject getUserByPhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            Log.w(TAG, "getUserByPhone: phone is null or empty");
            return null;
        }

        try {
            String trimmedPhone = phone.trim();
            // Build the query WITHOUT double-encoding (PostgREST expects the filter value as-is)
            String query = "select=id,display_name"
                    + "&phone_hash=eq." + trimmedPhone
                    + "&is_active=eq.true"
                    + "&limit=1";
            
            String fullUrl = supabaseUrl + "/rest/v1/users?" + query;
            Log.d(TAG, "getUserByPhone: Requesting URL: " + fullUrl);
            
            SupabaseResponse response = getRows("users", query);
            Log.d(TAG, "getUserByPhone: Response success=" + response.success);
            
            if (!response.success) {
                Log.w(TAG, "getUserByPhone failed. Message: " + response.message);
                return null;
            }

            if (response.message == null || response.message.isEmpty()) {
                Log.w(TAG, "getUserByPhone: Response body is empty");
                return null;
            }

            Log.d(TAG, "getUserByPhone: Response body: " + response.message);
            
            JSONArray rows;
            try {
                rows = new JSONArray(response.message);
            } catch (Exception parseErr) {
                Log.e(TAG, "getUserByPhone: Failed to parse response as JSONArray", parseErr);
                Log.e(TAG, "getUserByPhone: Response was: " + response.message);
                return null;
            }

            if (rows.length() == 0) {
                Log.d(TAG, "getUserByPhone: No matching user found for phone: " + trimmedPhone);
                return null;
            }

            JSONObject item = rows.optJSONObject(0);
            if (item == null) {
                Log.w(TAG, "getUserByPhone: First array element is null or not a JSONObject");
                return null;
            }
            
            String userId = item.optString("id", null);
            if (userId == null || userId.trim().isEmpty()) {
                Log.w(TAG, "getUserByPhone: User found but id is missing");
                return null;
            }
            
            Log.d(TAG, "getUserByPhone: Found user with id=" + userId + " for phone=" + trimmedPhone);
            return item;
        } catch (Exception e) {
            Log.e(TAG, "getUserByPhone exception", e);
            return null;
        }
    }

    public JSONObject getUserProfileByPhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) return null;
        try {
            String query = "select=id,display_name,keyword,"
                    + "emergency_contact_1,emergency_contact_2,emergency_contact_3"
                    + "&phone_hash=eq." + phone.trim()
                    + "&is_active=eq.true"
                    + "&limit=1";
            SupabaseResponse response = getRows("users", query);
            if (!response.success) return null;
            if (response.message == null || response.message.isEmpty()) return null;
            JSONArray rows = new JSONArray(response.message);
            if (rows.length() == 0) return null;
            return rows.optJSONObject(0);
        } catch (Exception e) {
            Log.e(TAG, "getUserProfileByPhone exception", e);
            return null;
        }
    }

    public void incrementTotalEmergencies(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return;
        }

        try {
            // Count all emergency_events for this user (more reliable than increment)
            String encodedUserId = URLEncoder.encode(userId.trim(), StandardCharsets.UTF_8.name());
            String query = "select=id&user_id=eq." + encodedUserId;
            SupabaseResponse response = getRows("emergency_events", query);
            
            if (!response.success) {
                Log.w(TAG, "incrementTotalEmergencies: Failed to count events: " + response.message);
                return;
            }

            int eventCount = 0;
            try {
                JSONArray rows = new JSONArray(response.message);
                eventCount = rows.length();
                Log.d(TAG, "incrementTotalEmergencies: Found " + eventCount + " emergency events for user " + userId);
            } catch (Exception e) {
                Log.e(TAG, "incrementTotalEmergencies: Failed to parse event count", e);
                return;
            }

            // Update users.total_emergencies with actual count
            JSONObject patch = new JSONObject();
            patch.put("total_emergencies", eventCount);
            patch.put("updated_at", nowIsoUtc());

            SupabaseResponse update = updateRow("users", "id", userId, patch);
            if (!update.success) {
                Log.w(TAG, "incrementTotalEmergencies: Failed to update count: " + update.message);
            } else {
                Log.d(TAG, "incrementTotalEmergencies: Updated total_emergencies to " + eventCount + " for user " + userId);
            }
        } catch (Exception e) {
            Log.e(TAG, "incrementTotalEmergencies: Exception", e);
        }
    }

    /**
     * Upsert live location session for this user.
     * One permanent row per user in live_location_sessions table.
     * Logic:
     *   - Check if row exists for this userId
     *   - If YES  → UPDATE lat, lng, accuracy, last_updated
     *   - If NO   → INSERT new row with token, display_name, started_at, expires_at, is_active=true
     *
     * Table columns verified:
     *   id, token, user_id, lat, lng, accuracy, last_updated,
     *   started_at, expires_at, is_active, display_name
     *
     * Called from SafeSphereService background location refresh thread.
     * Never call from main thread.
     *
     * @param userId      Supabase users.id (UUID)
     * @param displayName users.display_name shown on tracking page
     * @param lat         current latitude
     * @param lng         current longitude
     * @param accuracy    location accuracy in metres (0 if unknown)
     * @param token       permanent tracking token from Prefs.getLiveLocationToken()
     */
    public SupabaseResponse upsertLiveLocation(String userId, String displayName,
            double lat, double lng, float accuracy, String token) {
        if (userId == null || userId.trim().isEmpty()) {
            Log.w(TAG, "upsertLiveLocation: userId is null or empty — skipping");
            return new SupabaseResponse(0, false, "userId required");
        }
        if (token == null || token.trim().isEmpty()) {
            Log.w(TAG, "upsertLiveLocation: token is null or empty — skipping");
            return new SupabaseResponse(0, false, "token required");
        }

        try {
            // Step 1 — Check if a row already exists for this user
            String encodedUserId = URLEncoder.encode(userId.trim(), StandardCharsets.UTF_8.name());
            String checkQuery = "select=id&user_id=eq." + encodedUserId + "&limit=1";
            SupabaseResponse checkResponse = getRows("live_location_sessions", checkQuery);

            boolean rowExists = false;
            if (checkResponse.success && checkResponse.message != null
                    && !checkResponse.message.isEmpty()) {
                try {
                    JSONArray rows = new JSONArray(checkResponse.message);
                    rowExists = rows.length() > 0;
                } catch (Exception parseErr) {
                    Log.w(TAG, "upsertLiveLocation: could not parse check response", parseErr);
                }
            }

            long nowMs = System.currentTimeMillis();
            String nowIso = toIso8601(nowMs);
            String expiresIso = toIso8601(nowMs + LIVE_LINK_TTL_MS);

            if (rowExists) {
                // Step 2a — Row exists → UPDATE latest coords only.
                // Expiry must be extended only on emergency trigger flow.
                Log.d(TAG, "upsertLiveLocation: updating existing row for userId=" + userId);
                JSONObject patch = new JSONObject();
                patch.put("lat", lat);
                patch.put("lng", lng);
                patch.put("accuracy", accuracy);
                patch.put("last_updated", nowIso);
                patch.put("is_active", true);

                SupabaseResponse updateResponse = updateRow(
                        "live_location_sessions", "user_id", userId.trim(), patch);

                if (updateResponse.success) {
                    Log.d(TAG, "upsertLiveLocation: updated successfully");
                } else {
                    Log.w(TAG, "upsertLiveLocation: update failed — " + updateResponse.message);
                }
                return updateResponse;

            } else {
                // Step 2b — No row → INSERT new permanent row
                Log.d(TAG, "upsertLiveLocation: inserting new row for userId=" + userId);

                // expires_at = 1 day from now for newly created session.

                JSONObject insertData = new JSONObject();
                insertData.put("token", token.trim());
                insertData.put("user_id", userId.trim());
                insertData.put("display_name",
                        displayName != null && !displayName.trim().isEmpty()
                        ? displayName.trim() : "SafeSphere User");
                insertData.put("lat", lat);
                insertData.put("lng", lng);
                insertData.put("accuracy", accuracy);
                insertData.put("last_updated", nowIso);
                insertData.put("started_at", nowIso);
                insertData.put("expires_at", expiresIso);
                insertData.put("is_active", true);

                SupabaseResponse insertResponse = insertRow(
                        "live_location_sessions", insertData, "return=minimal");

                if (insertResponse.success) {
                    Log.d(TAG, "upsertLiveLocation: inserted successfully with token=" + token);
                } else {
                    Log.w(TAG, "upsertLiveLocation: insert failed — " + insertResponse.message);
                }
                return insertResponse;
            }

        } catch (Exception e) {
            Log.e(TAG, "upsertLiveLocation: exception", e);
            return new SupabaseResponse(0, false, e.getMessage());
        }
    }

    /**
     * Fetch the live location token for this user from Supabase users table.
     * Called once on startup to populate Prefs.setLiveLocationToken().
     * Returns null if no token found or user not found.
     * Never call from main thread.
     *
     * @param userId Supabase users.id (UUID)
     * @return token string or null
     */
    public String fetchLiveLocationToken(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            Log.w(TAG, "fetchLiveLocationToken: userId is null — skipping");
            return null;
        }
        try {
            String encodedUserId = URLEncoder.encode(userId.trim(), StandardCharsets.UTF_8.name());
            String query = "select=live_location_token&id=eq." + encodedUserId + "&limit=1";
            SupabaseResponse response = getRows("users", query);

            if (!response.success || response.message == null || response.message.isEmpty()) {
                Log.w(TAG, "fetchLiveLocationToken: query failed — " + response.message);
                return null;
            }

            JSONArray rows = new JSONArray(response.message);
            if (rows.length() == 0) {
                Log.w(TAG, "fetchLiveLocationToken: no user found for userId=" + userId);
                return null;
            }

            JSONObject row = rows.optJSONObject(0);
            if (row == null) return null;

            String token = row.optString("live_location_token", null);
            if (token == null || token.trim().isEmpty()) {
                Log.w(TAG, "fetchLiveLocationToken: token is null in DB for userId=" + userId);
                return null;
            }

            Log.d(TAG, "fetchLiveLocationToken: fetched token for userId=" + userId);
            return token.trim();

        } catch (Exception e) {
            Log.e(TAG, "fetchLiveLocationToken: exception", e);
            return null;
        }
    }

    /**
     * Extend live location session expiry by 1 day for a specific user.
     * This should be called only from emergency-trigger flow.
     */
    public SupabaseResponse extendLiveLocationExpiry(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return new SupabaseResponse(0, false, "userId required");
        }

        try {
            long nowMs = System.currentTimeMillis();
            JSONObject patch = new JSONObject();
            patch.put("expires_at", toIso8601(nowMs + LIVE_LINK_TTL_MS));
            patch.put("is_active", true);

            return updateRow("live_location_sessions", "user_id", userId.trim(), patch);
        } catch (Exception e) {
            Log.e(TAG, "extendLiveLocationExpiry: exception", e);
            return new SupabaseResponse(0, false, e.getMessage());
        }
    }

    public void updateUserLocation(String userId, double lat, double lng) {
        new Thread(() -> {
            try {
                org.json.JSONObject patch = new org.json.JSONObject();
                patch.put("last_known_lat", lat);
                patch.put("last_known_lng", lng);
                patch.put("last_location_updated_at",
                        toIso8601(System.currentTimeMillis()));
                patch.put("updated_at",
                        toIso8601(System.currentTimeMillis()));
                SupabaseResponse response =
                        updateRow("users", "id", userId, patch);
                if (response.success) {
                    Log.d(TAG, "updateUserLocation: synced to Supabase");
                } else {
                    Log.w(TAG, "updateUserLocation: failed - " + response.message);
                }
            } catch (Exception e) {
                Log.w(TAG, "updateUserLocation exception", e);
            }
        }, "location-sync").start();
    }

    private String nowIsoUtc() {
        return toIso8601(System.currentTimeMillis());
    }

    public static String toIso8601(long millis) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date(millis));
    }

    private Request.Builder baseRequestBuilder(String url) {
        return new Request.Builder()
                .url(url)
                .header("apikey", supabaseAnonKey)
                .header("Authorization", "Bearer " + supabaseAnonKey)
                .header("Content-Type", "application/json");
    }
    
    /**
     * Insert emergency event into Supabase.
     * Thread-safe: call from background thread only.
     */
    public SupabaseResponse insertEmergencyEvent(EmergencyEventData data) {
        JSONObject json = new JSONObject();
        try {
            json.put("user_id", data.userId);
            json.put("trigger_type", data.triggerType);
            json.put("triggered_at", data.triggeredAt);
            json.put("session_id", data.sessionId);
            json.put("location_lat", data.locationLat);
            json.put("location_lng", data.locationLng);
            json.put("status", "triggered");
            json.put("has_location_enabled", data.hasLocationEnabled);
        } catch (Exception e) {
            Log.e(TAG, "Error building emergency event payload", e);
            return new SupabaseResponse(0, false, e.getMessage());
        }
        return insertRow("emergency_events", json);
    }
    
    /**
     * Update emergency event status.
     * Thread-safe: call from background thread only.
     */
    public SupabaseResponse updateEmergencyEvent(String eventId, String status) {
        JSONObject json = new JSONObject();
        try {
            json.put("status", status);
        } catch (Exception e) {
            Log.e(TAG, "Error building emergency event status payload", e);
            return new SupabaseResponse(0, false, e.getMessage());
        }
        return updateRow("emergency_events", "id", eventId, json);
    }
    
    /**
     * Submit emergency feedback.
     * Thread-safe: call from background thread only.
     */
    public SupabaseResponse submitEmergencyFeedback(EmergencyFeedbackData data) {
        JSONObject json = new JSONObject();
        try {
            json.put("event_id", data.eventId);
            json.put("user_id", data.userId);
            json.put("was_real_emergency", data.wasRealEmergency);
            json.put("was_rescued_or_helped", data.wasRescuedOrHelped);
            json.put("rating", data.rating);
            json.put("feedback_text", data.feedbackText);
            json.put("submitted_at", nowIsoUtc());
        } catch (Exception e) {
            Log.e(TAG, "Error building emergency feedback payload", e);
            return new SupabaseResponse(0, false, e.getMessage());
        }
        return insertRow("emergency_feedback", json);
    }
    
    /**
     * Log analytics event.
     * Thread-safe: call from background thread only.
     */
    public SupabaseResponse logAnalyticsEvent(AnalyticsEventData data) {
        JSONObject json = new JSONObject();
        try {
            json.put("user_id", data.userId);
            json.put("event_type", data.eventType);
            json.put("event_name", data.eventName);
            json.put("session_id", data.sessionId);
            json.put("payload", data.payloadJson);
            json.put("client_timestamp", nowIsoUtc());
        } catch (Exception e) {
            Log.e(TAG, "Error building analytics payload", e);
            return new SupabaseResponse(0, false, e.getMessage());
        }
        return insertRow("analytics_events", json);
    }

    /**
     * Update emergency event with call results and resolved status.
     * Thread-safe: call from background thread only.
     */
    public SupabaseResponse updateEmergencyEventResults(String eventId, JSONObject updates) {
        if (eventId == null || eventId.trim().isEmpty()) {
            Log.w(TAG, "updateEmergencyEventResults: eventId is null or empty");
            return new SupabaseResponse(0, false, "eventId is required");
        }
        if (updates == null) {
            Log.w(TAG, "updateEmergencyEventResults: updates is null");
            return new SupabaseResponse(0, false, "updates is required");
        }

        try {
            // Add timestamp if not already present
            if (!updates.has("updated_at")) {
                updates.put("updated_at", nowIsoUtc());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error building emergency event results payload", e);
        }

        Log.d(TAG, "updateEmergencyEventResults: Updating event " + eventId + " with: " + updates.toString());
        SupabaseResponse response = updateRowReturningRepresentation("emergency_events", "id", eventId, updates);
        if (!response.success) {
            Log.w(TAG, "updateEmergencyEventResults failed: " + response.message);
        } else {
            try {
                JSONArray rows = new JSONArray(response.message == null ? "[]" : response.message);
                if (rows.length() == 0) {
                    Log.w(TAG, "updateEmergencyEventResults: Supabase returned 0 rows for event " + eventId);
                    return new SupabaseResponse(response.statusCode, false,
                            "PATCH matched 0 rows for event_id=" + eventId);
                }
                Log.d(TAG, "updateEmergencyEventResults: Successfully updated event " + eventId
                        + " (rows=" + rows.length() + ")");
            } catch (Exception parseErr) {
                Log.w(TAG, "updateEmergencyEventResults: Could not parse PATCH response body", parseErr);
                return new SupabaseResponse(response.statusCode, false,
                        "Could not parse PATCH response: " + parseErr.getMessage()
                                + " body=" + response.message);
            }
        }
        return response;
    }
    
    // ============ DATA CLASSES ============
    
    public static class SupabaseResponse {
        public int statusCode;
        public boolean success;
        public String message;
        
        public SupabaseResponse(int code, boolean success, String message) {
            this.statusCode = code;
            this.success = success;
            this.message = message;
        }
    }
    
    public static class EmergencyEventData {
        public String userId;
        public String triggerType;
        public String triggeredAt;
        public String sessionId;
        public Double locationLat;
        public Double locationLng;
        public Integer batteryPercent;
        public Boolean hasLocationEnabled;
    }
    
    public static class EmergencyFeedbackData {
        public String eventId;
        public String userId;
        public Boolean wasRealEmergency;
        public Boolean wasRescuedOrHelped;
        public Integer rating;
        public String feedbackText;
    }
    
    public static class AnalyticsEventData {
        public String userId;
        public String eventType;
        public String eventName;
        public String sessionId;
        public String payloadJson;
    }

    public static class PendingMessageData {
        public String id;
        public String messageId;
        public String createdAt;
    }

    public static class AdminMessageData {
        public String id;
        public String subject;
        public String body;
        public boolean isCritical;
        public String createdAt;
    }
}
