/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.accessibility

import android.content.Context
import android.content.Intent
import org.thoughtcrime.securesms.MainActivity

/**
 * Factory for creating Accessibility Mode related Intents with proper flags.
 */
object IntentFactory {

  /**
   * Creates Intent for Accessibility Mode root activity.
   */
  fun accessibilityRoot(context: Context, threadId: Long?): Intent {
    return Intent(context, AccessibilityModeActivity::class.java)
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
      .putExtra("selected_thread_id", threadId ?: -1L)
  }

  /**
   * Creates Intent for Normal Mode root activity.
   */
  fun normalRoot(context: Context): Intent {
    return Intent(context, MainActivity::class.java)
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
  }

  /**
   * Creates Intent for Settings activity.
   */
  fun settings(context: Context): Intent {
    return Intent(context, org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity::class.java)
  }
}
