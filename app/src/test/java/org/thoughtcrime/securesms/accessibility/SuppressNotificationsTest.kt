package org.thoughtcrime.securesms.accessibility

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Rule
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule
import org.thoughtcrime.securesms.keyvalue.AccessibilityModeValues
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.notifications.NotificationCancellationHelper
import org.thoughtcrime.securesms.notifications.v2.DefaultMessageNotifier
import org.thoughtcrime.securesms.notifications.v2.ConversationId

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = org.thoughtcrime.securesms.testing.accessibility.AccessibilityTestApplication::class)
class SuppressNotificationsTest {

  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  @Before
  fun setUp() {
    mockkObject(SignalStore)
    io.mockk.mockkStatic(org.thoughtcrime.securesms.service.KeyCachingService::class)
    every { org.thoughtcrime.securesms.service.KeyCachingService.isLocked(any()) } returns false
    // provide a minimal mocked SettingsValues for constructor usage
    val mockSettings = mockk<org.thoughtcrime.securesms.keyvalue.SettingsValues>(relaxed = true)
    every { mockSettings.messageNotificationsPrivacy } returns org.thoughtcrime.securesms.preferences.widgets.NotificationPrivacyPreference("all")
    every { SignalStore.settings } returns mockSettings
  }

  @After
  fun tearDown() {
    io.mockk.unmockkAll()
  }

  @Test
  fun `DefaultMessageNotifier cancels when suppression active`() {
    val am = mockk<AccessibilityModeValues>(relaxed = true)
    every { am.suppressNotifications } returns true
    every { am.isAccessibilityModeEnabled } returns true
    every { SignalStore.accessibilityMode } returns am

    // mock static helper
    io.mockk.mockkStatic(NotificationCancellationHelper::class)
    val context = ApplicationProvider.getApplicationContext<Application>()

    val notifier = DefaultMessageNotifier(context)

    notifier.updateNotification(context)

    verify(atLeast = 1) { NotificationCancellationHelper.cancelAllMessageNotifications(context, any()) }
  }
}
