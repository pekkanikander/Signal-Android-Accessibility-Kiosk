package org.thoughtcrime.securesms.components.settings.app.accessibility

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.util.Log
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.accessibility.AccessibilityModeSettingsFragment
import org.thoughtcrime.securesms.components.settings.app.accessibility.AccessibilityModeSettingsViewModel

class ChatSelectionFragment : ComposeFragment() {

  private val viewModel: ChatSelectionViewModel by viewModels()



  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val callbacks = remember { Callbacks() }

    ChatSelectionScreen(
      chats = state.chats,
      onChatSelected = callbacks::onChatSelected,
      onNavigationClick = callbacks::onNavigationClick
    )
  }

          private inner class Callbacks {
    fun onChatSelected(chat: ChatSelectionItem) {
      Log.d("ChatSelection", "Chat selected: ${chat.threadId}")

      // Store the selected thread ID in the activity's intent extras
      // This will be read when we return to the accessibility settings
      requireActivity().intent.putExtra("selected_thread_id", chat.threadId)

      // Show success message
      android.widget.Toast.makeText(
        requireContext(),
        "Chat selected: ${chat.recipient.getShortDisplayName(requireContext())}",
        android.widget.Toast.LENGTH_SHORT
      ).show()

      // Navigate back
      requireActivity().onBackPressedDispatcher.onBackPressed()
    }

    fun onNavigationClick() {
      requireActivity().onBackPressedDispatcher.onBackPressed()
    }
  }
}
