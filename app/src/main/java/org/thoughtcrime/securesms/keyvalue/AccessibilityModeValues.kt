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
    // Helper-backed kiosk preparation (DO policy prepared via external helper)
    const val KIOSK_ENABLED = "accessibility_mode.kiosk_enabled"

    // Advanced settings keys (many not implemented in the UI yet)
    const val EXIT_GESTURE_TYPE = "accessibility_mode.exit_gesture_type"
    const val EXIT_HEADER_HEIGHT_DP = "accessibility_mode.exit_header_height_dp"
    const val EXIT_GESTURE_TIMEOUT_MS = "accessibility_mode.exit_gesture_timeout_ms"
    const val EXIT_GESTURE_POINTER_TIMEOUT_MS = "accessibility_mode.exit_gesture_pointer_timeout_ms"
    const val EXIT_TRIPLE_TAP_INTERVAL_MS = "accessibility_mode.exit_triple_tap_interval_ms"
    const val EXIT_CONFIRM_TIMEOUT_MS = "accessibility_mode.exit_confirm_timeout_ms"

    // Suppress message notifications while Accessibility Mode is active
    const val SUPPRESS_NOTIFICATIONS = "accessibility_mode.suppress_notifications"
  }

  // Boolean values using booleanValue delegate
  var isAccessibilityModeEnabled: Boolean by booleanValue(ACCESSIBILITY_MODE_ENABLED, false)
  // Whether helper-backed kiosk policy has been prepared (best-effort; controlled by Settings)
  var kioskEnabled: Boolean by booleanValue(KIOSK_ENABLED, false)

  // Long value for recipient ID
  var accessibilityRecipientId: Long by longValue(ACCESSIBILITY_RECIPIENT_ID, -1L)

  // Exit gesture configuration
  var exitGestureType: Int by integerValue(EXIT_GESTURE_TYPE, AccessibilityModeExitGestureType.TripleTap.value)

  // Advanced configuration
  // Overall timeout, to catch device sleeps et (ms)
  var exitGestureTimeoutMs: Int by integerValue(EXIT_GESTURE_TIMEOUT_MS, 8000)
  // Timeout for the second finger to arrive (ms)
  var exitGesturePointerTimeoutMs: Int by integerValue(EXIT_GESTURE_POINTER_TIMEOUT_MS, 700)
  var exitHeaderHeightDp: Int by integerValue(EXIT_HEADER_HEIGHT_DP, 120)
  // Triple tap configuration
  var exitTripleTapIntervalMs: Int by integerValue(EXIT_TRIPLE_TAP_INTERVAL_MS, 350)
  // Confirmation popup timeout (ms)
  var exitConfirmTimeoutMs: Int by integerValue(EXIT_CONFIRM_TIMEOUT_MS, 5000)

  // Suppress message notifications while Accessibility Mode is active
  var suppressNotifications: Boolean by booleanValue(SUPPRESS_NOTIFICATIONS, true)

  public override fun onFirstEverAppLaunch() = Unit

  public override fun getKeysToIncludeInBackup(): List<String> {
    return listOf(
      ACCESSIBILITY_MODE_ENABLED,
      ACCESSIBILITY_RECIPIENT_ID,
      KIOSK_ENABLED,
      EXIT_GESTURE_TYPE,
      EXIT_GESTURE_TIMEOUT_MS,
      EXIT_GESTURE_POINTER_TIMEOUT_MS,
      EXIT_TRIPLE_TAP_INTERVAL_MS,
      EXIT_CONFIRM_TIMEOUT_MS,
      EXIT_HEADER_HEIGHT_DP,
      SUPPRESS_NOTIFICATIONS
    )
  }
}
