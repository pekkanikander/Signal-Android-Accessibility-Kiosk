/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.accessibility

import android.content.Context
import android.content.Intent
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * Factory for creating Accessibility Mode related Intents with proper flags.
 */
object IntentFactory {

  /**
   * Creates Intent for Accessibility Mode root activity.
   */
  fun accessibilityRoot(context: Context, recipientId: RecipientId?): Intent {
    return Intent(context, AccessibilityModeActivity::class.java)
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
      .apply {
        if (recipientId != null) putExtra("selected_recipient_id", recipientId)
      }
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
