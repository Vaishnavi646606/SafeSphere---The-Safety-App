package com.example.safesphere.analytics;

import android.content.Context;
import android.util.Log;

import com.example.safesphere.Prefs;
import com.example.safesphere.network.SupabaseClient;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Supabase Analytics Integration
 * 
 * Tracks app events and sends them to Supabase emergency_events + analytics_events tables.
 * 
 * Thread-safe: safe to call from any thread.
 * Non-blocking: all network calls happen on background ExecutorService.
 * 
 * Events tracked:
 * - app_open, app_close
 * - emergency_triggered, emergency_called, emergency_answered
 * - service_started, service_stopped
 * - keyword_detected, shake_detected
 * - feedback_submitted
 */
public class SupabaseAnalytics {
    
    private static final String TAG = "SupabaseAnalytics";
    private static volatile SupabaseAnalytics instance;
    
    private final Context appContext;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final SupabaseClient supabaseClient;
    
    private String currentSessionId;
    
    private SupabaseAnalytics(Context context) {
        this.appContext = context.getApplicationContext();
        this.supabaseClient = SupabaseClient.getInstance();
        this.currentSessionId = UUID.randomUUID().toString();
    }
    
    public static SupabaseAnalytics getInstance(Context context) {
        if (instance == null) {
            synchronized (SupabaseAnalytics.class) {
                if (instance == null) {
                    instance = new SupabaseAnalytics(context);
                }
            }
        }
        return instance;
    }
    
    // ============================================================================
    // PUBLIC EVENT LOGGING METHODS
    // ============================================================================
    
    /**
     * Track app open event
     */
    public void logAppOpen() {
        logEvent("app_open", "App Opened", null);
    }
    
    /**
     * Track app close event
     */
    public void logAppClose() {
        logEvent("app_close", "App Closed", null);
    }
    
    /**
     * Track service started
     */
    public void logServiceStarted() {
        logEvent("service_started", "SafeSphere Service Started", null);
    }
    
    /**
     * Track service stopped
     */
    public void logServiceStopped() {
        logEvent("service_stopped", "SafeSphere Service Stopped", null);
    }
    
    /**
     * Track keyword detection
     */
    public void logKeywordDetected(String keyword) {
        SupabaseClient.AnalyticsEventData data = new SupabaseClient.AnalyticsEventData();
        data.userId = Prefs.getUserId(appContext);
        data.eventType = "keyword_detected";
        data.eventName = "Keyword Detected: " + keyword;
        data.sessionId = currentSessionId;
        data.payloadJson = "{\"keyword\":\"" + keyword + "\"}";
        
        executor.execute(() -> {
            try {
                supabaseClient.logAnalyticsEvent(data);
                Log.d(TAG, "Logged keyword detection");
            } catch (Exception e) {
                Log.e(TAG, "Error logging keyword detection", e);
                // Don't block app execution on analytics failure
            }
        });
    }
    
    /**
     * Track shake detection
     */
    public void logShakeDetected() {
        logEvent("shake_detected", "Shake Detected", null);
    }
    
    /**
     * Log an emergency event directly to emergency_events table
     */
    public void logEmergencyTriggered(String triggerType, Double lat, Double lng,
                                     Integer batteryPercent, Boolean hasLocation) {
        executor.execute(() -> {
            try {
                String userId = Prefs.getUserId(appContext);
                if (userId == null) {
                    Log.w(TAG, "Cannot log emergency: userId is null");
                    return;
                }
                
                SupabaseClient.EmergencyEventData eventData = new SupabaseClient.EmergencyEventData();
                eventData.userId = userId;
                eventData.triggerType = triggerType;
                eventData.triggeredAt = getCurrentTimestamp();
                eventData.sessionId = currentSessionId;
                eventData.locationLat = lat;
                eventData.locationLng = lng;
                eventData.batteryPercent = batteryPercent;
                eventData.hasLocationEnabled = hasLocation;
                
                SupabaseClient.SupabaseResponse response = supabaseClient.insertEmergencyEvent(eventData);
                if (response.success) {
                    Log.d(TAG, "Emergency event logged: " + eventData.triggerType);
                } else {
                    Log.e(TAG, "Failed to log emergency event: " + response.message);
                    // Don't propagate to UI - emergency already triggered locally
                }
            } catch (Exception e) {
                Log.e(TAG, "Error logging emergency event", e);
                // App must continue with emergency call regardless of Supabase status
            }
        });
    }
    
