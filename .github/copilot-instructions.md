# Project Copilot Instructions (Global Context)

## STRICT RULES — NEVER VIOLATE THESE

### Anti-Hallucination Rules
1. NEVER invent files, functions, classes, columns, or tables that don't exist.
2. ALWAYS verify a file exists before referencing it.
3. NEVER assume a dependency is installed. Check package.json or build.gradle first.
4. If you don't know something, say "I need to verify this" instead of guessing.
5. ALWAYS read the actual file content before suggesting modifications.
6. NEVER fabricate API endpoints, database columns, or config values.
7. When in doubt about a column name — check the VERIFIED SCHEMA section below.

### Memory & Context Rules
8. When given new instructions about skills or agents, immediately update the relevant
   .md file in .github/skills/ or .github/prompts/
9. Append a changelog at the bottom of each skill file when updated.
10. Before every response, check: "Am I about to reference something I haven't verified?"

### Code Quality Rules
11. Follow existing code patterns, naming conventions, and folder structure.
12. Never remove existing functionality unless explicitly asked.
13. Always include error handling (try-catch in Java, try-catch in TypeScript).
14. ALL Supabase calls in Android must be in background threads.
15. NEVER send Unix milliseconds to Supabase — use ISO 8601 format.
16. Use SupabaseClient.toIso8601(long millis) for timestamp conversion in Android.

### Self-Review Before Responding
Before giving ANY code suggestion:
- Did I verify the files I am referencing actually exist?
- Did I check actual imports and dependencies?
- Does my suggestion match existing code patterns?
- Am I hallucinating any column names, table names, or method names?

---

## PROJECT OVERVIEW

SafeSphere is a women's safety app with:
- Android app (Java) — emergency detection via shake/keyword/manual, call routing, SMS, feedback
- Admin dashboard (Next.js 15) — analytics, incident management, user management
- Supabase backend — PostgreSQL database, RLS policies

---

## TECH STACK — VERIFIED

### Android
- Language: Java (NOT Kotlin)
- Min SDK: 26 (Android 8), Target SDK: 35, Compile SDK: 35
- Application ID: com.example.safesphere
- Build System: Gradle

Key Libraries (verified in app/build.gradle):
- androidx.appcompat:appcompat:1.7.0
- com.google.android.material:material:1.12.0
- com.google.android.gms:play-services-location:21.3.0
- com.alphacephei:vosk-android:0.3.47 (offline speech recognition)
- androidx.room:room-runtime:2.6.1 (local analytics queue)
- androidx.work:work-runtime:2.9.0 (background sync)
- com.squareup.okhttp3:okhttp:4.12.0 (HTTP client)
- com.google.code.gson:gson:2.10.1 (JSON)

Secrets (loaded from android/local.properties — GITIGNORED):
- SUPABASE_URL
- SUPABASE_ANON_KEY
- VERCEL_BASE_URL
- PHONE_HASH_SALT

### Admin Dashboard
- Framework: Next.js 15 with Turbopack
- Language: TypeScript
- Styling: Tailwind CSS (dark theme)
- Database: @supabase/supabase-js, @supabase/ssr
- Charts: Recharts
- Icons: Lucide React
- Dev server: localhost:3000

---

## FILE STRUCTURE — VERIFIED

### Android Files (all verified to exist)
android/app/src/main/java/com/example/safesphere/
  MainActivity.java          — main UI, protection toggle, permissions
  LoginActivity.java         — login with Supabase verification
  RegisterActivity.java      — new user registration
  ProfileActivity.java       — profile editing, keyword, emergency contacts
  EmergencyFeedbackActivity.java — feedback form after emergency
  EmergencyManager.java      — CORE emergency logic (1700+ lines)
  SafeSphereService.java     — background service, keyword/shake detection
  PhoneStateReceiver.java    — call state listener, sequence complete handler
  BootReceiver.java          — restart service on device boot
  ServiceRestartReceiver.java — AlarmManager-triggered service restart
  ShakeDetector.java         — accelerometer shake detection
  EmergencyDecisionAPI.java  — trigger scoring (keyword+5, shake+3, location+2)
  EmergencyActionOptimizer.java — battery-aware action selection (knapsack)
  FakeCallActivity.java      — fake incoming call UI
  CallActivity.java          — transparent trampoline for emergency calls
  Prefs.java                 — SharedPreferences helper (ALL app state)
  network/SupabaseClient.java — database API calls
  network/NetworkConfig.java  — HTTP timeouts
  AnalyticsQueue.java        — event queue for WorkManager
  SyncWorker.java            — WorkManager worker for offline profile/feedback sync

