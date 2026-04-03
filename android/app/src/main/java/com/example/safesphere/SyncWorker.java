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
 * Handles: pending profile updates, pending feedback submissions.
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

        // Sync pending profile update
        if (Prefs.isProfileSyncPending(ctx)) {
            anyFailed = !syncProfile(ctx);
        }

        // Sync pending feedback
        if (Prefs.isFeedbackSyncPending(ctx)) {
            boolean feedbackOk = syncFeedback(ctx);
            if (!feedbackOk) anyFailed = true;
        }

        return anyFailed ? Result.retry() : Result.success();
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
     * Call this from ProfileActivity and EmergencyFeedbackActivity
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
