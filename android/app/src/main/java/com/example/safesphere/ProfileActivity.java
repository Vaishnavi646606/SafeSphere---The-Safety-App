package com.example.safesphere;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class ProfileActivity extends AppCompatActivity {

    private EditText etName, etPhone, etKeyword, etE1, etE2, etE3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        etName = findViewById(R.id.etName);
        etPhone = findViewById(R.id.etPhone);
        etKeyword = findViewById(R.id.etKeyword);
        etE1 = findViewById(R.id.etE1);
        etE2 = findViewById(R.id.etE2);
        etE3 = findViewById(R.id.etE3);
        Button btnSave = findViewById(R.id.btnSave);
        Button btnLogout = findViewById(R.id.btnLogout);

        SharedPreferences sp = Prefs.getAll(this);
        etName.setText(sp.getString("name", ""));
        etPhone.setText(sp.getString("phone", ""));
        etKeyword.setText(sp.getString("keyword", ""));
        etE1.setText(sp.getString("e1", ""));
        etE2.setText(sp.getString("e2", ""));
        etE3.setText(sp.getString("e3", ""));

        btnSave.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String phone = etPhone.getText().toString().trim();
            String keyword = etKeyword.getText().toString().trim();
            String e1 = etE1.getText().toString().trim();
            String e2 = etE2.getText().toString().trim();
            String e3 = etE3.getText().toString().trim();

            if (!validateProfileFields(name, phone, keyword, e1, e2, e3)) {
                return;
            }

            Prefs.saveUser(
                    ProfileActivity.this,
                    name,
                    phone,
                    keyword,
                    e1,
                    e2,
                    e3
            );
            Toast.makeText(ProfileActivity.this, "Profile updated", Toast.LENGTH_SHORT).show();
            
            // Restart the service to reload the new keyword
            if (Prefs.isProtectionEnabled(this)) {
                android.content.Intent serviceIntent = new android.content.Intent(this, SafeSphereService.class);
                stopService(serviceIntent);

                boolean hasMic = ContextCompat.checkSelfPermission(this,
                        Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
                if (!hasMic) {
                    Toast.makeText(this,
                            "Microphone permission is required to run background protection.",
                            Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }

                // Small delay before restarting
                new android.os.Handler().postDelayed(() -> {
                    androidx.core.content.ContextCompat.startForegroundService(
                        ProfileActivity.this,
                        new android.content.Intent(ProfileActivity.this, SafeSphereService.class)
                    );
                }, 500);
            }
            
            finish();
        });

        btnLogout.setOnClickListener(v -> {
            // Stop the background service before logout
            android.content.Intent serviceIntent = new android.content.Intent(this, SafeSphereService.class);
            stopService(serviceIntent);
            
            // Logout user
            Prefs.logout(this);
            Prefs.setProtectionEnabled(this, false);
            Prefs.clearCallSequence(this);
            
            Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();
            
            // Redirect to login screen
            android.content.Intent intent = new android.content.Intent(this, LoginActivity.class);
            intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    private boolean validateProfileFields(String name, String phone, String keyword,
                                          String e1, String e2, String e3) {
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
}
