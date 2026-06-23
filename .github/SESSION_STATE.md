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
UI Note: "Location reported" time is phone-upload age; it can be delayed even when page refresh is recent.
Metrics API: all-time emergency counts enabled; query failures now return 500 instead of zeros.
Next: Clean + Rebuild Android, then verify SMS tracking URL -> map flow.
Start next session with: "Read SESSION_STATE.md and copilot-instructions.md. I want to [task]. After done update SESSION_STATE, skill QUICK REF, REGISTRY."