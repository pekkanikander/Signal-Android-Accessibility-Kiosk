/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.accessibility

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.kotlin.subscribeBy
import kotlinx.coroutines.launch
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.conversation.colors.ChatColors
import org.thoughtcrime.securesms.conversation.colors.ChatColorsPalette
import org.thoughtcrime.securesms.conversation.v2.ConversationAdapterV2
import org.thoughtcrime.securesms.conversation.v2.ConversationRecipientRepository
import org.thoughtcrime.securesms.conversation.v2.ConversationRepository
import org.thoughtcrime.securesms.conversation.v2.ConversationViewModel
import org.thoughtcrime.securesms.conversation.ScheduledMessagesRepository
import org.thoughtcrime.securesms.messagerequests.MessageRequestRepository

/**
 * Fragment for the accessibility conversation interface.
 *
 * This fragment uses Signal's proven components directly:
 * - ConversationViewModel for data management
 * - ConversationAdapterV2 for message display
 * - AccessibilityItemClickListener for simplified interaction
 */
class AccessibilityModeFragment : Fragment() {

    private lateinit var messageList: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: Button
    private var threadId: Long = -1L

    // Signal's components - initialized after we have threadId
    private lateinit var conversationRecipientRepository: ConversationRecipientRepository
    private lateinit var messageRequestRepository: MessageRequestRepository
    private lateinit var viewModel: ConversationViewModel
    private lateinit var adapter: ConversationAdapterV2

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_accessibility_mode, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Get the selected thread ID from arguments
        threadId = arguments?.getLong("selected_thread_id", -1L) ?: -1L
        if (threadId == -1L) {
            android.util.Log.e("AccessibilityFragment", "No thread ID provided")
            return
        }

        android.util.Log.d("AccessibilityFragment", "Setting up accessibility mode for thread: $threadId")

        // Initialize Signal's components with the correct threadId
        conversationRecipientRepository = ConversationRecipientRepository(threadId)
        messageRequestRepository = MessageRequestRepository(requireContext())
        
        viewModel = ConversationViewModel(
            threadId = threadId,
            requestedStartingPosition = 0, // Start from beginning
            repository = ConversationRepository(localContext = requireContext(), isInBubble = false),
            recipientRepository = conversationRecipientRepository,
            messageRequestRepository = messageRequestRepository,
            scheduledMessagesRepository = ScheduledMessagesRepository(),
            initialChatColors = ChatColorsPalette.Bubbles.default.withId(ChatColors.Id.Auto)
        )

        adapter = ConversationAdapterV2(
            lifecycleOwner = viewLifecycleOwner,
            requestManager = Glide.with(this),
            clickListener = AccessibilityItemClickListener(),
            hasWallpaper = false, // No wallpaper for accessibility
            colorizer = org.thoughtcrime.securesms.conversation.colors.Colorizer(),
            startExpirationTimeout = viewModel::startExpirationTimeout,
            chatColorsDataProvider = viewModel::chatColorsSnapshot,
            displayDialogFragment = { /* No dialogs for accessibility */ }
        )

        // Initialize views
        messageList = view.findViewById(R.id.message_list)
        messageInput = view.findViewById(R.id.message_input)
        sendButton = view.findViewById(R.id.send_button)

        // Setup RecyclerView with Signal's adapter
        messageList.layoutManager = LinearLayoutManager(context)
        messageList.adapter = adapter
        adapter.setPagingController(viewModel.pagingController)

        // Setup send button
        sendButton.setOnClickListener {
            val messageText = messageInput.text.toString().trim()
            if (messageText.isNotEmpty()) {
                // Use Signal's sendMessage method with minimal parameters
                viewModel.recipient
                    .firstElement()
                    .subscribeBy(
                        onSuccess = { recipient ->
                            viewModel.sendMessage(
                                metricId = null,
                                threadRecipient = recipient,
                                body = messageText,
                                slideDeck = null,
                                scheduledDate = 0L,
                                messageToEdit = null,
                                quote = null,
                                mentions = emptyList(),
                                bodyRanges = null,
                                contacts = emptyList(),
                                linkPreviews = emptyList(),
                                preUploadResults = emptyList(),
                                isViewOnce = false
                            ).subscribe()
                        }
                    )
                messageInput.text.clear()
            }
        }

        // Observe recipient for UI updates
        viewModel.recipient
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(
                onNext = { recipient ->
                    android.util.Log.d("AccessibilityFragment", "Recipient updated: ${recipient.getDisplayName(requireContext())}")
                    // Could update conversation header here if needed
                }
            )

        // For now, keep send button always enabled for accessibility
        // We can add proper input state handling later if needed
        sendButton.isEnabled = true
        messageInput.isEnabled = true

        android.util.Log.d("AccessibilityFragment", "Accessibility mode setup complete")
    }
}
