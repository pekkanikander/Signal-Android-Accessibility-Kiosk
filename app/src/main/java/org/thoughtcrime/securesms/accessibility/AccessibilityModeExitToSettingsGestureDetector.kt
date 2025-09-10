/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.accessibility

import android.content.Context
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.view.HapticFeedbackConstants
import android.os.Handler
import android.os.Looper
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
    // Default value, real value is read from SignalStore at runtime
    private const val DEFAULT_HAPTIC_FEEDBACK_INTERVAL_MS = 500L
  }

  private enum class GestureState {
    IDLE,
    TRIPLE_TAP_WAIT_FOR_SECOND_TAP_DOWN,     // First tap detected, waiting for second tap
    TRIPLE_TAP_WAIT_FOR_THIRD_TAP_DOWN, // Second tap detected, waiting for third tap
    TWO_FINGER_FIRST_POINTER_DOWN,      // First finger down, waiting for second finger down
    TWO_FINGER_SECOND_POINTER_DOWN,     // Second finger down, waiting for long enough hold
  }

  private val accessibilityManager: AccessibilityManager by lazy {
    context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
  }

  // Config
  private val holdDurationMs: Int by lazy { SignalStore.accessibilityMode.exitGestureHoldMs }
  private val driftTolerancePx: Float by lazy { context.resources.displayMetrics.density * SignalStore.accessibilityMode.exitGestureDriftDp }
  private val headerDeadzonePx: Int by lazy { (context.resources.displayMetrics.density * SignalStore.accessibilityMode.exitHeaderDeadzoneDp).toInt() }
  // Additional configurable downward inset to widen the active header tap area
  private val headerExtraBottomPx: Int by lazy { (context.resources.displayMetrics.density * SignalStore.accessibilityMode.exitHeaderExtraBottomDp).toInt() }
  private val pointerTimeoutMs: Int by lazy { SignalStore.accessibilityMode.exitGesturePointerTimeoutMs }
  private val tripleTapIntervalMs: Int by lazy { SignalStore.accessibilityMode.exitTripleTapIntervalMs }
  private val tripleTapWindowMs: Int by lazy { SignalStore.accessibilityMode.exitTripleTapWindowMs }
  private fun currentGestureType(): AccessibilityModeExitGestureType {
    val debugPrefs = context.getSharedPreferences("accessibility_mode_debug", Context.MODE_PRIVATE)
    val override = debugPrefs.getInt("exit_gesture_type_override", -1)
    val overrideEnabled = debugPrefs.getBoolean("exit_gesture_type_override_enabled", false)

    // Prefer the persisted user setting in SignalStore. Only apply the debug override
    // if an explicit debug-enable flag has been set to avoid silent mismatches.
    val value = if (overrideEnabled && (override == 0 || override == 1)) {
      Log.d(TAG, "Using debug override for exit gesture: $override")
      override
    } else {
      SignalStore.accessibilityMode.exitGestureType
    }
    return AccessibilityModeExitGestureType.fromValue(value)
  }

  // State
  private var state = GestureState.IDLE

  // Two-finger header hold state
  private var firstPointerId = -1
  private var secondPointerId = -1
  private var firstPointerDownTime = 0L
  private var secondPointerDownTime = 0L
  private var firstPointerStartX = 0f
  private var firstPointerStartY = 0f
  private var secondPointerStartX = 0f
  private var secondPointerStartY = 0f
  private var lastHapticTime = 0L

  // Triple tap state
  private var lastTapTime = 0L
  private var firstTapTime = 0L

  // Timer / scheduling infrastructure
  private val mainHandler = Handler(Looper.getMainLooper())
  private var stateVersion = 0L // bump on each state transition to invalidate stale runnables
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
        try {
          action()
        } finally {
          // remove self from tracking list
          scheduledRunnables.remove(this)
        }
      }
    }
    scheduleRunnable(delayMs, r)
  }

  private fun cancelScheduledRunnables() {
    scheduledRunnables.forEach { mainHandler.removeCallbacks(it) }
    scheduledRunnables.clear()
  }

  private fun headerBoundsInset(): Rect {
    val b = Rect(headerBoundsProvider())
    b.inset(headerDeadzonePx, 0)
    // Keep top edge strict; reduce bottom slightly to avoid content touches counted as header
    b.bottom = maxOf(b.top, b.bottom - headerDeadzonePx)
    // Expand bottom downward slightly to make hitting the header easier on real devices
    b.bottom = minOf(b.bottom + headerExtraBottomPx, Int.MAX_VALUE)
    return b
  }

  private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
    val dx = x1 - x2
    val dy = y1 - y2
    return sqrt(dx * dx + dy * dy)
  }

  override fun onTouch(view: View, event: MotionEvent): Boolean {
    val idx = event.actionIndex
    val x = event.getX(idx)
    val y = event.getY(idx)
    val now = System.currentTimeMillis()
    val headerRect = headerBoundsInset()
    val inHeader = headerRect.contains(x.toInt(), y.toInt())

    // If TalkBack touch exploration is on, don't intercept
    if (accessibilityManager.isEnabled && accessibilityManager.isTouchExplorationEnabled) return false

    return when (event.actionMasked) {
      MotionEvent.ACTION_DOWN                              -> handleActionDown(event, idx, x, y, now, inHeader)
      MotionEvent.ACTION_POINTER_DOWN                      -> handlePointerDown(event, idx, x, y, now, inHeader)
      MotionEvent.ACTION_MOVE                              -> handleMove(event, idx, x, y, now, inHeader)
      MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> handlePointerUp(event, idx, x, y, now, inHeader)
      MotionEvent.ACTION_CANCEL                            -> resetState()
      else -> false
    }
  }


  private fun handleActionDown(event: MotionEvent, idx: Int, x: Float, y: Float, now: Long, inHeader: Boolean): Boolean {

    // Debug logging for header hit detection and tap state
    Log.d(TAG, "ACTION_DOWN at=(${x.toInt()},${y.toInt()}) inHeader=${inHeader} lastTapTime=${lastTapTime}")

    if (!inHeader) {
      Log.d(TAG, "TWO_FINGER: ACTION_DOWN outside header -> ignored")
      return false
    }

    when (state) {
      GestureState.IDLE -> {
        val gestureType = currentGestureType()
        when {
          gestureType == AccessibilityModeExitGestureType.TRIPLE_TAP_DEBUG -> {
            state = GestureState.TRIPLE_TAP_WAIT_FOR_SECOND_TAP_DOWN
            firstTapTime = now
            lastTapTime = now
            Log.d(TAG, "TRIPLE_TAP: Starting wait for second tap down")

            // Schedule inter-tap timeout for second tap
            scheduleRunnable(tripleTapIntervalMs.toLong()) {
              if (state == GestureState.TRIPLE_TAP_WAIT_FOR_SECOND_TAP_DOWN && System.currentTimeMillis() - lastTapTime >= tripleTapIntervalMs) {
                Log.d(TAG, "TRIPLE_TAP: inter-tap timeout waiting for second tap -> reset")
                resetState()
              }
            }

            // Schedule overall window timeout
            scheduleRunnable(tripleTapWindowMs.toLong()) {
              if (firstTapTime != 0L && System.currentTimeMillis() - firstTapTime >= tripleTapWindowMs) {
                Log.d(TAG, "TRIPLE_TAP: overall window timeout -> reset")
                resetState()
              }
            }

            return true
          }

          gestureType == AccessibilityModeExitGestureType.TWO_FINGER_HEADER_HOLD -> {
            state = GestureState.TWO_FINGER_FIRST_POINTER_DOWN
            firstPointerId = idx
            firstPointerStartX = x
            firstPointerStartY = y
            firstPointerDownTime = now
            Log.d(TAG, "TWO_FINGER: FIRST_POINTER_DOWN id=${firstPointerId} start=(${firstPointerStartX.toInt()},${firstPointerStartY.toInt()})")
            return true
          }

          else -> {
            return false
          }
        }
      }

      // Triple tap states
      GestureState.TRIPLE_TAP_WAIT_FOR_SECOND_TAP_DOWN -> {
        Log.d(TAG, "TRIPLE_TAP: Second tap detected, waiting for third tap.")
        state = GestureState.TRIPLE_TAP_WAIT_FOR_THIRD_TAP_DOWN
        lastTapTime = now

        // Schedule inter-tap timeout for third tap
        scheduleRunnable(tripleTapIntervalMs.toLong()) {
          if (state == GestureState.TRIPLE_TAP_WAIT_FOR_THIRD_TAP_DOWN && System.currentTimeMillis() - lastTapTime >= tripleTapIntervalMs) {
            Log.d(TAG, "TRIPLE_TAP: inter-tap timeout waiting for third tap -> reset")
            resetState()
          }
        }

        return true
      }

      GestureState.TRIPLE_TAP_WAIT_FOR_THIRD_TAP_DOWN -> {
        Log.d(TAG, "TRIPLE_TAP: third tap detected, trigger gesture.")
        resetState()
        triggerGesture()
        return true
      }

      // Two-finger header hold states should never happen here
      GestureState.TWO_FINGER_FIRST_POINTER_DOWN,
      GestureState.TWO_FINGER_SECOND_POINTER_DOWN -> {
        Log.d(TAG, "Action down event: Impossible state")
        resetState()
        return false
      }
    }
  }

  private fun handlePointerDown(event: MotionEvent, idx: Int, x: Float, y: Float, now: Long, inHeader: Boolean): Boolean {
    when (state) {
      GestureState.TWO_FINGER_FIRST_POINTER_DOWN -> {
        secondPointerId = idx
        secondPointerDownTime = now
        secondPointerStartX = x
        secondPointerStartY = y
        Log.d(TAG, "SECOND_POINTER_DOWN id=${secondPointerId} start=(${secondPointerStartX.toInt()},${secondPointerStartY.toInt()})")
        state = GestureState.TWO_FINGER_SECOND_POINTER_DOWN

        // Schedule a pointer-timeout: if second finger isn't held within pointerTimeoutMs, reset
        scheduleRunnable(pointerTimeoutMs.toLong()) {
          if (state != GestureState.TWO_FINGER_SECOND_POINTER_DOWN) return@scheduleRunnable
          Log.d(TAG, "TWO_FINGER: pointer timeout reached without proper hold; resetting")
          resetState()
        }

        // Schedule a hold-complete runnable at holdDurationMs from the first pointer down time
        val timeUntilHoldComplete = (firstPointerDownTime + holdDurationMs) - now
        scheduleRunnable(timeUntilHoldComplete) {
          // If state still indicates both pointers are down, assume MOVE checks kept them valid
          if (state != GestureState.TWO_FINGER_SECOND_POINTER_DOWN) return@scheduleRunnable
          Log.d(TAG, "TWO_FINGER: hold complete (timer), triggering gesture")
          triggerGesture()
        }

        // Schedule periodic haptics while waiting for hold completion. We'll schedule the first
        // tick at haptic interval after the earliest down time.
        val hapticIntervalMs = SignalStore.accessibilityMode.exitHapticFeedbackIntervalMs.takeIf { it > 0 }?.toLong()
          ?: DEFAULT_HAPTIC_FEEDBACK_INTERVAL_MS
        val firstHapticDelay = maxOf(firstPointerDownTime + hapticIntervalMs - now)
        val hapticRunnable = object : Runnable {
          override fun run() {
            if (state != GestureState.TWO_FINGER_SECOND_POINTER_DOWN) return
            try {
              // perform haptic feedback on the overlay view via the caller's view if available
              // we can't access the overlay view here; log instead and leave TODO to wire view
              Log.d(TAG, "TWO_FINGER: haptic tick")
            } finally {
              // re-schedule
              scheduleRunnable(hapticIntervalMs, this)
            }
          }
        }
        scheduleRunnable(firstHapticDelay, hapticRunnable)
        return true
      }

      GestureState.TWO_FINGER_SECOND_POINTER_DOWN -> {
        Log.d(TAG, "TWO_FINGER: Third finger down — ignore gesture.")
        resetState()
        return false
      }

      else -> {
        Log.d(TAG, "Pointer down event: Impossible state")
        resetState()
        return false
      }
    }

  }

  private fun handleMove(event: MotionEvent, idx: Int, x: Float, y: Float, now: Long, inHeader: Boolean): Boolean {

    when (state) {
      GestureState.TWO_FINGER_SECOND_POINTER_DOWN -> {
        Log.d(TAG, "TWO_FINGER: Pointer move detected.")
        // fall through
      }

      else -> {
        Log.d(TAG, "Pointer move event: ignore gesture")
        resetState()
        return false
      }
    }

    val firstIndex  = event.findPointerIndex(firstPointerId)
    val secondIndex = event.findPointerIndex(secondPointerId)
    if (firstIndex == -1 || secondIndex == -1) {
      resetState(); return false
    }

    val firstX  = event.getX(firstIndex)
    val firstY  = event.getY(firstIndex)
    val secondX = event.getX(secondIndex)
    val secondY = event.getY(secondIndex)

    // Both fingers must stay within header bounds (inset) and within drift tolerance from start
    val header = headerBoundsInset()
    if (!header.contains(firstX.toInt(), firstY.toInt()) || !header.contains(secondX.toInt(), secondY.toInt())) {
      Log.d(TAG, "TWO_FINGER: Pointer move outside header bounds, ignore gesture")
      resetState()
      return false
    }

    val firstDrift = distance(firstX, firstY, firstPointerStartX, firstPointerStartY)
    val secondDrift = distance(secondX, secondY, secondPointerStartX, secondPointerStartY)
    if (firstDrift > driftTolerancePx || secondDrift > driftTolerancePx) {
      Log.d(TAG, "TWO_FINGER: Pointer move outside drift tolerance, ignore gesture")
      resetState()
      return false
    }

    return true
  }

  private fun handlePointerUp(event: MotionEvent, idx: Int, x: Float, y: Float, now: Long, inHeader: Boolean): Boolean {
    when (state) {
      GestureState.TRIPLE_TAP_WAIT_FOR_SECOND_TAP_DOWN,
      GestureState.TRIPLE_TAP_WAIT_FOR_THIRD_TAP_DOWN -> {
        // Do not reset on UP for triple tap; DOWNs carry the logic
        Log.d(TAG, "Pointer up event: Triple tap state")
        return true
      } else -> {
        // fall through
      }
    }

    val upId = event.getPointerId(idx)
    if (upId == firstPointerId || upId == secondPointerId) {
      resetState()
      return true
    }
    return false
  }

  private fun triggerGesture() {
    Log.i(TAG, "Exit gesture triggered")
    onTriggered()
  }

  private fun resetState(): Boolean {
    cancelScheduledRunnables()
    state = GestureState.IDLE
    stateVersion++
    firstPointerId = -1
    secondPointerId = -1
    firstPointerDownTime = 0L
    secondPointerDownTime = 0L
    firstPointerStartX = 0f
    firstPointerStartY = 0f
    secondPointerStartX = 0f
    secondPointerStartY = 0f
    lastHapticTime = 0L
    firstTapTime = 0L
    lastTapTime = 0L
    return true
  }
}
