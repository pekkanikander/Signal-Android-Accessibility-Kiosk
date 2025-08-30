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
    override fun onNavigationClick() {
      requireActivity().onBackPressedDispatcher.onBackPressed()
    }



            override fun onThreadSelectionClick() {
          // TODO: Navigate to thread selection screen
          // For now, show a toast to indicate the feature is working
          val state = viewModel.state.value

          // Check if there are any conversations available
          val hasChatsAvailable = org.thoughtcrime.securesms.database.SignalDatabase.threads
            .getUnarchivedConversationListCount(org.thoughtcrime.securesms.conversationlist.model.ConversationFilter.OFF) > 0

          when {
            !hasChatsAvailable -> {
              android.widget.Toast.makeText(
                requireContext(),
                "No chats available yet. Start a conversation first!",
                android.widget.Toast.LENGTH_LONG
              ).show()
            }
            state.threadId == -1L -> {
              android.widget.Toast.makeText(
                requireContext(),
                "No chat selected yet. Tap to choose a chat.",
                android.widget.Toast.LENGTH_SHORT
              ).show()
            }
            else -> {
              android.widget.Toast.makeText(
                requireContext(),
                "Chat selection coming soon!",
                android.widget.Toast.LENGTH_SHORT
              ).show()
            }
          }
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
