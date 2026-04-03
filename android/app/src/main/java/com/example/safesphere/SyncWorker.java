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

import org.json.JSONObject;

/**
 * WorkManager worker that syncs pending offline changes to Supabase.
 * Runs automatically when internet becomes available, even if app is killed.
 * Handles: pending emergency events, pending call results, pending profile updates, pending feedback submissions.
 */
public class SyncWorker extends Worker {

    private static final String TAG = "SyncWorker";

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        boolean anyFailed = false;

        // ORDER MATTERS — event must exist before call results and feedback

        // Step 1 — sync pending emergency event insert
        if (Prefs.isEmergencyEventSyncPending(ctx)) {
            boolean ok = syncEmergencyEvent(ctx);
            if (!ok) anyFailed = true;
        }

        // Step 2 — sync pending call results PATCH
        // Only attempt if event insert is no longer pending
        if (!Prefs.isEmergencyEventSyncPending(ctx)
                && Prefs.isCallResultsSyncPending(ctx)) {
            boolean ok = syncCallResults(ctx);
            if (!ok) anyFailed = true;
        }

        // Step 3 — sync pending profile update
        if (Prefs.isProfileSyncPending(ctx)) {
            boolean ok = syncProfile(ctx);
            if (!ok) anyFailed = true;
        }

        // Step 4 — sync pending feedback
        // Only attempt if event insert is no longer pending
        if (!Prefs.isEmergencyEventSyncPending(ctx)
                && Prefs.isFeedbackSyncPending(ctx)) {
            boolean ok = syncFeedback(ctx);
            if (!ok) anyFailed = true;
        }

