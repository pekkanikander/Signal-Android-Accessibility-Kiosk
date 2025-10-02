package org.thoughtcrime.securesms.testing

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.assertion.ViewAssertions.matches
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.keyvalue.SignalStore

object AccessibilityTestHelpers {

    fun enableAccessibilityModeForThread(threadId: Long) {
        SignalStore.accessibilityMode.isAccessibilityModeEnabled = true
        val rid = org.thoughtcrime.securesms.database.SignalDatabase.threads.getRecipientIdForThreadId(threadId)
        SignalStore.accessibilityMode.accessibilityRecipientId = rid?.toLong() ?: -1L
    }

    fun assertConversationListPresent() {
        onView(withId(R.id.message_list)).check(matches(org.hamcrest.Matchers.not(org.hamcrest.Matchers.nullValue())))
    }
}
