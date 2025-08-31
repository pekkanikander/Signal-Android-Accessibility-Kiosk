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

    /**
     * Set the thread ID for the current conversation.
     * This will trigger loading of messages for this thread.
     */
    fun setThreadId(threadId: Long) {
        android.util.Log.d("AccessibilityViewModel", "setThreadId called with: $threadId")
        _state.value = _state.value.copy(threadId = threadId, isLoading = true)

        // Load real messages from Signal's database
        loadMessages()
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
                        // Filter out system messages like "You started this chat"
                        if (record.type != org.thoughtcrime.securesms.database.MessageTypes.JOINED_TYPE) {
                            messageRecords.add(record)
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

    // This method is now implemented above
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
