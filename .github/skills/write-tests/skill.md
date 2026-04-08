# Skill: Write Tests for SafeSphere

## Description
Write unit and integration tests for SafeSphere code following existing test patterns and frameworks.

## When to Use
- Adding tests for new features
- Testing critical path changes (emergency calls, session persistence)
- Ensuring code quality before merge
- TDD (test-first development)

## Prerequisites
- SafeSphere test framework: JUnit 4.13.2 (Android), Jest (Web implied)
- Understanding of test patterns in project
- Access to build.gradle (Android) or package.json (Web)

## Android Testing

### Unit Tests (Local Tests)
**Location:** `app/src/test/java/com/example/safesphere/`

**Framework:** JUnit 4.13.2

```java
import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.*;

public class PrefsTest {
    
    private Context context;
    
    @Before
    public void setUp() {
        // Called before each test
        context = InstrumentationRegistry.getInstrumentation().getContext();
    }
    
    @Test
    public void testPrefsGetSet() {
        Prefs.setName(context, "John Doe");
        assertEquals("John Doe", Prefs.getName(context));
    }
    
    @Test
    public void testLoginFlag() {
        Prefs.setLoggedIn(context, true);
        assertTrue(Prefs.isLoggedIn(context));
        
        Prefs.setLoggedIn(context, false);
        assertFalse(Prefs.isLoggedIn(context));
    }
}
```

### Instrumentation Tests (Device Tests)
**Location:** `app/src/androidTest/java/com/example/safesphere/`

**Framework:** Espresso 3.6.1

```java
@RunWith(AndroidJUnit4.class)
public class LoginActivityTest {
    
    @Rule
    public ActivityScenarioRule<LoginActivity> activityRule =
        new ActivityScenarioRule<>(LoginActivity.class);
    
    @Test
    public void testLoginButtonClick() {
        // Type phone number
        onView(withId(R.id.phoneInput))
            .perform(typeText("5551234567"));
        
        // Click login button
        onView(withId(R.id.loginBtn))
            .perform(click());
        
        // Wait for navigation
        Thread.sleep(2000);
        
        // Verify MainActivity shown
        onView(withId(R.id.protectionToggle))
            .check(matches(isDisplayed()));
    }
    
    @Test
    public void testEmptyPhoneError() {
        onView(withId(R.id.loginBtn))
            .perform(click());
        
        onView(withId(R.id.errorText))
            .check(matches(withText(containsString("Phone required"))));
    }
}
```

### Test Patterns

**Testing SharedPreferences (Prefs.java):**
```java
@Test
public void testPrefsArePersistent() {
    String name = "Test User";
    Prefs.setName(context, name);
    
    // Simulate app restart by recreating SharedPreferences
    Context context2 = InstrumentationRegistry.getInstrumentation().getContext();
    assertEquals(name, Prefs.getName(context2));
}
```

**Testing emergency flow:**
```java
@Test
public void testEmergencyDecisionScoring() {
    // Keyword detected: +5
    int score = EmergencyDecisionAPI.calculateScore(
        true,   // keywordDetected
        false,  // shakeDetected
        false   // locationAvailable
    );
    assertEquals(5, score);
    assertTrue(EmergencyDecisionAPI.shouldTrigger(score));
    
    // Shake detected: +3 (not enough)
    score = EmergencyDecisionAPI.calculateScore(false, true, false);
    assertEquals(3, score);
    assertFalse(EmergencyDecisionAPI.shouldTrigger(score));
    
    // Keyword + location: +5+2 = +7 (triggers)
    score = EmergencyDecisionAPI.calculateScore(true, false, true);
    assertEquals(7, score);
    assertTrue(EmergencyDecisionAPI.shouldTrigger(score));
}
```

**Testing contact sequence:**
```java
@Test
public void testContactSequenceAdvances() {
    Prefs.setCallSequenceIndex(context, 0);
    assertEquals(0, Prefs.getCallSequenceIndex(context));
    
    // Simulate call not answered, move to next
    EmergencyManager.advanceToNextContact(context);
    assertEquals(1, Prefs.getCallSequenceIndex(context));
    
    EmergencyManager.advanceToNextContact(context);
    assertEquals(2, Prefs.getCallSequenceIndex(context));
    
    // No more contacts, should stay at 2
    EmergencyManager.advanceToNextContact(context);
    assertEquals(2, Prefs.getCallSequenceIndex(context));
}
```

## Web Testing

### Jest Unit Tests
**Location:** `admin/src/` or `__tests__/`

```typescript
describe('User Registration', () => {
  it('should validate empty phone', async () => {
    const response = await fetch('/api/user/register', {
      method: 'POST',
      body: JSON.stringify({ name: 'John', phone: '' }),
    });
    
    expect(response.status).toBe(400);
    const data = await response.json();
    expect(data.error).toContain('phone');
  });
  
  it('should register new user', async () => {
    const response = await fetch('/api/user/register', {
      method: 'POST',
      body: JSON.stringify({
        name: 'John Doe',
        phone: '5551234567',
      }),
    });
    
    expect(response.status).toBe(200);
    const data = await response.json();
    expect(data.user_id).toBeDefined();
    expect(data.is_active).toBe(true);
  });
});

describe('Admin Auth', () => {
  it('should reject unauthenticated request', async () => {
    const response = await fetch('/api/admin/metrics');
    
    expect(response.status).toBe(401);
  });
  
  it('should reject non-admin user', async () => {
    // Login as regular user, then try admin endpoint
    // Mock Supabase auth
    const response = await fetch('/api/admin/metrics', {
      headers: {
        'Authorization': 'Bearer user_token_not_admin',
      },
    });
    
    expect(response.status).toBe(403);
  });
});
```

## Test Execution

### Android
```bash
# Run all unit tests
./gradlew test

# Run instrumentation tests
./gradlew connectedAndroidTest

# Run specific test class
./gradlew test --tests com.example.safesphere.PrefsTest

# Run specific test method
./gradlew test --tests com.example.safesphere.PrefsTest.testPrefsGetSet
```

### Web
```bash
# Run Jest tests
npm test

# Watch mode
npm test -- --watch

# Coverage report
npm test -- --coverage
```

## Verification
- [ ] Tests compile without errors
- [ ] All tests pass locally
- [ ] Tests have descriptive names (testXxx or describe('XXX'))
- [ ] Edge cases covered (empty inputs, network errors, null checks)
- [ ] Critical path tests included (emergency, session, auth)
- [ ] Test output shows pass/fail clearly
- [ ] Code coverage reasonable (>70% for critical paths)

## Anti-Hallucination Checks
- [ ] JUnit 4.13.2 in app/build.gradle
- [ ] Espresso 3.6.1 in app/build.gradle  
- [ ] Test class names end with Test suffix
- [ ] Test method names start with test prefix
- [ ] Assertion methods from correct import (org.junit.Assert)
- [ ] No references to non-existent methods or classes

## Changelog
- 2026-03-30 - Initial testing documentation
- Documented JUnit and Espresso patterns
- Documented Web Jest pattern
- Added examples for Prefs, emergency flow, contact sequence
- Included test execution commands
