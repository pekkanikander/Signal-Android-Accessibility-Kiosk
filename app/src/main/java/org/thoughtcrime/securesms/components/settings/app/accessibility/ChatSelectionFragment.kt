package org.thoughtcrime.securesms.components.settings.app.accessibility

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.activity.result.contract.ActivityResultContracts
import androidx.navigation.fragment.findNavController

class ChatSelectionFragment : Fragment() {

  private val pickConversation = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
    if (result.resultCode == Activity.RESULT_OK) {
      val data = result.data
      // Be tolerant about extra naming
      val threadIdFromThreadId = data?.getLongExtra("thread_id", -1L) ?: -1L
      val threadIdFromCamel = data?.getLongExtra("threadId", -1L) ?: -1L
      val threadIdFromUri = data?.data?.lastPathSegment?.toLongOrNull() ?: -1L
      val threadId = listOf(threadIdFromThreadId, threadIdFromCamel, threadIdFromUri).firstOrNull { it > 0L } ?: -1L
      if (threadId > 0L) {
        parentFragmentManager.setFragmentResult("pick_thread", bundleOf("thread_id" to threadId))
      }
    }
    // Always go back after handling the result (success or cancel)
    safePop()
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // Launch as early as possible; this fragment is just a bridge
    launchPicker()
  }

  override fun onCreateView(
    inflater: android.view.LayoutInflater,
    container: android.view.ViewGroup?,
    savedInstanceState: Bundle?
  ): android.view.View {
    // Minimal view; we immediately navigate away once a result arrives
    return android.view.View(requireContext())
  }

  private fun launchPicker() {
    val ctx = requireContext()
    val pm = ctx.packageManager
    val pkg = ctx.packageName

    // Try a small set of known candidates. We keep them fully-qualified.
    val candidates = listOf(
      // Newer/explicit picker in some Signal versions
      "org.thoughtcrime.securesms.conversation.ChooseConversationActivity",
      // Fallbacks for other versions
      "org.thoughtcrime.securesms.conversationlist.ConversationListActivity",
      "org.thoughtcrime.securesms.recipients.ChooseRecipientActivity"
    )

    val intent = candidates.asSequence()
      .map { className ->
        Intent().apply {
          setClassName(pkg, className)
          putExtra("showAll", true)
          addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
      }
      .firstOrNull { it.resolveActivity(pm) != null }

    if (intent != null) {
      pickConversation.launch(intent)
    } else {
      Toast.makeText(ctx, "Unable to open conversation picker", Toast.LENGTH_SHORT).show()
      safePop()
    }
  }

  private fun safePop() {
    // Prefer nav pop if hosted in NavController; fallback to back press dispatcher
    val nav = findNavController()
    val popped = try { nav.popBackStack(); true } catch (_: IllegalStateException) { false }
    if (!popped) requireActivity().onBackPressedDispatcher.onBackPressed()
  }
}
