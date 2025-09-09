package org.thoughtcrime.securesms.components.settings.app.accessibility

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalPreview
import org.signal.core.ui.compose.theme.SignalTheme
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.avatar.AvatarImage
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.AvatarUtil

@Composable
@VisibleForTesting
fun ChatSelectionScreen(
  chats: List<ChatSelectionItem>,
  onChatSelected: (ChatSelectionItem) -> Unit,
  onNavigationClick: () -> Unit
) {
  Scaffolds.Settings(
    title = stringResource(R.string.preferences__accessibility_mode_select_chat),
    onNavigationClick = onNavigationClick,
    navigationIcon = ImageVector.vectorResource(R.drawable.ic_arrow_left_24)
  ) { contentPadding ->
    LazyColumn(
      modifier = Modifier
        .padding(contentPadding)
        .testTag(ChatSelectionTestTags.SCROLLER)
    ) {
      if (chats.isEmpty()) {
        item {
          Text(
            text = stringResource(R.string.preferences__accessibility_mode_no_chats_available),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp)
          )
        }
      } else {
                items(chats) { chat ->
          ChatRow(
            chat = chat,
            onClick = { onChatSelected(chat) },
            modifier = Modifier.testTag(ChatSelectionTestTags.CHAT_ROW)
          )
        }

        // Add dividers between items
        if (chats.size > 1) {
          items(chats.size - 1) {
            Dividers.Default()
          }
        }
      }
    }
  }
}

@Composable
private fun ChatRow(
  chat: ChatSelectionItem,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
      .clickable(onClick = onClick)
      .fillMaxWidth()
      .padding(horizontal = 24.dp, vertical = 16.dp)
  ) {
    if (LocalInspectionMode.current) {
      // Preview mode - show placeholder icon
      Image(
        imageVector = ImageVector.vectorResource(R.drawable.symbol_person_24),
        contentDescription = null,
        modifier = Modifier
          .size(40.dp)
          .background(
            color = Color.Red,
            shape = CircleShape
          )
      )
    } else {
      // Real mode - show avatar
      AvatarImage(
        recipient = chat.recipient,
        modifier = Modifier.size(40.dp),
        useProfile = false
      )
    }

    Spacer(modifier = Modifier.width(16.dp))

    Column(
      modifier = Modifier.weight(1f)
    ) {
      Text(
        text = if (chat.recipient.isSelf) {
          stringResource(R.string.note_to_self)
        } else {
          chat.recipient.getShortDisplayName(LocalContext.current)
        },
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface
      )

      if (chat.lastMessagePreview.isNotEmpty()) {
        Text(
          text = chat.lastMessagePreview,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1
        )
      }
    }
  }
}

data class ChatSelectionItem(
  val threadId: Long,
  val recipient: Recipient,
  val lastMessagePreview: String = ""
)

@SignalPreview
@Composable
private fun ChatSelectionScreenPreview() {
  SignalTheme(isDarkMode = false) {
    val previewChats = listOf(
      ChatSelectionItem(
        threadId = 1L,
        recipient = Recipient.UNKNOWN,
        lastMessagePreview = "Hello, how are you?"
      ),
      ChatSelectionItem(
        threadId = 2L,
        recipient = Recipient.UNKNOWN,
        lastMessagePreview = "Meeting at 3 PM"
      )
    )

    ChatSelectionScreen(
      chats = previewChats,
      onChatSelected = {},
      onNavigationClick = {}
    )
  }
}
