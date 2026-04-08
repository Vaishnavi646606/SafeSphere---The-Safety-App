# QUICK REF — Android New Activity
# Read only this block unless implementing changes

When to use this skill:
    - Adding a new screen/activity to Android app
    - Adding new UI flow

Key rules:
    Java only (never Kotlin)
    Register in AndroidManifest.xml
    Use bg_form_primary_button drawable for buttons
    Button loading pattern: setEnabled + setText + setBackgroundTintList
    Background tasks: always use new Thread() with try-catch

Last change: 2026-04-08

# Skill: Create Android Activity

## Description
Create a new Activity for SafeSphere Android app following exact project patterns and conventions.

## When to Use
- Adding new user-facing screens
- Implementing new workflows in Activity-based architecture
- Creating screens that integrate with SafeSphereService or emergency flows

## Prerequisites
- SafeSphere Android project structure intact
- Understanding of Android lifecycle (onCreate, onStart, onResume, onPause, onStop, onDestroy)
- Gradle build system access

## Steps

### Step 1: Plan Activity Purpose
Identify:
- [ ] What user interaction does this Activity enable?
- [ ] Does it need to start SafeSphereService?
- [ ] Does it need permissions? (check AndroidManifest.xml)
- [ ] Does it read/write to SharedPreferences via Prefs.java?
- [ ] Is it part of emergency flow or regular app flow?

### Step 2: Create Activity Class File
**Location:** `app/src/main/java/com/example/safesphere/YourNewActivity.java`

**Template Pattern (from existing LoginActivity, MainActivity, etc.):**
```java
package com.example.safesphere;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewbinding.ViewBinding;

public class YourNewActivity extends AppCompatActivity {

    private YourNewActivityBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Use ViewBinding (project standard)
        binding = YourNewActivityBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // TODO: Initialize views, listeners, data
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Optional: Register listeners, start SafeSphereService if needed
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Save state to SharedPreferences if needed
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Cleanup resources, unregister listeners
    }
}
```

**Key conventions found in codebase:**
- ✅ All Activities extend AppCompatActivity
- ✅ All use ViewBinding for type-safe view references
- ✅ All define lifecycle methods (onCreate minimum)
- ✅ All use `getLayoutInflater()` pattern for binding
- ✅ Package: `com.example.safesphere`

### Step 3: Create Layout XML File
**Location:** `app/src/main/res/layout/activity_your_new.xml`

**Pattern (from existing activity layouts):**
```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <!-- Your views here -->

</androidx.constraintlayout.widget.ConstraintLayout>
```

**Conventions:**
- ✅ Use ConstraintLayout (found in all project layouts)
- ✅ Follow Material Design guidelines (com.google.android.material:material:1.12.0)
- ✅ Use dp units for dimensions
- ✅ Use colors from `res/values/colors.xml`
- ✅ Use strings from `res/values/strings.xml`

### Step 4: Register in AndroidManifest.xml
**File:** `app/src/main/AndroidManifest.xml`

Add inside `<application>` tag:
```xml
<activity
    android:name=".YourNewActivity"
    android:exported="false"
    android:icon="@drawable/ic_launcher_foreground">
    <!-- Optional: Add intent-filter if this is entry point or deep link target -->
</activity>
```

**Patterns from existing entries:**
- ✅ If launcher activity: add intent-filter with action.MAIN + category.LAUNCHER
- ✅ android:exported="false" for internal activities
- ✅ android:exported="true" only if receiving intents from system/other apps
- ✅ Check if new permissions needed (22 permissions already declared, review in manifest)

### Step 5: Add Required Permissions (if applicable)
Review your Activity needs:
```xml
<!-- If reading phone state -->
<uses-permission android:name="android.permission.READ_PHONE_STATE" />

<!-- If making calls -->
<uses-permission android:name="android.permission.CALL_PHONE" />

<!-- If sending SMS -->
<uses-permission android:name="android.permission.SEND_SMS" />

<!-- If accessing location -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

<!-- See AndroidManifest.xml for full permission list -->
```

### Step 6: Handle Permissions at Runtime
**Pattern (from SafeSphereService.java):**
```java
// For Android 6.0+ (API 23+)
if (ContextCompat.checkSelfPermission(this, Manifest.permission.YOUR_PERMISSION)
        != PackageManager.PERMISSION_GRANTED) {
    ActivityCompat.requestPermissions(this,
        new String[]{Manifest.permission.YOUR_PERMISSION}, REQUEST_CODE);
}

// Handle result in onRequestPermissionsResult callback
@Override
public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == REQUEST_CODE && grantResults.length > 0
            && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        // Permission granted
    }
}
```

### Step 7: Integrate with SharedPreferences (if needed)
**Pattern (from Prefs.java):**
```java
// Read from SharedPreferences
String userName = Prefs.getName(this);
boolean isProtected = Prefs.isProtectionEnabled(this);
String keyword = Prefs.getKeyword(this);
String[] emergencyContacts = {
    Prefs.getEmergency1(this),
    Prefs.getEmergency2(this),
    Prefs.getEmergency3(this)
};

// Write to SharedPreferences
Prefs.setName(this, "John Doe");
Prefs.setProtectionEnabled(this, true);
```

