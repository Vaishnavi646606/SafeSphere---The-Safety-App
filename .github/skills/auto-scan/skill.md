# Skill: Auto-Scan SafeSphere Codebase

## Description
Automatically scan the entire SafeSphere codebase to identify architecture, patterns, dependencies, and generate accurate documentation of current state. This skill feeds into skill generation and keeps documentation current.

## When to Use
- When setting up Copilot agent system for first time
- After major refactors to re-baseline patterns
- When onboarding new team members
- Periodically to catch drift from documented patterns
- When generating new skills specific to SafeSphere

## Prerequisites
- SafeSphere repository checked out locally
- Git initialized in repository
- Access to read all source files
- Write access to `.github/` for updating documentation

## Steps

### Phase 1: Android Codebase Scan

1. **Identify Android Root Structure**
   - Check `app/build.gradle` for:
     - Android SDK version, target SDK
     - All dependencies (list them)
     - Build types, flavors
     - Proguard rules location
   
2. **Scan Source Tree** (`app/src/main/`)
   - List all Activities in `java/**/*Activity.java`
   - List all Services in `java/**/*Service.java`
   - List all BroadcastReceivers in `java/**/*Receiver.java`
   - Identify custom UI components
   - Find database/persistence layer code
   - Identify listener implementations
   
3. **Check AndroidManifest.xml**
   - Declared activities, services, receivers
   - Required permissions
   - Intent filters
   - Application name, icon
   
4. **Scan Resources** (`app/src/main/res/`)
   - Layout files and their structure
   - Drawable assets
   - String resources, values
   
5. **Build Configuration**
   - Gradle properties and plugin versions
   - ProGuard/R8 configuration
   - Signing configurations

### Phase 2: Web Codebase Scan

1. **Check Web Root** (`admin/`)
   - Package.json contents (all dependencies and their versions)
   - Next.js configuration (next.config.ts)
   - TypeScript configuration (tsconfig.json)
   - Environment variable examples
   
2. **Scan Source Structure** (`admin/src/`)
   - List all pages in `app/`
   - List all API routes in `app/api/`
   - Find all React components
   - Identify Supabase integration code
   - Custom hooks
   - Utilities and helpers
   
3. **Database Integration**
   - Supabase configuration
   - SQL migration files (if any)
   - Database schema information

### Phase 3: Identify Patterns

For both Android and Web, identify:

1. **Code Organization Patterns**
   - Folder structure conventions
   - File naming patterns
   - Class/function organization

2. **Error Handling**
   - How exceptions are caught/thrown
   - Error logging mechanism
   - User-facing error messages

3. **State Management**
   - Session management approach
   - Data persistence mechanism
   - Caching strategies

4. **Critical Workflows**
   - Emergency call handling flow
   - Authentication/login flow
   - Session persistence flow
   - Keyword detection mechanism

5. **Testing Patterns**
   - Test frameworks used
   - Test file organization
   - Testing conventions

6. **Async/Concurrency**
   - Android: Handler/Looper, Thread, Coroutines usage
   - Web: Promise, async/await patterns
   - Network request patterns

### Phase 4: Document Findings

Create or update documentation:
```
1. Update `.github/copilot-instructions.md` with:
   - Actual Tech Stack section
   - Architecture details
   - Key Patterns section (with real examples)
   - Known issues/pain points
   
2. Create or update `.github/CODEBASE_MAP.md`:
   - Overall project structure
   - Component inventory
   - Data flow diagrams
   - Dependency graph
   
3. Create or update `.github/PATTERNS.md`:
   - Android patterns with code references
   - Web patterns with code references
   - Critical path patterns
```

## Verification
- [ ] All identified files and classes actually exist in codebase
- [ ] Dependencies listed match what's in build files
- [ ] Patterns documented with real code examples
- [ ] No hallucinated components or libraries
- [ ] Documentation reflects current reality, not hoped-for state

## Anti-Hallucination Checks
- [ ] Every file path checked to actually exist
- [ ] Every dependency verified in build.gradle or package.json
- [ ] Every pattern example from real code in repository
- [ ] No fictional activities, services, or components
- [ ] No invented npm packages

## Examples

### Example Android Finding
```
Activity Inventory:
✅ MainActivity.java (entry point)
✅ EmergencyCallActivity.java (emergency workflow)
✅ SessionManagerActivity.java (session handling)

Dependencies Found:
- androidx.appcompat:appcompat:1.x.x
- com.google.android.material:material:1.x.x
- androidx.room:room-runtime:2.x.x
(etc.)
```

### Example Web Finding
```
Pages Structure:
✅ /src/app/page.tsx (dashboard home)
✅ /src/app/admin/dashboard/page.tsx (admin panel)
✅ /src/app/admin/incidents/page.tsx (incident management)

API Routes:
✅ /src/app/api/incidents/route.ts
✅ /src/app/api/auth/login/route.ts
✅ /src/app/api/users/route.ts

Dependencies:
- next: 14.x
- @supabase/supabase-js: 2.x
- react: 18.x
(etc.)
```

## Changelog
- 2026-03-30 - Initial creation for SafeSphere project
- Designed to scan both Android and Web tiers
- Focuses on reducing hallucination through verification
