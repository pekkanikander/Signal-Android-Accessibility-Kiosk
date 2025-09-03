/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.accessibility

/**
 * Defines the available exit gesture types for Accessibility Mode.
 *
 * Based on ChatGPT-5's exit gesture implementation plan:
 * - OPPOSITE_CORNERS_HOLD: Very strict, hard to trigger accidentally
 * - TWO_FINGER_HEADER_HOLD: More learnable but still intentional
 * - SINGLE_FINGER_EDGE_DRAG_HOLD: Easier for testing, single finger with drag to edge
 */
enum class AccessibilityModeExitGestureType(val value: Int, val displayName: String) {
  OPPOSITE_CORNERS_HOLD(0, "Opposite corners hold (strict)"),
  TWO_FINGER_HEADER_HOLD(1, "Two-finger header hold"),
  SINGLE_FINGER_EDGE_DRAG_HOLD(2, "Single-finger edge drag hold (easier)");

  companion object {
    fun fromValue(value: Int): AccessibilityModeExitGestureType {
      return values().find { it.value == value } ?: OPPOSITE_CORNERS_HOLD
    }
  }
}
