/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.accessibility

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.thoughtcrime.securesms.keyvalue.SignalStore

/**
 * Store for Care Mode state management.
 * Provides a clean interface to Care Mode settings.
 */
interface CareModeStore {
  fun state(): Flow<CareModeState>
  fun current(): CareModeState
  fun setEnabled(enabled: Boolean, threadId: Long?)
}

/**
 * Immutable Care Mode state.
 */
data class CareModeState(
  val enabled: Boolean,
  val threadId: Long?
)

/**
 * Implementation using existing SignalStore.accessibilityMode.
 */
class SignalCareModeStore : CareModeStore {
  override fun state(): Flow<CareModeState> {
    // For now, return a simple flow that reads current state
    // TODO: Implement proper Flow when SignalStore supports it
    return kotlinx.coroutines.flow.flowOf(current())
  }

  override fun current(): CareModeState {
    return CareModeState(
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
