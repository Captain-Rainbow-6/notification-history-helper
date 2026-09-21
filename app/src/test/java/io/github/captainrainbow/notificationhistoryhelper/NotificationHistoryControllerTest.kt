package io.github.captainrainbow.notificationhistoryhelper

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationHistoryControllerTest {
    @Test
    fun supportedGatewayProducesSupportedState() {
        val controller = NotificationHistoryController(FakeGateway(available = true))

        assertEquals(SupportState.SUPPORTED, controller.supportState())
    }

    @Test
    fun unavailableGatewayProducesUnsupportedState() {
        val controller = NotificationHistoryController(FakeGateway(available = false))

        assertEquals(SupportState.UNSUPPORTED, controller.supportState())
    }

    @Test
    fun openReturnsOpenedWhenGatewaySucceeds() {
        val controller = NotificationHistoryController(
            FakeGateway(available = true, openSucceeds = true),
        )

        assertEquals(OpenOutcome.OPENED, controller.open())
    }

    @Test
    fun openReturnsFailedWhenGatewayCannotStartActivity() {
        val controller = NotificationHistoryController(
            FakeGateway(available = true, openSucceeds = false),
        )

        assertEquals(OpenOutcome.FAILED, controller.open())
    }

    @Test
    fun openDoesNotCallGatewayWhenActivityIsUnavailable() {
        val gateway = FakeGateway(available = false)
        val controller = NotificationHistoryController(gateway)

        assertEquals(OpenOutcome.UNAVAILABLE, controller.open())
        assertEquals(0, gateway.openCalls)
    }

    private class FakeGateway(
        private val available: Boolean,
        private val openSucceeds: Boolean = true,
    ) : NotificationHistoryGateway {
        var openCalls: Int = 0
            private set

        override fun isAvailable(): Boolean = available

        override fun open(): Boolean {
            openCalls += 1
            return openSucceeds
        }
    }
}
