package io.github.captainrainbow.notificationhistoryhelper

internal enum class ShortcutButtonStatus { ADDED, NOT_ADDED, UNCONFIRMED, UNAVAILABLE, UNKNOWN, PERMISSION_REQUIRED }

internal object ShortcutScreenPresenter {
    fun settingsMessage(permission: ShortcutPermissionState): Int? = when (permission) {
        ShortcutPermissionState.ALLOWED -> null
        ShortcutPermissionState.DENIED -> R.string.shortcut_permission_denied
        ShortcutPermissionState.UNKNOWN -> R.string.shortcut_permission_unknown_help
    }
    fun outcomeAfterInspection(state: ShortcutState, outcome: PinOutcome?): PinOutcome? = when {
        // This result is already final: no new pin request was made.
        outcome == PinOutcome.ALREADY_PINNED -> null
        // Consume a pending request once Android confirms the shortcut exists.
        // A later removal must not revive that completed request.
        state == ShortcutState.PINNED && outcome == PinOutcome.CONFIRM_ON_LAUNCHER -> null
        else -> outcome
    }

    fun permissionStatus(state: ShortcutPermissionState): Int = when (state) {
        ShortcutPermissionState.ALLOWED -> R.string.shortcut_permission_allowed
        ShortcutPermissionState.DENIED -> R.string.shortcut_permission_denied_status
        ShortcutPermissionState.UNKNOWN -> R.string.shortcut_permission_status
    }

    fun buttonStatus(state: ShortcutState, outcome: PinOutcome?,
                     permission: ShortcutPermissionState = ShortcutPermissionState.UNKNOWN): ShortcutButtonStatus = when (state) {
        ShortcutState.PINNED -> ShortcutButtonStatus.ADDED
        ShortcutState.NOT_PINNED -> when {
            permission == ShortcutPermissionState.DENIED -> ShortcutButtonStatus.PERMISSION_REQUIRED
            outcome == PinOutcome.CONFIRM_ON_LAUNCHER -> ShortcutButtonStatus.UNCONFIRMED
            else -> ShortcutButtonStatus.NOT_ADDED
        }
        ShortcutState.HISTORY_UNAVAILABLE, ShortcutState.HOME_SCREEN_UNSUPPORTED -> ShortcutButtonStatus.UNAVAILABLE
        ShortcutState.UNKNOWN -> ShortcutButtonStatus.UNKNOWN
    }

    fun message(state: ShortcutState, outcome: PinOutcome?, appearanceUpdated: Boolean = true,
                permission: ShortcutPermissionState = ShortcutPermissionState.UNKNOWN): Int? = when {
        // Passive inspections update button labels only, never open the notice card.
        outcome == null || outcome == PinOutcome.ALREADY_PINNED -> null
        state == ShortcutState.HISTORY_UNAVAILABLE -> R.string.status_unsupported
        state == ShortcutState.HOME_SCREEN_UNSUPPORTED -> R.string.shortcut_unsupported
        permission == ShortcutPermissionState.DENIED &&
            (state != ShortcutState.PINNED || outcome == PinOutcome.PERMISSION_DENIED) -> R.string.shortcut_permission_denied
        state == ShortcutState.PINNED && !appearanceUpdated -> R.string.shortcut_update_failed
        // The button already shows Added, including after first-time additions and retries.
        // Keep actionable permission/update problems above; never repeat success below it.
        state == ShortcutState.PINNED -> null
        outcome == PinOutcome.HISTORY_UNAVAILABLE -> R.string.status_unsupported
        outcome == PinOutcome.LAUNCHER_UNSUPPORTED -> R.string.shortcut_unsupported
        outcome == PinOutcome.PERMISSION_DENIED -> if (permission == ShortcutPermissionState.ALLOWED)
            R.string.shortcut_permission_ready else R.string.shortcut_permission_unknown_help
        outcome == PinOutcome.FAILED -> R.string.shortcut_failed
        outcome == PinOutcome.CONFIRM_ON_LAUNCHER -> R.string.shortcut_not_confirmed
        else -> null
    }
}
