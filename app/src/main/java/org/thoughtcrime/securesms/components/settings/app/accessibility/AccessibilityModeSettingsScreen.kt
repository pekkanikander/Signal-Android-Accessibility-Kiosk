package org.thoughtcrime.securesms.components.settings.app.accessibility

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import org.thoughtcrime.securesms.accessibility.AccessibilityModeExitGestureType
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalPreview
import org.signal.core.ui.compose.theme.SignalTheme
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.AvatarUtil

@Composable
@VisibleForTesting
fun AccessibilityModeSettingsScreen(
  state: AccessibilityModeSettingsState,
  callbacks: AccessibilityModeSettingsCallbacks
) {
  val context = LocalContext.current

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
        // Thread Selection Row - show ChatRow when chat is selected
        when {
          SignalDatabase.threads.getUnarchivedConversationListCount(org.thoughtcrime.securesms.conversationlist.model.ConversationFilter.OFF) == 0 -> {
            // No chats available
            Rows.TextRow(
              text = stringResource(R.string.preferences__accessibility_mode_no_chats_available),
              icon = ImageVector.vectorResource(R.drawable.symbol_chat_24),
              onClick = { callbacks.onThreadSelectionClick() },
              modifier = Modifier.testTag(AccessibilityModeSettingsTestTags.ROW_THREAD_SELECTION)
            )
          }
          state.threadId == -1L -> {
            // No chat selected
            Rows.TextRow(
              text = stringResource(R.string.preferences__accessibility_mode_no_chat_selected),
              icon = ImageVector.vectorResource(R.drawable.symbol_chat_24),
              onClick = { callbacks.onThreadSelectionClick() },
              modifier = Modifier.testTag(AccessibilityModeSettingsTestTags.ROW_THREAD_SELECTION)
            )
          }
          else -> {
            // Chat is selected - show ChatRow
            val recipient = getRecipientForThread(state.threadId)
            val lastMessage = getLastMessageForThread(state.threadId)

            ChatRow(
              recipient = recipient,
              lastMessage = lastMessage,
              onClick = { callbacks.onThreadSelectionClick() },
              modifier = Modifier.testTag(AccessibilityModeSettingsTestTags.ROW_THREAD_SELECTION)
            )
          }
        }
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
        // Start Accessibility Mode button - only enabled when accessibility mode is enabled and chat is selected
        Rows.TextRow(
          text = stringResource(R.string.preferences__accessibility_mode_start),
          icon = ImageVector.vectorResource(R.drawable.symbol_arrow_right_24),
          onClick = { callbacks.onStartAccessibilityModeClick() },
          enabled = state.isAccessibilityModeEnabled && state.threadId != -1L,
          modifier = Modifier.testTag(AccessibilityModeSettingsTestTags.BUTTON_START_ACCESSIBILITY_MODE)
        )
      }

      item {
        Dividers.Default()
      }

      item {
        // Exit Gesture Type Selection
        Rows.TextRow(
          text = stringResource(R.string.preferences__accessibility_mode_exit_gesture_type),
          label = state.exitGestureType.displayName,
          icon = ImageVector.vectorResource(R.drawable.symbol_settings_android_24),
          onClick = { callbacks.onExitGestureTypeClick() },
          modifier = Modifier.testTag(AccessibilityModeSettingsTestTags.ROW_EXIT_GESTURE_TYPE)
        )
      }

      item {
        Dividers.Default()
      }

      item {
        // Exit Gesture PIN Requirement Toggle
        Rows.ToggleRow(
          text = stringResource(R.string.preferences__accessibility_mode_exit_gesture_require_pin),
          checked = state.exitGestureRequirePin,
          onCheckChanged = { requirePin -> callbacks.onExitGestureRequirePinToggled(requirePin) },
          modifier = Modifier.testTag(AccessibilityModeSettingsTestTags.TOGGLE_EXIT_GESTURE_REQUIRE_PIN)
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
        threadId = 123L,
        exitGestureType = AccessibilityModeExitGestureType.OPPOSITE_CORNERS_HOLD,
        exitGestureRequirePin = false
      ),
      callbacks = AccessibilityModeSettingsCallbacks.Empty
    )
  }
}

/**
 * Helper function to get recipient for a thread ID
 */
private fun getRecipientForThread(threadId: Long): Recipient {
  return try {
    val threadRecord = SignalDatabase.threads.getThreadRecord(threadId)
    if (threadRecord != null) {
      Recipient.resolved(threadRecord.recipient.id)
    } else {
      Recipient.UNKNOWN
    }
  } catch (e: Exception) {
    Recipient.UNKNOWN
  }
}

/**
 * Helper function to get last message for a thread ID
 */
private fun getLastMessageForThread(threadId: Long): String {
  return try {
    val cursor = SignalDatabase.messages.getConversation(threadId, 0L, 1L)
    cursor.use {
      if (cursor.moveToFirst()) {
        // For now, return empty string as we need to find the right way to read message body
        // This is a placeholder - we'll implement proper message reading later
        ""
      } else {
        ""
      }
    }
  } catch (e: Exception) {
    ""
  }
}

/**
 * ChatRow component to display selected chat information
 */
@Composable
private fun ChatRow(
  recipient: Recipient,
  lastMessage: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current

  Rows.TextRow(
    text = if (recipient.isSelf) {
      stringResource(R.string.note_to_self)
    } else {
      recipient.getShortDisplayName(context)
    },
    icon = ImageVector.vectorResource(R.drawable.symbol_chat_24),
    onClick = onClick,
    modifier = modifier
  )
}
