# QUICK REF — Session Persistence & Auth
# Read only this block unless implementing changes

When to use this skill:
    - Login / Register / auto-login flows
    - Token revocation
    - Admin message delivery

Key methods:
    Prefs.isLoggedIn() / setLoggedIn()
    Prefs.getSupabaseUserId() ← ALWAYS use this, never getUserId()
    Prefs.isPendingRevocation() / setRevocationMessage()

Supabase project: qzezwpzmxkwxgrtxucaw.supabase.co
Admin login: verified working April 2026

Last change: 2026-04-09 — Supabase migrated to new project

# Skill: Session Persistence & Auth

## Description
Handle user authentication, session persistence, and data survival across app crash/restart. This is a **CRITICAL PATH** requiring robust error handling and comprehensive testing.

## When to Use
- Fixing login/registration bugs
- Ensuring session survives app crash
- User data persists across app restart
- Debugging authentication issues
- Working with LoginActivity, RegisterActivity, Prefs.java

## Prerequisites
- Understanding Android SharedPreferences (key-value persistent storage)
- Understanding Android Activity lifecycle
- Understanding Web next-auth patterns via Supabase

## Critical Path Rules
- ✅ **Session data must survive app process death**
- ✅ **Credentials never hardcoded, always from SharedPreferences**
- ✅ **Login state checked on every Activity onCreate()**
- ✅ **Failure to restore session redirects to LoginActivity**
- ✅ **Network timeouts handled gracefully (don't block UI)**

## Session Architecture

### Android Tier Session Management

**File:** [app/src/main/java/com/example/safesphere/Prefs.java](app/src/main/java/com/example/safesphere/Prefs.java)

**SharedPreferences key:** `safesphere_prefs`

**Critical persistence keys:**
```java
// Identification
"name"          // User's display name
"phone"         // User's phone number
"keyword"       // Emergency keyword
"e1", "e2", "e3" // Emergency contacts (3)

// Authentication
"logged_in"     // Boolean: is user logged in?
"user_id"       // Unique user ID from Supabase
"session_id"    // Per-emergency session tracking

// Emergency state
"call_sequence_index" // 0, 1, 2 (which contact being called)
"protection_enabled"  // Boolean: is protection on?

// Revocation state
"pending_revocation"  // Boolean: revoked by admin?
"revocation_message"  // Message shown when account is revoked
```

**Pattern for reading (from Prefs.java):**
```java
public static String getName(Context context) {
    return getPrefs(context).getString("name", "");
}

public static boolean isLoggedIn(Context context) {
    return getPrefs(context).getBoolean("logged_in", false);
}

public static String getSupabaseUserId(Context context) {
    return getPrefs(context).getString("user_id", "");
}
```

**Pattern for writing:**
```java
public static void setLoggedIn(Context context, boolean logged) {
    getPrefs(context).edit()
        .putBoolean("logged_in", logged)
        .apply();  // async, safe for main thread
}

public static void setName(Context context, String name) {
    getPrefs(context).edit()
        .putString("name", name)
        .apply();
}
```

### Login FlowActivity (LoginActivity.java)

**Location:** [app/src/main/java/com/example/safesphere/LoginActivity.java](app/src/main/java/com/example/safesphere/LoginActivity.java)

**Flow:**
```
1. User enters phone number
2. POST to /api/user/register (Next.js backend)
3. Receive user_id
4. Store in SharedPreferences via Prefs.setLoggedIn(true)
5. Start MainActivity
6. MainActivity checks Prefs.isLoggedIn() — YES → show dashboard
```

**Template pattern:**
```java
public class LoginActivity extends AppCompatActivity {
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Check: already logged in?
        if (Prefs.isLoggedIn(this)) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }
        
        // Show login form
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        binding.loginBtn.setOnClickListener(v -> performLogin());
    }
    
    private void performLogin() {
        String phone = binding.phoneInput.getText().toString();
        
        if (phone.isEmpty()) {
            binding.errorText.setText("Phone required");
            return;
        }
        
        // Network call (background thread)
        new Thread(() -> {
            try {
                // POST to /api/user/register
                HttpURL request = new HttpURL("YOUR_URL/api/user/register");
                String jsonBody = "{\"phone\":\"" + phone + "\"}";
                String response = request.post(jsonBody, 15000);  // 15s timeout
                
                JSONObject json = new JSONObject(response);
                String userId = json.getString("user_id");
                String userId = json.getString("user_id");
                
                // Save to SharedPreferences
                Prefs.setLoggedIn(this, true);
                Prefs.setSupabaseUserId(this, userId);
                
                // Navigate to MainActivity
                runOnUiThread(() -> {
                    startActivity(new Intent(this, MainActivity.class));
                    finish();
                });
                
            } catch (Exception e) {
                runOnUiThread(() -> {
                    binding.errorText.setText("Login failed: " + e.getMessage());
                });
            }
        }).start();
    }
}
```

**How to modify login:**
- Change endpoint? Update BASE_URL in NetworkConfig.java
- Add password validation? Coordinate with backend /api/user/register
- Add email? Update Prefs.java key + registration form

### Session Recovery (MainActivity.java)

**On every app start:**

**Location:** [app/src/main/java/com/example/safesphere/MainActivity.java](app/src/main/java/com/example/safesphere/MainActivity.java)

```java
public class MainActivity extends AppCompatActivity {
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 1. Check: logged in?
        if (!Prefs.isLoggedIn(this)) {
            // Session lost → redirect to login
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        
        // 2. Get user data from SharedPreferences (fast, no network)
        String userName = Prefs.getName(this);
        String keyword = Prefs.getKeyword(this);
        boolean isProtected = Prefs.isProtectionEnabled(this);
        
        // 3. Show dashboard
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // 4. Restore state
        binding.nameText.setText(userName);
        binding.protectionToggle.setChecked(isProtected);
        
        // Optional: refresh from server (async, non-blocking)
        refreshUserDataFromServer();
    }
    
    private void refreshUserDataFromServer() {
        // Async task to check revocation status, get latest user data
        new Thread(() -> {
            try {
                String userId = Prefs.getSupabaseUserId(MainActivity.this);
                String response = makeApiCall("/api/revocation/check?user_id=" + userId);
                
                JSONObject json = new JSONObject(response);
                if (json.getBoolean("is_revoked")) {
                    // User was revoked by admin
                    handleRevocation();
                    return;
                }
                
                // Update local pending revocation state
                Prefs.setPendingRevocation(MainActivity.this, json.optBoolean("is_revoked", false));
                
            } catch (Exception e) {
                // Network error: OK, continue with cached data
                Log.e("MainActivity", "Refresh failed: " + e.getMessage());
            }
        }).start();
    }
    
    private void handleRevocation() {
        // Admin revoked this user
        Prefs.setLoggedIn(MainActivity.this, false);
        Prefs.clear(MainActivity.this);  // Clear all user data
        
        runOnUiThread(() -> {
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
            finish();
        });
    }
}
```

### Database: Analytics with Session Tracking

**File:** [app/src/main/java/com/example/safesphere/analytics/AnalyticsEvent.java](app/src/main/java/com/example/safesphere/analytics/AnalyticsEvent.java)

```java
@Entity(tableName = "analytics_events")
public class AnalyticsEvent {
    
    @PrimaryKey
    public String eventId;          // UUID v4 per event
    
    public String userId;           // Prefs.getSupabaseUserId()
    public String sessionId;        // Unique per emergency trigger
    public String eventType;        // "login", "trigger_source", "call_attempt"
    
    public long clientTsMs;         // System.currentTimeMillis()
    public String payloadJson;      // Gson-serialized data
    
    public boolean synced = false;  // Has this been sent to server?
    public boolean sentToServer = false;
}
```

**Session tracking example:**
```java
// When emergency triggered
String sessionId = UUID.randomUUID().toString();
Prefs.setCurrentSessionId(context, sessionId);

// Log trigger event
AnalyticsEvent event = new AnalyticsEvent();
event.eventId = UUID.randomUUID().toString();
event.userId = Prefs.getSupabaseUserId(context);
event.sessionId = sessionId;            // Links all events in this emergency
event.eventType = "trigger_source";
event.clientTsMs = System.currentTimeMillis();
event.payloadJson = new Gson().toJson(Map.of("source", "keyword_detected"));

// Insert into local database
database.analyticsEventDao().insert(event);

// Sync worker will batch send to /api/analytics/ingest every 15min
```

### Web Tier Auth (Next.js + Supabase)

**Middleware protection:** [admin/src/middleware.ts](admin/src/middleware.ts)

```typescript
import { createClient } from '@/lib/supabase/server';

export async function middleware(request: NextRequest) {
  const url = request.nextUrl;
  
  // Allow public routes
  if (url.pathname === '/' || url.pathname === '/admin/login') {
    return NextResponse.next();
  }
  
  // Protect `/admin/*` routes
  if (url.pathname.startsWith('/admin')) {
    const supabase = await createClient();
    
    // Check: user authenticated?
    const { data: { user }, error } = await supabase.auth.getUser();
    
    if (!user) {
      // Not logged in → redirect to login
      return NextResponse.redirect(new URL('/admin/login', request.url));
    }
    
    // Check: is admin?
    const { data: admin } = await supabase
      .from('admin_accounts')
      .select('id, is_active')
      .eq('email', user.email)
      .single();
    
    if (!admin?.is_active) {
      // Not an active admin → logout and redirect
      await supabase.auth.signOut();
      return NextResponse.redirect(new URL('/admin/login', request.url));
    }
  }
  
  return NextResponse.next();
}

export const config = {
  matcher: ['/((?!api|_next|.*\\..*).*)'],  // Apply to all except api routes
};
```

## Testing Checklist

### Unit Tests
- [ ] Prefs getter/setter works (SharedPreferences persistence)
- [ ] Login saves to SharedPreferences
- [ ] isLoggedIn() returns true after login
- [ ] isLoggedIn() returns false after clear()
- [ ] All critical keys present after login

### Integration Tests
- [ ] Full login flow: form → API call → SharedPreferences → MainActivity
- [ ] App process killed mid-login: data still recoverable
- [ ] Session survives:
  - [ ] Activity rotate (onCreate called with savedInstanceState)
  - [ ] App backgrounded/foregrounded
  - [ ] Device reboot
- [ ] Revocation detection stops app on next refresh

### Edge Cases (CRITICAL)
- [ ] Network timeout during login: graceful error message, don't crash
- [ ] Empty phone number: validation error, don't call API
- [ ] User already registered: API returns existing user_id, no duplicate
- [ ] SharedPreferences corrupted: clear and restart login
- [ ] No network: app still runs with cached data, offline mode
- [ ] Multiple rapid logins: deduplicate, prevent double-registration

### Real Device Tests
- [ ] Login, force-stop app, restart → session recovers
- [ ] Login, phone reboots → session recovers
- [ ] Check revocation API working (test with admin remove-user route)
- [ ] Check analytics events have correct sessionId

## Debugging Session Issues

**If user gets logged out unexpectedly:**

1. Check Prefs values:
   ```java
   Log.d("Session", "logged_in=" + Prefs.isLoggedIn(context));
    Log.d("Session", "user_id=" + Prefs.getSupabaseUserId(context));
    Log.d("Session", "pending_revocation=" + Prefs.isPendingRevocation(context));
   ```

2. Check SharedPreferences file:
   ```bash
   adb shell getprop | grep safesphere_prefs
   adb shell run-as com.example.safesphere cat data/data/com.example.safesphere/shared_prefs/safesphere_prefs.xml
   ```

3. Check revocation check worker:
   ```bash
   adb logcat | grep -i "revocation\|revoke"
   ```

4. Check analytics:
   - Look for `revocation_detected` event in `/admin/analytics`

## Changelog
- 2026-04-09 - Supabase project migrated to new URL: qzezwpzmxkwxgrtxucaw.supabase.co
- Admin account created on new project for dashboard login
- All auth flows verified working on new project
- 2026-04-08 - Updated for current SafeSphere structure and auth behavior
- Replaced stale `safesphere-admin/` path with `admin/`
- Replaced legacy `getUserId/setUserId` examples with `getSupabaseUserId/setSupabaseUserId`
- Replaced outdated revocation version examples with `pending_revocation` flow
- 2026-03-30 - Initial session persistence documentation
- Documented SharedPreferences keys and Prefs.java patterns
- Documented LoginActivity and session recovery flow
- Documented revocation detection and user removal
- Documented Web auth middleware approach
- Added testing checklist and debugging steps
