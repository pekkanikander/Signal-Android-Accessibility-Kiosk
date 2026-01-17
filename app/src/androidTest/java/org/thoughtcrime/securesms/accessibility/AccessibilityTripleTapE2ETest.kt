package org.thoughtcrime.securesms.accessibility

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.test.ext.junit.rules.activityScenarioRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.thoughtcrime.securesms.accessibility.AccessibilityModeActivity
import org.thoughtcrime.securesms.keyvalue.SignalStore

@RunWith(AndroidJUnit4::class)
class AccessibilityTripleTapE2ETest {

  @get:Rule
  val activityRule = activityScenarioRule<AccessibilityModeActivity>()

  @Test
  fun tripleTapShowsConfirmationDialog() {
    // Ensure triple-tap gesture is selected
    SignalStore.accessibilityMode.exitGestureType = 1 // TRIPLE_TAP_DEBUG value

    val context = ApplicationProvider.getApplicationContext<Context>()
    val intent = Intent(context, AccessibilityModeActivity::class.java)
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    activityRule.launchActivity(intent)

    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    // Allow activity to appear
    device.wait(Until.hasObject(By.pkg(device.launcherPackageName).depth(0)), 2000)

    // Tap header area three times
    val displayWidth = device.displayWidth
    val x = displayWidth / 2
    val y = 50 // near top - header area

    for (i in 0 until 3) {
      device.click(x, y)
      Thread.sleep(200)
    }

    // Wait for confirmation dialog (seekbar id should be present)
    val found = device.wait(Until.hasObject(By.res("org.thoughtcrime.securesms", "confirm_slider")), 2000)
    assertTrue("Confirmation dialog should be displayed after triple tap", found)
  }
}


