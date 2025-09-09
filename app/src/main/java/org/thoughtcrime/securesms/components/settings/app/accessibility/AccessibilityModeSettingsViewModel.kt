package org.thoughtcrime.securesms.components.settings.app.accessibility

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import android.util.Log
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.accessibility.AccessibilityModeExitGestureType

/**
 * ViewModel for accessibility mode settings screen.
 * Manages state and interactions with SignalStore.accessibilityMode.
 */
class AccessibilityModeSettingsViewModel : ViewModel() {

  private val store = MutableStateFlow(getState())
  val state: StateFlow<AccessibilityModeSettingsState> = store

  fun refreshState() {
    store.update { getState() }
  }

  fun setAccessibilityMode(enabled: Boolean) {
    SignalStore.accessibilityMode.isAccessibilityModeEnabled = enabled
    store.update { getState() }
  }

  fun setThreadId(threadId: Long) {
    Log.d("AccessibilityViewModel", "setThreadId called with: $threadId")
    SignalStore.accessibilityMode.accessibilityThreadId = threadId
    Log.d("AccessibilityViewModel", "Updated SignalStore, new value: ${SignalStore.accessibilityMode.accessibilityThreadId}")
    store.update { getState() }
    Log.d("AccessibilityViewModel", "State updated, current state: ${store.value}")
  }

  fun setExitGestureType(gestureType: AccessibilityModeExitGestureType) {
    SignalStore.accessibilityMode.exitGestureType = gestureType.value
    store.update { getState() }
  }

  fun setExitGestureRequirePin(requirePin: Boolean) {
    SignalStore.accessibilityMode.exitGestureRequirePin = requirePin
    store.update { getState() }
  }

  private fun getState(): AccessibilityModeSettingsState {
    return AccessibilityModeSettingsState(
      isAccessibilityModeEnabled = SignalStore.accessibilityMode.isAccessibilityModeEnabled,
      threadId = SignalStore.accessibilityMode.accessibilityThreadId,
      exitGestureType = AccessibilityModeExitGestureType.fromValue(SignalStore.accessibilityMode.exitGestureType),
      exitGestureRequirePin = SignalStore.accessibilityMode.exitGestureRequirePin
    )
  }
}
