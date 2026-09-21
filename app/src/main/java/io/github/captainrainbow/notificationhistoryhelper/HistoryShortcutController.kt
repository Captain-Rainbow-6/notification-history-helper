package io.github.captainrainbow.notificationhistoryhelper

internal interface HistoryShortcutGateway {
    fun isSupported(): Boolean
    fun requestPin(): Boolean
    fun isPinned(): Boolean
}

internal enum class ShortcutState {
    NOT_PINNED, PINNED, HISTORY_UNAVAILABLE, HOME_SCREEN_UNSUPPORTED, UNKNOWN,
}

internal enum class PinOutcome {
    HISTORY_UNAVAILABLE, LAUNCHER_UNSUPPORTED, ALREADY_PINNED, CONFIRM_ON_LAUNCHER, PERMISSION_DENIED, FAILED,
}

internal class HistoryShortcutController(
    private val history: NotificationHistoryGateway,
    private val shortcuts: HistoryShortcutGateway,
    private val permission: ShortcutPermissionGateway = ShortcutPermissionGateway { ShortcutPermissionState.UNKNOWN },
) {
    fun permissionState(): ShortcutPermissionState = try {
        permission.inspect()
    } catch (_: RuntimeException) {
        ShortcutPermissionState.UNKNOWN
    }

    fun inspect(): ShortcutState = try {
        when {
            !history.isAvailable() -> ShortcutState.HISTORY_UNAVAILABLE
            shortcuts.isPinned() -> ShortcutState.PINNED
            !shortcuts.isSupported() -> ShortcutState.HOME_SCREEN_UNSUPPORTED
            else -> ShortcutState.NOT_PINNED
        }
    } catch (_: SecurityException) {
        ShortcutState.UNKNOWN
    } catch (_: IllegalStateException) {
        ShortcutState.UNKNOWN
    }

    fun request(allowDuplicate: Boolean = false): PinOutcome = try {
        when {
            !history.isAvailable() -> PinOutcome.HISTORY_UNAVAILABLE
            !allowDuplicate && shortcuts.isPinned() -> PinOutcome.ALREADY_PINNED
            !shortcuts.isSupported() -> PinOutcome.LAUNCHER_UNSUPPORTED
            permissionState() == ShortcutPermissionState.DENIED -> PinOutcome.PERMISSION_DENIED
            shortcuts.requestPin() -> PinOutcome.CONFIRM_ON_LAUNCHER
            else -> PinOutcome.FAILED
        }
    } catch (_: SecurityException) {
        PinOutcome.FAILED
    } catch (_: IllegalStateException) {
        PinOutcome.FAILED
    } catch (_: IllegalArgumentException) {
        PinOutcome.FAILED
    }
}
