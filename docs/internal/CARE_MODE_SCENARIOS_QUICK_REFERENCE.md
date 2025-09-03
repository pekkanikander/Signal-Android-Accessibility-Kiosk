# Care Mode Intent Stack Quick Reference

## The Four Core Scenarios You Identified

### Scenario 1: Fresh Launch → Enable Care Mode
**Flow**: Signal Launch (Care Mode disabled) → Settings → Enable Care Mode → Return

**Current Behavior**:
- User sees normal Signal
- Goes to Settings, enables Care Mode
- Returns to Care Mode interface
- **Issue**: Brief flash of MainActivity before Care Mode

**Expected Behavior**: Smooth transition to Care Mode

### Scenario 2: Fresh Launch → Disable Care Mode
**Flow**: Signal Launch (Care Mode enabled) → Care Mode → Settings → Disable Care Mode → Return

**Current Behavior**:
- User sees Care Mode interface
- Goes to Settings, disables Care Mode
- Returns to normal Signal interface
- **Status**: Should work correctly

**Expected Behavior**: Return to normal Signal

### Scenario 3: Care Mode → Settings → Return (No Changes)
**Flow**: Care Mode Active → Settings → Return (Care Mode still enabled)

**Current Behavior**:
- User in Care Mode
- Goes to Settings, makes no changes
- Returns to Care Mode
- **Issue**: Unnecessary MainActivity → AccessibilityModeActivity transition

**Expected Behavior**: Stay in Care Mode without transition

### Scenario 4: Normal Signal → Settings → Enable Care Mode
**Flow**: Normal Signal → Settings → Enable Care Mode → Return

**Current Behavior**:
- User in normal Signal
- Goes to Settings, enables Care Mode
- Returns to Care Mode interface
- **Status**: Should work correctly

**Expected Behavior**: Transition to Care Mode

## Current Implementation Issues

### Issue 1: State Management
- `careModeLaunched` flag is per-activity instance
- No global state tracking
- State resets on activity recreation

### Issue 2: Unnecessary Transitions
- MainActivity.onResume() triggers Care Mode launch even when not needed
- No detection of state changes vs. no changes

### Issue 3: Intent Stack Confusion
- Multiple activities in stack in some scenarios
- Inconsistent back button behavior

## Immediate Fixes Needed

### Fix 1: State Change Detection
```kotlin
// Track previous state to detect changes
private var previousCareModeState = false

override fun onResume() {
  super.onResume()

  val currentCareModeState = SignalStore.accessibilityMode.isAccessibilityModeEnabled

  // Only launch if state changed from disabled to enabled
  if (currentCareModeState && !previousCareModeState && !careModeLaunched) {
    careModeLaunched = true
    launchCareMode()
  }

  previousCareModeState = currentCareModeState
}
```

### Fix 2: Global State Management
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

### Fix 3: Settings Result Handling
```kotlin
// Handle Settings return properly
override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
  if (requestCode == SETTINGS_REQUEST_CODE) {
    // Handle Care Mode state changes appropriately
    handleCareModeStateChange()
  }
}
```

## Testing Priority

1. **Test Scenario 3** - Most problematic (unnecessary transitions)
2. **Test Scenario 1** - Flash issue
3. **Test Scenario 2** - Should work, verify
4. **Test Scenario 4** - Should work, verify

## Next Implementation Steps

1. **Implement state change detection** (Fix 1)
2. **Add global state management** (Fix 2)
3. **Add Settings result handling** (Fix 3)
4. **Test all four scenarios**
5. **Refine based on results**
