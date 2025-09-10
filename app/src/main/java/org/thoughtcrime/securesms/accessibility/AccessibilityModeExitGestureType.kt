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
  OPPOSITE_CORNERS_HOLD(0, "Opposite corners hold (legacy)"), // to be removed
  TWO_FINGER_HEADER_HOLD(1, "Two-finger header hold"),
  SINGLE_FINGER_EDGE_DRAG_HOLD(2, "Single-finger edge drag hold (legacy)"), // to be removed
  TRIPLE_TAP_DEBUG(3, "Triple tap on header (debug)");

  companion object {
    fun fromValue(value: Int): AccessibilityModeExitGestureType {
      return values().find { it.value == value } ?: TWO_FINGER_HEADER_HOLD
    }
  }
}
