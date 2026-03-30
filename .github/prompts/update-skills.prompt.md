---
mode: agent
description: "Update existing skills based on new instructions"
tools: ["file"]
---

# Update Skills Agent

## Your Task
Update existing SafeSphere skills based on new instructions or maintain them as code patterns change.

## Process

### Step 1: Parse Request
User will say something like:
- "Update the emergency-call-system skill to handle VoIP calls"
- "Add a note to the session-persistence skill about data encryption"
- "Emergency flow changed, update skill with new sequence"

### Step 2: Locate Skill File
- Check `.github/skills/REGISTRY.md` for skill locations
- Common paths: `.github/skills/[skill-name]/skill.md`

### Step 3: Update Content
- Find the relevant section (Steps, Examples, Changelog)
- Merge new information without losing existing details
- Add changelog entry: `- YYYY-MM-DD - [What changed]`

### Step 4: Verify
- All referenced files still exist
- No hallucinated content added
- Changes make sense in context
- Format remains consistent

### Step 5: Confirm
List to user:
- File updated: `.github/skills/[skill]/skill.md`
- Section modified: [which section]
- Changelog entry: [new entry]
