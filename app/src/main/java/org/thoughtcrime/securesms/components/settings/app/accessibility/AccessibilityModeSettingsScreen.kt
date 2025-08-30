package org.thoughtcrime.securesms.components.settings.app.accessibility

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalPreview
import org.signal.core.ui.compose.theme.SignalTheme
import org.thoughtcrime.securesms.R

@Composable
@VisibleForTesting
fun AccessibilityModeSettingsScreen(
  state: AccessibilityModeSettingsState,
  callbacks: AccessibilityModeSettingsCallbacks
) {
  Scaffolds.Settings(
    title = stringResource(R.string.preferences__accessibility_mode),
    onNavigationClick = { callbacks.onNavigationClick() },
    navigationIcon = ImageVector.vectorResource(R.drawable.ic_arrow_left_24)
  ) { contentPadding ->
    LazyColumn(
      modifier = Modifier
        .padding(contentPadding)
        .testTag(AccessibilityModeSettingsTestTags.SCROLLER)
    ) {
      item {
        // Thread Selection Row
        Rows.TextRow(
          text = when {
            org.thoughtcrime.securesms.database.SignalDatabase.threads
              .getUnarchivedConversationListCount(org.thoughtcrime.securesms.conversationlist.model.ConversationFilter.OFF) == 0 ->
              stringResource(R.string.preferences__accessibility_mode_no_chats_available)
            state.threadId == -1L -> stringResource(R.string.preferences__accessibility_mode_no_chat_selected)
            else -> stringResource(R.string.preferences__accessibility_mode_chat_selected, state.threadId.toString())
          },
          icon = ImageVector.vectorResource(R.drawable.symbol_chat_24),
          onClick = { callbacks.onThreadSelectionClick() },
          modifier = Modifier.testTag(AccessibilityModeSettingsTestTags.ROW_THREAD_SELECTION)
        )
      }

      item {
        Dividers.Default()
      }

      item {
        // Accessibility Mode Toggle - only enabled when a chat is selected
        Rows.ToggleRow(
          text = stringResource(R.string.preferences__accessibility_mode_enabled),
          checked = state.isAccessibilityModeEnabled,
          onCheckChanged = { enabled -> callbacks.onAccessibilityModeToggled(enabled) },
          enabled = state.threadId != -1L,
          modifier = Modifier.testTag(AccessibilityModeSettingsTestTags.TOGGLE_ACCESSIBILITY_MODE)
        )
      }

      item {
        Dividers.Default()
      }

      item {
        // Description text
        Text(
          text = stringResource(R.string.preferences__accessibility_mode_description),
          style = MaterialTheme.typography.bodyMedium,
          modifier = Modifier.padding(16.dp)
        )
      }
    }
  }
}

@SignalPreview
@Composable
private fun AccessibilityModeSettingsScreenPreview() {
  SignalTheme(isDarkMode = false) {
    AccessibilityModeSettingsScreen(
      state = AccessibilityModeSettingsState(
        isAccessibilityModeEnabled = true,
        threadId = 123L
      ),
      callbacks = AccessibilityModeSettingsCallbacks.Empty
    )
  }
}
