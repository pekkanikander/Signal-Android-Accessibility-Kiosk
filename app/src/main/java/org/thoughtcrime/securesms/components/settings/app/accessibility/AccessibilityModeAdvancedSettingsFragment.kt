package org.thoughtcrime.securesms.components.settings.app.accessibility

import android.os.Bundle
import android.view.View
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.accessibility.AccessibilityModeExitGestureType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

class AccessibilityModeAdvancedSettingsFragment : ComposeFragment() {

  private val viewModel: AccessibilityModeSettingsViewModel by viewModels()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    // No fragment results here.
  }

  @Composable
  override fun FragmentContent() {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val callbacks = Callbacks()

    AccessibilityModeAdvancedSettingsScreen(
      ui = ui,
      callbacks = callbacks
    )
  }

@Composable
private fun AccessibilityModeAdvancedSettingsScreen(
  ui: AccessibilitySettingsUiState,
  callbacks: AccessibilityModeAdvancedSettingsCallbacks
) {
  org.signal.core.ui.compose.Scaffolds.Settings(
    title = androidx.compose.ui.res.stringResource(org.thoughtcrime.securesms.R.string.AccessibilityModeAdvancedSettingsFragment__title),
    onNavigationClick = callbacks::onNavigationClick,
    navigationIcon = androidx.compose.ui.graphics.vector.ImageVector.vectorResource(org.thoughtcrime.securesms.R.drawable.symbol_arrow_start_24)
  ) { paddingValues ->
    androidx.compose.foundation.layout.Column(
      modifier = androidx.compose.ui.Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .verticalScroll(androidx.compose.foundation.rememberScrollState())
    ) {
      // Section header
      androidx.compose.material3.Text(
        text = androidx.compose.ui.res.stringResource(org.thoughtcrime.securesms.R.string.AccessibilityModeAdvancedSettingsFragment__exit_gesture_header),
        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
        modifier = androidx.compose.ui.Modifier.padding(16.dp)
      )

      RadioRow(
        title = androidx.compose.ui.res.stringResource(org.thoughtcrime.securesms.R.string.AccessibilityModeAdvancedSettingsFragment__gesture_two_fingers_hold),
        selected = ui.exitGestureTypeValue == AccessibilityModeExitGestureType.ChordSlideUp.value,
        onClick = { callbacks.onChangeGesture(AccessibilityModeExitGestureType.ChordSlideUp.value) }
      )

      RadioRow(
        title = androidx.compose.ui.res.stringResource(org.thoughtcrime.securesms.R.string.AccessibilityModeAdvancedSettingsFragment__gesture_triple_tap),
        selected = ui.exitGestureTypeValue == AccessibilityModeExitGestureType.TripleTap.value,
        onClick = { callbacks.onChangeGesture(AccessibilityModeExitGestureType.TripleTap.value) }
      )

      // Numeric advanced options
      androidx.compose.material3.Text(
        text = androidx.compose.ui.res.stringResource(org.thoughtcrime.securesms.R.string.AccessibilityModeAdvancedSettingsFragment__advanced_tuning_header),
        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
        modifier = androidx.compose.ui.Modifier.padding(16.dp)
      )

      NumberRow(
        title = stringResource(org.thoughtcrime.securesms.R.string.AccessibilityModeAdvancedSettingsFragment__exit_timeout_ms),
        value = ui.exitGestureTimeoutMs,
        onSet = { callbacks.onSetExitGestureTimeoutMs(it) }
      )

      NumberRow(
        title = stringResource(org.thoughtcrime.securesms.R.string.AccessibilityModeAdvancedSettingsFragment__exit_pointer_timeout_ms),
        value = ui.exitGesturePointerTimeoutMs,
        onSet = { callbacks.onSetExitGesturePointerTimeoutMs(it) }
      )

      NumberRow(
        title = stringResource(org.thoughtcrime.securesms.R.string.AccessibilityModeAdvancedSettingsFragment__exit_header_height_dp),
        value = ui.exitHeaderHeightDp,
        onSet = { callbacks.onSetExitHeaderHeightDp(it) }
      )

      NumberRow(
        title = stringResource(org.thoughtcrime.securesms.R.string.AccessibilityModeAdvancedSettingsFragment__triple_tap_interval_ms),
        value = ui.exitTripleTapIntervalMs,
        onSet = { callbacks.onSetExitTripleTapIntervalMs(it) }
      )

      NumberRow(
        title = stringResource(org.thoughtcrime.securesms.R.string.AccessibilityModeAdvancedSettingsFragment__confirm_timeout_ms),
        value = ui.exitConfirmTimeoutMs,
        onSet = { callbacks.onSetExitConfirmTimeoutMs(it) }
      )
    }
  }
}

