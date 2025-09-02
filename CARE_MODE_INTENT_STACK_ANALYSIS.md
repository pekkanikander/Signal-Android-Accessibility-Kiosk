# Care Mode Intent Stack Analysis

## Overview

This document maps all possible user flows and their corresponding Intent stacks for Care Mode implementation. We need to ensure consistent behavior across all scenarios.

## Core Scenarios

### Scenario 1: Fresh Launch with Care Mode Disabled → Enable Care Mode
**Flow**: Signal Launch → Settings → Enable Care Mode → Return

**Current Intent Stack** (Option C):
```
MainActivity (onResume) → Settings → MainActivity (onResume) → AccessibilityModeActivity
```

**Expected Behavior**:
- User sees normal Signal
- Goes to Settings, enables Care Mode
- Returns to Care Mode interface
- **Issue**: MainActivity might re-launch Care Mode unnecessarily

### Scenario 2: Fresh Launch with Care Mode Enabled → Disable Care Mode
**Flow**: Signal Launch → Care Mode → Settings → Disable Care Mode → Return

**Current Intent Stack** (Option C):
```
MainActivity (onResume) → AccessibilityModeActivity → Settings → MainActivity (onResume)
```

**Expected Behavior**:
- User sees Care Mode interface
- Goes to Settings, disables Care Mode
- Returns to normal Signal interface
- **Issue**: Should work correctly with Option C

### Scenario 3: Care Mode Active → Settings → Return to Care Mode
**Flow**: Care Mode Active → Settings → Return (Care Mode still enabled)

**Current Intent Stack** (Option C):
```
AccessibilityModeActivity → Settings → MainActivity (onResume) → AccessibilityModeActivity
```

**Expected Behavior**:
- User in Care Mode
- Goes to Settings, makes no changes
- Returns to Care Mode
- **Issue**: Unnecessary MainActivity → AccessibilityModeActivity transition

### Scenario 4: Normal Signal → Settings → Enable Care Mode → Return
**Flow**: Normal Signal → Settings → Enable Care Mode → Return

**Current Intent Stack** (Option C):
```
MainActivity → Settings → MainActivity (onResume) → AccessibilityModeActivity
```

**Expected Behavior**:
- User in normal Signal
- Goes to Settings, enables Care Mode
- Returns to Care Mode interface
- **Issue**: Should work correctly

## Additional Scenarios

### Scenario 5: Back Button from Care Mode
**Flow**: Care Mode Active → Back Button

**Current Intent Stack** (Option C):
```
AccessibilityModeActivity → (Back) → Exit App
```

**Expected Behavior**:
- User exits Signal completely
- **Issue**: Should this return to normal Signal instead?

### Scenario 6: App Switcher from Care Mode
**Flow**: Care Mode Active → App Switcher → Return to Signal

**Current Intent Stack** (Option C):
```
AccessibilityModeActivity → (App Switcher) → AccessibilityModeActivity
```

**Expected Behavior**:
- User returns to Care Mode
- **Issue**: Should work correctly

### Scenario 7: Force Stop → Restart with Care Mode Enabled
**Flow**: Force Stop → Restart Signal (Care Mode enabled)

**Current Intent Stack** (Option C):
```
MainActivity (onResume) → AccessibilityModeActivity
```

**Expected Behavior**:
- Signal starts directly in Care Mode
- **Issue**: Should work correctly

### Scenario 8: Deep Link to Specific Conversation
**Flow**: Deep Link → Signal → Care Mode (if enabled)

**Current Intent Stack** (Option C):
```
MainActivity (onResume) → AccessibilityModeActivity
```

**Expected Behavior**:
- Deep link should be ignored if Care Mode is enabled
- **Issue**: Should work correctly

## Intent Stack Problems

### Problem 1: Unnecessary Transitions
In Scenarios 1 and 3, we have unnecessary MainActivity → AccessibilityModeActivity transitions.

### Problem 2: State Management
The `careModeLaunched` flag is per-activity instance, not global state.

### Problem 3: Settings Return Logic
No logic to handle different return scenarios from Settings.

## Proposed Solutions

### Solution A: Global State Management
```kotlin
object CareModeManager {
  private var careModeLaunched = false

  fun shouldLaunchCareMode(): Boolean {
    return SignalStore.accessibilityMode.isAccessibilityModeEnabled && !careModeLaunched
  }

  fun markCareModeLaunched() {
    careModeLaunched = true
  }

  fun reset() {
    careModeLaunched = false
  }
}
```

### Solution B: Settings Result Handling
```kotlin
// In MainActivity.onActivityResult
override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
  if (requestCode == SETTINGS_REQUEST_CODE) {
    // Handle Care Mode state changes
    if (SignalStore.accessibilityMode.isAccessibilityModeEnabled) {
      // Care Mode was enabled, launch it
      launchCareMode()
    } else {
      // Care Mode was disabled, stay in normal mode
      CareModeManager.reset()
    }
  }
}
```

### Solution C: Intent Flags Optimization
```kotlin
// Use different flags based on scenario
val intent = Intent(this, AccessibilityModeActivity::class.java).apply {
  when (scenario) {
    FRESH_LAUNCH -> flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
    FROM_SETTINGS -> flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
    else -> flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
  }
}
```

## Testing Strategy

### Manual Testing Checklist
- [ ] Scenario 1: Fresh launch, enable Care Mode
- [ ] Scenario 2: Fresh launch, disable Care Mode
- [ ] Scenario 3: Care Mode → Settings → Return
- [ ] Scenario 4: Normal Signal → Settings → Enable Care Mode
- [ ] Scenario 5: Back button from Care Mode
- [ ] Scenario 6: App switcher behavior
- [ ] Scenario 7: Force stop and restart
- [ ] Scenario 8: Deep link handling

### Automated Testing Approach
```kotlin
@RunWith(RobolectricTestRunner::class)
class CareModeIntentStackTest {

  @Test
  fun `scenario 1 - fresh launch enable care mode`() {
    // Test fresh launch with Care Mode disabled
    // Navigate to Settings, enable Care Mode
    // Verify correct Intent stack
  }

  @Test
  fun `scenario 2 - fresh launch disable care mode`() {
    // Test fresh launch with Care Mode enabled
    // Navigate to Settings, disable Care Mode
    // Verify correct Intent stack
  }

  // ... more test scenarios
}
```

## Next Steps

1. **Implement global state management** (CareModeManager)
2. **Add Settings result handling** in MainActivity
3. **Optimize Intent flags** for different scenarios
4. **Create automated tests** for all scenarios
5. **Manual testing** of all flows
6. **Refine based on test results**

## Questions to Resolve

1. **Back button behavior**: Should Care Mode back button exit app or return to normal Signal?
2. **Deep link handling**: Should deep links be ignored in Care Mode?
3. **State persistence**: How should Care Mode state persist across app restarts?
4. **Error handling**: What happens if Care Mode conversation is deleted?
5. **Performance**: Are the Intent transitions smooth enough?
