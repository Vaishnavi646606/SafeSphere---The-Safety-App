package com.example.safesphere;

public class EmergencyDecisionAPI {

    public static boolean shouldTriggerEmergency(
            boolean keywordDetected,
            boolean shakeDetected,
            boolean locationAvailable
    ) {
        int score = 0;

        if (keywordDetected) score += 5;
        if (shakeDetected) score += 3;
        if (locationAvailable) score += 2;

        return score >= 5;
    }
}
