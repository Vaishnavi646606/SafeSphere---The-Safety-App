# Skill: Offline Sync — WorkManager Pattern

## Description
SafeSphere uses WorkManager to sync offline changes to Supabase automatically
when internet becomes available. This works even if the app is completely killed.

## When to Use
- Any feature that writes to Supabase needs to handle offline
- When user makes changes without internet connection
- When adding new data submission features

## Architecture

### The Pattern (use this for every new offline-capable feature)

Step 1 — Check connectivity before Supabase call:
    android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
            getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
    android.net.NetworkInfo ni = cm.getActiveNetworkInfo();
    boolean isOnline = ni != null && ni.isConnected();

Step 2 — If offline, save to Prefs + schedule sync:
    if (!isOnline) {
        Prefs.setXxxSyncPending(this, true);
        Prefs.setPendingXxxData(this, ...data...);
        SyncWorker.scheduleSyncWhenOnline(this);
        // Show user friendly message
        // Navigate away normally
        return;
    }

Step 3 — If online but Supabase fails, same pattern:
    if (!response.success) {
        Prefs.setXxxSyncPending(this, true);
        Prefs.setPendingXxxData(this, ...data...);
        SyncWorker.scheduleSyncWhenOnline(this);
    }

Step 4 — Add sync logic to SyncWorker.doWork():
    if (Prefs.isXxxSyncPending(ctx)) {
        anyFailed = !syncXxx(ctx);
    }

Step 5 — Add Prefs methods for new pending data:
    setXxxSyncPending / isXxxSyncPending
    setPendingXxxData(...all fields...)
    getPendingXxxField1 / getPendingXxxField2 / etc.
    clearPendingXxxData

## Key Files

SyncWorker:
  android/app/src/main/java/com/example/safesphere/SyncWorker.java
  - Extends Worker
  - Constraint: NetworkType.CONNECTED
  - Handles: profile sync, feedback sync
  - Returns Result.retry() on failure (WorkManager retries automatically)
  - Scheduled with ExistingWorkPolicy.REPLACE

Prefs sync methods:
  android/app/src/main/java/com/example/safesphere/Prefs.java
  Profile: isProfileSyncPending, setPendingProfileData, clearPendingProfileData
  Feedback: isFeedbackSyncPending, setPendingFeedbackData, clearPendingFeedbackData

## What Is Currently Synced

Profile updates (ProfileActivity.handleSaveProfile):
  - display_name, keyword, emergency_contact_1/2/3
  - Syncs to: users table, column updated_at

Emergency feedback (EmergencyFeedbackActivity.submitFeedback):
  - eventId, userId, wasRealEmergency, wasRescuedOrHelped, rating, feedbackText
  - Syncs to: emergency_feedback table

## Adding A New Offline-Capable Feature

1. Add to Prefs.java:
   setNewFeatureSyncPending / isNewFeatureSyncPending
   setPendingNewFeatureData(...) with all required fields
   getPendingNewFeatureFieldX() for each field
   clearPendingNewFeatureData()

2. In your Activity/Service — when saving offline:
   Prefs.setNewFeatureSyncPending(this, true);
   Prefs.setPendingNewFeatureData(this, ...);
   SyncWorker.scheduleSyncWhenOnline(this);

## Changelog

### 2026-04-03 (Latest)
- **Replaced single-key offline queues with JSON array queues**
- Emergency events: `enqueueEmergencyEvent()` / `getEmergencyEventQueue()` / `removeEmergencyEventFromQueue()`
- Call results: `enqueueCallResults()` / `getCallResultsQueue()` / `removeCallResultsFromQueue()`
- Feedback: `enqueueFeedback()` / `getFeedbackQueue()` / `removeFeedbackFromQueue()`
- **Key improvement**: SyncWorker now loops through full arrays — syncs every pending item (no data loss on overwrite)
- **Removed 19 old Prefs methods**: setPendingEmergencyEventData, getPendingEventId, etc.
- **Added 12 new Prefs methods**: queue operations for emergency events, call results, feedback
- EmergencyManager: Updated all `setPending*` calls → `enqueue*` calls
- Removed: setEmergencyEventSyncPending, setCallResultsSyncPending, setFeedbackSyncPending calls
- **Multiple offline emergencies now fully preserved and synced correctly**

3. In SyncWorker.doWork():
   if (Prefs.isNewFeatureSyncPending(ctx)) {
       anyFailed = !syncNewFeature(ctx);
   }

4. Add private boolean syncNewFeature(Context ctx) method to SyncWorker

## Anti-Hallucination Checks
- SyncWorker.java exists at verified path above
- WorkManager dependency: androidx.work:work-runtime:2.9.0 (in app/build.gradle)
- Prefs methods verified to exist before using them
- Table and column names verified against VERIFIED SCHEMA in copilot-instructions.md

## Changelog
- 2026-04-03 - Initial creation
- WorkManager pattern for offline profile and feedback sync
- Survives app kill, retries on failure
- 2026-04-03 - Fixed SyncWorker feedback retry loop on foreign key error (23503)
  Clear pending feedback instead of retrying when event does not exist in Supabase
- 2026-04-03 - Added offline emergency event insert queue
- 2026-04-03 - Added offline call results PATCH queue
- 2026-04-03 - SyncWorker processes in dependency order: event → callResults → profile → feedback