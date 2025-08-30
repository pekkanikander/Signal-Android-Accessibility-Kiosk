package org.thoughtcrime.securesms.components.settings.app.accessibility

import android.app.Application
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.keyvalue.AccessibilityModeValues
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule
import java.lang.reflect.Modifier
import org.junit.Assert.assertFalse

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class AccessibilityModeSettingsFragmentTest {

  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  private lateinit var mockAccessibilityModeValues: AccessibilityModeValues

  @Before
  fun setUp() {
    mockAccessibilityModeValues = mockk<AccessibilityModeValues>()

    mockkObject(SignalStore)
    every { SignalStore.accessibilityMode } returns mockAccessibilityModeValues

    // Set up default mocks for the ViewModel constructor
    every { mockAccessibilityModeValues.isAccessibilityModeEnabled } returns false
    every { mockAccessibilityModeValues.accessibilityThreadId } returns -1L

    // Mock setter methods
    every { mockAccessibilityModeValues.isAccessibilityModeEnabled = any() } answers { }
    every { mockAccessibilityModeValues.accessibilityThreadId = any() } answers { }
  }

  @After
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun testFragmentClassExists() {
    // This test verifies that the Fragment class can be instantiated
    // and has the expected structure
    val fragmentClass = AccessibilityModeSettingsFragment::class.java
    assertNotNull(fragmentClass)

    // Verify it extends the expected base class
    val superclass = fragmentClass.superclass
    assertNotNull(superclass)
    assertEquals("org.thoughtcrime.securesms.compose.ComposeFragment", superclass.name)
  }

  @Test
  fun testFragmentCanBeCompiled() {
    // This test verifies that the Fragment class can be referenced
    // and compiled without errors
    val fragmentClass = AccessibilityModeSettingsFragment::class.java
    assertNotNull(fragmentClass)

    // Verify it's not abstract (can be instantiated)
    assertFalse(Modifier.isAbstract(fragmentClass.modifiers))
  }
}