    /**
     * Log emergency contact called
     */
    public void logEmergencyContactCalled(String phoneNumber) {
        SupabaseClient.AnalyticsEventData data = new SupabaseClient.AnalyticsEventData();
        data.userId = Prefs.getUserId(appContext);
        data.eventType = "emergency_contact_called";
        data.eventName = "Called Emergency Contact";
        data.sessionId = currentSessionId;
        data.payloadJson = "{\"phone\":\"" + maskPhoneNumber(phoneNumber) + "\"}";
        
        executor.execute(() -> {
            try {
                supabaseClient.logAnalyticsEvent(data);
            } catch (Exception e) {
                Log.e(TAG, "Error logging contact call", e);
            }
        });
    }
    
    /**
     * Log feedback submission
     */
    public void logFeedbackSubmitted(int rating, boolean wasReal, boolean wasRescued) {
        SupabaseClient.AnalyticsEventData data = new SupabaseClient.AnalyticsEventData();
        data.userId = Prefs.getUserId(appContext);
        data.eventType = "feedback_submitted";
        data.eventName = "Feedback Submitted - Rating: " + rating;
        data.sessionId = currentSessionId;
        data.payloadJson = "{\"rating\":" + rating + ",\"was_real\":" + wasReal +
                ",\"was_rescued\":" + wasRescued + "}";
        
        executor.execute(() -> {
            try {
                supabaseClient.logAnalyticsEvent(data);
            } catch (Exception e) {
                Log.e(TAG, "Error logging feedback", e);
            }
        });
    }
    
    /**
     * Log fake call used
     */
    public void logFakeCallUsed() {
        logEvent("fake_call_used", "Fake Call Used", null);
    }
    
    /**
     * Log profile update
     */
    public void logProfileUpdated() {
        logEvent("profile_updated", "User Profile Updated", null);
    }
    
    /**
     * Log login
     */
    public void logLogin(String userName) {
        SupabaseClient.AnalyticsEventData data = new SupabaseClient.AnalyticsEventData();
        data.userId = Prefs.getUserId(appContext);
        data.eventType = "login";
        data.eventName = "User Login";
        data.sessionId = currentSessionId;
        data.payloadJson = "{\"user\":\"" + userName + "\"}";
        
        executor.execute(() -> {
            try {
                supabaseClient.logAnalyticsEvent(data);
            } catch (Exception e) {
                Log.e(TAG, "Error logging login", e);
            }
        });
    }
    
    /**
     * Log logout
     */
    public void logLogout() {
        logEvent("logout", "User Logout", null);
    }
    
    // ============================================================================
    // HELPER METHODS
    // ============================================================================
    
    /**
     * Generic event logging
     */
    private void logEvent(String eventType, String eventName, String payloadJson) {
        SupabaseClient.AnalyticsEventData data = new SupabaseClient.AnalyticsEventData();
        data.userId = Prefs.getUserId(appContext);
        data.eventType = eventType;
        data.eventName = eventName;
        data.sessionId = currentSessionId;
        data.payloadJson = payloadJson != null ? payloadJson : "{}";
        
        executor.execute(() -> {
            try {
                supabaseClient.logAnalyticsEvent(data);
                Log.d(TAG, "Logged event: " + eventType);
            } catch (Exception e) {
                Log.e(TAG, "Error logging event " + eventType, e);
                // Don't propagate - analytics failures should never block app
            }
        });
    }
    
    /**
     * Get current timestamp in ISO 8601 format
     */
    private String getCurrentTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        return sdf.format(new Date());
    }
    
    /**
     * Mask phone number for privacy (NXX-XXX-XXXX)
     */
    private String maskPhoneNumber(String phone) {
        if (phone == null || phone.length() < 7) {
            return "XXXXXXXXXX";
        }
        return phone.substring(0, 3) + "XXXX" + phone.substring(phone.length() - 3);
    }
    
    /**
     * Reset session (e.g., on app restart)
     */
    public void resetSession() {
        this.currentSessionId = UUID.randomUUID().toString();
        Log.d(TAG, "Session reset: " + currentSessionId);
    }
    
    /**
     * Get current session ID
     */
    public String getSessionId() {
        return currentSessionId;
    }
}