### Admin Dashboard Files (all verified to exist)
admin/src/app/admin/
  dashboard/page.tsx
  incidents/page.tsx
  users/page.tsx
  users/[id]/page.tsx
  analytics/page.tsx
  feedback/page.tsx
  saved/page.tsx
  audit/page.tsx

admin/src/app/api/
  admin/analytics/route.ts
  admin/audit/route.ts
  admin/feedback/route.ts
  admin/incidents/route.ts
  admin/incidents/[id]/route.ts
  admin/metrics/route.ts
  admin/remove-user/route.ts
  admin/saved/route.ts
  admin/send-message/route.ts
  admin/users/route.ts
  admin/users/[id]/route.ts
  admin/users/[id]/incidents/route.ts
  admin/verify-rescue/route.ts
  analytics/ingest/route.ts
  emergency/event/route.ts
  emergency/feedback/route.ts
  revocation/check/route.ts
  user/register/route.ts

admin/src/components/
  AdminSidebar.tsx
  StatsCard.tsx
  StatusBadge.tsx
  LoadingSkeleton.tsx

admin/src/lib/supabase/
  client.ts
  server.ts

---

## SUPABASE SCHEMA — VERIFIED REAL COLUMNS ONLY

Supabase Project URL: https://geyjrugnxhmtcwwuuiab.supabase.co

### TABLE: users
id (uuid)
display_name (text)
phone_hash (text) — stores RAW phone number
is_active (boolean)
created_at (timestamptz)
updated_at (timestamptz)
last_app_open (timestamptz)
device_model (text)
android_version (text)
app_version (text)
total_emergencies (integer)
os_type (text)
keyword (text)
emergency_contact_1 (text)
emergency_contact_2 (text)
emergency_contact_3 (text)

### TABLE: emergency_events
id (uuid)
user_id (uuid → users.id)
trigger_type (text)
triggered_at (timestamptz)
session_id (text)
location_lat (numeric)
location_lng (numeric)
location_accuracy (numeric)
status (text)
primary_contact_called (text)
primary_contact_duration_s (integer)
primary_contact_answered (boolean)
secondary_contact_called (text)
secondary_contact_duration_s (integer)
secondary_contact_answered (boolean)
tertiary_contact_called (text)
tertiary_contact_duration_s (integer)
tertiary_contact_answered (boolean)
sms_sent_to (text)
resolved_at (timestamptz)
resolution_type (text)
admin_notes (text)
time_to_first_contact_s (integer)
time_to_answer_s (integer)
time_to_resolve_s (integer)
phone_battery_percent (integer)
phone_on_silent (boolean)
has_location_enabled (boolean)
is_test_event (boolean)
requires_admin_review (boolean)
created_at (timestamptz)
updated_at (timestamptz)

### TABLE: emergency_feedback
id (uuid)
event_id (uuid → emergency_events.id)
user_id (uuid → users.id)
was_real_emergency (boolean)
was_rescued_or_helped (boolean)
rating (integer 1-5)
feedback_text (text)
feedback_category (text)
helpful_features (text[])
admin_reviewed (boolean)
admin_response (text)
admin_reviewed_at (timestamptz)
submitted_at (timestamptz)
created_at (timestamptz)

### TABLE: admin_messages
id (uuid)
subject (text)
body (text)
target_user_id (uuid)
is_critical (boolean)
created_at (timestamptz)

### TABLE: pending_messages
id (uuid)
user_id (uuid)
message_id (uuid)
status (text)
created_at (timestamptz)
delivered_at (timestamptz)

### TABLE: admin_accounts
id (uuid)
supabase_uid (text)
email (text)
display_name (text)
role (text)
is_active (boolean)
created_at (timestamptz)

### TABLE: audit_logs
id (uuid)
admin_id (uuid)
action (text)
target_user_id (uuid)
details (jsonb)
created_at (timestamptz)

### TABLE: saved_verifications
id (uuid)
user_id (uuid)
incident_session_id (text)
verified_by (text)
evidence_type (text)
notes (text)
verified_at (timestamptz)

### COLUMNS THAT DO NOT EXIST — NEVER USE THESE
phone_masked
last_seen_at
revoked_at
email (in users table)
revocation_version
revocation_reason
revocation_tokens (table does not exist)
incidents (table does not exist — use emergency_events)
admin_actions (table does not exist — use audit_logs)
outcome
was_rescued
reviewed_by_admin
message_type
priority
read_at
acknowledged_at

---

## ANDROID KEY PATTERNS — VERIFIED

