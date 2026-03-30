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
 * Manifest-registered receiver that restarts SafeSphereService.
 *
 * This is triggered by:
 *  1. onTaskRemoved() in SafeSphereService (when app is swiped away)
 *  2. AlarmManager watchdog (every 60s, checks if service needs restart)
 *
 * Being manifest-registered means Android will spawn a new process to
 * deliver this broadcast even if the app process was killed — which is
 * exactly what we need on MIUI/aggressive ROMs.
 */
public class ServiceRestartReceiver extends BroadcastReceiver {

    static final String ACTION_RESTART = "com.example.safesphere.RESTART_SERVICE";
    private static final String TAG = "SVC_RESTART_RCV";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION_RESTART.equals(intent.getAction())) return;

        Log.d(TAG, "ServiceRestartReceiver triggered – restarting SafeSphereService");

        if (!Prefs.isLoggedIn(context)) {
            Log.d(TAG, "User not logged in – skipping restart");
            return;
        }

        if (!Prefs.isProtectionEnabled(context)) {
            Log.d(TAG, "Protection disabled – skipping restart");
            return;
        }

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "RECORD_AUDIO missing – skipping service restart");
            return;
        }

        try {
            Intent serviceIntent = new Intent(context, SafeSphereService.class);
            ContextCompat.startForegroundService(context, serviceIntent);
            Log.d(TAG, "✅ SafeSphereService restart requested");
        } catch (Exception e) {
            Log.e(TAG, "Failed to restart SafeSphereService", e);
        }
    }
}
