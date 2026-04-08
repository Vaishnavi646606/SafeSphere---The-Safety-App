# SafeSphere Skill Registry

This registry tracks all available skills and agents for SafeSphere development. Use `/skillname` in VS Code Copilot chat to trigger a skill.

## Core System Skills

| Skill Name | Path | Description | Type | Last Updated |
|------------|------|-------------|------|--------------|
| Auto-Update Skills | `.github/skills/skill-updater/skill.md` | Auto-updates skills based on new instructions | Core | 2026-03-30 |
| Code Review | `.github/skills/code-review/skill.md` | Comprehensive code review process | Core | 2026-03-30 |
| Auto-Scan | `.github/skills/auto-scan/skill.md` | Scan codebase and document patterns | Core | 2026-03-30 |

## Agent Prompts

| Agent Name | Path | Description | Type | Last Updated |
|------------|------|-------------|------|--------------|
| Generate All Skills | `.github/prompts/generate-all-skills.prompt.md` | Master scanner and skill generator | Agent | 2026-03-30 |
| Review Agent | `.github/prompts/review-agent.prompt.md` | Automated code review | Agent | 2026-03-30 |
| Fix Agent | `.github/prompts/fix-agent.prompt.md` | Apply fixes to code issues | Agent | 2026-03-30 |
| Test Agent | `.github/prompts/test-agent.prompt.md` | Generate tests for code | Agent | 2026-03-30 |
| New Feature Agent | `.github/prompts/new-feature.prompt.md` | Structured implementation flow for new features | Agent | 2026-03-30 |
| Scan Codebase Agent | `.github/prompts/scan-codebase.prompt.md` | Deep codebase scan and context extraction | Agent | 2026-03-30 |
| Update Skills Agent | `.github/prompts/update-skills.prompt.md` | Update skills and registry after changes | Agent | 2026-03-30 |
| ui-ux-agent | `.github/prompts/ui-ux-agent.prompt.md` | Review and fix UI/UX across all pages | Agent | 2026-04-08 |

## Android Development Skills

| Skill Name | Path | Description | Last Updated |
|------------|------|-------------|--------------|
| Create Android Activity | `.github/skills/android-new-activity/skill.md` | Create new Activities with ViewBinding, lifecycle methods, permissions | 2026-03-30 |

## Web Development Skills

| Skill Name | Path | Description | Last Updated |
|------------|------|-------------|--------------|
| Create Web API Route | `.github/skills/web-new-api-route/skill.md` | Create Next.js API routes with auth, Supabase queries, error handling | 2026-04-08 |
| Create Web Component | `.github/skills/web-new-component/skill.md` | Create React components with TypeScript, Tailwind CSS, Lucide icons | 2026-03-30 |
| UI/UX Review | `.github/skills/ui-ux-review/skill.md` | Review UI auth states, sidebar visibility, responsive behavior, and user flow regressions | 2026-03-30 |
| ui-ux-design | `.github/skills/ui-ux-design/skill.md` | Design system rules and visual standards | 2026-04-08 |

## Critical Path Skills

| Skill Name | Path | Description | Last Updated |
|------------|------|-------------|--------------|
| Emergency Call System | `.github/skills/emergency-call-system/skill.md` | Emergency call sequence, phone state detection, battery optimization, testing | 2026-03-30 |
| Session Persistence & Auth | `.github/skills/session-persistence/skill.md` | SharedPreferences, login flow, offline auto-login fix, session recovery, revocation handling | 2026-04-08 |
| Offline Sync Worker | `.github/skills/offline-sync/skill.md` | WorkManager-based sync for profile and feedback when offline | 2026-04-03 |

## General Development Skills

| Skill Name | Path | Description | Last Updated |
|------------|------|-------------|--------------|
| Write Tests | `.github/skills/write-tests/skill.md` | JUnit/Espresso (Android), Jest (Web), test patterns, examples | 2026-03-30 |

## How to Use

### Run Code Review
```
/review-agent
```

### Run Master Scan (First Setup)
```
Read the file .github/prompts/generate-all-skills.prompt.md and execute ALL the phases to generate project-specific skills.
```

### Update a Skill
Just tell the agent naturally:
```
"Update the code-review skill to also check for race conditions in concurrent code"
```

The skill-updater will automatically:
1. Find the skill file
2. Add your instruction
3. Update the changelog
4. Confirm the change

### Create New Skill
```
"Create a skill for how to integrate [new technology/pattern]"
```

## Registry Maintenance

This registry is automatically updated when:
- ✅ New skills are generated from master scan
- ✅ Projects are updated via the skill-updater
- ✅ New agent prompts are created

### Manual Updates Needed
- [ ] After running first master scan, review generated skills and add to this registry
- [ ] Periodically verify skills match actual codebase patterns
- [ ] Remove or archive outdated skills

## Anti-Hallucination Note

All skills in this registry:
- ✅ Reference REAL files in SafeSphere codebase
- ✅ Describe ACTUAL patterns found in code
- ✅ Use REAL examples from the project
- ✅ Contains NO invented components or features

If a skill references something that no longer exists, it should be updated or archived.

---

**Last Registry Update:** 2026-04-09  
**Registry Status:** ✅ Complete - All project-specific skills generated
**Total Skills:** 12 (3 core + 9 project-specific)
**Total Agent Prompts:** 8

### Skills Coverage
✅ Android Activities, Services, Broadcast Receivers
✅ Web Pages, API Routes, Components  
✅ Critical paths: Emergency calls, Session persistence, Auth
✅ Testing: JUnit/Espresso (Android), Jest (Web)
✅ Agent prompts: Review, Fix, Test, New Feature, Scan, Update Skills

---

## Changelog

### 2026-04-09
- Supabase project migrated to new project URL: qzezwpzmxkwxgrtxucaw.supabase.co
- Location system updated: 3-min refresh, first-capture on startup, offline fallback with age text
- New Prefs methods: setLastSyncedLocation, hasAnySavedLocation, setFirstLocationCaptured
- New SupabaseClient method: updateUserLocation(userId, lat, lng)
- Emergency INSERT now stores contact numbers immediately
- Connectivity check moved to BEFORE insert
- Admin dashboard: admin login created on new Supabase project
