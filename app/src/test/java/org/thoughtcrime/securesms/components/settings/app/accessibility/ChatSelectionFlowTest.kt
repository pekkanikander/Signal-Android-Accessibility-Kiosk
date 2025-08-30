package org.thoughtcrime.securesms.components.settings.app.accessibility

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.mockk
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.thoughtcrime.securesms.recipients.Recipient

@RunWith(AndroidJUnit4::class)
class ChatSelectionFlowTest {

  private lateinit var mockRecipient: Recipient
  private lateinit var chatSelectionItem: ChatSelectionItem

  @Before
  fun setUp() {
    // Create a simple mock recipient
    mockRecipient = mockk(relaxed = true)

    // Create test chat selection item
    chatSelectionItem = ChatSelectionItem(
      threadId = 123L,
      recipient = mockRecipient,
      lastMessagePreview = "Hello world"
    )
  }

  @Test
  fun `test AccessibilityModeSettingsViewModel setThreadId updates state`() {
    // Given: ViewModel with initial state
    val viewModel = AccessibilityModeSettingsViewModel()

    // Initial state should have threadId = -1L
    assert(viewModel.state.value.threadId == -1L)

    // When: Set thread ID
    viewModel.setThreadId(123L)

    // Then: State should be updated
    assert(viewModel.state.value.threadId == 123L)
  }
}
