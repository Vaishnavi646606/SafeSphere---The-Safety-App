package com.example.safesphere;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * Manifest-registered receiver for PHONE_STATE broadcasts.
 *
 * WHY THIS EXISTS:
 * MIUI kills the SafeSphere process ~3s after an emergency triggers (even with FULL_WAKE_LOCK).
 * The in-process PhoneStateListener dies with it, so we never detect RINGING→IDLE (no answer)
 * or OFFHOOK→IDLE (call ended). This receiver is re-spawned by Android for every broadcast,
 * so it survives process death and can drive the next-contact logic.
 *
 * STATE MACHINE:
 *   RINGING / OFFHOOK  → mark hasSeenActive=true in prefs
 *   IDLE after active  → call ended; if not answered, advance index and call next contact
 *   IDLE at start      → ignored (initial broadcast on registration)
 *
 * COORDINATION WITH IN-PROCESS LISTENER:
 * Both this receiver and the in-process PhoneStateListener may fire. The in-process listener
 * deactivates itself (listenerActive=false) when it handles a transition, and clears the
 * call sequence index. This receiver checks the index first — if -1, sequence is done/handled.
 */
public class PhoneStateReceiver extends BroadcastReceiver {

    private static final String TAG = "PHONE_STATE_RCV";
    private static final String FLOW = "FLOW";
    private static final long RECEIVER_END_RECHECK_DELAY_MS = 3_000L;
    private static final long NEXT_CALL_DELAY_MS = 2_000L;
    private static final long NEXT_CALL_IDLE_STABLE_MS = 1_500L;
    private static final long NEXT_CALL_RETRY_MS = 1_000L;

