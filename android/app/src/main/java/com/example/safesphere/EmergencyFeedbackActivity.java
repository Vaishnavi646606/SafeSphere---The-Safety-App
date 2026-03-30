package com.example.safesphere;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

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
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_emergency_feedback);
        
        // Get extras from intent
        Intent intent = getIntent();
        eventId = intent.getStringExtra(EXTRA_EVENT_ID);
        userId = intent.getStringExtra(EXTRA_USER_ID);
        triggerType = intent.getStringExtra(EXTRA_TRIGGER_TYPE);
        
        if (eventId == null || userId == null) {
            Log.e(TAG, "Missing required intent extras");
            finish();
            return;
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
            btnRealYes.setbackgroundColor(getColor(R.color.primary));
            btnRealNo.setBackgroundColor(getColor(R.color.gray_light));
            updateSubmitButtonState();
        });
        
        btnRealNo.setOnClickListener(v -> {
            wasRealEmergency = false;
            btnRealNo.setBackgroundColor(getColor(R.color.primary));
            btnRealYes.setBackgroundColor(getColor(R.color.gray_light));
            updateSubmitButtonState();
        });
        
        // Set question 2 listeners
        btnRescuedYes.setOnClickListener(v -> {
            wasRescued = true;
            btnRescuedYes.setBackgroundColor(getColor(R.color.primary));
            btnRescuedNo.setBackgroundColor(getColor(R.color.gray_light));
            updateSubmitButtonState();
        });
        
        btnRescuedNo.setOnClickListener(v -> {
            wasRescued = false;
            btnRescuedNo.setBackgroundColor(getColor(R.color.primary));
            btnRescuedYes.setBackgroundColor(getColor(R.color.gray_light));
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
        
        String feedbackText = etFeedback.getText().toString().trim();
        
        // Create feedback data
        SupabaseClient.EmergencyFeedbackData feedbackData = new SupabaseClient.EmergencyFeedbackData();
        feedbackData.eventId = eventId;
        feedbackData.userId = userId;
        feedbackData.wasRealEmergency = wasRealEmergency;
        feedbackData.wasRescuedOrHelped = wasRescued;
        feedbackData.rating = rating;
        feedbackData.feedbackText = feedbackText;
        
        // Submit on background thread
        new Thread(() -> {
            try {
                SupabaseClient client = SupabaseClient.getInstance();
                SupabaseClient.SupabaseResponse response = client.submitEmergencyFeedback(feedbackData);
                
                runOnUiThread(() -> {
                    if (response.success) {
                        Toast.makeText(EmergencyFeedbackActivity.this,
                                "Thank you for your feedback!", Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "Feedback submitted successfully: " + response.message);
                        finish();
                    } else {
                        Toast.makeText(EmergencyFeedbackActivity.this,
                                "Failed to submit feedback: " + response.message,
                                Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Feedback submission failed: " + response.message);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error submitting feedback", e);
                runOnUiThread(() -> Toast.makeText(EmergencyFeedbackActivity.this,
                        "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
    
    private void updateSubmitButtonState() {
        boolean hasRating = rating > 0;
        btnSubmit.setEnabled(hasRating);
        btnSubmit.setAlpha(hasRating ? 1.0f : 0.5f);
    }
}
