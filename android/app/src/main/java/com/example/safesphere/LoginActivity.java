package com.example.safesphere;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.safesphere.analytics.AnalyticsQueue;
import com.example.safesphere.analytics.EventType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class LoginActivity extends AppCompatActivity {

    private static final int REQ_PERMISSION = 141;
    private List<String> pendingPermissions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Debug: Log all SharedPreferences
        Prefs.logAllPrefs(this);
        
        // Check if user is already logged in
        if (Prefs.isLoggedIn(this)) {
            android.util.Log.d("LOGIN", "User already logged in, redirecting to MainActivity");
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }
        
        android.util.Log.d("LOGIN", "User not logged in, showing login screen");
        setContentView(R.layout.activity_login);

        EditText etLoginPhone = findViewById(R.id.etLoginPhone);
        Button btnLogin = findViewById(R.id.btnLogin);
        Button btnGoRegister = findViewById(R.id.btnGoRegister);

        // 🔐 LOGIN BUTTON
        btnLogin.setOnClickListener(v -> {

            String entered = etLoginPhone.getText().toString().trim();
            String saved   = Prefs.getUserPhone(this);

            if (TextUtils.isEmpty(entered)) {
                etLoginPhone.setError("Enter your mobile number");
                etLoginPhone.requestFocus();
                return;
            }

            if (!isValidPhone(entered)) {
                etLoginPhone.setError("Enter a valid 10-digit mobile number");
                etLoginPhone.requestFocus();
                return;
            }

            // ❗ Agar register nahi kiya
            if (saved == null || saved.isEmpty()) {
                Toast.makeText(this, "Please register first", Toast.LENGTH_SHORT).show();
                return;
            }

            // ❗ Phone mismatch
            if (!entered.equals(saved)) {
                Toast.makeText(this, "Invalid phone number", Toast.LENGTH_SHORT).show();
                return;
            }

            // ✅ Login success - set logged in flag
            Prefs.setLoggedIn(this, true);

            // ── Analytics: ensure user ID exists, enqueue login event ──
            if (Prefs.getUserId(this) == null) {
                Prefs.setUserId(this, UUID.randomUUID().toString());
            }
            AnalyticsQueue.get(this).enqueue(EventType.LOGIN, null);

            showWelcomeDialog();
        });

        // 🔁 Go to Register screen
        btnGoRegister.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, RegisterActivity.class)));
    }

    private boolean isValidPhone(String value) {
        return value.matches("\\d{10}");
    }

    private void showWelcomeDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Welcome to SafeSphere")
                .setMessage("To work properly and keep you safe, SafeSphere needs a few permissions.\n\nPlease allow them when asked — this helps the app send emergency alerts, detect your keyword, and share your location with your trusted contacts.")
                .setCancelable(false)
                .setPositiveButton("OK, Let's Go", (dialog, which) -> startSequentialPermissions())
                .show();
    }

    private void startSequentialPermissions() {
        pendingPermissions = new ArrayList<>();
        pendingPermissions.add(Manifest.permission.RECORD_AUDIO);
        pendingPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        pendingPermissions.add(Manifest.permission.SEND_SMS);
        pendingPermissions.add(Manifest.permission.CALL_PHONE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            pendingPermissions.add(Manifest.permission.ANSWER_PHONE_CALLS);
        }
        pendingPermissions.add(Manifest.permission.READ_PHONE_STATE);
        pendingPermissions.add(Manifest.permission.READ_CALL_LOG);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            pendingPermissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pendingPermissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        requestNextPermission();
    }

    private void requestNextPermission() {
        while (!pendingPermissions.isEmpty()) {
            String perm = pendingPermissions.get(0);
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{perm}, REQ_PERMISSION);
                return;
            }
            pendingPermissions.remove(0);
        }
        goToMain();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERMISSION) {
            if (!pendingPermissions.isEmpty()) {
                pendingPermissions.remove(0);
            }
            requestNextPermission();
        }
    }

    private void goToMain() {
        Prefs.setCompletedPermissionSetup(this, true);
        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }
}
