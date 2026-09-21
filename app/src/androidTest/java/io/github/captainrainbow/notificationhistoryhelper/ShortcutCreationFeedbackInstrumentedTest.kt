package io.github.captainrainbow.notificationhistoryhelper

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShortcutCreationFeedbackInstrumentedTest {
    // Isolated metadata only. These tests never create an icon or display a success Toast.
    private val context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            super.getSharedPreferences("test_$name", mode)
    }

    @Test fun callbackAndRequeryConsumeOnlyOneSuccess() {
        val token = ShortcutCreationFeedback.begin(context, wasPinned = false)
        try {
            assertFalse(ShortcutCreationFeedback.consumeConfirmation(context, token, pinned = false, callback = true))
            assertTrue(ShortcutCreationFeedback.consumeConfirmation(context, token, pinned = true, callback = true))
            assertFalse(ShortcutCreationFeedback.consumeConfirmation(context, token, pinned = true, callback = false))
            assertFalse(ShortcutCreationFeedback.consumeConfirmation(context, token, pinned = true, callback = true))
        } finally { ShortcutCreationFeedback.cancel(context, token) }
    }

    @Test fun anOlderCallbackCannotCompleteANewerRequestOrExistingShortcutRetry() {
        val old = ShortcutCreationFeedback.begin(context, wasPinned = false)
        val current = ShortcutCreationFeedback.begin(context, wasPinned = true)
        try {
            assertFalse(ShortcutCreationFeedback.consumeConfirmation(context, old, pinned = true, callback = true))
            assertFalse(ShortcutCreationFeedback.consumeConfirmation(context, current, pinned = true, callback = false))
            assertTrue(ShortcutCreationFeedback.consumeConfirmation(context, current, pinned = true, callback = true))
        } finally { ShortcutCreationFeedback.cancel(context, current) }
    }

    @Test fun failureCancelsTheRequestWithoutConfirmingCreation() {
        val token = ShortcutCreationFeedback.begin(context, wasPinned = false)
        ShortcutCreationFeedback.cancel(context, token)
        assertFalse(ShortcutCreationFeedback.consumeConfirmation(context, token, pinned = true, callback = true))
    }
}
