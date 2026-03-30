package com.example.safesphere;

import android.content.Context;
import android.content.SharedPreferences;

public class Prefs {

    private static final String PREF_NAME = "safesphere_prefs";

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
        android.util.Log.e("PREFS_DEBUG", "==============================");
    }

    public static SharedPreferences getAll(Context ctx) {
        return ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static String getKeyword(Context ctx) {

        return getAll(ctx).getString("keyword", "");
    }


    public static String getUserPhone(Context ctx) {
        return getAll(ctx).getString("phone", "");
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

    public static String getUserId(Context ctx) {
        return getAll(ctx).getString("analytics_user_id", null);
    }

    public static void setUserId(Context ctx, String userId) {
        getAll(ctx).edit().putString("analytics_user_id", userId).apply();
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

}

