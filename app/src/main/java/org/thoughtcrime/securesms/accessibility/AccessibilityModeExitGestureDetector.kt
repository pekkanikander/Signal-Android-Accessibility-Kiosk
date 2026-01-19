/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.accessibility

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
import org.signal.core.util.logging.Log as SignalCoreLog

// Compile-time gated logging
private object Log {
  private const val A11Y_GESTURE_TRACE_DEBUG   = true
  private const val A11Y_GESTURE_TRACE_VERBOSE = true

  fun tag(c: Class<*>) = SignalCoreLog.tag(c)

  inline fun v(tag: String, keepLonger: Boolean = false, msg: () -> String) {
    if (A11Y_GESTURE_TRACE_DEBUG) SignalCoreLog.v(tag, msg(), keepLonger)
  }

  inline fun d(tag: String, keepLonger: Boolean = false, msg: () -> String) {
    if (A11Y_GESTURE_TRACE_VERBOSE) SignalCoreLog.d(tag, msg(), keepLonger)
  }

  inline fun i(tag: String, msg: String, keepLonger: Boolean = false) =
    SignalCoreLog.i(tag, msg, keepLonger)
}

// Exit gesture configuration snapshot (see also AccessibilityModeActivity).
data class ExitGestureConfig(
  val type: AccessibilityModeExitGestureType,
  val totalTimeoutMs: Int,
  val tripleTapGapMs: Int,
  val chordSecondFingerTimeoutMs: Int,
  val headerHeightDp: Int
)

/** NOTE: See the end of file for state transition tables and further notes. **/

