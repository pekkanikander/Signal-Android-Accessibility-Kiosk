/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.accessibility

import android.app.Dialog
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import org.thoughtcrime.securesms.R

/**
 * Confirmation dialog for Accessibility Mode exit gesture.
 *
 * Intent and UX rationale
 * - Primary goal: prevent assisted users from accidentally exiting Accessibility Mode.
 * - The exit gesture is intended not to be discoverable for assisting users,
 *   but an assisted user may still trigger it unintentionally.
 *   If that happens, the dialog should not force a decision on a confused user;
 *   instead, it goes away on its own after a short timeout.
 *
 * Behavior (by design)
 * - Presents a clear "Go to settings" action for the assisting user.
 * - Is cancelable (including outside-tap), and also auto-dismisses after a short timeout.
 *   This ensures that if an assisted user reaches the dialog by accident, it will disappear
 *   quickly without requiring them to choose between actions they may not understand.
 *
 * Notes
 * - The timeout is not a failure path; it is an explicit UX choice for this kiosk-like flow.
 * - Duplicate dialogs are guarded at the callsite to avoid stacking on rapid re-triggers.
 */
class AccessibilityModeExitConfirmationDialog : DialogFragment() {

  companion object {
    const val TAG = "AccessibilityModeExitConfirmationDialog"

    private const val ARG_TIMEOUT_MS = "timeout_ms"

    fun newInstance(timeoutMs: Long): AccessibilityModeExitConfirmationDialog {
      val dlg = AccessibilityModeExitConfirmationDialog()
      val args = Bundle()
      args.putLong(ARG_TIMEOUT_MS, timeoutMs)
      dlg.arguments = args
      return dlg
    }
  }

  private var timer: CountDownTimer? = null

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val timeout = arguments?.getLong(ARG_TIMEOUT_MS) ?: 5000L

    val builder = AlertDialog.Builder(requireContext())
      .setTitle(R.string.AccessibilityModeExitConfirmationDialog__confirm_exit)
      .setMessage(R.string.AccessibilityModeExitConfirmationDialog__open_accessibility_settings_question)
      .setPositiveButton(R.string.AccessibilityModeExitConfirmationDialog__go_to_settings) { _, _ ->
        (activity as? AccessibilityModeActivity)?.navigateToSettings()
      }
      .setNegativeButton(android.R.string.cancel, null)

    val dialog = builder.create()
    dialog.setCanceledOnTouchOutside(true)

    // Ensure buttons are visible regardless of theme (some themes may render button text hard to see)
    dialog.setOnShowListener {
      try {
        val positive: Button? = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        val negative: Button? = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
        val color = ContextCompat.getColor(requireContext(), org.thoughtcrime.securesms.R.color.signal_colorOnSurface)
        positive?.setTextColor(color)
        negative?.setTextColor(color)
        positive?.textSize = 16f
        negative?.textSize = 16f
      } catch (_: Exception) {
        // Ignore; best-effort styling
      }
    }

    // Auto-dismiss after timeout; cancel on destroy
    timer = object : CountDownTimer(timeout, timeout) {
      override fun onTick(millisUntilFinished: Long) {}
      override fun onFinish() {
        if (isAdded && dialog.isShowing) dismiss()
      }
    }.start()

    return dialog
  }

  override fun onDestroyView() {
    try { timer?.cancel() } catch (_: Exception) {}
    timer = null
    super.onDestroyView()
  }
}
