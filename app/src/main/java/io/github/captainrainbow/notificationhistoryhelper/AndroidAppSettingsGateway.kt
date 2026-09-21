package io.github.captainrainbow.notificationhistoryhelper

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings

internal class AndroidAppSettingsGateway(private val context: Context) {
    fun open(): Boolean =
        tryOpen(createPermissionIntent(context)) || tryOpen(createIntent(context))

    private fun tryOpen(intent: Intent): Boolean {
        return try {
            val target = context.packageManager.resolveActivity(
                intent, PackageManager.MATCH_SYSTEM_ONLY,
            )?.activityInfo ?: return false
            val systemFlags = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
            if (target.applicationInfo.flags and systemFlags == 0) return false
            if (!target.enabled || !target.exported || !target.applicationInfo.enabled) return false
            if (intent.`package` != null && target.packageName != intent.`package`) return false
            val requiredPermission = target.permission
            if (!requiredPermission.isNullOrEmpty() &&
                context.checkSelfPermission(requiredPermission) != PackageManager.PERMISSION_GRANTED
            ) return false
            intent.component = ComponentName(target.packageName, target.name)
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    companion object {
        // Optional OEM navigation, not a runtime permission request. Always keep the
        // standard app-details fallback: this vendor entry may change or reject us.
        private fun createPermissionIntent(context: Context): Intent =
            Intent("miui.intent.action.APP_PERM_EDITOR")
                .setPackage("com.miui.securitycenter")
                .putExtra("extra_pkgname", context.packageName)
                .putExtra("extra_package_uid", context.applicationInfo.uid)

        internal fun createIntent(context: Context): Intent =
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", context.packageName, null))
    }
}
