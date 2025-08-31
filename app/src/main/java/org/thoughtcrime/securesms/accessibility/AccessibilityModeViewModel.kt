/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.accessibility

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.database.DatabaseObserver
import org.thoughtcrime.securesms.database.MessageTypes

/**
 * ViewModel for the accessibility conversation interface.
 *
 * Manages:
 * - Thread ID (selected conversation)
 * - Message list
 * - Loading state
 * - Conversation state
 */
class AccessibilityModeViewModel : ViewModel() {

    private val _state = MutableStateFlow(AccessibilityModeState())
    val state: StateFlow<AccessibilityModeState> = _state.asStateFlow()

    // Database observers for real-time updates
    private var messageInsertObserver: DatabaseObserver.MessageObserver? = null
    private var messageUpdateObserver: DatabaseObserver.MessageObserver? = null
    private var conversationObserver: DatabaseObserver.Observer? = null

    /**
     * Set the thread ID for the current conversation.
     * This will trigger loading of messages for this thread.
     */
    fun setThreadId(threadId: Long) {
        android.util.Log.d("AccessibilityViewModel", "setThreadId called with: $threadId")
        
        // Unregister previous observers if any
        unregisterObservers()
        
        _state.value = _state.value.copy(threadId = threadId, isLoading = true)

        // Suppress notifications for this thread while in accessibility mode
        org.thoughtcrime.securesms.notifications.v2.ConversationId.forConversation(threadId)?.let { conversationId ->
            org.thoughtcrime.securesms.dependencies.AppDependencies.messageNotifier.setVisibleThread(conversationId)
        }

        // Load real messages from Signal's database
        loadMessages()
        
        // Register observers for real-time updates
        registerObservers(threadId)
    }

    /**
     * Send a message to the current conversation.
     */
    fun sendMessage(messageText: String) {
        val threadId = _state.value.threadId
        if (threadId == -1L) {
            android.util.Log.e("AccessibilityViewModel", "Cannot send message: no thread selected")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Get the recipient for this thread
                val recipient = SignalDatabase.threads.getRecipientForThreadId(threadId)
                if (recipient == null) {
                    android.util.Log.e("AccessibilityViewModel", "Cannot send message: recipient not found")
                    return@launch
                }

                // Send the message using Signal's MessageSender
                org.thoughtcrime.securesms.sms.MessageSender.send(
                    org.thoughtcrime.securesms.dependencies.AppDependencies.application,
                    org.thoughtcrime.securesms.mms.OutgoingMessage(
                        threadRecipient = recipient,
                        sentTimeMillis = System.currentTimeMillis(),
                        body = messageText,
                        expiresIn = recipient.expiresInSeconds * 1000L,
                        isUrgent = true,
                        isSecure = true
                    ),
                    threadId,
                    org.thoughtcrime.securesms.sms.MessageSender.SendType.SIGNAL,
                    null
                ) {
                    android.util.Log.d("AccessibilityViewModel", "Message sent successfully")
                    // Reload messages to show the new message
                    loadMessages()
                }
            } catch (e: Exception) {
                android.util.Log.e("AccessibilityViewModel", "Failed to send message", e)
            }
        }
    }

            /**
     * Load real messages from Signal's database.
     */
    private fun loadMessages() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val threadId = _state.value.threadId
                if (threadId == -1L) {
                    android.util.Log.e("AccessibilityViewModel", "Cannot load messages: no thread selected")
                    return@launch
                }

                // Load messages from Signal's database
                val messageRecords = mutableListOf<MessageRecord>()
                org.thoughtcrime.securesms.database.MessageTable.mmsReaderFor(
                    SignalDatabase.messages.getConversation(threadId, 0L, 50L)
                ).use { reader ->
                    reader.forEach { record ->
                        android.util.Log.d("AccessibilityViewModel", "Message type: ${record.type}, JOINED_TYPE: ${MessageTypes.JOINED_TYPE}")
                        // Filter out system messages like "You started this chat"
                        if (record.type != MessageTypes.JOINED_TYPE) {
                            messageRecords.add(record)
                        } else {
                            android.util.Log.d("AccessibilityViewModel", "Filtered out JOINED_TYPE message: ${record.getDisplayBody(org.thoughtcrime.securesms.dependencies.AppDependencies.application)}")
                        }
                    }
                }

                // Convert MessageRecord to AccessibilityMessage and sort by timestamp (oldest first)
                val accessibilityMessages = messageRecords
                    .sortedBy { it.dateReceived } // Sort by timestamp, oldest first
                    .map { record ->
                        AccessibilityMessage(
                            id = record.id,
                            text = record.getDisplayBody(org.thoughtcrime.securesms.dependencies.AppDependencies.application).toString(),
                            isFromSelf = record.isOutgoing,
                            timestamp = record.dateReceived
                        )
                    }

                // Update state on main thread
                _state.value = _state.value.copy(
                    messages = accessibilityMessages,
                    isLoading = false
                )

                android.util.Log.d("AccessibilityViewModel", "Loaded ${accessibilityMessages.size} real messages from database")
            } catch (e: Exception) {
                android.util.Log.e("AccessibilityViewModel", "Failed to load messages", e)
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    /**
     * Register database observers for real-time updates
     */
    private fun registerObservers(threadId: Long) {
        messageInsertObserver = DatabaseObserver.MessageObserver { messageId ->
            android.util.Log.d("AccessibilityViewModel", "New message inserted: $messageId")
            // Reload messages when new message is inserted
            loadMessages()
        }
        
        messageUpdateObserver = DatabaseObserver.MessageObserver { messageId ->
            android.util.Log.d("AccessibilityViewModel", "Message updated: $messageId")
            // Reload messages when message is updated
            loadMessages()
        }
        
        conversationObserver = DatabaseObserver.Observer {
            android.util.Log.d("AccessibilityViewModel", "Conversation updated")
            // Reload messages when conversation changes
            loadMessages()
        }
        
        // Register observers
        org.thoughtcrime.securesms.dependencies.AppDependencies.databaseObserver.registerMessageInsertObserver(threadId, messageInsertObserver!!)
        org.thoughtcrime.securesms.dependencies.AppDependencies.databaseObserver.registerMessageUpdateObserver(messageUpdateObserver!!)
        org.thoughtcrime.securesms.dependencies.AppDependencies.databaseObserver.registerConversationObserver(threadId, conversationObserver!!)
    }
    
    /**
     * Unregister database observers
     */
    private fun unregisterObservers() {
        messageInsertObserver?.let { 
            org.thoughtcrime.securesms.dependencies.AppDependencies.databaseObserver.unregisterObserver(it)
            messageInsertObserver = null
        }
        messageUpdateObserver?.let { 
            org.thoughtcrime.securesms.dependencies.AppDependencies.databaseObserver.unregisterObserver(it)
            messageUpdateObserver = null
        }
        conversationObserver?.let { 
            org.thoughtcrime.securesms.dependencies.AppDependencies.databaseObserver.unregisterObserver(it)
            conversationObserver = null
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        unregisterObservers()
        
        // Clear visible thread to restore notifications
        org.thoughtcrime.securesms.dependencies.AppDependencies.messageNotifier.clearVisibleThread()
    }
}

/**
 * State for the accessibility conversation interface.
 */
data class AccessibilityModeState(
    val threadId: Long = -1L,
    val messages: List<AccessibilityMessage> = emptyList(),
    val isLoading: Boolean = false
)

// We'll use Signal's existing message types instead of custom ones
