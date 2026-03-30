package com.example.safesphere.revocation;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.safesphere.LoginActivity;
import com.example.safesphere.Prefs;
import com.example.safesphere.SafeSphereService;
import com.example.safesphere.analytics.AnalyticsDatabase;

import androidx.work.WorkManager;

/**
 * Handles force-logout when revocation is detected.
 * Called from both SyncWorker and RevocationCheckWorker.
 */
public class RevocationHandler {

    private static final String TAG = "RevocationHandler";
    private static final String CHANNEL_ID = "safesphere_revocation";
    private static final int NOTIF_ID = 9999;

    /**
     * Called when revocation is detected.
     * Stores revocation state and shows notification.
     * Actual logout happens in MainActivity.onResume via checkAndHandleRevocation().
     */
    public static void handleRevocation(Context ctx, int version, String message) {
        Log.w(TAG, "Revocation detected. Version: " + version);

        // Save revocation state
        Prefs.setRevocationVersion(ctx, version);
        Prefs.setPendingRevocation(ctx, true);
        if (message != null && !message.isEmpty()) {
            Prefs.setRevocationMessage(ctx, message);
        }

        // Show high-priority notification
        showRevocationNotification(ctx, message);
    }

    /**
     * Called from MainActivity.onResume() to check and handle pending revocation.
     * Returns true if revocation was detected (caller should handle navigation).
     */
    public static boolean isPendingRevocation(Context ctx) {
        return Prefs.isPendingRevocation(ctx);
    }

    /**
     * Perform the actual logout: clear all local data.
     * Call this AFTER showing the user the revocation message.
     */
    public static void performLogout(Context ctx) {
        Log.w(TAG, "Performing forced logout");

        // 1. Stop the SafeSphereService
        try {
            Intent serviceIntent = new Intent(ctx, SafeSphereService.class);
            ctx.stopService(serviceIntent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to stop service: " + e.getMessage());
        }

        // 2. Cancel all WorkManager jobs
        try {
            WorkManager.getInstance(ctx).cancelAllWork();
        } catch (Exception e) {
            Log.e(TAG, "Failed to cancel WorkManager: " + e.getMessage());
        }

        // 3. Clear Room analytics database
        try {
            AnalyticsDatabase.destroyInstance();
            ctx.deleteDatabase("safesphere_analytics.db");
        } catch (Exception e) {
            Log.e(TAG, "Failed to delete analytics DB: " + e.getMessage());
        }

        // 4. Clear SharedPreferences (preserves revocation message for display)
        String revocationMsg = Prefs.getRevocationMessage(ctx);
        Prefs.clearAllData(ctx);
        // Re-save message briefly so LoginActivity can display it
        if (revocationMsg != null) {
            Prefs.setRevocationMessage(ctx, revocationMsg);
        }

        // 5. Cancel revocation notification
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(NOTIF_ID);
    }

    private static void showRevocationNotification(Context ctx, String message) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Account Notices", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Admin messages and account status");
            nm.createNotificationChannel(channel);
        }

        Intent intent = new Intent(ctx, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pending = PendingIntent.getActivity(ctx, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("SafeSphere Account Notice")
                .setContentText(message != null ? message : "Your account status has changed. Please open the app.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pending);

        nm.notify(NOTIF_ID, builder.build());
    }
}
