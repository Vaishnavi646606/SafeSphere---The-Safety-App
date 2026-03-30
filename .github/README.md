# SafeSphere Copilot Customization System

This directory contains VS Code Copilot agent customization for SafeSphere development. It includes anti-hallucination rules, reusable skills, and agent prompts to increase code quality and prevent AI-generated mistakes.

## Quick Start

### First Time Setup (Run Once)

1. **Master Scan & Generate Skills**
   - Open VS Code Copilot Chat (Ctrl+Shift+I)
   - Copy this prompt and paste it in Copilot Chat:

```
Read the file .github/prompts/generate-all-skills.prompt.md
Execute ALL phases to scan the entire SafeSphere codebase
Generate all project-specific skills in .github/skills/
Create skill registry
Verify no hallucinations - only real files/patterns
```

2. **Verify the Results**
   - Check that new skill folders appeared in `.github/skills/`
   - Review `.github/skills/REGISTRY.md` for complete list
   - Update `.github/copilot-instructions.md` if needed

### Daily Development

| What You Need | Command |
|---|---|
| **Review your changes** | `@workspace /review-agent` |
| **Add new Android Activity** | `@workspace /android-new-activity` (after skills generated) |
| **Add new Web page** | `@workspace /web-new-page` (after skills generated) |
| **Generate tests** | `@workspace /test-agent` |
| **Fix code issues** | `@workspace /fix-agent` |
| **Update a skill** | Say: "Update the [X] skill to also include..." |

## Key Files

### Global Rules (Always Active)
- **`.github/copilot-instructions.md`** - Anti-hallucination rules, project context, must-follow guidelines

### Master Orchestrators
- **`.github/prompts/generate-all-skills.prompt.md`** - Scan entire codebase and auto-generate all skills

### Reusable Prompts (Use Anytime)
- **`.github/prompts/review-agent.prompt.md`** - Code review checklist and process
- **`.github/prompts/fix-agent.prompt.md`** - Apply fixes following patterns
- **`.github/prompts/test-agent.prompt.md`** - Generate tests

### Core Skills (Manually Created)
- **`.github/skills/auto-scan/skill.md`** - Scan codebase systematically
- **`.github/skills/code-review/skill.md`** - Detailed review process
- **`.github/skills/skill-updater/skill.md`** - Auto-update skills from instructions

### Generated Skills (Created by Master Scan)
After running the master scan, you'll have:
- `.github/skills/android-new-activity/`
- `.github/skills/web-new-page/`
- `.github/skills/emergency-call-handling/`
- `.github/skills/session-persistence/`
- ... (many more)

See `.github/skills/REGISTRY.md` for complete list.

## How It Works

### Step 1: Global Rules Always Apply
Every response from Copilot checks these rules first:
```
✓ Don't invent files/functions that don't exist
✓ Verify imports actually installed
✓ Use real examples from codebase
✓ Read actual code before suggesting changes
```

### Step 2: Skills Activate When Needed
When you type:
- `@workspace /review-agent` → uses `.github/prompts/review-agent.prompt.md`
- `@workspace /android-new-activity` → uses `.github/skills/android-new-activity/skill.md`
- Mention a skill by name → Copilot finds and uses it

### Step 3: Auto-Update on Instruction
When you say:
```
"Update the emergency-call-handling skill to add network timeout handling"
```

The system:
1. Finds `.github/skills/emergency-call-handling/skill.md`
2. Adds your instruction to Steps section
3. Appends changelog entry
4. Confirms changes to you

### Step 4: Verification & Feedback
Every output is checked:
```
Hallucination Check: ✅ PASS
├─ All files verified to exist
├─ All imports checked against package.json / build.gradle
└─ All examples from real code

Safety Check: ✅ PASS (if code-related)
└─ Critical paths handled correctly
```

## When to Use Each

### Code Review (`/review-agent`)
- Before committing to main
- When reviewing pull requests
- Before pushing to production
- **Command:** `/review-agent`

### Code Fix (`/fix-agent`)
- After issues identified in review
- When tests are failing
- When bugs reported
- **Command:** `/fix-agent` (will ask for issue details)

