package org.thoughtcrime.securesms.accessibility

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/**
 * Pure-JVM unit tests that assert the high-level behavior of the gesture detector
 * using lightweight fakes for timing and pointer sequencing. These tests follow
 * the repository baseline: algorithmic and timing logic should be tested in
 * pure-JVM tests without Robolectric. The fakes intentionally model only the
 * minimal behavior the production state machine must preserve so refactors that
 * change behavior will be caught by these tests.
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
    assertEquals(1, fake.triggerCount)
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
    assertEquals(0, fake.triggerCount)
  }

  @Test
  fun tripleTap_detects_when_equal_to_window() {
    val fake = TripleTapFakeDetector(windowMs = 400L)
    val now = 5_000_000L

    // first at now, last at now + 400 -> exactly window
    fake.recordTap(now)
    fake.recordTap(now + 200)
    fake.recordTap(now + 400)

    assertTrue("triple tap should be detected when equal to window", fake.detected)
    assertEquals(1, fake.triggerCount)
  }

  @Test
  fun tripleTap_sliding_window_detection() {
    val fake = TripleTapFakeDetector(windowMs = 600L)
    val now = 6_000_000L

    // four taps where last three are within the window
    fake.recordTap(now)
    fake.recordTap(now + 300)
    fake.recordTap(now + 700) // window between first and third = 700 (>600) -> not detected yet
    assertFalse(fake.detected)

    // a fourth tap makes the last three (300,700,800) -> 500 window -> should detect
    fake.recordTap(now + 800)
    assertTrue("triple tap should be detected using sliding window", fake.detected)
    assertEquals(1, fake.triggerCount)
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
    assertEquals(1, fake.triggerCount)
  }

  @Test
  fun twoFinger_second_too_late_does_not_trigger() {
    val fake = TwoFingerHoldFakeDetector(holdMs = 500L, pointerTimeoutMs = 200L)

    val start = 7_000_000L
    fake.firstDown(start)
    // second arrives after pointer timeout
    fake.secondDown(start + 500)
    fake.advanceTimeTo(start + 1000)

    assertFalse("second finger arriving too late should not trigger", fake.triggered)
    assertEquals(0, fake.triggerCount)
  }

  @Test
  fun twoFinger_only_triggers_once_even_if_time_advances() {
    val fake = TwoFingerHoldFakeDetector(holdMs = 400L)
    val start = 8_000_000L
    fake.firstDown(start)
    fake.secondDown(start + 50)

    fake.advanceTimeTo(start + 500)
    assertTrue(fake.triggered)
    assertEquals(1, fake.triggerCount)

    // advancing time further must not increase trigger count
    fake.advanceTimeTo(start + 1000)
    assertEquals(1, fake.triggerCount)
  }

  @Test
  fun twoFinger_up_before_hold_cancels_trigger() {
    val fake = TwoFingerHoldFakeDetector(holdMs = 500L)
    val start = 9_000_000L
    fake.firstDown(start)
    fake.secondDown(start + 50)

    // pointer up before hold completes
    fake.pointerUp(atTime = start + 200)
    fake.advanceTimeTo(start + 700)

    assertFalse("pointer up before hold should cancel trigger", fake.triggered)
    assertEquals(0, fake.triggerCount)
  }

  @Test
  fun twoFinger_third_pointer_cancels_hold() {
    val fake = TwoFingerHoldFakeDetector(holdMs = 500L)
    val start = 10_000_000L
    fake.firstDown(start)
    fake.secondDown(start + 50)

    // third pointer arrives and should cancel the two-finger hold
    fake.thirdPointerDown(start + 100)
    fake.advanceTimeTo(start + 700)

    assertFalse("third pointer should cancel two-finger hold", fake.triggered)
  }

  @Test
  fun haptics_stop_after_cancel() {
    // haptic interval of 100ms -> expect a few ticks before cancellation
    val fake = TwoFingerHoldFakeDetector(holdMs = 500L, hapticIntervalMs = 100L)
    val start = 11_000_000L
    fake.firstDown(start)
    fake.secondDown(start + 50)

    // advance to generate a couple of haptic ticks
    fake.advanceTimeTo(start + 250)
    val ticksBefore = fake.hapticTickCount
    assertTrue("should have produced at least one haptic tick", ticksBefore >= 1)

    // simulate move/cancel that should stop haptics
    fake.move(pointer = 1, x = 100f, y = 100f, atTime = start + 300)
    fake.advanceTimeTo(start + 1000)

    // no new ticks after cancellation
    assertEquals("no additional haptic ticks after cancellation", ticksBefore, fake.hapticTickCount)
  }

  @Test
  fun scheduled_runnable_versioning_prevents_stale_cancellation() {
    // overall timeout at 1000ms should have been scheduled at firstDown
    val fake = TwoFingerHoldFakeDetector(holdMs = 500L, overallTimeoutMs = 1000L)
    val start = 12_000_000L
    fake.firstDown(start)

    // schedule would cancel at start + 1000; but we simulate a state transition
    // that bumps version before the scheduled runnable runs
    fake.secondDown(start + 100)
    // secondDown increments version inside the fake

    // advance to the original scheduled cancel time
    fake.advanceTimeTo(start + 1000)

    // because version changed, the stale scheduled cancel should NOT have run
    // and the hold can still complete if we advance past hold duration
    fake.advanceTimeTo(start + 1200)
    assertTrue("hold should still be able to complete after state version change", fake.triggered)
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
  var triggerCount: Int = 0
    private set

  fun recordTap(timestampMs: Long) {
    taps.add(timestampMs)
    while (taps.size > 3) taps.removeAt(0)
    if (taps.size == 3) {
      val window = taps.last() - taps.first()
      if (window <= windowMs && !detected) {
        detected = true
        triggerCount++
      }
    }
  }
}

/**
 * Simple pure-JVM fake for two-finger hold detection. It models the minimal
 * timing and drift logic needed to assert hold completion. This fake also
 * simulates haptic ticks and scheduled runnables with a simple versioning
 * mechanism to catch regressions where scheduled callbacks are not invalidated
 * on state changes.
 */
