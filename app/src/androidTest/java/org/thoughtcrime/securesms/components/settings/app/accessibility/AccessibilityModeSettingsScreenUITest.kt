package org.thoughtcrime.securesms.components.settings.app.accessibility

import android.content.Context
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.mockk
import io.mockk.verify
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@Ignore("Compose test setup needs to be fixed")
class AccessibilityModeSettingsScreenUITest {

  @get:Rule
  val composeTestRule = createEmptyComposeRule()

  private val context: Context = ApplicationProvider.getApplicationContext()

  @Test
  fun testAccessibilityModeToggleDisplaysAndUpdatesState() {
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
  fun testThreadSelectionDisplaysCurrentSelection() {
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
  fun testThreadSelectionRowClickOpensThreadPicker() {
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
  fun testScreenDisplaysAllRequiredElements() {
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
