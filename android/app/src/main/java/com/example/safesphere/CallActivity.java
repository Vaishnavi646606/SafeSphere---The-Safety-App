package com.example.safesphere;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.telecom.TelecomManager;
import android.util.Log;
import android.view.WindowManager;

import androidx.core.app.ActivityCompat;

/**
 * Transparent trampoline activity that fires ACTION_CALL and immediately finishes.
 * Running in an Activity context bypasses Android 10+ background call restrictions.
 * Started by EmergencyManager via a full-screen PendingIntent.
 *
 * Also handles ACTION_END_CALL to terminate an active call before placing the next one.
 */
public class CallActivity extends android.app.Activity {

    private static final String TAG = "CALL_ACTIVITY";
    public  static final String EXTRA_NUMBER   = "number";
    public  static final String EXTRA_ACTION   = "action";
    public  static final String EXTRA_ATTEMPT_ID = "attempt_id";
    public  static final String ACTION_CALL    = "call";
    public  static final String ACTION_END_CALL = "end_call";
    private static long lastHandledAttemptId = -1L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "CallActivity.onCreate()");

        // Show over lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);

        if (shouldIgnoreDuplicateLaunch(getIntent())) {
            finish();
            return;
        }

        String action = getIntent().getStringExtra(EXTRA_ACTION);
        if (ACTION_END_CALL.equals(action)) {
            endCurrentCall();
            finish();
        } else {
            placeCall();
            // placeCall() handles its own delayed finish
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

        Log.d(TAG, "CallActivity.onNewIntent()");
        if (shouldIgnoreDuplicateLaunch(intent)) {
            return;
        }

        String action = intent.getStringExtra(EXTRA_ACTION);
        if (ACTION_END_CALL.equals(action)) {
            endCurrentCall();
            finish();
        } else {
            placeCall();
        }
    }

    private boolean shouldIgnoreDuplicateLaunch(Intent intent) {
        long attemptId = intent != null ? intent.getLongExtra(EXTRA_ATTEMPT_ID, -1L) : -1L;
        if (attemptId <= 0L) {
            return false;
        }
        if (attemptId == lastHandledAttemptId) {
            Log.d(TAG, "Ignoring duplicate CallActivity launch for attemptId=" + attemptId);
            return true;
        }
        lastHandledAttemptId = attemptId;
        return false;
    }

    private void placeCall() {
        String number = getIntent().getStringExtra(EXTRA_NUMBER);
        Log.d(TAG, "placeCall() to: " + number);

        if (number == null || number.isEmpty()) {
            Log.e(TAG, "No number provided");
            finish();
            return;
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "CALL_PHONE permission denied");
            finish();
            return;
        }

        boolean callStarted = false;

        // Primary: ACTION_CALL intent (works when app is in foreground / has permission)
        try {
            Intent callIntent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + number));
            callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(callIntent);
            Log.d(TAG, "✅ ACTION_CALL started for: " + number);
            callStarted = true;
        } catch (Exception e) {
            Log.e(TAG, "ACTION_CALL failed: " + e.getMessage());
        }

        // Fallback: TelecomManager.placeCall() — works even when ACTION_CALL is blocked
        if (!callStarted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                TelecomManager telecomManager = (TelecomManager) getSystemService(TELECOM_SERVICE);
                if (telecomManager != null) {
                    android.os.Bundle extras = new android.os.Bundle();
                    extras.putBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, false);
                    telecomManager.placeCall(Uri.parse("tel:" + number), extras);
                    Log.d(TAG, "✅ TelecomManager.placeCall() started for: " + number);
                    callStarted = true;
                }
            } catch (Exception e) {
                Log.e(TAG, "TelecomManager.placeCall() failed: " + e.getMessage());
            }
        }

        if (!callStarted) {
            Log.e(TAG, "❌ All call methods failed for: " + number);
        }

        // Delay finish so the call intent has time to be processed before this activity dies
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this::finish, 500);
    }

    private void endCurrentCall() {
        Log.d(TAG, "endCurrentCall()");
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                TelecomManager tm = (TelecomManager) getSystemService(TELECOM_SERVICE);
                if (tm != null && ActivityCompat.checkSelfPermission(this,
                        Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
                    tm.endCall();
                    Log.d(TAG, "✅ endCall() via TelecomManager (activity context)");
                } else {
                    Log.w(TAG, "TelecomManager null or permission missing");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "endCurrentCall failed: " + e.getMessage());
        }
    }
}
