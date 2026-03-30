package com.example.safesphere.analytics;

/**
 * All analytics event type string constants.
 * Matches the server-side CHECK constraint in analytics_events table.
 */
public final class EventType {
    private EventType() {}

    public static final String REGISTRATION          = "registration";
    public static final String LOGIN                 = "login";
    public static final String PROTECTION_ENABLED    = "protection_enabled";
    public static final String PROTECTION_DISABLED   = "protection_disabled";
    public static final String TRIGGER_SOURCE        = "trigger_source";    // payload: {source}
    public static final String SMS_SENT              = "sms_sent";
    public static final String CALL_ATTEMPT          = "call_attempt";      // payload: {contact_index}
    public static final String CALL_CONNECTED        = "call_connected";    // payload: {contact_index, duration_seconds}
    public static final String LOCATION_SHARED       = "location_shared";
    public static final String SESSION_END           = "session_end";
    public static final String SAFE_ACKNOWLEDGED     = "safe_acknowledged";
    public static final String ADMIN_MSG_RECEIVED    = "admin_message_received";
    public static final String REVOCATION_DETECTED   = "revocation_detected";
    public static final String APP_FOREGROUNDED      = "app_foregrounded";
    public static final String APP_BACKGROUNDED      = "app_backgrounded";
}
