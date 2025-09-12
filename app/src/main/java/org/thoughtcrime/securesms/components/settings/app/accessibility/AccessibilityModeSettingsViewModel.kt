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

// Small store wrapper to make testing easier
interface AccessibilityModeStore {
  val threadIdFlow: Flow<Long?>
  val enabledFlow: Flow<Boolean>
  val exitGestureFlow: Flow<Int>

  var selectedThreadId: Long?
  var enabled: Boolean
  var exitGestureTypeValue: Int
}

class SignalAccessibilityModeStore : AccessibilityModeStore {
  override val threadIdFlow: Flow<Long?> = flow { emit(SignalStore.accessibilityMode.accessibilityThreadId.takeIf { it > 0 }) }
  override val enabledFlow: Flow<Boolean> = flow { emit(SignalStore.accessibilityMode.isAccessibilityModeEnabled) }
  override val exitGestureFlow: Flow<Int> = flow { emit(SignalStore.accessibilityMode.exitGestureType) }

  override var selectedThreadId: Long?
    get() = SignalStore.accessibilityMode.accessibilityThreadId.takeIf { it > 0 }
    set(value) { SignalStore.accessibilityMode.accessibilityThreadId = value ?: -1L }

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
  private val _selectedThreadId = MutableStateFlow(store.selectedThreadId)
  private val _enabled = MutableStateFlow(store.enabled)
  private val _exitGesture = MutableStateFlow(store.exitGestureTypeValue)

  private val selectedThreadIdFlow = _selectedThreadId.asStateFlow()
  private val enabledFlow = _enabled.asStateFlow()
  private val exitGestureFlow = _exitGesture.asStateFlow()

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
              limit = 50L,
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

    // Keep persisted store values synced into the in-memory state flows
//    store.threadIdFlow.onEach { _selectedThreadId.value = it }.launchIn(viewModelScope)
//    store.enabledFlow.onEach { _enabled.value = it }.launchIn(viewModelScope)
//    store.exitGestureFlow.onEach { _exitGesture.value = it }.launchIn(viewModelScope)

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

    // If the selected thread is no longer present, clear selection and force disabled.
    combine(conversationsFlow, selectedThreadIdFlow) { conversations, sel ->
      Pair(conversations, sel)
    }.onEach { (conversations, sel) ->
      if (sel != null && !conversations.contains(sel)) {
        _selectedThreadId.value = null
        store.selectedThreadId = null
        if (_enabled.value) {
          _enabled.value = false
          store.enabled = false
        }
      }
    }.launchIn(viewModelScope)
  }

  // Commands
  fun onSelectConversation(threadId: Long) {
    _selectedThreadId.value = threadId
    store.selectedThreadId = threadId
    // Do not auto-enable; user must toggle explicitly
  }

  fun onToggleEnabled(enabled: Boolean) {
    val sel = _selectedThreadId.value
    if (sel == null) return
    _enabled.value = enabled
    store.enabled = enabled
  }

  fun onChangeGesture(typeValue: Int) {
    _exitGesture.value = typeValue
    store.exitGestureTypeValue = typeValue
  }

}
