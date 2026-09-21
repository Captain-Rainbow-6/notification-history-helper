package io.github.captainrainbow.notificationhistoryhelper

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.PersistableBundle

internal class AndroidHistoryShortcutGateway(private val context: Context) : HistoryShortcutGateway {
    private val manager get() = context.getSystemService(ShortcutManager::class.java)
    var pendingRequestToken: String? = null
        private set

    override fun isSupported(): Boolean = manager?.isRequestPinShortcutSupported == true
    override fun isPinned(): Boolean = checkNotNull(manager).pinnedShortcuts.any {
        it.id == SHORTCUT_ID && it.isEnabled
    }

    // A true result means the request was accepted, not that the user confirmed it.
    override fun requestPin(): Boolean {
        val service = manager ?: return false
        val token = ShortcutCreationFeedback.begin(context, isPinned())
        pendingRequestToken = token
        var accepted = false
        try {
            accepted = service.requestPinShortcut(buildShortcut(context),
                ShortcutPinResultReceiver.resultIntent(context, token))
            return accepted
        } finally {
            if (!accepted) {
                ShortcutCreationFeedback.cancel(context, token)
                pendingRequestToken = null
            }
        }
    }

    /** Refresh this app's existing label/artwork only. Never requests or creates an icon. */
    fun refreshPinnedAppearance(): Boolean = try {
        val service = manager
        val existing = service?.pinnedShortcuts?.firstOrNull { it.id == SHORTCUT_ID && it.isEnabled }
        if (existing == null || !ShortcutPresentationPolicy.needsUpdate(
                existing.shortLabel.toString(), existing.longLabel?.toString(),
                existing.extras?.getInt(ICON_REVISION_KEY),
                context.getString(R.string.shortcut_label), context.getString(R.string.open_history),
            )) true else service.updateShortcuts(listOf(buildShortcut(context)))
    } catch (_: SecurityException) {
        false
    } catch (_: IllegalStateException) {
        false
    } catch (_: IllegalArgumentException) {
        false
    }

    companion object {
        internal const val SHORTCUT_ID = "system-notification-history"
        internal const val ICON_REVISION_KEY = "shortcutIconRevision"

        internal fun buildShortcut(context: Context): ShortcutInfo =
            ShortcutInfo.Builder(context, SHORTCUT_ID)
                .setShortLabel(context.getString(R.string.shortcut_label))
                .setLongLabel(context.getString(R.string.open_history))
                .setIcon(Icon.createWithResource(context, R.drawable.ic_shortcut))
                .setExtras(PersistableBundle().apply {
                    putInt(ICON_REVISION_KEY, ShortcutPresentationPolicy.ICON_REVISION)
                })
                .setActivity(ComponentName(context, MainActivity::class.java))
                .setIntent(
                    Intent(context, NotificationHistoryShortcutActivity::class.java)
                        .setAction(Intent.ACTION_VIEW),
                )
                .build()
    }
}
