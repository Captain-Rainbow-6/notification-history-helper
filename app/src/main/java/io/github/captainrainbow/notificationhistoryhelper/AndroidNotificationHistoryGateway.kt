package io.github.captainrainbow.notificationhistoryhelper

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

private const val NOTIFICATION_HISTORY_ACTION = "android.settings.NOTIFICATION_HISTORY"

internal data class NotificationHistoryTarget(
    val packageName: String,
    val className: String,
    val isSystemApp: Boolean,
    val isEnabled: Boolean = true,
    val isExported: Boolean = true,
    val isApplicationEnabled: Boolean = true,
    val hasRequiredPermission: Boolean = true,
)

internal class AndroidNotificationHistoryGateway(
    private val resolver: (String) -> NotificationHistoryTarget?,
    private val starter: (String, NotificationHistoryTarget) -> Unit,
) : NotificationHistoryGateway {
    constructor(context: Context) : this(
        resolver = resolver@{ action ->
            val activityInfo = context.packageManager.resolveActivity(
                Intent(action),
                PackageManager.MATCH_SYSTEM_ONLY,
            )?.activityInfo ?: return@resolver null
            val systemFlags = ApplicationInfo.FLAG_SYSTEM or
                ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
            NotificationHistoryTarget(
                packageName = activityInfo.packageName,
                className = activityInfo.name,
                isSystemApp = activityInfo.applicationInfo.flags and systemFlags != 0,
                isEnabled = activityInfo.enabled,
                isExported = activityInfo.exported,
                isApplicationEnabled = activityInfo.applicationInfo.enabled,
                hasRequiredPermission = activityInfo.permission.isNullOrEmpty() ||
                    context.checkSelfPermission(activityInfo.permission) == PackageManager.PERMISSION_GRANTED,
            )
        },
        starter = { action, target ->
            context.startActivity(
                Intent(action).setComponent(
                    ComponentName(target.packageName, target.className),
                ),
            )
        },
    )

    override fun isAvailable(): Boolean = trustedTarget() != null

    override fun open(): Boolean = try {
        val target = trustedTarget() ?: return false
        starter(NOTIFICATION_HISTORY_ACTION, target)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    private fun trustedTarget(): NotificationHistoryTarget? = try {
        resolver(NOTIFICATION_HISTORY_ACTION)?.takeIf {
            it.isSystemApp && it.isEnabled && it.isExported &&
                it.isApplicationEnabled && it.hasRequiredPermission
        }
    } catch (_: SecurityException) {
        null
    }
}
