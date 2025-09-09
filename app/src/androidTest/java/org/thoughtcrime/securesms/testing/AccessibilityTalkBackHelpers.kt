package org.thoughtcrime.securesms.testing

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry

object AccessibilityTalkBackHelpers {
  private const val TAG = "AccessibilityTalkBackHelpers"

  /**
   * Attempts to enable TalkBack service by adding its package to secure enabled_accessibility_services
   * and enabling accessibility. This is best-effort for emulator images with TalkBack installed.
   */
  fun enableTalkBackIfAvailable() {
    try {
      val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
      // Common TalkBack service ids to try
      val candidates = listOf(
        "com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService",
        "com.android.talkback/.TalkBackService"
      )
      for (service in candidates) {
        try {
          ui.executeShellCommand("settings put secure enabled_accessibility_services $service").close()
          ui.executeShellCommand("settings put secure accessibility_enabled 1").close()
          Log.i(TAG, "Wrote $service to enabled_accessibility_services")
          return
        } catch (e: Exception) {
          Log.d(TAG, "Candidate $service not available: ${e.message}")
        }
      }
    } catch (e: Exception) {
      Log.w(TAG, "Failed to enable TalkBack via shell", e)
    }
  }
}


