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
 * Lightweight confirmation dialog used when the accessibility exit gesture is triggered.
 * Shows a simple confirmation and auto-dismisses after the provided timeout.
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
      .setTitle(R.string.acc_mode_exit_confirm_title)
      .setMessage(R.string.acc_mode_exit_confirm_message)
      .setPositiveButton(R.string.acc_mode_exit_confirm_positive) { _, _ ->
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
