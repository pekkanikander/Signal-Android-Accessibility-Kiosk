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

class AccessibilityModeSettingsFragment : ComposeFragment() {

  private val viewModel: AccessibilityModeSettingsViewModel by viewModels()

  override fun onResume() {
    super.onResume()

    // Check if we received a selected thread ID from chat selection
    val selectedThreadId = requireActivity().intent.getLongExtra("selected_thread_id", -1L)
    if (selectedThreadId != -1L) {
      Log.d("AccessibilityFragment", "Received selected thread ID: $selectedThreadId")
      viewModel.setThreadId(selectedThreadId)

      // Clear the extra so it doesn't get processed again
      requireActivity().intent.removeExtra("selected_thread_id")
    }
  }

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
    override fun onNavigationClick() {
      requireActivity().onBackPressedDispatcher.onBackPressed()
    }

    override fun onThreadSelectionClick() {
      // Check if there are any conversations available
      val hasChatsAvailable = SignalDatabase.threads
        .getUnarchivedConversationListCount(ConversationFilter.OFF) > 0

      if (!hasChatsAvailable) {
        android.widget.Toast.makeText(
          requireContext(),
          "No chats available yet. Start a conversation first!",
          android.widget.Toast.LENGTH_LONG
        ).show()
        return
      }

      // Navigate to chat selection screen
      findNavController().navigate(R.id.action_accessibilityModeSettingsFragment_to_chatSelectionFragment)
    }

    override fun onAccessibilityModeToggled(enabled: Boolean) {
      // Only allow enabling if a chat is selected
      if (enabled && viewModel.state.value.threadId == -1L) {
        android.widget.Toast.makeText(
          requireContext(),
          "Please select a chat first before enabling Accessibility Mode",
          android.widget.Toast.LENGTH_SHORT
        ).show()
        return
      }
      viewModel.setAccessibilityMode(enabled)
    }
  }
}