    // Prefs keys for cross-process state
    private static final String KEY_SEEN_ACTIVE = "rcv_seen_active";
    private static final String KEY_WAS_OFFHOOK = "rcv_was_offhook";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(intent.getAction())) return;

        String stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
        if (stateStr == null) return;

        int seqIndex = Prefs.getCallSequenceIndex(context);
        Log.d(TAG, "PHONE_STATE=" + stateStr + " seqIndex=" + seqIndex);
        Log.i(TAG, FLOW + "_RCV_STATE state=" + stateStr + " seqIndex=" + seqIndex);

        // No active emergency sequence — nothing to do
        if (seqIndex < 0) {
            Log.d(TAG, "No active call sequence – ignoring");
            return;
        }

        // Check for stale sequence (started > 5 minutes ago = abandoned)
        long startMs = Prefs.getCallStartTime(context);
        if (startMs > 0 && (System.currentTimeMillis() - startMs) > 5 * 60 * 1000L) {
            Log.d(TAG, "Call sequence is stale (>5 min) – clearing");
            clearState(context);
            return;
        }

        android.content.SharedPreferences prefs =
                context.getSharedPreferences("safesphere_prefs", Context.MODE_PRIVATE);

        if (TelephonyManager.EXTRA_STATE_RINGING.equals(stateStr)
                || TelephonyManager.EXTRA_STATE_OFFHOOK.equals(stateStr)) {
            Log.d(TAG, "Call active (" + stateStr + ") – marking seen");
            prefs.edit()
                    .putBoolean(KEY_SEEN_ACTIVE, true)
                    .putBoolean(KEY_WAS_OFFHOOK, TelephonyManager.EXTRA_STATE_OFFHOOK.equals(stateStr))
                    .apply();

        } else if (TelephonyManager.EXTRA_STATE_IDLE.equals(stateStr)) {
            long ignoreIdleUntilMs = prefs.getLong("rcv_ignore_idle_until_ms", -1L);
            if (ignoreIdleUntilMs > System.currentTimeMillis()) {
                Log.d(TAG, "Ignoring IDLE during protected window after in-process handling");
                return;
            }

            boolean skipNextIdle = prefs.getBoolean("rcv_skip_next_idle", false);
            if (skipNextIdle) {
                Log.d(TAG, "Skipping one IDLE event (already handled in-process)");
                prefs.edit().putBoolean("rcv_skip_next_idle", false).apply();
                return;
            }

            boolean seenActive = prefs.getBoolean(KEY_SEEN_ACTIVE, false);
            String[] numbers = Prefs.getEmergencyNumbers(context);
            String currentNumber = (seqIndex >= 0 && seqIndex < numbers.length) ? numbers[seqIndex] : null;
            boolean wasOffhook = EmergencyManager.wasOutgoingCallAnswered(context, currentNumber, startMs);
            Log.d(TAG, "IDLE – seenActive=" + seenActive + " wasOffhook=" + wasOffhook);

            // If we've never seen RINGING/OFFHOOK, this is likely the initial IDLE on registration.
            // HOWEVER: if the process was killed and restarted, we may have missed the RINGING
            // broadcast entirely. In that case seenActive=false but the call already ended.
            // Use call start time to distinguish: if a call was started recently (< 5 min ago),
            // treat this IDLE as a real call-ended event even without seenActive.
            boolean callStartedRecently = startMs > 0
                    && (System.currentTimeMillis() - startMs) < 5 * 60 * 1000L;

            if (!seenActive && !callStartedRecently) {
                Log.d(TAG, "Ignoring initial IDLE (call not yet active, no recent call start)");
                return;
            }

            // Reset per-call flags for next contact
            prefs.edit()
                    .putBoolean(KEY_SEEN_ACTIVE, false)
                    .putBoolean(KEY_WAS_OFFHOOK, false)
                    .apply();

            final int idleSeqIndex = seqIndex;
            final long idleStartMs = startMs;
            prefs.edit().putLong("rcv_ignore_idle_until_ms",
                    System.currentTimeMillis() + RECEIVER_END_RECHECK_DELAY_MS).apply();

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (Prefs.getCallSequenceIndex(context) != idleSeqIndex
                        || Prefs.getCallStartTime(context) != idleStartMs) {
                    Log.d(TAG, "Skipping stale receiver post-end decision");
                    return;
                }

                if (EmergencyManager.isAnyCallActive(context)) {
                    Log.d(TAG, "Receiver next blocked: call still active, waiting for real IDLE event");
                    return;
                }

                boolean answeredAfterDelay = EmergencyManager.wasOutgoingCallAnswered(
                        context, currentNumber, idleStartMs);
                if (answeredAfterDelay) {
                    Log.d(TAG, "✅ Call ANSWERED (receiver) – clearing sequence");
                    Log.i(TAG, FLOW + "_RCV_SEQUENCE_COMPLETE reason=answered");
                    clearState(context);
                    return;
                }

                int nextIndex = idleSeqIndex + 1;
                Log.d(TAG, "❌ No answer (receiver) after delayed recheck – advancing to contact " + (nextIndex + 1));
                Log.i(TAG, FLOW + "_RCV_NEXT from=" + (idleSeqIndex + 1) + " to=" + (nextIndex + 1));

                if (nextIndex >= numbers.length) {
                    Log.d(TAG, "All contacts tried – sequence complete");
                    String feedbackEventId = Prefs.getLastEmergencyEventId(context);
                    if (feedbackEventId != null && !feedbackEventId.trim().isEmpty()) {
                        EmergencyManager.showFeedbackNotification(context, feedbackEventId);
                    } else {
                        android.util.Log.w("PHONE_STATE_RCV",
                                "Cannot show feedback notification: no event ID in Prefs");
                    }
                    clearState(context);
                    return;
                }

                while (nextIndex < numbers.length
                        && (numbers[nextIndex] == null || numbers[nextIndex].trim().isEmpty())) {
                    nextIndex++;
                }
                if (nextIndex >= numbers.length) {
                    Log.d(TAG, "No more valid contacts – sequence complete");
                    clearState(context);
                    return;
                }

                final int finalIndex = nextIndex;
                Prefs.setCallSequenceIndex(context, finalIndex);
                Prefs.setCallStartTime(context, -1L);

                scheduleReceiverNextCallWhenIdleStable(context, finalIndex, numbers[finalIndex].trim());
            }, RECEIVER_END_RECHECK_DELAY_MS);
        }
    }

    private void scheduleReceiverNextCallWhenIdleStable(Context context, int finalIndex, String number) {
        Handler handler = new Handler(Looper.getMainLooper());
        Runnable[] waitRef = new Runnable[1];

        waitRef[0] = new Runnable() {
            @Override
            public void run() {
                if (Prefs.getCallSequenceIndex(context) != finalIndex
                        || Prefs.getCallStartTime(context) != -1L) {
                    Log.d(TAG, "Skipping stale receiver next-call callback");
                    return;
                }

                if (EmergencyManager.isAnyCallActive(context) || !isTelephonyIdle(context)) {
                    Log.d(TAG, "Receiver waiting for idle before calling next contact");
                    handler.postDelayed(waitRef[0], NEXT_CALL_RETRY_MS);
                    return;
                }

                handler.postDelayed(() -> {
                    if (Prefs.getCallSequenceIndex(context) != finalIndex
                            || Prefs.getCallStartTime(context) != -1L) {
                        Log.d(TAG, "Skipping stale receiver stable-idle callback");
                        return;
                    }

                    if (EmergencyManager.isAnyCallActive(context) || !isTelephonyIdle(context)) {
                        Log.d(TAG, "Receiver idle not stable yet, retrying");
                        handler.postDelayed(waitRef[0], NEXT_CALL_RETRY_MS);
                        return;
                    }

                    Log.d(TAG, "⏭️ Receiver calling contact " + (finalIndex + 1) + ": " + number);
                    Log.i(TAG, FLOW + "_RCV_CALL_ATTEMPT contact=" + (finalIndex + 1));
                    EmergencyManager.callSingleContact(context, number, finalIndex);
                }, NEXT_CALL_IDLE_STABLE_MS);
            }
        };

        handler.postDelayed(waitRef[0], NEXT_CALL_DELAY_MS);
    }

    private boolean isTelephonyIdle(Context context) {
        try {
            TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            return tm == null || tm.getCallState() == TelephonyManager.CALL_STATE_IDLE;
        } catch (Exception e) {
            Log.w(TAG, "Unable to read telephony idle state", e);
            return false;
        }
    }

    private void clearState(Context context) {
        Prefs.clearCallSequence(context);
        context.getSharedPreferences("safesphere_prefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_SEEN_ACTIVE, false)
                .putBoolean(KEY_WAS_OFFHOOK, false)
            .putLong("rcv_ignore_idle_until_ms", -1L)
                .apply();
        // Notify SafeSphereService to restart Vosk listener
        try {
            context.sendBroadcast(new Intent(SafeSphereService.ACTION_SEQUENCE_COMPLETE));
            Log.d(TAG, "📡 EMERGENCY_SEQUENCE_COMPLETE broadcast sent from receiver");
            Log.i(TAG, FLOW + "_RCV_BROADCAST_SEQUENCE_COMPLETE");
        } catch (Exception e) {
            Log.e(TAG, "Failed to broadcast sequence complete", e);
        }
        // If service was killed, wake it so mic listener resumes without opening app.
        try {
            boolean canRunProtection = Prefs.isProtectionEnabled(context)
                    && ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED;
            if (canRunProtection) {
                ContextCompat.startForegroundService(context,
                        new Intent(context, SafeSphereService.class));
                Log.d(TAG, "✅ Requested SafeSphereService restart from PhoneStateReceiver");
                Log.i(TAG, FLOW + "_RCV_SERVICE_WAKE_REQUESTED");
            } else {
                Log.d(TAG, "Skipping service wake: protection off or RECORD_AUDIO missing");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to restart SafeSphereService from PhoneStateReceiver", e);
        }
    }
}
