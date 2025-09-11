/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.accessibility

import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityManager
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.keyvalue.SignalStore
import kotlin.math.sqrt

/**
 * Detector for Accessibility Mode exit gestures.
 * Production: two-finger header hold. Debug: triple-tap header.
 */
class AccessibilityModeExitToSettingsGestureDetector(
  private val context: Context,
  private val headerBoundsProvider: () -> Rect,
  private val onTriggered: () -> Unit
) : View.OnTouchListener {

  companion object {
    private val TAG = "ExitGesture"
    private const val DEFAULT_HAPTIC_FEEDBACK_INTERVAL_MS = 500L
  }

  private val accessibilityManager: AccessibilityManager by lazy {
    context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
  }

  // --- Configuration (lazy to pick up live values from SignalStore) ---------
  private val holdDurationMs: Int by lazy { SignalStore.accessibilityMode.exitGestureHoldMs }
  private val driftTolerancePx: Float by lazy { context.resources.displayMetrics.density * SignalStore.accessibilityMode.exitGestureDriftDp }
  private val headerDeadzonePx: Int by lazy { (context.resources.displayMetrics.density * SignalStore.accessibilityMode.exitHeaderDeadzoneDp).toInt() }
  private val headerExtraBottomPx: Int by lazy { (context.resources.displayMetrics.density * SignalStore.accessibilityMode.exitHeaderExtraBottomDp).toInt() }
  private val pointerTimeoutMs: Int by lazy { SignalStore.accessibilityMode.exitGesturePointerTimeoutMs }
  private val tripleTapIntervalMs: Int by lazy { SignalStore.accessibilityMode.exitTripleTapIntervalMs }
  private val tripleTapWindowMs: Int by lazy { SignalStore.accessibilityMode.exitTripleTapWindowMs }
  private val exitGestureTimeoutMs: Int by lazy { SignalStore.accessibilityMode.exitGestureTimeoutMs }

  private fun currentGestureType(): AccessibilityModeExitGestureType {
    val debugPrefs = context.getSharedPreferences("accessibility_mode_debug", Context.MODE_PRIVATE)
    val override = debugPrefs.getInt("exit_gesture_type_override", -1)
    val overrideEnabled = debugPrefs.getBoolean("exit_gesture_type_override_enabled", false)
    val value = if (overrideEnabled && (override == 0 || override == 1)) {
      Log.d(TAG, "Using debug override for exit gesture: $override")
      override
    } else {
      SignalStore.accessibilityMode.exitGestureType
    }
    return AccessibilityModeExitGestureType.fromValue(value)
  }

  // --- Gesture contexts (per-family, mutable) --------------------------------
  private class TwoFingerCtx {
    var firstPointerId: Int = -1
    var secondPointerId: Int = -1
    var firstPointerDownTime: Long = 0L
    var secondPointerDownTime: Long = 0L
    var firstPointerStartX: Float = 0f
    var firstPointerStartY: Float = 0f
    var secondPointerStartX: Float = 0f
    var secondPointerStartY: Float = 0f
    fun clear() {
      firstPointerId = -1; secondPointerId = -1
      firstPointerDownTime = 0L; secondPointerDownTime = 0L
      firstPointerStartX = 0f; firstPointerStartY = 0f
      secondPointerStartX = 0f; secondPointerStartY = 0f
    }
  }
  private class TripleTapCtx {
    var startX: Float = 0f
    var startY: Float = 0f
    var lastTapTime: Long = 0L
    var flowStartTime: Long = 0L
    fun clear() { startX = 0f; startY = 0f; lastTapTime = 0L; flowStartTime = 0L }
  }

  private val two = TwoFingerCtx()
  private val tri = TripleTapCtx()

  // Triple-tap anchors & timing
  private var gestureStartTime = 0L

  // Scheduling infra with versioning to invalidate stale lambdas
  private val mainHandler = Handler(Looper.getMainLooper())
  private var stateVersion = 0L
  private val scheduledRunnables: MutableList<Runnable> = mutableListOf()

  private fun scheduleRunnable(delayMs: Long, runnable: Runnable) {
    scheduledRunnables.add(runnable)
    mainHandler.postDelayed(runnable, delayMs)
  }
  private fun scheduleRunnable(delayMs: Long, action: () -> Unit) {
    val myVersion = stateVersion
    val r = object : Runnable {
      override fun run() {
        if (myVersion != stateVersion) return
        try { action() } finally { scheduledRunnables.remove(this) }
      }
    }
    scheduleRunnable(delayMs, r)
  }
  private fun cancelScheduledRunnables() {
    scheduledRunnables.forEach { mainHandler.removeCallbacks(it) }
    scheduledRunnables.clear()
  }

  // Geometry helpers
  private fun headerBoundsInset(): Rect {
    val src = headerBoundsProvider()
    val b = Rect(src)
    val origBottom = b.bottom
    b.inset(headerDeadzonePx, 0) // tighten left/right; keep top strict
    b.bottom = maxOf(b.top, b.bottom - headerDeadzonePx) // reduce bottom a bit
    val expanded = b.bottom + headerExtraBottomPx
    b.bottom = maxOf(b.top, kotlin.math.min(expanded, origBottom + headerExtraBottomPx))
    return b
  }
  private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
    val dx = x1 - x2; val dy = y1 - y2; return sqrt(dx * dx + dy * dy)
  }

  // --- State pattern ---------------------------------------------------------
  // Singleton state instances (per detector instance)
  private val idleState = Idle()
  private val tripleTapWaitSecondState = TripleTapWaitSecond()
  private val tripleTapWaitThirdState = TripleTapWaitThird()
  private val twoFingerFirstDownState = TwoFingerFirstDown()
  private val twoFingerSecondDownState = TwoFingerSecondDown()

  private var state: State = idleState

  private fun setState(newState: State) {
    if (state === newState) return
    state.onExit()
    state = newState
    stateVersion++
    state.onEnter()
  }

  private abstract inner class State {
    open fun onEnter() {}
    open fun onExit() {}
    open fun handleActionDown(event: MotionEvent, idx: Int, x: Float, y: Float, now: Long, inHeader: Boolean): Boolean = false
    open fun handlePointerDown(event: MotionEvent, idx: Int, x: Float, y: Float, now: Long, inHeader: Boolean): Boolean = false
    open fun handleMove(event: MotionEvent, idx: Int, x: Float, y: Float, now: Long, inHeader: Boolean): Boolean = false
    open fun handlePointerUp(event: MotionEvent, idx: Int, x: Float, y: Float, now: Long, inHeader: Boolean): Boolean = false
  }

  // Gesture-family base states ------------------------------------------------
  private abstract inner class TripleTap : State() {
    protected val touchSlop: Int by lazy { ViewConfiguration.get(context).scaledTouchSlop }
    protected fun isWithinSlop(x: Float, y: Float): Boolean {
      val dx = x - tri.startX
      val dy = y - tri.startY
      return (dx * dx + dy * dy) <= (touchSlop * touchSlop)
    }
    override fun handleMove(event: MotionEvent, idx: Int, x: Float, y: Float, now: Long, inHeader: Boolean): Boolean {
      // Ignore jitter within touch slop during triple-tap; reset on larger movement
      return if (isWithinSlop(x, y)) {
        false
      } else {
        Log.d(TAG, "TRIPLE_TAP: MOVE > slop -> reset")
        resetState(); false
      }
    }
    override fun handlePointerUp(event: MotionEvent, idx: Int, x: Float, y: Float, now: Long, inHeader: Boolean): Boolean {
      // DOWNs drive the logic; UPs are ignored during triple-tap sequencing
      return true
    }
  }

  private abstract inner class TwoFingers : State() {
    protected fun headerContains(x: Float, y: Float): Boolean = headerBoundsInset().contains(x.toInt(), y.toInt())
    protected fun withinDrift(x: Float, y: Float, sx: Float, sy: Float): Boolean = distance(x, y, sx, sy) <= driftTolerancePx
    protected fun firstIndex(event: MotionEvent): Int = event.findPointerIndex(two.firstPointerId)
    protected fun secondIndex(event: MotionEvent): Int = event.findPointerIndex(two.secondPointerId)
    override fun handlePointerDown(event: MotionEvent, idx: Int, x: Float, y: Float, now: Long, inHeader: Boolean): Boolean {
      // Any third pointer cancels the two-finger gesture
      Log.d(TAG, "TWO_FINGER: Third pointer down -> reset")
      resetState(); return false
    }
  }

  private inner class Idle : State() {
    override fun handleActionDown(event: MotionEvent, idx: Int, x: Float, y: Float, now: Long, inHeader: Boolean): Boolean {
      if (!inHeader) return false

      gestureStartTime = now
      // Overall safety window (8s by default)
      scheduleRunnable(exitGestureTimeoutMs.toLong()) {
        if (SystemClock.uptimeMillis() - gestureStartTime >= exitGestureTimeoutMs) {
          Log.d(TAG, "Overall window timeout -> reset")
          resetState()
        }
      }

      return when (currentGestureType()) {
        AccessibilityModeExitGestureType.TRIPLE_TAP_DEBUG -> {
          // Seed triple-tap context
          tri.startX = x; tri.startY = y
          tri.flowStartTime = now; tri.lastTapTime = now
          setState(tripleTapWaitSecondState)
          // Overall triple-tap window still enforced here
          scheduleRunnable(tripleTapWindowMs.toLong()) {
            if (gestureStartTime != 0L && SystemClock.uptimeMillis() - gestureStartTime >= tripleTapWindowMs) {
              Log.d(TAG, "TRIPLE_TAP: overall window timeout"); resetState()
            }
          }
          true
        }
        AccessibilityModeExitGestureType.TWO_FINGER_HEADER_HOLD -> {
          // Seed two-finger context with first pointer
          two.firstPointerId = event.getPointerId(idx)
          two.firstPointerStartX = x; two.firstPointerStartY = y
          two.firstPointerDownTime = now
          setState(twoFingerFirstDownState)
          // Require second finger soon
          scheduleRunnable(pointerTimeoutMs.toLong()) {
            if (state === twoFingerFirstDownState) { Log.d(TAG, "TWO_FINGER: second finger timeout"); resetState() }
          }
          true
        }
      }
    }
  }

  private inner class TripleTapWaitSecond : TripleTap() {
    override fun onEnter() {
      // Inter-tap timeout for the second tap
      scheduleRunnable(tripleTapIntervalMs.toLong()) {
        if (state === tripleTapWaitSecondState && SystemClock.uptimeMillis() - tri.lastTapTime >= tripleTapIntervalMs) {
          Log.d(TAG, "TRIPLE_TAP: inter-tap timeout (second)"); resetState()
        }
      }
    }
    override fun handleActionDown(event: MotionEvent, idx: Int, x: Float, y: Float, now: Long, inHeader: Boolean): Boolean {
      if (!inHeader) return false
      tri.lastTapTime = now
      setState(tripleTapWaitThirdState)
      return true
    }
  }

  private inner class TripleTapWaitThird : TripleTap() {
    override fun onEnter() {
      // Inter-tap timeout waiting for the third tap
      scheduleRunnable(tripleTapIntervalMs.toLong()) {
        if (state === tripleTapWaitThirdState && SystemClock.uptimeMillis() - tri.lastTapTime >= tripleTapIntervalMs) {
          Log.d(TAG, "TRIPLE_TAP: inter-tap timeout (third)"); resetState()
        }
      }
    }
    override fun handleActionDown(event: MotionEvent, idx: Int, x: Float, y: Float, now: Long, inHeader: Boolean): Boolean {
      if (!inHeader) return false
      Log.d(TAG, "TRIPLE_TAP: third tap -> trigger")
      resetState(); triggerGesture(); return true
    }
  }

  private inner class TwoFingerFirstDown : TwoFingers() {
    override fun handlePointerDown(event: MotionEvent, idx: Int, x: Float, y: Float, now: Long, inHeader: Boolean): Boolean {
      if (!inHeader) { Log.d(TAG, "SECOND_POINTER_DOWN outside header"); resetState(); return false }
      two.secondPointerId = event.getPointerId(idx)
      two.secondPointerDownTime = now
      two.secondPointerStartX = x; two.secondPointerStartY = y
      Log.d(TAG, "SECOND_POINTER_DOWN id=${two.secondPointerId} start=(${two.secondPointerStartX.toInt()},${two.secondPointerStartY.toInt()})")
      setState(twoFingerSecondDownState)

      // Hold completion at first-pointer baseline
      val timeUntilHold = (two.firstPointerDownTime + holdDurationMs) - now
      scheduleRunnable(kotlin.math.max(0L, timeUntilHold)) {
        if (state === twoFingerSecondDownState) {
          Log.d(TAG, "TWO_FINGER: hold complete -> trigger")
          triggerGesture()
        }
      }

      // Periodic haptics while holding (kept as in your design)
      val hapticInterval = SignalStore.accessibilityMode.exitHapticFeedbackIntervalMs.takeIf { it > 0 }?.toLong()
        ?: DEFAULT_HAPTIC_FEEDBACK_INTERVAL_MS
      val firstHapticDelay = kotlin.math.max(0L, two.firstPointerDownTime + hapticInterval - now)
      val tick = object : Runnable {
        override fun run() {
          if (state !== twoFingerSecondDownState) return
          try {
            Log.d(TAG, "TWO_FINGER: haptic tick")
          } finally {
            scheduleRunnable(hapticInterval, this)
          }
        }
      }
      scheduleRunnable(firstHapticDelay, tick)
      return true
    }

    override fun handleMove(event: MotionEvent, idx: Int, x: Float, y: Float, now: Long, inHeader: Boolean): Boolean {
      // Allow small drift within header while waiting for second finger
      val i0 = firstIndex(event)
      if (i0 == -1) { resetState(); return false }
      val fx = event.getX(i0); val fy = event.getY(i0)
      val header = headerBoundsInset()
      if (!header.contains(fx.toInt(), fy.toInt())) { Log.d(TAG, "TWO_FINGER: MOVE outside header before second finger"); resetState(); return false }
      val drift = distance(fx, fy, two.firstPointerStartX, two.firstPointerStartY)
      if (drift > driftTolerancePx) { Log.d(TAG, "TWO_FINGER: MOVE beyond drift before second finger"); resetState(); return false }
      return true
    }

    override fun handlePointerUp(event: MotionEvent, idx: Int, x: Float, y: Float, now: Long, inHeader: Boolean): Boolean {
      val upId = event.getPointerId(idx)
      if (upId == two.firstPointerId) { resetState(); return true }
      return false
    }
  }

  private inner class TwoFingerSecondDown : TwoFingers() {
    override fun handleMove(event: MotionEvent, idx: Int, x: Float, y: Float, now: Long, inHeader: Boolean): Boolean {
      val i0 = firstIndex(event)
      val i1 = secondIndex(event)
      if (i0 == -1 || i1 == -1) { resetState(); return false }
      val f0x = event.getX(i0); val f0y = event.getY(i0)
      val f1x = event.getX(i1); val f1y = event.getY(i1)
      val header = headerBoundsInset()
      if (!header.contains(f0x.toInt(), f0y.toInt()) || !header.contains(f1x.toInt(), f1y.toInt())) {
        Log.d(TAG, "TWO_FINGER: MOVE outside header bounds"); resetState(); return false
      }
      val d0 = distance(f0x, f0y, two.firstPointerStartX, two.firstPointerStartY)
      val d1 = distance(f1x, f1y, two.secondPointerStartX, two.secondPointerStartY)
      if (d0 > driftTolerancePx || d1 > driftTolerancePx) {
        Log.d(TAG, "TWO_FINGER: MOVE beyond drift"); resetState(); return false
      }
      return true
    }

    override fun handlePointerUp(event: MotionEvent, idx: Int, x: Float, y: Float, now: Long, inHeader: Boolean): Boolean {
      val upId = event.getPointerId(idx)
      if (upId == two.firstPointerId || upId == two.secondPointerId) { resetState(); return true }
      return false
    }
  }

  // --- Public touch entry point --------------------------------------------
  override fun onTouch(view: View, event: MotionEvent): Boolean {
    // Accessibility exploration: don’t intercept
    if (accessibilityManager.isEnabled && accessibilityManager.isTouchExplorationEnabled) return false

    val idx = event.actionIndex
    val x = event.getX(idx)
    val y = event.getY(idx)
    val now = event.eventTime.toLong() // uptime-based, stable
    val inHeader = headerBoundsInset().contains(x.toInt(), y.toInt())

    return when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> state.handleActionDown(event, idx, x, y, now, inHeader)
      MotionEvent.ACTION_POINTER_DOWN -> state.handlePointerDown(event, idx, x, y, now, inHeader)
      MotionEvent.ACTION_MOVE -> state.handleMove(event, idx, x, y, now, inHeader)
      MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> state.handlePointerUp(event, idx, x, y, now, inHeader)
      MotionEvent.ACTION_CANCEL -> resetState()
      else -> false
    }
  }

  private fun triggerGesture() {
    Log.i(TAG, "Exit gesture triggered")
    onTriggered()
  }

  private fun resetState(): Boolean {
    cancelScheduledRunnables()
    gestureStartTime = 0L
    two.clear(); tri.clear()
    setState(idleState)
    return true
  }

  /**
   * Dispose detector resources and cancel any pending timers. Call from lifecycle teardown.
   */
  fun dispose() {
    // Ensure all scheduled work is cancelled and state cleared
    resetState()
    stateVersion++
  }
}
