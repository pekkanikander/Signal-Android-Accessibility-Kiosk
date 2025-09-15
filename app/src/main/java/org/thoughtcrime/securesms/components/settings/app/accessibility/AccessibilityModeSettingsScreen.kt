package org.thoughtcrime.securesms.components.settings.app.accessibility

import android.view.LayoutInflater
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Divider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bumptech.glide.Glide
import java.util.Locale
import org.thoughtcrime.securesms.BindableConversationListItem
import org.thoughtcrime.securesms.conversationlist.model.ConversationSet
import org.thoughtcrime.securesms.database.model.ThreadRecord
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.Scaffolds
import org.thoughtcrime.securesms.R

@Composable
fun AccessibilityModeSettingsScreen(
  ui: AccessibilitySettingsUiState,
  record: ThreadRecord?,
  callbacks: AccessibilityModeSettingsCallbacks
) {
  Scaffolds.Settings(
    title = stringResource(R.string.preferences__accessibility_mode),
    onNavigationClick = callbacks::onNavigationClick,
    navigationIcon = ImageVector.vectorResource(R.drawable.symbol_arrow_start_24)
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
    ) {

      item {
        if (ui.selectedThreadId != null) {
          ListItem(
            headlineContent = {
              Text(
                stringResource(R.string.acc_mode_selected_chat_header),
                style = MaterialTheme.typography.bodyLarge
              )
            },
            supportingContent = {
              Text(
                stringResource(R.string.acc_mode_selected_chat_subtitle),
                style = MaterialTheme.typography.bodySmall
              )
            }
          )
        } else {
          ListItem(
            headlineContent = {
              Text(
                stringResource(R.string.acc_mode_choose_chat_header),
                style = MaterialTheme.typography.bodyLarge
              )
            },
            supportingContent = {
              Text(
                stringResource(R.string.acc_mode_choose_chat_subtitle),
                style = MaterialTheme.typography.bodySmall
              )
            }
          )
        }
      }

      item {
        // Conversation selection row first
        ConversationSelectionRow(
          items = ui.conversations,
          selectedId = ui.selectedThreadId,
          record = record,
          onClick = callbacks::onLaunchPicker
        )
      }

      item { Divider() }

      item {
        // Enable toggle
        ListItem(
          headlineContent = {
            Text(
              stringResource(R.string.acc_mode_enable),
              style = MaterialTheme.typography.bodyLarge
            )
          },
          trailingContent = {
            Switch(
              checked = ui.enabled && ui.canEnable,
              onCheckedChange = { callbacks.onToggleEnabled(it) },
              enabled = ui.canEnable
            )
          },
          modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = ui.canEnable) {
              callbacks.onToggleEnabled(!ui.enabled)
            }
            .padding(horizontal = 8.dp)
        )
      }

      item { Divider() }

      item {
        // Advanced link
        ListItem(
          headlineContent = {
            Text(
              stringResource(R.string.acc_mode_advanced),
              style = MaterialTheme.typography.bodyLarge
            )
          },
          supportingContent = {
            Text(
              stringResource(R.string.acc_mode_advanced_subtitle),
              style = MaterialTheme.typography.bodySmall
            )
          },
          modifier = Modifier.clickable { callbacks.onOpenAdvanced() }
        )
      }
    }
  }
}

@Composable
private fun ConversationSelectionRow(items: List<Long>, selectedId: Long?, record: ThreadRecord?, onClick: () -> Unit) {
  when {
    items.isEmpty() ->
      ListItem(
        headlineContent = { Text(stringResource(R.string.acc_mode_no_chats)) },
        modifier = Modifier.clickable(onClick = onClick)
      )

    selectedId == null ->
      ListItem(
        headlineContent = { Text(stringResource(R.string.acc_mode_select_chat)) },
        modifier = Modifier.clickable(onClick = onClick)
      )

    else -> SelectedConversationExactRow(
      record = record,
      onClick = onClick
    )
  }
}

@Composable
private fun SelectedConversationExactRow(record: ThreadRecord?, onClick: () -> Unit) {
  val lifecycleOwner = LocalLifecycleOwner.current

  if (record == null) {
    ListItem(
      headlineContent = { Text(stringResource(R.string.acc_mode_select_chat)) },
      modifier = Modifier.clickable(onClick = onClick)
    )
    return
  }

  AndroidView(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(horizontal = 8.dp),
    factory = { ctx ->
      LayoutInflater.from(ctx).inflate(
        R.layout.conversation_list_item_view,
        null,
        false
      ) as android.view.View
    },
    update = { view ->
      val item = view as BindableConversationListItem
      item.bind(
        lifecycleOwner,
        record,
        Glide.with(view),
        Locale.getDefault(),
        emptySet<Long>(),
        ConversationSet(),
        0L
      )
    }
  )
}