@Composable
private fun RadioRow(title: String, selected: Boolean, onClick: () -> Unit) {
  androidx.compose.material3.ListItem(
    headlineContent = { androidx.compose.material3.Text(title) },
    trailingContent = { androidx.compose.material3.RadioButton(selected = selected, onClick = onClick) },
    modifier = androidx.compose.ui.Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(horizontal = 8.dp, vertical = 4.dp)
  )
}

@Composable
private fun NumberRow(title: String, value: Int, onSet: (Int) -> Unit) {
  var editing by remember { mutableStateOf(false) }
  var text by remember { mutableStateOf(value.toString()) }

  if (editing) {
    androidx.compose.material3.AlertDialog(
      onDismissRequest = { editing = false },
      title = { Text(title) },
      text = {
        TextField(
          value = text,
          onValueChange = { input ->
            text = input.filter { it.isDigit() }
          },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
      },
      confirmButton = {
        TextButton(
          enabled = text.toIntOrNull() != null,
          onClick = {
            text.toIntOrNull()?.let { onSet(it) }
            editing = false
          }
        ) { Text(stringResource(id = android.R.string.ok)) }
      },
      dismissButton = {
        TextButton(onClick = { editing = false }) { Text(stringResource(id = android.R.string.cancel)) }
      }
    )
  }

  androidx.compose.material3.ListItem(
    headlineContent = { Text(title) },
    supportingContent = { Text(value.toString()) },
    modifier = Modifier
      .fillMaxWidth()
      .clickable { editing = true }
      .padding(horizontal = 8.dp, vertical = 4.dp)
  )
}

  private inner class Callbacks : AccessibilityModeAdvancedSettingsCallbacks {
    override fun onNavigationClick() {
      requireActivity().onBackPressedDispatcher.onBackPressed()
    }

    override fun onChangeGesture(typeValue: Int) {
      viewModel.onChangeGesture(typeValue)
    }

    override fun onSetExitGestureTimeoutMs(value: Int) { viewModel.onSetExitGestureTimeoutMs(value) }
    override fun onSetExitGesturePointerTimeoutMs(value: Int) { viewModel.onSetExitGesturePointerTimeoutMs(value) }
    override fun onSetExitHeaderHeightDp(value: Int) { viewModel.onSetExitHeaderHeightDp(value) }
    override fun onSetExitTripleTapIntervalMs(value: Int) { viewModel.onSetExitTripleTapIntervalMs(value) }
    override fun onSetExitConfirmTimeoutMs(value: Int) { viewModel.onSetExitConfirmTimeoutMs(value) }
  }
}

interface AccessibilityModeAdvancedSettingsCallbacks {
  fun onNavigationClick() = Unit
  fun onChangeGesture(typeValue: Int) = Unit
  fun onSetExitGestureTimeoutMs(value: Int) = Unit
  fun onSetExitGesturePointerTimeoutMs(value: Int) = Unit
  fun onSetExitHeaderHeightDp(value: Int) = Unit
  fun onSetExitTripleTapIntervalMs(value: Int) = Unit
  fun onSetExitConfirmTimeoutMs(value: Int) = Unit

  object Empty : AccessibilityModeAdvancedSettingsCallbacks
}
