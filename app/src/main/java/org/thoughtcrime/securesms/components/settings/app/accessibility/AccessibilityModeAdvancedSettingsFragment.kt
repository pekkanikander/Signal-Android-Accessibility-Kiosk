package org.thoughtcrime.securesms.components.settings.app.accessibility

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import org.thoughtcrime.securesms.compose.ComposeFragment

class AccessibilityModeAdvancedSettingsFragment : ComposeFragment() {

  private val viewModel: AccessibilityModeSettingsViewModel by viewModels()

  @Composable
  override fun FragmentContent() {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    AccessibilityModeAdvancedSettingsScreen(
      viewModel = viewModel,
      onBack = { try { findNavController().navigateUp() } catch (_: Exception) {} }
    )
  }
}
