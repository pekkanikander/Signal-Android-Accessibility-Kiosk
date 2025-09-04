package org.thoughtcrime.securesms.accessibility

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.thoughtcrime.securesms.testing.SignalActivityRule
import org.thoughtcrime.securesms.testing.AccessibilityTestHelpers
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.MainActivity

@RunWith(AndroidJUnit4::class)
class AccessibilityEspressoE2E {

    @get:Rule
    val harness = SignalActivityRule()

    @Test
    fun enableAccessibilityMode_showsConversationList() {
        // Create or get a thread for one of the pre-created recipients from the harness
        val recipientId = harness.others.first()
        val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(recipientId)

        // Enable accessibility mode pointing to that thread before launching UI
        AccessibilityTestHelpers.enableAccessibilityModeForThread(threadId)

        // Launch main activity and assert the conversation RecyclerView is present
        harness.launchActivity<MainActivity>()
        AccessibilityTestHelpers.assertConversationListPresent()
    }
}


