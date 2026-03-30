# Skill: Emergency Call System

## Description
Comprehensive skill for understanding and modifying the SafeSphere emergency call system. This is a **CRITICAL PATH** component that requires extreme care and thorough testing.

## When to Use
- Fixing emergency call handling bugs
- Improving call fallback logic
- Optimizing call detection (phone state listeners)
- Testing emergency scenarios
- Working with EmergencyManager.java, PhoneStateReceiver.java, EmergencyActionOptimizer.java

## Prerequisites
- Deep understanding of Android lifecycle
- Understanding of phone state changes (RINGING, OFFHOOK, IDLE)
- Understanding of SharedPreferences persistence
- Understanding of background services (SafeSphereService)

## Important: Critical Path Rules
- ✅ **Every change must be tested with real emergency scenarios**
- ✅ **All edge cases must be covered** (network loss, rapid calls, app crash/restart)
- ✅ **Must not block UI thread** (use background threads)
- ✅ **Must handle permission errors gracefully**
- ✅ **Must log all state changes** for post-incident debugging
- ✅ **Must survive app crashes** (use SharedPreferences for state)

## Architecture Overview

**Files involved:**
- [app/src/main/java/com/example/safesphere/EmergencyManager.java](app/src/main/java/com/example/safesphere/EmergencyManager.java) — Main logic
- [app/src/main/java/com/example/safesphere/SafeSphereService.java](app/src/main/java/com/example/safesphere/SafeSphereService.java) — Background service
- [app/src/main/java/com/example/safesphere/PhoneStateReceiver.java](app/src/main/java/com/example/safesphere/PhoneStateReceiver.java) — Phone state detection
- [app/src/main/java/com/example/safesphere/EmergencyDecisionAPI.java](app/src/main/java/com/example/safesphere/EmergencyDecisionAPI.java) — Trigger scoring
- [app/src/main/java/com/example/safesphere/EmergencyActionOptimizer.java](app/src/main/java/com/example/safesphere/EmergencyActionOptimizer.java) — Battery-aware action selection
- [app/src/main/java/com/example/safesphere/Prefs.java](app/src/main/java/com/example/safesphere/Prefs.java) — State persistence

**Flow:**
```
Trigger Detected (keyword/shake/location)
    ↓
EmergencyDecisionAPI.shouldTrigger() [score ≥ 5]
    ↓
SafeSphereService starts emergency sequence
    ↓
EmergencyManager.initiateEmergency()
    ↓
Call Contact 1
    ↓
PhoneStateReceiver detects RINGING → IDLED
    ↓
Not answered? → Wait 3s → Call Contact 2
    ↓
Not answered? → Wait 3s → Call Contact 3
    ↓
Not answered? → Send SMS to all 3
    ↓  
Share location + log incident
```

## Key Components

### 1. Trigger Scoring (EmergencyDecisionAPI.java)

**Scoring rules:**
- Keyword detected: +5 points
- Shake detected: +3 points  
- Location available: +2 points
- **Threshold:** ≥5 points triggers emergency

**Check before modifying:**
```java
// File: app/src/main/java/com/example/safesphere/EmergencyDecisionAPI.java

int score = 0;
if (keywordDetected) score += 5;  // KEYWORD_SCORE = 5
if (shakeDetected) score += 3;    // SHAKE_SCORE = 3
if (locationAvailable) score += 2; // LOCATION_SCORE = 2

if (score >= 5) { // TRIGGER_THRESHOLD = 5
  // Initiate emergency
}
```

**How to modify scoring:**
- Change weights? Verify impact on false positive rate
- Change threshold? Must test with realistic detection patterns
- Add new trigger? Must test with SafeSphereService integration

### 2. Contact Sequence (EmergencyManager.java)

**Contacts stored in SharedPreferences:**
```java
String contact1 = Prefs.getEmergency1(context);  // Primary
String contact2 = Prefs.getEmergency2(context);  // Secondary
String contact3 = Prefs.getEmergency3(context);  // Tertiary

// Current position tracked
int currentIndex = Prefs.getCallSequenceIndex(context); // 0, 1, or 2
```

