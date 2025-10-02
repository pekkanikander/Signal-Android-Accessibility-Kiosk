package org.thoughtcrime.securesms.accessibility

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.thoughtcrime.securesms.keyvalue.AccessibilityModeValues
import org.thoughtcrime.securesms.keyvalue.SignalStore

class AccessibilityModeStoreUnitTest {

  @Before
  fun setUp() {
    mockkObject(SignalStore)
  }

  @After
  fun tearDown() {
    io.mockk.unmockkAll()
  }

  @Test
  fun `store reads and writes suppressNotifications (without Robolectric)`() {
    val am = mockk<AccessibilityModeValues>(relaxed = true)
    every { am.suppressNotifications } returns true
    every { SignalStore.accessibilityMode } returns am

    val store = SignalAccessibilityModeStore()
    assert(store.suppressNotifications)

    store.suppressNotifications = false
    verify { am.suppressNotifications = false }
  }
}
