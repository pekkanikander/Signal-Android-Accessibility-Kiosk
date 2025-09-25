package org.thoughtcrime.securesms.accessibility

import android.util.Log

import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** NOTE: See the end of file for state transition tables and further notes. **/

/**
 * Transparent, always-on exit gesture detector for Accessibility Mode.
 *
 * Scope: active only in the host Activity; wired from Activity.dispatchTouchEvent(..)
 * (calls onTouch and ignores its return). Policy is Transparent. Exclusive mode is designed but not implemented.
 *
 * Lifecycle: a single OuterSM instance lives for the lifetime of this detector and is
 * replaced when the selected gesture or its parameters change (Idle-only).
 * Per-attempt timers and coroutine scope exist only during Tracking and are cancelled on exit.
 */
class AccessibilityModeExitGestureDetector(
  context: Context,
  private val headerBoundsProvider: () -> Rect,
  private val onTriggered: () -> Unit
) {

  // --- Internal wiring ---

  private val appContext: Context = context.applicationContext
  private val mainHandler: Handler = Handler(Looper.getMainLooper())
  private val touchSlopPx: Int = ViewConfiguration.get(appContext).scaledTouchSlop

  // The selected gesture, which is used to select the right inner state machine
  private var selectedGesture: AccessibilityModeExitGestureType =
    AccessibilityModeExitGestureType.TripleTap

  private fun buildInner(id: AccessibilityModeExitGestureType): InnerSM = when (id) {
    AccessibilityModeExitGestureType.TripleTap     -> TripleTapSM()
    AccessibilityModeExitGestureType.ChordSlideUp  -> TripleTapSM() // ChordSlideUpSM()
    AccessibilityModeExitGestureType.ChordDial,
    AccessibilityModeExitGestureType.ChordPinchOut -> TripleTapSM() // ChordSlideUpSM() // placeholders map to default
  }

  // The outer state machine, which owns the current inner state machine
  private var outer: OuterSM = OuterSM(initialInner = buildInner(selectedGesture))

  private companion object {
    private const val TAG   = "AMExitGesture"
    private const val DEBUG = true // set to false to silence logs
  }

  // --- Public API ---

  /**
   * Update the selected gesture. May be called at any time.
   * If Tracking, the request is **queued** and applied on the next Idle entry (latest wins).
   * If [force] is true, rebuilds even if the type is unchanged (e.g., to apply new thresholds).
   */
  fun updateSelectedGesture(id: AccessibilityModeExitGestureType, force: Boolean = false) {
    // TODO: Simplify, now some code duplication with OuterSM.IdleState.onEnter()
    if (outer.isIdle()) {
      if (!force && id == selectedGesture) return
      selectedGesture = id
      outer = OuterSM(initialInner = buildInner(id))
    } else {
      if (!force && id == selectedGesture) return
      pendingGesture = id // latest wins
    }
  }
  // --- Pending gesture update ---

  private var pendingGesture: AccessibilityModeExitGestureType? = null

  /** Cancel any in-flight attempt and reset to Idle. */
  fun dispose() {
    outer.inner.transitionTo(outer.inner.Quiescent, Cause.Dispose)
    outer.transitionTo(outer.Idle, Cause.Dispose)
  }

  // Handle touch events, lie that we did not consume any of them
  fun onTouch(v: View?, event: MotionEvent): Boolean {
    if (DEBUG) Log.d(TAG, "[Detector] onTouch " + event.actionLabel())
    outer.handleEvent(event)
    return false // Transparent policy: do not consume
  }

  // --- Contract primitives ---

  /** Cause of a state transition in the gesture state machines. */
  sealed interface Cause {
    data class Touch(val ev: MotionEvent) : Cause
    object Initial  : Cause // initial transition
    object Timeout  : Cause
    object Internal : Cause // automatic transitions (e.g., Success → Idle)
    object Dispose  : Cause // disposal of the state gesture detector
  }

  /** Timers facade available only during Tracking. */
  interface TrackingTimers {
    fun schedule(delayMs: Long, block: () -> Unit)
  }

  // --- Base state machine, base class for all state machines ---

  open inner class StateMachine() {
    protected lateinit var idleRef: State
    private            var current:   State? = null
    protected open val smTag: String
      get() = this::class.simpleName ?: "SM"

    fun isCurrent(s: State): Boolean = (current === s)
    fun isIdle():            Boolean = (isCurrent(idleRef))

    public fun transitionTo(s: State, cause: Cause) {
      if (isCurrent(s)) return
      if (DEBUG) Log.d(TAG, "[" + smTag + "] " + stateName(current) + " --" + causeLabel(cause) + "--> " + stateName(s))
      current?.onExit()
      current = s
      current!!.onEnter(cause)
    }

    fun handleEvent(ev: MotionEvent) { current?.handle(ev) } // if no current state, do nothing

    open inner class State {
      open fun onEnter(cause: Cause) {}
      open fun onExit() {}

      // Default event handlers: continue on down, move; abort on up, cancel
      open fun onDown(ev: MotionEvent) { }
      open fun onMove(ev: MotionEvent) { }
      open fun onPD  (ev: MotionEvent) { }
      open fun onPU  (ev: MotionEvent) { }
      open fun onUp  (ev: MotionEvent) { transitionTo(idleRef, Cause.Touch(ev)) }
      open fun onCancel (cause: Cause) { transitionTo(idleRef, cause) }

      fun handle(ev: MotionEvent) {
        when (ev.actionMasked) {
          MotionEvent.ACTION_DOWN         -> onDown(ev)
          MotionEvent.ACTION_MOVE         -> onMove(ev)
          MotionEvent.ACTION_POINTER_DOWN -> onPD  (ev)
          MotionEvent.ACTION_POINTER_UP   -> onPU (ev)
          MotionEvent.ACTION_UP           -> onUp  (ev)
          MotionEvent.ACTION_CANCEL       -> onCancel(Cause.Touch(ev))
        }
      }
    }
  }

  // --- Outer state machine ---

  /**
   * Outer state machine: hotspot gating, pointer-cap, total-timeout, policy host.
   *
   * - Idle → Tracking (on DOWN in hotspot)
   * - Tracking (per-attempt coroutine scope + timers) → Success | Fail | Timeout
   * - Success auto-hops back to Idle (Internal)
   */
  inner class OuterSM(
    initialInner: InnerSM,
    private val maxTotalDurationMs: Long = 5_000L
  ) : StateMachine() {

    // Initialize the idle state first
    val Idle: State = IdleState()
    init {
      idleRef = Idle
    }

    var inner: InnerSM = initialInner

    private inner class IdleState : State() {
      override fun onEnter(cause: Cause) {
        // Apply any queued gesture selection change (latest wins)
        pendingGesture?.let { next ->
          pendingGesture = null
          selectedGesture = next
          // Replace the entire Outer, with a new inner; this method runs inside the old Outer
          // At this point, the old Outer is entering the Idle state. Hence, it is safe to replace it.
          outer = OuterSM(initialInner = buildInner(next))
          // The old outer is now quiescent and will be destroyed by GC.
          return
        }
        if (cause is Cause.Initial) {
          inner.transitionTo(inner.Quiescent, cause)
        }
        // Otherwise, throw if the inner state machine is not quiescent
        if (!inner.isCurrent(inner.Quiescent)) {
          throw IllegalStateException("Gesture recognition attempt Inner state machine is not quiescent")
        }
      }
      // If a finger goes down in the header, transition to Tracking and let the inner handle the event
      override fun onDown(ev: MotionEvent) {
        if (!ev.isIn(headerBoundsProvider())) return
        transitionTo(Tracking, Cause.Touch(ev)) // Create first the new timers, with a coroutine scope, for the inner state machine
        inner.handleEvent(ev)                   // Only then let the inner state machine handle the event, with the new timers
      }
    }

    private inner class TrackingState : State() {

      private var attemptScope: CoroutineScope? = null

      private val timers: TrackingTimers = object : TrackingTimers {
        override fun schedule(delayMs: Long, block: () -> Unit) {
          val s = requireNotNull(attemptScope) { "Gesture recognition attempt TrackingTimers not active" }
          if (DEBUG) Log.d(TAG, "[Outer] timers.schedule(" + delayMs + "ms)")
          s.launch(Dispatchers.Main.immediate) {
            delay(delayMs)
            if (isCurrent(Tracking)) {
              if (DEBUG) Log.d(TAG, "[Outer] timer fired after " + delayMs + "ms (state still Tracking)")
              block()
            } else if (DEBUG) {
              Log.d(TAG, "[Outer] timer fired after " + delayMs + "ms (ignored; state changed)")
            }
          }
        }
      }

      // Cancel all timers if the recognition attempt succeeds or fails
      override fun onExit() {
        attemptScope?.cancel()
        attemptScope = null
      }

      // Start a new recognition attempt, with a new coroutine scope and timers
      override fun onEnter(cause: Cause) {
        assert(attemptScope == null) { "Gesture recognition attempt already active" }
        attemptScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
        inner.onStart(timers)
        // Outer-enforced overall timeout
        timers.schedule(maxTotalDurationMs) {
          inner.transitionTo(inner.Quiescent, Cause.Timeout)
          transitionTo(Idle, Cause.Timeout)
        }
      }

      override fun onDown(ev: MotionEvent) { inner.handleEvent(ev) }
      override fun onMove(ev: MotionEvent) { inner.handleEvent(ev) }
      override fun onPU  (ev: MotionEvent) { inner.handleEvent(ev) }
      override fun onUp  (ev: MotionEvent) { inner.handleEvent(ev) }
      override fun onCancel(cause: Cause)  { inner.transitionTo(inner.Quiescent, cause); transitionTo(Idle, cause) }

      // Pointer-cap: abort if a new pointer would exceed maxPointers
      override fun onPD(ev: MotionEvent)   {
        if (ev.pointerCount > inner.maxPointers) {
          inner.transitionTo(inner.Quiescent, Cause.Touch(ev))
          transitionTo(Idle, Cause.Touch(ev))
        } else {
          inner.handleEvent(ev)
        }
      }
    }
    val Tracking: State = TrackingState()

    // Success state: transition to Idle, then fire exit callback on main thread
    private inner class SuccessState : State() {
      override fun onEnter(cause: Cause) {
        transitionTo(Idle, Cause.Internal)
        // Fire exit callback on main thread, non-reentrant
        mainHandler.post { onTriggered.invoke() }
      }
    }
    val Success: State = SuccessState()

    init {
      transitionTo(Idle, Cause.Initial)
    }
  }

  // --- Inner state machine, base class for all gesture-specific state machines ---

  /**
   * Gesture-specific recogniser base.
   * Responsibilities: pointer semantics, dwell/gap windows, spatial rules, success/fail.
   * Knows nothing about policy. Timers are provided only during Tracking via onStart.
   */
  open abstract inner class InnerSM(val maxPointers: Int) : StateMachine() {
    // Initialize the idle state first
    private inner class QuiescentState : State() {
      override fun onDown(ev: MotionEvent) {
        transitionTo(initialState(), Cause.Touch(ev))
      }
    }
    val Quiescent: State = QuiescentState()
    init {
      idleRef = Quiescent
    }

    // Initial state of the inner state machine
    abstract fun initialState(): State

    protected lateinit var timers: TrackingTimers

    /** Start of an attempt: Outer supplies the timers facade. */
    public fun onStart(timers: TrackingTimers) {
      this.timers = timers
    }

    protected fun succeed() {
      if (DEBUG) Log.d(TAG, "[" + smTag + "] SUCCESS")
      transitionTo(Quiescent, Cause.Internal)
      outer.transitionTo(outer.Success, Cause.Internal)
    }
    protected fun fail() {
      if (DEBUG) Log.d(TAG, "[" + smTag + "] FAIL")
      transitionTo(Quiescent, Cause.Internal)
      outer.transitionTo(outer.Idle,    Cause.Internal)
    }

    // Default for gesture-specific handlers: fail on up, pointer-up — override as needed
    protected open inner class InnerState : State() {
      override fun onUp(ev: MotionEvent) { fail() }
      override fun onPU(ev: MotionEvent) { fail() }
    }
  }

  // --- Concrete inners ---

  /** Triple-tap recogniser (1 finger). */
  inner class TripleTapSM : InnerSM(maxPointers = 1) {
    private val maxGapMs: Long = 600L
    private var downX: Float = 0f
    private var downY: Float = 0f

    override fun initialState(): State = FirstTapDown

    private open inner class TapDownState : InnerState() {
      override fun onEnter(cause: Cause) {
        val ev = (cause as? Cause.Touch)!!.ev
        downX = ev.x
        downY = ev.y
      }
      override fun onMove(ev: MotionEvent) {
        val dx = kotlin.math.abs(ev.x - downX)
        val dy = kotlin.math.abs(ev.y - downY)
        if (dx > touchSlopPx || dy > touchSlopPx) fail()
      }
    }

    private open inner class TapUpState : InnerState() {
      override fun onEnter(cause: Cause) {
        timers.schedule(maxGapMs) {
          if (isCurrent(this@TapUpState)) fail()
        }
      }
    }

    // --- State machine states ---
    private inner class FirstTapDownState : TapDownState() {
      override fun onUp(ev: MotionEvent) { transitionTo(FirstTapUp, Cause.Touch(ev)) }
    }
    private val FirstTapDown  = FirstTapDownState()

    private inner class FirstTapUpState : TapUpState() {
      override fun onDown(ev: MotionEvent) { transitionTo(SecondTapDown, Cause.Touch(ev)) }
    }
    private val FirstTapUp    = FirstTapUpState()

    private inner class SecondTapDownState : TapDownState() {
      override fun onUp(ev: MotionEvent) { transitionTo(SecondTapUp, Cause.Touch(ev)) }
    }
    private val SecondTapDown = SecondTapDownState()
    private inner class SecondTapUpState : TapUpState() {
      override fun onDown(ev: MotionEvent) { transitionTo(ThirdTapDown, Cause.Touch(ev)) }
    }
    private val SecondTapUp   = SecondTapUpState()
    private inner class ThirdTapDownState : TapDownState() {
      override fun onUp(ev: MotionEvent) { succeed() }
    }
    private val ThirdTapDown  = ThirdTapDownState()
  }

  /** Two-finger chord then slide the centroid upwards by ≥20 mm before any finger lifts. */
  inner class ChordSlideUpSM : InnerSM(maxPointers = 2) {
    private val chordMaxGapMs: Long = 250L
    private val slideUpThresholdPx: Float = mmToPx(20f)

    private var firstId: Int = -1
    private var secondId: Int = -1
    private var centroidStartY: Float = 0f

    override fun initialState(): State = AwaitSecond

    private inner class AwaitSecondState : InnerState() {
      override fun onEnter(cause: Cause) {
        val down = (cause as? Cause.Touch)!!.ev
        firstId = down.getPointerId(down.actionIndex)
        secondId = -1
        centroidStartY = down.getY(down.actionIndex)
        timers.schedule(chordMaxGapMs) {
          if (isCurrent(AwaitSecond)) fail()
        }
      }
      override fun onPD(ev: MotionEvent) {
        secondId = ev.getPointerId(ev.actionIndex)
        centroidStartY = currentCentroidY(ev)
        transitionTo(PairLocked, Cause.Touch(ev))
      }
    }
    private val AwaitSecond = AwaitSecondState()

    private inner class PairLockedState : InnerState() {
      override fun onMove(ev: MotionEvent) {
        val cy = currentCentroidY(ev)
        if (centroidStartY - cy >= slideUpThresholdPx) {
          succeed()
        }
      }
      override fun onPU(ev: MotionEvent) {
        fail()
      }
      override fun onUp(ev: MotionEvent) {
        fail()
      }
    }
    private val PairLocked = PairLockedState()

    private fun currentCentroidY(ev: MotionEvent): Float {
      val i1 = ev.findPointerIndex(firstId)
      val i2 = ev.findPointerIndex(secondId)
      if (i1 < 0 || i2 < 0) return ev.getY(0)
      return (ev.getY(i1) + ev.getY(i2)) / 2f
    }
  }

  // --- Helpers ---

  private fun MotionEvent.isIn(r: Rect): Boolean {
    val x = getX(0).toInt()
    val y = getY(0).toInt()
    return r.contains(x, y)
  }

  private fun mmToPx(mm: Float): Float {
    val dpi = appContext.resources.displayMetrics.densityDpi.toFloat()
    return mm * (dpi / 25.4f)
  }

  private fun MotionEvent.actionLabel(): String = MotionEvent.actionToString(action)

  private fun causeLabel(c: Cause): String = when (c) {
    is Cause.Touch -> "Touch(" + c.ev.actionLabel() + ")"
    else           -> (c::class.simpleName ?: "Cause")
  }

  private fun stateName(s: StateMachine.State?): String = s?.let { it::class.simpleName ?: "<anon>" } ?: "<null>"
}

