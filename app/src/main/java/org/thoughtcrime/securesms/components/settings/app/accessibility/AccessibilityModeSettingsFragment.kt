package org.thoughtcrime.securesms.components.settings.app.accessibility

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import android.util.Log
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.conversationlist.model.ConversationFilter
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.accessibility.AccessibilityModeExitGestureType

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
