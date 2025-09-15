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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.thoughtcrime.securesms.compose.ComposeFragment

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
    title = androidx.compose.ui.res.stringResource(org.thoughtcrime.securesms.R.string.acc_mode_advanced_title),
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
        text = androidx.compose.ui.res.stringResource(org.thoughtcrime.securesms.R.string.acc_mode_exit_gesture_header),
        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
        modifier = androidx.compose.ui.Modifier.padding(16.dp)
      )

      RadioRow(
        title = androidx.compose.ui.res.stringResource(org.thoughtcrime.securesms.R.string.acc_mode_gesture_two_fingers_hold_prod),
        selected = ui.exitGestureTypeValue == 1,
        onClick = { callbacks.onChangeGesture(1) }
      )

      RadioRow(
        title = androidx.compose.ui.res.stringResource(org.thoughtcrime.securesms.R.string.acc_mode_gesture_triple_tap_debug),
        selected = ui.exitGestureTypeValue == 3,
        onClick = { callbacks.onChangeGesture(3) }
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

  private inner class Callbacks : AccessibilityModeAdvancedSettingsCallbacks {
    override fun onNavigationClick() {
      requireActivity().onBackPressedDispatcher.onBackPressed()
    }

    override fun onChangeGesture(typeValue: Int) {
      viewModel.onChangeGesture(typeValue)
    }
  }
}

interface AccessibilityModeAdvancedSettingsCallbacks {
  fun onNavigationClick() = Unit
  fun onChangeGesture(typeValue: Int) = Unit

  object Empty : AccessibilityModeAdvancedSettingsCallbacks
}
