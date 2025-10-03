/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.accessibility

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * Accessibility Mode store with observable state.
 * Single source of truth backed by SignalStore.accessibilityMode.
 */
interface AccessibilityModeStore {
  val state: StateFlow<AccessibilityModeState>
  fun setEnabled(enabled: Boolean, recipientId: RecipientId? = state.value.recipientId)
  fun setRecipient(recipientId: RecipientId?)
  fun setGesture(type: AccessibilityModeExitGestureType)
  fun setSuppressNotifications(enabled: Boolean)
}

/** Immutable snapshot of Accessibility Mode state. */
data class AccessibilityModeState(
  val enabled: Boolean,
  val recipientId: RecipientId?,
  val gestureType: AccessibilityModeExitGestureType,
  val suppressNotifications: Boolean
)

/**
 * Singleton implementation. All callers share the same StateFlow and persistence.
 */
object SignalAccessibilityModeStore : AccessibilityModeStore {
  private fun readRecipientId(): RecipientId? {
    val ridLong = SignalStore.accessibilityMode.accessibilityRecipientId
    return if (ridLong > 0) RecipientId.from(ridLong) else null
  }

  private fun readState(): AccessibilityModeState = AccessibilityModeState(
    enabled = SignalStore.accessibilityMode.isAccessibilityModeEnabled,
    recipientId = readRecipientId(),
    gestureType = AccessibilityModeExitGestureType.fromValue(SignalStore.accessibilityMode.exitGestureType),
    suppressNotifications = SignalStore.accessibilityMode.suppressNotifications
  )

  private val internalState: MutableStateFlow<AccessibilityModeState> = MutableStateFlow(readState())

  override val state: StateFlow<AccessibilityModeState> = internalState

  override fun setEnabled(enabled: Boolean, recipientId: RecipientId?) {
    SignalStore.accessibilityMode.isAccessibilityModeEnabled = enabled
    SignalStore.accessibilityMode.accessibilityRecipientId = recipientId?.toLong() ?: -1L
    refresh()
  }

  override fun setRecipient(recipientId: RecipientId?) {
    SignalStore.accessibilityMode.accessibilityRecipientId = recipientId?.toLong() ?: -1L
    refresh()
  }

  override fun setGesture(type: AccessibilityModeExitGestureType) {
    SignalStore.accessibilityMode.exitGestureType = type.value
    refresh()
  }

  override fun setSuppressNotifications(enabled: Boolean) {
    SignalStore.accessibilityMode.suppressNotifications = enabled
    refresh()
  }

  private fun refresh() {
    internalState.update { readState() }
  }
}
