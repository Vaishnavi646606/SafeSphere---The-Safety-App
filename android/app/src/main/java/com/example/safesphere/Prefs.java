package com.example.safesphere;

import android.content.Context;
import android.content.SharedPreferences;

public class Prefs {

    private static final String PREF_NAME = "safesphere_prefs";
    private static final String KEY_SUPABASE_USER_ID = "supabase_user_id";
    private static final String KEY_LAST_EMERGENCY_EVENT_ID = "last_emergency_event_id";
    private static final String KEY_LIVE_LOCATION_TOKEN = "live_location_token";

    public static void saveUser(Context ctx, String name, String phone,
                                String keyword, String e1, String e2, String e3) {
        SharedPreferences sp = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        sp.edit()
                .putString("name", name)
                .putString("phone", phone)
                .putString("keyword", keyword.toLowerCase().trim())
                .putString("e1", e1)
                .putString("e2", e2)
                .putString("e3", e3)
                .putBoolean("logged_in", true)  // Mark user as logged in
                .apply();
    }

    public static void setLoggedIn(Context ctx, boolean loggedIn) {
        SharedPreferences sp = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        sp.edit().putBoolean("logged_in", loggedIn).apply();
    }

    public static boolean isLoggedIn(Context ctx) {
        return ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getBoolean("logged_in", false);
    }

    public static void logout(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        sp.edit().putBoolean("logged_in", false).apply();
    }

    // Debug method to clear all data (useful for testing)
    public static void clearAllData(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        sp.edit().clear().apply();
    }

    // Debug method to log all SharedPreferences
    public static void logAllPrefs(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        android.util.Log.e("PREFS_DEBUG", "=== ALL SHARED PREFERENCES ===");
        android.util.Log.e("PREFS_DEBUG", "Name: '" + sp.getString("name", "") + "'");
        android.util.Log.e("PREFS_DEBUG", "Phone: '" + sp.getString("phone", "") + "'");
        android.util.Log.e("PREFS_DEBUG", "Keyword: '" + sp.getString("keyword", "") + "'");
        android.util.Log.e("PREFS_DEBUG", "E1: '" + sp.getString("e1", "") + "'");
        android.util.Log.e("PREFS_DEBUG", "E2: '" + sp.getString("e2", "") + "'");
        android.util.Log.e("PREFS_DEBUG", "E3: '" + sp.getString("e3", "") + "'");
        android.util.Log.e("PREFS_DEBUG", "Logged In: " + sp.getBoolean("logged_in", false));
        android.util.Log.e("PREFS_DEBUG", "Supabase User ID: '" + sp.getString(KEY_SUPABASE_USER_ID, "") + "'");
        android.util.Log.e("PREFS_DEBUG", "Last Emergency Event ID: '" + sp.getString(KEY_LAST_EMERGENCY_EVENT_ID, "") + "'");
        android.util.Log.e("PREFS_DEBUG", "Live Location Token: '" + sp.getString(KEY_LIVE_LOCATION_TOKEN, "") + "'");
        android.util.Log.e("PREFS_DEBUG", "==============================");
    }

