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
        // Launch Signal RoutingActivity. getLaunchIntentForPackage may return null
        // if the package isn't found for the instrumentation context, so fall
        // back to using a shell am start via UiDevice.
        val packageName = "org.thoughtcrime.securesms"
        val instr = InstrumentationRegistry.getInstrumentation()
        val context = instr.targetContext

        // Quick sanity: ensure the package is installed before trying to launch.
        val pm = context.packageManager
        var installCheckException: Exception? = null
        val installed = try {
            pm.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            // Record and log the exception so it appears in instrumentation output
            installCheckException = e
            android.util.Log.w("AccessibilityTalkBackUiTest", "packageManager.getPackageInfo failed", e)
            false
        }

        if (!installed) {
            throw AssertionError("Required package not installed on device: $packageName; check error: ${installCheckException?.message}", installCheckException)
        }

        val intent = pm.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
            context.startActivity(intent)
        } else {
            // Best-effort fallback: start via shell command (RoutingActivity assumed)
            val activityName = "$packageName/.RoutingActivity"
            device.executeShellCommand("am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n $activityName")
        }

        // Give the app additional time to cold-start and settle
        Thread.sleep(5000)

        // Wait for app to appear
        device.wait(Until.hasObject(By.pkg("org.thoughtcrime.securesms")), 5000)

        // Look for an element that is likely to exist and should have a content-desc or text
        var node = device.wait(Until.findObject(By.descContains("Conversation")), 5000)
        if (node == null) {
            node = device.wait(Until.findObject(By.textContains("Conversation")), 5000)
        }

        assertTrue("Conversation list or label should be present", node != null)
    }
}