### Prefs.java — Key Methods (verified to exist)
Authentication:
  setLoggedIn / isLoggedIn
  setSupabaseUserId / getSupabaseUserId   ← USE THIS, not getUserId()
  setUserPhone / getUserPhone
  setUserName / getUserName
  setKeyword / getKeyword

Emergency contacts:
  setEmergency1 / setEmergency2 / setEmergency3
  getEmergencyNumbers() → String[]

Protection:
  setProtectionEnabled / isProtectionEnabled

Emergency session:
  setLastEmergencyEventId / getLastEmergencyEventId
  setCurrentEmergencySessionId / getCurrentEmergencySessionId
  setCallSequenceIndex / getCallSequenceIndex
  setCallStartTime / getCallStartTime
  clearCallSequence

Location:
  setLastKnownLocation / getLastKnownLocationLat / getLastKnownLocationLng

Offline sync queue:
  setProfileSyncPending / isProfileSyncPending
  setPendingProfileData(ctx, name, keyword, e1, e2, e3)
  getPendingProfileName / getPendingProfileKeyword
  getPendingProfileE1 / getPendingProfileE2 / getPendingProfileE3
  clearPendingProfileData

Admin messages:
  getPendingAdminMessages / addPendingAdminMessage / clearPendingAdminMessages

Revocation:
  isPendingRevocation / setPendingRevocation
  getRevocationMessage / setRevocationMessage

### SupabaseClient.java — Key Methods (verified to exist)
getUserByPhone(String phone) → JSONObject or null
getUserProfileByPhone(String phone) → JSONObject or null
insertRow(String table, JSONObject data) → SupabaseResponse
insertRowReturningRepresentation(String table, JSONObject data) → SupabaseResponse
updateRow(String table, String filterColumn, String filterValue, JSONObject data) → SupabaseResponse
updateEmergencyEventResults(String eventId, JSONObject updates) → SupabaseResponse
incrementTotalEmergencies(String userId) → void
toIso8601(long millis) → String   ← ALWAYS use this for timestamps
submitEmergencyFeedback(EmergencyFeedbackData) → SupabaseResponse

---

## EMERGENCY FLOW — VERIFIED CURRENT STATE

Trigger (keyword score≥5 / shake+3 / manual button)
  → EmergencyManager.initiateEmergency()
  → Insert row to emergency_events (Supabase)
  → Call Contact 1 → PhoneStateReceiver monitors state
  → Not answered → Call Contact 2 → Call Contact 3
  → SMS sent to all contacts with location
  → notifySequenceComplete() called
  → PATCH emergency_events with call results + timing
  → Feedback notification shown
  → EmergencyFeedbackActivity → submit to emergency_feedback
  → Return to MainActivity

Time calculations (verified working):
  time_to_first_contact_s = time from emergency start to first call placed
  time_to_answer_s = time_to_resolve_s minus answered call duration
  time_to_resolve_s = time from emergency trigger to sequence complete

---

## OFFLINE SYNC — CURRENT IMPLEMENTATION

Profile changes:
  Online: saved to Prefs + pushed to Supabase immediately
  Offline: saved to Prefs + Prefs.setProfileSyncPending = true
           + SyncWorker.scheduleSyncWhenOnline() called
  Sync: WorkManager SyncWorker runs automatically when internet returns
        Works even if app is completely killed

Feedback:
  Online: submitted to emergency_feedback table immediately
  Offline: saved to Prefs + Prefs.setFeedbackSyncPending = true
           + SyncWorker.scheduleSyncWhenOnline() called
  Sync: WorkManager SyncWorker handles feedback sync automatically

Emergency events:
  Online: inserted to emergency_events table immediately
  Offline: saved to Prefs + Prefs.setEmergencyEventSyncPending = true
           + SyncWorker.scheduleSyncWhenOnline() called
  Sync: WorkManager SyncWorker syncs event before call results and feedback

Call results:
  Online: PATCH to emergency_events table immediately (after all calls complete)
  Offline: saved to Prefs + Prefs.setCallResultsSyncPending = true
           + SyncWorker.scheduleSyncWhenOnline() called
  Sync: WorkManager SyncWorker syncs call results after event insert succeeds
  Sync order in SyncWorker: event → callResults → profile → feedback

SyncWorker (android/app/src/main/java/com/example/safesphere/SyncWorker.java):
  - Extends Worker (WorkManager)
  - Constraint: NetworkType.CONNECTED (only runs when internet available)
  - Handles both profile sync and feedback sync in one job
  - Returns Result.retry() if any sync fails (WorkManager retries automatically)
  - Scheduled with ExistingWorkPolicy.REPLACE (deduplicates multiple pending syncs)
  - Tag: "offline_sync"
  - Unique work name: "safesphere_offline_sync"

