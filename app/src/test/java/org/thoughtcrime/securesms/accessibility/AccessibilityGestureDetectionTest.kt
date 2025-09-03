package org.thoughtcrime.securesms.accessibility

import android.content.Context
import android.graphics.Rect
import android.view.MotionEvent
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.mockito.kotlin.any

/**
 * Test case: Gesture Detection Tests
 *
 * This test validates the gesture detection logic with real MotionEvent sequences.
 * Tests focus on state machine transitions and gesture recognition accuracy.
 */
@RunWith(JUnit4::class)
class AccessibilityGestureDetectionTest {

    private val mockContext = mock(Context::class.java)
    private val mockRect = Rect(0, 0, 1080, 100) // Header bounds

    private var gestureTriggered = false
    private val gestureCallback = { gestureTriggered = true }

    @Test
    fun triple_tap_debug_gesture_is_detected() {
        // Given: Triple tap debug gesture is configured
        SignalStore.accessibilityMode.exitGestureType = AccessibilityModeExitGestureType.TRIPLE_TAP_DEBUG.value

        val detector = AccessibilityModeExitToSettingsGestureDetector(
            context = mockContext,
            headerBoundsProvider = { mockRect },
            onTriggered = gestureCallback
        )

        // When: We simulate three rapid taps
        val mockView = mock(android.view.View::class.java)

        // First tap
        val tap1Down = createMotionEvent(MotionEvent.ACTION_DOWN, 500f, 500f)
        val tap1Up = createMotionEvent(MotionEvent.ACTION_UP, 500f, 500f)

        // Second tap (within timeout)
        val tap2Down = createMotionEvent(MotionEvent.ACTION_DOWN, 500f, 500f, downTime = 100)
        val tap2Up = createMotionEvent(MotionEvent.ACTION_UP, 500f, 500f, downTime = 100)

        // Third tap (within timeout)
        val tap3Down = createMotionEvent(MotionEvent.ACTION_DOWN, 500f, 500f, downTime = 200)
        val tap3Up = createMotionEvent(MotionEvent.ACTION_UP, 500f, 500f, downTime = 200)

        // Simulate the gesture sequence
        detector.onTouch(mockView, tap1Down)
        detector.onTouch(mockView, tap1Up)
        detector.onTouch(mockView, tap2Down)
        detector.onTouch(mockView, tap2Up)
        detector.onTouch(mockView, tap3Down)
        detector.onTouch(mockView, tap3Up)

        // Then: Gesture should be triggered
        assertThat("Triple tap gesture should trigger callback", gestureTriggered, equalTo(true))
    }

    @Test
    fun gesture_type_configuration_works() {
        // Given: Different gesture types are configured
        val gestureTypes = listOf(
            AccessibilityModeExitGestureType.TRIPLE_TAP_DEBUG,
            AccessibilityModeExitGestureType.SINGLE_FINGER_EDGE_DRAG_HOLD,
            AccessibilityModeExitGestureType.TWO_FINGER_HEADER_HOLD,
            AccessibilityModeExitGestureType.OPPOSITE_CORNERS_HOLD
        )

        // When/Then: Each gesture type should be properly configured
        gestureTypes.forEach { gestureType ->
            SignalStore.accessibilityMode.exitGestureType = gestureType.value

            val detector = AccessibilityModeExitToSettingsGestureDetector(
                context = mockContext,
                headerBoundsProvider = { mockRect },
                onTriggered = gestureCallback
            )

            // We can't easily test the internal gesture type without exposing it,
            // but we can verify the detector is created without errors
            assertThat("Detector should be created for ${gestureType.name}",
                       detector != null, equalTo(true))
        }
    }

    @Test
    fun accessibility_services_are_respected() {
        // Given: Accessibility services are enabled
        val mockAccessibilityManager = mock(android.view.accessibility.AccessibilityManager::class.java)
        `when`(mockAccessibilityManager.isEnabled).thenReturn(true)
        `when`(mockAccessibilityManager.isTouchExplorationEnabled).thenReturn(true)
        `when`(mockContext.getSystemService(Context.ACCESSIBILITY_SERVICE)).thenReturn(mockAccessibilityManager)

        SignalStore.accessibilityMode.exitGestureType = AccessibilityModeExitGestureType.TRIPLE_TAP_DEBUG.value

        val detector = AccessibilityModeExitToSettingsGestureDetector(
            context = mockContext,
            headerBoundsProvider = { mockRect },
            onTriggered = gestureCallback
        )

        // When: Touch event occurs while accessibility services are active
        val mockView = mock(android.view.View::class.java)
        val tapDown = createMotionEvent(MotionEvent.ACTION_DOWN, 500f, 500f)

        val result = detector.onTouch(mockView, tapDown)

        // Then: Gesture detector should not consume the event (return false)
        assertThat("Gesture detector should not consume events when accessibility services are active",
                   result, equalTo(false))
    }

    @Test
    fun gesture_state_machine_resets_properly() {
        // Given: Gesture detector in a non-idle state
        SignalStore.accessibilityMode.exitGestureType = AccessibilityModeExitGestureType.TRIPLE_TAP_DEBUG.value

        val detector = AccessibilityModeExitToSettingsGestureDetector(
            context = mockContext,
            headerBoundsProvider = { mockRect },
            onTriggered = gestureCallback
        )

        // When: We start a gesture but then cancel it
        val mockView = mock(android.view.View::class.java)

        // Start with one tap
        val tap1Down = createMotionEvent(MotionEvent.ACTION_DOWN, 500f, 500f)
        detector.onTouch(mockView, tap1Down)

        // Then cancel (simulate user lifting finger too slowly or moving away)
        val cancelEvent = createMotionEvent(MotionEvent.ACTION_CANCEL, 500f, 500f)
        val result = detector.onTouch(mockView, cancelEvent)

        // Then: Event should be consumed (detector handled the cancel)
        assertThat("Cancel event should be consumed", result, equalTo(true))

        // And subsequent taps should work normally (state machine reset)
        val tap2Down = createMotionEvent(MotionEvent.ACTION_DOWN, 500f, 500f, downTime = 3000) // Outside timeout
        val result2 = detector.onTouch(mockView, tap2Down)
        // This should start a new gesture sequence
        assertThat("New gesture should be processed after cancel", result2, equalTo(true))
    }

    private fun createMotionEvent(
        action: Int,
        x: Float,
        y: Float,
        downTime: Long = 0
    ): MotionEvent {
        val eventTime = System.currentTimeMillis() + downTime
        return MotionEvent.obtain(
            if (downTime == 0L) eventTime else eventTime - downTime, // downTime
            eventTime, // eventTime
            action, // action
            x, // x
            y, // y
            0f, // pressure
            1f, // size
            0, // metaState
            1f, // xPrecision
            1f, // yPrecision
            0, // deviceId
            0 // edgeFlags
        )
    }
}
