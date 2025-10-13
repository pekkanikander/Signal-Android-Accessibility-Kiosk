/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.accessibility

import android.os.Bundle
import android.content.Intent
import android.content.Context
import android.graphics.Rect
import android.view.View
import androidx.activity.viewModels
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.IntentCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.lifecycleScope

import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.PassphraseRequiredActivity
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

    val selectedRecipientId: RecipientId? = IntentCompat.getParcelableExtra(intent, "selected_recipient_id", RecipientId::class.java)
    val rid: RecipientId? = selectedRecipientId ?: SignalStore.accessibilityMode.accessibilityRecipientId.let { if (it > 0) RecipientId.from(it) else null }

    // Fail-safe: if, while we were away,  Accessibility Mode was disabled or no recipient id was available, exit to Main.
    if (!SignalStore.accessibilityMode.isAccessibilityModeEnabled || rid == null) {
      Log.e(TAG, "Accessibility Mode disabled or no recipient id available for AccessibilityModeActivity")
      startActivity(MainActivity.clearTop(this))
      finish()
      return
    }

    setContentView(R.layout.activity_accessibility_mode)

    // Hide action bar to remove back button
    supportActionBar?.hide()

    bindHeader(rid)

    if (savedInstanceState == null) {
      val fragment = AccessibilityModeFragment().apply {
        arguments = Bundle().apply { putParcelable("selected_recipient_id", rid) }
      }
      supportFragmentManager.beginTransaction()
        .replace(R.id.fragment_container, fragment)
        .commit()
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
    observeExitGestureConfig()
  }
  override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
    // Passive observe at the exit detector; return value ignored to keep Transparent policy
    if (::exitGestureDetector.isInitialized) {
      try { exitGestureDetector.onTouch(null, ev) } catch (_: Exception) {}
    }
    return super.dispatchTouchEvent(ev)
  }

  private fun computeHeaderBounds(): Rect {
    val win = android.graphics.Rect()
    window.decorView.getWindowVisibleDisplayFrame(win)

    val density = resources.displayMetrics.density
    val headerView = findViewById<View>(R.id.accessibility_title_view)
    val configuredDp = SignalStore.accessibilityMode.exitHeaderHeightDp

    val headerPx = when {
      configuredDp > 0                           -> (configuredDp * density).toInt()
      headerView?.height?.let { it > 0 } == true -> headerView.height
      else                                       -> (200f * density).toInt()
    }

    val bottom = (win.top + headerPx).coerceAtMost(win.bottom)
    Log.d(TAG, "Header bounds: left=${win.left}, top=${win.top}, right=${win.right}, bottom=$bottom (cfgDp=$configuredDp, px=$headerPx)")
    return Rect(win.left, win.top, win.right, bottom)
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

  private fun observeExitGestureConfig() {
    lifecycleScope.launch {
      repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
        SignalAccessibilityModeStore.state
          .map { st ->
            ExitGestureConfig(
              type = st.gestureType,
              totalTimeoutMs = st.exitGestureTimeoutMs,
              tripleTapGapMs = st.exitTripleTapIntervalMs,
              chordSecondFingerTimeoutMs = st.exitGesturePointerTimeoutMs,
              headerHeightDp = st.exitHeaderHeightDp
            )
          }
          .distinctUntilChanged()
          .collectLatest { cfg ->
            if (::exitGestureDetector.isInitialized) {
              exitGestureDetector.applyConfig(cfg)
              Log.d(TAG, "Applied updated exit gesture config: $cfg")
            }
          }
      }
    }
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
