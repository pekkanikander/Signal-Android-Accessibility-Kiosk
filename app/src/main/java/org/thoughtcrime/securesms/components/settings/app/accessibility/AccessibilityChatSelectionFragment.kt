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
import org.thoughtcrime.securesms.contacts.selection.ContactSelectionArguments
import org.thoughtcrime.securesms.groups.SelectionLimits
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.R

/*
 * Initial implementation.  Space for future improvements.
 *
 * TODO (at minimum)
 * - Highlight currently configured chat (if any) in the list.
 * - Handle group creation from Accessibility chat picker if/when approved.
 * - If a user selects a contact without an existing thread, create a new thread.
 */

class AccessibilityChatSelectionFragment : LoggingFragment(),
    ContactSelectionListFragment.OnContactSelectedListener,
    ContactSelectionListFragment.NewConversationCallback {

  private lateinit var contactFilterView: ContactFilterView
  private lateinit var selectionFragment: ContactSelectionListFragment

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {

    /*
     * TODO: Preselect currently configured chat if present.
     * Read SignalStore.accessibilityMode.accessibilityRecipientId, convert to RecipientId,
     * and add to currentSelection so the list highlights the current choice.
     */
    val currentSelection = ArrayList<RecipientId>(/* XXX: TODO */)

    // Configure child fragment arguments for single-selection, existing-chat-only behavior
    childFragmentManager.addFragmentOnAttachListener { _, fragment ->
      val mask = (
        ContactSelectionDisplayMode.FLAG_PUSH or
        ContactSelectionDisplayMode.FLAG_ACTIVE_GROUPS or
        ContactSelectionDisplayMode.FLAG_GROUPS_AFTER_CONTACTS
      )
      fragment.arguments = ContactSelectionArguments(
        displayMode = mask,
        isRefreshable = false,
        includeRecents = true,
        includeChatTypes = false,
        selectionLimits = SelectionLimits.NO_LIMITS,
        currentSelection = currentSelection.toSet(),
        displayChips = false,
        canSelectSelf = false,
        recyclerChildClipping = false,
        recyclerPadBottom = ViewUtil.dpToPx(60)
      ).toArgumentBundle()
    }

    return inflater.inflate(R.layout.fragment_accessibility_chat_selection, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    val toolbar: Toolbar = view.findViewById(R.id.toolbar)

    toolbar.setTitle(R.string.AccessibilityChatSelectionFragment__select_chat_title)
    toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

    selectionFragment = childFragmentManager.findFragmentById(R.id.contact_selection_list) as ContactSelectionListFragment

    childFragmentManager.beginTransaction()
      .replace(R.id.contact_selection_list, selectionFragment)
      .commitNowAllowingStateLoss()

    contactFilterView = view.findViewById(R.id.contact_filter_edit_text)
    contactFilterView.setOnFilterChangedListener {
      if (it.isNullOrEmpty()) {
        selectionFragment.resetQueryFilter()
      } else {
        selectionFragment.setQueryFilter(it)
      }
    }
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

  override fun onContactDeselected(
    recipientId: java.util.Optional<RecipientId>,
    number: String?,
    chatType: java.util.Optional<org.thoughtcrime.securesms.contacts.paged.ChatType>) {}

  override fun onSelectionChanged() {}

  override fun onNewGroup(forceV1: Boolean) {
    /*
     * TODO: Enable group creation from Accessibility chat picker if/when approved.
     * Define nav action id and selection flow; ensure the resulting group RecipientId
     * is returned via "pick_recipient" result.
     */
  }

  override fun onInvite() {
    // Intentionally no-op for now.
  }
}
