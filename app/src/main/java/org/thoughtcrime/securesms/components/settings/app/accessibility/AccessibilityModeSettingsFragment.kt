package org.thoughtcrime.securesms.components.settings.app.accessibility

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.R

class AccessibilityModeSettingsFragment : ComposeFragment() {

  private val viewModel: AccessibilityModeSettingsViewModel by viewModels()

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val callbacks = remember { Callbacks() }
    
    AccessibilityModeSettingsScreen(
      state = state,
      callbacks = callbacks
    )
  }

  private inner class Callbacks : AccessibilityModeSettingsCallbacks {
    override fun onAccessibilityModeToggled(enabled: Boolean) {
      viewModel.setAccessibilityMode(enabled)
    }

    override fun onThreadSelectionClick() {
      // TODO: Navigate to thread selection screen
      // For now, just log the action
      // callbacks.navigate(R.id.action_accessibilityModeSettingsFragment_to_threadSelectionFragment)
    }
  }
}
