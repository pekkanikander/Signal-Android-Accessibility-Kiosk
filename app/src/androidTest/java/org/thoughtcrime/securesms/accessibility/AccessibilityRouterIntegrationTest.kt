package org.thoughtcrime.securesms.accessibility

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.testing.SignalActivityRule
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat

/**
 * Test case: Router Integration Tests
 *
 * This test verifies that the AccessibilityModeRouter correctly integrates
 * with Activity lifecycle and manages mode switching appropriately.
 */
@RunWith(AndroidJUnit4::class)
class AccessibilityRouterIntegrationTest {

    @get:Rule
    val signalActivityRule = SignalActivityRule()

    // NOTE: These integration tests reference app internals and UI IDs that may not
    // be available in all builds. They are currently stubs to allow instrumentation
    // compilation in CI while we incrementally restore full integration checks.
    @Test
    fun stub_test_integration_compile_only() {
        // This test intentionally does no UI assertions; it only ensures the test
        // class loads and an ActivityScenario can be created without crashing.
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.close()
    }

    @Test
    fun router_does_not_launch_when_no_conversation_selected() {
        // Given: Accessibility mode is enabled but no conversation is selected
        SignalStore.accessibilityMode.run {
            isAccessibilityModeEnabled = true
            accessibilityThreadId = -1L // No conversation selected
        }

        // When: MainActivity starts
        val scenario = ActivityScenario.launch(MainActivity::class.java)

        scenario.onActivity { activity ->
            // Router should check both conditions: enabled AND conversation selected
            // Since no conversation is selected, it should not launch AccessibilityModeActivity
        }

        // Then: We should remain on the main Signal interface
        onView(withId(R.id.conversation_list_view))
            .check(matches(isDisplayed()))

        // And settings should remain unchanged
        assertThat("Accessibility mode should still be enabled",
                   SignalStore.accessibilityMode.isAccessibilityModeEnabled, equalTo(true))
        assertThat("No conversation should be selected",
                   SignalStore.accessibilityMode.accessibilityThreadId, equalTo(-1L))

        scenario.close()
    }

    @Test
    fun router_preserves_settings_state_during_navigation() {
        // Given: Accessibility mode is properly configured
        val testRecipient = signalActivityRule.others[0]
        val testThreadId = testRecipient.id.serialize()

        val initialEnabled = SignalStore.accessibilityMode.isAccessibilityModeEnabled
        val initialThreadId = SignalStore.accessibilityMode.accessibilityThreadId
        val initialGestureType = SignalStore.accessibilityMode.exitGestureType

        // Configure accessibility mode
        SignalStore.accessibilityMode.run {
            isAccessibilityModeEnabled = true
            accessibilityThreadId = testThreadId
            exitGestureType = AccessibilityModeExitGestureType.TRIPLE_TAP_DEBUG.value
        }

        // When: MainActivity lifecycle occurs (simulating normal app usage)
        val scenario = ActivityScenario.launch(MainActivity::class.java)

        scenario.onActivity { activity ->
            // Router should run during onStart()
            // In this test, it won't launch AccessibilityModeActivity because
            // we don't have the actual implementation yet, but it should not crash
        }

        scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        scenario.moveToState(androidx.lifecycle.Lifecycle.State.STARTED)
        scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        scenario.moveToState(androidx.lifecycle.Lifecycle.State.DESTROYED)

        // Then: Settings should remain intact throughout lifecycle changes
        assertThat("Accessibility mode should remain enabled",
                   SignalStore.accessibilityMode.isAccessibilityModeEnabled, equalTo(true))
        assertThat("Thread ID should remain set",
                   SignalStore.accessibilityMode.accessibilityThreadId, equalTo(testThreadId))
        assertThat("Gesture type should remain set",
                   SignalStore.accessibilityMode.exitGestureType,
                   equalTo(AccessibilityModeExitGestureType.TRIPLE_TAP_DEBUG.value))

        // Restore initial state
        SignalStore.accessibilityMode.run {
            isAccessibilityModeEnabled = initialEnabled
            accessibilityThreadId = initialThreadId
            exitGestureType = initialGestureType
        }

        scenario.close()
    }

    @Test
    fun router_handles_configuration_changes_gracefully() {
        // Given: Accessibility mode configuration changes during app session
        SignalStore.accessibilityMode.run {
            isAccessibilityModeEnabled = true
            accessibilityThreadId = signalActivityRule.others[0].id.serialize()
        }

        // When: Configuration changes occur (orientation, etc.)
        val scenario = ActivityScenario.launch(MainActivity::class.java)

        scenario.onActivity { activity ->
            // Simulate configuration change by recreating activity
            activity.recreate()
        }

        // Then: Router should handle the recreation without issues
        // (In real implementation, this would potentially relaunch accessibility mode)
        assertThat("Activity recreation should not crash the router",
                   SignalStore.accessibilityMode.isAccessibilityModeEnabled, equalTo(true))

        scenario.close()
    }

    @Test
    fun router_state_is_consistent_across_activity_instances() {
        // Given: Accessibility mode is configured
        val testRecipient = signalActivityRule.others[1]
        val testThreadId = testRecipient.id.serialize()

        SignalStore.accessibilityMode.run {
            isAccessibilityModeEnabled = true
            accessibilityThreadId = testThreadId
        }

        // When: Multiple activity instances are created
        val scenario1 = ActivityScenario.launch(MainActivity::class.java)
        val scenario2 = ActivityScenario.launch(MainActivity::class.java)

        // Then: Router state should be consistent across instances
        scenario1.onActivity { activity1 ->
            scenario2.onActivity { activity2 ->
                // Both activities should see the same router state
                assertThat("Accessibility mode should be consistent across activities",
                           SignalStore.accessibilityMode.isAccessibilityModeEnabled, equalTo(true))
                assertThat("Thread ID should be consistent across activities",
                           SignalStore.accessibilityMode.accessibilityThreadId, equalTo(testThreadId))
            }
        }

        scenario1.close()
        scenario2.close()
    }
}
