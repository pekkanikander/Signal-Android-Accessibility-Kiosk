package org.thoughtcrime.securesms.components.settings.app.accessibility

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AccessibilityModeAdvancedSettingsScreen(
  viewModel: org.thoughtcrime.securesms.components.settings.app.accessibility.AccessibilityModeSettingsViewModel,
  onBack: () -> Unit
) {
  val ui by viewModel.ui.collectAsState()

  Column(Modifier.verticalScroll(rememberScrollState())) {
    Text("Exit Gesture", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))

    RadioRow(
      title = "Two Fingers Hold (Production)",
      selected = ui.exitGestureTypeValue == 1,
      onClick = { viewModel.onChangeGesture(1) }
    )

    RadioRow(
      title = "Triple Tap (Debug)",
      selected = ui.exitGestureTypeValue == 3,
      onClick = { viewModel.onChangeGesture(3) }
    )
  }
}

@Composable private fun RadioRow(title: String, selected: Boolean, onClick: () -> Unit) {
  ListItem(
    headlineContent = { Text(title) },
    trailingContent = { RadioButton(selected = selected, onClick = onClick) },
    modifier = Modifier.clickable(onClick = onClick).fillMaxWidth().padding(8.dp)
  )
}
