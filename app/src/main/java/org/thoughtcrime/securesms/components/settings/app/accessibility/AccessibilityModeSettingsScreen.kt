package org.thoughtcrime.securesms.components.settings.app.accessibility

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Divider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.thoughtcrime.securesms.components.settings.app.accessibility.AccessibilityModeSettingsViewModel
import org.thoughtcrime.securesms.R

@Composable
fun AccessibilityModeSettingsScreen(
  viewModel: AccessibilityModeSettingsViewModel,
  navigateToAdvanced: () -> Unit,
  launchPicker: () -> Unit
) {
  val ui by viewModel.ui.collectAsState()

  Column(
    Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
  ) {
    // Conversation selection row first
    ConversationSelectionRow(ui.conversations, ui.selectedThreadId, launchPicker)

    Divider()

    // Enable toggle
    ListItem(
      headlineContent = { Text(stringResource(R.string.acc_mode_enable), style = MaterialTheme.typography.bodyLarge) },
      trailingContent = {
        Switch(
          checked = ui.enabled && ui.canEnable,
          onCheckedChange = { viewModel.onToggleEnabled(it) },
          enabled = ui.canEnable
        )
      },
      modifier = Modifier
        .fillMaxWidth()
        .clickable(enabled = ui.canEnable) {
          viewModel.onToggleEnabled(!ui.enabled)
        }
        .padding(horizontal = 8.dp)
    )

    Divider()

    // Advanced link
    ListItem(
      headlineContent = { Text(stringResource(R.string.acc_mode_advanced), style = MaterialTheme.typography.bodyLarge) },
      supportingContent = { Text(stringResource(R.string.acc_mode_advanced_subtitle), style = MaterialTheme.typography.bodySmall) },
      modifier = Modifier.clickable { navigateToAdvanced() }
    )
  }
}

@Composable
private fun ConversationSelectionRow(items: List<Long>, selectedId: Long?, onClick: () -> Unit) {
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

    else ->
      ListItem(
        headlineContent = { Text(stringResource(R.string.acc_mode_chat_selected_id, selectedId)) },
        modifier = Modifier.clickable(onClick = onClick)
      )
  }
}
