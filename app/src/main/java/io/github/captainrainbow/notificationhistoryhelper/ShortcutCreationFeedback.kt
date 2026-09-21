package io.github.captainrainbow.notificationhistoryhelper

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import android.widget.Toast
import androidx.core.content.edit
import java.util.UUID

/** Only request metadata, not notifications or UI notices, survives process recreation. */
internal object ShortcutCreationFeedback {
    private const val PREFERENCES = "shortcut_creation"
    private const val TOKEN = "pending_token"
    private const val WAS_PINNED = "was_pinned"

    @Synchronized fun begin(context: Context, wasPinned: Boolean): String {
        val token = UUID.randomUUID().toString()
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit {
            putString(TOKEN, token)
            putBoolean(WAS_PINNED, wasPinned)
        }
        return token
    }

    @Synchronized fun cancel(context: Context, token: String) {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        if (preferences.getString(TOKEN, null) == token) preferences.edit { clear() }
    }

    /** Consume once, so callback and foreground re-query cannot produce duplicate Toasts. */
    @Synchronized fun consumeConfirmation(context: Context, token: String?, pinned: Boolean,
                                          callback: Boolean): Boolean {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val pending = preferences.getString(TOKEN, null) ?: return false
        val request = ShortcutCreationRequest(pending, preferences.getBoolean(WAS_PINNED, true))
        if (!request.isConfirmed(token, pinned, callback)) return false
        preferences.edit { clear() }
        return true
    }

    fun confirm(context: Context, token: String?, pinned: Boolean, callback: Boolean = false) {
        if (consumeConfirmation(context, token, pinned, callback)) {
            // A receiver may run without an Activity; honor the app's manual language too.
            val configuration = Configuration().apply {
                setLocales(LocaleList.forLanguageTags(AppLanguage.desiredTag(context)))
            }
            val localized = context.createConfigurationContext(configuration)
            Toast.makeText(localized, R.string.shortcut_created, Toast.LENGTH_SHORT).show()
        }
    }
}
