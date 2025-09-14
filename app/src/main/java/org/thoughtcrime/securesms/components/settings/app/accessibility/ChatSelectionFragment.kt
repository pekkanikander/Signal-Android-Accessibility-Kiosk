package org.thoughtcrime.securesms.components.settings.app.accessibility

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import org.thoughtcrime.securesms.ContactSelectionListFragment
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.components.ContactFilterView
import org.thoughtcrime.securesms.contacts.ContactSelectionDisplayMode
import org.thoughtcrime.securesms.contacts.SelectedContact
import org.thoughtcrime.securesms.contacts.paged.ChatType
import org.thoughtcrime.securesms.groups.SelectionLimits
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.R

class ChatSelectionFragment : LoggingFragment(),
    ContactSelectionListFragment.OnContactSelectedListener,
    ContactSelectionListFragment.NewConversationCallback {

  private lateinit var contactFilterView: ContactFilterView
  private lateinit var selectionFragment: ContactSelectionListFragment

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {

    val currentSelection = ArrayList<RecipientId>(/* XXX: TODO */)

    // Configure child fragment arguments for single-selection, existing-chat-only behavior
    childFragmentManager.addFragmentOnAttachListener { _, fragment ->
      val mask = (
        ContactSelectionDisplayMode.FLAG_PUSH or
        ContactSelectionDisplayMode.FLAG_ACTIVE_GROUPS or
        ContactSelectionDisplayMode.FLAG_GROUPS_AFTER_CONTACTS
      )
      fragment.arguments = Bundle().apply {
        putInt(ContactSelectionListFragment.DISPLAY_MODE, mask)
        putBoolean(ContactSelectionListFragment.REFRESHABLE, false)
        putBoolean(ContactSelectionListFragment.RECENTS, true)
        putParcelable(ContactSelectionListFragment.SELECTION_LIMITS, SelectionLimits.NO_LIMITS)
        putParcelableArrayList(ContactSelectionListFragment.CURRENT_SELECTION, currentSelection)
        putBoolean(ContactSelectionListFragment.HIDE_COUNT, true)
        putBoolean(ContactSelectionListFragment.DISPLAY_CHIPS, false)
        putBoolean(ContactSelectionListFragment.CAN_SELECT_SELF, false)
        putBoolean(ContactSelectionListFragment.RV_CLIP, false)
        putInt(ContactSelectionListFragment.RV_PADDING_BOTTOM, ViewUtil.dpToPx(60))
        putBoolean(ContactSelectionListFragment.INCLUDE_CHAT_TYPES, false)
      }
    }

    return inflater.inflate(R.layout.fragment_chat_selection, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    val toolbar: Toolbar = view.findViewById(R.id.toolbar)

    toolbar.setTitle(R.string.acc_mode_select_chat_title)
    toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

    childFragmentManager.setFragmentResultListener("dummy", this) { _, _ -> }

    contactFilterView = view.findViewById(R.id.contact_filter_edit_text)
    contactFilterView.setOnFilterChangedListener {
      if (it.isNullOrEmpty()) {
        selectionFragment.resetQueryFilter()
      } else {
        selectionFragment.setQueryFilter(it)
      }
    }

    selectionFragment = childFragmentManager.findFragmentById(R.id.contact_selection_list) as ContactSelectionListFragment

    childFragmentManager.beginTransaction()
      .replace(R.id.contact_selection_list, selectionFragment)
      .commitNowAllowingStateLoss()
  }

  override fun onBeforeContactSelected(
    isFromUnknownSearchKey: Boolean,
    recipientId: java.util.Optional<RecipientId>,
    number: String?,
    chatType: java.util.Optional<ChatType>,
    callback: java.util.function.Consumer<Boolean>) {
    // We will handle selection; tell the list not to mutate selection itself

    callback.accept(false)
    if (isFromUnknownSearchKey) return

    if (recipientId.isPresent) {
      val rid = recipientId.get()
      parentFragmentManager.setFragmentResult(
        "pick_recipient",
        bundleOf("recipient_id" to rid)
      )
      findNavController().popBackStack()
    }
  }

  override fun onContactDeselected(recipientId: java.util.Optional<RecipientId>, number: String?, chatType: java.util.Optional<org.thoughtcrime.securesms.contacts.paged.ChatType>) {}

  override fun onSelectionChanged() {}

  override fun onNewGroup(forceV1: Boolean) {
    // Optional: persist the flag somewhere if you need to force v1 here.
    // XXX: TBD
    //    findNavController().safeNavigate(
    //  R.id.action_chatSelectionFragment_to_selectMembersFragment  // <- use your real action id
    //)
  }

  override fun onInvite() {
    // Intentionally no-op for now.
  }
}
