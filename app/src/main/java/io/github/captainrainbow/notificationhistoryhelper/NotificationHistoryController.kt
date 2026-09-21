package io.github.captainrainbow.notificationhistoryhelper

enum class SupportState {
    SUPPORTED,
    UNSUPPORTED,
}

enum class OpenOutcome {
    OPENED,
    UNAVAILABLE,
    FAILED,
}

class NotificationHistoryController(
    private val gateway: NotificationHistoryGateway,
) {
    fun supportState(): SupportState = if (gateway.isAvailable()) {
        SupportState.SUPPORTED
    } else {
        SupportState.UNSUPPORTED
    }

    fun open(): OpenOutcome {
        if (!gateway.isAvailable()) return OpenOutcome.UNAVAILABLE

        return if (gateway.open()) {
            OpenOutcome.OPENED
        } else {
            OpenOutcome.FAILED
        }
    }
}
