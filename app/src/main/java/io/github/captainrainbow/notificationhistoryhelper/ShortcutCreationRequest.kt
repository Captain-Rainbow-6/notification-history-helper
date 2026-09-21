package io.github.captainrainbow.notificationhistoryhelper

internal data class ShortcutCreationRequest(val token: String, val wasPinned: Boolean) {
    fun isConfirmed(requestToken: String?, pinned: Boolean, callback: Boolean): Boolean =
        token == requestToken && pinned && (!wasPinned || callback)
}
