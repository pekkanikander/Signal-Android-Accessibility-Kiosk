package org.thoughtcrime.securesms.components.settings.app.accessibility

import androidx.compose.runtime.Composable
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.R
import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.Toolbar

class AccessibilityModeSettingsFragment : ComposeFragment() {

  private val viewModel: AccessibilityModeSettingsViewModel by viewModels()

  override fun onCreate(savedInstanceState: android.os.Bundle?) {
    super.onCreate(savedInstanceState)

    // Listen for conversation picker results via Fragment Result API
    parentFragmentManager.setFragmentResultListener("pick_thread", this) { _, bundle ->
      val id = bundle.getLong("thread_id")
      viewModel.onSelectConversation(id)
    }
  }

  @Composable
  override fun FragmentContent() {
    AccessibilityModeSettingsScreen(
      viewModel = viewModel,
      navigateToAdvanced = {
        try {
          findNavController().navigate(R.id.action_accessibilityModeSettingsFragment_to_accessibilityModeAdvancedSettingsFragment)
        } catch (_: Exception) {
          // nav entry may not exist yet; ignore
        }
      },
      launchPicker = {
        try {
          findNavController().navigate(R.id.action_accessibilityModeSettingsFragment_to_chatSelectionFragment)
        } catch (_: Exception) {
        }
      }
    )
  }

}
