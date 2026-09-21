package io.github.captainrainbow.notificationhistoryhelper

import org.junit.Assert.*
import org.junit.Test

class ShortcutCreationRequestTest {
    @Test fun aNewRegistrationConfirmsOnlyTheCurrentRequest() {
        val request = ShortcutCreationRequest("current", false)
        assertTrue(request.isConfirmed("current", pinned = true, callback = false))
        assertFalse(request.isConfirmed("older", pinned = true, callback = true))
        assertFalse(request.isConfirmed(null, pinned = true, callback = true))
    }

    @Test fun acceptingOrCancellingARequestIsNotConfirmation() {
        val request = ShortcutCreationRequest("current", false)
        assertFalse(request.isConfirmed("current", pinned = false, callback = false))
        assertFalse(request.isConfirmed("current", pinned = false, callback = true))
    }

    @Test fun retryingAnExistingShortcutRequiresTheCurrentLauncherCallback() {
        val request = ShortcutCreationRequest("retry", true)
        assertFalse(request.isConfirmed("retry", pinned = true, callback = false))
        assertTrue(request.isConfirmed("retry", pinned = true, callback = true))
        assertFalse(request.isConfirmed("older", pinned = true, callback = true))
    }
}
