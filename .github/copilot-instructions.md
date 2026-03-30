# Project Copilot Instructions (Global Context)

## STRICT RULES — NEVER VIOLATE THESE

### Anti-Hallucination Rules
1. **NEVER invent files, functions, classes, or packages that don't exist in this codebase.**
2. **ALWAYS verify** a file exists before referencing it. If unsure, say "I need to check if this exists."
3. **NEVER assume** a dependency is installed. Check `package.json`, `requirements.txt`, `.csproj`, `pom.xml`, or equivalent first.
4. **If you don't know something, say "I don't know" instead of guessing.**
5. **ALWAYS read the actual file content** before suggesting modifications.
6. **Quote exact line numbers** when referencing existing code.
7. **NEVER fabricate API endpoints, database schemas, or config values.**

### Memory & Context Rules
8. When I give new instructions about skills or agents, **immediately update** the relevant `.md` file in `.github/skills/` or `.github/prompts/`.
9. **Append a changelog** at the bottom of each skill file when updated.
10. Before every response, mentally check: "Am I about to say something I haven't verified in the actual codebase?"

### Code Quality Rules
11. Follow existing code patterns, naming conventions, and folder structure already in this project.
12. Never remove existing functionality unless explicitly asked.
13. Always include error handling.
14. Write comments for complex logic only.

### Self-Review Before Responding
Before giving ANY code suggestion:
- [ ] Did I verify the files I'm referencing actually exist?
- [ ] Did I check the actual imports/dependencies?
- [ ] Does my suggestion match the existing code patterns?
- [ ] Am I hallucinating any function names or APIs?

## Project Context
<!-- Auto-detected from SafeSphere codebase scan -->
### Tech Stack: SafeSphere (Android + Web)
**Android Tier:**
- **Language:** Java
- **Build System:** Gradle
- **Compile SDK:** 35, Target SDK: 35, Min SDK: 24
- **Key Libraries:**
  - androidx.appcompat:appcompat:1.7.0
  - com.google.android.material:material:1.12.0
  - com.google.android.gms:play-services-location:21.3.0
  - com.alphacephei:vosk-android:0.3.47 (offline speech recognition)
  - androidx.room:room-runtime:2.6.1 (local analytics database)
  - androidx.work:work-runtime:2.9.0 (background tasks)
  - com.squareup.okhttp3:okhttp:4.12.0 (HTTP client)
  - com.google.code.gson:gson:2.10.1 (JSON serialization)

**Web Tier:**
- **Framework:** Next.js 16.1.7 with React 19.2.3
- **Language:** TypeScript 5.x
- **Styling:** Tailwind CSS 4.x
- **Database SDK:** @supabase/supabase-js:2.99.2, @supabase/ssr:0.9.0
- **UI Libraries:** Lucide React (0.577.0), Recharts (3.8.0)

**Backend:** Supabase (PostgreSQL) with real-time queries

**Application ID:** com.example.safesphere  
**Version:** 1.0 (Android), 0.1.0 (Web)

### Architecture Pattern
- **Android:** Activity-based lifecycle with background Service, Broadcast Receivers, SharedPreferences for persistence
- **Web:** Next.js App Router with protected routes (middleware-based auth), Supabase for auth & data, TypeScript for type safety
- **Communication:** Android ↔ Web via HTTPS REST API (OkHttp client to Next.js routes)
- **Database:** PostgreSQL via Supabase (single source of truth), Room local analytics queue on Android
- **Message Queue:** Emergency events synced via WorkManager (AnalyticsSyncWorker runs every 15 min)

### Key Patterns Found

**Android Activities:**
- Launcher: LoginActivity → RegisterActivity → MainActivity (dashboard)
- Dashboard: MainActivity (protection toggle, settings)
- Emergency Flow: Fake call interface (FakeCallActivity) + transparent trampoline (CallActivity)
- Profile editing: ProfileActivity
- Result: All extend AppCompatActivity, use ViewBinding, follow onCreate→onStart→onResume→onPause→onDestroy lifecycle

**Android Services & Receivers:**
- SafeSphereService (core: keyword detection, shake detection, location tracking, call management)
- BootReceiver (RECEIVE_BOOT_COMPLETED)
- PhoneStateReceiver (manifest-registered for process death resilience)
- ServiceRestartReceiver (AlarmManager-triggered restart)

**Web Pages:**
- Public: `/` (landing), `/admin/login` (auth form)
- Admin protected at `/admin/*` via middleware.ts (checks admin_accounts table)
- Dashboard: `/admin/dashboard` (metrics, charts)
- Management: `/admin/users`, `/admin/users/[id]`, `/admin/incidents`, `/admin/messages`
- Audit: `/admin/audit`, `/admin/analytics`, `/admin/saved`