**File:** `app/src/main/java/com/example/safesphere/Prefs.java` — all getters/setters use SharedPreferences internally

### Step 8: Integrate with SafeSphereService (if needed)
**Pattern from existing Activities:**
```java
// To start SafeSphereService
Intent serviceIntent = new Intent(this, SafeSphereService.class);
startService(serviceIntent);

// To check if service running
boolean isRunning = ServiceUtils.isServiceRunning(this, SafeSphereService.class);

// To send commands (if using LocalBroadcast)
Intent broadcastIntent = new Intent("com.example.safesphere.ACTION_COMMAND");
broadcastIntent.putExtra("command", "START_EMERGENCY");
LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent);
```

### Step 9: Add Battery/Performance Optimizations
**Patterns from project:**
- ✅ Don't block UI thread (use Thread or AsyncTask for heavy work)
- ✅ Unregister broadcast receivers in onDestroy
- ✅ Use WorkManager for background tasks (not Thread.sleep)
- ✅ Check battery level before intensive operations (see EmergencyActionOptimizer.java)

### Step 10: Write Tests
**Location:** `app/src/test/` and `app/src/androidTest/`

**Unit Test Pattern (JUnit 4.13.2):**
```java
@RunWith(AndroidJUnit4::class)
public class YourNewActivityTest {
    
    @get:Rule
    val activityRule = ActivityTestRule(YourNewActivity::class.java)
    
    @Test
    public void testActivityLaunches() {
        // Test onCreate
    }
}
```

## Verification
- [ ] Activity class created at `app/src/main/java/com/example/safesphere/YourNewActivity.java`
- [ ] Layout XML created at `app/src/main/res/layout/activity_your_new.xml`
- [ ] Registered in AndroidManifest.xml
- [ ] All imports resolve (check Android SDK + project classes)
- [ ] Extends AppCompatActivity (not Activity)
- [ ] Uses ViewBinding pattern
- [ ] Lifecycle methods implemented (at minimum onCreate)
- [ ] Permissions declared if needed
- [ ] Runtime permission checks added
- [ ] Gradle compiles without errors: `./gradlew build`
- [ ] Tests written covering basic flows

## Anti-Hallucination Checks
- [ ] `com.example.safesphere` package verified (APPLICATION_ID in app/build.gradle)
- [ ] ViewBinding class generated (YourNewActivityBinding exists)
- [ ] All referenced classes exist: AppCompatActivity, Prefs, SafeSphereService
- [ ] All imports are from android.*, androidx.*, or com.example.safesphere.*
- [ ] Layout resources referenced (binding.getRoot()) actually exist
- [ ] Permissions in AndroidManifest match Android SDK permissions
- [ ] No hallucinated services or permissions

## Examples

### Example 1: Simple Display Activity (like ProfileActivity pattern)
```java
// app/src/main/java/com/example/safesphere/EmergencyContactsActivity.java
public class EmergencyContactsActivity extends AppCompatActivity {
    private ActivityEmergencyContactsBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEmergencyContactsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // Load emergency contacts from Prefs
        binding.contact1.setText(Prefs.getEmergency1(this));
        binding.contact2.setText(Prefs.getEmergency2(this));
        binding.contact3.setText(Prefs.getEmergency3(this));
        
        // Save button listener
        binding.saveBtn.setOnClickListener(v -> {
            Prefs.setEmergency1(this, binding.contact1.getText().toString());
            Prefs.setEmergency2(this, binding.contact2.getText().toString());
            Prefs.setEmergency3(this, binding.contact3.getText().toString());
            finish();
        });
    }
}
```

### Example 2: Activity Triggering Service (core emergency pattern)
```java
// Pattern from MainActivity.java — toggle protection
public class ProtectionToggleActivity extends AppCompatActivity {
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProtectionBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // Mirror pattern from MainActivity
        boolean isEnabled = Prefs.isProtectionEnabled(this);
        binding.toggle.setChecked(isEnabled);
        
        binding.toggle.setOnCheckedChangeListener((btn, isChecked) -> {
            Prefs.setProtectionEnabled(this, isChecked);
            
            if (isChecked) {
                Intent service = new Intent(this, SafeSphereService.class);
                startService(service);
            } else {
                Intent service = new Intent(this, SafeSphereService.class);
                stopService(service);
            }
        });
    }
}
```

### Example 3: Activity with Permissions (like location-based)
```java
// Pattern from SafeSphereService.java
public class LocationActivity extends AppCompatActivity {
    private static final int LOCATION_REQUEST = 100;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLocationBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        binding.shareLocationBtn.setOnClickListener(v -> requestLocationPermission());
    }
    
    private void requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, 
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                LOCATION_REQUEST);
        } else {
            shareLocation();
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, 
            String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_REQUEST && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            shareLocation();
        }
    }
    
    private void shareLocation() {
        // Get location and send to server
    }
}
```

## Changelog
- 2026-03-30 - Initial creation
- Documented Activity pattern from LoginActivity, MainActivity, RegisterActivity, ProfileActivity, FakeCallActivity
- Included ViewBinding pattern, lifecycle methods, permission handling
- Added examples for display, service integration, and permission-based activities