### Test Generation (`/test-agent`)
- For all new critical code
- When code changes
- Before marking "ready"
- **Command:** `/test-agent` (will ask what to test)

### Add New Feature
- Follow the appropriate skill:
  - `/android-new-feature` (after generated)
  - `/web-new-feature` (after generated)
  - Or ask: "Create a skill for how to [do something] in this project"

### Update Skill
- Just say naturally:
  ```
  "Update the code-review skill to also check [new thing]"
  ```
- Skill-updater automatically finds and updates it

## Important Rules

### 🚫 Never Ignore
1. **Check `.github/copilot-instructions.md` first** - Global rules apply to everything
2. **Verify file existence** - Every file path should exist
3. **Match actual imports** - Never assume packages are installed
4. **Use real examples** - Never fabricate code examples
5. **Follow existing patterns** - Match what the codebase does

### ✅ Always Do
1. Read actual code before suggesting changes
2. Include line numbers when referencing code
3. Verify file paths actually exist in project
4. Match existing naming conventions
5. Include error handling in suggestions
6. Test critical path changes thoroughly
7. Document changes in skill changelog

## Critical Path Code

Special care for emergency-related code:

- Emergency call handling (`emergency-call-handling` skill)
- Session persistence (`session-persistence` skill)  
- Phone state listeners (`app/src/main/.../PhoneStateListener.java`)
- Authentication flows (`safesphere-admin/src/app/api/auth/`)

These have extra review requirements and must include:
- ✅ Comprehensive error handling
- ✅ Recovery from failures
- ✅ Proper logging for debugging
- ✅ Edge case coverage
- ✅ Test cases for failure scenarios

## Troubleshooting

### Skill Not Appearing
- **Cause:** Generated skills stored but not registered
- **Fix:** Check `.github/skills/REGISTRY.md` is updated

### Copilot Hallucinating (Suggesting Non-existent Files)
- **Cause:** Global instructions not being read
- **Fix:** Make sure `.github/copilot-instructions.md` exists and is complete
- **Verify:** Ask Copilot "List the anti-hallucination rules" and check response

### Skill Outdated (Code Changed)
- **Cause:** Skill created before recent code changes
- **Fix:** Say "Update the [skill] skill to reflect current patterns in [file]"
- **Automatic:** Skill-updater will find and update it

### Want to Create New Skill
- **Say:** "Create a new skill for [what you want to do] in this project"
- **Copilot will:** Use auto-scan to find real patterns, create skill file, update registry

## Skill Lifecycle

```
1. User Request
   ↓
2. Skill-Updater activated (if updating) OR Manual creation (if new)
   ↓
3. Skill file created/updated in .github/skills/[name]/skill.md
   ↓
4. REGISTRY.md updated automatically
   ↓
5. Changelog entry added with date
   ↓
6. Skill ready to use (activate with @workspace /[name])
   ↓
7. Periodically: Auto-scan verifies no hallucinations
```

## Registry & Documentation

- **`.github/skills/REGISTRY.md`** - Complete inventory of all skills
- **`.github/copilot-instructions.md`** - Global rules and project context
- **`README.md`** (this file) - How to use the system
- **Skill CHANGELOG** - Every skill has changelog of updates at bottom

## Common Patterns (After Master Scan)

These will be documented automatically:

### Android
- How to create Activities
- How to make Services
- How to handle permissions
- How to persist data
- How to register receivers

### Web
- How to create API routes
- How to integrate Supabase
- How to create components
- How to handle auth
- How to test

### Critical Paths
- Emergency call flow
- Session persistence
- Keyword detection
- Authentication

---

## Status

- ✅ Core system created and ready
- ⏳ Awaiting first master scan to generate project-specific skills
- 📋 Skills will auto-update as instructions given

**Setup Date:** 2026-03-30  
**System Status:** Ready for master scan

---

**Questions?**
1. Read `.github/copilot-instructions.md` for rules
2. Check skill file directly (e.g., `.github/skills/code-review/skill.md`)
3. Review skill examples for your use case
4. Ask Copilot: "What does [skill] do?"
