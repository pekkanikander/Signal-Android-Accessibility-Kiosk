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
  suspend fun requestKioskEnabled(enabled: Boolean): Boolean
  fun setExitGestureTimeoutMs(value: Int)
  fun setExitGesturePointerTimeoutMs(value: Int)
  fun setExitHeaderHeightDp(value: Int)
  fun setExitTripleTapIntervalMs(value: Int)
  fun setExitConfirmTimeoutMs(value: Int)
}

/** Immutable snapshot of Accessibility Mode state. */
data class AccessibilityModeState(
  val enabled: Boolean,
  val recipientId: RecipientId?,
  val gestureType: AccessibilityModeExitGestureType,
  val kioskEnabled: Boolean,
  val exitGestureTimeoutMs: Int,
  val exitGesturePointerTimeoutMs: Int,
  val exitHeaderHeightDp: Int,
  val exitTripleTapIntervalMs: Int,
  val exitConfirmTimeoutMs: Int
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
    kioskEnabled = SignalStore.accessibilityMode.isKioskEnabled,
    exitGestureTimeoutMs = SignalStore.accessibilityMode.exitGestureTimeoutMs,
    exitGesturePointerTimeoutMs = SignalStore.accessibilityMode.exitGesturePointerTimeoutMs,
    exitHeaderHeightDp = SignalStore.accessibilityMode.exitHeaderHeightDp,
    exitTripleTapIntervalMs = SignalStore.accessibilityMode.exitTripleTapIntervalMs,
    exitConfirmTimeoutMs = SignalStore.accessibilityMode.exitConfirmTimeoutMs
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

  override suspend fun requestKioskEnabled(enabled: Boolean): Boolean {
    val ok = KioskHelperClient.requestKioskEnabled(enable = enabled)
    if (ok) {
      SignalStore.accessibilityMode.isKioskEnabled = enabled
    } else {
      SignalStore.accessibilityMode.isKioskEnabled = false
    }
    refresh()
    return ok
  }

  override fun setExitGestureTimeoutMs(value: Int) {
    SignalStore.accessibilityMode.exitGestureTimeoutMs = value
    refresh()
  }

  override fun setExitGesturePointerTimeoutMs(value: Int) {
    SignalStore.accessibilityMode.exitGesturePointerTimeoutMs = value
    refresh()
  }

  override fun setExitHeaderHeightDp(value: Int) {
    SignalStore.accessibilityMode.exitHeaderHeightDp = value
    refresh()
  }

  override fun setExitTripleTapIntervalMs(value: Int) {
    SignalStore.accessibilityMode.exitTripleTapIntervalMs = value
    refresh()
  }

  override fun setExitConfirmTimeoutMs(value: Int) {
    SignalStore.accessibilityMode.exitConfirmTimeoutMs = value
    refresh()
  }

  private fun refresh() {
    internalState.update { readState() }
  }
}
