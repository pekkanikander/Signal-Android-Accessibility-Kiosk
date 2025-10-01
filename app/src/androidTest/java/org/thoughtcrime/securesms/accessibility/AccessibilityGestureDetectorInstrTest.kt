package org.thoughtcrime.securesms.accessibility

import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class AccessibilityGestureDetectorInstrTest {

  /** Builds a detector with a fixed header hotspot and a test callback counter. */
  private fun buildDetector(triggered: AtomicInteger): AccessibilityModeExitGestureDetector {
    val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    val header = Rect(0, 0, 400, 200) // top header hotspot
    return AccessibilityModeExitGestureDetector(
      ctx,
      headerBoundsProvider = { header },
    ) { triggered.incrementAndGet() }
  }

  private fun applyConfig(detector: AccessibilityModeExitGestureDetector,
                          type: AccessibilityModeExitGestureType,
                          totalTimeoutMs: Int = 1_000,
                          tripleGapMs: Int = 400,
                          chordSecondFingerMs: Int = 250,
                          headerHeightDp: Int = 120) {
    val cfg = ExitGestureConfig(
      type = type,
      totalTimeoutMs = totalTimeoutMs,
      tripleTapGapMs = tripleGapMs,
      chordSecondFingerTimeoutMs = chordSecondFingerMs,
      headerHeightDp = headerHeightDp
    )
    detector.applyConfig(cfg)
    detector.updateSelectedGesture(type, force = true)
  }

  // ---------------- Triple tap -------------------------------------------------

  @Test
  fun tripleTap_happyPath_triggersOnce() {
    val trig = AtomicInteger(0)
    val detector = buildDetector(trig)
    applyConfig(detector, AccessibilityModeExitGestureType.TripleTap)

    val base = SystemClock.uptimeMillis()
    val x = 100f; val y = 100f // inside header

    send(detector,
      *tap(base +   0, x, y),
      *tap(base + 150, x, y),
      *tap(base + 300, x, y)
    )

    assertEquals(1, trig.get())
  }

  @Test
  fun tripleTap_equality_boundary_triggers() {
    val trig = AtomicInteger(0)
    val detector = buildDetector(trig)
    // gap window = 400ms; use exactly the boundary
    applyConfig(detector, AccessibilityModeExitGestureType.TripleTap, tripleGapMs = 400)

    val base = SystemClock.uptimeMillis()
    val x = 80f; val y = 80f

    send(detector,
      *tap(base +   0, x, y),
      *tap(base + 400, x, y),
      *tap(base + 800, x, y)
    )

    assertEquals(1, trig.get())
  }

  @Test
  fun tripleTap_slop_exceeded_then_recovery() {
    val trig = AtomicInteger(0)
    val detector = buildDetector(trig)
    applyConfig(detector, AccessibilityModeExitGestureType.TripleTap)

    val base = SystemClock.uptimeMillis()
    val x = 120f; val y = 120f

    // First attempt: exceed slop during first tap → should fail
    send(detector,
      down(base +   0, x, y),
      move(base +  30, x + 100f, y), // exceed slop by far
      up  (base +  40, x + 100f, y)
    )
    assertEquals(0, trig.get())

    // Fresh valid triple tap
    send(detector,
      *tap(base + 200, x, y),
      *tap(base + 350, x, y),
      *tap(base + 500, x, y)
    )
    assertEquals(1, trig.get())
  }

  // ---------------- Chord + slide up ------------------------------------------

  @Test
  fun chordSlideUp_happyPath_triggersOnce() {
    val trig = AtomicInteger(0)
    val detector = buildDetector(trig)
    applyConfig(detector, AccessibilityModeExitGestureType.ChordSlideUp, chordSecondFingerMs = 250)

    val base = SystemClock.uptimeMillis()
    // Start inside header; slide centroid up by ≥ 60 px total
    send(detector,
      pd(base +   0, p(0, 150f, 150f)),               // first finger down
      pd(base + 100, p(0, 150f, 150f), p(1, 150f, 152f)), // second finger within window
      move2(base + 150, p(0, 130f, 120f), p(1, 170f, 118f)), // centroid up ~32px
      move2(base + 200, p(0, 130f,  90f), p(1, 170f,  88f))  // centroid up total ≥ 60px → trigger
    )

    assertEquals(1, trig.get())
  }

  @Test
  fun chordSlideUp_second_finger_timeout_fails() {
    val trig = AtomicInteger(0)
    val detector = buildDetector(trig)
    applyConfig(detector, AccessibilityModeExitGestureType.ChordSlideUp, chordSecondFingerMs = 100)

    val base = SystemClock.uptimeMillis()
    send(detector,
      pd(base +   0, p(0, 120f, 160f)),
      pd(base + 200, p(0, 120f, 160f), p(1, 120f, 160f)) // too late
    )
    assertEquals(0, trig.get())
  }

  @Test
  fun chordSlideUp_third_pointer_aborts() {
    val trig = AtomicInteger(0)
    val detector = buildDetector(trig)
    applyConfig(detector, AccessibilityModeExitGestureType.ChordSlideUp)

    val base = SystemClock.uptimeMillis()
    send(detector,
      pd(base +   0, p(0, 140f, 140f)),
      pd(base +  50, p(0, 140f, 140f), p(1, 160f, 140f)),
      // Third pointer arrives → policy should abort attempt
      pd(base +  80, p(0, 140f, 140f), p(1, 160f, 140f), p(2, 150f, 130f))
    )
    // No trigger
    assertEquals(0, trig.get())
  }

  @Test
  fun totalTimeout_aborts_slow_tripleTap() {
    val trig = AtomicInteger(0)
    val detector = buildDetector(trig)
    // Make overall timeout very small so a slow triple tap exceeds it
    applyConfig(detector, AccessibilityModeExitGestureType.TripleTap, totalTimeoutMs = 150, tripleGapMs = 200)

    val base = SystemClock.uptimeMillis()
    val x = 100f; val y = 100f

    send(detector,
      *tap(base +   0, x, y),
      *tap(base + 100, x, y),
      *tap(base + 400, x, y)
    )
    assertEquals(0, trig.get())
  }

  @Test
  fun cancel_aborts_attempt() {
    val trig = AtomicInteger(0)
    val detector = buildDetector(trig)
    applyConfig(detector, AccessibilityModeExitGestureType.TripleTap)

    val base = SystemClock.uptimeMillis()
    val x = 110f; val y = 110f

    send(detector,
      down(base +   0, x, y),
      cancel(base +  20)
    )
    assertEquals(0, trig.get())
  }

  // ---------------- Event synthesis helpers -----------------------------------

  private fun send(detector: AccessibilityModeExitGestureDetector, vararg events: MotionEvent) {
    events.forEach { ev ->
      val consumed = detector.onTouch(null, ev)
      assertTrue("Detector must be transparent (return false)", consumed == false)
      ev.recycle()
    }
  }

  private fun tap(t: Long, x: Float, y: Float): Array<MotionEvent> = arrayOf(
    down(t, x, y), up(t + 20, x, y)
  )

  private fun down(t: Long, x: Float, y: Float): MotionEvent =
    MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0)

  private fun up(t: Long, x: Float, y: Float): MotionEvent =
    MotionEvent.obtain(t, t, MotionEvent.ACTION_UP, x, y, 0)

  private fun move(t: Long, x: Float, y: Float): MotionEvent =
    MotionEvent.obtain(t, t, MotionEvent.ACTION_MOVE, x, y, 0)

  private fun cancel(t: Long): MotionEvent =
    MotionEvent.obtain(t, t, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)

  private data class P(val id: Int, val x: Float, val y: Float)
  private fun p(id: Int, x: Float, y: Float) = P(id, x, y)

  private fun pd(t: Long, vararg pts: P): MotionEvent {
    require(pts.isNotEmpty())
    val count = pts.size
    val props = Array(count) { MotionEvent.PointerProperties() }
    val coords = Array(count) { MotionEvent.PointerCoords() }
    for (i in 0 until count) {
      props[i].id = pts[i].id
      props[i].toolType = MotionEvent.TOOL_TYPE_FINGER
      coords[i].x = pts[i].x
      coords[i].y = pts[i].y
      coords[i].pressure = 1f
      coords[i].size = 1f
    }
    val action = if (count == 1) MotionEvent.ACTION_DOWN else {
      val index = count - 1 // last pointer went down
      MotionEvent.ACTION_POINTER_DOWN or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
    }
    return MotionEvent.obtain(
      t, t, action, count,
      props, coords,
      0, 0, 1f, 1f, 0, 0, 0, 0
    )
  }

  private fun move2(t: Long, p0: P, p1: P): MotionEvent {
    val props = Array(2) { MotionEvent.PointerProperties() }
    val coords = Array(2) { MotionEvent.PointerCoords() }
    props[0].id = p0.id; props[0].toolType = MotionEvent.TOOL_TYPE_FINGER
    props[1].id = p1.id; props[1].toolType = MotionEvent.TOOL_TYPE_FINGER
    coords[0].x = p0.x; coords[0].y = p0.y; coords[0].pressure = 1f; coords[0].size = 1f
    coords[1].x = p1.x; coords[1].y = p1.y; coords[1].pressure = 1f; coords[1].size = 1f
    return MotionEvent.obtain(
      t, t, MotionEvent.ACTION_MOVE, 2,
      props, coords,
      0, 0, 1f, 1f, 0, 0, 0, 0
    )
  }
}
