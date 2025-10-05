package org.thoughtcrime.securesms.components.settings.app.accessibility

import android.content.Context

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

    // Provide a concrete implementation callable from composables
    fun onSetSuppressNotifications(enabled: Boolean) {
      viewModel.onSetSuppressNotifications(enabled)
    }

    override fun onRequestKioskToggle(desired: Boolean) {
      sendKioskIntent(desired) { success ->
        if (success) {
          viewModel.onSetKioskEnabled(desired)
        } else {
          viewModel.onSetKioskEnabled(false)
          android.widget.Toast.makeText(requireContext(), R.string.acc_mode_kiosk_error, android.widget.Toast.LENGTH_SHORT).show()
        }
      }
    }
  }

  private fun sendKioskIntent(enable: Boolean, onResult: (Boolean) -> Unit) {
    // Minimal contract per helper design: setPackage + action + optional ResultReceiver.
    // Corner cases: helper missing or no response -> treat as failure and revert UI with a toast.
    val action = if (enable) ACTION_ENABLE_KIOSK else ACTION_DISABLE_KIOSK
    Log.d(TAG, "sendKioskIntent(enable=$enable) action=$action package=$HELPER_PACKAGE")

    val intent = Intent(action)
      .setPackage(HELPER_PACKAGE)

    // Prepare a unique one-shot broadcast for the helper's callback
    val callbackAction = "org.thoughtcrime.securesms.KIOSK_RESULT." + System.currentTimeMillis()
    val resultIntent = Intent(callbackAction).setPackage(requireContext().packageName)
    val resultPi = PendingIntent.getBroadcast(
      requireContext(), 0, resultIntent,
      PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_MUTABLE
    )

    // One-shot dynamic receiver
    val filter = IntentFilter(callbackAction)
    val handler = Handler(Looper.getMainLooper())
    var completed = false

    val receiver = object : BroadcastReceiver() {
      override fun onReceive(ctx: android.content.Context?, i: Intent?) {
        if (completed) return
        completed = true
        try { requireContext().unregisterReceiver(this) } catch (_: Throwable) {}
        handler.removeCallbacksAndMessages(null)
        val ok = i?.getStringExtra("status") == "OK"
        Log.d(TAG, "Helper callback received: status=${i?.getStringExtra("status")}")
        onResult(ok)
      }
    }

    // Register receiver (API 33+ explicit NOT_EXPORTED for dynamic receivers)
    requireContext().registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)

    // Attach the PendingIntent callback and start the helper command activity
    intent.putExtra("fi.iki.pnr.kioskhelper.extra.RESULT_PENDING_INTENT", resultPi)

    try {
      Log.d(TAG, "Starting helper command activity…")
      intent.setClassName(HELPER_PACKAGE, "fi.iki.pnr.kioskhelper.KioskCommandActivity")
      startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (se: SecurityException) {
      Log.e(TAG, "Helper rejected startActivity (permission?)", se)
      try { requireContext().unregisterReceiver(receiver) } catch (_: Throwable) {}
      onResult(false)
      return
    } catch (t: Throwable) {
      Log.w(TAG, "Failed to start helper activity", t)
      try { requireContext().unregisterReceiver(receiver) } catch (_: Throwable) {}
      onResult(false)
      return
    }

    // Fallback timeout if helper never responds
    handler.postDelayed({
      if (!completed) {
        Log.w(TAG, "Helper callback timed out")
        completed = true
        try { requireContext().unregisterReceiver(receiver) } catch (_: Throwable) {}
        onResult(false)
      }
    }, 3000L)
  }

  private companion object {
    private val TAG = Log.tag(AccessibilityModeSettingsFragment::class.java)
    const val HELPER_PACKAGE = "fi.iki.pnr.kioskhelper"
    const val ACTION_ENABLE_KIOSK = "fi.iki.pnr.kioskhelper.ACTION_ENABLE_KIOSK"
    const val ACTION_DISABLE_KIOSK = "fi.iki.pnr.kioskhelper.ACTION_DISABLE_KIOSK"
    const val EXTRA_RESULT_PENDING_INTENT = "fi.iki.pnr.kioskhelper.extra.RESULT_PENDING_INTENT"
  }

  // Adapter interface to expose the concrete callbacks to composables
  private interface AccessibilityModeSettingsCallbacksImpl {
    fun onSetSuppressNotifications(enabled: Boolean)
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
        if (record != null) {
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
          record = record,
          onClick = callbacks::onLaunchPicker
        )
      }

      item { Divider() }

      item {
        // Kiosk helper toggle (API 33+). Sends intent to external helper and reverts on error.
        val isApi33Plus = android.os.Build.VERSION.SDK_INT >= 33
        if (!isApi33Plus) {
          ListItem(
            headlineContent = {
              Text(
                stringResource(R.string.acc_mode_kiosk_enable),
                style = MaterialTheme.typography.bodyLarge
              )
            },
            supportingContent = {
              Text(
                stringResource(R.string.acc_mode_kiosk_requires_android13),
                style = MaterialTheme.typography.bodySmall
              )
            },
            trailingContent = {
              Switch(checked = false, onCheckedChange = null, enabled = false)
            }
          )
        } else {
          ListItem(
            headlineContent = {
              Text(
                stringResource(R.string.acc_mode_kiosk_enable),
                style = MaterialTheme.typography.bodyLarge
              )
            },
            supportingContent = {
              Text(
                stringResource(R.string.acc_mode_kiosk_subtitle),
                style = MaterialTheme.typography.bodySmall
              )
            },
            trailingContent = {
              Switch(
                checked = ui.kioskEnabled,
                onCheckedChange = { desired -> callbacks.onRequestKioskToggle(desired) }
              )
            },
            modifier = Modifier
              .fillMaxWidth()
              .clickable { callbacks.onRequestKioskToggle(!ui.kioskEnabled) }
              .padding(horizontal = 8.dp)
          )
        }
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
        headlineContent = { Text(stringResource(R.string.acc_mode_no_chats)) },
        modifier = Modifier.clickable(onClick = onClick)
      )

    record == null ->
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

interface AccessibilityModeSettingsCallbacks {
  fun onNavigationClick() = Unit
  fun onToggleEnabled(enabled: Boolean) = Unit
  fun onLaunchPicker() = Unit
  fun onOpenAdvanced() = Unit
  fun onRequestKioskToggle(desired: Boolean) = Unit

  object Empty : AccessibilityModeSettingsCallbacks
}
