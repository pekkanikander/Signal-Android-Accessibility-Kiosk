package org.thoughtcrime.securesms.keyvalue

import org.thoughtcrime.securesms.keyvalue.SignalStoreValues
import org.thoughtcrime.securesms.accessibility.AccessibilityModeExitGestureType

/**
 * Stores accessibility mode settings for the Signal app.
 * Provides a simplified interface for users with cognitive impairments.
 */
class AccessibilityModeValues(store: KeyValueStore) : SignalStoreValues(store) {

  companion object {
    // Setting keys
    const val ACCESSIBILITY_MODE_ENABLED = "accessibility_mode.enabled"
    const val ACCESSIBILITY_THREAD_ID = "accessibility_thread.id"
    const val EXIT_GESTURE_TYPE = "accessibility_mode.exit_gesture_type"
    const val EXIT_GESTURE_REQUIRE_PIN = "accessibility_mode.exit_gesture_require_pin"
    const val EXIT_GESTURE_PIN_HASH = "accessibility_mode.exit_gesture_pin_hash"
    const val EXIT_GESTURE_PIN_SALT = "accessibility_mode.exit_gesture_pin_salt"

    // Advanced configuration keys (Phase 2.5)
    const val EXIT_GESTURE_HOLD_MS = "accessibility_mode.exit_gesture_hold_ms"
    const val EXIT_GESTURE_CONFIRM_MS = "accessibility_mode.exit_gesture_confirm_ms"
    const val EXIT_GESTURE_TIMEOUT_MS = "accessibility_mode.exit_gesture_timeout_ms"
    const val EXIT_GESTURE_CORNER_DP = "accessibility_mode.exit_gesture_corner_dp"
    const val EXIT_GESTURE_DRIFT_DP = "accessibility_mode.exit_gesture_drift_dp"
    const val EXIT_GESTURE_POINTER_TIMEOUT_MS = "accessibility_mode.exit_gesture_pointer_timeout_ms"
  }

  // Boolean values using booleanValue delegate
  var isAccessibilityModeEnabled: Boolean by booleanValue(ACCESSIBILITY_MODE_ENABLED, false)

  // Long value for thread ID
  var accessibilityThreadId: Long by longValue(ACCESSIBILITY_THREAD_ID, -1L)

  // Exit gesture configuration
  var exitGestureType: Int by integerValue(EXIT_GESTURE_TYPE, AccessibilityModeExitGestureType.SINGLE_FINGER_EDGE_DRAG_HOLD.value)
  var exitGestureRequirePin: Boolean by booleanValue(EXIT_GESTURE_REQUIRE_PIN, false)
  var exitGesturePinHash: String by stringValue(EXIT_GESTURE_PIN_HASH, "")
  var exitGesturePinSalt: String by stringValue(EXIT_GESTURE_PIN_SALT, "")

  // Advanced configuration (Phase 2.5)
  var exitGestureHoldMs: Int by integerValue(EXIT_GESTURE_HOLD_MS, 2500) // Default A=2500ms, B=1800ms
  var exitGestureConfirmMs: Int by integerValue(EXIT_GESTURE_CONFIRM_MS, 1500) // Default 1500ms
  var exitGestureTimeoutMs: Int by integerValue(EXIT_GESTURE_TIMEOUT_MS, 10000) // Default 10s
  var exitGestureCornerDp: Int by integerValue(EXIT_GESTURE_CORNER_DP, 72) // Default 72dp
  var exitGestureDriftDp: Int by integerValue(EXIT_GESTURE_DRIFT_DP, 24) // Default 24dp
  var exitGesturePointerTimeoutMs: Int by integerValue(EXIT_GESTURE_POINTER_TIMEOUT_MS, 5000) // Default 5000ms for emulator testing

  public override fun onFirstEverAppLaunch() = Unit

  public override fun getKeysToIncludeInBackup(): List<String> {
    return listOf(
      ACCESSIBILITY_MODE_ENABLED,
      ACCESSIBILITY_THREAD_ID,
      EXIT_GESTURE_TYPE,
      EXIT_GESTURE_REQUIRE_PIN,
      EXIT_GESTURE_PIN_HASH,
      EXIT_GESTURE_PIN_SALT,
      EXIT_GESTURE_HOLD_MS,
      EXIT_GESTURE_CONFIRM_MS,
      EXIT_GESTURE_TIMEOUT_MS,
      EXIT_GESTURE_CORNER_DP,
      EXIT_GESTURE_DRIFT_DP,
      EXIT_GESTURE_POINTER_TIMEOUT_MS
    )
  }
}
