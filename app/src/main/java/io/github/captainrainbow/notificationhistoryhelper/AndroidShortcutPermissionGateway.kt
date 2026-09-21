package io.github.captainrainbow.notificationhistoryhelper

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process

/** Optional Xiaomi compatibility query, not an Android runtime permission.
 * Verified on Redmi K80 / HyperOS with this app's own UID and targetSdk 36.
 * OEM operation 10017 is not a public SDK contract; never extrapolate to other vendors,
 * bypass hidden-API restrictions, change permissions or turn a query failure into success.
 */
internal class AndroidShortcutPermissionGateway(private val context: Context) : ShortcutPermissionGateway {
    override fun inspect(): ShortcutPermissionState = ShortcutPermissionQuery.read(hasXiaomiSystem()) {
        val manager = context.getSystemService(AppOpsManager::class.java)
            ?: error("AppOps service unavailable")
        val method = AppOpsManager::class.java.getMethod("checkOpNoThrow",
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java)
        method.invoke(manager, XIAOMI_INSTALL_SHORTCUT, Process.myUid(), context.packageName) as Int
    }

    private fun hasXiaomiSystem(): Boolean {
        if (listOf("Xiaomi", "Redmi", "Poco").none { Build.MANUFACTURER.equals(it, ignoreCase = true) }) {
            return false
        }
        return try {
            val info = context.packageManager.getApplicationInfo("com.miui.securitycenter", 0)
            info.enabled && info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    private companion object {
        const val XIAOMI_INSTALL_SHORTCUT = 10017
    }
}
