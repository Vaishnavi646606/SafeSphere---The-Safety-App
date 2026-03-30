# Skill: Code Review

## Description
Thorough code review process for SafeSphere project changes. Checks for bugs, security issues, performance problems, hallucinations, and convention violations against actual project patterns.

## When to Use
- Before committing code to main branch
- When reviewing pull requests
- When peer-reviewing team member changes
- When checking critical path modifications (emergency calls, session management)
- Automatic trigger when user runs `@workspace /review-agent`

## Prerequisites
- Git repository initialized with SafeSphere project
- Changes staged or in working tree (visible via `git diff`)
- Reviewer has read relevant skill for the changed component
- For critical paths: reviewer understands emergency call or session persistence patterns

## Steps

### Step 1: Gather All Changes
```bash
git diff --stat
git diff HEAD
git diff --staged
```

### Step 2: Categorize Changes
- [ ] Android code (Java/Kotlin in `app/src/main/java`)
- [ ] Web code (TypeScript/Tsx in `safesphere-admin/src`)
- [ ] Configuration files
- [ ] Database migrations
- [ ] Test files
- [ ] Documentation

### Step 3: Review Each Category

#### For Android Code Changes
- [ ] **Imports**: Verify all imports are from actual Android SDK or project packages
- [ ] **Activities**: Check proper lifecycle method usage (onCreate, onStart, onResume, onPause, onDestroy)
- [ ] **Services**: Ensure bound services have proper ServiceConnection logic
- [ ] **Listeners**: Verify listeners unregister to prevent memory leaks
- [ ] **Permissions**: Check AndroidManifest.xml has required permissions declared
- [ ] **Threading**: Ensure UI updates on main thread, heavy work on background threads
- [ ] **Exception Handling**: Verify try-catch blocks around risky operations

#### For Web Code Changes (Next.js/TypeScript)
- [ ] **Imports**: Verify all npm packages in node_modules
- [ ] **API Routes**: Check /src/app/api routes handle errors and validation
- [ ] **Supabase Calls**: Verify proper async/await usage, error handling
- [ ] **Components**: Check React hooks rules (dependencies, cleanup)
- [ ] **Types**: Verify TypeScript types match Supabase schema
- [ ] **Environment Variables**: Ensure no hardcoded secrets, use .env.local
- [ ] **Async Operations**: Check for proper promise handling and race conditions

#### For Critical Paths (Emergency/Session)
If changes touch:
- Emergency call handling logic
- Session persistence mechanisms
- Phone state listeners
- Keyword detection
- Login workflows

Then also check:
- [ ] **Robustness**: Works with edge cases (network loss, app crash, rapid calls)
- [ ] **Recovery**: Can system recover from failure gracefully
- [ ] **Logging**: Critical events are logged for debugging
- [ ] **Persistence**: State survives app restart if needed
- [ ] **Testing**: Unit tests cover critical scenarios
- [ ] **Documentation**: Complex logic has comments explaining "why", not "what"

### Step 4: Hallucination Detection

For EVERY file reference:
- [ ] Run: Does this file exist? `ls "path/to/file.java"` or `ls "path/to/component.tsx"`
- [ ] If importing: Does this class/function actually exist in that file?
- [ ] If calling API: Does this Supabase function/table actually exist?
- [ ] If using Android lib: Is it in `app/build.gradle` dependencies?
- [ ] If using npm lib: Is it in `safesphere-admin/package.json`?

### Step 5: Convention Compliance

Check against real patterns found in codebase:
- [ ] **Android naming**: Activity classes PascalCase (e.g., `EmergencyCallActivity`), variables camelCase
- [ ] **Web naming**: Components PascalCase, hooks useCamelCase, utilities camelCase
- [ ] **File structure**: New files in appropriate folders matching existing structure
- [ ] **Import style**: Consistent with how project does imports
- [ ] **Error handling**: Match existing error handling patterns
- [ ] **Logging**: Use same logging mechanism as rest of project

### Step 6: Security Review

- [ ] **Secrets**: No hardcoded API keys, passwords, tokens
- [ ] **Input validation**: User input validated before use
- [ ] **SQL injection**: Supabase parameterized queries or ORM protection used correctly
- [ ] **Authentication**: Protected endpoints check user identity
- [ ] **Permissions**: Android permission checks before accessing protected resources
- [ ] **Data exposure**: No sensitive data logged or exposed in error messages

