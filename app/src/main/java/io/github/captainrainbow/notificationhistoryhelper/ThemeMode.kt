package io.github.captainrainbow.notificationhistoryhelper

internal enum class ThemeMode(val key: String) {
    SYSTEM("system"), LIGHT("light"), DARK("dark");

    companion object {
        fun fromStored(value: String?): ThemeMode = entries.firstOrNull { it.key == value } ?: SYSTEM
    }
}
