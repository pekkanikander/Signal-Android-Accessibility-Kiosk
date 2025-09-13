package org.thoughtcrime.securesms.components.settings.app.accessibility

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.Scaffolds
import org.thoughtcrime.securesms.R

@Composable
fun AccessibilityModeAdvancedSettingsScreen(
  ui: AccessibilitySettingsUiState,
  callbacks: AccessibilityModeAdvancedSettingsCallbacks
) {
  Scaffolds.Settings(
    title = stringResource(R.string.acc_mode_advanced_title),
    onNavigationClick = callbacks::onNavigationClick,
    navigationIcon = ImageVector.vectorResource(R.drawable.symbol_arrow_start_24)
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .verticalScroll(rememberScrollState())
    ) {
      // Section header
      Text(
        text = stringResource(R.string.acc_mode_exit_gesture_header),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(16.dp)
      )

      RadioRow(
        title = stringResource(R.string.acc_mode_gesture_two_fingers_hold_prod),
        selected = ui.exitGestureTypeValue == 1,
        onClick = { callbacks.onChangeGesture(1) }
      )

      RadioRow(
        title = stringResource(R.string.acc_mode_gesture_triple_tap_debug),
        selected = ui.exitGestureTypeValue == 3,
        onClick = { callbacks.onChangeGesture(3) }
      )
    }
  }
}

@Composable
private fun RadioRow(title: String, selected: Boolean, onClick: () -> Unit) {
  ListItem(
    headlineContent = { Text(title) },
    trailingContent = { RadioButton(selected = selected, onClick = onClick) },
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(horizontal = 8.dp, vertical = 4.dp)
  )
}
