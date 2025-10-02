package org.thoughtcrime.securesms.accessibility

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.testing.SignalActivityRule
import org.thoughtcrime.securesms.keyvalue.SignalStore

@RunWith(AndroidJUnit4::class)
class AccessibilityLaunchTest {

  @get:Rule
  val harness = SignalActivityRule(othersCount = 4, createGroup = true)

  @Test
  fun launchesNormally() {
    val scenario = harness.launchActivity<MainActivity>()

    // Prefer asserting a real content view rather than sleeping
    onView(withId(android.R.id.content)).check(matches(isDisplayed()))
    scenario.close()
  }

  @Test
  fun launchesWithAccessibilityModeEnabled() {
    // Pick a pre-created recipient from harness and ensure a thread exists
    val recipientId = harness.others.first()
    val recipient = org.thoughtcrime.securesms.recipients.Recipient.resolved(recipientId)
    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(recipient)

    // Enable accessibility mode for that thread (map to recipient-based store)
    enableAccessibilityModeForThread(threadId)

    // Start AccessibilityModeActivity directly to avoid race with router init timing
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val ctx = instrumentation.targetContext
    ctx.startActivity(IntentFactory.accessibilityRoot(ctx, recipientId))

    // Wait for AccessibilityModeActivity to render RecyclerView
    val device = UiDevice.getInstance(instrumentation)
    val pkg = ctx.packageName
    device.wait(Until.hasObject(By.res(pkg, "message_list")), 5_000)

    // Espresso assertion once present
    onView(withId(R.id.message_list)).check(matches(isDisplayed()))
  }
}

private fun enableAccessibilityModeForThread(threadId: Long) {
  // Map threadId -> recipientId and write recipient-based setting
  val rid = org.thoughtcrime.securesms.database.SignalDatabase.threads.getRecipientIdForThreadId(threadId)
  SignalStore.accessibilityMode.isAccessibilityModeEnabled = true
  SignalStore.accessibilityMode.accessibilityRecipientId = rid?.toLong() ?: -1L
}
