package org.thoughtcrime.securesms.keyvalue

import org.thoughtcrime.securesms.keyvalue.SignalStoreValues

/**
 * Stores accessibility mode settings for the Signal app.
 * Provides a simplified interface for users with cognitive impairments.
 */
class AccessibilityModeValues(store: KeyValueStore) : SignalStoreValues(store) {

  companion object {
    // Setting keys
    const val ACCESSIBILITY_MODE_ENABLED = "accessibility_mode.enabled"
    const val ACCESSIBILITY_THREAD_ID = "accessibility_thread.id"
  }

  // Boolean values using booleanValue delegate
  var isAccessibilityModeEnabled: Boolean by booleanValue(ACCESSIBILITY_MODE_ENABLED, false)

  // Long value for thread ID
  var accessibilityThreadId: Long by longValue(ACCESSIBILITY_THREAD_ID, -1L)

  public override fun onFirstEverAppLaunch() = Unit

  public override fun getKeysToIncludeInBackup(): List<String> {
    return listOf(
      ACCESSIBILITY_MODE_ENABLED,
      ACCESSIBILITY_THREAD_ID
    )
  }
}
