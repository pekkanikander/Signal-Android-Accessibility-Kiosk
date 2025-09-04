package org.thoughtcrime.securesms.testing

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry

object AccessibilityGestureHelpers {
  private const val TAG = "AccessibilityGestureHelpers"

  private fun execShell(cmd: String) {
    try {
      val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(cmd)
      pfd.close()
    } catch (e: Exception) {
      Log.w(TAG, "shell command failed: $cmd", e)
    }
  }

  private fun getScreenSize(): Pair<Int, Int> {
    return try {
      val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("wm size")
      val input = android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader()
      val out = input.readText()
      input.close()
      // parse "Physical size: 1080x1920"
      val m = Regex("([0-9]+)x([0-9]+)").find(out)
      if (m != null) {
        val w = m.groupValues[1].toInt()
        val h = m.groupValues[2].toInt()
        Pair(w, h)
      } else Pair(1080, 1920)
    } catch (e: Exception) {
      Log.w(TAG, "failed to get screen size, defaulting", e)
      Pair(1080, 1920)
    }
  }

  fun tripleTapCenter() {
    val (w, h) = getScreenSize()
    val cx = w / 2
    val cy = h / 2
    for (i in 1..3) {
      execShell("input touchscreen tap $cx $cy")
      Thread.sleep(120)
    }
  }

  fun oppositeCornersHold() {
    val (w, h) = getScreenSize()
    val offset = (Math.min(w, h) * 0.15).toInt()
    val x1 = offset
    val y1 = offset
    val x2 = w - offset
    val y2 = h - offset
    // start two long presses using swipe with same start/end and long duration
    execShell("input touchscreen swipe $x1 $y1 $x1 $y1 2000 &")
    Thread.sleep(100)
    execShell("input touchscreen swipe $x2 $y2 $x2 $y2 2000 &")
    Thread.sleep(2500)
  }

  fun twoFingerHeaderHold() {
    val (w, h) = getScreenSize()
    val headerY = (h * 0.12).toInt()
    val xL = (w * 0.25).toInt()
    val xR = (w * 0.75).toInt()
    execShell("input touchscreen swipe $xL $headerY $xL $headerY 2000 &")
    Thread.sleep(100)
    execShell("input touchscreen swipe $xR $headerY $xR $headerY 2000 &")
    Thread.sleep(2500)
  }

  fun singleFingerEdgeDrag() {
    val (w, h) = getScreenSize()
    val startX = (w * 0.05).toInt()
    val endX = (w * 0.95).toInt()
    val y = h / 2
    execShell("input touchscreen swipe $startX $y $endX $y 2000")
    Thread.sleep(500)
  }
}