### Step 7: Performance Review

- [ ] **Loops**: No nested loops doing unnecessary work
- [ ] **Memory**: No obvious memory leaks (listeners unregistered, closures released)
- [ ] **Database**: Queries optimized, no N+1 problems
- [ ] **UI**: No blocking operations on main thread
- [ ] **Caching**: Repeated calls cached appropriately
- [ ] **Bundle size**: No large dependencies added without justification

### Step 8: Test Coverage

- [ ] Critical path changes have unit tests
- [ ] Edge cases tested
- [ ] Error scenarios tested
- [ ] Tests follow existing test patterns in project
- [ ] Tests actually verify behavior, not just code coverage

## Verification

After completing all steps:
1. [ ] Generate the output report (see format below)
2. [ ] Share findings with developer
3. [ ] If issues found, discuss severity and fixes
4. [ ] Re-review after fixes applied
5. [ ] Approve or request changes

## Output Format

```markdown
## Code Review Results for SafeSphere

**Reviewed:** [list of changed files]
**Review Date:** [date]
**Reviewer:** [who]

### ✅ Strengths
- [What was done well]
- [Good practices found]
- [Solid implementations]

### ❌ Issues Found

#### HIGH SEVERITY
1. **File:** `path/to/File.java` | **Line:** XX
   - **Problem:** [Clear description]
   - **Impact:** [Why this matters]
   - **Fix:** [Specific solution]

#### MEDIUM SEVERITY
[Same format]

#### LOW SEVERITY
[Same format]

### ⚠️ Suggestions
- [Nice-to-haves, not blockers]
- [Potential improvements]
- [Learning opportunities]

### 🔍 Hallucination Check: ✅ PASS / ❌ FAIL
- All imports verified to exist
- All file references checked
- All function calls match actual signatures
- No invented APIs or components

### 🛡️ Security Check: ✅ PASS / ❌ FAIL
- No hardcoded secrets
- Proper input validation
- Protected endpoints authenticated
- Permissions properly checked

### 🚀 Performance Check: ✅ PASS / ❌ FAIL
- No obvious performance regressions
- Memory management sound
- Threading correct (Android)
- Async patterns correct (Web)

### 🚨 Critical Path Check: ✅ PASS / ❌ NEEDS ATTENTION
(If changes touch emergency calls or session persistence)
- Recovery handling adequate
- Edge cases covered
- Logging sufficient

## Approval Decision
- [ ] ✅ **APPROVED** - Ready to merge
- [ ] 🔄 **NEEDS FIXES** - Issues must be resolved before merge
- [ ] 📋 **APPROVED WITH SUGGESTIONS** - Can merge, but consider improvements
```

## Anti-Hallucination Checks
- [ ] All referenced files verified to exist in SafeSphere
- [ ] All Android imports checked against Android SDK and project
- [ ] All npm imports checked against package.json
- [ ] All function calls match actual function signatures
- [ ] All API endpoints match Supabase schema

## Examples

### Example 1: Android Emergency Call Fix
**Files Changed:** `app/src/main/java/EmergencyCallHandler.java`, `app/src/test/.../EmergencyCallHandlerTest.java`

**The Review Would Check:**
- Is `EmergencyCallHandler` class actually in that file?
- Do all method calls exist?
- Is listener properly unregistered?
- Are emergency broadcasts handled correctly?
- Does it follow existing patterns in `PhoneStateListener`?
- Are edge cases (no network, rapid calls) handled?
- Are there proper tests?

### Example 2: Web Supabase Integration
**Files Changed:** `safesphere-admin/src/app/api/incidents/route.ts`

**The Review Would Check:**
- Does this API file exist?
- Is `incidents` table in Supabase schema?
- Are query parameters validated?
- Is authentication checked?
- Is error handling consistent with other routes?
- Can this handle errors gracefully?
- Are there tests for success and failure cases?

## Changelog
- 2026-03-30 - Initial creation with SafeSphere patterns
- Includes Android-specific and Web-specific checks
- Special attention to emergency and session critical paths
