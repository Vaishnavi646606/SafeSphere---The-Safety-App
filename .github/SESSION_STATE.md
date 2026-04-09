# SafeSphere Session State
# READ THIS FIRST - every session, before anything else
# UPDATE THIS LAST - after every completed change
# MAX 25 LINES - never grow beyond this

## MANDATORY SESSION RULES
# START: Read SESSION_STATE.md -> copilot-instructions.md -> relevant skill only
# END: Update SESSION_STATE.md + relevant skill QUICK REF + REGISTRY.md if needed
# COMMIT: Only after ALL fixes done + BUILD SUCCESSFUL confirmed

## Last Session - 2026-04-10
Last task: Live location tracking — ALL STEPS COMPLETE — build + test pending
Build status: IN PROGRESS — Clean + Rebuild required in Android Studio
Branch: develop
Git: Configured as Vaishnavi (vaishnavishewale10@gmail.com)

## Current State
Android: SafeSphereService + EmergencyManager + MainActivity updated. Clean + Rebuild needed.
Dashboard: /track/[token] page + /api/track/[token] API created. Push to Vercel needed.
Supabase: live_location_sessions active. One permanent token per user.
Policy: Expiry extends ONLY on emergency trigger, not on 3-min background refresh.

## Completed This Session
- [x] STEP 0: SQL — live_location_token column + indexes + RLS policies
- [x] STEP 1: Prefs.java — setLiveLocationToken/getLiveLocationToken/clearLiveLocationToken
- [x] STEP 2: SupabaseClient.java — upsertLiveLocation() + fetchLiveLocationToken()
- [x] STEP 3: SafeSphereService.java — live-location-upsert thread on every refresh
- [x] STEP 4: EmergencyManager + MainActivity — SMS + share use tracking URL
- [x] STEP 5: admin/src/app/track/[token]/page.tsx — public Leaflet map page
- [x] STEP 6: admin/src/app/api/track/[token]/route.ts — public API route
- [x] STEP 7: admin/src/app/admin/users/[id]/page.tsx — live session card added

## Next Steps
- [ ] Clean + Rebuild Android in Android Studio — confirm BUILD SUCCESSFUL
- [ ] Git commit + push develop + main after BUILD SUCCESSFUL
- [ ] Test: emergency trigger → SMS has tracking URL → open link → map shows

## Start Next Session With Just This:
"Read SESSION_STATE.md and copilot-instructions.md. I want to [task]. After done update SESSION_STATE, skill QUICK REF, REGISTRY."