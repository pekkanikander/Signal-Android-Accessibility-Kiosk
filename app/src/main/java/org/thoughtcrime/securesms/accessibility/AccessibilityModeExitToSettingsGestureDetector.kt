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
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Clean, state-machine based detector for Accessibility Mode exit gestures.
 *
 * Supports both Gesture A (opposite corners) and Gesture B (header hold)
 * with proper accessibility support and configurable parameters.
 */
class AccessibilityModeExitToSettingsGestureDetector(
  private val context: Context,
  private val headerBoundsProvider: () -> Rect,
  private val onTriggered: () -> Unit
) : View.OnTouchListener {

  companion object {
    private val TAG = "ExitGesture"

    private const val POINTER_TIMEOUT_MS = 150L
    private const val HAPTIC_FEEDBACK_INTERVAL_MS = 500L
    private const val MIN_DISTANCE_RATIO = 0.85f
  }

  private enum class GestureState {
    IDLE,
    FIRST_POINTER_DOWN,
    SECOND_POINTER_DOWN,
    SINGLE_FINGER_LONG_PRESS,
    GESTURE_ACTIVE
  }

  private enum class GestureType {
    OPPOSITE_CORNERS,
    HEADER_HOLD,
    SINGLE_FINGER_EDGE_DRAG
  }

  // Configuration - lazy loaded for performance
  private val gestureType: GestureType by lazy {
    when (SignalStore.accessibilityMode.exitGestureType) {
      0 -> GestureType.OPPOSITE_CORNERS
      1 -> GestureType.HEADER_HOLD
      2 -> GestureType.SINGLE_FINGER_EDGE_DRAG
      else -> GestureType.SINGLE_FINGER_EDGE_DRAG // Default to easier option for testing
    }
  }

  private val holdDurationMs: Int by lazy { SignalStore.accessibilityMode.exitGestureHoldMs }
  private val cornerSizePx: Float by lazy { context.resources.displayMetrics.density * SignalStore.accessibilityMode.exitGestureCornerDp }
  private val driftTolerancePx: Float by lazy { context.resources.displayMetrics.density * SignalStore.accessibilityMode.exitGestureDriftDp }
  private val screenWidth: Int by lazy { context.resources.displayMetrics.widthPixels }
  private val screenHeight: Int by lazy { context.resources.displayMetrics.heightPixels }
  private val screenDiagonal: Float by lazy { sqrt((screenWidth * screenWidth + screenHeight * screenHeight).toFloat()) }
  private val minDistancePx: Float by lazy { screenDiagonal * MIN_DISTANCE_RATIO }

  private val accessibilityManager: AccessibilityManager by lazy {
    context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
  }

  // State machine variables
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

  // Single-finger edge drag state
  private var singleFingerLongPressStartTime = 0L
  private var singleFingerStartX = 0f
  private var singleFingerStartY = 0f
  private var isAtEdge = false

  override fun onTouch(view: View, event: MotionEvent): Boolean {
    // Skip if accessibility services are active (let them handle gestures)
    if (accessibilityManager.isEnabled && accessibilityManager.isTouchExplorationEnabled) {
      return false
    }

    return when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> handlePointerDown(event, 0)
      MotionEvent.ACTION_POINTER_DOWN -> handlePointerDown(event, event.actionIndex)
      MotionEvent.ACTION_MOVE -> handlePointerMove(event)
      MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> handlePointerUp(event, event.actionIndex)
      MotionEvent.ACTION_CANCEL -> { resetState(); false }
      else -> false
    }
  }

  private fun handlePointerDown(event: MotionEvent, pointerIndex: Int): Boolean {
    val pointerId = event.getPointerId(pointerIndex)
    val x = event.getX(pointerIndex)
    val y = event.getY(pointerIndex)
    val currentTime = System.currentTimeMillis()

    when (state) {
      GestureState.IDLE -> {
        // First pointer down - handle both single and multi-finger gestures
        firstPointerId = pointerId
        firstPointerDownTime = currentTime
        firstPointerStartX = x
        firstPointerStartY = y

        if (gestureType == GestureType.SINGLE_FINGER_EDGE_DRAG) {
          // For single-finger gesture, just record start position and wait for long press
          singleFingerStartX = x
          singleFingerStartY = y
          singleFingerLongPressStartTime = currentTime
          state = GestureState.FIRST_POINTER_DOWN
          Log.d(TAG, "Single-finger gesture tracking started: id=$pointerId at ($x, $y)")
        } else {
          // For multi-finger gestures, wait for second pointer
          state = GestureState.FIRST_POINTER_DOWN
          Log.d(TAG, "First pointer down: id=$pointerId at ($x, $y)")
        }
      }

      GestureState.FIRST_POINTER_DOWN -> {
        // Second pointer down - check if valid gesture start
        val timeDiff = currentTime - firstPointerDownTime
        if (timeDiff <= POINTER_TIMEOUT_MS) {
          secondPointerId = pointerId
          secondPointerDownTime = currentTime
          secondPointerStartX = x
          secondPointerStartY = y

          if (isValidGestureStart(x, y)) {
            state = GestureState.SECOND_POINTER_DOWN
            Log.d(TAG, "Second pointer down: valid gesture start")
          } else {
            resetState()
          }
        } else {
          resetState()
        }
      }

      GestureState.SECOND_POINTER_DOWN -> {
        // Third pointer - cancel gesture
        resetState()
      }

      GestureState.GESTURE_ACTIVE -> {
        // Additional pointer during active gesture - cancel
        resetState()
      }
    }

    return false // Don't consume, let normal touch handling continue
  }

  private fun handlePointerMove(event: MotionEvent): Boolean {
    val currentTime = System.currentTimeMillis()

    // Handle single-finger long press detection
    if (gestureType == GestureType.SINGLE_FINGER_EDGE_DRAG && state == GestureState.FIRST_POINTER_DOWN) {
      val firstIndex = event.findPointerIndex(firstPointerId)
      if (firstIndex == -1) {
        resetState()
        return false
      }

      // Check if long press duration has been reached
      val pressDuration = currentTime - firstPointerDownTime
      if (pressDuration >= 500L) { // 500ms long press
        state = GestureState.SINGLE_FINGER_LONG_PRESS
        Log.d(TAG, "Single-finger long press detected, now waiting for edge drag")
        // Continue to edge detection below
      } else {
        // Still waiting for long press, don't consume event yet
        return false
      }
    }

    if (state != GestureState.GESTURE_ACTIVE && state != GestureState.SINGLE_FINGER_LONG_PRESS) return false

    if (gestureType == GestureType.SINGLE_FINGER_EDGE_DRAG) {
      // Handle single-finger edge drag gesture
      val firstIndex = event.findPointerIndex(firstPointerId)
      if (firstIndex == -1) {
        resetState()
        return false
      }

      val currentX = event.getX(firstIndex)
      val currentY = event.getY(firstIndex)

      // Check if finger is at screen edge
      val isAtEdgeNow = isAtScreenEdge(currentX, currentY)

      if (state == GestureState.SINGLE_FINGER_LONG_PRESS && isAtEdgeNow) {
        // Just arrived at edge during long press - start the gesture
        state = GestureState.GESTURE_ACTIVE
        isAtEdge = true
        lastHapticTime = currentTime
        singleFingerLongPressStartTime = currentTime // Reset timer for hold duration
        Log.d(TAG, "Single-finger gesture started at edge ($currentX, $currentY)")
      } else if (state == GestureState.GESTURE_ACTIVE) {
        if (!isAtEdge && isAtEdgeNow) {
          // Just arrived at edge - start edge hold timer
          isAtEdge = true
          lastHapticTime = currentTime
          Log.d(TAG, "Finger reached edge at ($currentX, $currentY)")
        } else if (isAtEdge && !isAtEdgeNow) {
          // Moved away from edge - cancel gesture
          Log.d(TAG, "Finger moved away from edge")
          resetState()
          return false
        }
      }

      // Check drift tolerance from edge position
      if (isAtEdge) {
        val driftFromEdge = calculateDistance(currentX, currentY, singleFingerStartX, singleFingerStartY)
        if (driftFromEdge > driftTolerancePx) {
          Log.d(TAG, "Drift from edge exceeded: $driftFromEdge > $driftTolerancePx")
          resetState()
          return false
        }

        // Check hold duration at edge
        val holdTime = currentTime - singleFingerLongPressStartTime
        if (holdTime >= holdDurationMs) {
          triggerGesture()
          return true
        }

        // Provide haptic feedback during hold
        if (currentTime - lastHapticTime >= HAPTIC_FEEDBACK_INTERVAL_MS) {
          provideHapticFeedback()
          lastHapticTime = currentTime
        }
      }

      return true // Consume the event during active gesture
    } else {
      // Handle multi-finger gestures (original logic)
      val firstIndex = event.findPointerIndex(firstPointerId)
      val secondIndex = event.findPointerIndex(secondPointerId)
      if (firstIndex == -1 || secondIndex == -1) {
        resetState()
        return false
      }

      // Check drift tolerance
      val firstX = event.getX(firstIndex)
      val firstY = event.getY(firstIndex)
      val secondX = event.getX(secondIndex)
      val secondY = event.getY(secondIndex)

      val firstDrift = calculateDistance(firstX, firstY, firstPointerStartX, firstPointerStartY)
      val secondDrift = calculateDistance(secondX, secondY, secondPointerStartX, secondPointerStartY)

      if (firstDrift > driftTolerancePx || secondDrift > driftTolerancePx) {
        resetState()
        return false
      }

      // Check hold duration
      val holdTime = minOf(
        currentTime - firstPointerDownTime,
        currentTime - secondPointerDownTime
      )

      if (holdTime >= holdDurationMs) {
        triggerGesture()
        return true
      }

      // Provide haptic feedback during hold
      if (currentTime - lastHapticTime >= HAPTIC_FEEDBACK_INTERVAL_MS) {
        provideHapticFeedback()
        lastHapticTime = currentTime
      }

      return true // Consume the event during active gesture
    }
  }

  private fun handlePointerUp(event: MotionEvent, pointerIndex: Int): Boolean {
    val pointerId = event.getPointerId(pointerIndex)

    // If either tracked pointer goes up, cancel gesture
    if (pointerId == firstPointerId || pointerId == secondPointerId) {
      resetState()
    }

    return false
  }

  private fun isValidGestureStart(secondX: Float, secondY: Float): Boolean {
    return when (gestureType) {
      GestureType.OPPOSITE_CORNERS -> isValidCornerGesture(secondX, secondY)
      GestureType.HEADER_HOLD -> isValidHeaderGesture(secondX, secondY)
      GestureType.SINGLE_FINGER_EDGE_DRAG -> false // Single-finger gestures don't use this method
    }
  }

  private fun isValidCornerGesture(secondX: Float, secondY: Float): Boolean {
    // Check if pointers are in opposite corners
    val firstInTopLeft = isInCorner(firstPointerStartX, firstPointerStartY, isTopLeft = true)
    val firstInBottomRight = isInCorner(firstPointerStartX, firstPointerStartY, isTopLeft = false)
    val secondInTopLeft = isInCorner(secondX, secondY, isTopLeft = true)
    val secondInBottomRight = isInCorner(secondX, secondY, isTopLeft = false)

    val isValidPair = (firstInTopLeft && secondInBottomRight) || (firstInBottomRight && secondInTopLeft)
    if (!isValidPair) return false

    // Check minimum distance between pointers
    val distance = calculateDistance(secondX, secondY, firstPointerStartX, firstPointerStartY)
    return distance >= minDistancePx
  }

  private fun isValidHeaderGesture(secondX: Float, secondY: Float): Boolean {
    val headerBounds = headerBoundsProvider()
    return headerBounds.contains(firstPointerStartX.toInt(), firstPointerStartY.toInt()) &&
           headerBounds.contains(secondX.toInt(), secondY.toInt())
  }

  private fun isValidSingleFingerEdgeGesture(x: Float, y: Float): Boolean {
    // For single-finger edge drag, we just need to record the start position
    // The actual edge detection happens during the move phase
    singleFingerStartX = x
    singleFingerStartY = y
    singleFingerLongPressStartTime = System.currentTimeMillis()
    return true
  }

  private fun isInCorner(x: Float, y: Float, isTopLeft: Boolean): Boolean {
    return if (isTopLeft) {
      x <= cornerSizePx && y <= cornerSizePx
    } else {
      x >= (screenWidth - cornerSizePx) && y >= (screenHeight - cornerSizePx)
    }
  }

  private fun calculateDistance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
    val dx = x1 - x2
    val dy = y1 - y2
    return sqrt(dx * dx + dy * dy)
  }

  private fun isAtScreenEdge(x: Float, y: Float): Boolean {
    val edgeTolerancePx = context.resources.displayMetrics.density * 24f // 24dp tolerance
    return x <= edgeTolerancePx || // Left edge
           x >= (screenWidth - edgeTolerancePx) || // Right edge
           y <= edgeTolerancePx || // Top edge
           y >= (screenHeight - edgeTolerancePx) // Bottom edge
  }

  private fun triggerGesture() {
    Log.i(TAG, "Exit gesture triggered!")
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

    // Reset single-finger edge drag state
    singleFingerLongPressStartTime = 0L
    singleFingerStartX = 0f
    singleFingerStartY = 0f
    isAtEdge = false

    Log.d(TAG, "Gesture state reset")
  }

  private fun provideHapticFeedback() {
    // TODO: Implement haptic feedback
    // view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
  }
}
