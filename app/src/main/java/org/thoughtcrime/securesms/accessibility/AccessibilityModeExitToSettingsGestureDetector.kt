/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.accessibility

import android.content.Context
import android.graphics.Rect
import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityManager
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.keyvalue.SignalStore
import androidx.annotation.MainThread

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
  }

  private val accessibilityManager: AccessibilityManager by lazy {
    context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
  }

  // --- Configuration (lazy to pick up live values from SignalStore) ---------
  private val holdDurationMs:       Int by lazy { SignalStore.accessibilityMode.exitGestureHoldMs }
  private val driftTolerancePx:   Float by lazy { context.resources.displayMetrics.density * SignalStore.accessibilityMode.exitGestureDriftDp }
  private val headerDeadzonePx:     Int by lazy { (context.resources.displayMetrics.density * SignalStore.accessibilityMode.exitHeaderDeadzoneDp).toInt() }
  private val headerExtraBottomPx:  Int by lazy { (context.resources.displayMetrics.density * SignalStore.accessibilityMode.exitHeaderExtraBottomDp).toInt() }
  private val pointerTimeoutMs:     Int by lazy { SignalStore.accessibilityMode.exitGesturePointerTimeoutMs }
  private val tripleTapIntervalMs:  Int by lazy { SignalStore.accessibilityMode.exitTripleTapIntervalMs }
  private val tripleTapWindowMs:    Int by lazy { SignalStore.accessibilityMode.exitTripleTapWindowMs }
  private val exitGestureTimeoutMs: Int by lazy { SignalStore.accessibilityMode.exitGestureTimeoutMs }

  private fun currentGestureType(): AccessibilityModeExitGestureType {
    return AccessibilityModeExitGestureType.fromValue(SignalStore.accessibilityMode.exitGestureType)
  }

  // Time & thresholds
  private inline fun now(): Long = SystemClock.uptimeMillis()
  private val driftToleranceSq: Float by lazy { driftTolerancePx * driftTolerancePx }

  // Pointer state for two-finger and triple-tap gestures, seeded from MotionEvent
  private class PointerState {
    var id: Int = -1
    var downTime: Long = 0L
    val start: PointF = PointF()
    fun setStart(event: MotionEvent) {
      val idx = event.actionIndex
      id = event.getPointerId(idx);
      downTime = event.getEventTime()
      start.set(event.getX(idx), event.getY(idx))
    }
    fun clear() {
      id = -1;
      downTime = 0L;
      start.set(0f, 0f)
    }
  }

  // --- Gesture contexts (per-family, mutable) --------------------------------
  private class TwoFingerCtx {
    val first  = PointerState()
    val second = PointerState()
    fun clear() { first.clear(); second.clear() }
  }
  private class TripleTapCtx {
    val start: PointF = PointF()
    var lastTapTime: Long = 0L
    fun setStart(event: MotionEvent) {
      start.set(event.getX(event.actionIndex),
      event.getY(event.actionIndex))
      lastTapTime = event.getEventTime()
    }
    fun clear() { start.set(0f, 0f); lastTapTime = 0L }
  }

  // Shared gesture contexts (one per detector instance)
  private val twoCtx = TwoFingerCtx()
  private val triCtx = TripleTapCtx()

  // Triple-tap anchors & timing
  private var gestureStartTime = 0L

  // Convenience: is the current action point inside the (inset) header?
  private fun isActionInHeader(event: MotionEvent): Boolean {
    val i = event.actionIndex
    return headerBoundsInset().contains(event.getX(i).toInt(), event.getY(i).toInt())
  }

  // Scheduling infra with versioning to invalidate stale lambdas
  private val mainHandler = Handler(Looper.getMainLooper())
  private val scheduledRunnables: MutableList<Runnable> = mutableListOf()
  private var stateVersion = 0L  // stateVersion++ to invalidate stale lambdas

  // Event-consumption: once we engage a gesture (enter an Active state),
  // we consume the rest of the stream until the final UP/CANCEL.
  private var consumeStream = false
  private var currentTouchView: View? = null

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

  // --- State pattern ---------------------------------------------------------
  // Singleton state instances (per detector instance)
  private val idleState               = Idle()
  private val tripleTapWaitSecondState = TripleTapWaitSecond()
  private val tripleTapWaitThirdState  = TripleTapWaitThird()
  private val twoFingerFirstDownState  = TwoFingerFirstDown()
  private val twoFingerSecondDownState = TwoFingerSecondDown()
  /** Terminal absorbing states for an aborted and completed gestures;
   *  remains Active to keep consuming until UP/CANCEL or disposed. */
  private val cancelledState           = Cancelled()
  private val completedState           = Completed()

  private var state: State = idleState

  private fun setState(newState: State, event: MotionEvent? = null) {
    if (state === newState) return

    // If we are leaving a gesture family, clear its context here (NOT in onExit of the family base).
    // XXX: FIXME.  This is a hack to work around the fact that we don't have a clear distinction between
    // the two gesture families in the state machine.
    if (state is TwoFingers && newState !is TwoFingers) twoCtx.clear()
    if (state is TripleTap  && newState !is TripleTap)  triCtx.clear()

    state.onExit()
    state = newState
    stateVersion++
    state.onEnter(event)
  }

  private abstract inner class State {
    open fun onEnter(event: MotionEvent?) {}
    open fun onExit() {}
    open fun handleActionDown(event: MotionEvent) {}
    open fun handlePointerDown(event: MotionEvent) {}
    open fun handleMove(event: MotionEvent) {}
    open fun handlePointerUp(event: MotionEvent) {}
    open fun handleActionUp(event: MotionEvent) {}
    open fun handleCancel(event: MotionEvent) {}
  }

  // Active states consume the touch stream while engaged
  private abstract inner class Active : State() {
    override fun onEnter(event: MotionEvent?) {
      // Start consuming this touch stream and prevent parent intercept
      consumeStream = true
      currentTouchView?.parent?.requestDisallowInterceptTouchEvent(true)
    }
    override fun handleCancel(event: MotionEvent) {
      // End of stream via CANCEL: release consumption and return to Idle
      consumeStream = false
      setState(idleState, event)
    }
  }

  private inner class Cancelled : Active() {
    override fun handleActionDown(event: MotionEvent) {}
    override fun handlePointerDown(event: MotionEvent) {}
    override fun handleMove(event: MotionEvent) {}
    override fun handlePointerUp(event: MotionEvent) { /* ignore non-final UPs */ }
    override fun handleActionUp(event: MotionEvent) {
      consumeStream = false
      setState(idleState, event)
    }
  }

  private inner class Completed : Active() {
    override fun handleActionDown(event: MotionEvent) {}
    override fun handlePointerDown(event: MotionEvent) {}
    override fun handleMove(event: MotionEvent) {}
    override fun handlePointerUp(event: MotionEvent) { /* ignore non-final UPs */ }
    override fun handleActionUp(event: MotionEvent) {
      consumeStream = false
      setState(idleState, event)
    }
  }

  // Gesture-family base states ------------------------------------------------
  private abstract inner class TripleTap : Active() {
    protected val tri: TripleTapCtx get() = triCtx
    protected val touchSlop: Int by lazy { ViewConfiguration.get(context).scaledTouchSlop }
    protected val touchSlopSq: Float by lazy { (touchSlop * touchSlop).toFloat() }

    override fun handlePointerDown(event: MotionEvent) {
      Log.d(TAG, "TRIPLE_TAP: extra pointer down -> reset")
      cancelGesture(event)
    }
    override fun handleMove(event: MotionEvent) {
      // Only one pointer is allowed in triple-tap; jitter tolerance by slop
      val idx = event.actionIndex
      val dx = event.getX(idx) - tri.start.x
      val dy = event.getY(idx) - tri.start.y
      if ((dx * dx + dy * dy) > touchSlopSq) {
        Log.d(TAG, "TRIPLE_TAP: MOVE > slop -> reset")
        cancelGesture(event)
      }
    }
    override fun handlePointerUp(event: MotionEvent) {
      // DOWNs drive the logic; UPs are ignored during triple-tap sequencing
    }
  }

  private abstract inner class TwoFingers : Active() {
    protected val two: TwoFingerCtx get() = twoCtx
    protected fun firstIndex(event: MotionEvent): Int = event.findPointerIndex(two.first.id)
    protected fun secondIndex(event: MotionEvent): Int = event.findPointerIndex(two.second.id)

    override fun handlePointerDown(event: MotionEvent) {
      // Any third pointer cancels the two-finger gesture
      Log.d(TAG, "TWO_FINGER: Third pointer down -> reset")
      cancelGesture(event)
    }

    /** Common validator for a single pointer against header containment and drift. */
    private fun validatePointerInHeaderAndDrift(
      event: MotionEvent,
      pointerId: Int,
      start: PointF,
      header: Rect,
      label: String
    ): Boolean {
      val idx = event.findPointerIndex(pointerId)
      if (idx == -1) { cancelGesture(event); return false }
      val px = event.getX(idx); val py = event.getY(idx)
      if (!header.contains(px.toInt(), py.toInt())) {
        Log.d(TAG, "TWO_FINGER: MOVE outside header ($label)")
        cancelGesture(event); return false
      }
      val dx = px - start.x; val dy = py - start.y
      if ((dx * dx + dy * dy) > driftToleranceSq) {
        Log.d(TAG, "TWO_FINGER: MOVE beyond drift ($label)")
        cancelGesture(event); return false
      }
      return true
    }

    protected fun validateFirstPointerInHeaderAndDrift(event: MotionEvent): Boolean {
      val header = headerBoundsInset()
      return validatePointerInHeaderAndDrift(
        event,
        two.first.id,
        two.first.start,
        header,
        label = "first (before second)"
      )
    }

    protected fun validateBothPointersInHeaderAndDrift(event: MotionEvent): Boolean {
      val header = headerBoundsInset()
      if (!validatePointerInHeaderAndDrift(event, two.first.id, two.first.start, header, label = "first")) {
        return false
      }
      return validatePointerInHeaderAndDrift(event, two.second.id, two.second.start, header, label = "second")
    }

    protected fun scheduleHoldCompletionFromFirst(now: Long, expectedState: State) {
      val timeUntilHold = (two.first.downTime + holdDurationMs) - now
      scheduleRunnable(kotlin.math.max(0L, timeUntilHold)) {
        if (state === expectedState) {
          Log.d(TAG, "TWO_FINGER: hold complete -> trigger")
          completeGesture()
        }
      }
    }

    protected fun startHapticsLoop(firstDelayMs: Long, intervalMs: Long, expectedState: State) {
      val tick = object : Runnable {
        override fun run() {
          if (state !== expectedState) return
          try {
            Log.d(TAG, "TWO_FINGER: haptic tick")
          } finally {
            scheduleRunnable(intervalMs, this)
          }
        }
      }
      scheduleRunnable(firstDelayMs, tick)
    }

    override fun handlePointerUp(event: MotionEvent) {
      val idx = event.actionIndex
      val upId = event.getPointerId(idx)
      if (upId == two.first.id || upId == two.second.id) { cancelGesture(event) }
    }
    override fun handleActionUp(event: MotionEvent) {
      val idx = event.actionIndex
      val upId = event.getPointerId(idx)
      if (upId == two.first.id || upId == two.second.id) { cancelGesture(event) }
    }
  }

  // Individual gesture states ------------------------------------------------

  private inner class Idle : State() {
    override fun handleActionDown(event: MotionEvent) {
      if (!isActionInHeader(event)) return
      val now = event.getEventTime()

      gestureStartTime = now
      // Timeout policy:
      // - exitGestureTimeoutMs is a hard ceiling for any gesture attempt (safety net).
      // - tripleTapWindowMs bounds the total time for 3 taps; if set longer than exitGestureTimeoutMs,
      //   the overall timeout may pre-empt the triple-tap window; ditto for two fingers touch.
      // Overall safety window (8s by default)
      scheduleRunnable(exitGestureTimeoutMs.toLong()) {
        if (now() - gestureStartTime >= exitGestureTimeoutMs) {
          Log.d(TAG, "Overall window timeout -> reset")
          cancelGesture(event)
        }
      }

      when (currentGestureType()) {
        AccessibilityModeExitGestureType.TRIPLE_TAP_DEBUG -> {
          // Seed triple-tap context
          setState(tripleTapWaitSecondState, event)
          // Overall triple-tap window still enforced here
          scheduleRunnable(tripleTapWindowMs.toLong()) {
            if (gestureStartTime != 0L && now() - gestureStartTime >= tripleTapWindowMs) {
              Log.d(TAG, "TRIPLE_TAP: overall window timeout"); cancelGesture()
            }
          }
        }
        AccessibilityModeExitGestureType.TWO_FINGER_HEADER_HOLD -> {
          // Seed two-finger context with first pointer
          setState(twoFingerFirstDownState, event)
          // Require second finger soon
          scheduleRunnable(pointerTimeoutMs.toLong()) {
            if (state === twoFingerFirstDownState) { Log.d(TAG, "TWO_FINGER: second finger timeout"); cancelGesture() }
          }
        }
      }
    }
  }

  private inner class TripleTapWaitSecond : TripleTap() {
    override fun onEnter(event: MotionEvent?) {
      super.onEnter(event)
      tri.setStart(requireNotNull(event) { "TripleTapWaitSecond requires MotionEvent seed" })
      // Inter-tap timeout for the second tap
      scheduleRunnable(tripleTapIntervalMs.toLong()) {
        if (state === tripleTapWaitSecondState && now() - tri.lastTapTime >= tripleTapIntervalMs) {
          Log.d(TAG, "TRIPLE_TAP: inter-tap timeout (second)"); cancelGesture()
        }
      }
    }
    override fun handleActionDown(event: MotionEvent) {
      if (!isActionInHeader(event)) return
      tri.lastTapTime = now()
      setState(tripleTapWaitThirdState, event)
    }
  }

  private inner class TripleTapWaitThird : TripleTap() {
    override fun onEnter(event: MotionEvent?) {
      // Inter-tap timeout waiting for the third tap
      scheduleRunnable(tripleTapIntervalMs.toLong()) {
        if (state === tripleTapWaitThirdState && now() - tri.lastTapTime >= tripleTapIntervalMs) {
          Log.d(TAG, "TRIPLE_TAP: inter-tap timeout (third)"); cancelGesture()
        }
      }
    }
    override fun handleActionDown(event: MotionEvent) {
      if (!isActionInHeader(event)) return
      Log.d(TAG, "TRIPLE_TAP: third tap -> trigger")
      completeGesture(event)
    }
  }

  private inner class TwoFingerFirstDown : TwoFingers() {
    override fun onEnter(event: MotionEvent?) {
      super.onEnter(event)
      two.first.setStart(requireNotNull(event) { "TwoFingerFirstDown requires MotionEvent seed" })
    }
    override fun handlePointerDown(event: MotionEvent) {
      val inHeader = isActionInHeader(event)
      if (!inHeader) { Log.d(TAG, "SECOND_POINTER_DOWN outside header"); cancelGesture(); return }

      setState(twoFingerSecondDownState, event)
      Log.d(TAG, "SECOND_POINTER_DOWN id=${two.second.id} start=(${two.second.start.x.toInt()},${two.second.start.y.toInt()})")

      // Hold completion & haptics managed via base helpers
      val now = event.getEventTime()
      scheduleHoldCompletionFromFirst(now, twoFingerSecondDownState)
      val hapticIntervalPref = SignalStore.accessibilityMode.exitHapticFeedbackIntervalMs
      if (hapticIntervalPref > 0) {
        val hapticInterval = hapticIntervalPref.toLong()
        val firstHapticDelay = kotlin.math.max(0L, two.first.downTime + hapticInterval - now)
        startHapticsLoop(firstHapticDelay, hapticInterval, twoFingerSecondDownState)
      }
    }

    override fun handleMove(event: MotionEvent) {
      // Allow small drift within header while waiting for the second finger
      validateFirstPointerInHeaderAndDrift(event)
    }
  }

  private inner class TwoFingerSecondDown : TwoFingers() {
    override fun onEnter(event: MotionEvent?) {
      super.onEnter(event)
      two.second.setStart(requireNotNull(event) { "TwoFingerSecondDown requires MotionEvent seed" })
    }
    override fun handleMove(event: MotionEvent) {
      validateBothPointersInHeaderAndDrift(event)
    }
  }

  // --- Public touch entry point --------------------------------------------
  @MainThread
  override fun onTouch(view: View, event: MotionEvent): Boolean {
    if (accessibilityManager.isEnabled && accessibilityManager.isTouchExplorationEnabled) return false

    currentTouchView = view

    // Dispatch to current state; states decide gates, we decide consumption.
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> state.handleActionDown(event)
      MotionEvent.ACTION_POINTER_DOWN -> state.handlePointerDown(event)
      MotionEvent.ACTION_MOVE -> state.handleMove(event)
      MotionEvent.ACTION_POINTER_UP -> state.handlePointerUp(event)
      MotionEvent.ACTION_UP -> state.handleActionUp(event)
      MotionEvent.ACTION_CANCEL -> state.handleCancel(event)
      else -> { /* no-op */ }
    }
    return consumeStream
  }

  /**
   * Finish successfully but keep consuming the rest of this touch stream.
   * Cancels timers and clears contexts, moves to a terminal Active state, then posts the trigger.
   */
  private fun completeGesture(event: MotionEvent? = null) {
    resetState(completedState, event)
    // Use post to avoid re-entrancy during current dispatch; do not version-guard this.
    mainHandler.post { triggerGesture() } // XXX: Could go to CompletedState.onEnter()
  }

  /**
   * Abort the current gesture but continue consuming the stream until UP/CANCEL.
   * Cancels timers and clears contexts, then moves into a terminal Active state.
   */
  private fun cancelGesture(event: MotionEvent? = null) {
    resetState(cancelledState, event)
  }

  private fun triggerGesture() {
    Log.i(TAG, "Exit gesture triggered")
    onTriggered()
  }

  /**
   * Cancel all pending callbacks (including haptics/hold timers), clear gesture context, and
   * return to Idle. Safe to call on success or cancellation.
   *
   * Note: Does not clear consumption; we keep consuming the current stream until UP/CANCEL.
   */
  private fun resetState(newState: State = idleState, event: MotionEvent? = null) {
    cancelScheduledRunnables()
    gestureStartTime = 0L
    setState(newState, event)
  }

  /**
   * Dispose detector resources and cancel any pending timers. Call from lifecycle teardown.
   */
  fun dispose() {
    // Ensure all scheduled work is cancelled and state cleared
    resetState()
  }
}
