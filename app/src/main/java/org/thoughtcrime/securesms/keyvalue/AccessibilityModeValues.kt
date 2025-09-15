package org.thoughtcrime.securesms.keyvalue

import org.thoughtcrime.securesms.keyvalue.SignalStoreValues
import org.thoughtcrime.securesms.accessibility.AccessibilityModeExitGestureType

/**
 * Stores accessibility mode settings for the Signal app.
 * Provides a simplified interface for users with cognitive impairments.
 */
class AccessibilityModeValues(store: KeyValueStore) : SignalStoreValues(store) {

  companion object {
    // Basic setting keys
    const val ACCESSIBILITY_MODE_ENABLED = "accessibility_mode.enabled"
    const val ACCESSIBILITY_RECIPIENT_ID = "accessibility_mode.recipient_id"

    // Advanced settings keys (many not implented in the UI yet)
    const val EXIT_GESTURE_TYPE = "accessibility_mode.exit_gesture_type"
    const val EXIT_HEADER_DEADZONE_DP = "accessibility_mode.exit_header_deadzone_dp"
    const val EXIT_HEADER_EXTRA_BOTTOM_DP = "accessibility_mode.exit_header_extra_bottom_dp"
    const val EXIT_HEADER_HEIGHT_DP = "accessibility_mode.exit_header_height_dp"
    const val EXIT_GESTURE_HOLD_MS = "accessibility_mode.exit_gesture_hold_ms"
    const val EXIT_GESTURE_CONFIRM_MS = "accessibility_mode.exit_gesture_confirm_ms"
    const val EXIT_GESTURE_TIMEOUT_MS = "accessibility_mode.exit_gesture_timeout_ms"
    const val EXIT_GESTURE_CORNER_DP = "accessibility_mode.exit_gesture_corner_dp"
    const val EXIT_GESTURE_DRIFT_DP = "accessibility_mode.exit_gesture_drift_dp"
    const val EXIT_GESTURE_POINTER_TIMEOUT_MS = "accessibility_mode.exit_gesture_pointer_timeout_ms"
    const val EXIT_TRIPLE_TAP_INTERVAL_MS = "accessibility_mode.exit_triple_tap_interval_ms"
    const val EXIT_TRIPLE_TAP_WINDOW_MS = "accessibility_mode.exit_triple_tap_window_ms"
    const val EXIT_CONFIRM_TIMEOUT_MS = "accessibility_mode.exit_confirm_timeout_ms"
    const val EXIT_HAPTIC_FEEDBACK_INTERVAL_MS = "accessibility_mode.exit_haptic_feedback_interval_ms"

    // Suppress message notifications while Accessibility Mode is active
    const val SUPPRESS_NOTIFICATIONS = "accessibility_mode.suppress_notifications"
  }

  // Boolean values using booleanValue delegate
  var isAccessibilityModeEnabled: Boolean by booleanValue(ACCESSIBILITY_MODE_ENABLED, false)

  // Long value for recipient ID
  var accessibilityRecipientId: Long by longValue(ACCESSIBILITY_RECIPIENT_ID, -1L)

  // Exit gesture configuration
  var exitGestureType: Int by integerValue(EXIT_GESTURE_TYPE, AccessibilityModeExitGestureType.TWO_FINGER_HEADER_HOLD.value)

  // Advanced configuration
  // Overall timeout, to catch device sleeps et (ms)
  var exitGestureTimeoutMs: Int by integerValue(EXIT_GESTURE_TIMEOUT_MS, 8000)
  // Timeout for the second finger to arrive (ms)
  var exitGesturePointerTimeoutMs: Int by integerValue(EXIT_GESTURE_POINTER_TIMEOUT_MS, 700)
  // Two finger hold duration (ms)
  var exitGestureHoldMs: Int by integerValue(EXIT_GESTURE_HOLD_MS, 1800)
  // Corner hit-rect size (dp)
  var exitGestureCornerDp: Int by integerValue(EXIT_GESTURE_CORNER_DP, 72)
  // Movement tolerance (dp)
  var exitGestureDriftDp: Int by integerValue(EXIT_GESTURE_DRIFT_DP, 24)
  // Reduce the aggressive deadzone so header remains easily tappable
  var exitHeaderDeadzoneDp: Int by integerValue(EXIT_HEADER_DEADZONE_DP, 24)
  // Make the tappable area noticeably larger beneath the header
  var exitHeaderExtraBottomDp: Int by integerValue(EXIT_HEADER_EXTRA_BOTTOM_DP, 40)
  var exitHeaderHeightDp: Int by integerValue(EXIT_HEADER_HEIGHT_DP, 120)
  // Triple tap configuration
  var exitTripleTapIntervalMs: Int by integerValue(EXIT_TRIPLE_TAP_INTERVAL_MS, 350)
  var exitTripleTapWindowMs: Int by integerValue(EXIT_TRIPLE_TAP_WINDOW_MS, 1000)
  // Confirmation popup timeout (ms)
  var exitConfirmTimeoutMs: Int by integerValue(EXIT_CONFIRM_TIMEOUT_MS, 5000)
  // Haptic feedback interval (ms) used during hold gestures
  var exitHapticFeedbackIntervalMs: Int by integerValue(EXIT_HAPTIC_FEEDBACK_INTERVAL_MS, 500)

  // Suppress message notifications while Accessibility Mode is active
  var suppressNotifications: Boolean by booleanValue(SUPPRESS_NOTIFICATIONS, true)

  public override fun onFirstEverAppLaunch() = Unit

  public override fun getKeysToIncludeInBackup(): List<String> {
    return listOf(
      ACCESSIBILITY_MODE_ENABLED,
      ACCESSIBILITY_RECIPIENT_ID,
      EXIT_GESTURE_TYPE,
      EXIT_GESTURE_HOLD_MS,
      EXIT_GESTURE_CONFIRM_MS,
      EXIT_GESTURE_TIMEOUT_MS,
      EXIT_GESTURE_CORNER_DP,
      EXIT_GESTURE_DRIFT_DP,
      EXIT_GESTURE_POINTER_TIMEOUT_MS,
      EXIT_HEADER_DEADZONE_DP,
      EXIT_TRIPLE_TAP_INTERVAL_MS,
      EXIT_TRIPLE_TAP_WINDOW_MS,
      EXIT_CONFIRM_TIMEOUT_MS,
      EXIT_HEADER_HEIGHT_DP,
      SUPPRESS_NOTIFICATIONS
    )
  }
}
