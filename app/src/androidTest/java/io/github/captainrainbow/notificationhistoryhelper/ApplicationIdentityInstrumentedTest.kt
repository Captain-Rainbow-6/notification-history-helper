package io.github.captainrainbow.notificationhistoryhelper

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/** Catches partially renamed APKs whose launcher, shortcut or settings target the old app. */
@RunWith(AndroidJUnit4::class)
class ApplicationIdentityInstrumentedTest {
    @Test fun publicAppIdentityOwnsLauncherShortcutSettingsAndCallback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val appId = "io.github.captainrainbow.notificationhistoryhelper"
        assertEquals(appId, context.packageName)
        val launcher = context.packageManager.getLaunchIntentForPackage(appId)
        assertNotNull("The new app must be independently launchable", launcher)
        assertEquals(ComponentName(appId, "$appId.MainActivity"), launcher!!.component)

        val shortcut = AndroidHistoryShortcutGateway.buildShortcut(context)
        assertEquals("system-notification-history", shortcut.id)
        assertEquals(launcher.component, shortcut.activity)
        val destination = requireNotNull(shortcut.intent)
        assertEquals(ComponentName(appId, "$appId.NotificationHistoryShortcutActivity"), destination.component)
        assertNotNull(context.packageManager.resolveActivity(destination, 0))
        assertEquals("package:$appId", AndroidAppSettingsGateway.createIntent(context).dataString)

        val receiver = context.packageManager.getReceiverInfo(
            ComponentName(appId, "$appId.ShortcutPinResultReceiver"), PackageManager.GET_META_DATA,
        )
        assertFalse(receiver.exported)
        assertEquals("$appId.REFRESH_SHORTCUT_STATUS", ShortcutPinResultReceiver.REFRESH_ACTION)
    }
}
