package io.github.captainrainbow.notificationhistoryhelper

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit

/** App-only appearance; never changes the phone's system theme. */
internal object AppAppearance {
    private const val PREFERENCES = "ui_preferences"
    private const val MODE = "theme_mode"

    fun mode(context: Context): ThemeMode = ThemeMode.fromStored(
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(MODE, null),
    )

    fun select(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit { putString(MODE, mode.key) }
        prepare(context)
    }

    // Apply before Activity inflation, including a new process and shortcut entry.
    fun prepare(context: Context) {
        val nightMode = when (mode(context)) {
            ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
        }
        if (AppCompatDelegate.getDefaultNightMode() != nightMode) {
            AppCompatDelegate.setDefaultNightMode(nightMode)
        }
    }
}
