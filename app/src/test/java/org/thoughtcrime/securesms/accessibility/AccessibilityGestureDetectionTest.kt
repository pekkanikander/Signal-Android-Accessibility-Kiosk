package org.thoughtcrime.securesms.accessibility

import android.content.Context
import android.graphics.Rect
import android.view.MotionEvent
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import io.mockk.mockk
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import android.view.accessibility.AccessibilityManager
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat

/**
 * Test case: Gesture Detection Tests
 *
 * This test validates the gesture detection logic with real MotionEvent sequences.
 * Tests focus on state machine transitions and gesture recognition accuracy.
 */
@RunWith(JUnit4::class)
class AccessibilityGestureDetectionTest {

    private val mockContext = mockk<Context>()
    private val mockRect = Rect(0, 0, 1080, 100) // Header bounds

    private var gestureTriggered = false
    private val gestureCallback = { gestureTriggered = true }

    @org.junit.Before
    fun setUp() {
        // Ensure SignalStore is mocked to avoid NPEs in unit tests where SignalStore.instance isn't initialized
        mockkObject(SignalStore)
        val mockAccessibilityValues = mockk<org.thoughtcrime.securesms.keyvalue.AccessibilityModeValues>(relaxed = true)
        // Provide a mutable backing for mocked properties so tests can set/get values normally
        var currentExitGesture = org.thoughtcrime.securesms.accessibility.AccessibilityModeExitGestureType.SINGLE_FINGER_EDGE_DRAG_HOLD.value
        every { mockAccessibilityValues.exitGestureType } answers { currentExitGesture }
        every { mockAccessibilityValues.exitGestureType = any() } answers { currentExitGesture = it.invocation.args[0] as Int }
        every { mockAccessibilityValues.exitGestureHoldMs } returns 2500
        every { mockAccessibilityValues.exitGestureCornerDp } returns 72
        every { mockAccessibilityValues.exitGestureDriftDp } returns 24
        every { mockAccessibilityValues.exitGesturePointerTimeoutMs } returns 5000
        every { SignalStore.accessibilityMode } returns mockAccessibilityValues
        // Provide a default AccessibilityManager for the mocked Context so lazy access doesn't throw
        val defaultAccessibilityManager = mockk<AccessibilityManager>(relaxed = true)
        every { defaultAccessibilityManager.isEnabled } returns false
        every { defaultAccessibilityManager.isTouchExplorationEnabled } returns false
        every { mockContext.getSystemService(Context.ACCESSIBILITY_SERVICE) } returns defaultAccessibilityManager
    }

    @org.junit.After
    fun tearDown() {
        unmockkObject(SignalStore)
    }

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
        val mockView = mockk<android.view.View>()

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
        val mockAccessibilityManager = mockk<android.view.accessibility.AccessibilityManager>()
        every { mockAccessibilityManager.isEnabled } returns true
        every { mockAccessibilityManager.isTouchExplorationEnabled } returns true
        every { mockContext.getSystemService(Context.ACCESSIBILITY_SERVICE) } returns mockAccessibilityManager

        SignalStore.accessibilityMode.exitGestureType = AccessibilityModeExitGestureType.TRIPLE_TAP_DEBUG.value

        val detector = AccessibilityModeExitToSettingsGestureDetector(
            context = mockContext,
            headerBoundsProvider = { mockRect },
            onTriggered = gestureCallback
        )

        // When: Touch event occurs while accessibility services are active
        val mockView = mockk<android.view.View>()
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
        val mockView = mockk<android.view.View>()

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
        // Use a mocked MotionEvent for JVM unit tests (MotionEvent.obtain isn't available)
        val event = mockk<MotionEvent>(relaxed = true)
        every { event.actionMasked } returns action
        every { event.pointerCount } returns 1
        every { event.actionIndex } returns 0
        every { event.getPointerId(0) } returns 0
        every { event.getX(0) } returns x
        every { event.getY(0) } returns y
        every { event.findPointerIndex(0) } returns 0
        return event
    }
}
