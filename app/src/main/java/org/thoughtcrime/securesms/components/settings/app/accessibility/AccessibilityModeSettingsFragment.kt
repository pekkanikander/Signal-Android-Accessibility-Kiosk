package org.thoughtcrime.securesms.components.settings.app.accessibility

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import java.util.Locale
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import org.signal.core.util.logging.Log
import org.signal.core.ui.compose.Scaffolds
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.BindableConversationListItem
import org.thoughtcrime.securesms.conversationlist.model.ConversationSet
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import org.thoughtcrime.securesms.database.model.ThreadRecord
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
    val record by viewModel.selectedThreadRecord.collectAsStateWithLifecycle()
    val callbacks = Callbacks()

    AccessibilityModeSettingsScreen(
      ui = ui,
      record = record,
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

    override fun onRequestKioskToggle(desired: Boolean) {
      viewModel.onSetKioskEnabled(desired)
    }
  }


}

@Composable
private fun AccessibilityModeSettingsScreen(
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
        val header   = if (record == null) {
          R.string.AccessibilityModeSettingsFragment__selected_chat_header
        } else {
          R.string.AccessibilityModeSettingsFragment__choose_chat_header
        }
        val subtitle = if (record == null) {
          R.string.AccessibilityModeSettingsFragment__selected_chat_subtitle
        } else {
          R.string.AccessibilityModeSettingsFragment__choose_chat_subtitle
        }
        ListItem(
          headlineContent = {
            Text(
              stringResource(header),
              style = MaterialTheme.typography.bodyLarge
            )
          },
          supportingContent = {
            Text(
              stringResource(subtitle),
              style = MaterialTheme.typography.bodySmall
            )
          }
        )
      }

      item {
        // Conversation selection row first
        ConversationSelectionRow(
          items = ui.conversations,
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
              stringResource(R.string.AccessibilityModeSettingsFragment__enable),
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
        // Kiosk helper toggle (API 29+). Sends intent to external helper and reverts on error.
        val isApi29Plus = android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val subtitle = if (!isApi29Plus) {
          R.string.AccessibilityModeSettingsFragment__kiosk_requires_android10
        } else {
          R.string.AccessibilityModeSettingsFragment__kiosk_subtitle
        }
        ListItem(
          headlineContent = {
            Text(
              stringResource(R.string.AccessibilityModeSettingsFragment__enable_kiosk),
              style = MaterialTheme.typography.bodyLarge
            )
          },
          supportingContent = {
            Text(
              stringResource(subtitle),
              style = MaterialTheme.typography.bodySmall
            )
          },
          trailingContent = {
            if (!isApi29Plus) {
              Switch(checked = false, onCheckedChange = null, enabled = false)
            } else {
              Switch(checked = ui.kioskEnabled, onCheckedChange = { desired -> callbacks.onRequestKioskToggle(desired) })
            }
          },
          modifier = Modifier
          .fillMaxWidth()
          .clickable { callbacks.onRequestKioskToggle(!ui.kioskEnabled) }
          .padding(horizontal = 8.dp)
        )
      }

      item { Divider() }

      item {
        // Advanced link
        ListItem(
          headlineContent = {
            Text(
              stringResource(R.string.AccessibilityModeSettingsFragment__advanced),
              style = MaterialTheme.typography.bodyLarge
            )
          },
          supportingContent = {
            Text(
              stringResource(R.string.AccessibilityModeSettingsFragment__advanced_subtitle),
              style = MaterialTheme.typography.bodySmall
            )
          },
          modifier = Modifier.clickable { callbacks.onOpenAdvanced() }
        )
      }

      item { Divider() }

      // Suppress notifications is an advanced option; link to advanced settings instead of duplicating UI here.
    }
  }
}

@Composable
private fun ConversationSelectionRow(items: List<Long>, record: ThreadRecord?, onClick: () -> Unit) {
  when {
    items.isEmpty() ->
      ListItem(
        headlineContent = { Text(stringResource(R.string.AccessibilityModeSettingsFragment__no_chats)) },
        modifier = Modifier.clickable(onClick = onClick)
      )

    record == null ->
      ListItem(
        headlineContent = { Text(stringResource(R.string.AccessibilityModeSettingsFragment__select_chat)) },
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
      headlineContent = { Text(stringResource(R.string.AccessibilityModeSettingsFragment__select_chat)) },
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

interface AccessibilityModeSettingsCallbacks {
  fun onNavigationClick() = Unit
  fun onToggleEnabled(enabled: Boolean) = Unit
  fun onLaunchPicker() = Unit
  fun onOpenAdvanced() = Unit
  fun onRequestKioskToggle(desired: Boolean) = Unit

  object Empty : AccessibilityModeSettingsCallbacks
}
