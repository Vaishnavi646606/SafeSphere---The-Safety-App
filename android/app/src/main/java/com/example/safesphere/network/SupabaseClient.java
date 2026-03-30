package com.example.safesphere.network;

import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Supabase REST API client using OkHttp.
 * 
 * ⚠️ IMPORTANT: This is NON-BLOCKING and thread-safe.
 * All network calls are made on a background thread via ExecutorService in callers.
 * 
 * Usage:
 *   SupabaseClient client = SupabaseClient.getInstance();
 *   client.insertEmergencyEvent(eventData, callback);
 */
public class SupabaseClient {
    private static final String TAG = "SupabaseClient";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    
    private static SupabaseClient instance;
    private final OkHttpClient httpClient;
    private final String supabaseUrl;
    private final String supabaseAnonKey;
    private final Gson gson = new Gson();
    
    private SupabaseClient() {
        // Initialize from NetworkConfig (these should be set in BuildConfig values)
        this.supabaseUrl = "https://YOUR_SUPABASE_URL"; // Replace with your actual Supabase URL
        this.supabaseAnonKey = "YOUR_SUPABASE_ANON_KEY"; // Replace with anon key
        
        // Create OkHttp client with timeouts matching NetworkConfig
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(NetworkConfig.CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
                .readTimeout(NetworkConfig.READ_TIMEOUT_SEC, TimeUnit.SECONDS)
                .writeTimeout(NetworkConfig.WRITE_TIMEOUT_SEC, TimeUnit.SECONDS)
                .addInterceptor(chain -> {
                    // Add auth headers to all requests
                    Request original = chain.request();
                    Request withHeaders = original.newBuilder()
                            .header("Authorization", "Bearer " + supabaseAnonKey)
                            .header("apikey", supabaseAnonKey)
                            .header("Prefer", "return=representation")
                            .build();
                    return chain.proceed(withHeaders);
                })
                .build();
    }
    
    public static SupabaseClient getInstance() {
        if (instance == null) {
            synchronized (SupabaseClient.class) {
                if (instance == null) {
                    instance = new SupabaseClient();
                }
            }
        }
        return instance;
    }
    
    /**
     * Insert emergency event into Supabase.
     * Thread-safe: call from background thread only.
     */
    public SupabaseResponse insertEmergencyEvent(EmergencyEventData data) {
        try {
            String url = supabaseUrl + "/rest/v1/emergency_events";
            JsonObject json = new JsonObject();
            json.addProperty("user_id", data.userId);
            json.addProperty("trigger_type", data.triggerType);
            json.addProperty("triggered_at", data.triggeredAt);
            json.addProperty("session_id", data.sessionId);
            json.addProperty("location_lat", data.locationLat);
            json.addProperty("location_lng", data.locationLng);
            json.addProperty("status", "triggered");
            json.addProperty("phone_battery_percent", data.batteryPercent);
            json.addProperty("has_location_enabled", data.hasLocationEnabled);
            
            RequestBody body = RequestBody.create(json.toString(), JSON);
            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                return new SupabaseResponse(response.code(), response.isSuccessful(),
                        response.body() != null ? response.body().string() : "");
            }
        } catch (IOException e) {
            Log.e(TAG, "Error inserting emergency event", e);
            return new SupabaseResponse(0, false, e.getMessage());
        }
    }
    
    /**
     * Update emergency event status.
     * Thread-safe: call from background thread only.
     */
    public SupabaseResponse updateEmergencyEvent(String eventId, String status) {
        try {
            String url = supabaseUrl + "/rest/v1/emergency_events?id=eq." + eventId;
            JsonObject json = new JsonObject();
            json.addProperty("status", status);
            
            RequestBody body = RequestBody.create(json.toString(), JSON);
            Request request = new Request.Builder()
                    .url(url)
                    .patch(body)
                    .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                return new SupabaseResponse(response.code(), response.isSuccessful(),
                        response.body() != null ? response.body().string() : "");
            }
        } catch (IOException e) {
            Log.e(TAG, "Error updating emergency event", e);
            return new SupabaseResponse(0, false, e.getMessage());
        }
    }
    
    /**
     * Submit emergency feedback.
     * Thread-safe: call from background thread only.
     */
    public SupabaseResponse submitEmergencyFeedback(EmergencyFeedbackData data) {
        try {
            String url = supabaseUrl + "/rest/v1/emergency_feedback";
            JsonObject json = new JsonObject();
            json.addProperty("event_id", data.eventId);
            json.addProperty("user_id", data.userId);
            json.addProperty("was_real_emergency", data.wasRealEmergency);
            json.addProperty("was_rescued_or_helped", data.wasRescuedOrHelped);
            json.addProperty("rating", data.rating);
            json.addProperty("feedback_text", data.feedbackText);
            json.addProperty("submitted_at", System.currentTimeMillis());
            
            RequestBody body = RequestBody.create(json.toString(), JSON);
            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                return new SupabaseResponse(response.code(), response.isSuccessful(),
                        response.body() != null ? response.body().string() : "");
            }
        } catch (IOException e) {
            Log.e(TAG, "Error submitting feedback", e);
            return new SupabaseResponse(0, false, e.getMessage());
        }
    }
    
    /**
     * Log analytics event.
     * Thread-safe: call from background thread only.
     */
    public SupabaseResponse logAnalyticsEvent(AnalyticsEventData data) {
        try {
            String url = supabaseUrl + "/rest/v1/analytics_events";
            JsonObject json = new JsonObject();
            json.addProperty("user_id", data.userId);
            json.addProperty("event_type", data.eventType);
            json.addProperty("event_name", data.eventName);
            json.addProperty("session_id", data.sessionId);
            json.addProperty("payload", data.payloadJson);
            json.addProperty("client_timestamp", System.currentTimeMillis());
            
            RequestBody body = RequestBody.create(json.toString(), JSON);
            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                return new SupabaseResponse(response.code(), response.isSuccessful(),
                        response.body() != null ? response.body().string() : "");
            }
        } catch (IOException e) {
            Log.e(TAG, "Error logging analytics", e);
            return new SupabaseResponse(0, false, e.getMessage());
        }
    }
    
    // ============ DATA CLASSES ============
    
    public static class SupabaseResponse {
        public int statusCode;
        public boolean success;
        public String message;
        
        public SupabaseResponse(int code, boolean success, String message) {
            this.statusCode = code;
            this.success = success;
            this.message = message;
        }
    }
    
    public static class EmergencyEventData {
        public String userId;
        public String triggerType;
        public String triggeredAt;
        public String sessionId;
        public Double locationLat;
        public Double locationLng;
        public Integer batteryPercent;
        public Boolean hasLocationEnabled;
    }
    
    public static class EmergencyFeedbackData {
        public String eventId;
        public String userId;
        public Boolean wasRealEmergency;
        public Boolean wasRescuedOrHelped;
        public Integer rating;
        public String feedbackText;
    }
    
    public static class AnalyticsEventData {
        public String userId;
        public String eventType;
        public String eventName;
        public String sessionId;
        public String payloadJson;
    }
}
