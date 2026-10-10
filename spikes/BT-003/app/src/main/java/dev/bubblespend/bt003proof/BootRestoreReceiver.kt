package dev.bubblespend.bt003proof

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

private const val BOOT_LOG_TAG = "BT003Proof"

class BootRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!OverlayService.isEnabled(context)) {
            Log.i(BOOT_LOG_TAG, "boot restoration skipped: shortcut disabled")
            return
        }
        if (!Settings.canDrawOverlays(context)) {
            Log.i(BOOT_LOG_TAG, "boot restoration skipped: overlay permission absent")
            return
        }

        try {
            context.startService(Intent(context, OverlayService::class.java).setAction(OverlayService.ACTION_SHOW))
            Log.i(BOOT_LOG_TAG, "boot restoration requested")
        } catch (_: IllegalStateException) {
            Log.i(BOOT_LOG_TAG, "boot restoration blocked by background execution policy")
        } catch (_: SecurityException) {
            Log.i(BOOT_LOG_TAG, "boot restoration blocked by system policy")
        }
    }
}
