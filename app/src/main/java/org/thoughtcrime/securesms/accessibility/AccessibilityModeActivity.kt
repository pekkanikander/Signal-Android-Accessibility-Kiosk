/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.accessibility

import android.graphics.Rect
import android.os.Bundle
import android.view.View
// import android.view.ViewGroup (removed)
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import org.signal.core.util.logging.Log
import androidx.core.content.IntentCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.accessibility.AccessibilityModeRouter
import org.thoughtcrime.securesms.accessibility.AccessibilityModeExitGestureDetector
import org.thoughtcrime.securesms.components.settings.app.accessibility.AccessibilityModeSettingsViewModel
import org.thoughtcrime.securesms.accessibility.IntentFactory
import org.thoughtcrime.securesms.conversation.ConversationTitleView
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.keyvalue.SignalStore

import com.bumptech.glide.Glide

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
    private val TAG = Log.tag(AccessibilityModeActivity::class.java)
  }

  private lateinit var exitGestureDetector: AccessibilityModeExitGestureDetector
  private val settingsViewModel: AccessibilityModeSettingsViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Log.d(TAG, "AccessibilityModeActivity.onCreate() called")
    setContentView(R.layout.activity_accessibility_mode)
    Log.d(TAG, "Content view set")

    // Hide action bar to remove back button
    supportActionBar?.hide()

    val selectedRecipientId: RecipientId? = IntentCompat.getParcelableExtra(intent, "selected_recipient_id", RecipientId::class.java)
    if (selectedRecipientId != null) {
      bindHeader(selectedRecipientId)
      if (savedInstanceState == null) {
        lifecycleScope.launch {
          val threadId: Long = withContext(Dispatchers.IO) {
            val recipient = Recipient.resolved(selectedRecipientId)
            SignalDatabase.threads.getOrCreateThreadIdFor(recipient) ?: -1L
          }
          val fragment = AccessibilityModeFragment().apply {
            arguments = Bundle().apply { putLong("selected_thread_id", threadId) }
          }
          supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
        }
      }
      setupExitGestureDetector()
      return
    }

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
    exitGestureDetector = AccessibilityModeExitGestureDetector(
      this,
      headerBoundsProvider = { computeHeaderBounds() },
      onTriggered = {
        Log.d(TAG, "Exit gesture triggered, launching confirmation")
        showExitConfirmationOverlay()
      }
    )
  }
  override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
    // Passive observe for the exit detector; return value ignored to keep Transparent policy
    if (::exitGestureDetector.isInitialized) {
      try { exitGestureDetector.onTouch(null, ev) } catch (_: Exception) {}
    }
    return super.dispatchTouchEvent(ev)
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

  private fun bindHeader(recipientId: RecipientId) {
    val header = findViewById<View>(R.id.accessibility_title_view) as? ConversationTitleView ?: return
    lifecycleScope.launch {
      val recipient: Recipient? = withContext(Dispatchers.IO) { Recipient.resolved(recipientId) }
      recipient?.let { r -> header.setTitle(Glide.with(header), r) }
    }
  }

  private fun showExitConfirmationOverlay() {
    val timeoutMs = org.thoughtcrime.securesms.keyvalue.SignalStore.accessibilityMode.exitConfirmTimeoutMs.toLong()
    val fm = supportFragmentManager
    val existing = fm.findFragmentByTag(AccessibilityModeExitConfirmationDialog.TAG)
    if (existing == null) {
      AccessibilityModeExitConfirmationDialog.newInstance(timeoutMs)
        .show(fm, AccessibilityModeExitConfirmationDialog.TAG)
    } else {
      Log.d(TAG, "Exit confirmation already shown; skipping duplicate")
    }
  }

  fun navigateToSettings() {
    startActivity(IntentFactory.settings(this))
  }

  private fun readExitGestureConfig(): ExitGestureConfig {
    val s = SignalStore.accessibilityMode
    return ExitGestureConfig(
      type = AccessibilityModeExitGestureType.fromValue(s.exitGestureType),
      totalTimeoutMs = s.exitGestureTimeoutMs,
      tripleTapGapMs = s.exitTripleTapIntervalMs,
      chordSecondFingerTimeoutMs = s.exitGesturePointerTimeoutMs,
      headerHeightDp = s.exitHeaderHeightDp
    )
  }

  override fun onStart() {
    super.onStart()
    Log.d(TAG, "AccessibilityModeActivity.onStart() called")
    AccessibilityModeRouter.routeIfNeeded(this)
    Log.d(TAG, "AccessibilityModeActivity.onStart() completed")

    // Snapshot current settings and apply to detector on every (re)start.
    val cfg = readExitGestureConfig()
    exitGestureDetector.applyConfig(cfg)
    exitGestureDetector.updateSelectedGesture(cfg.type, force = true)
  }

  // (Removed debug lifecycle logging overrides)

  override fun onDestroy() {
    try {
      exitGestureDetector.dispose()
    } catch (_: Exception) {}
    super.onDestroy()
  }
}
