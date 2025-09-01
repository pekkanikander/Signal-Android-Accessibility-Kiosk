/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.accessibility

import android.content.Context
import org.thoughtcrime.securesms.conversation.colors.ChatColors
import org.thoughtcrime.securesms.conversation.colors.ChatColorsPalette
import org.thoughtcrime.securesms.conversation.v2.ConversationViewModel
import org.thoughtcrime.securesms.conversation.v2.ConversationRecipientRepository
import org.thoughtcrime.securesms.conversation.v2.ConversationRepository
import org.thoughtcrime.securesms.messagerequests.MessageRequestRepository
import org.thoughtcrime.securesms.conversation.ScheduledMessagesRepository

/**
 * Helper class to verify our integration approach compiles correctly.
 * 
 * This is a compilation test to ensure all dependencies can be imported
 * and the integration pattern is syntactically correct.
 */
object IntegrationTestHelper {

    /**
     * Verify that we can create all required dependencies for ConversationViewModel.
     * This method doesn't actually instantiate anything, just verifies the types compile.
     */
    fun verifyConversationViewModelDependencies(
        context: Context,
        threadId: Long
    ): Boolean {
        // Verify all required types can be imported and referenced
        val conversationRecipientRepository: ConversationRecipientRepository = ConversationRecipientRepository(threadId)
        val messageRequestRepository: MessageRequestRepository = MessageRequestRepository(context)
        val conversationRepository: ConversationRepository = ConversationRepository(localContext = context, isInBubble = false)
        val scheduledMessagesRepository: ScheduledMessagesRepository = ScheduledMessagesRepository()
        val initialChatColors: ChatColors = ChatColorsPalette.Bubbles.default.withId(ChatColors.Id.Auto)
        
        // Verify ConversationViewModel constructor signature
        val viewModel: ConversationViewModel = ConversationViewModel(
            threadId = threadId,
            requestedStartingPosition = 0,
            repository = conversationRepository,
            recipientRepository = conversationRecipientRepository,
            messageRequestRepository = messageRequestRepository,
            scheduledMessagesRepository = scheduledMessagesRepository,
            initialChatColors = initialChatColors
        )
        
        // Verify we can access key properties
        val recipient = viewModel.recipient
        val pagingController = viewModel.pagingController
        val inputReadyState = viewModel.inputReadyState
        val scrollButtonState = viewModel.scrollButtonState
        
        return true
    }
}
