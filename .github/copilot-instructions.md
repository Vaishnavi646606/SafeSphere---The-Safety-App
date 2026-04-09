# SafeSphere — Copilot Instructions
# Read order: 1) .github/SESSION_STATE.md 2) this file 3) needed skill 4) files being edited

RULES:
- Never invent files, tables, columns, methods, or imports.
- Never use Unix milliseconds with Supabase; use `toIso8601(millis)`.
- Never use `getUserId()`; use `getSupabaseUserId()`.
- Android is Java only.
- Read actual files before editing.
- Keep Supabase calls in Android background threads with try-catch.
- Commit only after build success.
- Always update SESSION_STATE.md after a completed task.
- Verify `android/local.properties` is not staged before any push.

PROJECT FACTS:
- App: SafeSphere (Android + admin dashboard)
- Branch: `develop` first, merge to `main` when stable
- Supabase: https://qzezwpzmxkwxgrtxucaw.supabase.co
- Android: min/target/compile SDK 26/35/35, app id `com.example.safesphere`
- Dashboard: Next.js 15 + TypeScript + Tailwind, deployed to safesphere-safety.vercel.app
- Admin routes use `createServiceClient()`; user routes use `createClient()`

VERIFIED FILES:
- Android: `MainActivity.java`, `LoginActivity.java`, `RegisterActivity.java`, `ProfileActivity.java`, `EmergencyFeedbackActivity.java`, `EmergencyManager.java`, `SafeSphereService.java`, `PhoneStateReceiver.java`, `BootReceiver.java`, `ServiceRestartReceiver.java`, `ShakeDetector.java`, `EmergencyDecisionAPI.java`, `EmergencyActionOptimizer.java`, `FakeCallActivity.java`, `CallActivity.java`, `Prefs.java`, `SyncWorker.java`, `network/SupabaseClient.java`, `network/NetworkConfig.java`, `AnalyticsQueue.java`
- Dashboard pages: `dashboard`, `incidents`, `users`, `users/[id]`, `analytics`, `feedback`, `saved`, `audit`, `track/[token]` (public — no login required)
- APIs: `admin/*`, `analytics/ingest`, `emergency/*`, `revocation/check`, `user/register`, `track/[token]` (public — no auth)
- Components: `AdminSidebar.tsx`, `StatsCard.tsx`, `StatusBadge.tsx`, `LoadingSkeleton.tsx`

SUPABASE SCHEMA (core columns):
- users: `id`, `display_name`, `phone_hash`, `is_active`, `created_at`, `updated_at`, `last_app_open`, `device_model`, `android_version`, `app_version`, `total_emergencies`, `os_type`, `keyword`, `emergency_contact_1`, `emergency_contact_2`, `emergency_contact_3`, `last_known_lat`, `last_known_lng`, `last_location_updated_at`
- emergency_events: `id`, `user_id`, `trigger_type`, `triggered_at`, `session_id`, `location_lat`, `location_lng`, `location_accuracy`, `status`, `primary_contact_called`, `primary_contact_duration_s`, `primary_contact_answered`, `secondary_contact_called`, `secondary_contact_duration_s`, `secondary_contact_answered`, `tertiary_contact_called`, `tertiary_contact_duration_s`, `tertiary_contact_answered`, `sms_sent_to`, `resolved_at`, `resolution_type`, `admin_notes`, `time_to_first_contact_s`, `time_to_answer_s`, `time_to_resolve_s`, `phone_battery_percent`, `phone_on_silent`, `has_location_enabled`, `is_test_event`, `requires_admin_review`, `created_at`, `updated_at`
- emergency_feedback, admin_messages, pending_messages, admin_accounts, audit_logs, saved_verifications: use verified schema from this repo
- live_location_sessions: `id`, `token`, `user_id`, `lat`, `lng`, `accuracy`, `last_updated`, `started_at`, `expires_at`, `is_active`, `display_name`
- Never use: `phone_masked`, `last_seen_at`, `revoked_at`, `revocation_*`, `incidents`, `admin_actions`, `outcome`, `was_rescued`, `reviewed_by_admin`, `message_type`, `priority`, `read_at`, `acknowledged_at`

VERIFIED METHODS:
- Prefs: `setLoggedIn/isLoggedIn`, `setSupabaseUserId/getSupabaseUserId`, `setUserPhone/getUserPhone`, `setUserName/getUserName`, `setKeyword/getKeyword`, `setEmergency1/2/3`, `getEmergencyNumbers()`, `setProtectionEnabled/isProtectionEnabled`, `setLastKnownLocation/getLastKnownLocationLat/getLastKnownLocationLng/getLastKnownLocationTime`, `setLastSyncedLocation/getLastSyncedLocationLat/getLastSyncedLocationLng`, `hasAnySavedLocation`, `setFirstLocationCaptured/isFirstLocationCaptured`, `setLastEmergencyEventId/getLastEmergencyEventId`, `setCurrentEmergencySessionId/getCurrentEmergencySessionId`, `setCallSequenceIndex/getCallSequenceIndex`, `setCallStartTime/getCallStartTime`, `clearCallSequence`, queue methods for emergency events/call results/feedback, profile sync, admin messages, revocation, `setLiveLocationToken/getLiveLocationToken/clearLiveLocationToken`
- SupabaseClient: `getUserByPhone`, `getUserProfileByPhone`, `insertRow`, `insertRowReturningRepresentation`, `updateRow`, `updateEmergencyEventResults`, `incrementTotalEmergencies`, `updateUserLocation`, `upsertLiveLocation`, `fetchLiveLocationToken`, `toIso8601`, `submitEmergencyFeedback`

WORKFLOW SUMMARY:
- Emergency flow: `triggerEmergencyWithSource()` → check connectivity → insert or queue → call 1/2/3 → SMS live GPS then stored fallback with age → patch results or queue → feedback
- Offline sync: WorkManager, `NetworkType.CONNECTED`, event insert then call results then profile then feedback, retries on failure
- Location: first capture on startup, refresh every 3 minutes when protection is on, save locally first, sync to Supabase only when online, SMS shows age text

DESIGN / UI:
- Dark dashboard only. Use the existing design tokens from `ui-ux-design` and `web-new-component`.
- Button loading pattern for Android: `setEnabled`, `setText`, `setBackgroundTintList`.

WHAT IS WORKING:
- Android login/register, emergency trigger flow, offline queues, location fallback SMS, share-location offline behavior
- Dashboard pages, admin login on the new Supabase project
- Prefs.java: live location token methods added (Step 1 of 8 complete)
- SupabaseClient.java: upsertLiveLocation() + fetchLiveLocationToken() added (Step 2 of 8 complete)
- SafeSphereService.java: upserts live location every 3 min when protection ON + online
- EmergencyManager.java: emergency SMS includes live tracking URL
- MainActivity.java: share location uses live tracking URL
- Dashboard: /track/[token] public Leaflet map page
- Dashboard: /api/track/[token] public API route (no auth)
- Dashboard: admin user detail shows live session card with auto-refresh

KNOWN ISSUES:
- Live location system complete — needs Clean + Rebuild + git push to Vercel
- Test: trigger emergency → confirm SMS tracking URL → open link → verify map
- Tracking page "Location reported" age reflects device upload recency, not web page refresh time

PATH RULES:
- Web root: `admin/` not `safesphere-admin/`
- Skills: `.github/skills/`
- Prompts: `.github/prompts/`
- Keep `REGISTRY.md` aligned with actual files