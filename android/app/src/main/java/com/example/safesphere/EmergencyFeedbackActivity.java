package com.example.safesphere;

import android.content.res.ColorStateList;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.safesphere.network.SupabaseClient;

/**
 * Activity shown after an emergency event to collect user feedback.
 * 
 * This activity:
 * 1. Displays questions about the emergency
 * 2. Collects user ratings (1-5 stars)
 * 3. Submits feedback to Supabase
 * 4. Allows user to skip/dismiss
 * 
 * Launched from SafeSphereService or via notification 5 minutes after emergency resolves
 */
public class EmergencyFeedbackActivity extends AppCompatActivity {
    
    private static final String TAG = "EmergencyFeedback";
    
    // Intent extras
    public static final String EXTRA_EVENT_ID = "event_id";
    public static final String EXTRA_USER_ID = "user_id";
    public static final String EXTRA_TRIGGER_TYPE = "trigger_type";
    
    // UI components
    private TextView tvTitle;
    private TextView tvQuestion1;
    private Button btnRealYes;
    private Button btnRealNo;
    
    private TextView tvQuestion2;
    private Button btnRescuedYes;
    private Button btnRescuedNo;
    
    private RatingBar ratingBar;
    private EditText etFeedback;
    
    private Button btnSubmit;
    private Button btnSkip;
    
    // State
    private String eventId;
    private String userId;
    private String triggerType;
    
    private boolean wasRealEmergency = false;
    private boolean wasRescued = false;
    private int rating = 0;

    private interface ResolveCallback {
        void onResult(boolean success);
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_emergency_feedback);
        
        // Get extras from intent
        Intent intent = getIntent();
        String eventId = getIntent().getStringExtra("event_id");
        this.eventId = eventId;
        userId = intent.getStringExtra(EXTRA_USER_ID);
        triggerType = intent.getStringExtra(EXTRA_TRIGGER_TYPE);

        if (eventId == null || eventId.trim().isEmpty()) {
            eventId = Prefs.getLastEmergencyEventId(this);
            this.eventId = eventId;
        }
        if (userId == null || userId.trim().isEmpty()) {
            userId = Prefs.getSupabaseUserId(this);
        }
        
        if (eventId == null || eventId.trim().isEmpty()) {
            Toast.makeText(this, "No emergency event to provide feedback for", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Missing event_id for feedback");
            Intent intentToMain = new Intent(this, MainActivity.class);
            intentToMain.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intentToMain);
            finish();
            return;
        }

        if (userId == null || userId.trim().isEmpty()) {
            resolveUserIdIfMissing(success -> {
                if (!success) {
                    Log.w(TAG, "Unable to resolve user_id on feedback open");
                }
            });
        }
        
