package org.thoughtcrime.securesms.components.settings.app.accessibility

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Divider
import androidx.compose.material3.ListItem
import androidx.compose.runtime.getValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AccessibilityModeSettingsScreen(
  viewModel: org.thoughtcrime.securesms.components.settings.app.accessibility.AccessibilityModeSettingsViewModel,
  navigateToAdvanced: () -> Unit,
  launchPicker: () -> Unit
) {
  val ui by viewModel.ui.collectAsState()

  Column(Modifier.verticalScroll(rememberScrollState())) {
    // Conversation selection row first
    ConversationSelectionRow(ui.conversations, ui.selectedThreadId, launchPicker)

    Divider()

    // Enable toggle
    ListItem(
      headlineContent = { Text("Enable Accessibility Mode", style = MaterialTheme.typography.bodyLarge) },
      trailingContent = { Switch(checked = ui.enabled && ui.canEnable, onCheckedChange = { viewModel.onToggleEnabled(it) }, enabled = ui.canEnable) },
      modifier = Modifier.fillMaxWidth().padding(8.dp)
    )

    Divider()

    // Advanced link
    ListItem(
      headlineContent = { Text("Advanced…", style = MaterialTheme.typography.bodyLarge) },
      supportingContent = { Text("Exit gesture and more", style = MaterialTheme.typography.bodySmall) },
      modifier = Modifier.clickable { navigateToAdvanced() }
    )
  }
}

@Composable
private fun ConversationSelectionRow(items: List<Long>, selectedId: Long?, onClick: () -> Unit) {
  when {
    items.isEmpty() -> ListItem(headlineContent = { Text("No chats available yet. Start a conversation first!") }, modifier = Modifier.clickable(onClick = onClick))
    selectedId == null -> ListItem(headlineContent = { Text("Select a chat for Accessibility Mode") }, modifier = Modifier.clickable(onClick = onClick))
    else -> ListItem(headlineContent = { Text("Chat selected: #${selectedId}") }, modifier = Modifier.clickable(onClick = onClick))
  }
}
