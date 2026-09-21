package io.github.captainrainbow.notificationhistoryhelper

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class LanguagePolicyTest {
    @Test fun automaticUsesOnlyThePrimarySystemLanguage() {
        assertEquals("en", LanguagePolicy.resolve(LanguageMode.SYSTEM,
            listOf(Locale.JAPANESE, Locale.SIMPLIFIED_CHINESE)))
        assertEquals("en", LanguagePolicy.resolve(LanguageMode.SYSTEM, emptyList()))
        assertEquals("zh-CN", LanguagePolicy.resolve(LanguageMode.SYSTEM,
            listOf(Locale.SIMPLIFIED_CHINESE, Locale.ENGLISH)))
    }

    @Test fun simplifiedScriptIsRecognizedWithoutTreatingTraditionalAsSimplified() {
        for (tag in listOf("zh-CN", "zh-SG", "zh-Hans", "zh-Hans-HK", "zh")) {
            assertEquals(tag, "zh-CN", resolve(tag))
        }
        for (tag in listOf("zh-TW", "zh-HK", "zh-MO", "zh-Hant", "zh-Hant-CN",
            "zh-Latn", "en", "fr", "ar", "ja")) {
            assertEquals(tag, "en", resolve(tag))
        }
    }

    @Test fun manualSelectionOverridesAnySystemLanguage() {
        assertEquals("zh-CN", LanguagePolicy.resolve(LanguageMode.CHINESE, listOf(Locale.JAPANESE)))
        assertEquals("en", LanguagePolicy.resolve(LanguageMode.ENGLISH, listOf(Locale.SIMPLIFIED_CHINESE)))
    }

    @Test fun savedChoiceRoundTripsAndInvalidPreferenceReturnsToAutomatic() {
        for (mode in LanguageMode.entries) assertEquals(mode, LanguageMode.fromStored(mode.key))
        assertEquals(LanguageMode.SYSTEM, LanguageMode.fromStored(null))
        assertEquals(LanguageMode.SYSTEM, LanguageMode.fromStored("invalid"))
    }

    private fun resolve(tag: String) = LanguagePolicy.resolve(
        LanguageMode.SYSTEM, listOf(Locale.forLanguageTag(tag)),
    )
}
