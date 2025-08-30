package org.thoughtcrime.securesms.components.settings.app.accessibility

import android.app.Application
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.keyvalue.AccessibilityModeValues
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class AccessibilityModeSettingsViewModelTest {

  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  private val testDispatcher = StandardTestDispatcher()
  private val testScope = TestScope(testDispatcher)
  private lateinit var viewModel: AccessibilityModeSettingsViewModel
  private lateinit var mockAccessibilityModeValues: AccessibilityModeValues

  @Before
  fun setUp() {
    mockAccessibilityModeValues = mockk<AccessibilityModeValues>()

    // Mock SignalStore.accessibilityMode to return our mock
    mockkObject(SignalStore)
    every { SignalStore.accessibilityMode } returns mockAccessibilityModeValues



    // Set up default mocks for the ViewModel constructor
    every { mockAccessibilityModeValues.isAccessibilityModeEnabled } returns false
    every { mockAccessibilityModeValues.accessibilityThreadId } returns -1L

    // Mock setter methods
    every { mockAccessibilityModeValues.isAccessibilityModeEnabled = any() } answers { }
    every { mockAccessibilityModeValues.accessibilityThreadId = any() } answers { }

    viewModel = AccessibilityModeSettingsViewModel()
  }

  @After
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun `test ViewModel creation and initial state`() {
    // Given - ViewModel is created in setUp()

    // Then
    assertNotNull(viewModel)
    assertNotNull(viewModel.state)
    assertEquals(false, viewModel.state.value.isAccessibilityModeEnabled)
    assertEquals(-1L, viewModel.state.value.threadId)
  }

  @Test
  fun `test setAccessibilityMode calls SignalStore setter`() = runTest {
    // Given
    val newValue = true

    // When
    viewModel.setAccessibilityMode(newValue)

    // Then
    verify { mockAccessibilityModeValues.isAccessibilityModeEnabled = newValue }
  }

  @Test
  fun `test setThreadId calls SignalStore setter`() = runTest {
    // Given
    val newValue = 456L

    // When
    viewModel.setThreadId(newValue)

    // Then
    verify { mockAccessibilityModeValues.accessibilityThreadId = newValue }
  }

  @Test
  fun `test state reflects no chats available`() {
    // Given - Default mocks are set in setUp() (no chats available)

    // When - ViewModel is created

    // Then
    val state = viewModel.state.value
    assertFalse(state.isAccessibilityModeEnabled)
    assertEquals(-1L, state.threadId)
  }

  @Test
  fun `test state reflects chats available but none selected`() = runTest {
    // Given
    every { mockAccessibilityModeValues.isAccessibilityModeEnabled } returns false
    every { mockAccessibilityModeValues.accessibilityThreadId } returns -1L

    // When
    viewModel.refreshState()
    advanceUntilIdle()

    // Then
    val state = viewModel.state.value
    assertFalse(state.isAccessibilityModeEnabled)
    assertEquals(-1L, state.threadId)
  }

  @Test
  fun `test state reflects chat selected`() = runTest {
    // Given
    every { mockAccessibilityModeValues.isAccessibilityModeEnabled } returns true
    every { mockAccessibilityModeValues.accessibilityThreadId } returns 123L

    // When
    viewModel.refreshState()
    advanceUntilIdle()

    // Then
    val state = viewModel.state.value
    assertTrue(state.isAccessibilityModeEnabled)
    assertEquals(123L, state.threadId)
  }

  @Test
  fun `test refreshState calls SignalStore getters`() = runTest {
    // Given
    every { mockAccessibilityModeValues.isAccessibilityModeEnabled } returns true
    every { mockAccessibilityModeValues.accessibilityThreadId } returns 789L

    // When
    viewModel.refreshState()
    advanceUntilIdle()

    // Then
    // The state should be updated with the new values
    val state = viewModel.state.value
    assertTrue(state.isAccessibilityModeEnabled)
    assertEquals(789L, state.threadId)
  }

  @Test
  fun `test default state values match SignalStore defaults`() {
    // Given - Default mocks are set in setUp()

    // When - ViewModel is created

    // Then
    val state = viewModel.state.value
    assertFalse(state.isAccessibilityModeEnabled)
    assertEquals(-1L, state.threadId)
  }
}
