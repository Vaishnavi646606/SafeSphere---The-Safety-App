---
mode: agent
description: "Scan entire SafeSphere codebase and auto-generate all skills and agents"
tools: ["codebase", "terminal", "file"]
---

# Master Task: Scan SafeSphere Codebase & Generate All Skills + Agents

You are a **Skill & Agent Architect**. Your job is to deeply analyze this entire SafeSphere codebase and automatically create useful, accurate skills and agents.

## Phase 1: Deep Codebase Scan

Scan and analyze EVERY file in this project. Identify:

1. **Tech Stack**: Languages, frameworks, libraries, package managers
2. **Architecture Pattern**: App structure, components, layers
3. **Folder Structure**: How code is organized in Android and Web modules
4. **Key Files**: Entry points, configs, main activities, services
5. **Build System**: Gradle (Android), npm/Next.js (Web)
6. **Database**: Supabase PostgreSQL, migrations, schema
7. **API Structure**: Supabase API routes, Android to backend communication
8. **Testing**: Test patterns and frameworks used
9. **CI/CD**: Pipeline configs if any
10. **Common Patterns**: Recurring code patterns, utilities, helpers
11. **Pain Points**: Complex areas (emergency call handling, session persistence, keyword detection)
12. **Dependencies**: Android libraries, npm packages with versions

## Phase 2: Update Global Instructions

Update `.github/copilot-instructions.md` with the actual detected:
- Confirmed tech stack details
- Architecture specifics for Android and Web tiers
- Key patterns found in code
- Important conventions for Android and Web

## Phase 3: Generate Skills

For EACH identified area, create a skill in `.github/skills/[skill-name]/skill.md`.

### Required Skills to Generate:

#### a) SafeSphere-Specific Skills
Based on what you find, generate skills for:
- **android-new-feature**: How to add a new feature to Android app following existing patterns
- **android-emergency-workflow**: How to work with emergency call system
- **web-new-feature**: How to add a new feature to Next.js admin dashboard
- **web-supabase-integration**: How to integrate Supabase API calls in web dashboard
- **android-new-activity**: How to create a new Activity following existing patterns
- **android-database-persistence**: How to implement persistence in Android following existing patterns
- **android-testing**: How to write tests for Android code
- **web-testing**: How to write tests for Next.js code
- **emergency-call-handling**: Special skill for emergency call system (critical area)
- **session-persistence**: Special skill for session management (critical area)
- **keyword-detection**: Special skill for keyword detection logic

#### b) Code Review Skill (MANDATORY)
Create `.github/skills/code-review/skill.md` that:
- Reviews for bugs, security issues, performance problems
- Checks against SafeSphere conventions
- Validates no hallucinated imports/references
- Verifies all files referenced actually exist
- Checks for proper error handling in critical paths (emergency calls, session management)
- Ensures tests are included for critical code

#### c) Skill Updater Skill (MANDATORY)
Create `.github/skills/skill-updater/skill.md` that:
- When given new instructions, finds the right skill file
- Updates it with new information
- Adds changelog entry
- If no matching skill exists, creates a new one

### Skill File Format (use this for EVERY skill):

```markdown
# Skill: [Name]

## Description
[What this skill does]

## When to Use
[Triggers/scenarios for this skill]

## Prerequisites
[What must exist before running this skill]

## Steps
[Detailed step-by-step procedure]

## Verification
[How to verify the skill worked correctly]

## Anti-Hallucination Checks
- [ ] All referenced files verified to exist
- [ ] All imports verified against package manager or Android libs
- [ ] Pattern matches existing codebase conventions

## Examples
[Real examples from THIS codebase, not invented ones]

## Changelog
- [Date] - Initial creation from codebase scan
```

## Phase 4: Generate Agent Prompts

Create reusable agent prompts in `.github/prompts/`:

### a) `review-agent.prompt.md` (Code Review)
Review code changes for errors, bugs, and convention violations

### b) `fix-agent.prompt.md` (Bug Fixing)
Fix identified issues following SafeSphere patterns

### c) `test-agent.prompt.md` (Testing)
Generate tests matching SafeSphere test patterns

### d) `android-agent.prompt.md` (Android Development)
Android-specific development tasks

### e) `web-agent.prompt.md` (Web Development)
Web/Next.js specific development tasks

## Phase 5: Create Skill Registry

Create `.github/skills/REGISTRY.md`:
```markdown
# SafeSphere Skill Registry
| Skill Name | Path | Description | Last Updated |
|------------|------|-------------|--------------|
| ... | ... | ... | ... |
```

## Phase 6: Verification

After generating everything:
1. List ALL files you created/modified
2. Verify each skill references REAL files from the SafeSphere codebase
3. Confirm no hallucinated content
4. Show the final folder structure

## IMPORTANT RULES
- Only reference files, functions, and patterns that ACTUALLY EXIST in SafeSphere
- If a component doesn't exist, don't invent it
- Every example in a skill must come from REAL code in this project
- Include the EXACT file paths from this project
- For Android: reference actual Activities, Services, and Android libraries
- For Web: reference actual Next.js pages, components, and API routes
