/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.accessibility

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.accessibility.AccessibilityModeRouter
import org.thoughtcrime.securesms.accessibility.AccessibilityModeExitToSettingsGestureDetector
import org.thoughtcrime.securesms.accessibility.IntentFactory

/**
 * Main accessibility interface for Signal conversations.
 *
 * Features:
 * - No back button or navigation options
 * - Large, high-contrast controls
 * - Simplified conversation interface
 * - Accessibility-optimized UI
 */
class AccessibilityModeActivity : AppCompatActivity() {

  companion object {
    private val TAG = Log.tag(AccessibilityModeActivity::class)
  }

  private lateinit var exitGestureDetector: AccessibilityModeExitToSettingsGestureDetector

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_accessibility_mode)

    // Hide action bar to remove back button
    supportActionBar?.hide()

    // Get the selected thread ID from intent
    val selectedThreadId = intent.getLongExtra("selected_thread_id", -1L)

    // Add the accessibility fragment if this is the first creation
    if (savedInstanceState == null) {
      val fragment = AccessibilityModeFragment()

      // Pass the thread ID to the fragment via arguments
      val args = Bundle()
      args.putLong("selected_thread_id", selectedThreadId)
      fragment.arguments = args

      supportFragmentManager.beginTransaction()
        .replace(R.id.fragment_container, fragment)
        .commit()
    }

    // Initialize exit gesture detector
    setupExitGestureDetector()
  }

  private fun setupExitGestureDetector() {
    exitGestureDetector = AccessibilityModeExitToSettingsGestureDetector(this) {
      Log.d(TAG, "Exit gesture triggered, launching confirmation")
      // TODO: Launch confirmation overlay instead of directly going to settings
      // For now, go directly to settings for testing
      startActivity(IntentFactory.settings(this))
    }

    // Attach to the root view
    val rootView = findViewById<View>(android.R.id.content)
    rootView.setOnTouchListener(exitGestureDetector)
  }

  override fun onStart() {
    super.onStart()
    AccessibilityModeRouter.routeIfNeeded(this)
  }
}
