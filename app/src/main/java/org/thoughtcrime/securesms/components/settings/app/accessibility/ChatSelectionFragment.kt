package org.thoughtcrime.securesms.components.settings.app.accessibility

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
      // Find the accessibility settings fragment and update its ViewModel
      val accessibilityFragment = requireActivity()
        .supportFragmentManager
        .fragments
        .filterIsInstance<AccessibilityModeSettingsFragment>()
        .firstOrNull()

      if (accessibilityFragment != null) {
        // Use reflection to access the private viewModel property
        val viewModelField = AccessibilityModeSettingsFragment::class.java.getDeclaredField("viewModel")
        viewModelField.isAccessible = true
        val viewModel = viewModelField.get(accessibilityFragment) as AccessibilityModeSettingsViewModel
        
        // Update the selected thread ID
        viewModel.setThreadId(chat.threadId)
        
        // Show success message
        android.widget.Toast.makeText(
          requireContext(),
          "Chat selected: ${chat.recipient.getShortDisplayName(requireContext())}",
          android.widget.Toast.LENGTH_SHORT
        ).show()
      }
      
      // Navigate back
      requireActivity().onBackPressedDispatcher.onBackPressed()
    }

    fun onNavigationClick() {
      requireActivity().onBackPressedDispatcher.onBackPressed()
    }
  }
}
