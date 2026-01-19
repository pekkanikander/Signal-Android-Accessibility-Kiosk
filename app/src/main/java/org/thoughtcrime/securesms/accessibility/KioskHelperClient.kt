package org.thoughtcrime.securesms.accessibility

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.AppDependencies
import kotlin.coroutines.resume

/**
 * IPC client that communicates with the companion kiosk helper application to enable/disable kiosk mode.
 * Uses application context; safe to invoke from non-UI layers.
 */
object KioskHelperClient {
  private val TAG = Log.tag(KioskHelperClient::class.java)

  private const val HELPER_PACKAGE              = "fi.iki.pnr.kioskhelper"
  private const val ACTION_ENABLE_KIOSK         = "${HELPER_PACKAGE}.ACTION_ENABLE_KIOSK"
  private const val ACTION_DISABLE_KIOSK        = "${HELPER_PACKAGE}.ACTION_DISABLE_KIOSK"
  private const val EXTRA_RESULT_PENDING_INTENT = "${HELPER_PACKAGE}.extra.RESULT_PENDING_INTENT"
  private const val EXTRA_ALLOWLIST             = "${HELPER_PACKAGE}.extra.ALLOWLIST"
  private const val EXTRA_DND_MODE              = "${HELPER_PACKAGE}.extra.DND_MODE"
  private const val EXTRA_SUPPRESS_STATUS_BAR   = "${HELPER_PACKAGE}.extra.SUPPRESS_STATUS_BAR"

  /**
   * Attempts to enable/disable kiosk via the helper app. Returns true on explicit OK callback, false otherwise.
   */
  suspend fun requestKioskEnabled(enable: Boolean, timeoutMs: Long = 2000L): Boolean {
    val context = AppDependencies.application
    val action = if (enable) ACTION_ENABLE_KIOSK else ACTION_DISABLE_KIOSK
    Log.d(TAG, "requestKioskEnabled(enable=$enable) action=$action")

    val callbackAction = "org.thoughtcrime.securesms.KIOSK_RESULT." + System.currentTimeMillis()
    val resultIntent = Intent(callbackAction).setPackage(context.packageName)
    val resultPi = PendingIntent.getBroadcast(
      context,
      0,
      resultIntent,
      PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_MUTABLE
    )

    val registerFlags = if (Build.VERSION.SDK_INT >= 33) Context.RECEIVER_NOT_EXPORTED else 0

    return withTimeoutOrNull(timeoutMs) {
      suspendCancellableCoroutine { cont ->
        var completed = false
        val receiver = object : BroadcastReceiver() {
          override fun onReceive(ctx: Context?, intent: Intent?) {
            if (completed) return
            completed = true
            try {
              context.unregisterReceiver(this)
            } catch (_: Throwable) {}
            val ok = intent?.getStringExtra("status") == "OK"
            Log.d(TAG, "Helper callback received: status=${intent?.getStringExtra("status")}")
            cont.resume(ok)
          }
        }

        val filter = IntentFilter(callbackAction)
        try {
          context.registerReceiver(receiver, filter, registerFlags)
        } catch (t: Throwable) {
          Log.w(TAG, "Failed to register receiver", t)
          cont.resume(false)
          return@suspendCancellableCoroutine
        }

        val callerPkg = context.packageName
        val intent = Intent(action)
          .setPackage(HELPER_PACKAGE)
          .putStringArrayListExtra(EXTRA_ALLOWLIST, arrayListOf(callerPkg))
          .putExtra(EXTRA_DND_MODE, "total")
          .putExtra(EXTRA_SUPPRESS_STATUS_BAR, true)
          .putExtra(EXTRA_RESULT_PENDING_INTENT, resultPi)

        try {
          intent.setClassName(HELPER_PACKAGE, "${HELPER_PACKAGE}.KioskCommandActivity")
          context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (t: Throwable) {
          Log.w(TAG, "Failed to start helper activity", t)
          try { context.unregisterReceiver(receiver) } catch (_: Throwable) {}
          cont.resume(false)
          return@suspendCancellableCoroutine
        }

        cont.invokeOnCancellation {
          try { context.unregisterReceiver(receiver) } catch (_: Throwable) {}
        }
      }
    } ?: false
  }
}
