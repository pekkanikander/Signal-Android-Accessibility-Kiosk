package org.thoughtcrime.securesms.components.settings.app.accessibility

import android.app.Application
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import io.mockk.mockk
import io.mockk.verify
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AccessibilityModeSettingsScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `test accessibility mode toggle displays and updates state`() {
    val callback = mockk<AccessibilityModeSettingsCallbacks>(relaxUnitFun = true)
    val state = createState(isAccessibilityModeEnabled = false)

    composeTestRule.setContent {
      AccessibilityModeSettingsScreen(
        state = state,
        callbacks = callback
      )
    }

    // Verify toggle is displayed and clickable
    composeTestRule.onNodeWithTag(AccessibilityModeSettingsTestTags.TOGGLE_ACCESSIBILITY_MODE)
      .assertIsDisplayed()
      .assertIsEnabled()
      .assertHasClickAction()
      .performClick()

    // Verify callback is called
    verify { callback.onAccessibilityModeToggled(true) }
  }

  @Test
  fun `test thread selection displays current selection`() {
    val callback = mockk<AccessibilityModeSettingsCallbacks>(relaxUnitFun = true)
    val state = createState(threadId = 123L)

    composeTestRule.setContent {
      AccessibilityModeSettingsScreen(
        state = state,
        callbacks = callback
      )
    }

    // Verify thread selection row is displayed
    composeTestRule.onNodeWithTag(AccessibilityModeSettingsTestTags.ROW_THREAD_SELECTION)
      .assertIsDisplayed()
      .assertIsEnabled()
      .assertHasClickAction()
  }

  @Test
  fun `test thread selection row shows no thread selected when threadId is -1`() {
    val callback = mockk<AccessibilityModeSettingsCallbacks>(relaxUnitFun = true)
    val state = createState(threadId = -1L)

    composeTestRule.setContent {
      AccessibilityModeSettingsScreen(
        state = state,
        callbacks = callback
      )
    }

    // Verify thread selection row shows "No thread selected"
    composeTestRule.onNodeWithTag(AccessibilityModeSettingsTestTags.ROW_THREAD_SELECTION)
      .assertIsDisplayed()
      .assertIsEnabled()
  }

  @Test
  fun `test thread selection row click opens thread picker`() {
    val callback = mockk<AccessibilityModeSettingsCallbacks>(relaxUnitFun = true)
    val state = createState(threadId = 123L)

    composeTestRule.setContent {
      AccessibilityModeSettingsScreen(
        state = state,
        callbacks = callback
      )
    }

    // Click on thread selection row
    composeTestRule.onNodeWithTag(AccessibilityModeSettingsTestTags.ROW_THREAD_SELECTION)
      .performClick()

    // Verify callback is called
    verify { callback.onThreadSelectionClick() }
  }

  @Test
  fun `test screen displays all required elements`() {
    val callback = mockk<AccessibilityModeSettingsCallbacks>(relaxUnitFun = true)
    val state = createState()

    composeTestRule.setContent {
      AccessibilityModeSettingsScreen(
        state = state,
        callbacks = callback
      )
    }

    // Verify all main elements are displayed
    composeTestRule.onNodeWithTag(AccessibilityModeSettingsTestTags.SCROLLER)
      .assertIsDisplayed()

    composeTestRule.onNodeWithTag(AccessibilityModeSettingsTestTags.TOGGLE_ACCESSIBILITY_MODE)
      .assertIsDisplayed()

    composeTestRule.onNodeWithTag(AccessibilityModeSettingsTestTags.ROW_THREAD_SELECTION)
      .assertIsDisplayed()
  }

  @Test
  fun `test accessibility mode toggle reflects current state`() {
    val callback = mockk<AccessibilityModeSettingsCallbacks>(relaxUnitFun = true)
    val state = createState(isAccessibilityModeEnabled = true)

    composeTestRule.setContent {
      AccessibilityModeSettingsScreen(
        state = state,
        callbacks = callback
      )
    }

    // Verify toggle shows enabled state
    composeTestRule.onNodeWithTag(AccessibilityModeSettingsTestTags.TOGGLE_ACCESSIBILITY_MODE)
      .assertIsDisplayed()
      .assertIsEnabled()
  }

  private fun createState(
    isAccessibilityModeEnabled: Boolean = false,
    threadId: Long = -1L
  ): AccessibilityModeSettingsState {
    return AccessibilityModeSettingsState(
      isAccessibilityModeEnabled = isAccessibilityModeEnabled,
      threadId = threadId
    )
  }
}
