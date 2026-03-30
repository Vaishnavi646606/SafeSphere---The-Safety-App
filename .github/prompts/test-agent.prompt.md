---
mode: agent
description: "Generate tests for new or modified SafeSphere code"
tools: ["codebase", "terminal", "file"]
---

# Test Agent for SafeSphere

## Your Role
Generate comprehensive tests for SafeSphere code following project test patterns and conventions.

## When to Generate Tests
- For all new critical path code (emergency calls, session management)
- When code changes are made to existing logic
- Before marking code as "ready for review"
- When test coverage is below project standards
- User explicitly requests test generation

## Test Generation Process

### Step 1: Analyze Code to Test
- [ ] Read the code completely
- [ ] Identify all logic paths
- [ ] Find edge cases
- [ ] Identify dependencies
- [ ] Check existing tests for this code

### Step 2: Identify Test Framework
- **For Android:** JUnit, Espresso, Mockito (verify what's actually in project)
- **For Web:** Jest, React Testing Library (verify in package.json)
- [ ] Match existing test patterns in project
- [ ] Use same assertion style
- [ ] Use same mocking approach

### Step 3: Plan Test Cases
- [ ] Happy path: normal operation
- [ ] Error cases: what can go wrong
- [ ] Edge cases: boundary conditions
- [ ] For critical paths: failure scenarios and recovery

### Step 4: Write Tests Following Actual Patterns
- [ ] Use exact same imports as existing tests
- [ ] Follow exact same naming conventions
- [ ] Match existing test file organization
- [ ] Include setup and teardown matching existing tests
- [ ] Use same assertion methods

### Step 5: Test Critical Scenarios
For emergency/session related code, include:
- [ ] Network failure scenarios
- [ ] App crash and restart recovery
- [ ] Invalid input handling
- [ ] Race condition scenarios
- [ ] Permission denied scenarios

### Step 6: Verify Tests
- [ ] Tests compile/run without errors
- [ ] All tests pass
- [ ] Tests actually verify behavior
- [ ] No flaky/unreliable tests
- [ ] Test coverage adequate for criticality

## Testing Patterns

### For Android Code
```kotlin
// Use actual test patterns from SafeSphere
@RunWith(AndroidJUnit4::class)
class YourComponentTest {
    // Follow actual setup in project
}
```

### For Web/Next.js Code
```typescript
// Use actual patterns from SafeSphere tests
describe('YourFunction', () => {
    // Follow actual test setup in project
});
```

## Anti-Hallucination Checks
- [ ] Test framework actually installed in project
- [ ] Assertion methods actually exist
- [ ] Mock libraries match what's used
- [ ] Test file location matches convention
- [ ] Imports are for real packages/modules

## Examples

### Android Test Example
```
Code: EmergencyCallHandler.handleEmergencyCall()
Tests Would Include:
- Call succeeds with valid input
- Call fails gracefully with network error
- Listeners properly registered/unregistered
- Multiple rapid calls handled correctly
```

### Web Test Example
```
Code: POST /api/incidents/route.ts
Tests Would Include:
- Successful incident creation
- Missing required fields rejected
- Unauthorized request returns 401
- Database error handled gracefully
```

## Critical Path Testing
For emergency call and session persistence code:
- [ ] Test app crash recovery
- [ ] Test network interruption handling  
- [ ] Test rapid repeated calls
- [ ] Test state restoration after restart
- [ ] Test error logging and recovery

