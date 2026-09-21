package io.github.captainrainbow.notificationhistoryhelper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MainScreenPresenterTest {
    @Test
    fun supportedStateEnablesButtonWithoutPermanentStatusCopy() {
        val model = MainScreenPresenter.present(SupportState.SUPPORTED)

        assertNull(model.statusTextRes)
        assertTrue(model.buttonEnabled)
        assertNull(model.errorTextRes)
    }

    @Test
    fun unsupportedStateDisablesButton() {
        val model = MainScreenPresenter.present(SupportState.UNSUPPORTED)

        assertEquals(R.string.status_unsupported, model.statusTextRes)
        assertFalse(model.buttonEnabled)
        assertNull(model.errorTextRes)
    }

    @Test
    fun launchFailureShowsInlineErrorWithoutDisablingSupportedButton() {
        val model = MainScreenPresenter.present(
            supportState = SupportState.SUPPORTED,
            outcome = OpenOutcome.FAILED,
        )

        assertTrue(model.buttonEnabled)
        assertEquals(R.string.open_failed, model.errorTextRes)
    }
}
