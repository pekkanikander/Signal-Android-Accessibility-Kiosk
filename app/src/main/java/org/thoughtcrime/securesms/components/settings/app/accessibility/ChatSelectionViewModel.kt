package org.thoughtcrime.securesms.components.settings.app.accessibility

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.thoughtcrime.securesms.conversationlist.model.ConversationFilter
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.ThreadRecord
import org.thoughtcrime.securesms.components.settings.app.chats.folders.ChatFolderRecord
import org.thoughtcrime.securesms.recipients.Recipient

/**
 * ViewModel for chat selection screen.
 * Loads available chats from the database and provides selection functionality.
 */
class ChatSelectionViewModel : ViewModel() {

  private val store = MutableStateFlow(ChatSelectionState())
  val state: StateFlow<ChatSelectionState> = store

  init {
    loadChats()
  }

  fun refreshChats() {
    loadChats()
  }

  private fun loadChats() {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        val cursor = SignalDatabase.threads.getUnarchivedConversationList(
          conversationFilter = ConversationFilter.OFF,
          pinned = false,
          offset = 0L,
          limit = 50L, // Limit to 50 most recent chats
          chatFolder = ChatFolderRecord()
        )

        val chats = mutableListOf<ChatSelectionItem>()

                cursor.use { 
          val reader = SignalDatabase.threads.readerFor(it)
          while (it.moveToNext()) {
            val threadRecord = reader.getCurrent()
            if (threadRecord != null) {
              val recipient = threadRecord.recipient
              
              // Get last message preview
              val lastMessagePreview = getLastMessagePreview(threadRecord.threadId)
              
              chats.add(
                ChatSelectionItem(
                  threadId = threadRecord.threadId,
                  recipient = recipient,
                  lastMessagePreview = lastMessagePreview
                )
              )
            }
          }
        }

        store.update { it.copy(chats = chats, isLoading = false) }
      } catch (e: Exception) {
        store.update { it.copy(error = e.message ?: "Unknown error", isLoading = false) }
      }
    }
  }

    private fun getLastMessagePreview(threadId: Long): String {
    return try {
      val cursor = SignalDatabase.messages.getConversation(threadId, 0L, 1L)
      cursor.use { 
        if (it.moveToFirst()) {
          val messageRecord = org.thoughtcrime.securesms.database.MessageTable.mmsReaderFor(it).getCurrent()
          messageRecord.body ?: ""
        } else {
          ""
        }
      }
    } catch (e: Exception) {
      ""
    }
  }
}

data class ChatSelectionState(
  val chats: List<ChatSelectionItem> = emptyList(),
  val isLoading: Boolean = true,
  val error: String? = null
)
