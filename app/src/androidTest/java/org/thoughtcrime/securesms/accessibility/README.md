# Accessibility Instrumentation Tests

This directory contains small, device-level smoke tests for Accessibility Mode.

## Tests and intent
- `AccessibilityLaunchTest`
  - Verifies the app can launch normally and that Accessibility Mode activity
    renders its conversation list when the mode is enabled for a valid thread.
- `AccessibilityDatabaseIntegrationTest`
  - Exercises database access patterns used by Accessibility Mode, ensuring
    threads and messages can be created and queried with the mode enabled.
- `AccessibilityGestureDetectorInstrTest`
  - Validates exit-gesture detection logic using synthesized `MotionEvent`
    sequences (triple tap and chord slide-up).

## Caveats
- These are instrumentation tests and require a connected device/emulator.
- The database integration test can be timing-sensitive on slower devices.
  It includes a small retry window to mitigate transient visibility issues.
