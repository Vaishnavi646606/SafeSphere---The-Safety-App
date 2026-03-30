package com.example.safesphere;

import android.Manifest;
import android.app.ActivityOptions;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.CallLog;
import android.os.BatteryManager;
import android.telecom.TelecomManager;
import android.telephony.PhoneNumberUtils;
import android.telephony.PhoneStateListener;
import android.telephony.SmsManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.safesphere.analytics.AnalyticsQueue;
import com.example.safesphere.analytics.EventType;

public class EmergencyManager {

    private static final String TAG             = "EMERGENCY";
    private static final String CALL_CHANNEL_ID = "safesphere_call";
    private static final int    CALL_NOTIF_BASE = 2000;
    private static final String FLOW = "FLOW";

    private static String[] currentNumbers;
    private static int      currentIndex    = 0;

    private static final int  LIVE_MAX_MESSAGES = 1;  // Changed from 5 to 1 - send location only once
    private static final long LIVE_INTERVAL_MS  = 60_000;
    private static int liveSentCount = 0;
    private static final Handler liveHandler = new Handler(Looper.getMainLooper());

    private static ToneGenerator toneGen;
    private static PhoneStateListener currentListener = null;
    
    // Event-driven flow: check call-log 3s after call-end before deciding next/stop.
    private static final long CALLLOG_END_RECHECK_DELAY_MS = 3_000;
    private static final Handler timeoutHandler = new Handler(Looper.getMainLooper());
    private static Runnable pendingStartNextRetry = null;

    // Polling fallback: check call state every 2s when PhoneStateListener may be dead
    private static final Handler pollHandler = new Handler(Looper.getMainLooper());
    private static Runnable pollRunnable = null;
    private static final long POLL_INTERVAL_MS = 2_000;
    private static final long CALL_START_GUARD_MS = 15_000;
    private static final long NEXT_CALL_DELAY_MS = 2_000;
    private static final long NEXT_CALL_IDLE_STABLE_MS = 1_500;
    private static final long NEXT_CALL_RETRY_MS = 1_000;
    private static long pollStartMs = 0;
    private static int pollLastState = -1;

    // ================================================================
    //  PUBLIC ENTRY POINTS
    // ================================================================

    /**
     * Called by SafeSphereService.onStartCommand when the process restarts mid-sequence.
     *
     * Strategy:
     *  - If a call is currently RINGING or OFFHOOK → re-attach polling so we detect when it ends.
     *  - If the phone is IDLE → the call already ended while we were dead; place the next call now.
     */
    public static void resumeCallSequence(Context ctx) {
        int idx = Prefs.getCallSequenceIndex(ctx);
        if (idx < 0) {
            Log.d(TAG, "resumeCallSequence: no active sequence (index=-1), nothing to do");
            return;
        }

        currentNumbers = Prefs.getEmergencyNumbers(ctx);
        currentIndex   = idx;

        TelephonyManager tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
        int callState = (tm != null) ? tm.getCallState() : TelephonyManager.CALL_STATE_IDLE;
        String stateName = callState == TelephonyManager.CALL_STATE_IDLE ? "IDLE"
                : callState == TelephonyManager.CALL_STATE_RINGING ? "RINGING" : "OFFHOOK";

        Log.d(TAG, "resumeCallSequence() idx=" + idx + " callState=" + stateName);
        long startMs = Prefs.getCallStartTime(ctx);
        long elapsedMs = (startMs > 0) ? (System.currentTimeMillis() - startMs) : Long.MAX_VALUE;
        Log.d(TAG, "resumeCallSequence() elapsedMs=" + elapsedMs + " since call start");

        if (callState != TelephonyManager.CALL_STATE_IDLE) {
            // Call is still active — re-attach polling to detect when it ends
            Log.d(TAG, "Call still active – re-attaching polling for contact " + (idx + 1));
            registerCallStateListener(ctx);
            startCallPolling(ctx);
        } else {
            // Event-driven handling: no timer-based escalation.
            Log.d(TAG, "Phone IDLE on resume - waiting for call-end handling via listener/receiver and call-log check");
            registerCallStateListener(ctx);
            startCallPolling(ctx);
        }
    }
     public static void triggerEmergencyLive(Context ctx) {
        String caller = detectTriggerCaller();
        Log.d(TAG, "🚨 triggerEmergencyLive() called by " + caller);
        Log.i(TAG, FLOW + "_TRIGGER mode=LIVE");
        Log.i(TAG, FLOW + "_TRIGGER_CALLER " + caller);
        liveHandler.removeCallbacksAndMessages(null);
        liveSentCount = 0;

        // ── Analytics: emergency trigger event ──
        java.util.Map<String, Object> triggerPayload = new java.util.HashMap<>();
        triggerPayload.put("source", "LIVE");
        triggerPayload.put("caller", caller);
        String sid = Prefs.ensureSessionId(ctx);
        AnalyticsQueue.get(ctx).enqueue(EventType.TRIGGER_SOURCE, sid, triggerPayload);

        // Run on main thread — SmsManager on Android 12+ requires main thread
        new Handler(Looper.getMainLooper()).post(() -> sendEmergency(ctx.getApplicationContext(), true));
    }

    public static void triggerEmergencyCurrent(Context ctx) {
        Log.d(TAG, "triggerEmergencyCurrent()");
        Log.i(TAG, FLOW + "_TRIGGER mode=CURRENT");

        // ── Analytics: emergency trigger event ──
        java.util.Map<String, Object> triggerPayload = new java.util.HashMap<>();
        triggerPayload.put("source", "CURRENT");
        String sid = Prefs.ensureSessionId(ctx);
        AnalyticsQueue.get(ctx).enqueue(EventType.TRIGGER_SOURCE, sid, triggerPayload);

        liveHandler.removeCallbacksAndMessages(null);
        sendEmergency(ctx.getApplicationContext(), false);
    }

    private static String detectTriggerCaller() {
        try {
            StackTraceElement[] stack = Thread.currentThread().getStackTrace();
            for (StackTraceElement e : stack) {
                String cls = e.getClassName();
                if (cls == null) continue;
                if (cls.contains("EmergencyManager") || cls.equals("java.lang.Thread")) {
                    continue;
                }
                return cls + "#" + e.getMethodName();
            }
        } catch (Exception ignored) {}
        return "unknown";
    }

    static boolean isCallMonitoringActive(Context ctx, int sequenceIndex) {
        if (sequenceIndex < 0) {
            return false;
        }
        if (Prefs.getCallSequenceIndex(ctx) != sequenceIndex) {
            return false;
        }
        return currentListener != null || pollRunnable != null || pendingStartNextRetry != null;
    }

    // ================================================================
    //  CORE SEND
    // ================================================================

