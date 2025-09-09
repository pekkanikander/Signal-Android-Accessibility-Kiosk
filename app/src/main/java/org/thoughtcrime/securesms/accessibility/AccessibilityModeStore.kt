/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.accessibility

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.thoughtcrime.securesms.keyvalue.SignalStore

/**
 * Store for Accessibility Mode state management.
 * Provides a clean interface to Accessibility Mode settings.
 */
interface AccessibilityModeStore {
  fun state(): Flow<AccessibilityModeState>
  fun current(): AccessibilityModeState
  fun setEnabled(enabled: Boolean, threadId: Long?)
}

/**
 * Immutable Accessibility Mode state.
 */
data class AccessibilityModeState(
  val enabled: Boolean,
  val threadId: Long?
)

/**
 * Implementation using existing SignalStore.accessibilityMode.
 */
class SignalAccessibilityModeStore : AccessibilityModeStore {
  override fun state(): Flow<AccessibilityModeState> {
    // For now, return a simple flow that reads current state
    // TODO: Implement proper Flow when SignalStore supports it
    return kotlinx.coroutines.flow.flowOf(current())
  }

  override fun current(): AccessibilityModeState {
    return AccessibilityModeState(
      enabled = SignalStore.accessibilityMode.isAccessibilityModeEnabled,
      threadId = SignalStore.accessibilityMode.accessibilityThreadId.takeIf { it > 0 }
    )
  }

  override fun setEnabled(enabled: Boolean, threadId: Long?) {
    SignalStore.accessibilityMode.isAccessibilityModeEnabled = enabled
    if (threadId != null) {
      SignalStore.accessibilityMode.accessibilityThreadId = threadId
    }
  }
}
