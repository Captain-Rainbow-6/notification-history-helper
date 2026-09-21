package io.github.captainrainbow.notificationhistoryhelper

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri

/** An unexported callback must match a pending request and fresh registry evidence. */
class ShortcutPinResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val token = intent.data?.takeIf { it.scheme == "recallarchive-pin" }?.schemeSpecificPart
        if (token != null) {
            val pinned = try {
                AndroidHistoryShortcutGateway(context).isPinned()
            } catch (_: RuntimeException) {
                false
            }
            ShortcutCreationFeedback.confirm(context, token, pinned, callback = true)
        }
        context.sendBroadcast(Intent(REFRESH_ACTION).setPackage(context.packageName))
    }

    companion object {
        internal const val REFRESH_ACTION =
            "io.github.captainrainbow.notificationhistoryhelper.REFRESH_SHORTCUT_STATUS"

        internal fun resultIntent(context: Context, token: String? = null): IntentSender =
            PendingIntent.getBroadcast(
                context, 0,
                Intent(context, ShortcutPinResultReceiver::class.java).apply {
                    if (token != null) data = Uri.fromParts("recallarchive-pin", token, null)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT,
            ).intentSender
    }
}
