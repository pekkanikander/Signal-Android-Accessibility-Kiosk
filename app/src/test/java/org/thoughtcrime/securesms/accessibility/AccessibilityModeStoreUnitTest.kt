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

  // suppressNotifications removed; no longer applicable
}
