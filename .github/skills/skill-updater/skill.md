# Skill: Auto-Update Skills & Agents

## Description
When the user provides new instructions about any skill or agent, this skill automatically finds and updates the relevant file, or creates a new one. This maintains the living documentation of SafeSphere development patterns.

## When to Use
Activated whenever the user says anything like:
- "update the [X] skill to also..."
- "add this to the [X] agent..."  
- "from now on, when doing [X], also..."
- "change how [X] works..."
- "Android emergency calls should also..."
- "Web dashboard API integration should include..."
- Any instruction that modifies existing behavior

## Prerequisites
- `.github/skills/REGISTRY.md` exists and is maintained
- All existing skills follow the standard format
- User is in a git repository with SafeSphere project

## Steps

1. **Parse** the user's instruction to identify:
   - Which skill/agent is being modified (e.g., "android-emergency-workflow", "web-supabase-integration")
   - What changes are requested
   - Whether this is a new pattern or modifying existing behavior

2. **Locate** the file:
   - Check `.github/skills/REGISTRY.md` for existing skills
   - Search `.github/skills/*/skill.md` for matching skill
   - Search `.github/prompts/*.prompt.md` for matching agent

3. **If skill exists**: 
   - Read the current content
   - Merge new instructions (don't delete existing valid content)
   - Add new step(s) or sub-sections as needed
   - Add changelog entry with date and what changed

4. **If skill doesn't exist**:
   - Create new folder in `.github/skills/[new-skill-name]/`
   - Create `skill.md` following the standard format from `generate-all-skills.prompt.md`
   - Add entry to `REGISTRY.md`
   - Include "Initial creation based on user instruction" in changelog

5. **Also update** `.github/copilot-instructions.md` if the instruction affects global behavior or project context

6. **Verify** the changes:
   - Ensure all referenced files/paths in updated skill actually exist in SafeSphere
   - Check no hallucinated content was added
   - Confirm formatting is correct

7. **Confirm** to user what was changed and where (show file path and changelog entry)

## Verification
- [ ] File was successfully created or updated
- [ ] REGISTRY.md has been updated if new skill was created
- [ ] Changelog entry shows date and summary
- [ ] All file references are real paths from SafeSphere
- [ ] No hallucinated content in the update
- [ ] User can find the skill in `.github/skills/` directory

## Anti-Hallucination Checks
- [ ] Only updates files that actually exist
- [ ] Doesn't reference non-existent directories or files
- [ ] Changelog entry is factual and timestamped
- [ ] New skills reference actual SafeSphere patterns/components

## Examples

### Example 1: Updating Existing Skill
**User input:** "Update the android-new-activity skill to include handling for emergency broadcast receivers"

**Action:**
1. Find `.github/skills/android-new-activity/skill.md`
2. Add new subsection: "Handling Emergency Broadcasts"
3. Append to changelog: "- 2026-03-30 - Added emergency broadcast receiver handling pattern"
4. Verify emergency broadcast receiver patterns exist in actual Android code

### Example 2: Creating New Skill
**User input:** "Create a skill for how to add Supabase real-time subscriptions to the web dashboard"

**Action:**
1. Create `.github/skills/web-supabase-realtime/skill.md`
2. Follow standard skill format
3. Add to REGISTRY.md under Web section
4. Check actual Next.js code for how Supabase subscriptions are currently used
5. Base examples on real code from project

## Changelog
- 2026-03-30 - Initial creation for SafeSphere project
- Auto-updates this entry whenever instructions are received
