package io.github.captainrainbow.notificationhistoryhelper

import org.junit.Assert.*
import org.junit.Test

class ShortcutPresentationPolicyTest {
    @Test fun oldShortcutWithoutRevisionNeedsIconUpdateEvenWhenLabelsMatch() {
        assertTrue(needsUpdate(null))
    }
    @Test fun olderIconRevisionNeedsUpdate() {
        assertTrue(needsUpdate(0))
    }
    @Test fun unchangedPresentationDoesNotKeepUpdatingOnEveryResume() {
        assertFalse(needsUpdate(ShortcutPresentationPolicy.ICON_REVISION))
    }
    @Test fun changedLanguageStillUpdatesExistingShortcut() {
        assertTrue(ShortcutPresentationPolicy.needsUpdate("Notification history", "Open notification history",
            ShortcutPresentationPolicy.ICON_REVISION, "通知历史", "打开通知历史"))
    }
    private fun needsUpdate(revision: Int?) = ShortcutPresentationPolicy.needsUpdate(
        "通知历史", "打开通知历史", revision, "通知历史", "打开通知历史")
}
