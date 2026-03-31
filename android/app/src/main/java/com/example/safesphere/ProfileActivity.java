package com.example.safesphere;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.safesphere.network.SupabaseClient;

import org.json.JSONObject;

public class ProfileActivity extends AppCompatActivity {

    private EditText etName, etPhone, etKeyword, etE1, etE2, etE3;
    private Button btnSave, btnLogout;
    private boolean setupRequired = false;

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
        btnSave = findViewById(R.id.btnSave);
        btnLogout = findViewById(R.id.btnLogout);

        setupRequired = getIntent().getBooleanExtra("setup_required", false);

        // Pre-fill fields from Prefs
        etName.setText(Prefs.getUserName(this));
        etPhone.setText(Prefs.getUserPhone(this));
        etPhone.setEnabled(false); // Phone is read-only
        etKeyword.setText(Prefs.getKeyword(this));
        etE1.setText(Prefs.getAll(this).getString("e1", ""));
        etE2.setText(Prefs.getAll(this).getString("e2", ""));
        etE3.setText(Prefs.getAll(this).getString("e3", ""));

        btnSave.setOnClickListener(v -> handleSaveProfile());
        btnLogout.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Logout", (dialog, which) -> performLogout())
                .setNegativeButton("Cancel", null)
                .show();
        });
    }

    private void handleSaveProfile() {
        String name = etName.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String keyword = etKeyword.getText().toString().trim();
        String e1 = etE1.getText().toString().trim();
        String e2 = etE2.getText().toString().trim();
        String e3 = etE3.getText().toString().trim();

        if (!validateProfileFields(name, keyword, e1, e2, e3)) {
            return;
        }

        // Step 2: Save to local Prefs immediately
        Prefs.setUserName(this, name);
        Prefs.setKeyword(this, keyword);
        Prefs.setEmergency1(this, e1);
        Prefs.setEmergency2(this, e2);
        Prefs.setEmergency3(this, e3);
        Prefs.setNeedsProfileSetup(this, false);

        // Step 3: Run background thread to sync with Supabase
        setLoading(true);

        new Thread(() -> {
            try {
                String userId = Prefs.getSupabaseUserId(this);
                if (userId == null || userId.isEmpty()) {
                    runOnUiThread(() -> {
                        setLoading(false);
                        Toast.makeText(this, "Error: No user ID found", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }

                JSONObject patch = new JSONObject();
                patch.put("display_name", name);
                patch.put("keyword", keyword.toLowerCase().trim());
                patch.put("emergency_contact_1", e1);
                patch.put("emergency_contact_2", e2);
                patch.put("emergency_contact_3", e3);
                patch.put("updated_at", SupabaseClient.toIso8601(System.currentTimeMillis()));

                SupabaseClient client = SupabaseClient.getInstance(this);
                SupabaseClient.SupabaseResponse response = client.updateRow("users", "id", userId, patch);

                runOnUiThread(() -> {
                    setLoading(false);
                    if (response.success) {
                        Toast.makeText(this, "Profile saved", Toast.LENGTH_SHORT).show();
                        // Step 4: If setup_required, finish and return to MainActivity
                        if (setupRequired) {
                            finish();
                        }
                    } else {
                        Toast.makeText(this, "Saved locally. Will sync when connected.", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                android.util.Log.e("ProfileActivity", "Save profile error", e);
                runOnUiThread(() -> {
                    setLoading(false);
                    Toast.makeText(this, "Saved locally. Will sync when connected.", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void performLogout() {
        // 1. Stop SafeSphereService immediately
        try {
            stopService(new Intent(this, SafeSphereService.class));
        } catch (Exception e) {
            android.util.Log.w("ProfileActivity", "Failed to stop service", e);
        }

        // 2. Cancel all WorkManager tasks
        try {
            androidx.work.WorkManager.getInstance(getApplicationContext())
                .cancelAllWork();
        } catch (Exception e) {
            android.util.Log.w("ProfileActivity", "Failed to cancel WorkManager", e);
        }

        // 3. Clear login state but keep profile data
        Prefs.setLoggedIn(getApplicationContext(), false);
        Prefs.setSupabaseUserId(getApplicationContext(), null);
        Prefs.setProtectionEnabled(getApplicationContext(), false);

        // 4. Go to login screen
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void setLoading(boolean loading) {
        btnSave.setEnabled(!loading);
        btnSave.setText(loading ? "Saving..." : "Save Profile");
        btnSave.setBackgroundTintList(
            android.content.res.ColorStateList.valueOf(
                android.graphics.Color.parseColor("#C2185B")));
    }

    private boolean validateProfileFields(String name, String keyword, String e1, String e2, String e3) {
        if (TextUtils.isEmpty(name)) {
            etName.setError("Enter your name");
            etName.requestFocus();
            return false;
        }
        if (TextUtils.isEmpty(keyword)) {
            etKeyword.setError("Enter emergency keyword");
            etKeyword.requestFocus();
            return false;
        }
        if (keyword.length() < 3) {
            etKeyword.setError("Keyword must be at least 3 letters");
            etKeyword.requestFocus();
            return false;
        }
        if (!keyword.matches("[a-zA-Z]+")) {
            etKeyword.setError("Keyword must contain letters only");
            etKeyword.requestFocus();
            return false;
        }
        if (!isValidPhone(e1)) {
            etE1.setError("Contact 1 must be a valid 10-digit mobile number");
            etE1.requestFocus();
            return false;
        }
        if (!isValidPhone(e2)) {
            etE2.setError("Contact 2 must be a valid 10-digit mobile number");
            etE2.requestFocus();
            return false;
        }
        if (!isValidPhone(e3)) {
            etE3.setError("Contact 3 must be a valid 10-digit mobile number");
            etE3.requestFocus();
            return false;
        }
        return true;
    }

    private boolean isValidPhone(String value) {
        return value != null && value.matches("\\d{10}");
    }
}