Emergency events:
  Calls and SMS work offline always (no internet needed)
  Supabase insert may fail offline but emergency still completes

---

## ADMIN DASHBOARD DESIGN SYSTEM

Page background:   #08090e
Card background:   #111219
Sidebar:           #0c0d13
Input background:  #16171f
Border:            border-white/[0.08]

Text:
  Primary:         #f1f5f9 (slate-100)
  Secondary:       #94a3b8 (slate-400)
  Muted:           #64748b (slate-500)

Accents:
  Success:         #10b981 (emerald-500)
  Danger:          #ef4444 (red-500)
  Info:            #06b6d4 (cyan-500)

Cards:             rounded-2xl, border border-white/[0.06]
Buttons:           rounded-xl, bg-emerald-600 hover:bg-emerald-500
Inputs:            rounded-xl, border border-white/10

---

## ANDROID BUTTON LOADING PATTERN — REQUIRED

DO NOT use setEnabled(false) alone — makes button invisible in dark mode.

Correct pattern:
  button.setEnabled(!loading);
  button.setText(loading ? "Loading..." : "Submit");
  button.setBackgroundTintList(
      ColorStateList.valueOf(Color.parseColor("#C2185B")));

Button XML must use BOTH:
  android:background="@drawable/bg_form_primary_button"
  android:backgroundTint="#C2185B"

---

## CODING RULES — ALWAYS FOLLOW

1. NEVER invent columns, methods, or file names — verify first
2. ALWAYS read actual files before modifying
3. ALL Supabase calls in background threads (new Thread or AsyncTask)
4. ALL Supabase calls in try-catch blocks
5. Emergency calls MUST work even if Supabase is completely down
6. Admin API routes use createServiceClient() — bypasses RLS
7. User API routes use createClient() with auth verification
8. Timestamps: ISO 8601 always, never Unix milliseconds
9. getUserByPhone() returns JSONObject or null (not JSONArray)
10. Java only for Android — never Kotlin
11. Git: work on develop branch, merge to main when stable
12. Secrets: local.properties only (GITIGNORED)
13. getSupabaseUserId() NOT getUserId() — getUserId() does not exist in Prefs

---

## KNOWN ISSUES — CURRENT

1. Admin dashboard: not deployed to Vercel yet
   Status: works on localhost:3000

2. Admin message polling: low priority
   Only fires when SafeSphereService running (protection ON)

3. SyncWorker_new.java: DELETED — was temporary backup file

All previous issues resolved:
  - Trigger type fix: FIXED (triggerEmergencyWithSource)
  - Offline emergency event: FIXED (SyncWorker.syncEmergencyEvent)
  - Offline call results: FIXED (SyncWorker.syncCallResults)
  - Offline profile sync: FIXED (SyncWorker.syncProfile)
  - Offline feedback sync: FIXED (SyncWorker.syncFeedback)
  - Login offline message: FIXED
  - Register offline message: FIXED
  - GPS prompt: FIXED
  - LoginActivity redirect after ProfileActivity: FIXED
  - Feedback foreign key retry loop: FIXED

---

## WHAT IS WORKING — VERIFIED

Android:
  Login/register with Supabase verification
  Auto-login on app restart
  Emergency flow: shake/keyword/manual trigger
  3-contact call sequence with fallback
  SMS to all contacts with location
  Call state detection via PhoneStateReceiver
  Call duration and answered status from CallLog
  Time calculations: time_to_first_contact_s, time_to_answer_s, time_to_resolve_s
  Feedback notification after emergency
  EmergencyFeedbackActivity submission
  Profile save/load with Supabase
  Offline profile save → WorkManager syncs when internet returns (survives app kill)
  Offline feedback save → WorkManager syncs when internet returns (survives app kill)
  Login shows correct error when offline (no internet message)
  Register shows correct error when offline (no internet message)
  GPS prompt shown when protection enabled and GPS is off (optional, dismissable)
  Battery optimization prompt (once only)
    ✅ Trigger type correctly captured: SHAKE, KEYWORD, MANUAL (not all showing LIVE)
    ✅ Offline emergency event insert → WorkManager syncs when internet returns
    ✅ Call results PATCH queued offline → syncs after event insert succeeds
  API keys in local.properties (not hardcoded)

Admin Dashboard:
  All 8 pages working on localhost:3000
  Dashboard: 4 metric cards + funnel + charts
  Incidents: table + expand detail + outcome dropdown
  Users: list + search + filter
  User detail: profile + emergency history + feedback
  Analytics: 5 stat cards + 4 charts
  Feedback: user feedback list
  Saved verifications: admin-verified rescues
  Audit: admin action log
