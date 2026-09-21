package io.github.captainrainbow.notificationhistoryhelper

internal enum class ShortcutPermissionState { ALLOWED, DENIED, UNKNOWN }

internal fun interface ShortcutPermissionGateway {
    fun inspect(): ShortcutPermissionState
}

internal object ShortcutPermissionQuery {
    fun read(isVerifiedXiaomiSystem: Boolean, readOwnMode: () -> Int): ShortcutPermissionState {
        if (!isVerifiedXiaomiSystem) return ShortcutPermissionState.UNKNOWN
        return try {
            // AppOps MODE_ALLOWED, MODE_IGNORED and MODE_ERRORED respectively.
            // DEFAULT/FOREGROUND and future values do not establish this permission.
            when (readOwnMode()) {
                0 -> ShortcutPermissionState.ALLOWED
                1, 2 -> ShortcutPermissionState.DENIED
                else -> ShortcutPermissionState.UNKNOWN
            }
        } catch (_: ReflectiveOperationException) {
            ShortcutPermissionState.UNKNOWN
        } catch (_: RuntimeException) {
            ShortcutPermissionState.UNKNOWN
        } catch (_: LinkageError) {
            ShortcutPermissionState.UNKNOWN
        }
    }
}
