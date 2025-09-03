package org.thoughtcrime.securesms.accessibility

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Simple UIAutomator test to validate Accessibility elements are present on Signal main screen.
 * This is a lightweight smoke test and should be extended for robust verification.
 */
@RunWith(AndroidJUnit4::class)
class AccessibilityTalkBackUiTest {
    private lateinit var device: UiDevice

    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        // Wait for the device to be idle
        device.waitForIdle()
    }

    @Test
    fun mainScreen_hasAccessibleConversationList() {
        // Launch Signal RoutingActivity
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = context.packageManager.getLaunchIntentForPackage("org.thoughtcrime.securesms")
        intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)

        // Wait for app to appear
        device.wait(Until.hasObject(By.pkg("org.thoughtcrime.securesms")), 5000)

        // Look for an element that is likely to exist and should have a content-desc or text
        val node = device.wait(Until.findObject(By.descContains("Conversation").or(By.textContains("Conversation"))), 5000)

        assertTrue("Conversation list or label should be present", node != null)
    }
}
