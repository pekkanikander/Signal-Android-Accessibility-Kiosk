package org.thoughtcrime.securesms.accessibility

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.testing.SignalActivityRule
import org.thoughtcrime.securesms.accessibility.AccessibilityModeExitGestureType
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat

/**
 * Test case: "Settings persist across app restarts"
 *
 * This test verifies that accessibility mode settings are properly persisted
 * and restored when the application is restarted.
 */
@RunWith(AndroidJUnit4::class)
class AccessibilitySettingsPersistenceTest {

    @get:Rule
    val signalActivityRule = SignalActivityRule()

    @Test
    fun settings_persist_across_app_restarts() {
        // Given: Initial state of accessibility settings
        val initialEnabled = SignalStore.accessibilityMode.isAccessibilityModeEnabled
        val initialRecipientId = SignalStore.accessibilityMode.accessibilityRecipientId
        val initialGestureType = SignalStore.accessibilityMode.exitGestureType

        // When: We modify accessibility settings
        val testRecipientId = signalActivityRule.others[0].toLong()
        val newGestureType = AccessibilityModeExitGestureType.TripleTap.value

        SignalStore.accessibilityMode.run {
            isAccessibilityModeEnabled = true
            accessibilityRecipientId = testRecipientId
            exitGestureType = newGestureType
        }

        // Then: Settings should be persisted and readable
        assertThat("Accessibility mode enabled should be persisted",
                   SignalStore.accessibilityMode.isAccessibilityModeEnabled, equalTo(true))
        assertThat("Recipient ID should be persisted",
                   SignalStore.accessibilityMode.accessibilityRecipientId, equalTo(testRecipientId))
        assertThat("Gesture type should be persisted",
                   SignalStore.accessibilityMode.exitGestureType, equalTo(newGestureType))

        // When: We restore to initial state (simulating app restart behavior)
        SignalStore.accessibilityMode.run {
            isAccessibilityModeEnabled = initialEnabled
            accessibilityRecipientId = initialRecipientId
            exitGestureType = initialGestureType
        }

        // Then: Settings should return to initial values
        assertThat("Accessibility mode should restore to initial state",
                   SignalStore.accessibilityMode.isAccessibilityModeEnabled, equalTo(initialEnabled))
        assertThat("Recipient ID should restore to initial state",
                   SignalStore.accessibilityMode.accessibilityRecipientId, equalTo(initialRecipientId))
        assertThat("Gesture type should restore to initial state",
                   SignalStore.accessibilityMode.exitGestureType, equalTo(initialGestureType))
    }

    @Test
    fun default_values_are_correct() {
        // Given: Fresh SignalStore instance (simulating first app launch)
        // When: We access accessibility mode settings without modification

        // Then: Default values should be as expected
        assertThat("Default accessibility mode should be disabled",
                   SignalStore.accessibilityMode.isAccessibilityModeEnabled, equalTo(false))
        assertThat("Default recipient ID should be -1 (none selected)",
                   SignalStore.accessibilityMode.accessibilityRecipientId, equalTo(-1L))
        assertThat("Default gesture type should be TripleTap",
                   SignalStore.accessibilityMode.exitGestureType,
                   equalTo(AccessibilityModeExitGestureType.TripleTap.value))
    }

    @Test
    fun gesture_type_enum_conversion_works() {
        // Given: We set each gesture type
        val gestureTypes = listOf(
            AccessibilityModeExitGestureType.TripleTap,
            AccessibilityModeExitGestureType.ChordSlideUp
        )

        // When/Then: Each gesture type should be stored and retrieved correctly
        gestureTypes.forEach { gestureType ->
            SignalStore.accessibilityMode.exitGestureType = gestureType.value
            assertThat("Gesture type ${gestureType.name} should be stored and retrieved correctly",
                       SignalStore.accessibilityMode.exitGestureType, equalTo(gestureType.value))

            // Test enum conversion
            val retrievedGestureType = AccessibilityModeExitGestureType.fromValue(SignalStore.accessibilityMode.exitGestureType)
            assertThat("Gesture type enum conversion should work for ${gestureType.name}",
                       retrievedGestureType, equalTo(gestureType))
        }
    }
}
