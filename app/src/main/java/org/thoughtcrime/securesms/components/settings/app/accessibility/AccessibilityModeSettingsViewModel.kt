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
import org.thoughtcrime.securesms.database.ThreadTable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import org.thoughtcrime.securesms.database.model.ThreadRecord

// Store wrapper for settings screen; avoids name collision with runtime router store
interface AccessibilityModeSettingsStore {
  var selectedRecipientId: RecipientId?
  var enabled: Boolean
  var exitGestureTypeValue: Int
  var suppressNotifications: Boolean
}

class SignalAccessibilityModeSettingsStore : AccessibilityModeSettingsStore {
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

  override var suppressNotifications: Boolean
    get() = SignalStore.accessibilityMode.suppressNotifications
    set(value) { SignalStore.accessibilityMode.suppressNotifications = value }
}

// UI state returned to Compose
data class AccessibilitySettingsUiState(
  val conversations: List<Long> = emptyList(), // minimal DTO: thread ids for now
  val selectedThreadId: Long? = null,
  val canEnable: Boolean = false,
  val enabled: Boolean = false,
  val exitGestureTypeValue: Int = 0,
  val suppressNotifications: Boolean = true
)

class AccessibilityModeSettingsViewModel(
  private val store: AccessibilityModeSettingsStore = SignalAccessibilityModeSettingsStore()
) : ViewModel() {

  // Typed snapshot of current conversations and their recipient ids
  private data class Snapshot(val threadIds: List<Long>, val recipientIds: Set<RecipientId>)

  // 1) Reactive conversation IDs (map Unit -> read DB)
  private val _conversationsFlow = MutableStateFlow<List<Long>>(emptyList())

  // Track which recipients currently have existing threads (for auto-disable when deleted)
  private val _recipientPresenceFlow = MutableStateFlow<Set<RecipientId>?>(null)
  private val recipientPresenceFlow: StateFlow<Set<RecipientId>?> = _recipientPresenceFlow.asStateFlow()

  private val conversationsFlow: StateFlow<List<Long>> = _conversationsFlow.asStateFlow()

  // 2) Store-backed state (reactive within this screen)
  private val _selectedRecipientId = MutableStateFlow(store.selectedRecipientId)
  private val _enabled = MutableStateFlow(store.enabled)
  private val _exitGesture = MutableStateFlow(store.exitGestureTypeValue)
  private val _suppressNotifications = MutableStateFlow(store.suppressNotifications)

  private val selectedRecipientIdFlow = _selectedRecipientId.asStateFlow()
  private val enabledFlow = _enabled.asStateFlow()
  // Publicly exposed for external observers (Activity) to react to gesture changes
  val exitGestureFlow = _exitGesture.asStateFlow()
  private val suppressNotificationsFlow = _suppressNotifications.asStateFlow()

  private val selectedThreadIdFlow: Flow<Long?> = selectedRecipientIdFlow.mapLatest { rid ->
    if (rid == null) return@mapLatest null
    withContext(Dispatchers.IO) {
      SignalDatabase.threads.getThreadIdIfExistsFor(rid)
    }
  }

  // Expose the selected thread's record (null if thread does not yet exist). No creation here.
  val selectedThreadRecord: StateFlow<ThreadRecord?> =
    selectedThreadIdFlow
      .mapLatest { id ->
        if (id == null) return@mapLatest null
        withContext(Dispatchers.IO) { SignalDatabase.threads.getThreadRecord(id) }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  // Track whether a thread has ever existed for the current recipient (to detect deletions)
  private val everExistedForRecipient = MutableStateFlow(false)

  // Re-evaluate existence on DB changes and recipient changes
  private val selectedThreadExistsFlow: Flow<Boolean> =
    combine(selectedRecipientIdFlow, RxDatabaseObserver.conversationList.asFlow()) { rid, _ -> rid }
      .mapLatest { rid ->
        if (rid == null) return@mapLatest false
        withContext(Dispatchers.IO) { SignalDatabase.threads.getThreadIdIfExistsFor(rid) != null }
      }
      .distinctUntilChanged()

  // 3) combined UI state
  private val _ui = MutableStateFlow(AccessibilitySettingsUiState())
  val ui: StateFlow<AccessibilitySettingsUiState> = _ui.asStateFlow()

  init {
    RxDatabaseObserver.conversationList.asFlow()
      .mapLatest {
        try {
          withContext(Dispatchers.IO) {
            val cursor = SignalDatabase.threads.getUnarchivedConversationList(
              conversationFilter = ConversationFilter.OFF,
              pinned = false,
              offset = 0L,
              limit = 200L,
              chatFolder = ChatFolderRecord()
            )

            cursor.use { c ->
              val reader = SignalDatabase.threads.readerFor(c)
              val threadIds = ArrayList<Long>(128)
              val recipientIds = LinkedHashSet<RecipientId>(128)
              val recipIdx = c.getColumnIndexOrThrow(ThreadTable.RECIPIENT_ID)
              while (c.moveToNext()) {
                val tr = reader.getCurrent()
                if (tr != null) threadIds.add(tr.threadId)
                val ridLong = c.getLong(recipIdx)
                if (ridLong > 0) recipientIds.add(RecipientId.from(ridLong))
              }
              Snapshot(threadIds, recipientIds)
            }
          }
        } catch (_: Exception) {
          // On error, surface empty snapshot (don’t crash UI)
          Snapshot(emptyList(), emptySet())
        }
      }
      .distinctUntilChanged()
      .onEach { snap: Snapshot ->
        _conversationsFlow.value = snap.threadIds
        _recipientPresenceFlow.value = snap.recipientIds
      }
      .launchIn(viewModelScope)

    // Reset existence tracker on selection change
    selectedRecipientIdFlow
      .onEach { everExistedForRecipient.value = false }
      .launchIn(viewModelScope)

    // Mark as having existed once we observe an existing thread
    selectedThreadExistsFlow
      .onEach { exists -> if (exists) everExistedForRecipient.value = true }
      .launchIn(viewModelScope)

    // Drive UI state from combined sources in two steps to keep overloads explicit.
    val baseStateFlow = combine(
      conversationsFlow,
      selectedThreadIdFlow,
      selectedRecipientIdFlow,
      enabledFlow,
      exitGestureFlow
    ) { conversations, selThread, selRecipient, en, gesture ->
      val hasSelection = selRecipient != null
      val canEnable = hasSelection
      val effectiveEnabled = en && canEnable
      AccessibilitySettingsUiState(conversations, selThread, canEnable, effectiveEnabled, gesture)
    }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccessibilitySettingsUiState())

    combine(baseStateFlow, suppressNotificationsFlow) { base, suppress ->
      base.copy(suppressNotifications = suppress)
    }
      .onEach { _ui.value = it }
      .launchIn(viewModelScope)

    selectedThreadExistsFlow
      .onEach { exists ->
        if (!exists && everExistedForRecipient.value && _enabled.value) {
          _enabled.value = false
          store.enabled = false
        }
      }
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

  fun onSetSuppressNotifications(enabled: Boolean) {
    _suppressNotifications.value = enabled
    store.suppressNotifications = enabled
  }

}
