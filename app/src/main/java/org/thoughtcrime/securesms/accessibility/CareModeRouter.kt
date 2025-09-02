/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.accessibility

import android.app.Activity
import android.content.Context
import org.thoughtcrime.securesms.MainActivity
import org.signal.core.util.logging.Log

/**
 * Central router for Care Mode transitions.
 * Handles all routing decisions and task rebasing.
 */
object CareModeRouter {
  
  private val TAG = Log.tag(CareModeRouter::class)
  
  // Store instance - will be initialized in Application.onCreate
  lateinit var store: CareModeStore

  /**
   * Call from MainActivity.onStart() and AccessibilityModeActivity.onStart().
   * Routes to the correct mode if needed.
   */
  fun routeIfNeeded(host: Activity) {
    val state = store.current()
    val isCare = state.enabled

    when (host) {
      is AccessibilityModeActivity -> {
        // We're in Care Mode - verify we should be here
        val hostThread = host.intent.getLongExtra("selected_thread_id", -1L).takeIf { it > 0 }
        
        if (!isCare) {
          Log.d(TAG, "Care Mode disabled, rebasing to Normal Mode")
          rebaseToNormal(host)
        } else if (state.threadId != hostThread) {
          Log.d(TAG, "Thread ID mismatch, rebasing to correct Care Mode")
          rebaseToCare(host, state.threadId)
        }
      }
      
      is MainActivity -> {
        if (isCare) {
          Log.d(TAG, "Care Mode enabled, rebasing to Care Mode")
          rebaseToCare(host, state.threadId)
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
  fun rebaseToCare(context: Context, threadId: Long?) {
    Log.d(TAG, "Rebasing to Care Mode with threadId: $threadId")
    context.startActivity(IntentFactory.careRoot(context, threadId))
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
