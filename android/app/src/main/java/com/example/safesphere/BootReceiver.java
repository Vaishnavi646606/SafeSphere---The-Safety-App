package com.example.safesphere;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * Restarts SafeSphereService automatically after the phone reboots.
 * Requires RECEIVE_BOOT_COMPLETED permission in AndroidManifest.
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BOOT_RECEIVER";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)) {

            // Only restart if user is logged in AND protection is enabled
            if (Prefs.isLoggedIn(context) && Prefs.isProtectionEnabled(context)) {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Boot completed – RECORD_AUDIO missing, skipping service start");
                    return;
                }
                Log.d(TAG, "Boot completed – restarting SafeSphereService");
                Intent serviceIntent = new Intent(context, SafeSphereService.class);
                ContextCompat.startForegroundService(context, serviceIntent);
            } else {
                Log.d(TAG, "Boot completed – user not logged in or protection disabled, skipping service start");
            }
        }
    }
}
