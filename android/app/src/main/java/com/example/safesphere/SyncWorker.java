package com.example.safesphere;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.example.safesphere.network.SupabaseClient;
import org.json.JSONArray;
import org.json.JSONObject;

public class SyncWorker extends Worker {

    private static final String TAG = "SyncWorker";
    private static final String WORK_TAG = "offline_sync";

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    public static void scheduleSyncWhenOnline(Context ctx) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(SyncWorker.class)
                .setConstraints(constraints)
                .addTag(WORK_TAG)
                .build();
        WorkManager.getInstance(ctx)
                .enqueueUniqueWork(
                        "safesphere_offline_sync",
                        androidx.work.ExistingWorkPolicy.REPLACE,
                        request);
        Log.d(TAG, "Sync job scheduled - will run when internet available");
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        boolean anyFailed = false;

        if (!Prefs.isEmergencyEventQueueEmpty(ctx)) {
            boolean ok = syncEmergencyEventQueue(ctx);
            if (!ok) anyFailed = true;
        }

        if (Prefs.isEmergencyEventQueueEmpty(ctx)
                && !Prefs.isCallResultsQueueEmpty(ctx)) {
            boolean ok = syncCallResultsQueue(ctx);
            if (!ok) anyFailed = true;
        }

        if (Prefs.isProfileSyncPending(ctx)) {
            boolean ok = syncProfile(ctx);
            if (!ok) anyFailed = true;
        }

        if (Prefs.isEmergencyEventQueueEmpty(ctx)
                && !Prefs.isFeedbackQueueEmpty(ctx)) {
            boolean ok = syncFeedbackQueue(ctx);
            if (!ok) anyFailed = true;
        }

