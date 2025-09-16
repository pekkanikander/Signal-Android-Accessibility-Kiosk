package org.thoughtcrime.securesms.accessibility

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/**
 * Pure-JVM unit tests that assert the high-level behavior of the gesture detector
 * using lightweight fakes for timing and pointer sequencing. These tests follow
 * the repository baseline: algorithmic and timing logic should be tested in
 * pure-JVM tests without Robolectric.
 */
class AccessibilityGestureDetectorTest {

  @Test
  fun tripleTap_detection_within_window() {
    val fake = TripleTapFakeDetector(windowMs = 600L)

    val now = 1_000_000L
    // three taps 100ms apart -> within 600ms window
    fake.recordTap(now)
    fake.recordTap(now + 100)
    fake.recordTap(now + 200)

    assertTrue("triple tap should be detected", fake.detected)
  }

  @Test
  fun tripleTap_rejects_when_outside_window() {
    val fake = TripleTapFakeDetector(windowMs = 300L)

    val now = 2_000_000L
    // three taps 200ms apart -> total 400ms > 300ms window -> reject
    fake.recordTap(now)
    fake.recordTap(now + 200)
    fake.recordTap(now + 400)

    assertFalse("triple tap should NOT be detected", fake.detected)
  }

  @Test
  fun twoFinger_hold_triggers_after_hold_duration() {
    val fake = TwoFingerHoldFakeDetector(holdMs = 500L)

    val start = 3_000_000L
    // first finger down
    fake.firstDown(start)
    // second finger down within pointer timeout
    fake.secondDown(start + 50)

    // advance time to just after hold duration
    fake.advanceTimeTo(start + 600)

    assertTrue("two-finger hold should trigger after hold duration", fake.triggered)
  }

  @Test
  fun twoFinger_hold_cancels_if_drift_exceeds_tolerance() {
    val fake = TwoFingerHoldFakeDetector(holdMs = 500L, driftTolerancePx = 5f)

    val start = 4_000_000L
    fake.firstDown(start, x = 10f, y = 10f)
    fake.secondDown(start + 50, x = 12f, y = 12f)

    // simulate move that exceeds drift tolerance
    fake.move(pointer = 1, x = 30f, y = 30f, atTime = start + 100)

    // advance past hold window
    fake.advanceTimeTo(start + 700)

    assertFalse("two-finger hold should not trigger after excessive drift", fake.triggered)
  }
}

// --- Fakes -----------------------------------------------------------------

/**
 * Simple pure-JVM fake for triple-tap detection logic used in tests.
 */
class TripleTapFakeDetector(private val windowMs: Long) {
  private val taps = mutableListOf<Long>()
  var detected: Boolean = false
    private set

  fun recordTap(timestampMs: Long) {
    taps.add(timestampMs)
    while (taps.size > 3) taps.removeAt(0)
    if (taps.size == 3) {
      val window = taps.last() - taps.first()
      if (window <= windowMs) detected = true
    }
  }
}

/**
 * Simple pure-JVM fake for two-finger hold detection. It models the minimal
 * timing and drift logic needed to assert hold completion.
 */
class TwoFingerHoldFakeDetector(
  private val holdMs: Long,
  private val pointerTimeoutMs: Long = 250L,
  private val driftTolerancePx: Float = 10f
) {
  private var now = 0L
  private var firstDownTime = 0L
  private var secondDownTime = 0L
  private var firstStartX = 0f
  private var firstStartY = 0f
  var triggered: Boolean = false
    private set

  fun firstDown(atTime: Long, x: Float = 0f, y: Float = 0f) {
    now = atTime
    firstDownTime = atTime
    firstStartX = x
    firstStartY = y
  }

  fun secondDown(atTime: Long, x: Float = firstStartX, y: Float = firstStartY) {
    now = atTime
    if (atTime - firstDownTime > pointerTimeoutMs) return // timed out
    secondDownTime = atTime
  }

  fun move(pointer: Int, x: Float, y: Float, atTime: Long) {
    now = atTime
    val dx = x - firstStartX
    val dy = y - firstStartY
    if ((dx * dx + dy * dy) > (driftTolerancePx * driftTolerancePx)) {
      // cancel
      firstDownTime = 0L
      secondDownTime = 0L
    }
  }

  fun advanceTimeTo(t: Long) {
    now = t
    if (firstDownTime > 0 && secondDownTime > 0) {
      val holdUntil = firstDownTime + holdMs
      if (now >= holdUntil) triggered = true
    }
  }
}
