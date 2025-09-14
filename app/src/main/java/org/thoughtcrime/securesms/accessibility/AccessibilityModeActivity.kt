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

import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.thoughtcrime.securesms.conversation.ConversationTitleView
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId

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
  private var overlayView: View? = null

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

    bindHeader(selectedThreadId)

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
      headerBoundsProvider = { computeHeaderBounds() },
      onTriggered = {
        Log.d(TAG, "Exit gesture triggered, launching confirmation")
        showExitConfirmationOverlay()
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
    this.overlayView = overlayView

    // Add debug info to logcat
    Log.d(TAG, "Exit gesture detector initialized and attached to overlay view")
    Log.d(TAG, "Root view bounds: ${rootView.width}x${rootView.height}")
  }

  private fun computeHeaderBounds(): android.graphics.Rect {
    val header = findViewById<View>(R.id.accessibility_title_view)
    val measured = header?.height ?: 0
    val heightPx = if (measured > 0) measured else {
      val heightDp = org.thoughtcrime.securesms.keyvalue.SignalStore.accessibilityMode.exitHeaderHeightDp
      (resources.displayMetrics.density * heightDp).toInt()
    }
    val width = resources.displayMetrics.widthPixels
    return android.graphics.Rect(0, 0, width, heightPx)
  }

  private fun bindHeader(threadId: Long) {
    val header = findViewById<View>(R.id.accessibility_title_view) as? ConversationTitleView ?: return
    if (threadId <= 0L) return

    lifecycleScope.launch {
      val recipient: Recipient? = withContext(Dispatchers.IO) {
        val rid: RecipientId? = SignalDatabase.threads.getRecipientIdForThreadId(threadId)
        rid?.let { Recipient.resolved(it) }
      }
      recipient?.let { r ->
        header.setTitle(Glide.with(header), r)
      }
    }
  }

  private fun showExitConfirmationOverlay() {
    val timeoutMs = org.thoughtcrime.securesms.keyvalue.SignalStore.accessibilityMode.exitConfirmTimeoutMs.toLong()
    val dialog = AccessibilityModeExitConfirmationDialog.newInstance(timeoutMs)
    dialog.show(supportFragmentManager, AccessibilityModeExitConfirmationDialog.TAG)
  }

  fun navigateToSettings() {
    startActivity(IntentFactory.settings(this))
  }

  override fun onStart() {
    super.onStart()
    Log.d(TAG, "AccessibilityModeActivity.onStart() called")
    AccessibilityModeRouter.routeIfNeeded(this)
    Log.d(TAG, "AccessibilityModeActivity.onStart() completed")
  }

  override fun onDestroy() {
    super.onDestroy()
    try {
      exitGestureDetector.dispose()
    } catch (_: Exception) {}
    try {
      val rootView = findViewById<View>(android.R.id.content) as? ViewGroup
      overlayView?.let { rootView?.removeView(it) }
    } catch (_: Exception) {}
  }
}
