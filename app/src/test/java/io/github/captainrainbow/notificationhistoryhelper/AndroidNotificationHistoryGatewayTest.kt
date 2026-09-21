package io.github.captainrainbow.notificationhistoryhelper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidNotificationHistoryGatewayTest {
    private val systemTarget = NotificationHistoryTarget(
        "com.android.settings", "com.android.settings.NotificationHistoryActivity", true,
    )

    @Test fun rejectsDisabledActivity() = assertUnavailable(systemTarget.copy(isEnabled = false))
    @Test fun rejectsNonExportedActivity() = assertUnavailable(systemTarget.copy(isExported = false))
    @Test fun rejectsDisabledApplication() = assertUnavailable(systemTarget.copy(isApplicationEnabled = false))
    @Test fun rejectsMissingLaunchPermission() = assertUnavailable(systemTarget.copy(hasRequiredPermission = false))

    private fun assertUnavailable(target: NotificationHistoryTarget) {
        val gateway = AndroidNotificationHistoryGateway(
            resolver = { target }, starter = { _, _ -> error("must not launch") },
        )
        assertFalse(gateway.isAvailable())
        assertFalse(gateway.open())
    }

    @Test fun openRechecksAccessEvenIfPreflightPreviouslySucceeded() {
        var target = systemTarget
        val gateway = AndroidNotificationHistoryGateway(
            resolver = { target }, starter = { _, _ -> error("must not launch") },
        )
        assertTrue(gateway.isAvailable())
        target = target.copy(isExported = false)
        assertFalse(gateway.open())
    }

    @Test
    fun availabilityReturnsFalseWhenSystemRejectsResolution() {
        val gateway = AndroidNotificationHistoryGateway(
            resolver = { throw SecurityException("lookup blocked") },
            starter = { _, _ -> error("must not launch") },
        )
        assertFalse(gateway.isAvailable())
        assertFalse(gateway.open())
    }

    @Test
    fun isAvailableAcceptsOnlyATrustedSystemHandler() {
        val trustedTarget = NotificationHistoryTarget(
            packageName = "com.android.settings",
            className = "com.android.settings.NotificationHistoryActivity",
            isSystemApp = true,
        )
        val gateway = AndroidNotificationHistoryGateway(
            resolver = { trustedTarget },
            starter = { _, _ -> },
        )

        assertTrue(gateway.isAvailable())
    }

    @Test
    fun isAvailableRejectsAThirdPartyHandler() {
        val untrustedTarget = NotificationHistoryTarget(
            packageName = "example.attacker",
            className = "example.attacker.FakeHistoryActivity",
            isSystemApp = false,
        )
        val gateway = AndroidNotificationHistoryGateway(
            resolver = { untrustedTarget },
            starter = { _, _ -> },
        )

        assertFalse(gateway.isAvailable())
    }

    @Test
    fun openReresolvesAndLaunchesTheExplicitSystemTarget() {
        var resolutionCount = 0
        var startedAction: String? = null
        var startedTarget: NotificationHistoryTarget? = null
        val trustedTarget = NotificationHistoryTarget(
            packageName = "com.android.settings",
            className = "com.android.settings.NotificationHistoryActivity",
            isSystemApp = true,
        )
        val gateway = AndroidNotificationHistoryGateway(
            resolver = {
                resolutionCount += 1
                trustedTarget
            },
            starter = { action, target ->
                startedAction = action
                startedTarget = target
            },
        )

        assertTrue(gateway.isAvailable())
        assertTrue(gateway.open())
        assertEquals(2, resolutionCount)
        assertEquals("android.settings.NOTIFICATION_HISTORY", startedAction)
        assertEquals(trustedTarget, startedTarget)
    }

    @Test
    fun openReturnsFalseWhenNoTrustedSystemHandlerExists() {
        val gateway = AndroidNotificationHistoryGateway(
            resolver = { null },
            starter = { _, _ -> error("must not launch") },
        )

        assertFalse(gateway.open())
    }

    @Test
    fun openReturnsFalseWhenSystemRejectsLaunch() {
        val gateway = AndroidNotificationHistoryGateway(
            resolver = {
                NotificationHistoryTarget(
                    packageName = "com.android.settings",
                    className = "com.android.settings.NotificationHistoryActivity",
                    isSystemApp = true,
                )
            },
            starter = { _, _ -> throw SecurityException("blocked") },
        )

        assertFalse(gateway.open())
    }
}
