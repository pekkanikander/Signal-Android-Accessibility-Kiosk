package org.thoughtcrime.securesms.keyvalue

import androidx.lifecycle.LiveData
import org.thoughtcrime.securesms.keyvalue.SignalStoreValues
import org.thoughtcrime.securesms.accessibility.AccessibilityModeExitGestureType
import org.thoughtcrime.securesms.util.SingleLiveEvent

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
  }

  // Configuration change event (mirrors SettingsValues pattern)
  private val _onConfigurationSettingChanged: SingleLiveEvent<String> = SingleLiveEvent()
  val onConfigurationSettingChanged: LiveData<String> get() = _onConfigurationSettingChanged

  // Boolean value with explicit setter to emit configuration change events (like theme/language)
  var isAccessibilityModeEnabled: Boolean
    get() = store.getBoolean(ACCESSIBILITY_MODE_ENABLED, false)
    set(value) {
      store.beginWrite()
        .putBoolean(ACCESSIBILITY_MODE_ENABLED, value)
        .commit()
      _onConfigurationSettingChanged.postValue(ACCESSIBILITY_MODE_ENABLED)
    }
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
    )
  }
}
