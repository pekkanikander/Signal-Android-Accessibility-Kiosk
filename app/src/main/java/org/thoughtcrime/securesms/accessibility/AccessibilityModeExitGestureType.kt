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
  TripleTap(1, "Triple tap on header (debug)"),
  ChordSlideUp(2, "Chord slide up"),
  ChordPinchOut(3, "Chord pinch out"),
  ChordDial(4, "Chord dial");
  companion object {
    fun fromValue(value: Int): AccessibilityModeExitGestureType {
      return values().find { it.value == value } ?: ChordSlideUp
    }
  }
}
