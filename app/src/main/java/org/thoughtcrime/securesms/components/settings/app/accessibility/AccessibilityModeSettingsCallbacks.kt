package org.thoughtcrime.securesms.components.settings.app.accessibility

import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Stable

@Stable
@VisibleForTesting
interface AccessibilityModeSettingsCallbacks {

  fun onNavigationClick() = Unit
  fun onAccessibilityModeToggled(enabled: Boolean) = Unit
  fun onThreadSelectionClick() = Unit
  fun onStartAccessibilityModeClick() = Unit
  fun onExitGestureTypeClick() = Unit
  fun onExitGestureRequirePinToggled(requirePin: Boolean) = Unit

  object Empty : AccessibilityModeSettingsCallbacks
}
