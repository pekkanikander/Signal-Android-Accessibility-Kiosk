package org.thoughtcrime.securesms.components.settings.app.accessibility

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.thoughtcrime.securesms.compose.ComposeFragment

class AccessibilityModeAdvancedSettingsFragment : ComposeFragment() {

  private val viewModel: AccessibilityModeSettingsViewModel by viewModels()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    // No fragment results here.
  }

  @Composable
  override fun FragmentContent() {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val callbacks = Callbacks()

    AccessibilityModeAdvancedSettingsScreen(
      ui = ui,
      callbacks = callbacks
    )
  }

  private inner class Callbacks : AccessibilityModeAdvancedSettingsCallbacks {
    override fun onNavigationClick() {
      requireActivity().onBackPressedDispatcher.onBackPressed()
    }

    override fun onChangeGesture(typeValue: Int) {
      viewModel.onChangeGesture(typeValue)
    }
  }
}

interface AccessibilityModeAdvancedSettingsCallbacks {
  fun onNavigationClick() = Unit
  fun onChangeGesture(typeValue: Int) = Unit

  object Empty : AccessibilityModeAdvancedSettingsCallbacks
}
