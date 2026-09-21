package io.github.captainrainbow.notificationhistoryhelper

import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryShortcutControllerTest {
    private var available = true
    private var supported = true
    private var requested = 0
    private var accepted = true
    private var pinned = false
    private var inspectionFailure: RuntimeException? = null
    private var failure: RuntimeException? = null
    private var permissionState = ShortcutPermissionState.UNKNOWN
    private val permission = ShortcutPermissionGateway { permissionState }
    private val history = object : NotificationHistoryGateway {
        override fun isAvailable() = available
        override fun open(): Boolean = error("Pinning must not launch history")
    }
    private val pin = object : HistoryShortcutGateway {
        override fun isSupported() = supported
        override fun isPinned(): Boolean {
            inspectionFailure?.let { throw it }
            return pinned
        }
        override fun requestPin(): Boolean {
            requested++
            failure?.let { throw it }
            return accepted
        }
    }

    @Test fun unavailableHistoryDoesNotRequestPin() {
        available = false
        assertEquals(PinOutcome.HISTORY_UNAVAILABLE, HistoryShortcutController(history, pin).request())
        assertEquals(0, requested)
    }

    @Test fun deniedPermissionBlocksANewRequestAndExplicitRetry() {
        permissionState = ShortcutPermissionState.DENIED
        val controller = HistoryShortcutController(history, pin, permission)
        assertEquals(PinOutcome.PERMISSION_DENIED, controller.request())
        assertEquals(PinOutcome.PERMISSION_DENIED, controller.request(allowDuplicate = true))
        assertEquals(0, requested)
    }

    @Test fun alreadyAddedDoesNotMeanPermissionIsStillGranted() {
        permissionState = ShortcutPermissionState.DENIED
        pinned = true
        val controller = HistoryShortcutController(history, pin, permission)
        assertEquals(ShortcutState.PINNED, controller.inspect())
        assertEquals(PinOutcome.ALREADY_PINNED, controller.request())
        assertEquals(0, requested)
    }

    @Test fun permissionGrantedLaterAllowsRetryWithoutRestarting() {
        val controller = HistoryShortcutController(history, pin, permission)
        permissionState = ShortcutPermissionState.DENIED
        assertEquals(PinOutcome.PERMISSION_DENIED, controller.request())
        permissionState = ShortcutPermissionState.ALLOWED
        assertEquals(PinOutcome.CONFIRM_ON_LAUNCHER, controller.request())
        assertEquals(1, requested)
    }

    @Test fun unknownPermissionStillAllowsStandardAndroidRequest() {
        assertEquals(PinOutcome.CONFIRM_ON_LAUNCHER, HistoryShortcutController(history, pin, permission).request())
        assertEquals(1, requested)
    }

    @Test fun unsupportedLauncherDoesNotRequestPin() {
        supported = false
        assertEquals(PinOutcome.LAUNCHER_UNSUPPORTED, HistoryShortcutController(history, pin).request())
        assertEquals(0, requested)
    }

    @Test fun acceptedRequestIsPendingUserConfirmationNotInstallationSuccess() {
        assertEquals(PinOutcome.CONFIRM_ON_LAUNCHER, HistoryShortcutController(history, pin).request())
        assertEquals(1, requested)
    }

    @Test fun existingShortcutDoesNotSendAnotherPinRequest() {
        pinned = true
        assertEquals(PinOutcome.ALREADY_PINNED, HistoryShortcutController(history, pin).request())
        assertEquals("An existing shortcut must be explained, not duplicated", 0, requested)
    }

    @Test fun explicitRetryCanRequestAgainButStillChecksCapability() {
        pinned = true
        val controller = HistoryShortcutController(history, pin)
        assertEquals(PinOutcome.CONFIRM_ON_LAUNCHER, controller.request(allowDuplicate = true))
        assertEquals(1, requested)
        supported = false
        assertEquals(PinOutcome.LAUNCHER_UNSUPPORTED, controller.request(allowDuplicate = true))
        available = false
        assertEquals(PinOutcome.HISTORY_UNAVAILABLE, controller.request(allowDuplicate = true))
        assertEquals(1, requested)
    }

    @Test fun explicitRetryRecoversFromUnreadableRegistryWithoutClaimingSuccess() {
        inspectionFailure = SecurityException("unavailable")
        assertEquals(PinOutcome.CONFIRM_ON_LAUNCHER,
            HistoryShortcutController(history, pin).request(allowDuplicate = true))
        assertEquals(1, requested)
    }

    @Test fun unreadableRegistryDoesNotBlindlyCreateADuplicate() {
        inspectionFailure = SecurityException("unavailable")
        HistoryShortcutController(history, pin).request()
        assertEquals(0, requested)
    }

    @Test fun existingShortcutIsRecognizedEvenIfPinningIsCurrentlyUnavailable() {
        pinned = true
        supported = false
        assertEquals(ShortcutState.PINNED, HistoryShortcutController(history, pin).inspect())
    }

    @Test fun rejectedRequestIsReported() {
        accepted = false
        assertEquals(PinOutcome.FAILED, HistoryShortcutController(history, pin).request())
    }

    @Test fun permissionRejectionDoesNotCrash() {
        failure = SecurityException("denied")
        assertEquals(PinOutcome.FAILED, HistoryShortcutController(history, pin).request())
    }

    @Test fun noLongerForegroundDoesNotCrash() {
        failure = IllegalStateException("background")
        assertEquals(PinOutcome.FAILED, HistoryShortcutController(history, pin).request())
    }

    @Test fun supportIsCheckedAgainForEveryRequest() {
        val controller = HistoryShortcutController(history, pin)
        controller.request()
        available = false
        assertEquals(PinOutcome.HISTORY_UNAVAILABLE, controller.request())
        assertEquals(1, requested)
    }

    @Test fun acceptedButNotPinnedRemainsUnconfirmed() {
        val controller = HistoryShortcutController(history, pin)
        controller.request()
        assertEquals(ShortcutState.NOT_PINNED, controller.inspect())
    }

    @Test fun onlySystemPinnedStateConfirmsCreation() {
        pinned = true
        assertEquals(ShortcutState.PINNED, HistoryShortcutController(history, pin).inspect())
    }

    @Test fun returningFromSettingsReadsFreshState() {
        val controller = HistoryShortcutController(history, pin)
        assertEquals(ShortcutState.NOT_PINNED, controller.inspect())
        pinned = true
        assertEquals(ShortcutState.PINNED, controller.inspect())
        pinned = false
        assertEquals(ShortcutState.NOT_PINNED, controller.inspect())
    }

    @Test fun failedInspectionDoesNotClaimMissingPermissionOrSuccess() {
        inspectionFailure = SecurityException("unavailable")
        assertEquals(ShortcutState.UNKNOWN, HistoryShortcutController(history, pin).inspect())
    }

    @Test fun inspectionDistinguishesUnsupportedHistoryAndHomeScreen() {
        val controller = HistoryShortcutController(history, pin)
        available = false
        assertEquals(ShortcutState.HISTORY_UNAVAILABLE, controller.inspect())
        available = true
        supported = false
        assertEquals(ShortcutState.HOME_SCREEN_UNSUPPORTED, controller.inspect())
    }

    @Test fun disabledShortcutRejectionDoesNotCrash() {
        failure = IllegalArgumentException("shortcut disabled")
        assertEquals(PinOutcome.FAILED, HistoryShortcutController(history, pin).request())
    }
}
