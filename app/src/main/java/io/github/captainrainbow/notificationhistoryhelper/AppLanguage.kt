package io.github.captainrainbow.notificationhistoryhelper

import android.app.LocaleManager
import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat

internal object AppLanguage {
    private const val PREFERENCES = "ui_preferences"
    private const val MODE = "language_mode"

    fun mode(context: Context): LanguageMode = LanguageMode.fromStored(
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(MODE, null),
    )

    fun desiredTag(context: Context): String {
        // Use system locales, not this Activity's already-overridden resource locales.
        val locales = if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).systemLocales
        } else {
            Resources.getSystem().configuration.locales
        }
        return LanguagePolicy.resolve(mode(context), (0 until locales.size()).map { locales[it] })
    }

    fun select(context: Context, mode: LanguageMode) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit { putString(MODE, mode.key) }
        synchronize(context)
    }

    /** Before the first delegate is registered, Android 13+ needs the public context API. */
    fun prepare(context: Context) {
        val tag = desiredTag(context)
        if (Build.VERSION.SDK_INT >= 33) {
            val manager = context.getSystemService(LocaleManager::class.java)
            if (manager.applicationLocales.toLanguageTags() != tag) {
                manager.applicationLocales = LocaleList.forLanguageTags(tag)
            }
        } else {
            applyCompat(tag)
        }
    }

    fun synchronize(context: Context) = applyCompat(desiredTag(context))

    private fun applyCompat(tag: String) {
        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != tag) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        }
    }
}
