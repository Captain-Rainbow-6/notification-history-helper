package io.github.captainrainbow.notificationhistoryhelper

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeModeTest {
    @Test fun savedChoiceRoundTripsWithoutTurningSystemIntoAManualChoice() {
        for (mode in ThemeMode.entries) assertEquals(mode, ThemeMode.fromStored(mode.key))
    }

    @Test fun newOrUnknownPreferencesFollowSystem() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored("unsupported"))
    }
}
