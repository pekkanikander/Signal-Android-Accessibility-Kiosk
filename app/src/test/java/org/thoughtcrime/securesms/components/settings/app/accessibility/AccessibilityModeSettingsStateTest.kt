package org.thoughtcrime.securesms.components.settings.app.accessibility

import org.junit.Test
import org.junit.Assert.*

class AccessibilityModeSettingsStateTest {

  @Test
  fun `test state creation with all properties`() {
    val state = AccessibilityModeSettingsState(
      isAccessibilityModeEnabled = true,
      threadId = 123L
    )

    assertTrue(state.isAccessibilityModeEnabled)
    assertEquals(123L, state.threadId)
  }

  @Test
  fun `test state creation with default values`() {
    val state = AccessibilityModeSettingsState(
      isAccessibilityModeEnabled = false,
      threadId = -1L
    )

    assertFalse(state.isAccessibilityModeEnabled)
    assertEquals(-1L, state.threadId)
  }

  @Test
  fun `test state creation with no chats yet`() {
    val state = AccessibilityModeSettingsState(
      isAccessibilityModeEnabled = true,
      threadId = -1L
    )

    assertTrue(state.isAccessibilityModeEnabled)
    assertEquals(-1L, state.threadId)
  }

  @Test
  fun `test state equality`() {
    val state1 = AccessibilityModeSettingsState(
      isAccessibilityModeEnabled = true,
      threadId = 123L
    )

    val state2 = AccessibilityModeSettingsState(
      isAccessibilityModeEnabled = true,
      threadId = 123L
    )

    assertEquals(state1, state2)
  }

  @Test
  fun `test state inequality`() {
    val state1 = AccessibilityModeSettingsState(
      isAccessibilityModeEnabled = true,
      threadId = 123L
    )

    val state2 = AccessibilityModeSettingsState(
      isAccessibilityModeEnabled = false,
      threadId = 123L
    )

    assertNotEquals(state1, state2)
  }

  @Test
  fun `test state copy with modifications`() {
    val originalState = AccessibilityModeSettingsState(
      isAccessibilityModeEnabled = false,
      threadId = -1L
    )

    val modifiedState = originalState.copy(
      isAccessibilityModeEnabled = true,
      threadId = 456L
    )

    assertTrue(modifiedState.isAccessibilityModeEnabled)
    assertEquals(456L, modifiedState.threadId)

    // Original state should remain unchanged
    assertFalse(originalState.isAccessibilityModeEnabled)
    assertEquals(-1L, originalState.threadId)
  }

  @Test
  fun `test state hashCode consistency`() {
    val state1 = AccessibilityModeSettingsState(
      isAccessibilityModeEnabled = true,
      threadId = 123L
    )

    val state2 = AccessibilityModeSettingsState(
      isAccessibilityModeEnabled = true,
      threadId = 123L
    )

    assertEquals(state1.hashCode(), state2.hashCode())
  }
}
