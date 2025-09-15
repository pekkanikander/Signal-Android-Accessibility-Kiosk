package org.thoughtcrime.securesms.components.settings.app.accessibility

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import org.thoughtcrime.securesms.recipients.RecipientId

class AccessibilityModeSettingsFragment : ComposeFragment() {

  private val viewModel: AccessibilityModeSettingsViewModel by viewModels()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    // Scope result to the view lifecycle (matches baseline settings fragments)
    parentFragmentManager.setFragmentResultListener("pick_recipient", viewLifecycleOwner) { _, bundle ->
      val rid: RecipientId? = bundle.getParcelable("recipient_id")
      if (rid != null) {
        viewModel.onSelectRecipient(rid)
      }
    }
  }

  @Composable
  override fun FragmentContent() {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val callbacks = Callbacks()

    AccessibilityModeSettingsScreen(
      ui = ui,
      callbacks = callbacks
    )
  }

  private inner class Callbacks : AccessibilityModeSettingsCallbacks {
    override fun onNavigationClick() {
      requireActivity().onBackPressedDispatcher.onBackPressed()
    }

    override fun onToggleEnabled(enabled: Boolean) {
      viewModel.onToggleEnabled(enabled)
    }

    override fun onLaunchPicker() {
      findNavController().safeNavigate(
        R.id.action_accessibilityModeSettingsFragment_to_chatSelectionFragment
      )
    }

    override fun onOpenAdvanced() {
      findNavController().safeNavigate(
        R.id.action_accessibilityModeSettingsFragment_to_accessibilityModeAdvancedSettingsFragment
      )
    }
  }
}

interface AccessibilityModeSettingsCallbacks {
  fun onNavigationClick() = Unit
  fun onToggleEnabled(enabled: Boolean) = Unit
  fun onLaunchPicker() = Unit
  fun onOpenAdvanced() = Unit

  object Empty : AccessibilityModeSettingsCallbacks
}
