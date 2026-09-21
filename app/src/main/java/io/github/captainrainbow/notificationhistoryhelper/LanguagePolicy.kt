package io.github.captainrainbow.notificationhistoryhelper

import java.util.Locale

internal enum class LanguageMode(val key: String) {
    SYSTEM("system"), CHINESE("zh-Hans"), ENGLISH("en");

    companion object {
        fun fromStored(value: String?): LanguageMode = entries.firstOrNull { it.key == value } ?: SYSTEM
    }
}

/** Pure policy: never use a secondary system language as an implicit fallback. */
internal object LanguagePolicy {
    fun resolve(mode: LanguageMode, systemLocales: List<Locale>): String = when (mode) {
        LanguageMode.CHINESE -> "zh-CN"
        LanguageMode.ENGLISH -> "en"
        LanguageMode.SYSTEM -> if (isSimplified(systemLocales.firstOrNull())) "zh-CN" else "en"
    }

    private fun isSimplified(locale: Locale?): Boolean {
        if (locale?.language != "zh") return false
        return when (locale.script) {
            "Hans" -> true
            "" -> locale.country !in setOf("TW", "HK", "MO")
            else -> false
        }
    }
}
