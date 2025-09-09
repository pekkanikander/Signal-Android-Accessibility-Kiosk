package org.thoughtcrime.securesms.testing

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Helpers that replace parts of `testing-tools/accessibility-testing/talkback-control.sh`.
 * These are thin wrappers around uiAutomation shell commands and are intentionally
 * conservative — enabling global accessibility flags but not attempting fragile UI
 * interactions across OEM Settings apps.
 */
object AccessibilitySystemHelpers {
  private const val TAG = "AccessibilitySystemHelpers"

  /**
   * Enable Android's global accessibility flag. This does not guarantee a specific
   * service (e.g., TalkBack) is enabled; it only sets the global switch. Use
   * UiAutomator flows for service-specific toggles if needed.
   */
  fun enableGlobalAccessibility() {
    try {
      val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
      val cmd = "settings put secure accessibility_enabled 1"
      uiAutomation.executeShellCommand(cmd).close()
      Log.i(TAG, "Set accessibility_enabled=1 via shell")
    } catch (e: Exception) {
      Log.w(TAG, "Failed to enable global accessibility via shell", e)
    }
  }

  /**
   * Disable Android's global accessibility flag.
   */
  fun disableGlobalAccessibility() {
    try {
      val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
      val cmd = "settings put secure accessibility_enabled 0"
      uiAutomation.executeShellCommand(cmd).close()
      Log.i(TAG, "Set accessibility_enabled=0 via shell")
    } catch (e: Exception) {
      Log.w(TAG, "Failed to disable global accessibility via shell", e)
    }
  }
}