    public static SharedPreferences getAll(Context ctx) {
        return ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    private static SharedPreferences getPrefs(Context ctx) {
        return getAll(ctx);
    }

    public static String getKeyword(Context ctx) {

        return getAll(ctx).getString("keyword", "");
    }

    public static void setKeyword(Context ctx, String keyword) {
        getAll(ctx).edit().putString("keyword", keyword == null ? "" : keyword.toLowerCase().trim()).apply();
    }

    public static String getUserName(Context ctx) {
        return getAll(ctx).getString("name", "");
    }

    public static void setUserName(Context ctx, String name) {
        getAll(ctx).edit().putString("name", name == null ? "" : name.trim()).apply();
    }


    public static String getUserPhone(Context ctx) {
        return getAll(ctx).getString("phone", "");
    }

    public static void setUserPhone(Context ctx, String phone) {
        getAll(ctx).edit().putString("phone", phone == null ? "" : phone.trim()).apply();
    }

    public static void setEmergency1(Context ctx, String e1) {
        getAll(ctx).edit().putString("e1", e1 == null ? "" : e1.trim()).apply();
    }

    public static void setEmergency2(Context ctx, String e2) {
        getAll(ctx).edit().putString("e2", e2 == null ? "" : e2.trim()).apply();
    }

    public static void setEmergency3(Context ctx, String e3) {
        getAll(ctx).edit().putString("e3", e3 == null ? "" : e3.trim()).apply();
    }

    public static String[] getEmergencyNumbers(Context ctx) {
        SharedPreferences sp = getAll(ctx);
        return new String[]{
                sp.getString("e1", ""),
                sp.getString("e2", ""),
                sp.getString("e3", "")
        };
    }

    // ================================================================
    //  CALL SEQUENCE STATE  (survives process death)
    // ================================================================

    /** Save the current call sequence index so PhoneStateReceiver can resume after process restart */
    public static void setCallSequenceIndex(Context ctx, int index) {
        getAll(ctx).edit().putInt("call_seq_index", index).apply();
    }

    public static int getCallSequenceIndex(Context ctx) {
        return getAll(ctx).getInt("call_seq_index", -1);
    }

    /** -1 = no active emergency sequence */
    public static void clearCallSequence(Context ctx) {
        getAll(ctx).edit()
                .putInt("call_seq_index", -1)
                .putLong("call_seq_start_ms", -1)
                .apply();
    }

    /** Timestamp when the current call was initiated — used to detect stale state */
    public static void setCallStartTime(Context ctx, long ms) {
        getAll(ctx).edit().putLong("call_seq_start_ms", ms).apply();
    }

    public static long getCallStartTime(Context ctx) {
        return getAll(ctx).getLong("call_seq_start_ms", -1);
    }

    public static void setLastKnownLocation(Context ctx, double latitude, double longitude, long timestampMs) {
        getAll(ctx).edit()
                .putString("last_known_location_lat", Double.toString(latitude))
                .putString("last_known_location_lng", Double.toString(longitude))
                .putLong("last_known_location_time_ms", timestampMs)
                .apply();
    }

    public static double getLastKnownLocationLat(Context ctx) {
        try {
            return Double.parseDouble(getAll(ctx).getString("last_known_location_lat", "NaN"));
        } catch (Exception ignored) {
            return Double.NaN;
        }
    }

    public static double getLastKnownLocationLng(Context ctx) {
        try {
            return Double.parseDouble(getAll(ctx).getString("last_known_location_lng", "NaN"));
        } catch (Exception ignored) {
            return Double.NaN;
        }
    }

    public static long getLastKnownLocationTime(Context ctx) {
        return getAll(ctx).getLong("last_known_location_time_ms", -1L);
    }

    // ================================================================
    //  LIVE LOCATION TOKEN  (permanent per-user tracking token)
    // ================================================================

    /**
     * Store the permanent live location token for this user.
     * Token is generated once and never changes — stored in Supabase
     * users.live_location_token and locally here for offline SMS.
     * Tracking URL: https://safesphere-safety.vercel.app/track/[token]
     */
    public static void setLiveLocationToken(Context ctx, String token) {
        if (token == null || token.trim().isEmpty()) {
            getAll(ctx).edit().remove(KEY_LIVE_LOCATION_TOKEN).apply();
            return;
        }
        getAll(ctx).edit().putString(KEY_LIVE_LOCATION_TOKEN, token.trim()).apply();
    }

    /**
     * Get the permanent live location token for this user.
     * Returns null if not yet fetched from Supabase.
     * Caller must generate + upsert if null.
     */
    public static String getLiveLocationToken(Context ctx) {
        return getAll(ctx).getString(KEY_LIVE_LOCATION_TOKEN, null);
    }

    /**
     * Clear the live location token from local storage.
     * Called on logout only — never during normal operation.
     */
    public static void clearLiveLocationToken(Context ctx) {
        getAll(ctx).edit().remove(KEY_LIVE_LOCATION_TOKEN).apply();
    }

    // ================================================================
    //  PROTECTION TOGGLE  (user-controlled background monitoring)
    // ================================================================

    /** Returns true only when the user has explicitly enabled background protection. */
    public static boolean isProtectionEnabled(Context ctx) {
        return getAll(ctx).getBoolean("protection_enabled", false);
    }

    public static void setProtectionEnabled(Context ctx, boolean enabled) {
        getAll(ctx).edit().putBoolean("protection_enabled", enabled).apply();
    }

    // ================================================================
    //  ONE-TIME PROMPT FLAGS
    // ================================================================

    public static boolean hasRequestedRuntimePermissionsOnce(Context ctx) {
        return getAll(ctx).getBoolean("runtime_permissions_requested_once", false);
    }

    public static void setRequestedRuntimePermissionsOnce(Context ctx, boolean requested) {
        getAll(ctx).edit().putBoolean("runtime_permissions_requested_once", requested).apply();
    }

    public static boolean hasShownBatteryOptimizationPromptOnce(Context ctx) {
        return getAll(ctx).getBoolean("battery_opt_prompt_shown_once", false);
    }

    public static void setShownBatteryOptimizationPromptOnce(Context ctx, boolean shown) {
        getAll(ctx).edit().putBoolean("battery_opt_prompt_shown_once", shown).apply();
    }

    public static boolean hasCompletedPermissionSetup(Context ctx) {
        return getAll(ctx).getBoolean("permission_setup_completed", false);
    }

    public static void setCompletedPermissionSetup(Context ctx, boolean completed) {
        getAll(ctx).edit().putBoolean("permission_setup_completed", completed).apply();
    }

    // ================================================================
    //  ANALYTICS — USER IDENTITY (added for analytics platform)
    // ================================================================

    public static String getSupabaseUserId(Context ctx) {
        SharedPreferences sp = getAll(ctx);
        String id = sp.getString(KEY_SUPABASE_USER_ID, null);
        if (id != null && !id.trim().isEmpty()) {
            return id;
        }
        // Migration fallback for older app versions.
        String legacy = sp.getString("analytics_user_id", null);
        if (legacy != null && !legacy.trim().isEmpty()) {
            setSupabaseUserId(ctx, legacy);
            return legacy;
        }
        return null;
    }

    public static void setSupabaseUserId(Context ctx, String id) {
        if (id == null || id.trim().isEmpty()) {
            getAll(ctx).edit().remove(KEY_SUPABASE_USER_ID).apply();
            return;
        }
        getAll(ctx).edit().putString(KEY_SUPABASE_USER_ID, id.trim()).apply();
    }

    public static String getUserId(Context ctx) {
        return getSupabaseUserId(ctx);
    }

    public static void setUserId(Context ctx, String userId) {
        setSupabaseUserId(ctx, userId);
    }

    // ================================================================
    //  REVOCATION STATE
    // ================================================================

    public static int getRevocationVersion(Context ctx) {
        return getAll(ctx).getInt("revocation_version", 0);
    }

    public static void setRevocationVersion(Context ctx, int version) {
        getAll(ctx).edit().putInt("revocation_version", version).apply();
    }

    public static boolean isPendingRevocation(Context ctx) {
        return getAll(ctx).getBoolean("pending_revocation", false);
    }

    public static void setPendingRevocation(Context ctx, boolean pending) {
        getAll(ctx).edit().putBoolean("pending_revocation", pending).apply();
    }

    public static String getRevocationMessage(Context ctx) {
        return getAll(ctx).getString("revocation_message", null);
    }

    public static void setRevocationMessage(Context ctx, String message) {
        getAll(ctx).edit().putString("revocation_message", message).apply();
    }

    // ================================================================
    //  SESSION ID (per-emergency session)
    // ================================================================

    public static String ensureSessionId(Context ctx) {
        SharedPreferences sp = getAll(ctx);
        String sid = sp.getString("current_session_id", null);
        if (sid == null) {
            sid = java.util.UUID.randomUUID().toString();
            sp.edit().putString("current_session_id", sid).apply();
        }
        return sid;
    }

    public static String getSessionId(Context ctx) {
        return getAll(ctx).getString("current_session_id", null);
    }

    public static void setCurrentEmergencySessionId(Context ctx, String id) {
        if (id == null || id.trim().isEmpty()) {
            getAll(ctx).edit().remove("current_emergency_session_id").apply();
        } else {
            getAll(ctx).edit().putString("current_emergency_session_id", id).apply();
        }
    }

    public static String getCurrentEmergencySessionId(Context ctx) {
        return getAll(ctx).getString("current_emergency_session_id", null);
    }

    public static void setLastEmergencyEventId(Context ctx, String eventId) {
        if (eventId == null || eventId.trim().isEmpty()) {
            getAll(ctx).edit().remove(KEY_LAST_EMERGENCY_EVENT_ID).apply();
            return;
        }
        getAll(ctx).edit().putString(KEY_LAST_EMERGENCY_EVENT_ID, eventId.trim()).apply();
    }

    public static String getLastEmergencyEventId(Context ctx) {
        return getAll(ctx).getString(KEY_LAST_EMERGENCY_EVENT_ID, null);
    }

    public static void endSession(Context ctx) {
        getAll(ctx).edit().remove("current_session_id").apply();
    }

    // ================================================================
    //  ADMIN MESSAGES (pending display queue — stored as simple JSON list)
    // ================================================================

    public static void addPendingAdminMessage(Context ctx, String subject, String body, boolean isCritical) {
        try {
            org.json.JSONArray arr = getPendingAdminMessages(ctx);
            org.json.JSONObject msg = new org.json.JSONObject();
            msg.put("subject", subject);
            msg.put("body", body);
            msg.put("isCritical", isCritical);
            msg.put("ts", System.currentTimeMillis());
            arr.put(msg);
            getAll(ctx).edit().putString("pending_admin_messages", arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    public static org.json.JSONArray getPendingAdminMessages(Context ctx) {
        try {
            String raw = getAll(ctx).getString("pending_admin_messages", "[]");
            return new org.json.JSONArray(raw);
        } catch (Exception e) {
            return new org.json.JSONArray();
        }
    }

    public static void clearPendingAdminMessages(Context ctx) {
        getAll(ctx).edit().remove("pending_admin_messages").apply();
    }

    public static boolean needsProfileSetup(Context ctx) {
        return getAll(ctx).getBoolean("needs_profile_setup", false);
    }

    public static void setNeedsProfileSetup(Context ctx, boolean needed) {
        getAll(ctx).edit().putBoolean("needs_profile_setup", needed).apply();
    }

    // ── Offline profile sync queue ──────────────────────────
    public static void setProfileSyncPending(Context ctx, boolean pending) {
        getPrefs(ctx).edit().putBoolean("profile_sync_pending", pending).apply();
    }

    public static boolean isProfileSyncPending(Context ctx) {
        return getPrefs(ctx).getBoolean("profile_sync_pending", false);
    }

    public static void setPendingProfileData(Context ctx, String name, String keyword,
            String e1, String e2, String e3) {
        getPrefs(ctx).edit()
                .putString("pending_profile_name", name)
                .putString("pending_profile_keyword", keyword)
                .putString("pending_profile_e1", e1)
                .putString("pending_profile_e2", e2)
                .putString("pending_profile_e3", e3)
                .apply();
    }

    public static String getPendingProfileName(Context ctx) {
        return getPrefs(ctx).getString("pending_profile_name", null);
    }

    public static String getPendingProfileKeyword(Context ctx) {
        return getPrefs(ctx).getString("pending_profile_keyword", null);
    }

    public static String getPendingProfileE1(Context ctx) {
        return getPrefs(ctx).getString("pending_profile_e1", null);
    }

    public static String getPendingProfileE2(Context ctx) {
        return getPrefs(ctx).getString("pending_profile_e2", null);
    }

    public static String getPendingProfileE3(Context ctx) {
        return getPrefs(ctx).getString("pending_profile_e3", null);
    }

    public static void clearPendingProfileData(Context ctx) {
        getPrefs(ctx).edit()
                .remove("profile_sync_pending")
                .remove("pending_profile_name")
                .remove("pending_profile_keyword")
                .remove("pending_profile_e1")
                .remove("pending_profile_e2")
                .remove("pending_profile_e3")
                .apply();
    }

    // ================================================================
    //  OFFLINE EMERGENCY EVENT QUEUE — JSON Array (supports multiple)
    // ================================================================

    private static final String KEY_EMERGENCY_EVENT_QUEUE = "emergency_event_queue";

    public static void enqueueEmergencyEvent(Context ctx,
            String eventId, String userId, String triggerType,
            String sessionId, String triggeredAt,
            int batteryPercent, double locationLat, double locationLng,
            boolean hasLocationEnabled) {
        try {
            org.json.JSONArray queue = getEmergencyEventQueue(ctx);
            org.json.JSONObject item = new org.json.JSONObject();
            item.put("event_id", eventId);
            item.put("user_id", userId);
            item.put("trigger_type", triggerType != null ? triggerType : "UNKNOWN");
            item.put("session_id", sessionId);
            item.put("triggered_at", triggeredAt);
            item.put("battery_percent", batteryPercent);
            item.put("location_lat", locationLat);
            item.put("location_lng", locationLng);
            item.put("has_location_enabled", hasLocationEnabled);
            queue.put(item);
            getPrefs(ctx).edit()
                    .putString(KEY_EMERGENCY_EVENT_QUEUE, queue.toString())
                    .apply();
        } catch (Exception e) {
            android.util.Log.e("Prefs", "enqueueEmergencyEvent failed", e);
        }
    }

    public static org.json.JSONArray getEmergencyEventQueue(Context ctx) {
        try {
            String raw = getPrefs(ctx).getString(KEY_EMERGENCY_EVENT_QUEUE, "[]");
            return new org.json.JSONArray(raw);
        } catch (Exception e) {
            return new org.json.JSONArray();
        }
    }

    public static void removeEmergencyEventFromQueue(Context ctx, String eventId) {
        try {
            org.json.JSONArray queue = getEmergencyEventQueue(ctx);
            org.json.JSONArray updated = new org.json.JSONArray();
            for (int i = 0; i < queue.length(); i++) {
                org.json.JSONObject item = queue.getJSONObject(i);
                if (!eventId.equals(item.optString("event_id"))) {
                    updated.put(item);
                }
            }
            getPrefs(ctx).edit()
                    .putString(KEY_EMERGENCY_EVENT_QUEUE, updated.toString())
                    .apply();
        } catch (Exception e) {
            android.util.Log.e("Prefs", "removeEmergencyEventFromQueue failed", e);
        }
    }

    public static boolean isEmergencyEventQueueEmpty(Context ctx) {
        return getEmergencyEventQueue(ctx).length() == 0;
    }

    // ================================================================
    //  OFFLINE CALL RESULTS QUEUE — JSON Array (supports multiple)
    // ================================================================

    private static final String KEY_CALL_RESULTS_QUEUE = "call_results_queue";

    public static void enqueueCallResults(Context ctx,
            String eventId, String resultsJson) {
        try {
            org.json.JSONArray queue = getCallResultsQueue(ctx);
            org.json.JSONObject item = new org.json.JSONObject();
            item.put("event_id", eventId);
            item.put("results_json", resultsJson);
            queue.put(item);
            getPrefs(ctx).edit()
                    .putString(KEY_CALL_RESULTS_QUEUE, queue.toString())
                    .apply();
        } catch (Exception e) {
            android.util.Log.e("Prefs", "enqueueCallResults failed", e);
        }
    }

    public static org.json.JSONArray getCallResultsQueue(Context ctx) {
        try {
            String raw = getPrefs(ctx).getString(KEY_CALL_RESULTS_QUEUE, "[]");
            return new org.json.JSONArray(raw);
        } catch (Exception e) {
            return new org.json.JSONArray();
        }
    }

    public static void removeCallResultsFromQueue(Context ctx, String eventId) {
        try {
            org.json.JSONArray queue = getCallResultsQueue(ctx);
            org.json.JSONArray updated = new org.json.JSONArray();
            for (int i = 0; i < queue.length(); i++) {
                org.json.JSONObject item = queue.getJSONObject(i);
                if (!eventId.equals(item.optString("event_id"))) {
                    updated.put(item);
                }
            }
            getPrefs(ctx).edit()
                    .putString(KEY_CALL_RESULTS_QUEUE, updated.toString())
                    .apply();
        } catch (Exception e) {
            android.util.Log.e("Prefs", "removeCallResultsFromQueue failed", e);
        }
    }

    public static boolean isCallResultsQueueEmpty(Context ctx) {
        return getCallResultsQueue(ctx).length() == 0;
    }

    // ================================================================
    //  OFFLINE FEEDBACK QUEUE — JSON Array (supports multiple)
    // ================================================================

    private static final String KEY_FEEDBACK_QUEUE = "feedback_queue";

    public static void enqueueFeedback(Context ctx,
            String eventId, String userId,
            boolean wasRealEmergency, boolean wasRescued,
            int rating, String feedbackText) {
        try {
            org.json.JSONArray queue = getFeedbackQueue(ctx);
            org.json.JSONObject item = new org.json.JSONObject();
            item.put("event_id", eventId);
            item.put("user_id", userId);
            item.put("was_real_emergency", wasRealEmergency);
            item.put("was_rescued", wasRescued);
            item.put("rating", rating);
            item.put("feedback_text", feedbackText != null ? feedbackText : "");
            queue.put(item);
            getPrefs(ctx).edit()
                    .putString(KEY_FEEDBACK_QUEUE, queue.toString())
                    .apply();
        } catch (Exception e) {
            android.util.Log.e("Prefs", "enqueueFeedback failed", e);
        }
    }

    public static org.json.JSONArray getFeedbackQueue(Context ctx) {
        try {
            String raw = getPrefs(ctx).getString(KEY_FEEDBACK_QUEUE, "[]");
            return new org.json.JSONArray(raw);
        } catch (Exception e) {
            return new org.json.JSONArray();
        }
    }

    public static void removeFeedbackFromQueue(Context ctx, String eventId) {
        try {
            org.json.JSONArray queue = getFeedbackQueue(ctx);
            org.json.JSONArray updated = new org.json.JSONArray();
            for (int i = 0; i < queue.length(); i++) {
                org.json.JSONObject item = queue.getJSONObject(i);
                if (!eventId.equals(item.optString("event_id"))) {
                    updated.put(item);
                }
            }
            getPrefs(ctx).edit()
                    .putString(KEY_FEEDBACK_QUEUE, updated.toString())
                    .apply();
        } catch (Exception e) {
            android.util.Log.e("Prefs", "removeFeedbackFromQueue failed", e);
        }
    }

    public static boolean isFeedbackQueueEmpty(Context ctx) {
        return getFeedbackQueue(ctx).length() == 0;
    }

    // -- Last location synced to Supabase ---------------------------------
    public static void setLastSyncedLocation(Context ctx,
            double lat, double lng, long timestampMs) {
        getAll(ctx).edit()
                .putString("last_synced_location_lat", Double.toString(lat))
                .putString("last_synced_location_lng", Double.toString(lng))
                .putLong("last_synced_location_time_ms", timestampMs)
                .apply();
    }

    public static double getLastSyncedLocationLat(Context ctx) {
        try {
            return Double.parseDouble(
                    getAll(ctx).getString("last_synced_location_lat", "NaN"));
        } catch (Exception ignored) { return Double.NaN; }
    }

    public static double getLastSyncedLocationLng(Context ctx) {
        try {
            return Double.parseDouble(
                    getAll(ctx).getString("last_synced_location_lng", "NaN"));
        } catch (Exception ignored) { return Double.NaN; }
    }

    public static boolean hasAnySavedLocation(Context ctx) {
        double lat = getLastKnownLocationLat(ctx);
        double lng = getLastKnownLocationLng(ctx);
        return !Double.isNaN(lat) && !Double.isNaN(lng);
    }

    public static void setFirstLocationCaptured(Context ctx, boolean captured) {
        getAll(ctx).edit()
                .putBoolean("first_location_captured", captured).apply();
    }

    public static boolean isFirstLocationCaptured(Context ctx) {
        return getAll(ctx).getBoolean("first_location_captured", false);
    }

}
