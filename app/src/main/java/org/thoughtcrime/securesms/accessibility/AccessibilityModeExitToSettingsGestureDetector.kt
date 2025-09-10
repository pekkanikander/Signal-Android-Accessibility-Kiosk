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
    FIRST_POINTER_DOWN,
    SECOND_POINTER_DOWN,
    GESTURE_ACTIVE
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
  private var tapCount = 0
  private var firstTapTime = 0L
  private var lastTapTime = 0L

  override fun onTouch(view: View, event: MotionEvent): Boolean {
    // If TalkBack touch exploration is on, don't intercept
    if (accessibilityManager.isEnabled && accessibilityManager.isTouchExplorationEnabled) return false

    return when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> handleActionDown(event)
      MotionEvent.ACTION_POINTER_DOWN -> handlePointerDown(event)
      MotionEvent.ACTION_MOVE -> handleMove(event)
      MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> handlePointerUp(event)
      MotionEvent.ACTION_CANCEL -> { resetState(); true }
      else -> false
    }
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

  private fun handleActionDown(event: MotionEvent): Boolean {
    val x = event.getX(0)
    val y = event.getY(0)
    val now = System.currentTimeMillis()
    val headerRect = headerBoundsInset()
    val inHeader = headerRect.contains(x.toInt(), y.toInt())

    // Debug logging for header hit detection and tap state
    Log.d(TAG, "ACTION_DOWN at=(${x.toInt()},${y.toInt()}) inHeader=${inHeader} headerRect=${headerRect} tapCount=${tapCount} firstTapTime=${firstTapTime} lastTapTime=${lastTapTime}")

    val gestureType = currentGestureType()
    if (gestureType == AccessibilityModeExitGestureType.TRIPLE_TAP_DEBUG) {
      if (!inHeader) {
        Log.d(TAG, "TRIPLE_TAP: ACTION_DOWN outside header -> ignored")
        return false
      }
      if (tapCount == 0 || now - firstTapTime > tripleTapWindowMs) {
        tapCount = 1
        firstTapTime = now
        lastTapTime = now
      } else {
        if (now - lastTapTime <= tripleTapIntervalMs) {
          tapCount += 1
          lastTapTime = now
          if (tapCount >= 3) {
            triggerGesture()
            tapCount = 0
          }
        } else {
          tapCount = 1
          firstTapTime = now
          lastTapTime = now
        }
      }
      Log.d(TAG, "TRIPLE_TAP: tapCount=${tapCount}")
      return true
    }

    // Production: two-finger header hold
    if (!inHeader) {
      Log.d(TAG, "TWO_FINGER: ACTION_DOWN outside header -> ignored header=${headerRect}")
      return false
    }

    state = GestureState.FIRST_POINTER_DOWN
    firstPointerId = event.getPointerId(0)
    firstPointerDownTime = now
    firstPointerStartX = x
    firstPointerStartY = y
    Log.d(TAG, "FIRST_POINTER_DOWN id=${firstPointerId} start=(${firstPointerStartX.toInt()},${firstPointerStartY.toInt()})")
    return true
  }

  private fun handlePointerDown(event: MotionEvent): Boolean {
    if (currentGestureType() != AccessibilityModeExitGestureType.TWO_FINGER_HEADER_HOLD) return false
    if (state != GestureState.FIRST_POINTER_DOWN) return false

    val idx = event.actionIndex
    val x = event.getX(idx)
    val y = event.getY(idx)
    val now = System.currentTimeMillis()

    if (now - firstPointerDownTime > pointerTimeoutMs) { resetState(); return false }
    if (!headerBoundsInset().contains(x.toInt(), y.toInt())) { resetState(); return false }

    secondPointerId = event.getPointerId(idx)
    secondPointerDownTime = now
    secondPointerStartX = x
    secondPointerStartY = y
    state = GestureState.SECOND_POINTER_DOWN
    return true
  }

  private fun handleMove(event: MotionEvent): Boolean {
    if (currentGestureType() != AccessibilityModeExitGestureType.TWO_FINGER_HEADER_HOLD) return false
    if (state != GestureState.SECOND_POINTER_DOWN && state != GestureState.GESTURE_ACTIVE) return false

    val firstIndex = event.findPointerIndex(firstPointerId)
    val secondIndex = event.findPointerIndex(secondPointerId)
    if (firstIndex == -1 || secondIndex == -1) { resetState(); return false }

    val firstX = event.getX(firstIndex)
    val firstY = event.getY(firstIndex)
    val secondX = event.getX(secondIndex)
    val secondY = event.getY(secondIndex)

    // Both must stay within header bounds (inset) and within drift tolerance from start
    val header = headerBoundsInset()
    if (!header.contains(firstX.toInt(), firstY.toInt()) || !header.contains(secondX.toInt(), secondY.toInt())) {
      resetState(); return false
    }

    val firstDrift = distance(firstX, firstY, firstPointerStartX, firstPointerStartY)
    val secondDrift = distance(secondX, secondY, secondPointerStartX, secondPointerStartY)
    if (firstDrift > driftTolerancePx || secondDrift > driftTolerancePx) { resetState(); return false }

    val now = System.currentTimeMillis()
    val holdTime = minOf(now - firstPointerDownTime, now - secondPointerDownTime)
    if (holdTime >= holdDurationMs) {
      triggerGesture()
      return true
    }

    // Ensure we compare Longs: KV stores an Int, convert to Long and fall back to default Long
    val hapticInterval = if (SignalStore.accessibilityMode.exitHapticFeedbackIntervalMs > 0)
      SignalStore.accessibilityMode.exitHapticFeedbackIntervalMs.toLong()
    else
      DEFAULT_HAPTIC_FEEDBACK_INTERVAL_MS

    if (now - lastHapticTime >= hapticInterval) {
      lastHapticTime = now
      // TODO: Provide subtle haptic feedback if available (use view.performHapticFeedback when appropriate)
    }

    state = GestureState.GESTURE_ACTIVE
    return true
  }

  private fun handlePointerUp(event: MotionEvent): Boolean {
    if (currentGestureType() == AccessibilityModeExitGestureType.TRIPLE_TAP_DEBUG) {
      // Do not reset on UP for triple tap; DOWNs carry the logic
      return true
    }

    val upId = event.getPointerId(event.actionIndex)
    if (upId == firstPointerId || upId == secondPointerId) {
      resetState()
      return true
    }
    return false
  }

  private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
    val dx = x1 - x2
    val dy = y1 - y2
    return sqrt(dx * dx + dy * dy)
  }

  private fun triggerGesture() {
    Log.i(TAG, "Exit gesture triggered")
    resetState()
    onTriggered()
  }

  private fun resetState() {
    state = GestureState.IDLE
    firstPointerId = -1
    secondPointerId = -1
    firstPointerDownTime = 0L
    secondPointerDownTime = 0L
    firstPointerStartX = 0f
    firstPointerStartY = 0f
    secondPointerStartX = 0f
    secondPointerStartY = 0f
    lastHapticTime = 0L
  }
}
