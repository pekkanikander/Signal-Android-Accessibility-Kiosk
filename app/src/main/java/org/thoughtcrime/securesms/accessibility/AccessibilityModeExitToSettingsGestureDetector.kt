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
    private val TAG = Log.tag(AccessibilityModeExitToSettingsGestureDetector::class)

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
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        handlePointerDown(event, 0)
        return false // Don't consume, let other touch handlers work
      }

      MotionEvent.ACTION_POINTER_DOWN -> {
        val pointerIndex = event.actionIndex
        handlePointerDown(event, pointerIndex)
        return false
      }

      MotionEvent.ACTION_MOVE -> {
        handlePointerMove(event)
        return false
      }

      MotionEvent.ACTION_UP -> {
        handlePointerUp(event, 0)
        return false
      }

      MotionEvent.ACTION_POINTER_UP -> {
        val pointerIndex = event.actionIndex
        handlePointerUp(event, pointerIndex)
        return false
      }

      MotionEvent.ACTION_CANCEL -> {
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
        if (timeDiff <= POINTER_TIMEOUT_MS) {
          secondPointerId = pointerId
          secondPointerDownTime = currentTime
          secondPointerStartX = x
          secondPointerStartY = y
          Log.d(TAG, "Second pointer down: id=$pointerId, pos=($x, $y), timeDiff=$timeDiff")

          // Check if this could be a valid gesture
          if (isValidCornerGesture(x, y)) {
            startGestureDetection()
          } else {
            resetGesture()
          }
        } else {
          // Too much time between pointers, reset
          Log.d(TAG, "Pointer timeout: $timeDiff ms")
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

  private fun handlePointerMove(event: MotionEvent) {
    if (!isGestureActive || firstPointerId == -1 || secondPointerId == -1) {
      return
    }

    val firstIndex = event.findPointerIndex(firstPointerId)
    val secondIndex = event.findPointerIndex(secondPointerId)

    if (firstIndex == -1 || secondIndex == -1) {
      resetGesture()
      return
    }

    val firstX = event.getX(firstIndex)
    val firstY = event.getY(firstIndex)
    val secondX = event.getX(secondIndex)
    val secondY = event.getY(secondIndex)

    // Check drift tolerance
    val firstDrift = sqrt((firstX - firstPointerStartX).pow(2) + (firstY - firstPointerStartY).pow(2))
    val secondDrift = sqrt((secondX - secondPointerStartX).pow(2) + (secondY - secondPointerStartY).pow(2))
    val maxDriftPx = context.resources.displayMetrics.density * driftToleranceDp

    if (firstDrift > maxDriftPx || secondDrift > maxDriftPx) {
      Log.d(TAG, "Drift exceeded: first=$firstDrift, second=$secondDrift, max=$maxDriftPx")
      resetGesture()
      return
    }

    // Check hold duration
    val currentTime = System.currentTimeMillis()
    val holdTime = minOf(currentTime - firstPointerDownTime, currentTime - secondPointerDownTime)

    if (holdTime >= holdDurationMs) {
      Log.d(TAG, "Gesture triggered after ${holdTime}ms")
      triggerGesture()
      return
    }

    // Provide haptic feedback during hold
    if (currentTime - lastHapticTime >= HAPTIC_FEEDBACK_INTERVAL_MS) {
      // TODO: Add haptic feedback
      lastHapticTime = currentTime
    }
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

    // Check if second pointer is in opposite corner
    val firstInTopLeft = isInTopLeftCorner(firstPointerStartX, firstPointerStartY, cornerSizePx)
    val firstInBottomRight = isInBottomRightCorner(firstPointerStartX, firstPointerStartY, screenWidth, screenHeight, cornerSizePx)
    val secondInTopLeft = isInTopLeftCorner(x, y, cornerSizePx)
    val secondInBottomRight = isInBottomRightCorner(x, y, screenWidth, screenHeight, cornerSizePx)

    val isValidCornerPair = (firstInTopLeft && secondInBottomRight) || (firstInBottomRight && secondInTopLeft)

    if (!isValidCornerPair) {
      Log.d(TAG, "Invalid corner pair: first=(${firstPointerStartX}, ${firstPointerStartY}), second=($x, $y)")
      return false
    }

    // Check minimum distance
    val distance = sqrt((x - firstPointerStartX).pow(2) + (y - firstPointerStartY).pow(2))
    val screenDiagonal = sqrt((screenWidth * screenWidth + screenHeight * screenHeight).toFloat())
    val minDistance = screenDiagonal * MIN_DISTANCE_RATIO

    if (distance < minDistance) {
      Log.d(TAG, "Distance too small: $distance < $minDistance")
      return false
    }

    Log.d(TAG, "Valid corner gesture: distance=$distance, minDistance=$minDistance")
    return true
  }

  private fun isInTopLeftCorner(x: Float, y: Float, cornerSizePx: Float): Boolean {
    return x <= cornerSizePx && y <= cornerSizePx
  }

  private fun isInBottomRightCorner(x: Float, y: Float, screenWidth: Int, screenHeight: Int, cornerSizePx: Float): Boolean {
    return x >= (screenWidth - cornerSizePx) && y >= (screenHeight - cornerSizePx)
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
