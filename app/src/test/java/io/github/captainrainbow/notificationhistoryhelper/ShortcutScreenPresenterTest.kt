package io.github.captainrainbow.notificationhistoryhelper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShortcutScreenPresenterTest {
    @Test fun returningFromSettingsDoesNotConfuseUnknownWithDenied() {
        assertNull(ShortcutScreenPresenter.settingsMessage(ShortcutPermissionState.ALLOWED))
        assertEquals(R.string.shortcut_permission_denied,
            ShortcutScreenPresenter.settingsMessage(ShortcutPermissionState.DENIED))
        assertEquals(R.string.shortcut_permission_unknown_help,
            ShortcutScreenPresenter.settingsMessage(ShortcutPermissionState.UNKNOWN))
    }

    @Test fun passiveChecksNeverShowANoticeIncludingDeniedPermission() {
        for (state in ShortcutState.entries) for (permission in ShortcutPermissionState.entries) {
            assertNull("No action: $state / $permission", ShortcutScreenPresenter.message(
                state, null, appearanceUpdated = false, permission = permission))
        }
    }
    @Test fun confirmedRequestDoesNotReturnToPendingAfterDeletion() {
        for (request in listOf(PinOutcome.CONFIRM_ON_LAUNCHER, PinOutcome.ALREADY_PINNED)) {
            var remaining = ShortcutScreenPresenter.outcomeAfterInspection(ShortcutState.PINNED, request)
            remaining = ShortcutScreenPresenter.outcomeAfterInspection(ShortcutState.UNKNOWN, remaining)
            remaining = ShortcutScreenPresenter.outcomeAfterInspection(ShortcutState.NOT_PINNED, remaining)
            assertEquals(ShortcutButtonStatus.NOT_ADDED,
                ShortcutScreenPresenter.buttonStatus(ShortcutState.NOT_PINNED, remaining))
            assertNull(ShortcutScreenPresenter.message(ShortcutState.NOT_PINNED, remaining))
            assertNull("Completed requests must not be saved for activity recreation", remaining)
        }
    }

    @Test fun anActuallyUnconfirmedRequestShowsGuidanceOnlyAfterAnAttempt() {
        for (state in listOf(ShortcutState.NOT_PINNED, ShortcutState.UNKNOWN)) {
            val remaining = ShortcutScreenPresenter.outcomeAfterInspection(state, PinOutcome.CONFIRM_ON_LAUNCHER)
            assertEquals(PinOutcome.CONFIRM_ON_LAUNCHER, remaining)
            assertNotEquals(null, ShortcutScreenPresenter.message(state, remaining))
            assertEquals(ShortcutButtonStatus.UNCONFIRMED,
                ShortcutScreenPresenter.buttonStatus(ShortcutState.NOT_PINNED, remaining))
        }
    }

    @Test fun restoredAlreadyPinnedResultCannotBecomeAPendingRequest() {
        val remaining = ShortcutScreenPresenter.outcomeAfterInspection(ShortcutState.NOT_PINNED, PinOutcome.ALREADY_PINNED)
        assertEquals(ShortcutButtonStatus.NOT_ADDED,
            ShortcutScreenPresenter.buttonStatus(ShortcutState.NOT_PINNED, remaining))
        assertNull(remaining)
    }

    @Test fun finishingAnAdditionDoesNotEraseActionableErrors() {
        for (outcome in listOf(PinOutcome.PERMISSION_DENIED, PinOutcome.FAILED)) {
            assertEquals(outcome, ShortcutScreenPresenter.outcomeAfterInspection(ShortcutState.PINNED, outcome))
            assertEquals(outcome, ShortcutScreenPresenter.outcomeAfterInspection(ShortcutState.NOT_PINNED, outcome))
        }
    }

    @Test fun permissionButtonShowsAllowedDeniedAndUnknownSeparately() {
        assertEquals(R.string.shortcut_permission_allowed,
            ShortcutScreenPresenter.permissionStatus(ShortcutPermissionState.ALLOWED))
        assertEquals(R.string.shortcut_permission_denied_status,
            ShortcutScreenPresenter.permissionStatus(ShortcutPermissionState.DENIED))
        assertEquals(R.string.shortcut_permission_status,
            ShortcutScreenPresenter.permissionStatus(ShortcutPermissionState.UNKNOWN))
    }

    @Test fun knownDenialIsNotReportedAsUnconfirmedCreation() {
        assertEquals(ShortcutButtonStatus.PERMISSION_REQUIRED, ShortcutScreenPresenter.buttonStatus(
            ShortcutState.NOT_PINNED, PinOutcome.CONFIRM_ON_LAUNCHER, ShortcutPermissionState.DENIED))
        assertEquals(R.string.shortcut_permission_denied, ShortcutScreenPresenter.message(
            ShortcutState.NOT_PINNED, PinOutcome.CONFIRM_ON_LAUNCHER, permission = ShortcutPermissionState.DENIED))
    }

    @Test fun registeredShortcutAndPermissionAreIndependent() {
        assertEquals(ShortcutButtonStatus.ADDED, ShortcutScreenPresenter.buttonStatus(
            ShortcutState.PINNED, null, ShortcutPermissionState.DENIED))
        assertNull(ShortcutScreenPresenter.message(ShortcutState.PINNED, null, permission = ShortcutPermissionState.DENIED))
        assertEquals(R.string.shortcut_permission_denied, ShortcutScreenPresenter.message(
            ShortcutState.PINNED, PinOutcome.PERMISSION_DENIED, permission = ShortcutPermissionState.DENIED))
    }

    @Test fun returningWithPermissionReplacesOldDenialWithRetryGuidance() {
        assertEquals(R.string.shortcut_permission_ready, ShortcutScreenPresenter.message(
            ShortcutState.NOT_PINNED, PinOutcome.PERMISSION_DENIED, permission = ShortcutPermissionState.ALLOWED))
        assertEquals(R.string.shortcut_permission_unknown_help, ShortcutScreenPresenter.message(
            ShortcutState.NOT_PINNED, PinOutcome.PERMISSION_DENIED, permission = ShortcutPermissionState.UNKNOWN))
    }

    @Test fun buttonStatusDistinguishesKnownAbsenceFromPendingAndUnknown() {
        assertEquals(ShortcutButtonStatus.NOT_ADDED,
            ShortcutScreenPresenter.buttonStatus(ShortcutState.NOT_PINNED, null))
        assertEquals(ShortcutButtonStatus.UNCONFIRMED,
            ShortcutScreenPresenter.buttonStatus(ShortcutState.NOT_PINNED, PinOutcome.CONFIRM_ON_LAUNCHER))
        assertEquals(ShortcutButtonStatus.UNKNOWN,
            ShortcutScreenPresenter.buttonStatus(ShortcutState.UNKNOWN, null))
    }

    @Test fun buttonUsesFreshRegistryEvenAfterAnOlderFailedRequest() {
        assertEquals(ShortcutButtonStatus.ADDED,
            ShortcutScreenPresenter.buttonStatus(ShortcutState.PINNED, PinOutcome.FAILED))
        assertEquals(ShortcutButtonStatus.NOT_ADDED,
            ShortcutScreenPresenter.buttonStatus(ShortcutState.NOT_PINNED, PinOutcome.ALREADY_PINNED))
        assertNull(ShortcutScreenPresenter.message(ShortcutState.NOT_PINNED, PinOutcome.ALREADY_PINNED))
    }

    @Test fun unsupportedButtonDoesNotPretendShortcutIsJustMissing() {
        assertEquals(ShortcutButtonStatus.UNAVAILABLE,
            ShortcutScreenPresenter.buttonStatus(ShortcutState.HOME_SCREEN_UNSUPPORTED, null))
        assertEquals(ShortcutButtonStatus.UNAVAILABLE,
            ShortcutScreenPresenter.buttonStatus(ShortcutState.HISTORY_UNAVAILABLE, null))
    }

    @Test fun acceptedRequestDoesNotDisplaySuccessWithoutPinnedEvidence() {
        assertNotEquals(null, ShortcutScreenPresenter.message(ShortcutState.NOT_PINNED, PinOutcome.CONFIRM_ON_LAUNCHER))
        assertNotEquals(null, ShortcutScreenPresenter.message(ShortcutState.UNKNOWN, PinOutcome.CONFIRM_ON_LAUNCHER))
        assertEquals(ShortcutButtonStatus.UNCONFIRMED,
            ShortcutScreenPresenter.buttonStatus(ShortcutState.NOT_PINNED, PinOutcome.CONFIRM_ON_LAUNCHER))
        assertEquals(ShortcutButtonStatus.UNKNOWN,
            ShortcutScreenPresenter.buttonStatus(ShortcutState.UNKNOWN, PinOutcome.CONFIRM_ON_LAUNCHER))
    }

    @Test fun confirmedRegistryStateReplacesOldFailure() {
        assertNull(ShortcutScreenPresenter.message(ShortcutState.PINNED, PinOutcome.FAILED))
        assertEquals(ShortcutButtonStatus.ADDED,
            ShortcutScreenPresenter.buttonStatus(ShortcutState.PINNED, PinOutcome.FAILED))
    }

    @Test fun confirmedAdditionOrRetryUsesButtonStatusWithoutInlineSuccess() {
        for (outcome in listOf(null) + PinOutcome.entries) {
            assertNull("Confirmed shortcut must not repeat a success message after $outcome",
                ShortcutScreenPresenter.message(ShortcutState.PINNED, outcome))
            assertEquals(ShortcutButtonStatus.ADDED,
                ShortcutScreenPresenter.buttonStatus(ShortcutState.PINNED, outcome))
        }
    }

    @Test fun unavailableHistoryTakesPrecedenceOverOldRequest() {
        assertEquals(R.string.status_unsupported,
            ShortcutScreenPresenter.message(ShortcutState.HISTORY_UNAVAILABLE, PinOutcome.CONFIRM_ON_LAUNCHER))
    }

    @Test fun requestFailureRemainsActionableInsteadOfShowingIdleDescription() {
        assertEquals(R.string.shortcut_failed,
            ShortcutScreenPresenter.message(ShortcutState.NOT_PINNED, PinOutcome.FAILED))
    }

    @Test fun unknownStateDoesNotClaimPermissionDenial() {
        assertNull(ShortcutScreenPresenter.message(ShortcutState.UNKNOWN, null))
        assertEquals(ShortcutButtonStatus.UNKNOWN,
            ShortcutScreenPresenter.buttonStatus(ShortcutState.UNKNOWN, null))
    }

    @Test fun unsupportedHomeScreenAndIdleStateAreDistinct() {
        assertNull(ShortcutScreenPresenter.message(ShortcutState.HOME_SCREEN_UNSUPPORTED, null))
        assertEquals(R.string.shortcut_unsupported,
            ShortcutScreenPresenter.message(ShortcutState.HOME_SCREEN_UNSUPPORTED, PinOutcome.LAUNCHER_UNSUPPORTED))
        assertNull(ShortcutScreenPresenter.message(ShortcutState.NOT_PINNED, null))
    }

    @Test fun existingShortcutDoesNotClutterHomeUntilUserRequestsOne() {
        assertNull(ShortcutScreenPresenter.message(ShortcutState.PINNED, null))
    }

    @Test fun alreadyPinnedDialogDoesNotAlsoShowInlineSuccess() {
        assertNull(ShortcutScreenPresenter.message(ShortcutState.PINNED, PinOutcome.ALREADY_PINNED))
        assertEquals(ShortcutButtonStatus.ADDED,
            ShortcutScreenPresenter.buttonStatus(ShortcutState.PINNED, PinOutcome.ALREADY_PINNED))
    }

    @Test fun unavailableRequestIsNotLostIfInspectionHasBecomeUnknown() {
        assertEquals(R.string.shortcut_unsupported,
            ShortcutScreenPresenter.message(ShortcutState.UNKNOWN, PinOutcome.LAUNCHER_UNSUPPORTED))
        assertEquals(R.string.status_unsupported,
            ShortcutScreenPresenter.message(ShortcutState.UNKNOWN, PinOutcome.HISTORY_UNAVAILABLE))
    }
    @Test fun appearanceUpdateFailureIsNotHiddenByAnEarlierPinResult() {
        for (outcome in listOf(PinOutcome.CONFIRM_ON_LAUNCHER, PinOutcome.FAILED)) {
            assertEquals(R.string.shortcut_update_failed,
                ShortcutScreenPresenter.message(ShortcutState.PINNED, outcome, appearanceUpdated = false))
        }
    }
}
