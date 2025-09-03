/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.accessibility

import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
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
    private val TAG = "AccessModeActivity"
  }

  private lateinit var exitGestureDetector: AccessibilityModeExitToSettingsGestureDetector

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Log.d(TAG, "AccessibilityModeActivity.onCreate() called")
    setContentView(R.layout.activity_accessibility_mode)
    Log.d(TAG, "Content view set")

    // Hide action bar to remove back button
    supportActionBar?.hide()

    // Get the selected thread ID from intent
    val selectedThreadId = intent.getLongExtra("selected_thread_id", -1L)
    Log.d(TAG, "Selected thread ID: $selectedThreadId")

    // Add the accessibility fragment if this is the first creation
    if (savedInstanceState == null) {
      Log.d(TAG, "Creating new fragment")
      val fragment = AccessibilityModeFragment()

      // Pass the thread ID to the fragment via arguments
      val args = Bundle()
      args.putLong("selected_thread_id", selectedThreadId)
      fragment.arguments = args

      supportFragmentManager.beginTransaction()
        .replace(R.id.fragment_container, fragment)
        .commit()
      Log.d(TAG, "Fragment transaction committed")
    } else {
      Log.d(TAG, "Using existing fragment from savedInstanceState")
    }

    // Initialize exit gesture detector
    Log.d(TAG, "Setting up exit gesture detector")
    setupExitGestureDetector()
    Log.d(TAG, "AccessibilityModeActivity.onCreate() completed")
  }

    private fun setupExitGestureDetector() {
    exitGestureDetector = AccessibilityModeExitToSettingsGestureDetector(
      context = this,
      headerBoundsProvider = { android.graphics.Rect() }, // Empty rect - can be updated when toolbar bounds are available
      onTriggered = {
        Log.d(TAG, "Exit gesture triggered, launching confirmation")
        // TODO: Launch confirmation overlay instead of directly going to settings
        // For now, go directly to settings for testing
        startActivity(IntentFactory.settings(this))
      }
    )

    // Create a transparent overlay view that sits on top of everything
    val overlayView = View(this).apply {
      layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
      )
      setOnTouchListener(exitGestureDetector)
    }

    // Add the overlay to the root view
    val rootView = findViewById<View>(android.R.id.content) as ViewGroup
    rootView.addView(overlayView)

    // Add debug info to logcat
    Log.d(TAG, "Exit gesture detector initialized and attached to overlay view")
    Log.d(TAG, "Root view bounds: ${rootView.width}x${rootView.height}")
  }

  override fun onStart() {
    super.onStart()
    Log.d(TAG, "AccessibilityModeActivity.onStart() called")
    AccessibilityModeRouter.routeIfNeeded(this)
    Log.d(TAG, "AccessibilityModeActivity.onStart() completed")
  }

  // Temporary debug method - can be called from adb or removed later
  fun debugTriggerGesture() {
    Log.d(TAG, "Debug: Manually triggering gesture")
    startActivity(IntentFactory.settings(this))
  }

  // Debug method to test gesture states
  fun debugGestureState() {
    Log.d(TAG, "Debug gesture state: current_state=${exitGestureDetector.getCurrentState()}")
  }

  // Debug method to simulate edge touch
  fun debugSimulateEdgeTouch() {
    Log.d(TAG, "Debug: Simulating edge touch at (50, 500)")
    // This would require modifying the gesture detector to accept simulated events
    startActivity(IntentFactory.settings(this))
  }
}
