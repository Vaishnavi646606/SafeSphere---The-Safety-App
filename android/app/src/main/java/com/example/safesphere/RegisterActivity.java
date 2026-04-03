package com.example.safesphere;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.safesphere.network.SupabaseClient;

import org.json.JSONObject;

import java.util.UUID;

public class RegisterActivity extends AppCompatActivity {

    private EditText etName;
    private EditText etPhone;
    private EditText etKeyword;
    private EditText etE1;
    private EditText etE2;
    private EditText etE3;
    private Button btnRegister;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        etName = findViewById(R.id.etRegName);
        etPhone = findViewById(R.id.etRegPhone);
        etKeyword = findViewById(R.id.etRegKeyword);
        etE1 = findViewById(R.id.etRegE1);
        etE2 = findViewById(R.id.etRegE2);
        etE3 = findViewById(R.id.etRegE3);
        btnRegister = findViewById(R.id.btnRegister);

        btnRegister.setOnClickListener(v -> doRegister());
    }

    private void doRegister() {
        String name = etName.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String keyword = etKeyword.getText().toString().trim();
        String e1 = etE1.getText().toString().trim();
        String e2 = etE2.getText().toString().trim();
        String e3 = etE3.getText().toString().trim();

        if (!validateAllFields(name, phone, keyword, e1, e2, e3)) {
            return;
        }

        setLoading(true);

        new Thread(() -> {
            try {
                // Check connectivity before any Supabase call
                android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                        getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
                android.net.NetworkInfo ni = cm.getActiveNetworkInfo();
                boolean isOnline = ni != null && ni.isConnected();

                if (!isOnline) {
                    runOnUiThread(() -> {
                        setLoading(false);
                        Toast.makeText(RegisterActivity.this,
                                "No internet connection. Registration requires internet. Please connect and try again.",
                                Toast.LENGTH_LONG).show();
                    });
                    return;
                }

                SupabaseClient client = SupabaseClient.getInstance(getApplicationContext());
                JSONObject existingUser = client.getUserByPhone(phone);

                if (existingUser != null) {
                    String existingId = existingUser.optString("id", "");
                    String existingName = existingUser.optString("display_name", "");
                    if (!existingId.trim().isEmpty()) {
                        String resolvedName = existingName.trim().isEmpty() ? name : existingName.trim();
                        persistLocally(existingId, resolvedName, phone, keyword, e1, e2, e3);
                        runOnUiThread(() -> {
                            setLoading(false);
                            Toast.makeText(RegisterActivity.this, "Welcome back, " + resolvedName + "!", Toast.LENGTH_SHORT).show();
                            goToMain();
                        });
                        return;
                    }
                }

                String newUserId = UUID.randomUUID().toString();
                JSONObject userData = new JSONObject();
                userData.put("id", newUserId);
                userData.put("display_name", name);
                userData.put("phone_hash", phone);
                userData.put("is_active", true);
                userData.put("device_model", android.os.Build.MODEL);
                userData.put("android_version", String.valueOf(android.os.Build.VERSION.SDK_INT));
                userData.put("app_version", BuildConfig.VERSION_NAME);
                userData.put("os_type", "android");
                userData.put("total_emergencies", 0);
                userData.put("keyword", keyword.toLowerCase().trim());
                userData.put("emergency_contact_1", e1);
                userData.put("emergency_contact_2", e2);
                userData.put("emergency_contact_3", e3);

                SupabaseClient.SupabaseResponse response = client.insertRow("users", userData);
                if (response.success) {
                    persistLocally(newUserId, name, phone, keyword, e1, e2, e3);
                    runOnUiThread(() -> {
                        setLoading(false);
                        goToMain();
                    });
                    return;
                }

                JSONObject fallbackUser = client.getUserByPhone(phone);
                if (fallbackUser != null) {
                    String fallbackId = fallbackUser.optString("id", "");
                    String fallbackName = fallbackUser.optString("display_name", "");
                    if (!fallbackId.trim().isEmpty()) {
                        String resolvedName = fallbackName.trim().isEmpty() ? name : fallbackName.trim();
                        persistLocally(fallbackId, resolvedName, phone, keyword, e1, e2, e3);
                        runOnUiThread(() -> {
                            setLoading(false);
                            goToMain();
                        });
                        return;
                    }
                }

                runOnUiThread(() -> {
                    setLoading(false);
                    Toast.makeText(RegisterActivity.this,
                            "Registration failed. Check your connection and try again.",
                            Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                android.util.Log.w("SafeSphere_REG", "Registration failed", e);
                runOnUiThread(() -> {
                    setLoading(false);
                    Toast.makeText(RegisterActivity.this,
                            "Registration failed. Check your connection and try again.",
                            Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private boolean validateAllFields(String name, String phone, String keyword, String e1, String e2, String e3) {
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

    private void persistLocally(String userId, String name, String phone, String keyword, String e1, String e2, String e3) {
        Prefs.setSupabaseUserId(getApplicationContext(), userId);
        Prefs.setUserName(getApplicationContext(), name);
        Prefs.setUserPhone(getApplicationContext(), phone);
        Prefs.setKeyword(getApplicationContext(), keyword);
        Prefs.setEmergency1(getApplicationContext(), e1);
        Prefs.setEmergency2(getApplicationContext(), e2);
        Prefs.setEmergency3(getApplicationContext(), e3);
        Prefs.setLoggedIn(getApplicationContext(), true);
    }

    private void setLoading(boolean loading) {
        btnRegister.setEnabled(!loading);
        btnRegister.setText(loading ? "Please wait..." : "Create Account");
        btnRegister.setBackgroundTintList(
            android.content.res.ColorStateList.valueOf(
                android.graphics.Color.parseColor("#C2185B")));
    }

    private void goToMain() {
        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }
}
