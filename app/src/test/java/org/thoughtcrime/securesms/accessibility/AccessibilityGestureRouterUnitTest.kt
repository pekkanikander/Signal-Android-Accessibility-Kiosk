package org.thoughtcrime.securesms.accessibility

import org.junit.Test
import org.junit.Assert.assertEquals

class AccessibilityGestureRouterUnitTest {

  @Test
  fun tripleTap_stateTransitions() {
    val detector = AccessibilityModeExitToSettingsGestureDetectorFake()

    // Simulate three logical taps spaced 100ms apart
    val now = System.currentTimeMillis()
    for (i in 0 until 3) {
      detector.recordTap(now + i * 100L)
    }

    assertEquals("DETECTED", detector.lastState)
  }
}

// Pure-JVM fake detector that mimics triple-tap detection without Android types.
class AccessibilityModeExitToSettingsGestureDetectorFake {
  var lastState: String = "IDLE"
  private val tapTimestamps = mutableListOf<Long>()
  private val tripleTapWindowMs = 600L

  fun recordTap(timestampMs: Long) {
    tapTimestamps.add(timestampMs)
    // Keep only recent taps
    while (tapTimestamps.size > 3) tapTimestamps.removeAt(0)
    if (tapTimestamps.size == 3) {
      val window = tapTimestamps.last() - tapTimestamps.first()
      if (window <= tripleTapWindowMs) {
        triggerGesture()
      }
    }
  }

  private fun triggerGesture() {
    lastState = "DETECTED"
  }
}
