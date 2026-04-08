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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class LoginActivity extends AppCompatActivity {

    private EditText etLoginPhone;
    private Button btnLogin;
    private Button btnGoRegister;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String savedPhone = Prefs.getUserPhone(this);
        boolean isLoggedIn = Prefs.isLoggedIn(this);
        if (isLoggedIn && savedPhone != null && savedPhone.matches("\\d{10}")) {
            attemptAutoLogin(savedPhone);
            return;
        }

        setContentView(R.layout.activity_login);
        initViews();
    }

    private void initViews() {
        etLoginPhone = findViewById(R.id.etLoginPhone);
        btnLogin = findViewById(R.id.btnLogin);
        btnGoRegister = findViewById(R.id.btnGoRegister);

        btnLogin.setOnClickListener(v -> doLogin());
        btnGoRegister.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, RegisterActivity.class)));
    }

    private void attemptAutoLogin(String savedPhone) {
        new Thread(() -> {
            try {
                // -- Check connectivity FIRST -- never make network call when offline --
                android.net.ConnectivityManager cm =
                        (android.net.ConnectivityManager)
                        getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
                android.net.NetworkInfo ni =
                        cm != null ? cm.getActiveNetworkInfo() : null;
                boolean isOnline = ni != null && ni.isConnected();

                // -- OFFLINE: already logged in -> use cached credentials ---------
                if (!isOnline) {
                    String cachedUserId = Prefs.getSupabaseUserId(getApplicationContext());
                    boolean hasCache = cachedUserId != null && !cachedUserId.trim().isEmpty();
                    if (hasCache) {
                        android.util.Log.d("SafeSphere_LOGIN",
                                "Offline auto-login: using cached credentials, userId=" + cachedUserId);
                        runOnUiThread(this::goToMain);
                    } else {
                        // No cached userId -- cannot verify who this is offline
                        // Show login screen but do NOT call setLoggedIn(false)
                        android.util.Log.w("SafeSphere_LOGIN",
                                "Offline and no cached userId -- showing login screen without logout");
                        runOnUiThread(() -> {
                            setContentView(R.layout.activity_login);
                            initViews();
                            Toast.makeText(LoginActivity.this,
                                    "No internet connection. Please connect to continue.",
                                    Toast.LENGTH_LONG).show();
                        });
                    }
                    return;
                }

                // -- ONLINE: verify credentials with Supabase --------------------
                SupabaseClient client = SupabaseClient.getInstance(getApplicationContext());
                JSONObject user = client.getUserProfileByPhone(savedPhone);
                String userId = user != null ? user.optString("id", "") : "";

                if (user != null && userId != null && !userId.trim().isEmpty()) {
                    // Account confirmed online -- refresh all cached data
                    String displayName = user.optString("display_name", "");
                    String keyword = user.optString("keyword", "");
                    String e1 = user.optString("emergency_contact_1", "");
                    String e2 = user.optString("emergency_contact_2", "");
                    String e3 = user.optString("emergency_contact_3", "");

                    Prefs.setSupabaseUserId(getApplicationContext(), userId);
                    if (!displayName.isEmpty()) {
                        Prefs.setUserName(getApplicationContext(), displayName);
                    }
                    if (!keyword.isEmpty()) {
                        Prefs.setKeyword(getApplicationContext(), keyword);
                    }
                    if (!e1.isEmpty()) {
                        Prefs.setEmergency1(getApplicationContext(), e1);
                    }
                    if (!e2.isEmpty()) {
                        Prefs.setEmergency2(getApplicationContext(), e2);
                    }
                    if (!e3.isEmpty()) {
                        Prefs.setEmergency3(getApplicationContext(), e3);
                    }
                    Prefs.setUserPhone(getApplicationContext(), savedPhone);
                    Prefs.setLoggedIn(getApplicationContext(), true);

                    if (keyword.isEmpty() || e1.isEmpty()) {
                        Prefs.setNeedsProfileSetup(getApplicationContext(), true);
                    }

                    JSONObject updateData = new JSONObject();
                    updateData.put("last_app_open", nowIsoUtc());
                    client.updateRow("users", "phone_hash", savedPhone, updateData);

                    runOnUiThread(this::goToMain);
                    return;
                }

                // -- Online but account not found -> truly gone from Supabase ----
                // ONLY here do we call setLoggedIn(false)
                Prefs.setLoggedIn(getApplicationContext(), false);
                Prefs.setSupabaseUserId(getApplicationContext(), null);
                runOnUiThread(() -> {
                    setContentView(R.layout.activity_login);
                    initViews();
                    Toast.makeText(LoginActivity.this,
                            "Account not found. Please register again.",
                            Toast.LENGTH_LONG).show();
                });

            } catch (Exception e) {
                // Network exception -- do NOT log out
                // Just show login screen so user can retry
                android.util.Log.w("SafeSphere_LOGIN", "Auto-login network error", e);
                runOnUiThread(() -> {
                    setContentView(R.layout.activity_login);
                    initViews();
                    Toast.makeText(LoginActivity.this,
                            "Connection error. Please check your internet and try again.",
                            Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void doLogin() {
        String phone = etLoginPhone.getText().toString().trim();
        if (TextUtils.isEmpty(phone) || !phone.matches("\\d{10}")) {
            etLoginPhone.setError("Enter a valid 10-digit mobile number");
            etLoginPhone.requestFocus();
            return;
        }

        setLoading(true);

        new Thread(() -> {
            try {
                SupabaseClient client = SupabaseClient.getInstance(getApplicationContext());
                // Check connectivity before Supabase call
                android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                        getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
                android.net.NetworkInfo ni = cm.getActiveNetworkInfo();
                boolean isOnline = ni != null && ni.isConnected();

                if (!isOnline) {
                    runOnUiThread(() -> {
                        setLoading(false);
                        Toast.makeText(LoginActivity.this,
                                "No internet connection. Please check your connection and try again.",
                                Toast.LENGTH_LONG).show();
                    });
                    return;
                }

                JSONObject user = client.getUserProfileByPhone(phone);

                if (user == null) {
                    runOnUiThread(() -> {
                        setLoading(false);
                        Toast.makeText(LoginActivity.this,
                                "No account found with this number. Please register first.",
                                Toast.LENGTH_LONG).show();
                    });
                    return;
                }

                String userId = user.optString("id", "");
                String displayName = user.optString("display_name", "");
                String keyword = user.optString("keyword", "");
                String e1 = user.optString("emergency_contact_1", "");
                String e2 = user.optString("emergency_contact_2", "");
                String e3 = user.optString("emergency_contact_3", "");

                Prefs.setUserPhone(getApplicationContext(), phone);
                Prefs.setSupabaseUserId(getApplicationContext(), userId);
                if (!displayName.isEmpty()) {
                    Prefs.setUserName(getApplicationContext(), displayName);
                }
                if (!keyword.isEmpty()) {
                    Prefs.setKeyword(getApplicationContext(), keyword);
                }
                if (!e1.isEmpty()) {
                    Prefs.setEmergency1(getApplicationContext(), e1);
                }
                if (!e2.isEmpty()) {
                    Prefs.setEmergency2(getApplicationContext(), e2);
                }
                if (!e3.isEmpty()) {
                    Prefs.setEmergency3(getApplicationContext(), e3);
                }
                Prefs.setLoggedIn(getApplicationContext(), true);

                // Check if profile setup is needed
                if (keyword.isEmpty() || e1.isEmpty()) {
                    Prefs.setNeedsProfileSetup(getApplicationContext(), true);
                }

                JSONObject updateData = new JSONObject();
                updateData.put("last_app_open", nowIsoUtc());
                client.updateRow("users", "phone_hash", phone, updateData);

                runOnUiThread(this::goToMain);
            } catch (Exception e) {
                android.util.Log.w("SafeSphere_LOGIN", "Login failed", e);
                runOnUiThread(() -> {
                    setLoading(false);
                    Toast.makeText(LoginActivity.this, "Login failed. Check your connection and try again.", Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void setLoading(boolean loading) {
        btnLogin.setEnabled(!loading);
        btnLogin.setText(loading ? "Checking..." : "Login");
        btnLogin.setBackgroundTintList(
            android.content.res.ColorStateList.valueOf(
                android.graphics.Color.parseColor("#C2185B")));
        btnGoRegister.setEnabled(!loading);
        btnGoRegister.setBackgroundTintList(
            android.content.res.ColorStateList.valueOf(
                android.graphics.Color.parseColor("#880E4F")));
    }

    private static String nowIsoUtc() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date());
    }

    private void goToMain() {
        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }
}