        return anyFailed ? Result.retry() : Result.success();
    }

    private boolean syncEmergencyEventQueue(Context ctx) {
        boolean allSucceeded = true;
        try {
            JSONArray queue = Prefs.getEmergencyEventQueue(ctx);
            Log.d(TAG, "Syncing emergency event queue - " + queue.length() + " item(s)");
            for (int i = 0; i < queue.length(); i++) {
                JSONObject item = queue.getJSONObject(i);
                String eventId = item.optString("event_id", null);
                String userId = item.optString("user_id", null);
                if (eventId == null || userId == null) {
                    Prefs.removeEmergencyEventFromQueue(ctx, eventId);
                    continue;
                }
                try {
                    JSONObject eventData = new JSONObject();
                    eventData.put("id", eventId);
                    eventData.put("user_id", userId);
                    eventData.put("trigger_type", item.optString("trigger_type", "UNKNOWN"));
                    eventData.put("session_id", item.optString("session_id", null));
                    eventData.put("triggered_at", item.optString("triggered_at",
                            SupabaseClient.toIso8601(System.currentTimeMillis())));
                    eventData.put("status", "triggered");
                    eventData.put("is_test_event", false);
                    eventData.put("has_location_enabled",
                            item.optBoolean("has_location_enabled", false));
                    eventData.put("phone_battery_percent",
                            item.optInt("battery_percent", 0));
                    double lat = item.optDouble("location_lat", Double.NaN);
                    double lng = item.optDouble("location_lng", Double.NaN);
                    if (!Double.isNaN(lat) && !Double.isNaN(lng)) {
                        eventData.put("location_lat", lat);
                        eventData.put("location_lng", lng);
                    }
                    SupabaseClient client = SupabaseClient.getInstance(ctx);
                    SupabaseClient.SupabaseResponse response =
                            client.insertRowReturningRepresentation("emergency_events", eventData);
                    if (response.success) {
                        Prefs.removeEmergencyEventFromQueue(ctx, eventId);
                        Log.d(TAG, "Emergency event synced: " + eventId);
                        client.incrementTotalEmergencies(userId);
                    } else {
                        Log.w(TAG, "Emergency event sync failed: " + response.message);
                        allSucceeded = false;
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Emergency event sync exception for " + eventId, e);
                    allSucceeded = false;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "syncEmergencyEventQueue exception", e);
            allSucceeded = false;
        }
        return allSucceeded;
    }

    private boolean syncCallResultsQueue(Context ctx) {
        boolean allSucceeded = true;
        try {
            JSONArray queue = Prefs.getCallResultsQueue(ctx);
            Log.d(TAG, "Syncing call results queue - " + queue.length() + " item(s)");
            for (int i = 0; i < queue.length(); i++) {
                JSONObject item = queue.getJSONObject(i);
                String eventId = item.optString("event_id", null);
                String resultsJson = item.optString("results_json", null);
                if (eventId == null || resultsJson == null) {
                    Prefs.removeCallResultsFromQueue(ctx, eventId);
                    continue;
                }
                try {
                    JSONObject resultsUpdate = new JSONObject(resultsJson);
                    SupabaseClient client = SupabaseClient.getInstance(ctx);
                    SupabaseClient.SupabaseResponse response =
                            client.updateEmergencyEventResults(eventId, resultsUpdate);
                    if (response.success) {
                        Prefs.removeCallResultsFromQueue(ctx, eventId);
                        Log.d(TAG, "Call results synced for event: " + eventId);
                    } else {
                        Log.w(TAG, "Call results sync failed: " + response.message);
                        allSucceeded = false;
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Call results sync exception for " + eventId, e);
                    allSucceeded = false;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "syncCallResultsQueue exception", e);
            allSucceeded = false;
        }
        return allSucceeded;
    }

    private boolean syncProfile(Context ctx) {
        try {
            String userId = Prefs.getSupabaseUserId(ctx);
            String name = Prefs.getPendingProfileName(ctx);
            String keyword = Prefs.getPendingProfileKeyword(ctx);
            String e1 = Prefs.getPendingProfileE1(ctx);
            String e2 = Prefs.getPendingProfileE2(ctx);
            String e3 = Prefs.getPendingProfileE3(ctx);
            if (userId == null || name == null) {
                Prefs.clearPendingProfileData(ctx);
                return true;
            }
            JSONObject profileData = new JSONObject();
            profileData.put("display_name", name);
            profileData.put("keyword", keyword);
            profileData.put("emergency_contact_1", e1 != null ? e1 : "");
            profileData.put("emergency_contact_2", e2 != null ? e2 : "");
            profileData.put("emergency_contact_3", e3 != null ? e3 : "");
            profileData.put("updated_at",
                    SupabaseClient.toIso8601(System.currentTimeMillis()));
            SupabaseClient client = SupabaseClient.getInstance(ctx);
            SupabaseClient.SupabaseResponse response =
                    client.updateRow("users", "id", userId, profileData);
            if (response.success) {
                Prefs.clearPendingProfileData(ctx);
                Log.d(TAG, "Profile synced successfully");
                return true;
            } else {
                Log.w(TAG, "Profile sync failed: " + response.message);
                return false;
            }
        } catch (Exception e) {
            Log.w(TAG, "syncProfile exception", e);
            return false;
        }
    }

    private boolean syncFeedbackQueue(Context ctx) {
        boolean allSucceeded = true;
        try {
            JSONArray queue = Prefs.getFeedbackQueue(ctx);
            Log.d(TAG, "Syncing feedback queue - " + queue.length() + " item(s)");
            for (int i = 0; i < queue.length(); i++) {
                JSONObject item = queue.getJSONObject(i);
                String eventId = item.optString("event_id", null);
                String userId = item.optString("user_id", null);
                int rating = item.optInt("rating", 0);
                if (eventId == null || userId == null || rating == 0) {
                    Prefs.removeFeedbackFromQueue(ctx, eventId);
                    continue;
                }
                try {
                    SupabaseClient.EmergencyFeedbackData feedbackData =
                            new SupabaseClient.EmergencyFeedbackData();
                    feedbackData.eventId = eventId;
                    feedbackData.userId = userId;
                    feedbackData.wasRealEmergency = item.optBoolean("was_real_emergency", false);
                    feedbackData.wasRescuedOrHelped = item.optBoolean("was_rescued", false);
                    feedbackData.rating = rating;
                    feedbackData.feedbackText = item.optString("feedback_text", "");
                    SupabaseClient client = SupabaseClient.getInstance(ctx);
                    SupabaseClient.SupabaseResponse response =
                            client.submitEmergencyFeedback(feedbackData);
                    if (response.success) {
                        Prefs.removeFeedbackFromQueue(ctx, eventId);
                        Log.d(TAG, "Feedback synced for event: " + eventId);
                    } else if (response.message != null
                            && response.message.contains("23503")) {
                        Prefs.removeFeedbackFromQueue(ctx, eventId);
                        Log.w(TAG, "Feedback FK violation - removed: " + eventId);
                    } else {
                        Log.w(TAG, "Feedback sync failed: " + response.message);
                        allSucceeded = false;
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Feedback sync exception for " + eventId, e);
                    allSucceeded = false;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "syncFeedbackQueue exception", e);
            allSucceeded = false;
        }
        return allSucceeded;
    }
}