**Web API Routes:**
- Public: `/api/user/register` (POST), `/api/revocation/check` (GET), `/api/analytics/ingest` (POST)
- Admin: `/api/admin/dashboard/metrics` (GET), `/api/admin/incidents` (GET/POST), `/api/admin/analytics` (GET), `/api/admin/audit` (GET), `/api/admin/remove-user` (POST), `/api/admin/send-message` (POST), `/api/admin/saved` (GET)

**Critical Paths:**

1. **Emergency Call Sequence** (app/src/main/java/com/example/safesphere/EmergencyManager.java)
   - 3-contact fallback with polling every 2s
   - Call state detection via manifest-registered PhoneStateReceiver
   - SMS fallback if no answer
   - Location sharing per contact
   - Battery optimization via knapsack algorithm (EmergencyActionOptimizer.java)

2. **Keyword Detection** (SafeSphereService.java)
   - Vosk offline speech recognition (sample rate 16000 Hz)
   - Real-time background thread processing
   - Trigger threshold: 5+ points (keyword +5, shake +3, location +2)

3. **Session Persistence** (Prefs.java - SharedPreferences)
   - Keys: name, phone, keyword, e1/e2/e3 contacts, logged_in, call_sequence_index, protection_enabled
   - Used by: EmergencyManager (call_sequence_index), MainActivity (logged_in, protection_enabled)
   - Survives app crashes & force stop

4. **Phone State Listener** (PhoneStateReceiver.java)
   - Manifest-registered (AndroidManifest.xml) for process death resilience
   - State machine: RINGING/OFFHOOK → set hasSeenActive, IDLE → check answered
   - 3s re-check after call end, 2s delay before next contact, 1.5s idle stability

5. **Analytics Pipeline** (AnalyticsDatabase.java + AnalyticsSyncWorker.java)
   - Local database: safesphere_analytics.db (Room) with analytics_events table
   - Event types: registration, login, protection_enabled, trigger_source, call_attempt, call_connected, sms_sent, location_shared, etc.
   - Sync: WorkManager every 15 min, batched 100 events/request to /api/analytics/ingest
   - Idempotency: client-generated UUID per event

6. **Revocation Check** (RevocationCheckWorker.java → /api/revocation/check)
   - WorkManager every 15 min
   - Detects: force logout, revocation version increment, admin messages
   - Response includes pending_messages array
   - On revocation: stops service, clears analytics DB, cancels jobs, logs out

7. **Admin Dashboard Metrics** (/api/admin/dashboard/metrics)
   - Queries: total_users, active_users, removed_users, total_incidents, outcomes (safe_self, call_connected)
   - Charts: daily incidents (LineChart), funnel (BarChart), trigger sources
   - Data refreshes real-time, powered by Supabase queries

8. **User Removal (Revocation)** (/api/admin/remove-user)
   - Increment revocation_version atomic transaction
   - Set is_active=false, revoked_at=now(), revocation_reason, revocation_message
   - Log to admin_actions audit table (immutable)
   - Next check by RevocationCheckWorker detects change, stops app

### Database Schema (Supabase PostgreSQL)
**Main Tables:**
- `users` (id, name, phone_hash, masked_phone, is_active, revoked_at, revocation_version, revocation_reason)
- `incidents` (id, user_id, trigger_source, call_connected, sms_sent, location_shared, outcome, safe_acknowledged)
- `analytics_events` (event_id UUID, user_id, session_id, event_type, schema_version, client_ts_ms, payload_json, synced)
- `admin_accounts` (id, email, password_hash, is_active, created_at)
- `admin_messages` (id, subject, body, message_type, is_critical, target_user_id, created_at)
- `revocation_tokens` (user_id, revocation_version, last_checked_at)
- `saved_verifications` (id, incident_id, evidence_type, evidence_notes, verified_by_admin, verified_at)
- `admin_actions` (id, admin_id, action, resource_type, resource_id, timestamp) [IMMUTABLE AUDIT LOG]

### Networking & Config
- **Android API Base:** buildConfigField VERCEL_BASE_URL = "http://192.168.0.108:3000/api/" (configurable)
- **HTTP Timeouts:** 15s connect, 30s read, 30s write (NetworkConfig.java)
- **Phone Hash Salt:** "SafeSphere2024SecureSalt32Chars!!" (matching Android & Web for consistency)
- **Supabase Anon Key:** buildConfigField SUPABASE_ANON_KEY

### Testing Frameworks
- **Android:** JUnit 4.13.2, Espresso 3.6.1, Mockito (implied by patterns)
- **Web:** TypeScript strict mode, Next.js built-in testing support

### Known Critical Issues Being Addressed
- Emergency call system: multi-contact fallback with polling reliability
- Session persistence: SharedPreferences survival across app kills
- Keyword detection: Vosk offline accuracy with low battery impact
- Phone state listener: Crash handling and process death resilience (manifest receiver)
- Auth flows: Supabase session cookies + middleware-based route protection
- Analytics: Batch sync with idempotency and retry logic
- Revocation: Atomic multi-step user removal with immutable audit trail
