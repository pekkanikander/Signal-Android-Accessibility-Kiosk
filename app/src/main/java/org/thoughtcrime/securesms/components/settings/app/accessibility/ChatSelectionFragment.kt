package org.thoughtcrime.securesms.components.settings.app.accessibility

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.R

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
      // For now, just show a toast and navigate back
      // The user will need to manually refresh the accessibility settings
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