**Call sequence logic:**
```
Prefs.setCallSequenceIndex(context, 0) → Call contact 1
Wait 2s → PhoneStateReceiver monitors RINGING
Wait max 30s for OFFHOOK
If not answered after 3s idle → Mark hasSeenActive = false
Wait 2s → Move to index 1 → Call contact 2
Repeat for contact 3
All failed? → Send SMS
```

**State machine in PhoneStateReceiver:**
```java
// Manifest-registered receiver (survives process death)
// File: app/src/main/java/com/example/safesphere/PhoneStateReceiver.java

TelephonyManager.CALL_STATE_RINGING
  → Set: hasSeenActive = false (in SharedPreferences)

TelephonyManager.CALL_STATE_OFFHOOK
  → Set: hasSeenActive = true (answered!)

TelephonyManager.CALL_STATE_IDLE
  → Check: Did user answer (hasSeenActive==true)?
  → If NO: nextContactIndex++, wait 2s, call next
  → If YES: Continue current call, log success
```

**How to modify contact logic:**
- Add more contacts (4th, 5th)? Update Prefs.java + loop in EmergencyManager
- Change wait times (2s, 3s)? Must test with real phone behavior
- Add new notification between contacts? Must not block main thread

### 3. Battery Optimization (EmergencyActionOptimizer.java)

**Knapsack algorithm** for low battery:

```java
// File: app/src/main/java/com/example/safesphere/EmergencyActionOptimizer.java

Battery < 20% → Optimize actions
Budget = battery% / 5  (e.g., 20% battery = budget of 4)

Actions with costs/values:
CALL_CONTACT_1     → cost 2, value 10
CALL_CONTACT_2     → cost 2, value 8
CALL_CONTACT_3     → cost 2, value 6
LIVE_LOCATION      → cost 4, value 5

Knapsack solution selects highest value within budget
```

**How to modify:**
- Change costs/values? Impact on which contacts called when battery low
- Change threshold (20%)? When optimization kicks in
- Add new actions? Must have cost/value assignment

### 4. Phone State Detection (PhoneStateReceiver.java)

**CRITICAL: Manifest-registered** to survive app process death

```xml
<!-- AndroidManifest.xml -->
<receiver
    android:name=".PhoneStateReceiver"
    android:enabled="true">
    <intent-filter>
        <action android:name="android.intent.action.PHONE_STATE" />
    </intent-filter>
</receiver>
```

**State flow:**
```java
onReceive(Intent intent) {
    String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
    String incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
    
    if (state.equals(TelephonyManager.EXTRA_STATE_RINGING)) {
        // Our call is ringing
        Prefs.setHasSeenActive(false);  // Track: not answered yet
    }
    
    if (state.equals(TelephonyManager.EXTRA_STATE_OFFHOOK)) {
        // Someone picked up!
        Prefs.setHasSeenActive(true);   // Track: answered
    }
    
    if (state.equals(TelephonyManager.EXTRA_STATE_IDLE)) {
        // Call ended
        boolean wasAnswered = Prefs.hasSeenActive();
        
        if (!wasAnswered) {
            // Not answered → next contact
            scheduleNextCall(3000);  // 3s delay
        }
    }
}
```

**How to modify:**
- Change delay times (2s, 3s)? Test with real call timing
- Add call screening (caller ID check)? Must preserve fallback
- Add audio recording? Requires RECORD_AUDIO permission + notification

### 5. SMS Fallback

```java
// Called if all contacts failed to answer
String[] contacts = {
    Prefs.getEmergency1(context),
    Prefs.getEmergency2(context),
    Prefs.getEmergency3(context)
};

String message = "Emergency! Unable to connect by phone. "
    + "Name: " + Prefs.getName(context) + ". "
    + "Keyword: " + Prefs.getKeyword(context) + ". "
    + "Location: https://maps.google.com/?q=lat,lng";

for (String contact : contacts) {
    sendSMS(contact, message);  // Requires SEND_SMS permission
}
```

**How to modify:**
- Change message format? Keep essential info (name, keyword, location)
- Add to incident log? Post location to server, log in database
- Add email fallback? Requires INTERNET permission + backend

## Testing Checklist