        return anyFailed ? Result.retry() : Result.success();
    }

    private boolean syncEmergencyEvent(Context ctx) {
        try {
            String eventId    = Prefs.getPendingEventId(ctx);
            String userId     = Prefs.getPendingEventUserId(ctx);
            String triggerType = Prefs.getPendingEventTriggerType(ctx);
            String sessionId  = Prefs.getPendingEventSessionId(ctx);
            String triggeredAt = Prefs.getPendingEventTriggeredAt(ctx);

            if (eventId == null || userId == null) {
                Prefs.clearPendingEmergencyEventData(ctx);
                return true;
            }

            JSONObject eventData = new JSONObject();
            eventData.put("id", eventId);
            eventData.put("user_id", userId);
            eventData.put("trigger_type",
                    triggerType != null ? triggerType : "UNKNOWN");
            eventData.put("session_id", sessionId);
            eventData.put("triggered_at",
                    triggeredAt != null ? triggeredAt :
                    SupabaseClient.toIso8601(System.currentTimeMillis()));
            eventData.put("status", "triggered");
            eventData.put("is_test_event", false);
            eventData.put("has_location_enabled",
                    Prefs.getPendingEventHasLocation(ctx));
            eventData.put("phone_battery_percent",
                    Prefs.getPendingEventBattery(ctx));

            double lat = Prefs.getPendingEventLat(ctx);
            double lng = Prefs.getPendingEventLng(ctx);
            if (!Double.isNaN(lat) && !Double.isNaN(lng)) {
                eventData.put("location_lat", lat);
                eventData.put("location_lng", lng);
            }

            SupabaseClient client = SupabaseClient.getInstance(ctx);
            SupabaseClient.SupabaseResponse response =
                    client.insertRowReturningRepresentation(
                            "emergency_events", eventData);

            if (response.success) {
                Prefs.clearPendingEmergencyEventData(ctx);
                Log.d(TAG, "Emergency event synced successfully: " + eventId);
                // Also increment total emergencies
                client.incrementTotalEmergencies(userId);
                return true;
            } else {
                Log.w(TAG, "Emergency event sync failed: " + response.message);
                return false;
            }
        } catch (Exception e) {
            Log.w(TAG, "Emergency event sync exception", e);
            return false;
        }
    }

    private boolean syncCallResults(Context ctx) {
        try {
            String eventId     = Prefs.getPendingCallResultsEventId(ctx);
            String resultsJson = Prefs.getPendingCallResultsJson(ctx);

            if (eventId == null || resultsJson == null) {
                Prefs.clearPendingCallResultsData(ctx);
                return true;
            }

            JSONObject resultsUpdate = new JSONObject(resultsJson);

            SupabaseClient client = SupabaseClient.getInstance(ctx);
            SupabaseClient.SupabaseResponse response =
                    client.updateEmergencyEventResults(eventId, resultsUpdate);

            if (response.success) {
                Prefs.clearPendingCallResultsData(ctx);
                Log.d(TAG, "Call results synced successfully for event: " + eventId);
                return true;
            } else {
                Log.w(TAG, "Call results sync failed: " + response.message);
                return false;
            }
        } catch (Exception e) {
            Log.w(TAG, "Call results sync exception", e);
            return false;
        }
    }

    private boolean syncProfile(Context ctx) {
        try {
            String userId = Prefs.getSupabaseUserId(ctx);
            if (userId == null || userId.isEmpty()) return false;

            String name    = Prefs.getPendingProfileName(ctx);
            String keyword = Prefs.getPendingProfileKeyword(ctx);
            String e1      = Prefs.getPendingProfileE1(ctx);
            String e2      = Prefs.getPendingProfileE2(ctx);
            String e3      = Prefs.getPendingProfileE3(ctx);

            if (name == null && keyword == null) return true;

            JSONObject patch = new JSONObject();
            if (name != null)    patch.put("display_name", name);
            if (keyword != null) patch.put("keyword", keyword.toLowerCase().trim());
            if (e1 != null)      patch.put("emergency_contact_1", e1);
            if (e2 != null)      patch.put("emergency_contact_2", e2);
            if (e3 != null)      patch.put("emergency_contact_3", e3);
            patch.put("updated_at", SupabaseClient.toIso8601(System.currentTimeMillis()));

            SupabaseClient client = SupabaseClient.getInstance(ctx);
            SupabaseClient.SupabaseResponse response =
                    client.updateRow("users", "id", userId, patch);

            if (response.success) {
                Prefs.clearPendingProfileData(ctx);
                Log.d(TAG, "Profile synced successfully");
                return true;
            } else {
                Log.w(TAG, "Profile sync failed: " + response.message);
                return false;
            }
        } catch (Exception e) {
            Log.w(TAG, "Profile sync exception", e);
            return false;
        }
    }

    private boolean syncFeedback(Context ctx) {
        try {
            String eventId        = Prefs.getPendingFeedbackEventId(ctx);
            String feedbackUserId = Prefs.getPendingFeedbackUserId(ctx);
            int rating            = Prefs.getPendingFeedbackRating(ctx);

            if (eventId == null || feedbackUserId == null || rating == 0) return true;

            SupabaseClient.EmergencyFeedbackData feedbackData =
                    new SupabaseClient.EmergencyFeedbackData();
            feedbackData.eventId            = eventId;
            feedbackData.userId             = feedbackUserId;
            feedbackData.wasRealEmergency   = Prefs.getPendingFeedbackWasReal(ctx);
            feedbackData.wasRescuedOrHelped = Prefs.getPendingFeedbackWasRescued(ctx);
            feedbackData.rating             = rating;
            feedbackData.feedbackText       = Prefs.getPendingFeedbackText(ctx);

            SupabaseClient client = SupabaseClient.getInstance(ctx);
            SupabaseClient.SupabaseResponse response =
                    client.submitEmergencyFeedback(feedbackData);

            if (response.success) {
                Prefs.clearPendingFeedbackData(ctx);
                Log.d(TAG, "Feedback synced successfully");
                return true;
            }

            // Check for foreign key violation (error code 23503)
            // This means the emergency_events row does not exist in Supabase
            // (emergency was triggered offline and event insert also failed)
            // Retrying will never succeed — clear the pending feedback
            if (response.message != null && response.message.contains("23503")) {
                Log.w(TAG, "Feedback eventId not found in emergency_events — "
                        + "clearing pending feedback, cannot retry: " + eventId);
                Prefs.clearPendingFeedbackData(ctx);
                return true; // return true so WorkManager does not retry
            }

            // Any other error — keep pending and retry later
            Log.w(TAG, "Feedback sync failed, will retry: " + response.message);
            return false;

        } catch (Exception e) {
            Log.w(TAG, "Feedback sync exception", e);
            return false;
        }
    }

    /**
     * Call this from ProfileActivity,  EmergencyFeedbackActivity, or EmergencyManager
     * after saving data to Prefs offline.
     * WorkManager will run this automatically when internet returns,
     * even if the app is completely killed.
     */
    public static void scheduleSyncWhenOnline(Context ctx) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest syncRequest = new OneTimeWorkRequest.Builder(SyncWorker.class)
                .setConstraints(constraints)
                .addTag("offline_sync")
                .build();

        WorkManager.getInstance(ctx)
                .enqueueUniqueWork(
                        "safesphere_offline_sync",
                        androidx.work.ExistingWorkPolicy.REPLACE,
                        syncRequest);

        Log.d(TAG, "Sync job scheduled — will run when internet available");
    }
}