/*
==============================================================
Transition table — OuterSM <-> InnerSM (common to all gestures)
==============================================================

Event / Condition                     Outer: state → next                 Inner: state → next
---------------------------------------------------------------------------------------------------------------
Idle entry with changed parameters    Idle  → Idle (rebuild Outer+Inner)  (old) any → Quiescent; (new) Quiescent
DOWN @ hotspot                        Idle     → Tracking                 Quiescent → initialState (gesture-specific)
MOVE                                  Tracking → Tracking                 gesture-specific handling
POINTER_DOWN within maxPointers       Tracking → Tracking                 gesture-specific handling
POINTER_DOWN exceeds maxPointers      Tracking → Idle                     any → Quiescent   (outer aborts)
POINTER_UP                            Tracking → inner dependent          fail() unless subclass overrides
UP                                    Tracking → inner dependent          fail() unless subclass overrides
CANCEL (system/Dialog etc.)           Tracking → Idle                     any → Quiescent   (external abort)
Total timeout (~5 s)                  Tracking → Idle                     any → Quiescent   (outer timeout)
Dispose()                             any → Idle                          any → Quiescent

Inner succeed()                       Tracking → Success → Idle           any → Quiescent   (inner self-cleans)
Inner fail()                          Tracking → Idle                     any → Quiescent   (inner self-cleans)

Notes:
- Parameter changes are handled by rebuilding the entire Outer+Inner structure
- Pointer cap is enforced only on ACTION_POINTER_DOWN (new finger arriving).
- On Idle→Tracking hand-off: enter Tracking first (scope+timers live), then forward the same DOWN to the inner.
- Gesture selection changes received during Tracking are queued (latest-wins) and applied on next Idle entry.
---------------------------------------------------------------------------------------------------------------


======================================
InnerSM — TripleTapSM (1-finger)
======================================

State machine:
  Quiescent → FirstTapDown → FirstTapUp → SecondTapDown → SecondTapUp → ThirdTapDown → (UP) → Success

Event / Condition                     Inner: state → next
-----------------------------------------------------------------------------------------------
onEnter(FirstTapUp/SecondTapUp)       arm gap timer (≤ 600 ms); if timer fires while still in state → Fail
MOVE in any *Down                     if motion exceeds touch slop → Fail; else Continue
UP in FirstTapDown                    → FirstTapUp
DOWN in FirstTapUp                    → SecondTapDown
UP in SecondTapDown                   → SecondTapUp
DOWN in SecondTapUp                   → ThirdTapDown
UP in ThirdTapDown                    → Success
Any unexpected UP/POINTER_UP          default InnerState behaviour → Fail
CANCEL / external abort               Outer drives → inner → Quiescent


======================================
InnerSM — ChordSlideUpSM (2-finger)
======================================

State machine:
  Quiescent → AwaitSecond --(timeout 250 ms)--> Fail
                 └─(POINTER_DOWN)→ PairLocked --(MOVE: Δy_centroid ≥ 20 mm up)--> Success
                                            └─(UP | POINTER_UP)------------------> Fail

Event / Condition                     Inner: state → next
-----------------------------------------------------------------------------------------------
onEnter(AwaitSecond)                  remember firstId, startY; arm 250 ms timer; if fires while still here → Fail
POINTER_DOWN in AwaitSecond           capture secondId; set centroidStartY; → PairLocked
MOVE in PairLocked                    if centroidStartY − currentCentroidY ≥ 20 mm → Success; else Continue
UP or POINTER_UP in PairLocked        → Fail
UP in AwaitSecond                     default InnerState behaviour → Fail
CANCEL / external abort               Outer drives → inner → Quiescent

*/
