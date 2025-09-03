# Signal Accessibility Mode - Integration Guide

## Overview

This guide provides technical instructions for integrating Signal Accessibility Mode into the Signal Android codebase. The implementation is designed to be minimally invasive while providing comprehensive accessibility features.

## Architecture

### Core Components

#### `AccessibilityModeActivity.kt`
Main activity for accessibility mode interface.
- **Location**: `app/src/main/java/org/thoughtcrime/securesms/accessibility/`
- **Purpose**: Hosts the accessibility-optimized conversation view
- **Integration**: Launched via `AccessibilityModeRouter`

#### `AccessibilityModeFragment.kt`
Fragment containing the conversation UI.
- **Location**: `app/src/main/java/org/thoughtcrime/securesms/accessibility/`
- **Purpose**: Displays messages using Signal's `ConversationAdapterV2`
- **Features**: Simplified controls, large buttons, accessibility labels

#### `AccessibilityModeRouter.kt`
Central routing logic for mode switching.
- **Location**: `app/src/main/java/org/thoughtcrime/securesms/accessibility/`
- **Purpose**: Manages transitions between normal and accessibility modes
- **Integration**: Called from `MainActivity.onStart()`

#### `IntentFactory.kt`
Intent creation utilities.
- **Location**: `app/src/main/java/org/thoughtcrime/securesms/accessibility/`
- **Purpose**: Creates properly configured Intents for mode transitions
- **Features**: `FLAG_ACTIVITY_CLEAR_TASK` for clean activity stack

#### `AccessibilityModeExitToSettingsGestureDetector.kt`
Gesture detection for exiting accessibility mode.
- **Location**: `app/src/main/java/org/thoughtcrime/securesms/accessibility/`
- **Purpose**: Detects configured exit gestures
- **Features**: Production gesture + debug gesture options

### Settings Integration

#### `AccessibilityModeSettingsFragment.kt`
Main settings screen for accessibility mode.
- **Location**: `app/src/main/java/org/thoughtcrime/securesms/components/settings/app/accessibility/`
- **Purpose**: Provides configuration UI for accessibility features
- **Features**: Enable/disable, gesture selection, conversation picker

#### `AccessibilityModeValues.kt`
Persistent storage for accessibility settings.
- **Location**: `app/src/main/java/org/thoughtcrime/securesms/keyvalue/`
- **Purpose**: Stores user preferences and configuration
- **Integration**: Uses Signal's `SignalStore` system

## Integration Points

### Application Initialization
```kotlin
// In ApplicationContext.java
.addNonBlocking(() -> AccessibilityModeRouter.store = new SignalAccessibilityModeStore())
```

### Activity Lifecycle Integration
```kotlin
// In MainActivity.kt
override fun onStart() {
    super.onStart()
    AccessibilityModeRouter.routeIfNeeded(this)
}
```

### Settings Registration
Accessibility mode settings are integrated into Signal's existing settings structure through the navigation system.

## File Structure

```
app/src/main/java/org/thoughtcrime/securesms/
├── accessibility/
│   ├── AccessibilityModeActivity.kt          # Main activity
│   ├── AccessibilityModeFragment.kt          # Conversation fragment
│   ├── AccessibilityModeRouter.kt            # Routing logic
│   ├── IntentFactory.kt                      # Intent utilities
│   ├── AccessibilityItemClickListener.kt     # Simplified interactions
│   ├── AccessibilityModeExitToSettingsGestureDetector.kt  # Gesture detection
│   └── AccessibilityModeStore.kt             # State management
├── components/settings/app/accessibility/
│   ├── AccessibilityModeSettingsFragment.kt  # Settings UI
│   ├── AccessibilityModeSettingsScreen.kt    # Compose UI
│   ├── AccessibilityModeSettingsViewModel.kt # Settings logic
│   ├── ChatSelectionFragment.kt              # Conversation picker
│   └── ChatSelectionScreen.kt                # Picker UI
└── keyvalue/
    └── AccessibilityModeValues.kt            # Persistent storage
```

## Dependencies

### Signal Components Used
- `ConversationAdapterV2` - Message display and interaction
- `ConversationViewModel` - Data management for conversations
- `SignalStore` - Persistent storage system
- `ConversationLayoutManager` - Message layout management

### Android Framework
- `AppCompatActivity` - Base activity class
- `Fragment` - UI component framework
- `RecyclerView` - List display component
- `MotionEvent` - Touch gesture handling

## Configuration

### Build Integration
No additional build dependencies required. All functionality uses existing Signal and Android framework components.

### Manifest Declarations
```xml
<!-- Accessibility Mode Activity -->
<activity
    android:name=".accessibility.AccessibilityModeActivity"
    android:exported="false"
    android:theme="@style/Signal.DayNight" />

<!-- Navigation integration -->
<navigation
    android:id="@+id/accessibility_mode_settings"
    app:startDestination="@id/accessibilityModeSettingsFragment" />
```

## Testing Strategy

### Unit Tests
- Core logic testing for gesture detection
- Router logic validation
- Settings persistence verification

### Integration Tests
- End-to-end accessibility mode flow
- Signal component compatibility
- Gesture detection accuracy

### Accessibility Tests
- TalkBack compatibility verification
- Screen reader announcement testing
- Accessibility service integration

## Deployment Checklist

### Pre-deployment
- [ ] All tests pass
- [ ] Accessibility audit completed
- [ ] Manual testing on target devices
- [ ] Documentation updated

### Integration Verification
- [ ] Settings navigation works
- [ ] Mode switching functions correctly
- [ ] Gestures work as expected
- [ ] No regressions in core Signal functionality

### Post-deployment
- [ ] Monitor for accessibility-related issues
- [ ] User feedback collection
- [ ] Performance impact assessment

## Maintenance Guidelines

### Code Style
- Follow Signal's Kotlin coding standards
- Use descriptive variable and method names
- Include comprehensive documentation comments
- Maintain consistent error handling patterns

### Version Compatibility
- Test on minimum supported Android version (API 26)
- Verify compatibility with latest Android version
- Monitor for framework API changes

### Feature Updates
- Keep accessibility features aligned with Android standards
- Regular review against WCAG guidelines
- User feedback integration into updates

---

*This integration maintains Signal's architectural patterns while providing essential accessibility functionality for users who need simplified interfaces.*
