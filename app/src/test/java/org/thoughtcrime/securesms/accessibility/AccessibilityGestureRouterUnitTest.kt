package org.thoughtcrime.securesms.accessibility

import android.view.MotionEvent
import org.junit.Test
import org.junit.Assert.assertEquals

class AccessibilityGestureRouterUnitTest {

  @Test
  fun tripleTap_stateTransitions() {
    val detector = AccessibilityModeExitToSettingsGestureDetectorFake()

    // Simulate three ACTION_DOWN/ACTION_UP sequences
    val x = 100f
    val y = 100f
    val now = System.currentTimeMillis()

    for (i in 0 until 3) {
      val down = MotionEvent.obtain(now + i*100L, now + i*100L, MotionEvent.ACTION_DOWN, x, y, 0)
      detector.onTouch(null, down)
      val up = MotionEvent.obtain(now + i*100L + 20, now + i*100L + 20, MotionEvent.ACTION_UP, x, y, 0)
      detector.onTouch(null, up)
      down.recycle()
      up.recycle()
    }

    assertEquals("DETECTED", detector.lastState)
  }
}

// Minimal fake detector that flips state when triple-tap logic would trigger
class AccessibilityModeExitToSettingsGestureDetectorFake : AccessibilityModeExitToSettingsGestureDetector(
  context = androidx.test.core.app.ApplicationProvider.getApplicationContext(),
  headerBoundsProvider = { android.graphics.Rect(0,0,100,100) },
  onTriggered = {}
) {
  var lastState: String = "IDLE"

  override fun triggerGesture() {
    super.triggerGesture()
    lastState = "DETECTED"
  }
}