/**
 * Accessibility Mode – Exit Gesture Detector.
 *
 * Purpose & motivation
 * - Provide a hidden-but-simple way for a caregiver to exit the "playground" UI without disturbing
 *   everyday use. The detector observes touch at the Activity level and never intercepts events.
 *
 * Architecture (Policy vs Recognition)
 * - Gesture PolicySM: hotspot (header), pointer-cap, total timeout, attempt lifecycle, success callback.
 * - Gesture RecognitionSM: semantic rules; starts at Quiescent → initialState(); emits succeed()/fail().
 * - Timers are per-attempt via a coroutine scope; cancelled on any terminal state.
 * - Invariants: Recognition is Quiescent whenever Policy is Idle; timers never scheduled outside Tracking.
 *
 * Event routing & policy
 * - Wired via Activity.dispatchTouchEvent(): every MotionEvent is seen, return value ignored (transparent).
 * - Mapping: DOWN→onDown, MOVE→onMove, POINTER_DOWN/UP→onPD/onPU, UP→onUp, CANCEL→onCancel.
 * - Idle → Tracking: enters Tracking (arms timers) then forwards the same DOWN to Recognition.
 * - Hotspot test on DOWN only; pointer-cap enforced on POINTER_DOWN; CANCEL/timeout aborts Tracking.
 *
 * Configuration
 * - ExitGestureConfig is snapshotted in AccessibilityModeActivity.onStart() and applied via applyConfig(...).
 * - Gesture selection updates call applyConfig(...). While Tracking, updates are queued
 *   (latest-wins) and applied on the next transition to Idle.
 * - Supported gestures: TripleTap, ChordSlideUp. Others may be added by mapping in buildRecognition(...).
 *
 * Performance
 * - Transparent observe at Activity: O(1) dispatch per MotionEvent; no allocations on the hot path.
 * - Per-attempt coroutine scope exists only during Tracking; cancelled on success/fail/abort.
 * - Debug logging is guardable with DEBUG; reflection (simpleName) used only when DEBUG.
 *
 * Adding new gestures (checklist)
 * 1) Subclass RecognitionSM(maxPointers).
 * 2) Define initialState() and states; note that default UP/PU ⇒ fail.
 * 3) Use timers only after Tracking begins (provided in onStart).
 * 4) Call succeed()/fail() to finish and self-clean to Quiescent.
 * 5) Add mapping in buildRecognition(...) and include thresholds in ExitGestureConfig if needed.
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

  // Latest configuration snapshot (updated by the Activity in onStart()).
  // Defaults are conservative and will be overridden on first applyConfig().
  private var cfg: ExitGestureConfig = ExitGestureConfig(
    type = AccessibilityModeExitGestureType.TripleTap,
    totalTimeoutMs = 5_000,
    tripleTapGapMs = 600,
    chordSecondFingerTimeoutMs = 250,
    headerHeightDp = 120
  )

  // The selected gesture, which is used to select the right recognition state machine
  private var selectedGesture: AccessibilityModeExitGestureType =
    AccessibilityModeExitGestureType.TripleTap

  private fun buildRecognition(id: AccessibilityModeExitGestureType): RecognitionSM {
    val rec: RecognitionSM = when (id) {
      AccessibilityModeExitGestureType.TripleTap     -> TripleTapSM()
      AccessibilityModeExitGestureType.ChordSlideUp  -> ChordSlideUpSM()
      AccessibilityModeExitGestureType.ChordDial,
      AccessibilityModeExitGestureType.ChordPinchOut -> TripleTapSM() // TODO: implement
    }
    Log.d(TAG) { "[Build] recognition=${rec::class.simpleName} for gesture=$id" }
    return rec
  }

  // The policy state machine, which owns the current recognition state machine
  private var policy: PolicySM = PolicySM(initialRecognition = buildRecognition(selectedGesture), maxTotalDurationMs = cfg.totalTimeoutMs.toLong())

  private companion object {
    private val TAG = Log.tag(AccessibilityModeExitGestureDetector::class.java)
  }

  // --- Public API ---

  /** Replace the current configuration snapshot. Call from Activity.onStart(). */
  fun applyConfig(newCfg: ExitGestureConfig) {
    cfg = newCfg
    Log.d(TAG) {
      "[Config] type=${cfg.type} totalTimeoutMs=${cfg.totalTimeoutMs} tripleTapGapMs=${cfg.tripleTapGapMs} " +
      "chordSecondFingerTimeoutMs=${cfg.chordSecondFingerTimeoutMs} headerHeightDp=${cfg.headerHeightDp}"
    }
    // Ensure policy reflects new thresholds and (possibly) new gesture selection.
    // Force rebuild so updated total timeout and recognition mapping take effect immediately or next Idle.

    // TODO: Simplify, now some code duplication with PolicySM.IdleState.onEnter()
    if (policy.isIdle()) {
      Log.d(TAG) { "[Detector] applyConfig: applying immediately → $newCfg.type" }
      selectedGesture = newCfg.type
      policy = PolicySM(initialRecognition = buildRecognition(newCfg.type), maxTotalDurationMs = newCfg.totalTimeoutMs.toLong())
    } else {
      Log.d(TAG) { "[Detector] applyConfig: queued → $newCfg.type" }
      pendingGesture = newCfg.type // latest wins
    }
  }
  // --- Pending gesture update ---

  private var pendingGesture: AccessibilityModeExitGestureType? = null

  /** Cancel any in-flight attempt and reset to Idle. */
  fun dispose() {
    Log.d(TAG) { "[Detector] dispose(): cancelling any in-flight attempt" }
    policy.recognition.transitionTo(policy.recognition.Quiescent, Cause.Dispose)
    policy.transitionTo(policy.Idle, Cause.Dispose)
  }

  // Handle touch events, lie that we did not consume any of them
  fun onTouch(v: View?, event: MotionEvent): Boolean {
    // Log.v(TAG) { "[Detector] onTouch ${event.actionLabel()}" }
    policy.handleEvent(event)
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
      Log.v(TAG) { "[${smTag}] ${stateName(current)} --${causeLabel(cause)}--> ${stateName(s)}" }
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

  // --- Policy state machine ---

  /**
   * Policy state machine: hotspot gating, pointer-cap, total-timeout, policy host.
   *
   * - Idle → Tracking (on DOWN in hotspot)
   * - Tracking (per-attempt coroutine scope + timers) → Success | Fail | Timeout
   * - Success auto-hops back to Idle (Internal)
   */
  inner class PolicySM(
    initialRecognition: RecognitionSM,
    private val maxTotalDurationMs: Long = 5_000L
  ) : StateMachine() {

    // Initialize the idle state first
    val Idle: State = IdleState()
    init {
      idleRef = Idle
    }

    var recognition: RecognitionSM = initialRecognition

    private inner class IdleState : State() {
      override fun onEnter(cause: Cause) {
        // Apply any queued gesture selection change (latest wins)
        Log.d(TAG) { "[Policy] Idle.onEnter cause=${causeLabel(cause)} pendingGesture=$pendingGesture" }
        pendingGesture?.let { next ->
          pendingGesture = null
          selectedGesture = next
          Log.d(TAG) { "[Policy] Applying queued gesture → $next (rebuild Policy+Recognition)" }
          // Replace the entire Policy, with a new recognition; this method runs inside the old Policy
          // At this point, the old Policy is entering the Idle state. Hence, it is safe to replace it.
          policy = PolicySM(
            initialRecognition = buildRecognition(next),
            maxTotalDurationMs = cfg.totalTimeoutMs.toLong()
          )
          // The old policy is now quiescent and will be destroyed by GC.
          return
        }
        if (cause is Cause.Initial) {
          recognition.transitionTo(recognition.Quiescent, cause)
        }
        // Otherwise, throw if the recognition state machine is not quiescent
        if (!recognition.isCurrent(recognition.Quiescent)) {
          throw IllegalStateException("Gesture recognition attempt Recognition state machine is not quiescent")
        }
      }
      // If a finger goes down in the header, transition to Tracking and let the recognition handle the event
      override fun onDown(ev: MotionEvent) {
        val inHeader = ev.isIn(headerBoundsProvider())
        if (!inHeader) {
          Log.v(TAG) { "[Policy] DOWN outside header: x=${ev.getX(0).toInt()} y=${ev.getY(0).toInt()}" }
          return
        }
        Log.d(TAG) { "[Policy] DOWN in header: start Tracking" }
        transitionTo(Tracking, Cause.Touch(ev)) // Create first the new timers, with a coroutine scope, for the recognition state machine
        recognition.handleEvent(ev)             // Only then let the recognition state machine handle the event, with the new timers
      }
    }

    private inner class TrackingState : State() {

      private var attemptScope: CoroutineScope? = null

      private val timers: TrackingTimers = object : TrackingTimers {
        override fun schedule(delayMs: Long, block: () -> Unit) {
          val s = requireNotNull(attemptScope) { "Gesture recognition attempt TrackingTimers not active" }
          Log.v(TAG) { "[${smTag}] timers.schedule(${delayMs}ms)" }
          s.launch(Dispatchers.Main.immediate) {
            delay(delayMs)
            if (isCurrent(Tracking)) {
              Log.v(TAG) { "[${smTag}] timer fired after ${delayMs}ms (state still Tracking)" }
              block()
            } else {
              Log.v(TAG) { "[${smTag}] timer fired after ${delayMs}ms (ignored; state changed)" }
            }
          }
        }
      }

      // Cancel all timers if the recognition attempt succeeds or fails
      override fun onExit() {
        Log.d(TAG) { "[Policy] Tracking.onExit – cancel timers" }
        attemptScope?.cancel()
        attemptScope = null
      }

      // Start a new recognition attempt, with a new coroutine scope and timers
      override fun onEnter(cause: Cause) {
        assert(attemptScope == null) { "Gesture recognition attempt already active" }
        attemptScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
        Log.d(TAG) { "[Policy] Tracking.onEnter – gesture=$selectedGesture, maxTotal=${maxTotalDurationMs}ms" }
        recognition.onStart(timers)
        // Policy-enforced overall timeout
        timers.schedule(maxTotalDurationMs) {
          recognition.transitionTo(recognition.Quiescent, Cause.Timeout)
          transitionTo(Idle, Cause.Timeout)
        }
      }

      override fun onDown(ev: MotionEvent) { recognition.handleEvent(ev) }
      override fun onMove(ev: MotionEvent) { recognition.handleEvent(ev) }
      override fun onPU  (ev: MotionEvent) { recognition.handleEvent(ev) }
      override fun onUp  (ev: MotionEvent) { recognition.handleEvent(ev) }
      override fun onCancel(cause: Cause)  { recognition.transitionTo(recognition.Quiescent, cause); transitionTo(Idle, cause) }

      // Pointer-cap: abort if a new pointer would exceed maxPointers
      override fun onPD(ev: MotionEvent)   {
        if (ev.pointerCount > recognition.maxPointers) {
          Log.d(TAG) { "[Policy] pointer-cap exceeded: count=${ev.pointerCount} > max=${recognition.maxPointers} – abort" }
          recognition.transitionTo(recognition.Quiescent, Cause.Touch(ev))
          transitionTo(Idle, Cause.Touch(ev))
        } else {
          recognition.handleEvent(ev)
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

  // --- Recognition state machine, base class for all gesture-specific state machines ---

  /**
   * Gesture-specific recogniser base.
   * Responsibilities: pointer semantics, dwell/gap windows, spatial rules, success/fail.
   * Knows nothing about policy. Timers are provided only during Tracking via onStart.
   */
  open abstract inner class RecognitionSM(val maxPointers: Int) : StateMachine() {
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

    // Initial state of the recognition state machine
    abstract fun initialState(): State

    protected lateinit var timers: TrackingTimers

    /** Start of an attempt: Policy supplies the timers facade. */
    public fun onStart(timers: TrackingTimers) {
      this.timers = timers
    }

    protected fun succeed() {
      Log.i(TAG, "[" + smTag + "] SUCCESS")
      transitionTo(Quiescent, Cause.Internal)
      policy.transitionTo(policy.Success, Cause.Internal)
    }
    protected fun fail() {
      Log.i(TAG, "[" + smTag + "] FAIL")
      transitionTo(Quiescent, Cause.Internal)
      policy.transitionTo(policy.Idle,    Cause.Internal)
    }

    // Default for gesture-specific handlers: fail on up, pointer-up — override as needed
    protected open inner class RecognitionState : State() {
      override fun onUp(ev: MotionEvent) { fail() }
      override fun onPU(ev: MotionEvent) { fail() }
    }
  }

  // --- Concrete recognition state submachines ---

  /** Triple-tap recogniser (1 finger). */
  inner class TripleTapSM : RecognitionSM(maxPointers = 1) {
    private val maxGapMs: Long get() = cfg.tripleTapGapMs.toLong()
    private var downX: Float = 0f
    private var downY: Float = 0f

    override fun initialState(): State = FirstTapDown

    private open inner class TapDownState : RecognitionState() {
      override fun onEnter(cause: Cause) {
        val ev = (cause as? Cause.Touch)!!.ev
        downX = ev.x
        downY = ev.y
        Log.d(TAG) { "[TripleTap] down@(${downX.toInt()},${downY.toInt()})" }
      }
      override fun onMove(ev: MotionEvent) {
        val dx = kotlin.math.abs(ev.x - downX)
        val dy = kotlin.math.abs(ev.y - downY)
        if (dx > touchSlopPx || dy > touchSlopPx) {
          Log.v(TAG) { "[TripleTap] moved beyond slop: dx=${dx.toInt()} dy=${dy.toInt()} slop=${touchSlopPx}" }
          fail()
        }
      }
    }

    private open inner class TapUpState : RecognitionState() {
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
  inner class ChordSlideUpSM : RecognitionSM(maxPointers = 2) {
    private val chordMaxGapMs: Long get() = cfg.chordSecondFingerTimeoutMs.toLong()
    private val slideUpThresholdPx: Float = mmToPx(10f)

    private var firstId: Int = -1
    private var secondId: Int = -1
    private var centroidStartY: Float = 0f

    override fun initialState(): State = AwaitSecond

    private inner class AwaitSecondState : RecognitionState() {
      override fun onEnter(cause: Cause) {
        val down = (cause as? Cause.Touch)!!.ev
        firstId = down.getPointerId(down.actionIndex)
        secondId = -1
        centroidStartY = down.getY(down.actionIndex)
        Log.d(TAG) { "[Chord] first finger id=$firstId y=${centroidStartY}" }
        timers.schedule(chordMaxGapMs) {
          if (isCurrent(AwaitSecond)) fail()
        }
      }
      override fun onPD(ev: MotionEvent) {
        secondId = ev.getPointerId(ev.actionIndex)
        centroidStartY = currentCentroidY(ev)
        Log.d(TAG) { "[Chord] second finger id=$secondId centroidStartY=${centroidStartY}" }
        transitionTo(PairLocked, Cause.Touch(ev))
      }
    }
    private val AwaitSecond = AwaitSecondState()

    private inner class PairLockedState : RecognitionState() {
      override fun onMove(ev: MotionEvent) {
        val cy = currentCentroidY(ev)
        val delta = centroidStartY - cy
        Log.v(TAG) { "[Chord] move: cy=${cy} Δ=${delta} thr=${slideUpThresholdPx}" }
        if (delta >= slideUpThresholdPx) {
          succeed()
        }
      }
      override fun onPU(ev: MotionEvent) {
        Log.d(TAG) { "[Chord] POINTER_UP before threshold – fail" }
        fail()
      }
      override fun onUp(ev: MotionEvent) {
        Log.d(TAG) { "[Chord] UP before threshold – fail" }
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
Transition table — PolicySM <-> RecognitionSM (common to all gestures)
==============================================================

Event / Condition                     Policy: state → next                 Recognition: state → next
---------------------------------------------------------------------------------------------------------------
Idle entry with changed parameters    Idle  → Idle (rebuild Policy+Recognition)  (old) any → Quiescent; (new) Quiescent
DOWN @ hotspot                        Idle     → Tracking                 Quiescent → initialState (gesture-specific)
MOVE                                  Tracking → Tracking                 gesture-specific handling
POINTER_DOWN within maxPointers       Tracking → Tracking                 gesture-specific handling
POINTER_DOWN exceeds maxPointers      Tracking → Idle                     any → Quiescent   (policy aborts)
POINTER_UP                            Tracking → Recognition dependent          fail() unless subclass overrides
UP                                    Tracking → Recognition dependent          fail() unless subclass overrides
CANCEL (system/Dialog etc.)           Tracking → Idle                     any → Quiescent   (external abort)
Total timeout (~5 s)                  Tracking → Idle                     any → Quiescent   (policy timeout)
Dispose()                             any → Idle                          any → Quiescent

Recognition succeed()                       Tracking → Success → Idle           any → Quiescent   (Recognition self-cleans)
Recognition fail()                          Tracking → Idle                     any → Quiescent   (Recognition self-cleans)

Notes:
- Parameter changes are handled by rebuilding the entire Policy+Recognition structure
- Pointer cap is enforced only on ACTION_POINTER_DOWN (new finger arriving).
- On Idle→Tracking hand-off: enter Tracking first (scope+timers live), then forward the same DOWN to the recognition.
- Gesture selection changes received during Tracking are queued (latest-wins) and applied on next Idle entry.
---------------------------------------------------------------------------------------------------------------


======================================
RecognitionSM — TripleTapSM (1-finger)
======================================

State machine:
  Quiescent → FirstTapDown → FirstTapUp → SecondTapDown → SecondTapUp → ThirdTapDown → (UP) → Success

Event / Condition                     Recognition: state → next
-----------------------------------------------------------------------------------------------
onEnter(FirstTapUp/SecondTapUp)       arm gap timer (≤ 600 ms); if timer fires while still in state → Fail
MOVE in any *Down                     if motion exceeds touch slop → Fail; else Continue
UP in FirstTapDown                    → FirstTapUp
DOWN in FirstTapUp                    → SecondTapDown
UP in SecondTapDown                   → SecondTapUp
DOWN in SecondTapUp                   → ThirdTapDown
UP in ThirdTapDown                    → Success
Any unexpected UP/POINTER_UP          default RecognitionState behaviour → Fail
CANCEL / external abort               Policy drives → recognition → Quiescent


======================================
RecognitionSM — ChordSlideUpSM (2-finger)
======================================

State machine:
  Quiescent → AwaitSecond --(timeout 250 ms)--> Fail
                 └─(POINTER_DOWN)→ PairLocked --(MOVE: Δy_centroid ≥ 20 mm up)--> Success
                                            └─(UP | POINTER_UP)------------------> Fail

Event / Condition                     Recognition: state → next
-----------------------------------------------------------------------------------------------
onEnter(AwaitSecond)                  remember firstId, startY; arm 250 ms timer; if fires while still here → Fail
POINTER_DOWN in AwaitSecond           capture secondId; set centroidStartY; → PairLocked
MOVE in PairLocked                    if centroidStartY − currentCentroidY ≥ 20 mm → Success; else Continue
UP or POINTER_UP in PairLocked        → Fail
UP in AwaitSecond                     default RecognitionState behaviour → Fail
CANCEL / external abort               Policy drives → recognition → Quiescent

*/
