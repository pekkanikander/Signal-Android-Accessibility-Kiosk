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

#### `AccessibilityModeExitGestureDetector.kt`
Gesture detection for exiting accessibility mode.
- **Location**: `app/src/main/java/org/thoughtcrime/securesms/accessibility/`
- **Purpose**: Detects configured exit gestures (policy + recognition state machines)
- **Features**: Production gesture (Chord slide up) + debug gesture (Triple tap)

#### `AccessibilityModeExitGestureType.kt`
Gesture type enumeration for exit gestures.
- **Location**: `app/src/main/java/org/thoughtcrime/securesms/accessibility/`
- **Purpose**: Defines supported production and debug gesture types

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
│   └── AccessibilityModeStore.kt             # State management
│   ├── IntentFactory.kt                      # Intent utilities
│   ├── AccessibilityModeItemClickListener.kt # Simplified interactions
│   ├── AccessibilityModeExitGestureType.kt                # Exit gesture type enum
│   ├── AccessibilityModeExitGestureDetector.kt            # Exit gesture detection
│   ├── AccessibilityModeExitConfirmationDialog.kt         # Exit confirmation dialog
├── components/settings/app/accessibility/
│   ├── AccessibilityModeSettingsFragment.kt  # Settings UI
│   ├── AccessibilityModeSettingsViewModel.kt # Settings logic
│   ├── AccessibilityModeSettingsState.kt     # Settings state
│   ├── ChatSelectionFragment.kt              # Conversation picker
└── keyvalue/
    └── AccessibilityModeValues.kt            # Persistent storage
```

Additional resources:

```
app/src/main/res/layout/
├── activity_accessibility_mode.xml           # Activity layout
├── fragment_accessibility_mode.xml           # Fragment layout
└── dialog_accessibility_exit_confirmation.xml # Exit confirmation dialog layout
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
    android:theme="@style/Theme.Signal.DayNight.NoActionBar" />

<!-- Navigation integration -->
<!-- Accessibility settings are registered inside the main app settings graph:
     `app/src/main/res/navigation/app_settings_with_change_number.xml`
     Look for the fragment with id `@+id/accessibilityModeSettingsFragment`. -->
<!-- Example (host graph contains the fragment): -->
<!--
  <fragment
      android:id="@+id/accessibilityModeSettingsFragment"
      android:name="org.thoughtcrime.securesms.components.settings.app.accessibility.AccessibilityModeSettingsFragment"
      android:label="@string/preferences__accessibility_mode" />
