/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.accessibility

import android.app.Activity
import android.content.Context
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * Central router for Accessibility Mode transitions.
 * Handles all routing decisions and task rebasing.
 */
object AccessibilityModeRouter {

  private val TAG = Log.tag(AccessibilityModeRouter::class)

  // Store instance - will be initialized in Application.onCreate
  lateinit var store: AccessibilityModeStore

  /**
   * Call from MainActivity.onStart() and AccessibilityModeActivity.onStart().
   * Routes to the correct mode if needed.
   */
  fun routeIfNeeded(host: Activity) {
    val state = store.current()
    val isAccessibility = state.enabled

    when (host) {
      is AccessibilityModeActivity -> {
        val hostRecipient: RecipientId? = host.intent.getParcelableExtra("selected_recipient_id")

        if (!isAccessibility) {
          Log.d(TAG, "Accessibility Mode disabled, rebasing to Normal Mode")
          rebaseToNormal(host)
        } else if (state.recipientId != hostRecipient) {
          Log.d(TAG, "Recipient ID mismatch, rebasing to correct Accessibility Mode")
          rebaseToAccessibility(host, state.recipientId)
        }
      }

      is MainActivity -> {
        if (isAccessibility) {
          Log.d(TAG, "Accessibility Mode enabled, rebasing to Accessibility Mode")
          rebaseToAccessibility(host, state.recipientId)
        }
      }

      else -> {
        // No-op for other activities
      }
    }
  }

  /**
   * Call directly from Settings when user toggles mode for immediate rebase.
   */
  fun rebaseToAccessibility(context: Context, recipientId: RecipientId?) {
    Log.d(TAG, "Rebasing to Accessibility Mode with recipientId: $recipientId")
    context.startActivity(IntentFactory.accessibilityRoot(context, recipientId))
    if (context is Activity) {
      context.overridePendingTransition(0, 0)
    }
  }

  fun rebaseToNormal(context: Context) {
    Log.d(TAG, "Rebasing to Normal Mode")
    context.startActivity(IntentFactory.normalRoot(context))
    if (context is Activity) {
      context.overridePendingTransition(0, 0)
    }
  }
}
