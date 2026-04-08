package com.example.safesphere;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.content.res.AssetManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.example.safesphere.analytics.AnalyticsQueue;
import com.example.safesphere.analytics.EventType;
import com.example.safesphere.network.SupabaseClient;

import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class SafeSphereService extends Service implements ShakeDetector.OnShakeListener {

    private static final String TAG        = "SAFESPHERE_SVC";
    private static final int    NOTIF_ID   = 1001;
    private static final String CHANNEL_ID = "safesphere_channel";

    /** Broadcast sent by EmergencyManager when the call sequence finishes */
    static final String ACTION_SEQUENCE_COMPLETE = "com.example.safesphere.EMERGENCY_SEQUENCE_COMPLETE";

    private static final int  SAMPLE_RATE  = 16000;
    private static final int  BUFFER_MULT  = 4;
    private static final long BG_LOCATION_REFRESH_MS = 3 * 60 * 1000L;
    private static final String ADMIN_MESSAGE_CHANNEL_ID = "safesphere_messages";
    private static final long ADMIN_MESSAGE_POLL_MS = 5 * 60 * 1000L;

    private PowerManager.WakeLock wakeLock;
    private SensorManager  sensorManager;
    private ShakeDetector  shakeDetector;
    private Model          voskModel;
    private Recognizer     recognizer;
    private AudioRecord    audioRecord;
    private final Handler  mainHandler = new Handler(Looper.getMainLooper());

    private Thread           audioThread;
    private volatile boolean listening     = false;
    private volatile boolean voskReady     = false;
    private volatile boolean initInProgress = false;
    private boolean locationRefreshScheduled = false;
    private boolean adminMessagePollScheduled = false;

    private long lastTriggerTime = 0;
    private static final long DEBOUNCE_MS = 3_000;
    // Receives the "sequence complete" broadcast from EmergencyManager
    private final BroadcastReceiver sequenceCompleteReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "📡 EMERGENCY_SEQUENCE_COMPLETE received – restarting Vosk immediately");
            restartVoskNow();
        }
    };

    private final Runnable backgroundLocationRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (!Prefs.isProtectionEnabled(getApplicationContext())) {
                locationRefreshScheduled = false;
                return;
            }
            requestSingleBackgroundLocationUpdate();
            mainHandler.postDelayed(this, BG_LOCATION_REFRESH_MS);
        }
    };

    private final Runnable adminMessagePollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!Prefs.isProtectionEnabled(getApplicationContext())) {
                adminMessagePollScheduled = false;
                return;
            }
            pollAdminMessagesFromSupabase();
            mainHandler.postDelayed(this, ADMIN_MESSAGE_POLL_MS);
        }
    };

    // ================================================================
    //  LIFECYCLE
    // ================================================================

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate()");

        try {
            startForegroundWithNotification();
        } catch (RuntimeException e) {
            // Android 12+ may reject foreground start from some background contexts.
            Log.e(TAG, "startForeground failed in onCreate, stopping service to avoid process crash", e);
            stopSelf();
            return;
        }

        if (!hasRecordAudioPermission()) {
            Log.e(TAG, "No RECORD_AUDIO permission – stopping");
            stopSelf();
            return;
        }

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SafeSphere::WakeLock");
            wakeLock.acquire();
        }

        setupShakeDetector();

        // Listen for call-sequence-complete so we can restart Vosk immediately
        IntentFilter filter = new IntentFilter(ACTION_SEQUENCE_COMPLETE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(sequenceCompleteReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(sequenceCompleteReceiver, filter);
        }

        // Schedule AlarmManager watchdog — fires every 60s to ensure service stays alive
        scheduleWatchdog();

        // Battery-friendly refresh so emergency fallback location stays recent.
        startBackgroundLocationRefresh();
        startAdminMessagePolling();
        captureFirstLocationOnStartup();

        Log.d(TAG, "Keyword in prefs: '" + Prefs.getKeyword(this) + "'");
        initVosk();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand() voskReady=" + voskReady + " listening=" + listening
                + " initInProgress=" + initInProgress);

        if (!hasRecordAudioPermission()) {
            Log.w(TAG, "onStartCommand: RECORD_AUDIO missing, stopping safely");
            stopSelf();
            return START_NOT_STICKY;
        }

        final Context ctx = getApplicationContext();

        if (Prefs.isProtectionEnabled(ctx)) {
            startAdminMessagePolling();
        } else {
            stopAdminMessagePolling();
        }

        // Check if we were killed mid-sequence and need to resume
        int seqIndex = Prefs.getCallSequenceIndex(ctx);
        if (seqIndex >= 0) {
            if (EmergencyManager.isCallMonitoringActive(ctx, seqIndex)) {
                Log.d(TAG, "onStartCommand: call sequence already monitored in-process at index="
                        + seqIndex + " – skipping duplicate resume");
                return START_STICKY;
            }
            Log.d(TAG, "onStartCommand: call sequence in progress at index=" + seqIndex
                    + " – resuming after 2s stabilisation delay");
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                // Re-check: sequence may have been cleared by PhoneStateReceiver in the meantime
                int idx = Prefs.getCallSequenceIndex(ctx);
                if (idx >= 0) {
                    Log.d(TAG, "Resuming call sequence at index=" + idx);
                    EmergencyManager.resumeCallSequence(ctx);
                } else {
                    Log.d(TAG, "Sequence already cleared by receiver – skipping resume");
                }
            }, 2_000);
            // Never restart mic listener while emergency call sequence is active.
            // It will be resumed by ACTION_SEQUENCE_COMPLETE.
            return START_STICKY;
        }

        // Re-arm the 60s watchdog every time onStartCommand is called so the rolling
        // alarm chains indefinitely as long as protection is enabled.
        if (Prefs.isProtectionEnabled(ctx)) {
            scheduleWatchdog();
            startBackgroundLocationRefresh();
        } else {
            stopBackgroundLocationRefresh();
        }

        // Vosk startup / restart logic
        String keyword = Prefs.getKeyword(ctx).toLowerCase().trim();
        if (!keyword.isEmpty() && !voskReady && !listening && !initInProgress) {
            initVosk();
        } else if (!keyword.isEmpty() && voskReady && !listening) {
            // Vosk model loaded but audio loop died — restart immediately.
            Log.d(TAG, "onStartCommand: Vosk ready but not listening – restarting audio loop immediately");
            restartVoskNow();
        } else if (keyword.isEmpty()) {
            Log.w(TAG, "Keyword not set yet – service waiting. Set keyword in Profile.");
        }
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        int seqIndex = Prefs.getCallSequenceIndex(getApplicationContext());
        if (seqIndex >= 0) {
            Log.d(TAG, "onTaskRemoved during active emergency call sequence at index=" + seqIndex
                    + " – skipping immediate service restart");
            super.onTaskRemoved(rootIntent);
            return;
        }
        if (Prefs.isProtectionEnabled(getApplicationContext())) {
            Log.d(TAG, "onTaskRemoved – scheduling restart via ServiceRestartReceiver");
            scheduleRestartBroadcast(getApplicationContext());
        } else {
            Log.d(TAG, "onTaskRemoved: protection disabled – not restarting");
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy()");
        // Re-schedule restart so the service comes back after being destroyed.
        // Only reschedule if protection is still enabled (user hasn't deliberately turned it off).
        if (Prefs.getCallSequenceIndex(getApplicationContext()) < 0) {
            if (Prefs.isProtectionEnabled(getApplicationContext())) {
                if (hasRecordAudioPermission()) {
                    scheduleRestartBroadcast(getApplicationContext());
                } else {
                    Log.d(TAG, "onDestroy: RECORD_AUDIO missing – skipping restart");
                }
            } else {
                Log.d(TAG, "onDestroy: protection disabled – skipping restart");
            }
        } else {
            Log.d(TAG, "onDestroy during active emergency call sequence – skipping immediate restart alarm");
        }
        try { unregisterReceiver(sequenceCompleteReceiver); } catch (Exception ignored) {}
        stopBackgroundLocationRefresh();
        stopAdminMessagePolling();
        stopListening();
        if (sensorManager != null && shakeDetector != null)
            sensorManager.unregisterListener(shakeDetector);
        closeVosk();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
    }

    @Nullable @Override
    public IBinder onBind(Intent intent) { return null; }

    // ================================================================
    //  FOREGROUND NOTIFICATION
    // ================================================================

    private void startForegroundWithNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "SafeSphere Listener", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }

        PendingIntent pi = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);

        Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("SafeSphere active")
                .setContentText("Listening for keyword & shake")
                .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ strictly requires the corresponding runtime permission to be granted
            // before including its service type in startForeground(). If location isn't granted
            // yet (e.g. first launch), omit LOCATION type to avoid SecurityException crash.
            boolean hasLocation =
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                            == PackageManager.PERMISSION_GRANTED
                    || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                            == PackageManager.PERMISSION_GRANTED;
            int serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
            if (hasLocation) {
                serviceType |= ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION;
            }
            try {
                startForeground(NOTIF_ID, n, serviceType);
            } catch (SecurityException e) {
                Log.e(TAG, "startForeground with type failed, retrying without type: " + e.getMessage());
                startForeground(NOTIF_ID, n);
            }
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    private boolean hasRecordAudioPermission() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void startBackgroundLocationRefresh() {
        if (locationRefreshScheduled) return;
        locationRefreshScheduled = true;
        mainHandler.removeCallbacks(backgroundLocationRefreshRunnable);
        mainHandler.postDelayed(backgroundLocationRefreshRunnable, 8_000L);
    }

    private void stopBackgroundLocationRefresh() {
        locationRefreshScheduled = false;
        mainHandler.removeCallbacks(backgroundLocationRefreshRunnable);
    }

    private void requestSingleBackgroundLocationUpdate() {
        boolean hasFine = ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean hasCoarse = ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        if (!hasFine && !hasCoarse) {
            Log.d(TAG, "Skipping location refresh: no permission");
            return;
        }

        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (lm == null) return;

        LocationListener listener = new LocationListener() {
            @Override
            public void onLocationChanged(android.location.Location location) {
                if (location == null) return;
                long timestampMs = location.getTime() > 0
                        ? location.getTime()
                        : System.currentTimeMillis();
                double lat = location.getLatitude();
                double lng = location.getLongitude();

                // Always save locally first
                Prefs.setLastKnownLocation(
                        getApplicationContext(), lat, lng, timestampMs);
                Prefs.setFirstLocationCaptured(getApplicationContext(), true);
                Log.d(TAG, "Location updated locally: " + lat + "," + lng);

                // Sync to Supabase only if online
                android.net.ConnectivityManager cm =
                        (android.net.ConnectivityManager)
                        getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
                android.net.NetworkInfo ni =
                        cm != null ? cm.getActiveNetworkInfo() : null;
                boolean isOnline = ni != null && ni.isConnected();

                if (isOnline) {
                    String userId = Prefs.getSupabaseUserId(getApplicationContext());
                    if (userId != null && !userId.isEmpty()) {
                        SupabaseClient client =
                                SupabaseClient.getInstance(getApplicationContext());
                        client.updateUserLocation(userId, lat, lng);
                        Prefs.setLastSyncedLocation(
                                getApplicationContext(), lat, lng, timestampMs);
                        Log.d(TAG, "Location synced to Supabase");
                    }
                } else {
                    Log.d(TAG, "Offline - location saved locally only");
                }

                try { lm.removeUpdates(this); } catch (Exception ignored) {}
            }
        };

        try {
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestSingleUpdate(
                        LocationManager.NETWORK_PROVIDER, listener,
                        Looper.getMainLooper());
            } else if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestSingleUpdate(
                        LocationManager.GPS_PROVIDER, listener,
                        Looper.getMainLooper());
            } else {
                Log.d(TAG, "No location provider enabled - skipping refresh");
            }
        } catch (Exception e) {
            Log.e(TAG, "requestSingleBackgroundLocationUpdate failed", e);
        }
    }

    private void captureFirstLocationOnStartup() {
        if (Prefs.isFirstLocationCaptured(getApplicationContext())) return;
        Log.d(TAG, "First location capture on startup");
        requestSingleBackgroundLocationUpdate();
    }

    private void startAdminMessagePolling() {
        if (adminMessagePollScheduled) return;
        adminMessagePollScheduled = true;
        mainHandler.removeCallbacks(adminMessagePollRunnable);
        mainHandler.postDelayed(adminMessagePollRunnable, 10_000L);
        pollAdminMessagesFromSupabase();
        syncPendingProfileIfNeeded();
    }

    private void stopAdminMessagePolling() {
        adminMessagePollScheduled = false;
        mainHandler.removeCallbacks(adminMessagePollRunnable);
    }

    private void pollAdminMessagesFromSupabase() {
        final Context ctx = getApplicationContext();
        final String userId = Prefs.getSupabaseUserId(ctx);
        if (userId == null || userId.trim().isEmpty()) {
            return;
        }

        new Thread(() -> {
            try {
                SupabaseClient client = SupabaseClient.getInstance(ctx);
                java.util.List<SupabaseClient.PendingMessageData> pending =
                        client.fetchPendingMessages(userId, 20);

                for (SupabaseClient.PendingMessageData pm : pending) {
                    if (pm == null || pm.id == null || pm.messageId == null) continue;

                    SupabaseClient.AdminMessageData msg = client.fetchAdminMessageById(pm.messageId);
                    if (msg == null || msg.body == null || msg.body.trim().isEmpty()) {
                        continue;
                    }

                    postAdminMessageNotification(msg, pm.id);
                    Prefs.addPendingAdminMessage(ctx, msg.subject, msg.body, msg.isCritical);
                    AnalyticsQueue.get(ctx).enqueue(EventType.ADMIN_MSG_RECEIVED, null);

                    SupabaseClient.SupabaseResponse mark = client.markPendingMessageDelivered(pm.id);
                    if (!mark.success) {
                        Log.w(TAG, "Failed to mark pending message delivered id=" + pm.id + " reason=" + mark.message);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Admin message poll failed", e);
            }
        }, "admin-message-poll").start();
    }

    private void syncPendingProfileIfNeeded() {
        final Context ctx = getApplicationContext();
        if (!Prefs.isProfileSyncPending(ctx)) return;

        new Thread(() -> {
            try {
                // Check connectivity
                android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                        ctx.getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
                android.net.NetworkInfo ni = cm.getActiveNetworkInfo();
                if (ni == null || !ni.isConnected()) return;

                String userId = Prefs.getSupabaseUserId(ctx);
                if (userId == null || userId.isEmpty()) return;

                String name    = Prefs.getPendingProfileName(ctx);
                String keyword = Prefs.getPendingProfileKeyword(ctx);
                String e1      = Prefs.getPendingProfileE1(ctx);
                String e2      = Prefs.getPendingProfileE2(ctx);
                String e3      = Prefs.getPendingProfileE3(ctx);

                if (name == null && keyword == null) return;

                org.json.JSONObject patch = new org.json.JSONObject();
                if (name != null)    patch.put("display_name", name);
                if (keyword != null) patch.put("keyword", keyword.toLowerCase().trim());
                if (e1 != null)      patch.put("emergency_contact_1", e1);
                if (e2 != null)      patch.put("emergency_contact_2", e2);
                if (e3 != null)      patch.put("emergency_contact_3", e3);
                patch.put("updated_at",
                        com.example.safesphere.network.SupabaseClient.toIso8601(
                                System.currentTimeMillis()));

                com.example.safesphere.network.SupabaseClient client =
                        com.example.safesphere.network.SupabaseClient.getInstance(ctx);
                com.example.safesphere.network.SupabaseClient.SupabaseResponse response =
                        client.updateRow("users", "id", userId, patch);

                if (response.success) {
                    Prefs.clearPendingProfileData(ctx);
                    Log.d(TAG, "syncPendingProfileIfNeeded: pending profile synced successfully");
                } else {
                    Log.w(TAG, "syncPendingProfileIfNeeded: sync failed, will retry next poll - " + response.message);
                }
            } catch (Exception e) {
                Log.w(TAG, "syncPendingProfileIfNeeded: exception during sync", e);
            }
        }, "profile-sync").start();
    }

    private void postAdminMessageNotification(SupabaseClient.AdminMessageData msg, String pendingId) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NotificationManager.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm != null) {
                NotificationChannel ch = new NotificationChannel(
                        ADMIN_MESSAGE_CHANNEL_ID,
                        "Admin Messages",
                        NotificationManager.IMPORTANCE_HIGH
                );
                ch.setDescription("Important admin communication for SafeSphere users");
                nm.createNotificationChannel(ch);
            }

            Intent openIntent = new Intent(this, MainActivity.class);
            openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent contentIntent = PendingIntent.getActivity(
                    this,
                    pendingId.hashCode(),
                    openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, ADMIN_MESSAGE_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(msg.subject == null || msg.subject.trim().isEmpty() ? "Admin Notice" : msg.subject)
                    .setContentText(msg.body)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(msg.body))
                    .setPriority(msg.isCritical ? NotificationCompat.PRIORITY_MAX : NotificationCompat.PRIORITY_HIGH)
                    .setCategory(msg.isCritical ? NotificationCompat.CATEGORY_ALARM : NotificationCompat.CATEGORY_MESSAGE)
                    .setAutoCancel(true)
                    .setContentIntent(contentIntent)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

            if (msg.isCritical) {
                builder.setFullScreenIntent(contentIntent, true);
            }

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                NotificationManager notificationManager =
                        (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (notificationManager != null) {
                    notificationManager.notify(Math.abs(pendingId.hashCode()), builder.build());
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to post admin message notification", e);
        }
    }

    // ================================================================
    //  SHAKE DETECTOR
    // ================================================================

    private void setupShakeDetector() {
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        Sensor accel  = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        shakeDetector = new ShakeDetector(this);
        sensorManager.registerListener(shakeDetector, accel, SensorManager.SENSOR_DELAY_UI);
        Log.d(TAG, "Shake detector registered");
    }

    @Override
    public void onShakeTriggered() {
        Log.d(TAG, "Shake detected!");
        triggerEmergency("SHAKE");
    }

    // ================================================================
    //  VOSK INIT
    // ================================================================

    private void initVosk() {
        voskReady = false;
        initInProgress = true;
        new Thread(() -> {
            try {
                Log.d(TAG, "Initialising Vosk…");
                File modelDir = copyModelFromAssets();
                if (modelDir == null) { Log.e(TAG, "Model copy failed"); return; }

                // Close old model if any
                closeVosk();

                voskModel = new Model(modelDir.getAbsolutePath());

                String keyword = Prefs.getKeyword(getApplicationContext()).toLowerCase().trim();
                if (keyword.isEmpty()) {
                    Log.e(TAG, "Keyword is empty – cannot start recognizer");
                    return;
                }

                // Grammar mode: only listen for the keyword + [unk] for everything else
                String grammar = "[\"" + keyword + "\", \"[unk]\"]";
                Log.d(TAG, "Vosk grammar: " + grammar);

                recognizer = new Recognizer(voskModel, SAMPLE_RATE, grammar);
                voskReady = true;
                initInProgress = false;
                Log.d(TAG, "Vosk ready. Listening for: '" + keyword + "'");
                Prefs.logAllPrefs(getApplicationContext());

                startAudioLoop();
            } catch (Exception e) {
                Log.e(TAG, "Vosk init error", e);
                voskReady = false;
                initInProgress = false;
            }
        }, "vosk-init").start();
    }

    // ================================================================
    //  ASSET MODEL COPY
    // ================================================================

    private File copyModelFromAssets() {
        File outDir = new File(getFilesDir(), "model-android");
        if (outDir.exists()) {
            Log.d(TAG, "Model already at: " + outDir.getAbsolutePath());
            return outDir;
        }
        try {
            copyAssetFolder(getAssets(), "model-android", outDir.getAbsolutePath());
            Log.d(TAG, "Model copied to: " + outDir.getAbsolutePath());
            return outDir;
        } catch (Exception e) {
            Log.e(TAG, "copyModelFromAssets failed", e);
            return null;
        }
    }

    private void copyAssetFolder(AssetManager am, String src, String dst) throws IOException {
        String[] files = am.list(src);
        if (files == null) return;
        new File(dst).mkdirs();
        for (String f : files) {
            String[] sub = am.list(src + "/" + f);
            if (sub != null && sub.length > 0) {
                copyAssetFolder(am, src + "/" + f, dst + "/" + f);
            } else {
                copyAssetFile(am, src + "/" + f, dst + "/" + f);
            }
        }
    }

    private void copyAssetFile(AssetManager am, String src, String dst) throws IOException {
        File out = new File(dst);
        if (out.exists()) return;
        try (InputStream in = am.open(src); FileOutputStream fos = new FileOutputStream(out)) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) fos.write(buf, 0, n);
        }
    }

    // ================================================================
    //  AUDIO LOOP
    // ================================================================

    private void startAudioLoop() {
        // Stop any existing audio loop first
        stopListening();

        int minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        int bufSize = minBuf * BUFFER_MULT;

        audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufSize);

        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord MIC failed – retrying with DEFAULT");
            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.DEFAULT,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufSize);
            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord init failed completely");
                return;
            }
        }

        audioRecord.startRecording();
        listening = true;
        Log.d(TAG, "Audio loop started (bufSize=" + bufSize + ")");

        final byte[] buffer = new byte[bufSize];

        audioThread = new Thread(() -> {
            while (listening && !Thread.currentThread().isInterrupted()) {
                int read = audioRecord.read(buffer, 0, buffer.length);
                if (read > 0 && recognizer != null) {
                    if (recognizer.acceptWaveForm(buffer, read)) {
                        checkKeyword(recognizer.getResult(), false);
                    } else {
                        checkKeyword(recognizer.getPartialResult(), true);
                    }
                }
            }
            Log.d(TAG, "Audio loop exited");
        }, "vosk-audio");
        audioThread.start();
    }

    // ================================================================
    //  KEYWORD CHECK
    // ================================================================

    private void checkKeyword(String json, boolean isPartial) {
        if (json == null || json.isEmpty()) return;
        try {
            JSONObject obj = new JSONObject(json);
            String text = isPartial
                    ? obj.optString("partial", "")
                    : obj.optString("text", "");
            if (text.isEmpty()) return;

            String keyword = Prefs.getKeyword(getApplicationContext()).toLowerCase().trim();
            if (keyword.isEmpty()) return;

            Log.d(TAG, (isPartial ? "[partial] " : "[final]   ") +
                    "Heard: '" + text + "' | Looking for: '" + keyword + "'");

            // STRICT MATCHING: Only trigger on FINAL results (not partial) and exact word match
            if (!isPartial && text.toLowerCase().trim().equals(keyword)) {
                Log.d(TAG, "✅ Keyword detected (exact match): " + keyword);
                triggerEmergency("KEYWORD");
            }
        } catch (Exception ignored) {}
    }

    // ================================================================
    //  CLEANUP
    // ================================================================

    private void closeVosk() {
        try {
            if (recognizer != null) { recognizer.close(); recognizer = null; }
            if (voskModel  != null) { voskModel.close();  voskModel  = null; }
        } catch (Exception e) { Log.e(TAG, "closeVosk error", e); }
        voskReady = false;
    }

    private void stopListening() {
        listening = false;
        if (audioThread != null) {
            audioThread.interrupt();
            audioThread = null;
        }
        try {
            if (audioRecord != null) {
                audioRecord.stop();
                audioRecord.release();
                audioRecord = null;
            }
        } catch (Exception e) { Log.e(TAG, "stopListening error", e); }
    }

    // ================================================================
    //  EMERGENCY TRIGGER
    // ================================================================

    private void triggerEmergency(String source) {
        if (Prefs.getCallSequenceIndex(getApplicationContext()) >= 0) {
            Log.d(TAG, "Emergency sequence already active - ignoring new trigger from " + source);
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastTriggerTime < DEBOUNCE_MS) {
            Log.d(TAG, "Debounce – ignoring trigger from " + source);
            return;
        }
        lastTriggerTime = now;
        Log.d(TAG, "🚨 Emergency triggered by: " + source);
        vibrateEmergencyFeedback();

        // Upgrade to FULL_WAKE_LOCK during emergency
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
            wakeLock = pm.newWakeLock(
                    PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "SafeSphere::EmergencyWakeLock");
            wakeLock.acquire(120_000); // 2 minutes max
            Log.d(TAG, "⚡ FULL_WAKE_LOCK acquired for emergency (2 min max)");
        }

        final Context ctx = getApplicationContext();

        // Stop listening while the call is in progress (mic is taken by the phone call anyway)
        stopListening();

        // Broadcast for UI feedback in MainActivity
        try {
            Intent broadcast = new Intent("com.example.safesphere.KEYWORD_DETECTED");
            broadcast.putExtra("source", source);
            sendBroadcast(broadcast);
        } catch (Exception e) {
            Log.e(TAG, "Failed to send broadcast", e);
        }

        // Show system-wide Toast
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                android.widget.Toast.makeText(ctx,
                    "🚨 Emergency! Sending SMS & calling contacts...",
                    android.widget.Toast.LENGTH_LONG).show();
            } catch (Exception ignored) {}
        });

        // Trigger emergency workflow — Vosk will restart via EMERGENCY_SEQUENCE_COMPLETE broadcast
        try {
            Log.d(TAG, "Calling EmergencyManager.triggerEmergencyWithSource()");
            EmergencyManager.triggerEmergencyWithSource(ctx, source);
        } catch (Exception e) {
            Log.e(TAG, "❌ CRITICAL: EmergencyManager.triggerEmergencyWithSource() FAILED", e);
            // If emergency failed entirely, restart Vosk now so we keep listening
            restartVoskNow();
        }
    }

    private void vibrateEmergencyFeedback() {
        try {
            Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (vibrator == null || !vibrator.hasVibrator()) return;

            long[] pattern = new long[]{0, 220, 120, 220, 120, 320};
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
            } else {
                vibrator.vibrate(pattern, -1);
            }
        } catch (Exception e) {
            Log.w(TAG, "Emergency vibration failed", e);
        }
    }

    /**
     * Restart Vosk immediately — called when the call sequence completes.
     * Uses the already-loaded model if available (fast path), otherwise full reinit.
     */
    void restartVoskNow() {
        mainHandler.post(() -> {
            Log.d(TAG, "🎤 restartVoskNow() – restarting keyword listener");
            if (voskModel != null) {
                try {
                    String keyword = Prefs.getKeyword(getApplicationContext()).toLowerCase().trim();
                    if (!keyword.isEmpty()) {
                        if (recognizer != null) {
                            try { recognizer.close(); } catch (Exception ignored) {}
                            recognizer = null;
                        }
                        String grammar = "[\"" + keyword + "\", \"[unk]\"]";
                        recognizer = new Recognizer(voskModel, SAMPLE_RATE, grammar);
                        voskReady = true;
                        startAudioLoop();
                        Log.d(TAG, "✅ Vosk restarted immediately, listening for: '" + keyword + "'");
                    } else {
                        Log.w(TAG, "restartVoskNow: keyword empty, skipping");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "restartVoskNow fast-path failed, doing full reinit", e);
                    initVosk();
                }
            } else {
                Log.d(TAG, "restartVoskNow: model not loaded, doing full initVosk()");
                initVosk();
            }
        });
    }

    /** @deprecated Use restartVoskNow() for immediate restart, or initVosk() for full reinit */
    private void scheduleVoskRestart(Context ctx, long delayMs) {
        mainHandler.postDelayed(this::restartVoskNow, delayMs);
    }

    // ================================================================
    //  SERVICE WATCHDOG  (survives swipe-away on MIUI)
    // ================================================================

    /**
     * Schedules an AlarmManager alarm that fires ServiceRestartReceiver every 60s.
     * If the service is already running, onStartCommand is a no-op.
     * If it was killed, the receiver restarts it.
     *
     * On Android 12+ (API 31+) with targetSdk 31+, setExactAndAllowWhileIdle() requires
     * SCHEDULE_EXACT_ALARM, which must be user-granted. We use canScheduleExactAlarms() to
     * check first and fall back to setAndAllowWhileIdle() (inexact but Doze-safe) to avoid
     * the SecurityException that would crash the entire app process.
     */
    private void scheduleWatchdog() {
        try {
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (am == null) return;

            PendingIntent pi = getRestartPendingIntent(getApplicationContext());
            long triggerMs = System.currentTimeMillis() + 60_000L;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // API 31
                // canScheduleExactAlarms() returns false if SCHEDULE_EXACT_ALARM not granted.
                // Fall back to inexact Doze-safe alarm rather than crashing.
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi);
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi);
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi);
            } else {
                am.setRepeating(AlarmManager.RTC_WAKEUP, triggerMs, 60_000L, pi);
            }
            Log.d(TAG, "⏰ Watchdog alarm scheduled (60s)");
        } catch (SecurityException e) {
            // SCHEDULE_EXACT_ALARM not granted — fall back to inexact, never crash
            Log.w(TAG, "scheduleWatchdog: exact alarm denied, using inexact fallback");
            try {
                AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
                if (am != null) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                            System.currentTimeMillis() + 60_000L,
                            getRestartPendingIntent(getApplicationContext()));
                }
            } catch (Exception e2) {
                Log.e(TAG, "scheduleWatchdog inexact fallback also failed", e2);
            }
        } catch (Exception e) {
            Log.e(TAG, "scheduleWatchdog failed", e);
        }
    }

    /**
     * Sends an immediate restart broadcast via ServiceRestartReceiver.
     * Used from onTaskRemoved and onDestroy so the service comes back
     * even when the process is killed right after.
     */
    static void scheduleRestartBroadcast(Context ctx) {
        scheduleRestartBroadcast(ctx, 1_000L);
    }

    /**
     * Schedules a restart broadcast after the requested delay.
     * Used as a process-death-safe wake-up for emergency call progression.
     */
    static void scheduleRestartBroadcast(Context ctx, long delayMs) {
        try {
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            PendingIntent pi = getRestartPendingIntent(ctx);
            long triggerMs = System.currentTimeMillis() + Math.max(500L, delayMs);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // API 31
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi);
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi);
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi);
            } else {
                am.set(AlarmManager.RTC_WAKEUP, triggerMs, pi);
            }
            Log.d("SAFESPHERE_SVC", "⏰ Restart broadcast scheduled in " + delayMs + "ms");
        } catch (Exception e) {
            Log.e("SAFESPHERE_SVC", "scheduleRestartBroadcast failed", e);
        }
    }

    private static PendingIntent getRestartPendingIntent(Context ctx) {
        Intent intent = new Intent(ServiceRestartReceiver.ACTION_RESTART);
        intent.setClass(ctx, ServiceRestartReceiver.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(ctx, 0, intent, flags);
    }
}
