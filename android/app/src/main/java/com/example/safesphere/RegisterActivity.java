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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RegisterActivity extends AppCompatActivity {

    private static final int REQ_PERMISSION = 141;
    private List<String> pendingPermissions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        EditText etName = findViewById(R.id.etRegName);
        EditText etPhone = findViewById(R.id.etRegPhone);
        EditText etKeyword = findViewById(R.id.etRegKeyword);
        EditText etE1 = findViewById(R.id.etRegE1);
        EditText etE2 = findViewById(R.id.etRegE2);
        EditText etE3 = findViewById(R.id.etRegE3);
        Button btnRegister = findViewById(R.id.btnRegister);

        btnRegister.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String phone = etPhone.getText().toString().trim();
            String keyword = etKeyword.getText().toString().trim();
            String e1 = etE1.getText().toString().trim();
            String e2 = etE2.getText().toString().trim();
            String e3 = etE3.getText().toString().trim();

            if (!validateProfileFields(etName, etPhone, etKeyword, etE1, etE2, etE3,
                    name, phone, keyword, e1, e2, e3)) {
                return;
            }

            Prefs.saveUser(this, name, phone, keyword, e1, e2, e3);

            // ── Analytics: generate user ID and enqueue registration event ──
            if (Prefs.getUserId(this) == null) {
                // Generate a stable UUID for this device/user
                String newUserId = UUID.randomUUID().toString();
                Prefs.setUserId(this, newUserId);
            }
            Map<String, Object> regPayload = new HashMap<>();
            regPayload.put("name_length", name.length());
            regPayload.put("has_e1", !e1.isEmpty());
            regPayload.put("has_e2", !e2.isEmpty());
            regPayload.put("has_e3", !e3.isEmpty());
            AnalyticsQueue.get(this).enqueue(EventType.REGISTRATION, null, regPayload);

            Toast.makeText(this, "Registered", Toast.LENGTH_SHORT).show();
            showWelcomeDialog(name);
        });
    }

    private boolean validateProfileFields(
            EditText etName,
            EditText etPhone,
            EditText etKeyword,
            EditText etE1,
            EditText etE2,
            EditText etE3,
            String name,
            String phone,
            String keyword,
            String e1,
            String e2,
            String e3) {
        if (TextUtils.isEmpty(name)) {
            etName.setError("Enter your name");
            etName.requestFocus();
            return false;
        }
        if (!isValidPhone(phone)) {
            etPhone.setError("Enter a valid 10-digit mobile number");
            etPhone.requestFocus();
            return false;
        }
        if (TextUtils.isEmpty(keyword)) {
            etKeyword.setError("Enter emergency keyword");
            etKeyword.requestFocus();
            return false;
        }
        if (!isValidPhone(e1)) {
            etE1.setError("Contact 1 is required and must be 10 digits");
            etE1.requestFocus();
            return false;
        }
        if (!isValidPhone(e2)) {
            etE2.setError("Contact 2 is required and must be 10 digits");
            etE2.requestFocus();
            return false;
        }
        if (!isValidPhone(e3)) {
            etE3.setError("Contact 3 is required and must be 10 digits");
            etE3.requestFocus();
            return false;
        }
        return true;
    }

    private boolean isValidPhone(String value) {
        return value != null && value.matches("\\d{10}");
    }

    private void showWelcomeDialog(String name) {
        new AlertDialog.Builder(this)
                .setTitle("Welcome to SafeSphere, " + name + "!")
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
