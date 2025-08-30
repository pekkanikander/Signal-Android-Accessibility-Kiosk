package org.thoughtcrime.securesms.components.settings.app.accessibility

import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Stable

@Stable
@VisibleForTesting
interface AccessibilityModeSettingsCallbacks {

  fun onAccessibilityModeToggled(enabled: Boolean) = Unit
  fun onThreadSelectionClick() = Unit

  object Empty : AccessibilityModeSettingsCallbacks
}
