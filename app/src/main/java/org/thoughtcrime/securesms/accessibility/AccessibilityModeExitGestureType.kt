/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.accessibility

/**
 * Defines the available exit gesture types for Accessibility Mode.
 *
 * Production: Two-finger header hold. Debug: Triple-tap header.
 */
enum class AccessibilityModeExitGestureType(val value: Int, val displayName: String) {
  TWO_FINGER_HEADER_HOLD(1, "Two-finger header hold"),
  TRIPLE_TAP_DEBUG(3, "Triple tap on header (debug)");

  companion object {
    fun fromValue(value: Int): AccessibilityModeExitGestureType {
      return values().find { it.value == value } ?: TWO_FINGER_HEADER_HOLD
    }
  }
}
