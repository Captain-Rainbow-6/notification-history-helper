package io.github.captainrainbow.notificationhistoryhelper

import org.junit.Assert.*
import org.junit.Test

class ShortcutPermissionQueryTest {
    @Test fun allowedAndDeniedModesAreDifferentFromUnknown() {
        assertEquals(ShortcutPermissionState.ALLOWED, ShortcutPermissionQuery.read(true) { 0 })
        assertEquals(ShortcutPermissionState.DENIED, ShortcutPermissionQuery.read(true) { 1 })
        assertEquals(ShortcutPermissionState.DENIED, ShortcutPermissionQuery.read(true) { 2 })
    }
    @Test fun defaultsAndUnrecognizedModesNeverClaimPermission() {
        for (mode in listOf(3, 4, -1, 999)) {
            assertEquals(ShortcutPermissionState.UNKNOWN, ShortcutPermissionQuery.read(true) { mode })
        }
    }
    @Test fun otherSystemsNeverCallTheOemInterface() {
        var called = false
        assertEquals(ShortcutPermissionState.UNKNOWN, ShortcutPermissionQuery.read(false) { called = true; 0 })
        assertFalse(called)
    }
    @Test fun blockedOrMissingInterfaceReturnsUnknownNotAllowedOrDenied() {
        for (failure in listOf(NoSuchMethodException(), SecurityException(), IllegalArgumentException())) {
            assertEquals(ShortcutPermissionState.UNKNOWN, ShortcutPermissionQuery.read(true) { throw failure })
        }
    }
    @Test fun permissionIsReadAgainRatherThanCached() {
        var mode = 0
        assertEquals(ShortcutPermissionState.ALLOWED, ShortcutPermissionQuery.read(true) { mode })
        mode = 1
        assertEquals(ShortcutPermissionState.DENIED, ShortcutPermissionQuery.read(true) { mode })
    }
}