-->
```

## Testing Strategy

### Policy
- Prefer pure-JVM unit tests (fakes) for algorithmic logic, timing windows, and state machines. These tests are fast, deterministic, and do not rely on Android framework or native libraries.
- Use Robolectric only for narrowly scoped Android integration tests that require `Context`, `Resources`, `View`, or `Handler` behavior.
- Do not modify production code to satisfy tests (for example, avoid changing native library loaders). Use test-only shadows, rules, and mocks instead.

### Unit Tests
- Core logic testing for gesture detection (prefer pure-JVM fakes)
- Router logic validation (pure-JVM where possible)
- Settings persistence verification (use `mockkObject` for singletons)

### Robolectric / Integration Tests
- Keep Robolectric integration tests minimal and focused on View / Handler / Resource interactions.
- Recommended Robolectric checklist:
  - Use `ApplicationProvider.getApplicationContext()` for Context when possible.
  - Annotate with `@Config(manifest = Config.NONE)` unless the test needs a manifest.
  - Prefer `application = org.thoughtcrime.securesms.testing.TestApplication::class` in `@Config` for tests that need a test Application.
  - Prevent native/encrypted libraries from loading using test-only Shadows (e.g., `ShadowSqlCipherLibraryLoader`) rather than editing production loader code.
  - When tests post work to the main Looper, call `shadowOf(Looper.getMainLooper()).idle()` to execute queued runnables.
  - Inject Android services as needed with `Shadows.shadowOf(app as Application).setSystemService(...)`.
  - Stub `View.parent` with a relaxed `ViewParent` mock if production code calls `getParent()` in tests.

### Accessibility / End-to-end Tests
- One focused Robolectric integration test per feature should verify View/Handler interactions and system-service integration (e.g., `AccessibilityManager`, TalkBack announcements). Keep these small; use pure-JVM tests for the rest.

### Test Utilities and Rules
- Reuse repository canonical test helpers where appropriate:
  - `testutil/MockAppDependenciesRule` — for isolating `AppDependencies` and mocking global singletons.
  - `testutil/SignalDatabaseRule` — in-memory DB for database tests; avoid Robolectric for DB tests that rely on native/encrypted libs.
  - Use MockK's `mockkObject(...)`/`unmockkObject(...)` to override singletons like `SignalStore` in tests.

### Examples / References
- Pure-JVM fake pattern: `app/src/test/.../AccessibilityGestureRouterUnitTest.kt`
- Robolectric integration example: `app/src/test/.../AccessibilityRouterTest.kt`
- Upstream UI Robolectric example: `app/src/test/.../stories/StoryFirstTimeNavigationViewTest.kt`
- Test Application and shadows: `app/src/test/java/org/thoughtcrime/securesms/testing/TestApplication.kt`, `app/src/test/java/org/thoughtcrime/securesms/testing/ShadowSqlCipherLibraryLoader.kt`

## Deployment Checklist

### Pre-deployment
- [ ] All unit and integration tests pass (see Testing Strategy). Prefer pure-JVM verification for algorithms and a single focused Robolectric integration test for View/Handler interactions.
- [ ] Accessibility audit completed (WCAG checklist reviewed and issues documented).
- [ ] Manual testing on target devices (phones/tablets) covering TalkBack and common screen sizes.
- [ ] Documentation updated (`docs/IMPLEMENTATION-GUIDE.md`, `ACCESSIBILITY-MODE.md`, and relevant README sections).

### Integration Verification
- **Manifest & Theme**: Confirm `AccessibilityModeActivity` is declared in `app/src/main/AndroidManifest.xml` with the intended theme (`@style/Theme.Signal.DayNight.NoActionBar`).
- **Navigation**: Verify `AccessibilityModeSettingsFragment` is registered in `app/src/main/res/navigation/app_settings_with_change_number.xml` (or the current app settings graph).
- **Mode switching**: Verify `AccessibilityModeRouter.routeIfNeeded(...)` is invoked from `MainActivity.onStart()` and that intents contain the correct extras/flags.
- **Gestures**: Confirm a Robolectric integration test covers the touch-to-exit interaction and that timing/edge cases are covered by pure-JVM tests.
- **Singletons & DB**: Ensure `SignalStore` and other singletons are mocked in tests (e.g., via `mockkObject` or `MockAppDependenciesRule`). Do not modify native DB loader code for tests; use shadows if needed.
- **Baseline intact**: No regressions in core Signal functionality

### Post-deployment
- [ ] Monitor for accessibility-related issues via crash reports and user feedback channels
- [ ] Collect and triage user feedback specific to accessibility flows

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

## Troubleshooting

### Common Integration Issues
- **Router not initializing**: Check `ApplicationContext.java` registration
- **Settings not appearing**: Verify navigation XML configuration
- **Gestures not working**: Check gesture detector attachment in activity
- **Conversation not loading**: Verify `ConversationViewModel` integration
- **Test flakiness due to global singletons**: Ensure tests that touch `AppDependencies` or `SignalStore` use `MockAppDependenciesRule` or `mockkObject(...)` to isolate and mock global singletons; avoid relying on real `SignalStore` state in unit tests.

### Debug Tools
- Gesture detection logging available in debug builds
- Accessibility mode state visible in developer options
- Test gestures available through debug menu

---

## Support

For integration questions or issues:
1. Review this implementation guide
2. Check existing test coverage
3. Consult Signal's development documentation
4. File issues through standard Signal contribution process

---

*This integration maintains Signal's architectural patterns while providing essential accessibility functionality for users who need simplified interfaces.*
