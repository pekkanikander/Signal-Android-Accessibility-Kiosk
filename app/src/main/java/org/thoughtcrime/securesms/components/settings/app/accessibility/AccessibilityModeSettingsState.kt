package org.thoughtcrime.securesms.components.settings.app.accessibility

/**
 * UI state for accessibility mode settings screen.
 * Maps directly to AccessibilityModeValues properties.
 */
data class AccessibilityModeSettingsState(
  val isAccessibilityModeEnabled: Boolean,
  val threadId: Long
)
