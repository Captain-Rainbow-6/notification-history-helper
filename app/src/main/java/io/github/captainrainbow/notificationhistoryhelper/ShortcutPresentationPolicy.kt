package io.github.captainrainbow.notificationhistoryhelper

internal object ShortcutPresentationPolicy {
    const val ICON_REVISION = 5

    fun needsUpdate(shortLabel: String, longLabel: String?, iconRevision: Int?,
                    expectedShortLabel: String, expectedLongLabel: String): Boolean =
        shortLabel != expectedShortLabel || longLabel != expectedLongLabel || iconRevision != ICON_REVISION
}
