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
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ConcatAdapter
import com.bumptech.glide.Glide
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
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
import org.thoughtcrime.securesms.util.SignalLocalMetrics
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.notifications.v2.ConversationId
import androidx.recyclerview.widget.ConversationLayoutManager
import org.thoughtcrime.securesms.conversation.MarkReadHelper

/**
 * Fragment for the accessibility conversation interface.
 *
 * This fragment uses Signal's proven components directly:
 * - ConversationViewModel for data management
 * - ConversationAdapterV2 for message display
 * - AccessibilityItemClickListener for simplified interaction
 * - MarkReadHelper for proper read status management
 */
class AccessibilityModeFragment : Fragment() {

    private lateinit var messageList: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: Button
    private var threadId: Long = -1L
    private var previousMessageCount = 0

    // Signal's components - initialized after we have threadId
    private lateinit var conversationRecipientRepository: ConversationRecipientRepository
    private lateinit var messageRequestRepository: MessageRequestRepository
    private lateinit var viewModel: ConversationViewModel
    private lateinit var adapter: ConversationAdapterV2
    private lateinit var layoutManager: ConversationLayoutManager
    private lateinit var markReadHelper: MarkReadHelper

    // RxJava subscription management
    private val disposables = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize Signal's metrics system before creating ViewModel
        SignalLocalMetrics.ConversationOpen.start()
    }

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

        // Suppress notifications for this thread to prevent popups
        AppDependencies.messageNotifier.setVisibleThread(ConversationId.forConversation(threadId))

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

        // Initialize MarkReadHelper for proper read status management
        markReadHelper = MarkReadHelper(ConversationId.forConversation(threadId), requireContext(), viewLifecycleOwner)
        markReadHelper.ignoreViewReveals() // Ignore during initial setup

        // Initialize views
        messageList = view.findViewById(R.id.message_list)
        messageInput = view.findViewById(R.id.message_input)
        sendButton = view.findViewById(R.id.send_button)

        // Setup RecyclerView with Signal's configuration
        layoutManager = ConversationLayoutManager(requireContext())
        messageList.setHasFixedSize(false)
        messageList.layoutManager = layoutManager
        messageList.adapter = adapter
        adapter.setPagingController(viewModel.pagingController)

        // Add scroll listener for mark as read functionality
        messageList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                // Mark messages as read when scrolled to them
                val timestamp = MarkReadHelper.getLatestTimestamp(adapter, layoutManager)
                timestamp.ifPresent(markReadHelper::onViewsRevealed)
            }
        })

        // Observe conversation data and update adapter
        disposables.add(
            viewModel.conversationThreadState
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .flatMapObservable { it.items.data }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                    onNext = { messages ->
                        android.util.Log.d("AccessibilityFragment", "Received ${messages.size} messages, updating adapter")
                        adapter.submitList(messages) {
                            android.util.Log.d("AccessibilityFragment", "Adapter updated with ${messages.size} messages")

                            // Auto-scroll to bottom if new messages were added
                            if (messages.size > previousMessageCount) {
                                android.util.Log.d("AccessibilityFragment", "New messages detected, scrolling to bottom")
                                messageList.post {
                                    layoutManager.scrollToPositionWithOffset(0, 0) {
                                        android.util.Log.d("AccessibilityFragment", "Scrolled to bottom")
                                    }
                                }
                            }
                            previousMessageCount = messages.size

                            // Stop ignoring view reveals after initial setup
                            if (messages.isNotEmpty()) {
                                markReadHelper.stopIgnoringViewReveals(MarkReadHelper.getLatestTimestamp(adapter, layoutManager).orElse(null))
                            }
                        }
                    },
                    onError = { error ->
                        android.util.Log.e("AccessibilityFragment", "Error loading conversation data", error)
                    }
                )
        )

        // Setup send button
        sendButton.setOnClickListener {
            val messageText = messageInput.text.toString().trim()
            if (messageText.isNotEmpty()) {
                // Use Signal's sendMessage method with minimal parameters
                disposables.add(
                    viewModel.recipient
                        .firstElement()
                        .subscribeBy(
                            onSuccess = { recipient ->
                                disposables.add(
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
                                )
                            }
                        )
                )
                messageInput.text.clear()
            }
        }

        // Observe recipient for UI updates
        disposables.add(
            viewModel.recipient
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                    onNext = { recipient ->
                        android.util.Log.d("AccessibilityFragment", "Recipient updated: ${recipient.getDisplayName(requireContext())}")
                        // Could update conversation header here if needed
                    }
                )
        )

        // For now, keep send button always enabled for accessibility
        // We can add proper input state handling later if needed
        sendButton.isEnabled = true
        messageInput.isEnabled = true

        android.util.Log.d("AccessibilityFragment", "Accessibility mode setup complete")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Dispose all RxJava subscriptions to prevent memory leaks and crashes
        disposables.clear()
        // Clear the visible thread when leaving accessibility mode
        AppDependencies.messageNotifier.setVisibleThread(null)
    }
}
