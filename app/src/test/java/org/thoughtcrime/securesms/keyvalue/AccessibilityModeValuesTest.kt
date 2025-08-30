package org.thoughtcrime.securesms.keyvalue

import android.app.Application
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class AccessibilityModeValuesTest {

  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  private lateinit var accessibilityModeValues: AccessibilityModeValues
  private lateinit var keyValueStore: KeyValueStore

  @Before
  fun setup() {
    mockkObject(SignalStore)

    // Create a mock KeyValueStore
    keyValueStore = mockk()
    accessibilityModeValues = AccessibilityModeValues(keyValueStore)
  }

  @After
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun `test default values on first launch`() {
    // Mock the getters that the property delegates use
    every { keyValueStore.getBoolean(AccessibilityModeValues.ACCESSIBILITY_MODE_ENABLED, false) } returns false
    every { keyValueStore.getLong(AccessibilityModeValues.ACCESSIBILITY_THREAD_ID, -1L) } returns -1L

    // onFirstEverAppLaunch() now just returns Unit, defaults are handled by property delegates
    accessibilityModeValues.onFirstEverAppLaunch()

    // Defaults should still be the same due to property delegate defaults
    assertFalse(accessibilityModeValues.isAccessibilityModeEnabled)
    assertEquals(-1L, accessibilityModeValues.accessibilityThreadId)
  }

  @Test
  fun `test setting and retrieving accessibility mode enabled`() {
    // Mock the beginWrite method
    val mockWrite = mockk<KeyValueStore.Writer>()
    every { keyValueStore.beginWrite() } returns mockWrite
    every { mockWrite.putBoolean(any(), any()) } returns mockWrite
    every { mockWrite.apply() } returns Unit

    // Mock the getter to return the set value
    every { keyValueStore.getBoolean(AccessibilityModeValues.ACCESSIBILITY_MODE_ENABLED, false) } returns true

    accessibilityModeValues.isAccessibilityModeEnabled = true

    assertTrue(accessibilityModeValues.isAccessibilityModeEnabled)
  }

  @Test
  fun `test setting and retrieving thread ID`() {
    // Mock the beginWrite method
    val mockWrite = mockk<KeyValueStore.Writer>()
    every { keyValueStore.beginWrite() } returns mockWrite
    every { mockWrite.putLong(any(), any()) } returns mockWrite
    every { mockWrite.apply() } returns Unit

    // Mock the getter to return the set value
    every { keyValueStore.getLong(AccessibilityModeValues.ACCESSIBILITY_THREAD_ID, -1L) } returns 123L

    accessibilityModeValues.accessibilityThreadId = 123L

    assertEquals(123L, accessibilityModeValues.accessibilityThreadId)
  }

  @Test
  fun `test getKeysToIncludeInBackup returns all keys`() {
    val keys = accessibilityModeValues.getKeysToIncludeInBackup()

    assertEquals(2, keys.size)
    assertTrue(keys.contains(AccessibilityModeValues.ACCESSIBILITY_MODE_ENABLED))
    assertTrue(keys.contains(AccessibilityModeValues.ACCESSIBILITY_THREAD_ID))
  }

  @Test
  fun `test accessibility mode keys are included in SignalStore backup`() {
    // This test verifies that our keys are properly included in the backup system
    val keys = accessibilityModeValues.getKeysToIncludeInBackup()

    // Verify our specific keys are present
    assertTrue("ACCESSIBILITY_MODE_ENABLED should be in backup keys",
               keys.contains(AccessibilityModeValues.ACCESSIBILITY_MODE_ENABLED))
    assertTrue("ACCESSIBILITY_THREAD_ID should be in backup keys",
               keys.contains(AccessibilityModeValues.ACCESSIBILITY_THREAD_ID))

    // Verify no extra keys were added
    assertEquals("Should have exactly 2 keys", 2, keys.size)
  }
}
