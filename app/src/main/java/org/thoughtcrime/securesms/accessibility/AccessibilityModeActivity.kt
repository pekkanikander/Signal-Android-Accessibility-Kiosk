/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.accessibility

import android.os.Bundle
import android.content.Intent
import android.content.Context
import android.view.View
import androidx.activity.viewModels
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import org.signal.core.util.logging.Log
import androidx.core.content.IntentCompat
import androidx.core.app.NotificationManagerCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.accessibility.AccessibilityModeExitGestureDetector
import org.thoughtcrime.securesms.components.settings.app.accessibility.AccessibilityModeSettingsViewModel
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.conversation.ConversationTitleView
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.MainActivity
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
class AccessibilityModeActivity : PassphraseRequiredActivity() {

  companion object {
    private val TAG = Log.tag(AccessibilityModeActivity::class.java)

    @JvmStatic
    fun getAccessibilityModeIntent(context: Context): Intent? {
      // Loop prevention: if already in AccessibilityModeActivity, don’t re-launch
      if (context is AccessibilityModeActivity) return null

      // Never open AccessibilityModeActivity from AppSettingsActivity
      if (context is AppSettingsActivity)       return null;

      val s = SignalStore.accessibilityMode
      val ridLong = s.accessibilityRecipientId

      return Intent(context, AccessibilityModeActivity::class.java).apply {
        if (ridLong > 0L) {
          putExtra("selected_recipient_id", RecipientId.from(ridLong))
        }
      }
    }
  }

  private lateinit var exitGestureDetector: AccessibilityModeExitGestureDetector
  private val settingsViewModel: AccessibilityModeSettingsViewModel by viewModels()

  private val openSettings: ActivityResultLauncher<Intent> =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      if (result.resultCode == MainActivity.RESULT_CONFIG_CHANGED) {
        recreate()
      }
    }

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    super.onCreate(savedInstanceState, ready)
    Log.d(TAG, "AccessibilityModeActivity.onCreate() called")

    // Fail-safe: if Accessibility Mode was disabled while we were away, exit to Main.
    if (!SignalStore.accessibilityMode.isAccessibilityModeEnabled) {
      startActivity(MainActivity.clearTop(this))
      finish()
      return
    }

    setContentView(R.layout.activity_accessibility_mode)

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
    // Passive observe at the exit detector; return value ignored to keep Transparent policy
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
    val timeoutMs = SignalStore.accessibilityMode.exitConfirmTimeoutMs.toLong()
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
    openSettings.launch(AppSettingsActivity.home(this))
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

  private fun clearAppNotifications(reason: String) {
    try {
      NotificationManagerCompat.from(this).cancelAll()
      Log.d(TAG, "Cleared notifications ($reason)")
    } catch (t: Throwable) {
      Log.w(TAG, "clearAppNotifications failed: ${t.message}")
    }
  }

  override fun onStart() {
    super.onStart()
    // Snapshot current settings and apply to detector on every (re)start.
    val cfg = readExitGestureConfig()
    exitGestureDetector.applyConfig(cfg)
    exitGestureDetector.updateSelectedGesture(cfg.type, force = true)
  }

/**
 * Handover point: the caregiver gives the device to the assisted user.
 * From here on, the UI must be distraction-free.
 *
 * We first attempt to enter Lock Task (if the helper has prepared the allowlist),
 * then clear any app notifications so there are no badges/toasts at hand-off.
 * While Accessibility Mode is active, Signal remains pinned;
 * when the user exits it, normal system behavior resumes.
 */
override fun onResume() {
  super.onResume()
  // Attempt to enter Lock Task if helper has prepared allowlist. Safe to try; ignore if not allowed.
  try { startLockTask() } catch (_: IllegalStateException) {
    // Not allowlisted yet (helper didn’t prepare). Intentionally silent to avoid surprises.
  }
  clearAppNotifications("entering Accessibility Mode")
}

/**
 * Handover back: the caregiver has performed the exit gesture and confirmed it.
 *
 * Notifications may become visible/audible again after kiosk is lifted;
 * to avoid any burst of backlog, clear Signal's notifications before unpinning.
 */
override fun onPause() {
  // Clear before unpinning to avoid a visible burst when returning to Settings.
  clearAppNotifications("exiting Accessibility Mode")
  // Optional unpin on pause; we also unpin explicitly on Exit. Safe to try and ignore failures.
  try { stopLockTask() } catch (_: IllegalStateException) { }
  super.onPause()
}

  override fun onDestroy() {
    try {
      exitGestureDetector.dispose()
    } catch (_: Exception) {}
    super.onDestroy()
  }
}
