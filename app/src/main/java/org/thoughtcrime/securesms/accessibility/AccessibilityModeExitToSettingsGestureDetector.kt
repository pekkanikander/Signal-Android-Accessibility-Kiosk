/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.accessibility

import android.content.Context
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.keyvalue.SignalStore
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Detector for Accessibility Mode exit to settings gestures.
 *
 * Implements Gesture A (opposite corners hold) as specified in ChatGPT-5's plan:
 * - Two pointers down within 150ms of each other
 * - Each pointer in opposite corner hit-rects (default 72dp)
 * - Inter-pointer distance ≥ 0.85 × screen diagonal
 * - Maintain hold for configured duration with drift tolerance
 */
class AccessibilityModeExitToSettingsGestureDetector(
  private val context: Context,
  private val onTriggered: () -> Unit
) : View.OnTouchListener {

  companion object {
    private val TAG = "ExitGesture"

    // Timing constraints
    private const val POINTER_TIMEOUT_MS = 150L
    private const val HAPTIC_FEEDBACK_INTERVAL_MS = 500L

    // Distance constraints
    private const val MIN_DISTANCE_RATIO = 0.85f
  }

  // Gesture state
  private var firstPointerId = -1
  private var secondPointerId = -1
  private var firstPointerDownTime = 0L
  private var secondPointerDownTime = 0L
  private var firstPointerStartX = 0f
  private var firstPointerStartY = 0f
  private var secondPointerStartX = 0f
  private var secondPointerStartY = 0f
  private var lastHapticTime = 0L
  private var isGestureActive = false

  // Configuration from SignalStore
  private val holdDurationMs: Int get() = SignalStore.accessibilityMode.exitGestureHoldMs
  private val cornerSizeDp: Int get() = SignalStore.accessibilityMode.exitGestureCornerDp
  private val driftToleranceDp: Int get() = SignalStore.accessibilityMode.exitGestureDriftDp

      override fun onTouch(view: View, event: MotionEvent): Boolean {
    Log.d(TAG, "Touch event: action=${event.actionMasked}, pointerCount=${event.pointerCount}, actionIndex=${event.actionIndex}")

    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        Log.d(TAG, "ACTION_DOWN: pointerId=${event.getPointerId(0)}, x=${event.getX(0)}, y=${event.getY(0)}")
        handlePointerDown(event, 0)
        return false // Don't consume, let other touch handlers work
      }

      MotionEvent.ACTION_POINTER_DOWN -> {
        val pointerIndex = event.actionIndex
        val pointerId = event.getPointerId(pointerIndex)
        Log.d(TAG, "ACTION_POINTER_DOWN: pointerId=$pointerId, x=${event.getX(pointerIndex)}, y=${event.getY(pointerIndex)}")
        handlePointerDown(event, pointerIndex)
        return false
      }

      MotionEvent.ACTION_MOVE -> {
        if (event.pointerCount > 0) {
          Log.d(TAG, "ACTION_MOVE: pointerCount=${event.pointerCount}, firstPointerId=${event.getPointerId(0)}")
          val consumed = handlePointerMove(event)
          if (consumed) {
            return true // Consume the event if gesture is active
          }
        }
        return false
      }

      MotionEvent.ACTION_UP -> {
        Log.d(TAG, "ACTION_UP: pointerId=${event.getPointerId(0)}")
        handlePointerUp(event, 0)
        return false
      }

      MotionEvent.ACTION_POINTER_UP -> {
        val pointerIndex = event.actionIndex
        val pointerId = event.getPointerId(pointerIndex)
        Log.d(TAG, "ACTION_POINTER_UP: pointerId=$pointerId")
        handlePointerUp(event, pointerIndex)
        return false
      }

      MotionEvent.ACTION_CANCEL -> {
        Log.d(TAG, "ACTION_CANCEL")
        resetGesture()
        return false
      }
    }

    return false
  }

    private fun handlePointerDown(event: MotionEvent, pointerIndex: Int) {
    val pointerId = event.getPointerId(pointerIndex)
    val x = event.getX(pointerIndex)
    val y = event.getY(pointerIndex)
    val currentTime = System.currentTimeMillis()

    Log.d(TAG, "handlePointerDown: pointerId=$pointerId, x=$x, y=$y, firstPointerId=$firstPointerId, secondPointerId=$secondPointerId")

    when {
      firstPointerId == -1 -> {
        // First pointer
        firstPointerId = pointerId
        firstPointerDownTime = currentTime
        firstPointerStartX = x
        firstPointerStartY = y
        Log.d(TAG, "First pointer down: id=$pointerId, pos=($x, $y)")
      }

      secondPointerId == -1 -> {
        // Second pointer - check timing
        val timeDiff = currentTime - firstPointerDownTime
        Log.d(TAG, "Second pointer detected: timeDiff=$timeDiff ms, timeout=$POINTER_TIMEOUT_MS ms")

        if (timeDiff <= POINTER_TIMEOUT_MS) {
          secondPointerId = pointerId
          secondPointerDownTime = currentTime
          secondPointerStartX = x
          secondPointerStartY = y
          Log.d(TAG, "Second pointer down: id=$pointerId, pos=($x, $y), timeDiff=$timeDiff")

          // Check if this could be a valid gesture
          Log.d(TAG, "Checking corner gesture validity...")
          if (isValidCornerGesture(x, y)) {
            Log.d(TAG, "Corner gesture is valid, starting detection")
            startGestureDetection()
          } else {
            Log.d(TAG, "Corner gesture is invalid, resetting")
            resetGesture()
          }
        } else {
          // Too much time between pointers, reset
          Log.d(TAG, "Pointer timeout: $timeDiff ms > $POINTER_TIMEOUT_MS ms")
          resetGesture()
        }
      }

      else -> {
        // Third pointer - cancel gesture
        Log.d(TAG, "Third pointer detected, canceling gesture")
        resetGesture()
      }
    }
  }

    private fun handlePointerMove(event: MotionEvent): Boolean {
    if (!isGestureActive || firstPointerId == -1 || secondPointerId == -1) {
      Log.d(TAG, "handlePointerMove: gesture not active or pointers missing - active=$isGestureActive, first=$firstPointerId, second=$secondPointerId")
      return false
    }

    val firstIndex = event.findPointerIndex(firstPointerId)
    val secondIndex = event.findPointerIndex(secondPointerId)

    if (firstIndex == -1 || secondIndex == -1) {
      Log.d(TAG, "handlePointerMove: pointer indices not found - firstIndex=$firstIndex, secondIndex=$secondIndex")
      resetGesture()
      return false
    }

    val firstX = event.getX(firstIndex)
    val firstY = event.getY(firstIndex)
    val secondX = event.getX(secondIndex)
    val secondY = event.getY(secondIndex)

    Log.d(TAG, "handlePointerMove: first=($firstX, $firstY), second=($secondX, $secondY)")

    // Check drift tolerance
    val firstDrift = sqrt((firstX - firstPointerStartX).pow(2) + (firstY - firstPointerStartY).pow(2))
    val secondDrift = sqrt((secondX - secondPointerStartX).pow(2) + (secondY - secondPointerStartY).pow(2))
    val maxDriftPx = context.resources.displayMetrics.density * driftToleranceDp

    Log.d(TAG, "Drift check: first=$firstDrift, second=$secondDrift, max=$maxDriftPx, driftToleranceDp=$driftToleranceDp")

    if (firstDrift > maxDriftPx || secondDrift > maxDriftPx) {
      Log.d(TAG, "Drift exceeded: first=$firstDrift, second=$secondDrift, max=$maxDriftPx")
      resetGesture()
      return false
    }

    // Check hold duration
    val currentTime = System.currentTimeMillis()
    val holdTime = minOf(currentTime - firstPointerDownTime, currentTime - secondPointerDownTime)

    Log.d(TAG, "Hold check: holdTime=$holdTime ms, required=$holdDurationMs ms")

    if (holdTime >= holdDurationMs) {
      Log.d(TAG, "Gesture triggered after ${holdTime}ms")
      triggerGesture()
      return true
    }

    // Provide haptic feedback during hold
    if (currentTime - lastHapticTime >= HAPTIC_FEEDBACK_INTERVAL_MS) {
      // TODO: Add haptic feedback
      lastHapticTime = currentTime
      Log.d(TAG, "Haptic feedback triggered")
    }

    return true // Consume the event when gesture is active
  }

  private fun handlePointerUp(event: MotionEvent, pointerIndex: Int) {
    val pointerId = event.getPointerId(pointerIndex)

    when (pointerId) {
      firstPointerId -> {
        Log.d(TAG, "First pointer up")
        resetGesture()
      }
      secondPointerId -> {
        Log.d(TAG, "Second pointer up")
        resetGesture()
      }
    }
  }

  private fun isValidCornerGesture(x: Float, y: Float): Boolean {
    val screenWidth = context.resources.displayMetrics.widthPixels
    val screenHeight = context.resources.displayMetrics.heightPixels
    val cornerSizePx = context.resources.displayMetrics.density * cornerSizeDp

    Log.d(TAG, "isValidCornerGesture: screen=${screenWidth}x${screenHeight}, cornerSize=${cornerSizePx}px, cornerSizeDp=$cornerSizeDp")

    // Check if second pointer is in opposite corner
    val firstInTopLeft = isInTopLeftCorner(firstPointerStartX, firstPointerStartY, cornerSizePx)
    val firstInBottomRight = isInBottomRightCorner(firstPointerStartX, firstPointerStartY, screenWidth, screenHeight, cornerSizePx)
    val secondInTopLeft = isInTopLeftCorner(x, y, cornerSizePx)
    val secondInBottomRight = isInBottomRightCorner(x, y, screenWidth, screenHeight, cornerSizePx)

    Log.d(TAG, "Corner checks: first=(${firstPointerStartX}, ${firstPointerStartY}) - topLeft=$firstInTopLeft, bottomRight=$firstInBottomRight")
    Log.d(TAG, "Corner checks: second=($x, $y) - topLeft=$secondInTopLeft, bottomRight=$secondInBottomRight")

    val isValidCornerPair = (firstInTopLeft && secondInBottomRight) || (firstInBottomRight && secondInTopLeft)

    if (!isValidCornerPair) {
      Log.d(TAG, "Invalid corner pair: first=(${firstPointerStartX}, ${firstPointerStartY}), second=($x, $y)")
      return false
    }

    // Check minimum distance
    val distance = sqrt((x - firstPointerStartX).pow(2) + (y - firstPointerStartY).pow(2))
    val screenDiagonal = sqrt((screenWidth * screenWidth + screenHeight * screenHeight).toFloat())
    val minDistance = screenDiagonal * MIN_DISTANCE_RATIO

    Log.d(TAG, "Distance check: distance=$distance, screenDiagonal=$screenDiagonal, minDistance=$minDistance, ratio=$MIN_DISTANCE_RATIO")

    if (distance < minDistance) {
      Log.d(TAG, "Distance too small: $distance < $minDistance")
      return false
    }

    Log.d(TAG, "Valid corner gesture: distance=$distance, minDistance=$minDistance")
    return true
  }

  private fun isInTopLeftCorner(x: Float, y: Float, cornerSizePx: Float): Boolean {
    val result = x <= cornerSizePx && y <= cornerSizePx
    Log.d(TAG, "isInTopLeftCorner: x=$x, y=$y, cornerSize=$cornerSizePx, result=$result")
    return result
  }

  private fun isInBottomRightCorner(x: Float, y: Float, screenWidth: Int, screenHeight: Int, cornerSizePx: Float): Boolean {
    val result = x >= (screenWidth - cornerSizePx) && y >= (screenHeight - cornerSizePx)
    Log.d(TAG, "isInBottomRightCorner: x=$x, y=$y, screen=${screenWidth}x${screenHeight}, cornerSize=$cornerSizePx, result=$result")
    return result
  }

  private fun startGestureDetection() {
    isGestureActive = true
    lastHapticTime = System.currentTimeMillis()
    Log.d(TAG, "Starting gesture detection: holdDuration=${holdDurationMs}ms")

    // Provide initial feedback
    Toast.makeText(context, "Release. Confirm slider visible.", Toast.LENGTH_SHORT).show()
  }

  private fun triggerGesture() {
    Log.d(TAG, "Exit gesture triggered!")
    resetGesture()
    onTriggered()
  }

  private fun resetGesture() {
    firstPointerId = -1
    secondPointerId = -1
    firstPointerDownTime = 0L
    secondPointerDownTime = 0L
    firstPointerStartX = 0f
    firstPointerStartY = 0f
    secondPointerStartX = 0f
    secondPointerStartY = 0f
    lastHapticTime = 0L
    isGestureActive = false
    Log.d(TAG, "Gesture reset")
  }
}
