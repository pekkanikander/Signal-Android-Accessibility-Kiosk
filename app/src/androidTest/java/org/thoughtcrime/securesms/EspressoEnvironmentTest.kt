package org.thoughtcrime.securesms

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Simple Espresso test to verify the testing environment is working.
 * This test just launches the MainActivity and verifies it's displayed.
 */
@RunWith(AndroidJUnit4::class)
class EspressoEnvironmentTest {

  @Test
  fun testMainActivityLaunches() {
    // Launch the main activity
    val scenario = ActivityScenario.launch(MainActivity::class.java)
    
    // Wait a moment for the activity to fully load
    Thread.sleep(1000)
    
    // Verify the activity is displayed
    // We'll just check that the activity is visible
    // This is a basic test to verify Espresso is working
    onView(withId(android.R.id.content)).check(matches(isDisplayed()))
    
    scenario.close()
  }
}