    private static void sendEmergency(Context ctx, boolean liveMode) {
        Log.d(TAG, "╔═══════════════════════════════════════════════════╗");
        Log.d(TAG, "║         sendEmergency() START                     ║");
        Log.d(TAG, "║         liveMode=" + liveMode + "                              ║");
        Log.d(TAG, "╚═══════════════════════════════════════════════════╝");
        
        String[] allNumbers = Prefs.getEmergencyNumbers(ctx);
        currentIndex   = 0;
        final String[] numbers = allNumbers;

        int batteryPercent = getBatteryPercent(ctx);
        EmergencyActionOptimizer.OptimizationResult plan =
            EmergencyActionOptimizer.planForEmergency(numbers, batteryPercent, liveMode);
        currentNumbers = plan.callNumbers;
        Log.d(TAG, "Optimizer: battery=" + batteryPercent
            + "% threshold=" + EmergencyActionOptimizer.LOW_BATTERY_THRESHOLD_PERCENT
            + "% applied=" + plan.optimizationApplied
            + " callsToAttempt=" + currentNumbers.length);
        Log.d(TAG, "Optimizer summary: " + plan.debugSummary);

        // Cancel any stale listener/retry from a previous emergency
        if (pendingStartNextRetry != null) {
            timeoutHandler.removeCallbacks(pendingStartNextRetry);
            pendingStartNextRetry = null;
        }
        stopCallPolling();
        if (currentListener != null) {
            try {
                TelephonyManager tmClean = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
                if (tmClean != null) tmClean.listen(currentListener, PhoneStateListener.LISTEN_NONE);
            } catch (Exception ignored) {}
            currentListener = null;
        }
        // Reset receiver state from any previous emergency
        Prefs.clearCallSequence(ctx);

        Log.d(TAG, "Emergency numbers retrieved:");
        Log.d(TAG, "  e1='" + numbers[0] + "'");
        Log.d(TAG, "  e2='" + numbers[1] + "'");
        Log.d(TAG, "  e3='" + numbers[2] + "'");

        // 1. Send SMS to all contacts — synchronously, before anything else
        // Do NOT use Handler.post here — if the process dies before the Handler fires, SMS is lost
        Log.d(TAG, "┌─────────────────────────────────────────────────┐");
        Log.d(TAG, "│ Step 1: Sending SMS to all contacts            │");
        Log.d(TAG, "└─────────────────────────────────────────────────┘");
        Log.i(TAG, FLOW + "_SMS_START");
        sendSmsWithBestLocation(ctx, numbers);
        Log.i(TAG, FLOW + "_SMS_DONE");
        // ── Analytics: SMS_SENT event ──
        String sessionId = Prefs.getSessionId(ctx);
        java.util.Map<String, Object> smsPayload = new java.util.HashMap<>();
        smsPayload.put("recipient_count", (int) java.util.Arrays.stream(numbers).filter(n -> n != null && !n.trim().isEmpty()).count());
        AnalyticsQueue.get(ctx).enqueue(EventType.SMS_SENT, sessionId, smsPayload);

        // 2. Start calling sequence (small delay so SMS dispatch starts first)
        Log.d(TAG, "┌─────────────────────────────────────────────────┐");
        Log.d(TAG, "│ Step 2: Starting call sequence                  │");
        Log.d(TAG, "└─────────────────────────────────────────────────┘");
        Log.i(TAG, FLOW + "_CALL_SEQUENCE_START");
        if (currentNumbers.length > 0) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> startNextCall(ctx), 300);
        } else {
            Log.d(TAG, "No valid call targets selected; finishing after SMS dispatch");
            notifySequenceComplete(ctx);
        }

        // 3. Schedule live location updates - DISABLED to prevent duplicate SMS
        // Initial SMS already contains location. Live update would send 3 more SMS after 60s.
        // if (liveMode) { scheduleLiveLocation(ctx, numbers); }
        
        Log.d(TAG, "╔═══════════════════════════════════════════════════╗");
        Log.d(TAG, "║         sendEmergency() COMPLETE                  ║");
        Log.d(TAG, "╚═══════════════════════════════════════════════════╝");
    }

    private static int getBatteryPercent(Context ctx) {
        try {
            Intent batteryStatus = ctx.registerReceiver(null,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (batteryStatus == null) {
                return 100;
            }
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            if (level <= -1 || scale <= 0) {
                return 100;
            }
            return (int) ((level * 100f) / scale);
        } catch (Exception e) {
            Log.w(TAG, "Battery percent unavailable, defaulting high", e);
            return 100;
        }
    }

    // ================================================================
    //  LOCATION + SMS
    // ================================================================

    private static void sendSmsWithBestLocation(Context ctx, String[] numbers) {
        boolean hasFine   = ActivityCompat.checkSelfPermission(ctx,
                Manifest.permission.ACCESS_FINE_LOCATION)   == PackageManager.PERMISSION_GRANTED;
        boolean hasCoarse = ActivityCompat.checkSelfPermission(ctx,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        if (!hasFine && !hasCoarse) {
            String unavailable = locationUnavailableText("this person's location permission is denied on this device");
            sendSmsToAll(ctx, numbers, buildMsg(unavailable, unavailable));
            return;
        }

        LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) {
            String unavailable = locationUnavailableText("this person's location service is unavailable on this device");
            sendSmsToAll(ctx, numbers, buildMsg(unavailable, unavailable));
            return;
        }

        boolean gpsEnabled = false;
        boolean networkEnabled = false;
        try {
            gpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch (Exception ignored) {}
        try {
            networkEnabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Exception ignored) {}

        Location best = getBestCachedLocation(lm);

        if (best != null) {
            rememberLastKnownLocation(ctx, best);
            long ageSeconds = (System.currentTimeMillis() - best.getTime()) / 1000;
            Log.d(TAG, "Cached location: " + best.getLatitude() + ","
                    + best.getLongitude() + " (age " + ageSeconds + "s)");
            String link = mapsLink(best);
            sendSmsToAll(ctx, numbers, buildMsg(link, link));
        } else {
            if (!gpsEnabled) {
                String currentUnavailable = currentLocationGpsOffText();
                String storedFallback = getStoredLastKnownLocationText(ctx);
                if (storedFallback != null) {
                    Log.d(TAG, "GPS is off - sending current-unavailable message with last updated location");
                    sendSmsToAll(ctx, numbers, buildMsg(currentUnavailable, storedFallback));
                } else {
                    Log.d(TAG, "GPS is off and no stored location exists");
                    sendSmsToAll(ctx, numbers,
                            buildMsg(currentUnavailable, locationUnavailableText("last updated location is not available yet")));
                }
                return;
            }

            String unavailable = locationUnavailableText(
                    networkEnabled
                            ? "this person's location could not be fetched right now"
                            : "this person's location services are turned off right now");
            Log.d(TAG, "No cached location with GPS on - sending unavailable-location message");
            sendSmsToAll(ctx, numbers, buildMsg(unavailable, unavailable));
        }
    }

    private static Location getBestCachedLocation(LocationManager lm) {
        Location best = null;
        for (String provider : new String[]{
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER}) {
            try {
                Location l = lm.getLastKnownLocation(provider);
                if (l != null && (best == null || l.getTime() > best.getTime())) {
                    best = l;
                }
            } catch (Exception ignored) {}
        }
        return best;
    }

    private static void requestFreshLocationAndUpdate(Context ctx, String[] numbers,
                                                       LocationManager lm) {
        boolean hasFine   = ActivityCompat.checkSelfPermission(ctx,
                Manifest.permission.ACCESS_FINE_LOCATION)   == PackageManager.PERMISSION_GRANTED;
        boolean hasCoarse = ActivityCompat.checkSelfPermission(ctx,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!hasFine && !hasCoarse) return;

        LocationListener listener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                rememberLastKnownLocation(ctx, location);
                String link = mapsLink(location);
                Log.d(TAG, "Fresh location received: " + link);
                sendSmsToAll(ctx, numbers, "📍 Location update: " + link);
                lm.removeUpdates(this);
            }
            public void onStatusChanged(String p, int s, Bundle b) {}
            public void onProviderEnabled(String p) {}
            public void onProviderDisabled(String p) {}
        };

        try {
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestSingleUpdate(LocationManager.NETWORK_PROVIDER,
                        listener, Looper.getMainLooper());
                Log.d(TAG, "Requested network location update");
            } else if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestSingleUpdate(LocationManager.GPS_PROVIDER,
                        listener, Looper.getMainLooper());
                Log.d(TAG, "Requested GPS location update");
            } else {
                Log.w(TAG, "No location provider enabled – location will not be sent");
            }
        } catch (Exception e) {
            Log.e(TAG, "requestFreshLocationAndUpdate failed", e);
        }
    }

    public static String buildCurrentLocationLink(Context ctx) {
        boolean hasFine   = ActivityCompat.checkSelfPermission(ctx,
                Manifest.permission.ACCESS_FINE_LOCATION)   == PackageManager.PERMISSION_GRANTED;
        boolean hasCoarse = ActivityCompat.checkSelfPermission(ctx,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!hasFine && !hasCoarse) return locationUnavailableText("this person's location permission is denied on this device");

        LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return locationUnavailableText("this person's location service is unavailable on this device");
        Location best = getBestCachedLocation(lm);
        if (best != null) {
            rememberLastKnownLocation(ctx, best);
            return mapsLink(best);
        }

        boolean gpsEnabled = false;
        boolean networkEnabled = false;
        try {
            gpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch (Exception ignored) {}
        try {
            networkEnabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Exception ignored) {}

        if (!gpsEnabled) {
            String storedFallback = getStoredLastKnownLocationText(ctx);
            if (storedFallback != null) {
                return currentLocationGpsOffText() + " " + storedFallback;
            }
            return locationUnavailableText("current location is not available because GPS is turned off and no last updated location is saved");
        }

        return locationUnavailableText(networkEnabled
                ? "this person's location could not be fetched right now"
                : "this person's location services are turned off right now");
    }

    public static String buildLiveLocationLink(Context ctx) { return buildCurrentLocationLink(ctx); }
    public static String buildLocationText(Context ctx)     { return buildCurrentLocationLink(ctx); }

    private static String mapsLink(Location loc) {
        return "https://maps.google.com/?q=" + loc.getLatitude() + "," + loc.getLongitude();
    }

    private static void rememberLastKnownLocation(Context ctx, Location location) {
        if (ctx == null || location == null) {
            return;
        }
        long timestampMs = location.getTime() > 0 ? location.getTime() : System.currentTimeMillis();
        Prefs.setLastKnownLocation(ctx, location.getLatitude(), location.getLongitude(), timestampMs);
    }

    private static String getStoredLastKnownLocationText(Context ctx) {
        double latitude = Prefs.getLastKnownLocationLat(ctx);
        double longitude = Prefs.getLastKnownLocationLng(ctx);
        long timestampMs = Prefs.getLastKnownLocationTime(ctx);

        if (Double.isNaN(latitude) || Double.isNaN(longitude) || timestampMs <= 0L) {
            return null;
        }

        long ageMinutes = Math.max(0L, (System.currentTimeMillis() - timestampMs) / 60_000L);
        String ageText = ageMinutes <= 1L ? "just now" : ageMinutes + " min ago";
        return "Last updated location (" + ageText + "): https://maps.google.com/?q="
                + latitude + "," + longitude;
    }

    private static String locationUnavailableText(String reason) {
        return "Location unavailable: " + reason;
    }

    private static String currentLocationGpsOffText() {
        return "Current location is not available because GPS is turned off on this device";
    }

    private static String buildMsg(String cur, String live) {
        return "🚨 Emergency Alert! This person may be in danger. " +
                "Please try to contact them immediately.\n" +
                "📍 Current Location: " + cur + "\n" +
                "🔴 Live Location: " + live;
    }

    // ================================================================
    //  SMS
    // ================================================================

    private static void sendSmsToAll(Context ctx, String[] numbers, String message) {
        if (numbers == null) {
            Log.e(TAG, "sendSmsToAll: numbers array is null");
            return;
        }
        Log.d(TAG, "sendSmsToAll: sending to " + numbers.length + " contacts synchronously");

        // Send synchronously — no Handler delays (delays cause missed SMS when app is closed)
        for (int i = 0; i < numbers.length; i++) {
            String num = numbers[i];
            if (num != null && !num.trim().isEmpty()) {
                Log.d(TAG, "  Contact " + (i + 1) + ": '" + num.trim() + "'");
                sendSms(ctx, num.trim(), message);
            } else {
                Log.d(TAG, "  Contact " + (i + 1) + " is empty – skipping");
            }
        }
    }

    private static void sendSms(Context ctx, String number, String msg) {
        if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "SMS permission denied");
            return;
        }
        try {
            SmsManager sms = null;
            // Prefer the user's default SMS subscription when available (dual-SIM safe).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                int subId = SubscriptionManager.getDefaultSmsSubscriptionId();
                if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                    sms = SmsManager.getSmsManagerForSubscriptionId(subId);
                }
            }
            if (sms == null) {
                sms = SmsManager.getDefault();
            }
            if (sms == null) {
                Log.e(TAG, "SmsManager is null for: " + number);
                return;
            }
            java.util.ArrayList<String> parts = sms.divideMessage(msg);
            sms.sendMultipartTextMessage(number, null, parts, null, null);
            Log.d(TAG, "✅ SMS sent to: " + number);
        } catch (Exception e) {
            Log.e(TAG, "Failed to send SMS to: " + number, e);
        }
    }

    // ================================================================
    //  SEQUENTIAL CALLING  (1 → 2 → 3)
    //
    //  Strategy: fire a MAX-priority full-screen notification whose
    //  content intent IS the call intent. Android will auto-launch it
    //  on the lock screen. Also attempt direct startActivity via
    //  CallActivity as a secondary path.
    // ================================================================

    /**
     * Called by PhoneStateReceiver when the process was killed and restarted mid-sequence.
     * Resumes the call sequence at the given index without re-sending SMS.
     */
    public static void callSingleContact(Context ctx, String number, int index) {
        currentNumbers = Prefs.getEmergencyNumbers(ctx);
        currentIndex   = index;
        Log.d(TAG, "callSingleContact() resumed at index=" + index + " number=" + number);
        Log.i(TAG, FLOW + "_CALL_RESUME contact=" + (index + 1));
        // Persist state so receiver can continue if we die again
        long callStartMs = System.currentTimeMillis();
        Prefs.setCallSequenceIndex(ctx, index);
        Prefs.setCallStartTime(ctx, callStartMs);
        playDialingTone();
        registerCallStateListener(ctx);
        // Keep polling fallback active for resumed calls as well.
        startCallPolling(ctx);
        // Wake service to keep monitoring robust in case process gets constrained.
        SafeSphereService.scheduleRestartBroadcast(ctx, 13_000L);

        // Always post full-screen notification first so MIUI can auto-launch call UI
        // even if direct activity launch is restricted in background.
        boolean placedDirectly = attemptDirectTelecomPlaceCall(ctx, number, index + 1);
        if (!placedDirectly) {
            fireCallViaFullScreenNotification(ctx, number, index + 1);
        }
        if (!placedDirectly && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Log.d(TAG, "Skipping direct CallActivity start on Android 14+; relying on BAL-approved PendingIntent path");
        } else if (!placedDirectly) {
            try {
                Intent i = new Intent(ctx, CallActivity.class);
                i.putExtra(CallActivity.EXTRA_NUMBER, number);
                i.putExtra(CallActivity.EXTRA_ACTION, CallActivity.ACTION_CALL);
                i.putExtra(CallActivity.EXTRA_ATTEMPT_ID, callStartMs);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_NO_USER_ACTION | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                ctx.startActivity(i);
                Log.d(TAG, "Direct CallActivity start requested (resumed) for contact " + (index + 1));
            } catch (Exception e) {
                Log.e(TAG, "CallActivity launch failed (resumed): " + e.getMessage());
                fireCallViaFullScreenNotification(ctx, number, index + 1);
            }
        }
    }

    private static void startNextCall(Context ctx) {
        Log.d(TAG, "═══════════════════════════════════════════════════");
        Log.d(TAG, "startNextCall() ENTRY - currentIndex=" + currentIndex + ", total=" + (currentNumbers != null ? currentNumbers.length : 0));

        // Never place a new emergency call while another call is active.
        if (isAnyCallActive(ctx)) {
            Log.w(TAG, "startNextCall blocked: phone is not IDLE; waiting before dialing next contact");
            scheduleStartNextRetry(ctx);
            Log.d(TAG, "═══════════════════════════════════════════════════");
            return;
        }

        if (pendingStartNextRetry != null) {
            timeoutHandler.removeCallbacks(pendingStartNextRetry);
            pendingStartNextRetry = null;
        }

        int persistedIndex = Prefs.getCallSequenceIndex(ctx);
        if (persistedIndex < 0 && currentNumbers != null && currentIndex > 0) {
            Log.d(TAG, "Sequence already cleared - ignoring stale startNextCall invocation");
            Log.d(TAG, "═══════════════════════════════════════════════════");
            return;
        }

        if (persistedIndex >= 0 && persistedIndex != currentIndex) {
            Log.d(TAG, "Aligning currentIndex with persisted index: " + persistedIndex);
            currentIndex = persistedIndex;
        }
        
        if (currentNumbers == null || currentIndex >= currentNumbers.length) {
            Log.d(TAG, "Call sequence complete (no more contacts)");
            Prefs.clearCallSequence(ctx);  // Clear persisted state
            notifySequenceComplete(ctx);
            Log.d(TAG, "═══════════════════════════════════════════════════");
            return;
        }

        String num = currentNumbers[currentIndex];
        Log.d(TAG, "Checking contact " + (currentIndex + 1) + ": '" + num + "'");
        
        if (num == null || num.trim().isEmpty()) {
            Log.d(TAG, "Empty number at index " + currentIndex + ", skipping to next");
            currentIndex++;
            startNextCall(ctx);
            return;
        }
        num = num.trim();

        if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.CALL_PHONE)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "CALL_PHONE permission denied - cannot make calls");
            Log.d(TAG, "═══════════════════════════════════════════════════");
            return;
        }

        Log.d(TAG, "📞 INITIATING CALL to contact " + (currentIndex + 1) + ": " + num);
        Log.i(TAG, FLOW + "_CALL_ATTEMPT contact=" + (currentIndex + 1) + " number=" + num);

        // ── Analytics: call attempt event ──
        String sessionId = Prefs.getSessionId(ctx);
        java.util.Map<String, Object> callPayload = new java.util.HashMap<>();
        callPayload.put("contact_index", currentIndex + 1);
        AnalyticsQueue.get(ctx).enqueue(EventType.CALL_ATTEMPT, sessionId, callPayload);
        playDialingTone();

        // Persist call sequence state so PhoneStateReceiver can resume if process is killed
        long callStartMs = System.currentTimeMillis();
        Prefs.setCallSequenceIndex(ctx, currentIndex);
        Prefs.setCallStartTime(ctx, callStartMs);

        // Register call state listener FIRST (before starting call)
        Log.d(TAG, "Step 1: Registering PhoneStateListener");
        registerCallStateListener(ctx);

        // Start polling as fallback (survives process death better than PhoneStateListener)
        Log.d(TAG, "Step 2: Starting call state polling (MIUI fallback)");
        startCallPolling(ctx);
        // Process-death-safe wake-up while call state monitoring is active.
        SafeSphereService.scheduleRestartBroadcast(ctx, 13_000L);

        // Always post full-screen notification as primary/backup trigger. This is critical
        // on MIUI where direct background activity starts are often blocked silently.
        Log.d(TAG, "Step 2c: Preparing call launch fallback");
        boolean placedDirectly = attemptDirectTelecomPlaceCall(ctx, num, currentIndex + 1);
        if (!placedDirectly) {
            fireCallViaFullScreenNotification(ctx, num, currentIndex + 1);
        }

        // PRIMARY: Launch CallActivity to place the call (bypasses Android 10+ background restriction)
        Log.d(TAG, "Step 3: Launching CallActivity to place call");
        if (placedDirectly) {
            Log.d(TAG, "Skipping CallActivity launch because TelecomManager.placeCall already started contact " + (currentIndex + 1));
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Log.d(TAG, "Skipping direct CallActivity start on Android 14+; relying on BAL-approved PendingIntent path");
        } else {
            try {
                Intent callActivityIntent = new Intent(ctx, CallActivity.class);
                callActivityIntent.putExtra(CallActivity.EXTRA_NUMBER, num);
                callActivityIntent.putExtra(CallActivity.EXTRA_ACTION, CallActivity.ACTION_CALL);
                callActivityIntent.putExtra(CallActivity.EXTRA_ATTEMPT_ID, callStartMs);
                callActivityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_NO_USER_ACTION
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                ctx.startActivity(callActivityIntent);
                Log.d(TAG, "Direct CallActivity start requested for contact " + (currentIndex + 1));
            } catch (Exception e) {
                Log.e(TAG, "❌ CallActivity launch failed: " + e.getMessage());
                // Full-screen fallback was already posted above.
                Log.d(TAG, "Full-screen fallback already posted; waiting for system to launch it");
            }
        }
        
        Log.d(TAG, "startNextCall() EXIT - Call initiated for contact " + (currentIndex + 1));
        Log.d(TAG, "═══════════════════════════════════════════════════");
    }

    private static boolean attemptDirectTelecomPlaceCall(Context ctx, String number, int contactNum) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false;
        }
        if (number == null || number.trim().isEmpty()) {
            return false;
        }
        if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.CALL_PHONE)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "CALL_PHONE permission denied - TelecomManager.placeCall unavailable");
            return false;
        }

        try {
            TelecomManager telecomManager = (TelecomManager) ctx.getSystemService(Context.TELECOM_SERVICE);
            if (telecomManager == null) {
                Log.w(TAG, "TelecomManager is null - cannot place direct call for contact " + contactNum);
                return false;
            }

            Bundle extras = new Bundle();
            extras.putBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, false);
            telecomManager.placeCall(Uri.parse("tel:" + number.trim()), extras);
            Log.d(TAG, "✅ TelecomManager.placeCall requested for contact " + contactNum + ": " + number);
            return true;
        } catch (SecurityException e) {
            Log.e(TAG, "TelecomManager.placeCall blocked for contact " + contactNum, e);
            return false;
        } catch (Exception e) {
            Log.e(TAG, "TelecomManager.placeCall failed for contact " + contactNum, e);
            return false;
        }
    }

    /**
     * Posts a MAX-priority full-screen notification whose full-screen intent
     * points to CallActivity. Android will auto-launch this on the lock screen
     * without requiring user interaction.
     * 
     * CRITICAL: This is the PRIMARY mechanism for calls when app is CLOSED/BACKGROUND.
     * The full-screen intent will auto-launch CallActivity which then initiates the call.
     * 
     * ISSUE 3 FIX: Added more aggressive flags to force activity launch on Xiaomi MIUI.
     */
    private static void fireCallViaFullScreenNotification(Context ctx, String number, int contactNum) {
        Log.d(TAG, "───────────────────────────────────────────────────");
        Log.d(TAG, "fireCallViaFullScreenNotification() for contact " + contactNum + ": " + number);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CALL_CHANNEL_ID, "Emergency Calls", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Emergency call notifications");
            ch.setBypassDnd(true);
            ch.enableVibration(true);
            ch.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);
            ch.setShowBadge(true);
            ctx.getSystemService(NotificationManager.class).createNotificationChannel(ch);
            Log.d(TAG, "Notification channel created/updated");
        }

        // Full-screen intent → CallActivity (auto-launches on lock screen)
        Intent callActivityIntent = new Intent(ctx, CallActivity.class);
        callActivityIntent.putExtra(CallActivity.EXTRA_NUMBER, number);
        callActivityIntent.putExtra(CallActivity.EXTRA_ACTION, CallActivity.ACTION_CALL);
        callActivityIntent.putExtra(CallActivity.EXTRA_ATTEMPT_ID, Prefs.getCallStartTime(ctx));
        callActivityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_NO_USER_ACTION
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent fullScreenPi = createCallActivityPendingIntent(
            ctx, contactNum + 200, callActivityIntent, piFlags);
        Log.d(TAG, "Full-screen PendingIntent created for CallActivity");

        // Tap action → direct call intent (fallback if full-screen doesn't auto-launch)
        Intent directCallIntent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + number));
        directCallIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent callPi = createCallActivityPendingIntent(ctx, contactNum, directCallIntent, piFlags);
        Log.d(TAG, "Direct call PendingIntent created");

        NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, CALL_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_call)
                .setContentTitle("🚨 Emergency Call – Contact " + contactNum)
                .setContentText("Calling " + number + " automatically…")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setAutoCancel(false)
                .setOngoing(true)
                .setFullScreenIntent(fullScreenPi, true)   // auto-launches on lock screen
                .setContentIntent(callPi)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setTimeoutAfter(15000);  // Auto-dismiss after 15 seconds

        try {
            NotificationManagerCompat.from(ctx).notify(CALL_NOTIF_BASE + contactNum, builder.build());
            Log.d(TAG, "✅ Full-screen call notification posted (ID: " + (CALL_NOTIF_BASE + contactNum) + ")");
            triggerFullScreenPendingIntent(ctx, fullScreenPi, contactNum);
            Log.d(TAG, "───────────────────────────────────────────────────");
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to show call notification", e);
            Log.d(TAG, "───────────────────────────────────────────────────");
        }
    }

    private static PendingIntent createCallActivityPendingIntent(
            Context ctx,
            int requestCode,
            Intent intent,
            int flags) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ActivityOptions options = ActivityOptions.makeBasic();
            options.setPendingIntentCreatorBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
            return PendingIntent.getActivity(ctx, requestCode, intent, flags, options.toBundle());
        }
        return PendingIntent.getActivity(ctx, requestCode, intent, flags);
    }

    private static void triggerFullScreenPendingIntent(Context ctx, PendingIntent fullScreenPi, int contactNum) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return;
        }
        try {
            ActivityOptions options = ActivityOptions.makeBasic();
            options.setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
            fullScreenPi.send(ctx, 0, null, null, null, null, options.toBundle());
            Log.d(TAG, "BAL-approved full-screen PendingIntent sent for contact " + contactNum);
        } catch (PendingIntent.CanceledException e) {
            Log.e(TAG, "Full-screen PendingIntent send canceled for contact " + contactNum, e);
        } catch (Exception e) {
            Log.e(TAG, "Failed to send BAL-approved full-screen PendingIntent for contact " + contactNum, e);
        }
    }

    private static void registerCallStateListener(Context ctx) {
        Log.d(TAG, "───────────────────────────────────────────────────");
        Log.d(TAG, "registerCallStateListener() ENTRY for contact " + (currentIndex + 1));
        
        // CRITICAL FIX: PhoneStateListener MUST be created on main thread with Looper
        // Background threads don't have a Looper, causing NullPointerException in Handler
        new Handler(Looper.getMainLooper()).post(() -> {
            TelephonyManager tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm == null) {
                Log.e(TAG, "TelephonyManager is null - cannot register call state listener");
                Log.d(TAG, "───────────────────────────────────────────────────");
                return;
            }

            if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "READ_PHONE_STATE denied – cannot detect call end");
                Log.d(TAG, "───────────────────────────────────────────────────");
                return;
            }

            // Unregister previous listener if any
            if (currentListener != null) {
                try {
                    tm.listen(currentListener, PhoneStateListener.LISTEN_NONE);
                    Log.d(TAG, "Previous PhoneStateListener unregistered");
                } catch (Exception e) {
                    Log.e(TAG, "Failed to unregister previous listener", e);
                }
            }

            Log.d(TAG, "Creating NEW PhoneStateListener for contact " + (currentIndex + 1));

            // Capture current index for this listener
            final int listenerContactIndex = currentIndex;
            final long listenerCallStartMs = Prefs.getCallStartTime(ctx);
            
            currentListener = new PhoneStateListener() {
            private boolean callWasOffhook = false;
            private boolean listenerActive = true;
            private boolean hasSeenNonIdleState = false;  // FIX: Track if we've seen RINGING or OFFHOOK
            private final int myContactIndex = listenerContactIndex;
            private final long myCallStartMs = listenerCallStartMs;

            @Override
            public void onCallStateChanged(int state, String phoneNumber) {
                if (!listenerActive) {
                    Log.d(TAG, "⚠️ Listener inactive - ignoring state change");
                    return;
                }

                if (Prefs.getCallSequenceIndex(ctx) != myContactIndex
                        || Prefs.getCallStartTime(ctx) != myCallStartMs) {
                    Log.d(TAG, "⚠️ Ignoring stale listener state change for contact " + (myContactIndex + 1));
                    listenerActive = false;
                    return;
                }
                
                String stateName = (state == TelephonyManager.CALL_STATE_IDLE) ? "IDLE" :
                                  (state == TelephonyManager.CALL_STATE_RINGING) ? "RINGING" :
                                  (state == TelephonyManager.CALL_STATE_OFFHOOK) ? "OFFHOOK" : "UNKNOWN";
                
                Log.d(TAG, "┌─────────────────────────────────────────────────");
                Log.d(TAG, "│ 📞 CALL STATE CHANGE for contact " + (myContactIndex + 1));
                Log.d(TAG, "│ State: " + stateName + " (" + state + ")");
                Log.d(TAG, "│ callWasOffhook: " + callWasOffhook);
                Log.d(TAG, "│ hasSeenNonIdleState: " + hasSeenNonIdleState);
                Log.d(TAG, "│ listenerActive: " + listenerActive);
                Log.d(TAG, "└─────────────────────────────────────────────────");
                
                // FIX: Track when we see RINGING or OFFHOOK (call is in progress)
                if (state == TelephonyManager.CALL_STATE_RINGING || state == TelephonyManager.CALL_STATE_OFFHOOK) {
                    hasSeenNonIdleState = true;
                    Log.d(TAG, "✅ Call in progress - hasSeenNonIdleState set to true");
                }
                
                if (state == TelephonyManager.CALL_STATE_OFFHOOK) {
                    // Wait for natural IDLE transition, then use call-log duration to decide.
                    callWasOffhook = true;
                    ctx.getSharedPreferences("safesphere_prefs", Context.MODE_PRIVATE)
                            .edit()
                            .putBoolean("rcv_seen_active", true)
                            .putBoolean("rcv_was_offhook", false)
                            .apply();
                    playAnsweredTone();
                    
                } else if (state == TelephonyManager.CALL_STATE_IDLE) {
                    // FIX: Only process IDLE if we've seen a non-IDLE state first
                    // This prevents reacting to the initial IDLE state before the call connects
                    if (!hasSeenNonIdleState) {
                        Log.d(TAG, "⚠️ IGNORING initial IDLE state (call hasn't started yet)");
                        return;
                    }
                    
                    Log.d(TAG, "📴 Call ENDED for contact " + (myContactIndex + 1) + ". callWasOffhook=" + callWasOffhook);
                    
                    // Deactivate and unregister this listener
                    listenerActive = false;
                    stopCallPolling();  // Stop polling — listener handled this transition
                    // Clear receiver state so PhoneStateReceiver doesn't also react to this IDLE
                    ctx.getSharedPreferences("safesphere_prefs", Context.MODE_PRIVATE)
                            .edit()
                            .putBoolean("rcv_seen_active", false)
                            .putBoolean("rcv_was_offhook", false)
                            .apply();
                    try {
                        tm.listen(this, PhoneStateListener.LISTEN_NONE);
                        currentListener = null;
                        Log.d(TAG, "✅ PhoneStateListener unregistered for contact " + (myContactIndex + 1));
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to unregister listener", e);
                    }
                    
                    final int endedContactIndex = myContactIndex;
                    final long endedCallStartMs = myCallStartMs;
                    evaluateAnsweredAfterNaturalEnd(ctx, endedContactIndex, endedCallStartMs,
                            "listener",
                            () -> {
                                Log.d(TAG, "✅ Call was answered and ended - completing sequence (no escalation)");
                                Log.i(TAG, FLOW + "_SEQUENCE_COMPLETE reason=answered contact=" + (endedContactIndex + 1));
                                Prefs.clearCallSequence(ctx);
                                notifySequenceComplete(ctx);
                            },
                            () -> {
                                Log.d(TAG, "╔═══════════════════════════════════════════════════╗");
                                Log.d(TAG, "║ ⏭️ Call attempt finished for contact " + (endedContactIndex + 1) + " - Moving next ║");
                                Log.d(TAG, "╚═══════════════════════════════════════════════════╝");
                                Log.i(TAG, FLOW + "_CALL_END_NEXT from=" + (endedContactIndex + 1)
                                        + " to=" + (endedContactIndex + 2));

                                // Skip one receiver IDLE reaction to avoid double-advance races.
                                ctx.getSharedPreferences("safesphere_prefs", Context.MODE_PRIVATE)
                                        .edit()
                                        .putBoolean("rcv_skip_next_idle", true)
                                        .apply();

                                currentIndex = endedContactIndex + 1;
                                Prefs.setCallSequenceIndex(ctx, currentIndex);
                                Prefs.setCallStartTime(ctx, -1L);
                                scheduleNextCallWhenIdleStable(ctx, currentIndex, -1L, "listener");
                            });
                }
            }
        };

            try {
                tm.listen(currentListener, PhoneStateListener.LISTEN_CALL_STATE);
                Log.d(TAG, "✅ PhoneStateListener registered successfully for contact " + (currentIndex + 1));

                // Check if call is already in progress (process may have been killed and restarted mid-call)
                int currentState = tm.getCallState();
                if (currentState != TelephonyManager.CALL_STATE_IDLE) {
                    String stateName = (currentState == TelephonyManager.CALL_STATE_RINGING) ? "RINGING" : "OFFHOOK";
                    Log.d(TAG, "⚡ Call already in progress (state=" + stateName + ") - notifying listener immediately");
                    currentListener.onCallStateChanged(currentState, "");
                }
                Log.d(TAG, "───────────────────────────────────────────────────");
            } catch (Exception e) {
                Log.e(TAG, "❌ Failed to register PhoneStateListener", e);
                Log.d(TAG, "───────────────────────────────────────────────────");
            }
        });
    }

    // ================================================================
    //  CALL STATE POLLING  (fallback when PhoneStateListener dies with process)
    //
    //  Polls TelephonyManager.getCallState() every 2 seconds.
    //  This runs on the main thread of the foreground service which MIUI
    //  keeps alive longer than in-process listeners.
    // ================================================================

    private static void startCallPolling(Context ctx) {
        stopCallPolling();
        pollStartMs = System.currentTimeMillis();
        pollLastState = -1;
        final int pollContactIndex = currentIndex;
        final long pollCallStartMs = Prefs.getCallStartTime(ctx);

        pollRunnable = new Runnable() {
            @Override
            public void run() {
                // Safety: stop if sequence was cleared externally
                if (Prefs.getCallSequenceIndex(ctx) < 0) {
                    Log.d(TAG, "Poll: sequence cleared externally – stopping poll");
                    stopCallPolling();
                    return;
                }
                if (Prefs.getCallSequenceIndex(ctx) != pollContactIndex
                        || Prefs.getCallStartTime(ctx) != pollCallStartMs) {
                    Log.d(TAG, "Poll: stale call attempt detected – stopping poll");
                    stopCallPolling();
                    return;
                }

                TelephonyManager tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
                int state = (tm != null) ? tm.getCallState() : TelephonyManager.CALL_STATE_IDLE;
                String stateName = state == TelephonyManager.CALL_STATE_IDLE ? "IDLE"
                        : state == TelephonyManager.CALL_STATE_RINGING ? "RINGING"
                        : "OFFHOOK";

                Log.d(TAG, "Poll[" + (pollContactIndex + 1) + "]: " + stateName
                        + " lastState=" + pollLastState);

                if (state == TelephonyManager.CALL_STATE_OFFHOOK) {
                    pollLastState = state;
                } else if (state == TelephonyManager.CALL_STATE_RINGING) {
                    pollLastState = state;
                } else if (state == TelephonyManager.CALL_STATE_IDLE) {
                    if (pollLastState == TelephonyManager.CALL_STATE_OFFHOOK
                            || pollLastState == TelephonyManager.CALL_STATE_RINGING) {
                        stopCallPolling();
                        final int endedContactIndex = pollContactIndex;
                        final long endedCallStartMs = pollCallStartMs;
                        evaluateAnsweredAfterNaturalEnd(ctx, endedContactIndex, endedCallStartMs,
                                "poll",
                                () -> {
                                    Log.d(TAG, "Poll: call ANSWERED then ended – sequence complete");
                                    Log.i(TAG, FLOW + "_SEQUENCE_COMPLETE reason=poll_answered contact=" + (endedContactIndex + 1));
                                    Prefs.clearCallSequence(ctx);
                                    notifySequenceComplete(ctx);
                                },
                                () -> {
                                    Log.d(TAG, "Poll: OFFHOOK ended without answered evidence – treating as no answer");
                                    Log.i(TAG, FLOW + "_POLL_END_NEXT from=" + (endedContactIndex + 1)
                                            + " to=" + (endedContactIndex + 2));

                                    if (isAnyCallActive(ctx)) {
                                        Log.w(TAG, "Poll next blocked: another call is still active");
                                        currentIndex = endedContactIndex + 1;
                                        Prefs.setCallSequenceIndex(ctx, currentIndex);
                                        Prefs.setCallStartTime(ctx, -1L);
                                        scheduleNextCallWhenIdleStable(ctx, currentIndex, -1L, "poll-blocked");
                                        return;
                                    }

                                    currentIndex = endedContactIndex + 1;
                                    Prefs.setCallSequenceIndex(ctx, currentIndex);
                                    Prefs.setCallStartTime(ctx, -1L);
                                    scheduleNextCallWhenIdleStable(ctx, currentIndex, -1L, "poll-end");
                                });
                        return;
                    } else if (pollLastState == -1) {
                        long elapsedMs = System.currentTimeMillis() - pollStartMs;
                        if (elapsedMs >= CALL_START_GUARD_MS) {
                            // No transition out of IDLE means call likely never started.
                            Log.d(TAG, "Poll: call did not leave IDLE for " + elapsedMs
                                    + "ms on contact " + (pollContactIndex + 1)
                                    + " - moving to next contact");
                            Log.i(TAG, FLOW + "_POLL_NO_START_NEXT from=" + (pollContactIndex + 1)
                                    + " to=" + (pollContactIndex + 2));

                            if (isAnyCallActive(ctx)) {
                                Log.w(TAG, "Poll no-start next blocked: call became active");
                                pollHandler.postDelayed(this, POLL_INTERVAL_MS);
                                return;
                            }

                            stopCallPolling();
                            currentIndex = pollContactIndex + 1;
                            Prefs.setCallSequenceIndex(ctx, currentIndex);
                            Prefs.setCallStartTime(ctx, -1L);
                            scheduleNextCallWhenIdleStable(ctx, currentIndex, -1L, "poll-no-start");
                            return;
                        }

                        Log.d(TAG, "Poll: waiting for call-state transition before deciding next contact");
                    }
                }

                pollHandler.postDelayed(this, POLL_INTERVAL_MS);
            }
        };

        pollHandler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
        Log.d(TAG, "📡 Call state polling started for contact " + (currentIndex + 1));
    }

    static boolean isAnyCallActive(Context ctx) {
        boolean telephonyActive = false;
        try {
            TelephonyManager tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
            telephonyActive = tm != null && tm.getCallState() != TelephonyManager.CALL_STATE_IDLE;
        } catch (Exception e) {
            Log.w(TAG, "Unable to read TelephonyManager call state", e);
        }

        boolean telecomActive = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                TelecomManager telecomManager = (TelecomManager) ctx.getSystemService(Context.TELECOM_SERVICE);
                telecomActive = telecomManager != null && telecomManager.isInCall();
            } catch (SecurityException se) {
                Log.w(TAG, "TelecomManager.isInCall blocked", se);
            } catch (Exception e) {
                Log.w(TAG, "Unable to read TelecomManager isInCall", e);
            }
        }

        return telephonyActive || telecomActive;
    }

    private static boolean isTelephonyIdle(Context ctx) {
        try {
            TelephonyManager tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
            return tm == null || tm.getCallState() == TelephonyManager.CALL_STATE_IDLE;
        } catch (Exception e) {
            Log.w(TAG, "Unable to read TelephonyManager idle state", e);
            return false;
        }
    }

    private static void scheduleNextCallWhenIdleStable(
            Context ctx,
            int expectedIndex,
            long expectedStartMs,
            String source) {
        Handler handler = new Handler(Looper.getMainLooper());
        Runnable[] waitRef = new Runnable[1];

        waitRef[0] = new Runnable() {
            @Override
            public void run() {
                if (Prefs.getCallSequenceIndex(ctx) != expectedIndex
                        || Prefs.getCallStartTime(ctx) != expectedStartMs) {
                    Log.d(TAG, "Skipping stale " + source + " next-call callback");
                    return;
                }

                if (isAnyCallActive(ctx) || !isTelephonyIdle(ctx)) {
                    Log.d(TAG, source + ": waiting for idle before next contact");
                    handler.postDelayed(waitRef[0], NEXT_CALL_RETRY_MS);
                    return;
                }

                handler.postDelayed(() -> {
                    if (Prefs.getCallSequenceIndex(ctx) != expectedIndex
                            || Prefs.getCallStartTime(ctx) != expectedStartMs) {
                        Log.d(TAG, "Skipping stale " + source + " stable-idle callback");
                        return;
                    }

                    if (isAnyCallActive(ctx) || !isTelephonyIdle(ctx)) {
                        Log.d(TAG, source + ": idle was not stable, retrying");
                        handler.postDelayed(waitRef[0], NEXT_CALL_RETRY_MS);
                        return;
                    }

                    Log.d(TAG, source + ": idle stable for " + NEXT_CALL_IDLE_STABLE_MS
                            + "ms - proceeding to next contact");
                    startNextCall(ctx);
                }, NEXT_CALL_IDLE_STABLE_MS);
            }
        };

        handler.postDelayed(waitRef[0], NEXT_CALL_DELAY_MS);
    }

    private static void scheduleStartNextRetry(Context ctx) {
        if (pendingStartNextRetry != null) {
            Log.d(TAG, "startNextCall retry already scheduled");
            return;
        }
        pendingStartNextRetry = () -> {
            pendingStartNextRetry = null;
            if (Prefs.getCallSequenceIndex(ctx) < 0) {
                Log.d(TAG, "startNextCall retry canceled: call sequence already cleared");
                return;
            }
            startNextCall(ctx);
        };
        timeoutHandler.postDelayed(pendingStartNextRetry, 2_000);
        Log.d(TAG, "Scheduled startNextCall retry in 2000ms while waiting for IDLE");
    }

    private static void stopCallPolling() {
        if (pollRunnable != null) {
            pollHandler.removeCallbacks(pollRunnable);
            pollRunnable = null;
            Log.d(TAG, "📡 Call state polling stopped");
        }
    }

    // ================================================================
    //  TONES
    // ================================================================

    private static void playDialingTone() {
        try {
            if (toneGen != null) toneGen.release();
            toneGen = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80);
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 400);
        } catch (Exception e) { Log.e(TAG, "Dialing tone error", e); }
    }

    private static void playAnsweredTone() {
        try {
            if (toneGen != null) toneGen.release();
            toneGen = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80);
            toneGen.startTone(ToneGenerator.TONE_PROP_ACK, 600);
        } catch (Exception e) { Log.e(TAG, "Answered tone error", e); }
    }

    static boolean wasOutgoingCallAnswered(Context ctx, String expectedNumber, long startedAfterMs) {
        if (expectedNumber == null || expectedNumber.trim().isEmpty()) {
            return false;
        }
        if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALL_LOG)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "READ_CALL_LOG not granted - cannot verify answered status");
            return false;
        }

        Cursor cursor = null;
        try {
            cursor = ctx.getContentResolver().query(
                    CallLog.Calls.CONTENT_URI,
                    new String[] {
                            CallLog.Calls.NUMBER,
                            CallLog.Calls.DURATION,
                            CallLog.Calls.DATE,
                            CallLog.Calls.TYPE
                    },
                    null,
                    null,
                    CallLog.Calls.DATE + " DESC"
            );
            if (cursor == null) {
                return false;
            }

            int scanned = 0;
            while (cursor.moveToNext() && scanned < 10) {
                scanned++;
                String number = cursor.getString(0);
                long durationSec = cursor.getLong(1);
                long dateMs = cursor.getLong(2);
                int type = cursor.getInt(3);

                if (type != CallLog.Calls.OUTGOING_TYPE) {
                    continue;
                }
                if (startedAfterMs > 0 && dateMs + 1_000L < startedAfterMs) {
                    break;
                }
                if (!numbersMatch(number, expectedNumber)) {
                    continue;
                }

                boolean answered = durationSec > 0;
                Log.d(TAG, "CallLog match for " + expectedNumber + ": durationSec=" + durationSec + " answered=" + answered);
                return answered;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed reading call log for answered-state detection", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return false;
    }

    private static boolean hasLiveAnsweredEvidence(Context ctx, String expectedNumber, long startedAfterMs) {
        boolean answeredInLog = wasOutgoingCallAnswered(ctx, expectedNumber, startedAfterMs);
        Log.d(TAG, "Live answer check: answeredInLog=" + answeredInLog
                + " startedAfterMs=" + startedAfterMs);
        return answeredInLog;
    }

    private static void evaluateAnsweredAfterNaturalEnd(
            Context ctx,
            int contactIndex,
            long callStartMs,
            String source,
            Runnable onAnswered,
            Runnable onUnanswered) {
        final String expectedNumber = currentNumbers != null && contactIndex < currentNumbers.length
                ? currentNumbers[contactIndex] : null;
        Log.d(TAG, "Call-end decision scheduled for contact " + (contactIndex + 1)
                + " (source=" + source + ") after " + CALLLOG_END_RECHECK_DELAY_MS + "ms");
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (Prefs.getCallSequenceIndex(ctx) != contactIndex
                    || Prefs.getCallStartTime(ctx) != callStartMs) {
                Log.d(TAG, "Skipping stale " + source + " post-end answer check for contact " + (contactIndex + 1));
                return;
            }

            boolean answered = wasOutgoingCallAnswered(ctx, expectedNumber, callStartMs);
            if (answered) {
                onAnswered.run();
                return;
            }

            onUnanswered.run();
        }, CALLLOG_END_RECHECK_DELAY_MS);
    }

    private static boolean numbersMatch(String first, String second) {
        if (first == null || second == null) {
            return false;
        }
        if (PhoneNumberUtils.compare(first, second)) {
            return true;
        }

        String normFirst = first.replaceAll("\\D", "");
        String normSecond = second.replaceAll("\\D", "");
        if (normFirst.length() > 10) {
            normFirst = normFirst.substring(normFirst.length() - 10);
        }
        if (normSecond.length() > 10) {
            normSecond = normSecond.substring(normSecond.length() - 10);
        }
        return normFirst.equals(normSecond);
    }

    // ================================================================
    //  SEQUENCE COMPLETE — notify SafeSphereService to restart Vosk
    // ================================================================

    private static void notifySequenceComplete(Context ctx) {
        if (pendingStartNextRetry != null) {
            timeoutHandler.removeCallbacks(pendingStartNextRetry);
            pendingStartNextRetry = null;
        }
        stopCallPolling();
        if (currentListener != null) {
            try {
                TelephonyManager tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
                if (tm != null) {
                    tm.listen(currentListener, PhoneStateListener.LISTEN_NONE);
                }
            } catch (Exception ignored) {}
            currentListener = null;
        }
        Log.d(TAG, "📡 Broadcasting EMERGENCY_SEQUENCE_COMPLETE");
        Log.i(TAG, FLOW + "_BROADCAST_SEQUENCE_COMPLETE");
        try {
            ctx.sendBroadcast(new Intent(SafeSphereService.ACTION_SEQUENCE_COMPLETE));
        } catch (Exception e) {
            Log.e(TAG, "Failed to broadcast sequence complete", e);
        }
        ensureServiceRunning(ctx);
    }

    /**
     * Ensures SafeSphereService is alive so the keyword listener restarts automatically
     * after the call sequence completes, without requiring the user to open the app.
     */
    private static void ensureServiceRunning(Context ctx) {
        try {
            boolean canRunProtection = Prefs.isProtectionEnabled(ctx)
                    && ActivityCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED;
            if (!canRunProtection) {
                Log.d(TAG, "Skipping service wake: protection off or RECORD_AUDIO missing");
                return;
            }
            Intent serviceIntent = new Intent(ctx, SafeSphereService.class);
            ContextCompat.startForegroundService(ctx, serviceIntent);
            Log.d(TAG, "✅ Requested SafeSphereService start for mic listener recovery");
            Log.i(TAG, FLOW + "_SERVICE_WAKE_REQUESTED");
        } catch (Exception e) {
            Log.e(TAG, "Failed to ensure SafeSphereService is running", e);
        }
    }

    // ================================================================
    //  LIVE LOCATION LOOP  (5 × 60s)
    // ================================================================

    public static void scheduleLiveLocation(Context ctx, String[] numbers) {
        Log.d(TAG, "Scheduling " + LIVE_MAX_MESSAGES + " live location update(s) (changed from 5 to 1)");
        liveHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (liveSentCount >= LIVE_MAX_MESSAGES) {
                    Log.d(TAG, "Live location updates complete (" + liveSentCount + "/" + LIVE_MAX_MESSAGES + ")");
                    return;
                }
                liveSentCount++;
                final int n = liveSentCount;
                String link = buildCurrentLocationLink(ctx);
                String msg  = "🔴 Live location update " + n + "/" + LIVE_MAX_MESSAGES + ": " + link;
                Log.d(TAG, "Sending live location update " + n + "/" + LIVE_MAX_MESSAGES);
                sendSmsToAll(ctx, numbers, msg);
                if (liveSentCount < LIVE_MAX_MESSAGES) {
                    liveHandler.postDelayed(this, LIVE_INTERVAL_MS);
                } else {
                    Log.d(TAG, "All live location updates sent (" + LIVE_MAX_MESSAGES + " total)");
                }
            }
        }, LIVE_INTERVAL_MS);
    }

    public static void scheduleLiveLocation(Context ctx) {
        scheduleLiveLocation(ctx, currentNumbers);
    }
}
