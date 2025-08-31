/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.accessibility

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
        _state.value = _state.value.copy(threadId = threadId)

        // Load test messages for now
        loadTestMessages()
    }

    /**
     * Send a message to the current conversation.
     */
    fun sendMessage(messageText: String) {
        // TODO: Implement message sending
        // This will integrate with Signal's messaging system
    }

        /**
     * Load test messages for demonstration.
     */
    private fun loadTestMessages() {
        val testMessages = listOf(
            AccessibilityMessage(
                id = 1L,
                text = "Hello! This is a test message using Signal's existing layouts.",
                isFromSelf = false,
                timestamp = System.currentTimeMillis() - 60000
            ),
            AccessibilityMessage(
                id = 2L,
                text = "Hi there! How are you doing? This uses Signal's outgoing message layout.",
                isFromSelf = true,
                timestamp = System.currentTimeMillis() - 30000
            ),
            AccessibilityMessage(
                id = 3L,
                text = "I'm doing great, thanks for asking! The accessibility mode now uses Signal's existing UI components.",
                isFromSelf = false,
                timestamp = System.currentTimeMillis()
            )
        )

        _state.value = _state.value.copy(messages = testMessages)
        android.util.Log.d("AccessibilityViewModel", "Loaded ${testMessages.size} test messages using Signal layouts")
    }

    /**
     * Load messages for the current thread.
     */
    private fun loadMessages() {
        // TODO: Implement message loading from SignalDatabase
        // This will load messages for the current threadId
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
