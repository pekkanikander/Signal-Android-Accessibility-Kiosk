/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.accessibility

import android.content.Context
import android.graphics.Rect
import android.graphics.PointF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityManager
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.keyvalue.SignalStore
import androidx.annotation.MainThread

/**
 * Detector for Accessibility Mode exit gestures.
 * Production: two-finger header hold, TBD.
 * Debug: triple-tap header.
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
  private val driftTolerancePx:   Float by lazy {  context.resources.displayMetrics.density * SignalStore.accessibilityMode.exitGestureDriftDp }
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
  private fun now(): Long = SystemClock.uptimeMillis()
  private val driftToleranceSq: Float by lazy { driftTolerancePx * driftTolerancePx }

  // Pointer state for gestures, seeded from MotionEvent
  private class PointerState {
    var id: Int = -1                                // pointer id
    var downTime: Long = 0L                         // pointer down time
    val start: PointF = PointF()                    // pointer down position
    fun setStart(event: MotionEvent) {              // set pointer state from MotionEvent
      val idx = event.actionIndex
      id = event.getPointerId(idx);
      downTime = event.getEventTime()
      start.set(event.getX(idx), event.getY(idx))
    }
    fun clear() {                                    // clear pointer state
      id = -1;
      downTime = 0L;
      start.set(0f, 0f)
    }
  }

  // --- Gesture contexts (per-family, mutable) --------------------------------
  private open class GestureCtx
  private class TwoFingerCtx : GestureCtx() {
    val first  = PointerState()                     // state of the first finger in two-finger gesture
    val second = PointerState()                     // state of the second finger in two-finger gesture
    fun clear() { first.clear(); second.clear() }
  }
  private class TripleTapCtx : GestureCtx() {
    val start: PointF = PointF()                    // start position of the triple-tap gesture
    var lastTapTime: Long = 0L                      // time of the last tap in the triple-tap gesture
    fun setStart(event: MotionEvent) {
      start.set(event.getX(event.actionIndex),
      event.getY(event.actionIndex))
      lastTapTime = event.getEventTime()
    }
    fun clear() { start.set(0f, 0f); lastTapTime = 0L }
  }


  // Convenience: is the current action point inside the (inset) header?
  private fun isActionInHeader(event: MotionEvent): Boolean {
    val i = event.actionIndex
    return headerBoundsInset().contains(event.getX(i).toInt(), event.getY(i).toInt())
  }

  // Scheduling infra with versioning to invalidate stale callbacks
  private val mainHandler = Handler(Looper.getMainLooper())
  // XXX: Move inside VersionedScheduler?
  private var stateVersion = 0L  // stateVersion++ to invalidate stale callbacks

  /**
   * Centralized versioned scheduler. Any task scheduled through this helper will
   * be a no-op if the [stateVersion] has changed since scheduling.
   */
  private inner class VersionedScheduler(
    private val handler: Handler,
    private val versionProvider: () -> Long,
  ) {
    private val entries: MutableList<Runnable> = mutableListOf()

    fun schedule(delayMs: Long, action: () -> Unit): Runnable {
      return schedule(delayMs, action, { true })
    }
    fun schedule(delayMs: Long, action: () -> Unit, predicate: () -> Boolean) : Runnable {
      return schedule(delayMs, 0L, action, predicate)
    }
    fun schedule(firstDelayMs: Long, interval: Long, action: () -> Unit, predicate: () -> Boolean) : Runnable {
      val myVersion = versionProvider()
      val runnable = object : Runnable {
        override fun run() {
          try {
            if (myVersion == versionProvider() && predicate()) {
              action()
            }
          } finally {
            if (myVersion == versionProvider() && predicate() && interval > 0) {
              handler.postDelayed(this, interval)
            } else {
              entries.remove(this)
            }
          }
        }
      }
      entries.add(runnable)
      handler.postDelayed(runnable, firstDelayMs)
      return runnable
    }

    /** Cancel a specific Runnable. */
    fun cancel(runnable: Runnable?) {
      if (runnable == null) return
      handler.removeCallbacks(runnable)
      entries.remove(runnable)
    }

    /* Cancel all scheduled runnables. */
    fun cancelAll() {
      entries.forEach { handler.removeCallbacks(it) }
      entries.clear()
    }

    /** Convenience helper to schedule a cancellation of the current gesture. */
    fun scheduleCancelGestureAt(delayMs: Long) {
      schedule(delayMs, { cancelGesture() }, { true })
    }

    /** Schedule a conditional cancellation; predicate is evaluated when the Runnable runs. */
    fun scheduleCancelGestureAt(delayMs: Long, predicate: () -> Boolean) {
      schedule(delayMs, { cancelGesture() }, predicate)
    }
  }

  private val scheduler = VersionedScheduler(mainHandler) { stateVersion }

  /**
   * Haptics controller. Owns haptic effects and schedules periodic ticks while
   * [predicate] remains true. It uses the detector's scheduler so ticks are
   * versioned and cancelled automatically on state changes.
   */
  private inner class HapticsController {
    var tick: Runnable? = null
    fun start(firstDelayMs: Long, interval: Long, predicate: () -> Boolean) {
      tick = scheduler.schedule(firstDelayMs, interval, {
        currentTouchView?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
      }, predicate)
    }
    fun confirm() {
      if (Build.VERSION.SDK_INT >= 30) {
        currentTouchView?.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
      } else {
        currentTouchView?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
      }
      scheduler.cancel(tick)
      tick = null
    }
    fun reject() {
      scheduler.cancel(tick)
      tick = null
    }
  }

  private val haptics = HapticsController()

  // --- Outer state machine ---------------------------------------------------
  private sealed class OuterState {
    object Idle      : OuterState()
    object Detecting : OuterState()
    object Completed : OuterState()
    object Cancelled : OuterState()
  }
  private var outerState: OuterState = OuterState.Idle

  // Event-consumption: once we engage a gesture (enter an Active state),
  // we consume the rest of the stream until the final UP/CANCEL.
  private var consumeStream = false
  private var currentTouchView: View? = null


  // --- State pattern ---------------------------------------------------------
  // Singleton state instances (per detector instance)
  private val idleState                = Idle()
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

    state.onExit()
    state = newState
    stateVersion++
    state.onEnter(event)
  }

  private abstract inner class State {
    open fun onEnter(event: MotionEvent?) {}
    open fun onExit() {}
    open fun handleActionDown( event: MotionEvent) {}
    open fun handlePointerDown(event: MotionEvent) {}
    open fun handleMove(       event: MotionEvent) {}
    open fun handlePointerUp(  event: MotionEvent) {}
    open fun handleActionUp(   event: MotionEvent) {}
    open fun handleCancel(     event: MotionEvent) {}
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
      outerState = OuterState.Idle
    }
  }

  private inner class Cancelled : Active() {
    override fun onEnter(          event: MotionEvent?) {
      haptics.reject()
      super.onEnter(event)
    }
    override fun handleActionDown( event: MotionEvent) {}
    override fun handlePointerDown(event: MotionEvent) {}
    override fun handleMove(       event: MotionEvent) {}
    override fun handlePointerUp(  event: MotionEvent) { /* ignore non-final UPs */ }
    override fun handleActionUp(   event: MotionEvent) {
      consumeStream = false
      setState(idleState, event)
      outerState = OuterState.Idle
    }
  }

  private inner class Completed : Active() {
    override fun onEnter(          event: MotionEvent?) {
      haptics.confirm()
      super.onEnter(event)
    }
    override fun handleActionDown( event: MotionEvent) {}
    override fun handlePointerDown(event: MotionEvent) {}
    override fun handleMove(       event: MotionEvent) {}
    override fun handlePointerUp(  event: MotionEvent) { /* ignore non-final UPs */ }
    override fun handleActionUp(   event: MotionEvent) {
      consumeStream = false
      setState(idleState, event)
      outerState = OuterState.Idle
    }
  }

  // Geometry helpers — XXX to be cleaned
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

  // Gesture-family base states ------------------------------------------------
  private abstract inner class InnerState<C : GestureCtx> : Active() {
    protected abstract val ctx: C
  }

  // Per-detector gesture context (owned by the detector instance).
  // Placed next to the family state definitions for locality.
  // NOTE: shared between substates, but not shared between (potential) detector instances, hence not static.
  private val triCtx = TripleTapCtx()

  private abstract inner class TripleTap : InnerState<TripleTapCtx>() {
    override fun onExit() {
      ctx.clear()
    }
    override val ctx: TripleTapCtx get() = triCtx
    protected val touchSlop: Int by lazy { ViewConfiguration.get(context).scaledTouchSlop }
    protected val touchSlopSq: Float by lazy { (touchSlop * touchSlop).toFloat() }

    override fun handlePointerDown(event: MotionEvent) {
      // extra pointer cancels triple-tap
      cancelGesture(event)
    }
    override fun handleMove(event: MotionEvent) {
      // Only one pointer is allowed in triple-tap; jitter tolerance by slop
      val idx = event.actionIndex
      val dx = event.getX(idx) - ctx.start.x
      val dy = event.getY(idx) - ctx.start.y
      if ((dx * dx + dy * dy) > touchSlopSq) {
        cancelGesture(event)
      }
    }
    override fun handlePointerUp(event: MotionEvent) {
      // DOWNs drive the logic; UPs are ignored during triple-tap sequencing
    }
  }

  // NOTE: not shared between (potential) detector instances, hence not static.
  private val twoCtx = TwoFingerCtx()

  private abstract inner class TwoFingers : InnerState<TwoFingerCtx>() {
    override fun onExit() {
      ctx.clear()
    }
    override val ctx: TwoFingerCtx get() = twoCtx

    override fun handlePointerDown(event: MotionEvent) {
      // Any third pointer cancels the two-finger gesture
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
        cancelGesture(event); return false
      }
      val dx = px - start.x; val dy = py - start.y
      if ((dx * dx + dy * dy) > driftToleranceSq) {
        cancelGesture(event); return false
      }
      return true
    }

    protected fun validateFirstPointerInHeaderAndDrift(event: MotionEvent): Boolean {
      val header = headerBoundsInset()
      return validatePointerInHeaderAndDrift(
        event,
        ctx.first.id,
        ctx.first.start,
        header,
        label = "first (before second)"
      )
    }

    protected fun validateBothPointersInHeaderAndDrift(event: MotionEvent): Boolean {
      val header = headerBoundsInset()
      if (!validatePointerInHeaderAndDrift(event, ctx.first.id, ctx.first.start, header, label = "first")) {
        return false
      }
      return validatePointerInHeaderAndDrift(event, ctx.second.id, ctx.second.start, header, label = "second")
    }

    protected fun scheduleHoldCompletionFromFirst(now: Long, expectedState: State) {
      val timeUntilHold = maxOf(0L, (ctx.first.downTime + holdDurationMs) - now)
      scheduler.schedule(timeUntilHold, { completeGesture() }, { state === expectedState })
    }

    protected fun startHapticsLoop(firstDelayMs: Long, intervalMs: Long, expectedState: State) {
      haptics.start(firstDelayMs, intervalMs) { state === expectedState && outerState === OuterState.Detecting }
    }

    override fun handlePointerUp(event: MotionEvent) {
      val idx = event.actionIndex
      val upId = event.getPointerId(idx)
      if (upId == ctx.first.id || upId == ctx.second.id) { cancelGesture(event) }
    }
    override fun handleActionUp(event: MotionEvent) {
      val idx = event.actionIndex
      val upId = event.getPointerId(idx)
      if (upId == ctx.first.id || upId == ctx.second.id) { cancelGesture(event) }
    }
  }

  // Individual gesture states ------------------------------------------------
  // gestureStartTime removed: use captured seed times in scheduled runnables instead

  private inner class Idle : State() {
    override fun handleActionDown(event: MotionEvent) {
      if (!isActionInHeader(event)) return

      // Overall timeout policy:
      // - exitGestureTimeoutMs is a hard ceiling for any gesture attempt (safety net).
      // - tripleTapWindowMs bounds the total time for 3 taps; if set longer than exitGestureTimeoutMs,
      //   the overall timeout may pre-empt the triple-tap window; ditto for two fingers touch.
      // Overall safety window (8s by default)
      scheduler.scheduleCancelGestureAt(exitGestureTimeoutMs.toLong())

      when (currentGestureType()) {
        AccessibilityModeExitGestureType.TripleTap -> {
          // Seed triple-tap context
          setState(tripleTapWaitSecondState, event)
          outerState = OuterState.Detecting
          // Overall triple-tap window still enforced here
          scheduler.scheduleCancelGestureAt(tripleTapWindowMs.toLong())
        }
        AccessibilityModeExitGestureType.ChordSlideUp -> {
          // Seed two-finger context with first pointer
          setState(twoFingerFirstDownState, event)
          outerState = OuterState.Detecting
          // Require second finger soon
          scheduler.scheduleCancelGestureAt(pointerTimeoutMs.toLong(), { state === twoFingerFirstDownState })
        }
        else -> {
          Log.d(TAG, "Unsupported gesture type: ${currentGestureType()}")
        }
      }
    }
  }

  private inner class TripleTapWaitSecond : TripleTap() {
    override fun onEnter(event: MotionEvent?) {
      super.onEnter(event)
      ctx.setStart(requireNotNull(event) { "TripleTapWaitSecond requires MotionEvent seed" })
      // Inter-tap timeout for the second tap
      scheduler.scheduleCancelGestureAt(pointerTimeoutMs.toLong(), { state === tripleTapWaitSecondState })
    }
    override fun handleActionDown(event: MotionEvent) {
      if (!isActionInHeader(event)) return
      ctx.lastTapTime = event.getEventTime()
      setState(tripleTapWaitThirdState, event)
    }
  }

  private inner class TripleTapWaitThird : TripleTap() {
    override fun onEnter(event: MotionEvent?) {
      // Inter-tap timeout waiting for the third tap
      scheduler.scheduleCancelGestureAt(tripleTapIntervalMs.toLong(), { state === tripleTapWaitThirdState })
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
      ctx.first.setStart(requireNotNull(event) { "TwoFingerFirstDown requires MotionEvent seed" })
    }
    override fun handlePointerDown(event: MotionEvent) {
      val inHeader = isActionInHeader(event)
      if (!inHeader) { Log.d(TAG, "SECOND_POINTER_DOWN outside header"); cancelGesture(); return }

      setState(twoFingerSecondDownState, event)
      // second pointer accepted in header; start timers/haptics

      // Hold completion & haptics managed via base helpers
      val now = event.getEventTime()
      scheduleHoldCompletionFromFirst(now, twoFingerSecondDownState)
      val hapticIntervalPref = SignalStore.accessibilityMode.exitHapticFeedbackIntervalMs
      if (hapticIntervalPref > 0) {
        val hapticInterval = hapticIntervalPref.toLong()
        val firstHapticDelay = kotlin.math.max(0L, ctx.first.downTime + hapticInterval - now)
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
      ctx.second.setStart(requireNotNull(event) { "TwoFingerSecondDown requires MotionEvent seed" })
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
    // Explicitly stop haptics when detection ends
    resetState(completedState, event)
    // Use post to avoid re-entrancy during current dispatch; do not version-guard this.
    mainHandler.post { triggerGesture() } // XXX: Could go to CompletedState.onEnter()
    outerState = OuterState.Completed
  }

  /**
   * Abort the current gesture but continue consuming the stream until UP/CANCEL.
   * Cancels timers and clears contexts, then moves into a terminal Active state.
   */
  private fun cancelGesture(event: MotionEvent? = null) {
    // Explicitly stop haptics when detection ends
    resetState(cancelledState, event)
    outerState = OuterState.Cancelled
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
    scheduler.cancelAll()
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