        initializeUI();
    }
    
    private void initializeUI() {
        tvTitle = findViewById(R.id.tvFeedbackTitle);
        tvQuestion1 = findViewById(R.id.tvQuestion1);
        btnRealYes = findViewById(R.id.btnRealYes);
        btnRealNo = findViewById(R.id.btnRealNo);
        
        tvQuestion2 = findViewById(R.id.tvQuestion2);
        btnRescuedYes = findViewById(R.id.btnRescuedYes);
        btnRescuedNo = findViewById(R.id.btnRescuedNo);
        
        ratingBar = findViewById(R.id.ratingBar);
        etFeedback = findViewById(R.id.etFeedbackText);
        
        btnSubmit = findViewById(R.id.btnSubmitFeedback);
        btnSkip = findViewById(R.id.btnSkipFeedback);
        
        // Set question 1 listeners
        btnRealYes.setOnClickListener(v -> {
            wasRealEmergency = true;
            btnRealYes.setBackgroundTintList(
                ColorStateList.valueOf(ContextCompat.getColor(this, R.color.primary))
            );
            btnRealNo.setBackgroundTintList(
                ColorStateList.valueOf(ContextCompat.getColor(this, R.color.gray_light))
            );
            updateSubmitButtonState();
        });
        
        btnRealNo.setOnClickListener(v -> {
            wasRealEmergency = false;
            btnRealNo.setBackgroundTintList(
                ColorStateList.valueOf(ContextCompat.getColor(this, R.color.primary))
            );
            btnRealYes.setBackgroundTintList(
                ColorStateList.valueOf(ContextCompat.getColor(this, R.color.gray_light))
            );
            updateSubmitButtonState();
        });
        
        // Set question 2 listeners
        btnRescuedYes.setOnClickListener(v -> {
            wasRescued = true;
            btnRescuedYes.setBackgroundTintList(
                ColorStateList.valueOf(ContextCompat.getColor(this, R.color.primary))
            );
            btnRescuedNo.setBackgroundTintList(
                ColorStateList.valueOf(ContextCompat.getColor(this, R.color.gray_light))
            );
            updateSubmitButtonState();
        });
        
        btnRescuedNo.setOnClickListener(v -> {
            wasRescued = false;
            btnRescuedNo.setBackgroundTintList(
                ColorStateList.valueOf(ContextCompat.getColor(this, R.color.primary))
            );
            btnRescuedYes.setBackgroundTintList(
                ColorStateList.valueOf(ContextCompat.getColor(this, R.color.gray_light))
            );
            updateSubmitButtonState();
        });
        
        // Rating bar listener
        ratingBar.setOnRatingBarChangeListener((ratingBar, rating, fromUser) -> {
            this.rating = (int) rating;
            updateSubmitButtonState();
        });
        
        // Submit button
        btnSubmit.setOnClickListener(v -> submitFeedback());
        
        // Skip button
        btnSkip.setOnClickListener(v -> {
            Log.d(TAG, "User skipped feedback");
            Intent intentToMain = new Intent(this, MainActivity.class);
            intentToMain.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intentToMain);
            finish();
        });
        
        // Set title
        tvTitle.setText("How did the emergency go?");
    }
    
    private void submitFeedback() {
        if (rating == 0) {
            Toast.makeText(this, "Please select a rating", Toast.LENGTH_SHORT).show();
            return;
        }

        if (eventId == null || eventId.trim().isEmpty()) {
            Toast.makeText(this, "No emergency event to provide feedback for", Toast.LENGTH_LONG).show();
            return;
        }

        if (userId == null || userId.trim().isEmpty()) {
            resolveUserIdIfMissing(success -> {
                if (success) {
                    submitFeedback();
                } else {
                    Toast.makeText(this, "Unable to resolve account for feedback", Toast.LENGTH_LONG).show();
                }
            });
            return;
        }
        
        String feedbackText = etFeedback.getText().toString().trim();
        Log.d("EmergencyFeedback", "Submitting feedback for eventId: " + eventId);
        
        // Create feedback data
        SupabaseClient.EmergencyFeedbackData feedbackData = new SupabaseClient.EmergencyFeedbackData();
        feedbackData.eventId = eventId;
        feedbackData.userId = userId;
        feedbackData.wasRealEmergency = wasRealEmergency;
        feedbackData.wasRescuedOrHelped = wasRescued;
        feedbackData.rating = rating;
        feedbackData.feedbackText = feedbackText;
        
        // Check network before attempting submission
        android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
        android.net.NetworkInfo ni = cm.getActiveNetworkInfo();
        boolean isOnline = ni != null && ni.isConnected();

        if (!isOnline) {
            // Offline — queue for later sync
            Prefs.setFeedbackSyncPending(this, true);
            SyncWorker.scheduleSyncWhenOnline(this);
            Prefs.setPendingFeedbackData(this, eventId, userId,
                    wasRealEmergency, wasRescued, rating, feedbackText);
            Toast.makeText(this,
                    "Offline — feedback saved and will submit automatically when connected.",
                    Toast.LENGTH_LONG).show();
            Intent intentToMain = new Intent(EmergencyFeedbackActivity.this, MainActivity.class);
            intentToMain.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intentToMain);
            finish();
            return;
        }

        // Online — submit now
        new Thread(() -> {
            try {
                SupabaseClient client = SupabaseClient.getInstance();
                SupabaseClient.SupabaseResponse response = client.submitEmergencyFeedback(feedbackData);

                runOnUiThread(() -> {
                    if (response.success) {
                        // Clear any pending flag since we just submitted successfully
                        Prefs.clearPendingFeedbackData(EmergencyFeedbackActivity.this);
                        Toast.makeText(EmergencyFeedbackActivity.this,
                                "Thank you for your feedback!", Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "Feedback submitted successfully: " + response.message);
                        Intent intentToMain = new Intent(EmergencyFeedbackActivity.this, MainActivity.class);
                        intentToMain.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        startActivity(intentToMain);
                        finish();
                    } else {
                        // Supabase error — queue for retry
                        Prefs.setFeedbackSyncPending(EmergencyFeedbackActivity.this, true);
                        SyncWorker.scheduleSyncWhenOnline(EmergencyFeedbackActivity.this);
                        Prefs.setPendingFeedbackData(EmergencyFeedbackActivity.this,
                                eventId, userId, wasRealEmergency, wasRescued, rating, feedbackText);
                        Toast.makeText(EmergencyFeedbackActivity.this,
                                "Saved locally. Will submit when connected.",
                                Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Feedback submission failed: " + response.message);
                        Intent intentToMain = new Intent(EmergencyFeedbackActivity.this, MainActivity.class);
                        intentToMain.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        startActivity(intentToMain);
                        finish();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error submitting feedback", e);
                // Exception — queue for retry
                Prefs.setFeedbackSyncPending(EmergencyFeedbackActivity.this, true);
                SyncWorker.scheduleSyncWhenOnline(EmergencyFeedbackActivity.this);
                Prefs.setPendingFeedbackData(EmergencyFeedbackActivity.this,
                        eventId, userId, wasRealEmergency, wasRescued, rating, feedbackText);
                runOnUiThread(() -> {
                    Toast.makeText(EmergencyFeedbackActivity.this,
                            "Saved locally. Will submit when connected.",
                            Toast.LENGTH_SHORT).show();
                    Intent intentToMain = new Intent(EmergencyFeedbackActivity.this, MainActivity.class);
                    intentToMain.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intentToMain);
                    finish();
                });
            }
        }).start();
    }

    private void resolveUserIdIfMissing(ResolveCallback callback) {
        if (userId != null && !userId.trim().isEmpty()) {
            callback.onResult(true);
            return;
        }

        final String phone = Prefs.getUserPhone(this);
        if (phone == null || phone.trim().isEmpty()) {
            callback.onResult(false);
            return;
        }

        new Thread(() -> {
            boolean success = false;
            try {
                SupabaseClient client = SupabaseClient.getInstance(getApplicationContext());
                org.json.JSONObject user = client.getUserByPhone(phone);
                if (user != null) {
                    String resolvedId = user.optString("id", null);
                    if (resolvedId != null && !resolvedId.trim().isEmpty()) {
                        userId = resolvedId;
                        Prefs.setSupabaseUserId(getApplicationContext(), resolvedId);
                        success = true;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to resolve user_id for feedback", e);
            }

            final boolean result = success;
            runOnUiThread(() -> callback.onResult(result));
        }).start();
    }
    
    private void updateSubmitButtonState() {
        boolean hasRating = rating > 0;
        btnSubmit.setEnabled(hasRating);
        btnSubmit.setAlpha(hasRating ? 1.0f : 0.5f);
    }
}
