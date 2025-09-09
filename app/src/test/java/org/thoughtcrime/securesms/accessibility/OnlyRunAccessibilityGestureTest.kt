package org.thoughtcrime.securesms.accessibility

import org.junit.Test

/**
 * Temporary launcher test to invoke only the gesture router unit test logic.
 * This avoids running the entire test suite while we iterate on this one test.
 */
class OnlyRunAccessibilityGestureTest {
  @Test
  fun runGestureRouterUnitTestDirectly() {
    // Call the unit test method directly to exercise the logic in isolation.
    val test = AccessibilityGestureRouterUnitTest()
    test.tripleTap_stateTransitions()
  }
}
