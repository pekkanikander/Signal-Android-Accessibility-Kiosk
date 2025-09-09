package org.thoughtcrime.securesms.accessibility

import android.graphics.Rect
import android.view.MotionEvent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class AccessibilityGestureDetectorInstrTest {

  @Test
  fun tripleTap_triggersCallback() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val latch = CountDownLatch(1)

    // Ensure detector is configured to use the triple-tap debug gesture for the test
    org.thoughtcrime.securesms.keyvalue.SignalStore.accessibilityMode.exitGestureType = 3

    val detector = AccessibilityModeExitToSettingsGestureDetector(context, { Rect(0,0,100,100) }) {
      latch.countDown()
    }

    // Simulate three quick taps at center
    val x = 100f
    val y = 100f
    val now = System.currentTimeMillis()

    val view = android.view.View(context)
    for (i in 0 until 3) {
      val down = MotionEvent.obtain(now + i*100L, now + i*100L, MotionEvent.ACTION_DOWN, x, y, 0)
      detector.onTouch(view, down)
      val up = MotionEvent.obtain(now + i*100L + 20, now + i*100L + 20, MotionEvent.ACTION_UP, x, y, 0)
      detector.onTouch(view, up)
      down.recycle()
      up.recycle()
    }

    val triggered = latch.await(2, TimeUnit.SECONDS)
    assertTrue("Triple tap should trigger gesture callback", triggered)
  }
}


