package io.github.captainrainbow.notificationhistoryhelper

interface NotificationHistoryGateway {
    fun isAvailable(): Boolean

    fun open(): Boolean
}
