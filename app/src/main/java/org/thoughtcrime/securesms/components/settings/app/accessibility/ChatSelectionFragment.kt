package org.thoughtcrime.securesms.components.settings.app.accessibility

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.util.Log
import androidx.core.os.bundleOf
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.accessibility.AccessibilityModeSettingsFragment
import org.thoughtcrime.securesms.components.settings.app.accessibility.AccessibilityModeSettingsViewModel

class ChatSelectionFragment : androidx.fragment.app.Fragment() {

  private val PICK_CONVERSATION_REQUEST = 1

  override fun onCreateView(inflater: android.view.LayoutInflater, container: android.view.ViewGroup?, savedInstanceState: android.os.Bundle?): android.view.View? {
    // Immediately launch the existing Signal conversation picker activity and finish
    // We reuse the platform picker by starting the ChooseConversationActivity if available.
    try {
      val intent = android.content.Intent().apply {
        // The canonical picker in Signal is org.thoughtcrime.securesms.conversation.ChooseConversationActivity
        setClassName(requireContext(), "org.thoughtcrime.securesms.conversation.ChooseConversationActivity")
        putExtra("showAll", true)
      }
      startActivityForResult(intent, PICK_CONVERSATION_REQUEST)
    } catch (e: Exception) {
      // Fallback: show toast and close
      android.widget.Toast.makeText(requireContext(), "Unable to open conversation picker", android.widget.Toast.LENGTH_SHORT).show()
      requireActivity().onBackPressedDispatcher.onBackPressed()
    }

    // Return an empty view as this fragment only acts as a launcher
    return android.view.View(requireContext())
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    if (requestCode == PICK_CONVERSATION_REQUEST) {
      if (resultCode == android.app.Activity.RESULT_OK && data != null) {
        // The ChooseConversationActivity typically returns a thread id under "thread_id"
        val threadId = data.getLongExtra("thread_id", -1L)
        if (threadId > 0) {
          parentFragmentManager.setFragmentResult("pick_thread", bundleOf("thread_id" to threadId))
        }
      }

      // Navigate back regardless
      requireActivity().onBackPressedDispatcher.onBackPressed()
    }
  }
}
