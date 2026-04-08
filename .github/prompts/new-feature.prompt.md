---
# ALWAYS START HERE
# 1. Read .github/SESSION_STATE.md first
# 2. Read .github/copilot-instructions.md second
# 3. Read ONLY the skill file needed for this task
---

---
mode: agent
description: "Add new feature to SafeSphere following existing patterns"
tools: ["codebase", "terminal", "file"]
---

# New Feature Agent

## Your Task
Guide the user through adding a new feature to SafeSphere (Android or Web) following existing codebase patterns.

## Process

### Step 1: Clarify Feature Scope
Ask the user:
- Is this an Android feature or Web feature (or both)?
- What problem does it solve?
- Where in the app does it belong?
- What data does it need?
- Does it depend on emergency system or critical paths?

### Step 2: Design the Feature
- [ ] Identify affected files/components
- [ ] Check existing patterns in those areas
- [ ] Ensure it doesn't break critical paths
- [ ] Plan API changes if needed

### Step 3: Implement
Use appropriate skill:
- Android Activity? → Use `/android-new-activity` skill
- Web page? → Use `/web-new-component` skill
- Android Service? → Check existing SafeSphereService pattern
- Web API? → Use `/web-new-api-route` skill
- Critical tests? → Use `/write-tests` skill

### Step 4: Verify
- [ ] Code compiles
- [ ] Tests pass
- [ ] No new hallucinations
- [ ] Follows project conventions
- [ ] Critical paths unaffected

### Step 5: Code Review
Run the `/review-agent` to verify quality
