package org.thoughtcrime.securesms.accessibility

import android.content.Intent
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * Router behaviour tests against AccessibilityModeRouter.
 */
@RunWith(JUnit4::class)
class AccessibilityRouterTest {

  private class TestStore(var enabled: Boolean, var rid: RecipientId?) : AccessibilityModeStore {
    override fun state(): Flow<AccessibilityModeState> = flowOf(current())
    override fun current(): AccessibilityModeState = AccessibilityModeState(enabled = enabled, recipientId = rid)
    override fun setEnabled(enabled: Boolean, recipientId: RecipientId?) {
      this.enabled = enabled
      this.rid = recipientId
    }
  }

  @Before
  fun setUp() {
    AccessibilityModeRouter.store = TestStore(enabled = false, rid = null)
  }

  @After
  fun tearDown() {
    AccessibilityModeRouter.store = TestStore(enabled = false, rid = null)
  }

  @Test
  fun route_from_main_launches_accessibility_when_enabled_and_has_recipient() {
    val rid = RecipientId.from(123L)
    AccessibilityModeRouter.store = TestStore(enabled = true, rid = rid)

    val host = mockk<MainActivity>(relaxed = true)

    AccessibilityModeRouter.routeIfNeeded(host)

    verify(exactly = 1) {
      host.startActivity(withArg { intent ->
        val compName = intent.component?.className ?: ""
        assertThat(compName, containsString("AccessibilityModeActivity"))
        val extra = intent.getParcelableExtra<RecipientId>("selected_recipient_id")
        assertThat(extra, equalTo(rid))
        val expected = Intent.FLAG_ACTIVITY_NEW_TASK or
                       Intent.FLAG_ACTIVITY_CLEAR_TASK or
                       Intent.FLAG_ACTIVITY_NO_ANIMATION
        assertThat(intent.flags and expected, equalTo(expected))
      })
    }
    verify { host.overridePendingTransition(0, 0) }
  }

  @Test
  fun route_from_main_does_nothing_when_disabled() {
    AccessibilityModeRouter.store = TestStore(enabled = false, rid = RecipientId.from(7L))
    val host = mockk<MainActivity>(relaxed = true)

    AccessibilityModeRouter.routeIfNeeded(host)

    verify(exactly = 0) { host.startActivity(any()) }
  }

  @Test
  fun route_from_accessibility_rebases_to_normal_when_disabled() {
    AccessibilityModeRouter.store = TestStore(enabled = false, rid = null)

    val host = mockk<AccessibilityModeActivity>(relaxed = true)
    every { host.intent } returns Intent()

    AccessibilityModeRouter.routeIfNeeded(host)

    verify(exactly = 1) {
      host.startActivity(withArg { intent ->
        val compName = intent.component?.className ?: ""
        assertThat(compName, containsString("MainActivity"))
      })
    }
    verify { host.overridePendingTransition(0, 0) }
  }

  @Test
  fun route_from_accessibility_rebases_when_recipient_mismatch() {
    val configuredRid = RecipientId.from(42L)
    val wrongRid = RecipientId.from(99L)
    AccessibilityModeRouter.store = TestStore(enabled = true, rid = configuredRid)

    val host = mockk<AccessibilityModeActivity>(relaxed = true)
    every { host.intent } returns Intent().apply { putExtra("selected_recipient_id", wrongRid) }

    AccessibilityModeRouter.routeIfNeeded(host)

    verify(exactly = 1) {
      host.startActivity(withArg { intent ->
        val compName = intent.component?.className ?: ""
        assertThat(compName, containsString("AccessibilityModeActivity"))
        val extra = intent.getParcelableExtra<RecipientId>("selected_recipient_id")
        assertThat(extra, equalTo(configuredRid))
      })
    }
    verify { host.overridePendingTransition(0, 0) }
  }

  @Test
  fun route_from_accessibility_noop_when_recipient_matches_and_enabled() {
    val rid = RecipientId.from(77L)
    AccessibilityModeRouter.store = TestStore(enabled = true, rid = rid)

    val host = mockk<AccessibilityModeActivity>(relaxed = true)
    every { host.intent } returns Intent().apply { putExtra("selected_recipient_id", rid) }

    AccessibilityModeRouter.routeIfNeeded(host)

    verify(exactly = 0) { host.startActivity(any()) }
  }
}