**Before any emergency flow change:**

### Unit Tests
- [ ] Trigger scoring works (keyword, shake, location combinations)
- [ ] Contact sequence advances (0→1→2)
- [ ] SharedPreferences persist across app restart
- [ ] State machine handles all CALL_STATE values

### Integration Tests  
- [ ] PhoneStateReceiver survives app process death
- [ ] Manifest-registered receiver fires on real call
- [ ] Full sequence: call 1 → unanswered → call 2 → answered → log
- [ ] SMS fallback sends to all 3 contacts

### Edge Cases (CRITICAL)
- [ ] Network lost during call: gracefully fallback to next
- [ ] App force-stopped mid-emergency: receiver still fires
- [ ] Rapid re-trigger (user shakes again): deduplicate, don't double-call
- [ ] No contacts configured: fail safely, don't crash
- [ ] Location unavailable: proceed without location
- [ ] Phone locked: transparent activity works (CallActivity.java)
- [ ] Do Not Disturb enabled: override with full screen intent
- [ ] Battery 5%: still attempt contact 1, skip locations if budget depleted

### Real Device Testing
- [ ] Test with each emergency contact (actual phone numbers)
- [ ] Test with device in lock screen
- [ ] Test with VoIP app instead of cellular
- [ ] Test with call forwarding enabled
- [ ] Test offline/no signal: immediate SMS fallback
- [ ] Test after 24h off → app killed

## Code Modification Template

**When making emergency flow changes:**

```java
// 1. Identify file
// File: app/src/main/java/com/example/safesphere/WHAT_TO_MODIFY.java

// 2. Show original code with context
// Original (lines XX-YY):
/*
    int score = 0;
    if (keywordDetected) score += 5;
    if (score >= 5) handleEmergency();
*/

// 3. Proposed change with justification
// Change: Increase keyword score from 5 → 6 to reduce false positives
// Reason: Testing showed X% false positive rate
// Risk: May delay real emergencies by ~10% per test data
// Mitigation: Add keyword confidence check + require confirmation

// 4. Modified code
/*
    int score = 0;
    if (keywordDetected && confidenceScore > 0.8) score += 6;  // Updated
    if (score >= 5) handleEmergency();
*/

// 5. Testing plan
// - Test 100 real keyword detections, measure false positive rate
// - Verify emergency still triggers for legitimate keywords
// - Check no regression: calls still go through
```

## Debugging Emergency Incidents

**If emergency call fails:**

1. **Check analytics** → `/admin/analytics` 
   - Look for `trigger_source` event (keyword/shake detected)
   - Check `call_attempt` event (contact called)
   - Check `call_connected` event (answered)

2. **Check device logs** → Run on test device:
   ```bash
   adb logcat | grep -i "emerg\|call\|phone\|trigger"
   ```

3. **Check SharedPreferences** → Debug prefs dump:
   ```
   Prefs.getCallSequenceIndex(context)
   Prefs.getEmergency1/2/3(context)
   Prefs.getLoggedIn(context)
   ```

4. **Check manifest receiver** → Verify in AndroidManifest.xml:
   ```xml
   <receiver android:name=".PhoneStateReceiver" android:enabled="true">
       <intent-filter>
           <action android:name="android.intent.action.PHONE_STATE" />
       </intent-filter>
   </receiver>
   ```

5. **Check permissions** → Verify granted at runtime:
   ```
   READ_PHONE_STATE
   CALL_PHONE
   SEND_SMS
   ACCESS_FINE_LOCATION
   ```

## Performance Considerations

- **No blocking**: Emergency sequence runs on background thread
- **Polling UI**: use Handler.postDelayed(), not Thread.sleep()
- **Battery**: use ExactAlarmManager cautiously (drains battery)
- **Memory**: unregister all listeners in onDestroy()
- **Network**: timeouts set to 15s (NetworkConfig.java)

## Changelog
- 2026-03-30 - Initial emergency flow documentation
- Documented trigger scoring (keyword +5, shake +3, location +2)
- Documented contact sequence and PhoneStateReceiver state machine
- Documented battery optimization via knapsack algorithm
- Included testing checklist and debugging steps
- Added code modification template