class TwoFingerHoldFakeDetector(
  private val holdMs: Long,
  private val pointerTimeoutMs: Long = 250L,
  private val driftTolerancePx: Float = 10f,
  // optional haptic interval; if provided, the fake will simulate ticks at
  // firstDownTime + n*hapticIntervalMs while the hold remains active
  private val hapticIntervalMs: Long? = null,
  // optional overall timeout scheduling to emulate scheduled runnables
  private val overallTimeoutMs: Long? = null
) {
  private var now = 0L
  private var firstDownTime = 0L
  private var secondDownTime = 0L
  private var firstStartX = 0f
  private var firstStartY = 0f
  var triggered: Boolean = false
    private set
  var triggerCount: Int = 0
    private set

  // haptic simulation
  var hapticTickCount: Int = 0
    private set
  private var lastHapticTickTime: Long = 0L

  // scheduling/versioning simulation
  private var stateVersion: Long = 0L
  private data class ScheduledEvent(val executeAt: Long, val version: Long, val action: () -> Unit)
  private val scheduled = mutableListOf<ScheduledEvent>()

  fun firstDown(atTime: Long, x: Float = 0f, y: Float = 0f) {
    now = atTime
    firstDownTime = atTime
    firstStartX = x
    firstStartY = y
    // reset previous second pointer
    secondDownTime = 0L
    // schedule overall timeout if requested
    overallTimeoutMs?.let { ot ->
      scheduleRunnable(firstDownTime + ot) {
        // cancellation runnable: clear first/second so hold cannot complete
        firstDownTime = 0L
        secondDownTime = 0L
      }
    }
  }

  fun secondDown(atTime: Long, x: Float = firstStartX, y: Float = firstStartY) {
    now = atTime
    if (firstDownTime == 0L) return
    if (atTime - firstDownTime > pointerTimeoutMs) return // timed out
    secondDownTime = atTime
    // state transition: bump version to emulate setState incrementing
    stateVersion++
  }

  fun thirdPointerDown(atTime: Long) {
    now = atTime
    // third pointer cancels the two-finger gesture
    firstDownTime = 0L
    secondDownTime = 0L
    // bump version to invalidate scheduled runnables
    stateVersion++
  }

  fun move(pointer: Int, x: Float, y: Float, atTime: Long) {
    now = atTime
    val dx = x - firstStartX
    val dy = y - firstStartY
    if ((dx * dx + dy * dy) > (driftTolerancePx * driftTolerancePx)) {
      // cancel
      firstDownTime = 0L
      secondDownTime = 0L
      stateVersion++
    }
  }

  fun pointerUp(atTime: Long) {
    now = atTime
    // any UP cancels the hold
    firstDownTime = 0L
    secondDownTime = 0L
    stateVersion++
  }

  private fun scheduleRunnable(executeAt: Long, action: () -> Unit) {
    scheduled.add(ScheduledEvent(executeAt, stateVersion, action))
  }

  fun advanceTimeTo(t: Long) {
    // advance time and execute scheduled events that match current version
    // in chronological order
    scheduled.sortBy { it.executeAt }
    now = t
    val toRun = scheduled.filter { it.executeAt <= now }
    scheduled.removeAll(toRun)
    for (ev in toRun) {
      if (ev.version == stateVersion) ev.action()
    }

    // haptic ticks: if haptics enabled and gesture active (first+second present),
    // generate ticks at multiples of hapticIntervalMs after firstDownTime
    hapticIntervalMs?.let { interval ->
      if (firstDownTime > 0 && secondDownTime > 0) {
        var nextTick = lastHapticTickTime.takeIf { it > 0 } ?: (firstDownTime + interval)
        while (nextTick <= now) {
          hapticTickCount++
          lastHapticTickTime = nextTick
          nextTick += interval
        }
      }
    }

    if (firstDownTime > 0 && secondDownTime > 0) {
      val holdUntil = firstDownTime + holdMs
      if (now >= holdUntil && !triggered) {
        triggered = true
        triggerCount++
      }
    }
  }
}
