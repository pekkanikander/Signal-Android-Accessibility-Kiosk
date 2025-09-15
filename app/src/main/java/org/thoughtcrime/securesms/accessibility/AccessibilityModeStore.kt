/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.accessibility
import android.app.Activity
import android.content.Context
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.Flow
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * Store for Accessibility Mode state management.
 * Provides a clean interface to Accessibility Mode settings.
 */
interface AccessibilityModeStore {
  fun state(): kotlinx.coroutines.flow.Flow<AccessibilityModeState>
  fun current(): AccessibilityModeState
  fun setEnabled(enabled: Boolean, recipientId: RecipientId?)
}
/**
 * Immutable Accessibility Mode state.
 */
data class AccessibilityModeState(
  val enabled: Boolean,
  val recipientId: RecipientId?,
)
/**
 * Implementation using existing SignalStore.accessibilityMode.
 */
class SignalAccessibilityModeStore : AccessibilityModeStore {
  override fun state(): kotlinx.coroutines.flow.Flow<AccessibilityModeState> {
    return flowOf(current())
  }

  override fun current(): AccessibilityModeState {
    val ridLong = SignalStore.accessibilityMode.accessibilityRecipientId
    val rid = if (ridLong > 0) RecipientId.from(ridLong) else null
    return AccessibilityModeState(
      enabled = SignalStore.accessibilityMode.isAccessibilityModeEnabled,
      recipientId = rid
    )
  }

  override fun setEnabled(enabled: Boolean, recipientId: RecipientId?) {
    SignalStore.accessibilityMode.isAccessibilityModeEnabled = enabled
    SignalStore.accessibilityMode.accessibilityRecipientId = recipientId?.toLong() ?: -1L
  }

  // Advanced option: suppress notifications while accessibility mode is active
  var suppressNotifications: Boolean
    get() = SignalStore.accessibilityMode.suppressNotifications
    set(value) { SignalStore.accessibilityMode.suppressNotifications = value }
}
