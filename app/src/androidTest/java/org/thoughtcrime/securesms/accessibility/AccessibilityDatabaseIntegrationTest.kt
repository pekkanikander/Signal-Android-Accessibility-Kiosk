package org.thoughtcrime.securesms.accessibility

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.thoughtcrime.securesms.database.MmsHelper
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.ThreadTable
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.testing.SignalActivityRule
import org.thoughtcrime.securesms.testing.SignalDatabaseRule
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.not
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.thoughtcrime.securesms.keyvalue.SignalStore

/**
 * Test case: Database Integration Tests
 *
 * This test verifies that accessibility mode can properly access and work with
 * Signal's conversation data structures and database operations.
 */
@RunWith(AndroidJUnit4::class)
class AccessibilityDatabaseIntegrationTest {

    @get:Rule
    val signalActivityRule = SignalActivityRule()

    @get:Rule
    val databaseRule = SignalDatabaseRule()

    @Test
    fun can_access_conversation_data_for_accessibility_mode() {
        // Given: A conversation exists with messages
        val recipient = signalActivityRule.self
        val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(recipient)

        // Insert some test messages
        val messageId1 = MmsHelper.insert(recipient = recipient, threadId = threadId, body = "First message")
        val messageId2 = MmsHelper.insert(recipient = recipient, threadId = threadId, body = "Second message")
        val messageId3 = MmsHelper.insert(recipient = recipient, threadId = threadId, body = "Third message")

        // When: We access conversation data (as accessibility mode would)
        val conversationList = SignalDatabase.threads.getUnarchivedConversationList(
            conversationFilter = org.thoughtcrime.securesms.conversationlist.model.ConversationFilter.OFF,
            includeMuted = true,
            includeArchived = false,
            includeGroups = true,
            includeIndividuals = true,
            chatFolder = org.thoughtcrime.securesms.components.settings.app.chats.folders.ChatFolderRecord.FolderType.ALL
        )

        val messages = SignalDatabase.messages.getMessages(threadId, 0, 10)

        // Then: We should be able to access the conversation and messages
        assertThat("Conversation list should not be empty", conversationList.size, not(equalTo(0)))
        assertThat("Messages should be accessible", messages, not(nullValue()))
        assertThat("Should have 3 messages", messages.size, equalTo(3))

        // Verify thread exists and is accessible
        val threadRecord = SignalDatabase.threads.getThreadRecord(threadId)
        assertThat("Thread record should exist", threadRecord, not(nullValue()))
        assertThat("Thread ID should match", threadRecord!!.threadId, equalTo(threadId))
    }

    @Test
    fun accessibility_settings_work_with_database_operations() {
        // Given: Accessibility mode is configured
        val testRecipient = signalActivityRule.others[0]
        val testThreadId = SignalDatabase.threads.getOrCreateThreadIdFor(testRecipient)

        // Configure accessibility mode settings
        SignalStore.accessibilityMode.run {
            isAccessibilityModeEnabled = true
            accessibilityThreadId = testThreadId
        }

        // When: We perform database operations that accessibility mode would use
        val threadRecord = SignalDatabase.threads.getThreadRecord(testThreadId)
        val recipient = threadRecord?.recipient

        // Then: All data should be accessible and consistent
        assertThat("Thread should exist", threadRecord, not(nullValue()))
        assertThat("Recipient should be accessible", recipient, not(nullValue()))
        assertThat("Thread ID should match settings", testThreadId,
                   equalTo(SignalStore.accessibilityMode.accessibilityThreadId))

        // Verify the recipient matches
        assertThat("Recipient should match the test recipient",
                   recipient?.id, equalTo(testRecipient.id))
    }

    @Test
    fun can_create_and_access_thread_for_accessibility_mode() {
        // Given: A new recipient for accessibility mode
        val newRecipient = signalActivityRule.others[1]

        // When: We create a thread for accessibility mode (simulating user selection)
        val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(newRecipient)

        // Add a message to make it a real conversation
        MmsHelper.insert(recipient = newRecipient, threadId = threadId, body = "Accessibility mode message")

        // Then: The thread should be properly created and accessible
        assertThat("Thread ID should be valid", threadId, not(equalTo(-1L)))

        val threadRecord = SignalDatabase.threads.getThreadRecord(threadId)
        assertThat("Thread record should be created", threadRecord, not(nullValue()))

        // Verify thread is not archived (important for accessibility mode)
        assertThat("Thread should not be archived", threadRecord?.isArchived, equalTo(false))

        // Verify we can get message count
        val messageCount = SignalDatabase.messages.getMessageCount(threadId)
        assertThat("Should have at least one message", messageCount, not(equalTo(0)))
    }

    @Test
    fun thread_operations_preserve_accessibility_mode_settings() {
        // Given: Accessibility mode is configured with a thread
        val testRecipient = signalActivityRule.others[2]
        val originalThreadId = SignalDatabase.threads.getOrCreateThreadIdFor(testRecipient)

        SignalStore.accessibilityMode.run {
            isAccessibilityModeEnabled = true
            accessibilityThreadId = originalThreadId
        }

        // When: We perform various thread operations
        // Add a message
        MmsHelper.insert(recipient = testRecipient, threadId = originalThreadId, body = "Test message")

        // Update thread (simulate read/unread operations)
        SignalDatabase.threads.update(originalThreadId, false)

        // Then: Accessibility mode settings should remain intact
        assertThat("Accessibility mode should still be enabled",
                   SignalStore.accessibilityMode.isAccessibilityModeEnabled, equalTo(true))
        assertThat("Thread ID should remain the same",
                   SignalStore.accessibilityMode.accessibilityThreadId, equalTo(originalThreadId))

        // And thread should still be accessible
        val threadRecord = SignalDatabase.threads.getThreadRecord(originalThreadId)
        assertThat("Thread should still be accessible", threadRecord, not(nullValue()))
    }
}
