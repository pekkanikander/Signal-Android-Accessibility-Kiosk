package org.thoughtcrime.securesms.components.settings.app.accessibility

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.*
import org.thoughtcrime.securesms.database.RxDatabaseObserver
import org.thoughtcrime.securesms.conversationlist.model.ConversationFilter
import org.thoughtcrime.securesms.components.settings.app.chats.folders.ChatFolderRecord
import org.thoughtcrime.securesms.database.SignalDatabase
import kotlinx.coroutines.reactive.asFlow
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.recipients.Recipient

// Small store wrapper to make testing easier
interface AccessibilityModeStore {
  var selectedRecipientId: RecipientId?
  var enabled: Boolean
  var exitGestureTypeValue: Int
}

class SignalAccessibilityModeStore : AccessibilityModeStore {
  override var selectedRecipientId: RecipientId?
    get() = SignalStore.accessibilityMode.accessibilityRecipientId
      .takeIf { it > 0 }?.let { RecipientId.from(it) }
    set(value) { SignalStore.accessibilityMode.accessibilityRecipientId = value?.toLong() ?: -1L }

  override var enabled: Boolean
    get() = SignalStore.accessibilityMode.isAccessibilityModeEnabled
    set(value) { SignalStore.accessibilityMode.isAccessibilityModeEnabled = value }

  override var exitGestureTypeValue: Int
    get() = SignalStore.accessibilityMode.exitGestureType
    set(value) { SignalStore.accessibilityMode.exitGestureType = value }
}

// UI state returned to Compose
data class AccessibilitySettingsUiState(
  val conversations: List<Long> = emptyList(), // minimal DTO: thread ids for now
  val selectedThreadId: Long? = null,
  val canEnable: Boolean = false,
  val enabled: Boolean = false,
  val exitGestureTypeValue: Int = 0
)

class AccessibilityModeSettingsViewModel(
  private val store: AccessibilityModeStore = SignalAccessibilityModeStore()
) : ViewModel() {

  // 1) Reactive conversation IDs (map Unit -> read DB)
  private val _conversationsFlow = MutableStateFlow<List<Long>>(emptyList())
  private val conversationsFlow: StateFlow<List<Long>> = _conversationsFlow.asStateFlow()

  // 2) Store-backed state (reactive within this screen)
  private val _selectedRecipientId = MutableStateFlow(store.selectedRecipientId)
  private val _enabled = MutableStateFlow(store.enabled)
  private val _exitGesture = MutableStateFlow(store.exitGestureTypeValue)

  private val selectedRecipientIdFlow = _selectedRecipientId.asStateFlow()
  private val enabledFlow = _enabled.asStateFlow()
  private val exitGestureFlow = _exitGesture.asStateFlow()

  private val selectedThreadIdFlow: Flow<Long?> = selectedRecipientIdFlow.mapLatest { rid ->
    if (rid == null) return@mapLatest null
    withContext(Dispatchers.IO) {
      val recipient = Recipient.resolved(rid)
      SignalDatabase.threads.getOrCreateThreadIdFor(recipient)
    }
  }

  // 3) combined UI state
  private val _ui = MutableStateFlow(AccessibilitySettingsUiState())
  val ui: StateFlow<AccessibilitySettingsUiState> = _ui.asStateFlow()

  init {
    RxDatabaseObserver.conversationList.asFlow()
      .mapLatest {
        try {
          withContext(Dispatchers.IO) {
            // lightweight query - return thread ids (reuse existing DB access pattern)
            val cursor = SignalDatabase.threads.getUnarchivedConversationList(
              conversationFilter = ConversationFilter.OFF,
              pinned = false,
              offset = 0L,
              limit = 200L,
              chatFolder = ChatFolderRecord()
            )

            val ids = mutableListOf<Long>()
            cursor.use {
              val reader = SignalDatabase.threads.readerFor(it)
              while (it.moveToNext()) {
                val threadRecord = reader.getCurrent()
                if (threadRecord != null) ids.add(threadRecord.threadId)
              }
            }

            ids
          }
        } catch (_: Exception) { emptyList<Long>() }
      }
      .distinctUntilChanged()
      .onEach { _conversationsFlow.value = it }
      .launchIn(viewModelScope)

    // Drive UI state from combined sources.
    combine(
      conversationsFlow,
      selectedThreadIdFlow,
      enabledFlow,
      exitGestureFlow
    ) { conversations, sel, en, gesture ->
      val hasSelection = sel != null
      val canEnable = hasSelection
      val effectiveEnabled = if (!canEnable) false else en
      AccessibilitySettingsUiState(conversations, sel, canEnable, effectiveEnabled, gesture)
    }
      .onEach { _ui.value = it }
      .launchIn(viewModelScope)
  }

  // Commands
  fun onSelectRecipient(rid: RecipientId) {
    _selectedRecipientId.value = rid
    store.selectedRecipientId = rid
  }

  fun onToggleEnabled(enabled: Boolean) {
    val sel = _selectedRecipientId.value
    if (sel == null) return
    _enabled.value = enabled
    store.enabled = enabled
  }

  fun onChangeGesture(typeValue: Int) {
    _exitGesture.value = typeValue
    store.exitGestureTypeValue = typeValue
  }

}
