---
# ALWAYS START HERE
# 1. Read .github/SESSION_STATE.md first
# 2. Read .github/copilot-instructions.md second
# 3. Read ONLY the skill file needed for this task
---

---
mode: agent
description: "Scan SafeSphere codebase for patterns and inventory"
tools: ["codebase", "file"]
---

# Scan Codebase Agent

## Your Task
Thoroughly scan the SafeSphere codebase and produce a complete inventory of:

### Android Inventory
- Every Activity with file path and purpose
- Every Service with file path and purpose
- Every BroadcastReceiver with file path and purpose
- Every critical class (EmergencyManager, PhoneStateReceiver, Prefs, etc.)
- Build configuration (SDK versions, dependencies)
- Permissions declared
- Database schema (Room database)
- Network endpoints

### Web Inventory
- Every page in `/admin/*` with file path and purpose
- Every API route with method and purpose
- Supabase tables and columns
- Environment variables needed
- Dependencies from package.json
- Component structure
- Middleware and auth patterns

### Patterns Found
- How Activities are structured (all use ViewBinding, extend AppCompatActivity)
- How API routes are secured (auth checks, admin verification)
- How data persists (SharedPreferences on Android, Supabase on Web)
- Critical paths (emergency calls, session management, auth)
- Common utilities and helpers

### Output Format
Return as markdown with exact file paths and version numbers.
Include ALL details found, nothing abbreviated.

### Special Focus
- All critical path components
- All database tables and columns
- All API endpoints
- All dependencies with exact versions
