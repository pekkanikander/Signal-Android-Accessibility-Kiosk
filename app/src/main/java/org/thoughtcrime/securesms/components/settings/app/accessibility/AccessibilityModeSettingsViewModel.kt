package org.thoughtcrime.securesms.components.settings.app.accessibility

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
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

import org.thoughtcrime.securesms.accessibility.AccessibilityModeStore
import org.thoughtcrime.securesms.accessibility.SignalAccessibilityModeStore
import org.thoughtcrime.securesms.accessibility.AccessibilityModeExitGestureType

// UI state returned to Compose
data class AccessibilitySettingsUiState(
  val conversations: List<Long> = emptyList(), // minimal DTO: thread ids for now
  val canEnable: Boolean = false,
  val enabled: Boolean = false,
  val exitGestureTypeValue: Int = 0,
  val kioskEnabled: Boolean = false,
  val exitGestureTimeoutMs: Int = 8000,
  val exitGesturePointerTimeoutMs: Int = 700,
  val exitHeaderHeightDp: Int = 120,
  val exitTripleTapIntervalMs: Int = 350,
  val exitConfirmTimeoutMs: Int = 5000
)

class AccessibilityModeSettingsViewModel(
  private val store: AccessibilityModeStore = SignalAccessibilityModeStore
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
  private val _selectedRecipientId = MutableStateFlow(store.state.value.recipientId)
  private val _enabled = MutableStateFlow(store.state.value.enabled)
  private val _exitGesture = MutableStateFlow(store.state.value.gestureType.value)
  private val _kioskEnabled = MutableStateFlow(store.state.value.kioskEnabled)
  private val _exitGestureTimeoutMs = MutableStateFlow(store.state.value.exitGestureTimeoutMs)
  private val _exitGesturePointerTimeoutMs = MutableStateFlow(store.state.value.exitGesturePointerTimeoutMs)
  private val _exitHeaderHeightDp = MutableStateFlow(store.state.value.exitHeaderHeightDp)
  private val _exitTripleTapIntervalMs = MutableStateFlow(store.state.value.exitTripleTapIntervalMs)
  private val _exitConfirmTimeoutMs = MutableStateFlow(store.state.value.exitConfirmTimeoutMs)

  private val selectedRecipientIdFlow = _selectedRecipientId.asStateFlow()
  private val enabledFlow = _enabled.asStateFlow()
  // Publicly exposed for external observers (Activity) to react to gesture changes
  val exitGestureFlow = _exitGesture.asStateFlow()
  private val kioskEnabledFlow = _kioskEnabled.asStateFlow()
  private val exitGestureTimeoutFlow = _exitGestureTimeoutMs.asStateFlow()
  private val exitGesturePointerTimeoutFlow = _exitGesturePointerTimeoutMs.asStateFlow()
  private val exitHeaderHeightFlow = _exitHeaderHeightDp.asStateFlow()
  private val exitTripleTapIntervalFlow = _exitTripleTapIntervalMs.asStateFlow()
  private val exitConfirmTimeoutFlow = _exitConfirmTimeoutMs.asStateFlow()

  // Expose the selected thread's record (null if thread does not yet exist). No creation here.
  val selectedThreadRecord: StateFlow<ThreadRecord?> =
    selectedRecipientIdFlow
      .mapLatest { rid ->
        if (rid == null) return@mapLatest null
        withContext(Dispatchers.IO) {
          val id = SignalDatabase.threads.getThreadIdIfExistsFor(rid)
          if (id != null) SignalDatabase.threads.getThreadRecord(id) else null
        }
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
    val hasSelectionFlow: StateFlow<Boolean> = selectedRecipientIdFlow
      .mapLatest { it != null }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val baseStateFlow = combine(
      conversationsFlow,
      hasSelectionFlow,
      enabledFlow,
      exitGestureFlow
    ) { conversations: List<Long>, hasSelection: Boolean, en: Boolean, gesture: Int ->
      val canEnable = hasSelection
      val effectiveEnabled = en && canEnable
      AccessibilitySettingsUiState(
        conversations = conversations,
        canEnable = canEnable,
        enabled = effectiveEnabled,
        exitGestureTypeValue = gesture
      )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccessibilitySettingsUiState())

    val withStore = combine(baseStateFlow, store.state) { base, st ->
      base.copy(
        kioskEnabled = st.kioskEnabled,
        exitGestureTimeoutMs = st.exitGestureTimeoutMs,
        exitGesturePointerTimeoutMs = st.exitGesturePointerTimeoutMs,
        exitHeaderHeightDp = st.exitHeaderHeightDp,
        exitTripleTapIntervalMs = st.exitTripleTapIntervalMs,
        exitConfirmTimeoutMs = st.exitConfirmTimeoutMs
      )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccessibilitySettingsUiState())

    withStore.onEach { _ui.value = it }
      .launchIn(viewModelScope)

    selectedThreadExistsFlow
      .onEach { exists ->
        if (!exists && everExistedForRecipient.value && _enabled.value) {
          _enabled.value = false
          store.setEnabled(false)
        }
      }
      .launchIn(viewModelScope)
  }

  // Commands
  fun onSelectRecipient(rid: RecipientId) {
    _selectedRecipientId.value = rid
    store.setRecipient(rid)
  }

  fun onToggleEnabled(enabled: Boolean) {
    val sel = _selectedRecipientId.value
    if (sel == null) return
    _enabled.value = enabled
    store.setEnabled(enabled)
  }

  fun onChangeGesture(typeValue: Int) {
    _exitGesture.value = typeValue
    store.setGesture(AccessibilityModeExitGestureType.fromValue(typeValue))
  }

  fun onSetExitGestureTimeoutMs(value: Int) {
    _exitGestureTimeoutMs.value = value
    store.setExitGestureTimeoutMs(value)
  }

  fun onSetExitGesturePointerTimeoutMs(value: Int) {
    _exitGesturePointerTimeoutMs.value = value
    store.setExitGesturePointerTimeoutMs(value)
  }

  fun onSetExitHeaderHeightDp(value: Int) {
    _exitHeaderHeightDp.value = value
    store.setExitHeaderHeightDp(value)
  }

  fun onSetExitTripleTapIntervalMs(value: Int) {
    _exitTripleTapIntervalMs.value = value
    store.setExitTripleTapIntervalMs(value)
  }

  fun onSetExitConfirmTimeoutMs(value: Int) {
    _exitConfirmTimeoutMs.value = value
    store.setExitConfirmTimeoutMs(value)
  }

  fun onSetKioskEnabled(enabled: Boolean) {
    viewModelScope.launch {
      val ok = store.requestKioskEnabled(enabled)
      _kioskEnabled.value = ok && enabled
    }
  }

}
