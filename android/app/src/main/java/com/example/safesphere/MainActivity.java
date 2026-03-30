package com.example.safesphere;

import android.Manifest;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.os.PowerManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.SwitchCompat;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.example.safesphere.analytics.AnalyticsQueue;
import com.example.safesphere.analytics.EventType;
import com.example.safesphere.revocation.RevocationCheckWorker;
import com.example.safesphere.revocation.RevocationHandler;
import com.example.safesphere.sync.AnalyticsSyncWorker;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_PERMISSIONS = 101;
    private static final long MIN_LOCATION_LOADING_MS = 2000L;
    private static final long LOCATION_WAIT_TIMEOUT_MS = 15000L;
    private static final long LOCATION_POLL_INTERVAL_MS = 500L;

    private MediaPlayer sirenPlayer;
    private boolean waitingForLocationEnable = false;
    private Boolean pendingShareLive = null;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingLocationShareRunnable;
    private long locationLoadingStartMs = 0L;
    private boolean locationReceiverRegistered = false;

    // Protection toggle views (set in onCreate, reused in onResume)
    private LinearLayout cardProtection;
    private TextView tvProtectionStatus;
    private SwitchCompat switchProtection;
    private boolean suppressSwitchListener = false;
    private View locationLoadingOverlay;
    private TextView tvLocationLoading;

    // Receives keyword/shake detection broadcast from SafeSphereService
    private final BroadcastReceiver emergencyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String source = intent.getStringExtra("source");
            String msg = "KEYWORD".equals(source)
                    ? "🚨 Keyword Detected! Triggering Emergency Actions..."
                    : "🚨 Shake Detected! Triggering Emergency Actions...";
            Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
        }
    };

    private final BroadcastReceiver locationModeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (isLocationServiceEnabled()) {
                refreshLastKnownLocationSilently();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Check if user is logged in
        if (!Prefs.isLoggedIn(this)) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        // ── Analytics: schedule background workers on first launch ──
        scheduleAnalyticsWorkers();

        // Track login/foreground event
        AnalyticsQueue.get(this).enqueue(EventType.APP_FOREGROUNDED);

        setContentView(R.layout.activity_main);

        // ---------- PROTECTION TOGGLE ----------
        cardProtection     = findViewById(R.id.cardProtection);
        tvProtectionStatus = findViewById(R.id.tvProtectionStatus);
        switchProtection   = findViewById(R.id.switchProtection);
        locationLoadingOverlay = findViewById(R.id.locationLoadingOverlay);
        tvLocationLoading = findViewById(R.id.tvLocationLoading);

        boolean protActive = Prefs.isProtectionEnabled(this);
        suppressSwitchListener = true;
        switchProtection.setChecked(protActive);
        suppressSwitchListener = false;
        updateProtectionUI(protActive);

        switchProtection.setOnCheckedChangeListener((btn, isChecked) -> {
            if (suppressSwitchListener) return;
            Prefs.setProtectionEnabled(this, isChecked);
            updateProtectionUI(isChecked);
            if (isChecked) {
                requestBatteryOptimizationExemption();
                startSafeSphereService();
                Toast.makeText(this,
                        "Protection ON — App will monitor in background indefinitely.",
                        Toast.LENGTH_LONG).show();
            } else {
                stopService(new Intent(this, SafeSphereService.class));
                Toast.makeText(this,
                        "Protection OFF — Background monitoring stopped.",
                        Toast.LENGTH_SHORT).show();
            }
        });

        // Show a one-time informational dialog that tells the user which settings to
        // enable manually. Delayed 600 ms so the system permission dialog above has
        // time to settle — avoids WindowManager$BadTokenException on MIUI.
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!isFinishing() && !isDestroyed()) {
                showPermissionGuidanceDialog();
            }
        }, 600);

        // Battery optimization and overlay checks temporarily disabled —
        // they are not the root cause and interfere with testing.
        // checkBatteryOptimization();
        // checkOverlayPermission();

        // Start service immediately if mic permission already granted
        startSafeSphereService();

        // ---------- VIEWS ----------
        Button btnSosCurrent = findViewById(R.id.btnSosCurrent);
        Button btnSosLive    = findViewById(R.id.btnSosLive);
        Button btnSiren      = findViewById(R.id.btnSiren);
        Button btnFakeCall   = findViewById(R.id.btnFakeCall);
        ImageButton btnProfile = findViewById(R.id.btnProfile);

        // ---------- CLICK LISTENERS ----------

        btnSosCurrent.setOnClickListener(v -> {
            if (ensurePermissions()) shareLocation(false);
        });

        btnSosLive.setOnClickListener(v -> {
            if (ensurePermissions()) shareLocation(true);
        });

        btnSiren.setOnClickListener(v -> toggleSiren());

        btnFakeCall.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, FakeCallActivity.class)));

        btnProfile.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, ProfileActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();

        // ── Analytics: check for pending revocation ──
        if (RevocationHandler.isPendingRevocation(this)) {
            String msg = Prefs.getRevocationMessage(this);
            new android.app.AlertDialog.Builder(this)
                .setTitle("Account Removed")
                .setMessage(msg != null ? msg : "Your account has been removed from SafeSphere.")
                .setCancelable(false)
                .setPositiveButton("OK", (d, w) -> {
                    RevocationHandler.performLogout(this);
                    startActivity(new Intent(this, LoginActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                    finish();
                })
                .show();
            return;
        }

        // ── Analytics: check for pending admin messages ──
        try {
            org.json.JSONArray msgs = Prefs.getPendingAdminMessages(this);
            if (msgs.length() > 0) {
                org.json.JSONObject first = msgs.getJSONObject(0);
                String subject = first.optString("subject", "Admin Notice");
                String body = first.optString("body", "");
                boolean critical = first.optBoolean("isCritical", false);
                Prefs.clearPendingAdminMessages(this);
                if (!body.isEmpty()) {
                    new android.app.AlertDialog.Builder(this)
                        .setTitle(subject)
                        .setMessage(body)
                        .setCancelable(!critical)
                        .setPositiveButton("OK", null)
                        .show();
                }
                AnalyticsQueue.get(this).enqueue(EventType.ADMIN_MSG_RECEIVED, null);
            }
        } catch (Exception ignored) {}

        // Register receiver for keyword/shake UI feedback
        IntentFilter filter = new IntentFilter("com.example.safesphere.KEYWORD_DETECTED");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(emergencyReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(emergencyReceiver, filter);
        }

        // Sync toggle UI in case user changed something externally
        boolean protActive = Prefs.isProtectionEnabled(this);
        suppressSwitchListener = true;
        switchProtection.setChecked(protActive);
        suppressSwitchListener = false;
        updateProtectionUI(protActive);

        // Restart service on resume (gated — only runs if protection is ON)
        startSafeSphereService();

        registerLocationModeReceiver();
        refreshLastKnownLocationSilently();

        if (waitingForLocationEnable) {
            waitingForLocationEnable = false;

            boolean enabled = isLocationServiceEnabled();

            if (enabled) {
                boolean live = pendingShareLive != null && pendingShareLive;
                pendingShareLive = null;
                Toast.makeText(this, "Location enabled! Opening share now", Toast.LENGTH_SHORT).show();
                shareLocation(live);
            } else {
                pendingShareLive = null;
                Toast.makeText(this, "Location is still OFF. Please enable it to share location.", Toast.LENGTH_SHORT).show();
            }
        }
    }


    // ---------- SHARE LOCATION (CURRENT / LIVE) ----------

    private void shareLocation(boolean live) {
        boolean enabled = isLocationServiceEnabled();

        // ❌ Location OFF → open settings, auto-send on return
        if (!enabled) {
            waitingForLocationEnable = true;
            pendingShareLive = live;
            Toast.makeText(this, "Please turn ON location first", Toast.LENGTH_LONG).show();
            startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            return;
        }

        startLocationLoadingAndShare(live);
    }

    private void startLocationLoadingAndShare(boolean live) {
        cancelPendingLocationShare();
        locationLoadingStartMs = System.currentTimeMillis();
        showLocationLoading(true, "Getting your location...");
        requestOneShotLocationUpdate();
        waitForUsableLocationThenShare(live, System.currentTimeMillis());
    }

    private void waitForUsableLocationThenShare(boolean live, long startedAtMs) {
        pendingLocationShareRunnable = new Runnable() {
            @Override
            public void run() {
                String currentLink = EmergencyManager.buildCurrentLocationLink(MainActivity.this);
                boolean hasUsableLocation = isUsableMapsLink(currentLink);
                long waitedMs = System.currentTimeMillis() - startedAtMs;

                if (!hasUsableLocation && waitedMs < LOCATION_WAIT_TIMEOUT_MS) {
                    uiHandler.postDelayed(this, LOCATION_POLL_INTERVAL_MS);
                    return;
                }

                long loadingElapsed = System.currentTimeMillis() - locationLoadingStartMs;
                long remainingLoadingMs = Math.max(0L, MIN_LOCATION_LOADING_MS - loadingElapsed);

                uiHandler.postDelayed(() -> {
                    showLocationLoading(false, null);
                    if (!hasUsableLocation) {
                        Toast.makeText(MainActivity.this,
                                "Unable to get current location. Please wait a moment and try again.",
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    openShareChooser(live, currentLink);
                }, remainingLoadingMs);
            }
        };

        uiHandler.postDelayed(pendingLocationShareRunnable, LOCATION_POLL_INTERVAL_MS);
    }

    private void openShareChooser(boolean live, String currentLink) {
        String liveLink = EmergencyManager.buildLiveLocationLink(this);
        if (!isUsableMapsLink(liveLink)) {
            liveLink = currentLink;
        }

        String text = live
                ? "📍 My current location: " + currentLink +
                  "\n🔴 Live location: "      + liveLink
                : "📍 My current location: " + currentLink;

        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.setType("text/plain");
        sendIntent.putExtra(Intent.EXTRA_TEXT, text);

        startActivity(Intent.createChooser(
                sendIntent,
                live ? "Share live location via" : "Share current location via"
        ));
    }

    private void requestOneShotLocationUpdate() {
        boolean hasFine = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean hasCoarse = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!hasFine && !hasCoarse) {
            return;
        }

        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (lm == null) {
            return;
        }

        LocationListener listener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                long timestampMs = location.getTime() > 0 ? location.getTime() : System.currentTimeMillis();
                Prefs.setLastKnownLocation(MainActivity.this,
                        location.getLatitude(), location.getLongitude(), timestampMs);
                try {
                    lm.removeUpdates(this);
                } catch (Exception ignored) {
                }
            }
        };

        try {
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, listener, Looper.getMainLooper());
            } else if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestSingleUpdate(LocationManager.GPS_PROVIDER, listener, Looper.getMainLooper());
            }
        } catch (Exception ignored) {
        }
    }

    private void refreshLastKnownLocationSilently() {
        if (!isLocationServiceEnabled()) return;
        requestOneShotLocationUpdate();
    }

    private void registerLocationModeReceiver() {
        if (locationReceiverRegistered) return;
        IntentFilter filter = new IntentFilter();
        filter.addAction(LocationManager.MODE_CHANGED_ACTION);
        filter.addAction(LocationManager.PROVIDERS_CHANGED_ACTION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(locationModeReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(locationModeReceiver, filter);
        }
        locationReceiverRegistered = true;
    }

    private boolean isUsableMapsLink(String locationText) {
        return locationText != null && locationText.startsWith("https://maps.google.com/?q=");
    }

    private void showLocationLoading(boolean visible, String message) {
        if (locationLoadingOverlay == null) return;
        locationLoadingOverlay.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible && tvLocationLoading != null && message != null) {
            tvLocationLoading.setText(message);
        }
    }

    private void cancelPendingLocationShare() {
        if (pendingLocationShareRunnable != null) {
            uiHandler.removeCallbacks(pendingLocationShareRunnable);
            pendingLocationShareRunnable = null;
        }
        showLocationLoading(false, null);
    }

    /**
     * Reliable location-service check across OEMs. Provider-only checks can be misleading
     * on some devices when toggling location from Settings.
     */
    private boolean isLocationServiceEnabled() {
        android.location.LocationManager lm =
                (android.location.LocationManager) getSystemService(LOCATION_SERVICE);
        if (lm == null) return false;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return lm.isLocationEnabled();
            }
            return lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
                    || lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER);
        } catch (Exception e) {
            return false;
        }
    }

    // ---------- PERMISSIONS + SERVICE ----------

    /**
     * SMS + CALL + LOCATION + MIC + PHONE_STATE permissions check karega.
     * Agar kuch missing ho to request karega.
     * Sab mil gaye to background service start karega.
     */
    private boolean ensurePermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // Old devices – direct start
            startSafeSphereService();
            return true;
        }

        String[] needed = new String[]{
                Manifest.permission.SEND_SMS,
                Manifest.permission.CALL_PHONE,
            Manifest.permission.ANSWER_PHONE_CALLS,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG
        };

        List<String> toRequest = new ArrayList<>();
        for (String p : needed) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                toRequest.add(p);
            }
        }

        if (!toRequest.isEmpty()) {
            Toast.makeText(this,
                    "Permission missing. Some features may not work properly.",
                    Toast.LENGTH_LONG).show();
            return false;
        } else {
            // Sab permissions mil chuki hain → service ensure karo
            startSafeSphereService();
            return true;
        }
    }

    /**
     * Mic permission granted ho to keyword+shake service ko foreground me start karo.
     * Only starts if protection is explicitly enabled by the user.
     */
    private void startSafeSphereService() {
        if (!Prefs.isProtectionEnabled(this)) return;
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {

            Intent serviceIntent = new Intent(this, SafeSphereService.class);
            ContextCompat.startForegroundService(this, serviceIntent);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQ_PERMISSIONS) {
            boolean allGranted = true;
            for (int res : grantResults) {
                if (res != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                Toast.makeText(this, "Permissions granted for SOS & keyword.", Toast.LENGTH_SHORT).show();
                startSafeSphereService();
            } else {
                Toast.makeText(this,
                        "Some permissions denied. Share & keyword features may be limited.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    // ---------- SIREN LOGIC ----------

    private void toggleSiren() {
        if (sirenPlayer == null) {
            try {
                sirenPlayer = MediaPlayer.create(this, R.raw.siren);
                if (sirenPlayer == null) {
                    Toast.makeText(this, "Siren audio not found", Toast.LENGTH_SHORT).show();
                    return;
                }
                sirenPlayer.setLooping(true);
                sirenPlayer.start();
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Failed to play siren", Toast.LENGTH_SHORT).show();
            }
        } else {
            try {
                if (sirenPlayer.isPlaying()) {
                    sirenPlayer.stop();
                }
            } catch (Exception ignored) {
            }
            sirenPlayer.release();
            sirenPlayer = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelPendingLocationShare();
        if (sirenPlayer != null) {
            sirenPlayer.release();
            sirenPlayer = null;
        }
    }

    /** Schedule analytics sync + revocation check as periodic background workers. */
    private void scheduleAnalyticsWorkers() {
        Constraints netConstraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        // Analytics sync — every 15 min when online
        PeriodicWorkRequest syncWork = new PeriodicWorkRequest.Builder(
                AnalyticsSyncWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(netConstraints)
                .addTag("analytics_sync")
                .build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "analytics_sync", ExistingPeriodicWorkPolicy.KEEP, syncWork);

        // Revocation check — every 15 min (no network constraint so it still polls offline→shows cached)
        PeriodicWorkRequest revocationWork = new PeriodicWorkRequest.Builder(
                RevocationCheckWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(netConstraints)
                .addTag("revocation_check")
                .build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "revocation_check", ExistingPeriodicWorkPolicy.KEEP, revocationWork);
    }

    @Override
    protected void onPause() {
        super.onPause();
        cancelPendingLocationShare();
        try {
            unregisterReceiver(emergencyReceiver);
        } catch (IllegalArgumentException ignored) {
            // Not registered – safe to ignore
        }
        if (locationReceiverRegistered) {
            try {
                unregisterReceiver(locationModeReceiver);
            } catch (IllegalArgumentException ignored) {
            }
            locationReceiverRegistered = false;
        }
    }

    // ---------- PROTECTION TOGGLE UI ----------

    private void updateProtectionUI(boolean active) {
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        float radius = 14 * getResources().getDisplayMetrics().density;
        bg.setCornerRadius(radius);
        if (active) {
            bg.setColor(0xFFE8F5E9);                          // light green
            tvProtectionStatus.setText("ON — Keyword & shake monitoring active");
            tvProtectionStatus.setTextColor(0xFF2E7D32);      // dark green
        } else {
            bg.setColor(0xFFF3F3F3);                          // light gray
            tvProtectionStatus.setText("OFF — Tap to activate background protection");
            tvProtectionStatus.setTextColor(0xFF757575);      // gray
        }
        cardProtection.setBackground(bg);
    }

    /**
     * Asks Android to whitelist SafeSphere from battery optimization.
     * This is the single most effective step against OEM battery killers (MIUI, OneUI, etc.).
     */
    private void requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        if (Prefs.hasShownBatteryOptimizationPromptOnce(this)) return;

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
            Prefs.setShownBatteryOptimizationPromptOnce(this, true);
            try {
                Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                i.setData(Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } catch (Exception e) {
                // Some OEMs block this intent — fall back to app settings page
                try {
                    Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    i.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                    Toast.makeText(this,
                            "Go to Battery → Remove restrictions, then enable Autostart.",
                            Toast.LENGTH_LONG).show();
                } catch (Exception ignored) {}
            }
        } else {
            Prefs.setShownBatteryOptimizationPromptOnce(this, true);
        }
    }

    /**
     * Shows a one-time informational dialog explaining which settings the user must
     * enable manually for emergency calls to work in the background / on lock screen.
     *
     * NO automatic navigation or permission redirects happen here — the user chooses
     * whether to open settings.  This avoids any SecurityException or
     * WindowManager$BadTokenException that MIUI can throw when an Intent to a
     * manufacturer-specific settings screen is fired automatically on startup.
     *
     * Shown only once, tracked via SharedPreferences.
     */
    private void showPermissionGuidanceDialog() {
        boolean shown = getSharedPreferences("safesphere_prefs", MODE_PRIVATE)
                .getBoolean("lock_popup_prompt_shown", false);
        if (shown) return;

        // Mark as shown immediately so a crash inside the dialog never re-shows it.
        getSharedPreferences("safesphere_prefs", MODE_PRIVATE)
                .edit().putBoolean("lock_popup_prompt_shown", true).apply();

        new AlertDialog.Builder(this)
                .setTitle("Enable Emergency Features")
                .setMessage(
                        "To make sure SafeSphere can call contacts and show alerts " +
                        "even when your phone is locked or the app is closed, " +
                        "please enable these settings for SafeSphere:\n\n" +
                        "1. Show on Lock Screen\n" +
                        "2. Display pop-up windows while running in the background\n" +
                        "3. Allow background activity / No battery restrictions\n\n" +
                        "Tap \"Open Settings\" to go to SafeSphere's app settings page, " +
                        "then look under \"Other permissions\" or \"Battery\".")
                .setPositiveButton("Open Settings", (d, w) -> {
                    try {
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    } catch (Exception e) {
                        Toast.makeText(this,
                                "Go to Settings → Apps → SafeSphere → Other permissions",
                                Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("OK", null)
                .setCancelable(true)
                .show();
    }

}
