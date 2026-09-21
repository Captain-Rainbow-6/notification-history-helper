package io.github.captainrainbow.notificationhistoryhelper

import android.content.ComponentName
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.ActivityNotFoundException
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.SystemClock
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class HistoryShortcutInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun historyPreflightMatchesTheInstalledSystemComponentsAccessRules() {
        val target = context.packageManager.resolveActivity(
            Intent("android.settings.NOTIFICATION_HISTORY"), PackageManager.MATCH_SYSTEM_ONLY,
        )?.activityInfo
        val expected = target != null && target.enabled && target.exported && target.applicationInfo.enabled &&
            (target.applicationInfo.flags and (android.content.pm.ApplicationInfo.FLAG_SYSTEM or
                android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) &&
            (target.permission.isNullOrEmpty() || context.checkSelfPermission(target.permission) == PackageManager.PERMISSION_GRANTED)
        var launched: Intent? = null
        val recordingContext = object : ContextWrapper(context) {
            override fun startActivity(intent: Intent) { launched = intent }
        }
        val gateway = AndroidNotificationHistoryGateway(recordingContext)
        assertEquals(expected, gateway.isAvailable())
        assertEquals(expected, gateway.open())
        if (expected) {
            assertEquals(ComponentName(target!!.packageName, target.name), launched?.component)
            assertEquals("android.settings.NOTIFICATION_HISTORY", launched?.action)
        } else {
            assertNull(launched)
        }
    }

    @Test fun shortcutUsesStableIdAndExplicitFixedDestinationWithoutExtras() {
        val first = AndroidHistoryShortcutGateway.buildShortcut(context)
        val second = AndroidHistoryShortcutGateway.buildShortcut(context)
        assertEquals("system-notification-history", first.id)
        assertEquals(first.id, second.id)
        assertEquals(ComponentName(context, MainActivity::class.java), first.activity)
        assertEquals(1, requireNotNull(first.intents).size)
        val intent = requireNotNull(first.intent)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(ComponentName(context, NotificationHistoryShortcutActivity::class.java), intent.component)
        assertNull(intent.data)
        assertTrue(intent.extras == null || intent.extras!!.isEmpty)
        assertEquals(context.getString(R.string.shortcut_label), first.shortLabel.toString())
        assertEquals(ShortcutPresentationPolicy.ICON_REVISION,
            first.extras?.getInt(AndroidHistoryShortcutGateway.ICON_REVISION_KEY))
    }

    @Test fun installedAppHasNoRequestedPermissionsOrNotificationListener() {
        val info = context.packageManager.getPackageInfo(
            context.packageName, PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES,
        )
        // AndroidX can add an app-private signature permission; no platform permissions.
        assertTrue(info.requestedPermissions.orEmpty().none { it.startsWith("android.permission.") })
        assertTrue(info.services.isNullOrEmpty())
    }

    @Test fun mainScreenKeepsOpenButtonAndAddsShortcutButton() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        DeviceUiTestSupport.bringAppToForeground()
        val activity = instrumentation.startActivitySync(
            DeviceUiTestSupport.scenarioIntent(),
        )
        try {
            val deadline = SystemClock.uptimeMillis() + 5000
            var checking = true
            while (checking && SystemClock.uptimeMillis() < deadline) {
                instrumentation.runOnMainSync {
                    checking = !DeviceUiTestSupport.hasRenderedShortcutStatus(
                        activity.findViewById<TextView>(R.id.addShortcutButton))
                }
                if (checking) SystemClock.sleep(50)
            }
            assertFalse("Shortcut query must complete", checking)
            instrumentation.runOnMainSync {
                assertNotNull(activity.findViewById<android.view.View>(R.id.openHistoryButton))
                assertNotNull(activity.findViewById<android.view.View>(R.id.addShortcutButton))
                assertTrue(activity.findViewById<android.view.View>(R.id.appSettingsButton).isShown)
                assertTrue(activity.findViewById<android.view.View>(R.id.appSettingsButton).isEnabled)
                assertTrue(activity.findViewById<android.view.View>(R.id.shortcutHelpButton).isShown)
                assertTrue(activity.findViewById<android.view.View>(R.id.permissionHelpButton).isShown)
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    @Test fun settingsLinkTargetsOnlyThisAppsDetails() {
        val intent = AndroidAppSettingsGateway.createIntent(context)
        assertEquals("android.settings.APPLICATION_DETAILS_SETTINGS", intent.action)
        assertEquals("package", intent.data?.scheme)
        assertEquals(context.packageName, intent.data?.schemeSpecificPart)
        assertNull(intent.extras)
    }

    @Test fun xiaomiSettingsLinkPrefersOwnPermissionsWithoutOpeningSettings() {
        val permissionIntent = Intent("miui.intent.action.APP_PERM_EDITOR")
            .setPackage("com.miui.securitycenter")
        // This integration case applies only where this OEM entry exists.
        org.junit.Assume.assumeTrue(context.packageManager.resolveActivity(
            permissionIntent, PackageManager.MATCH_SYSTEM_ONLY,
        ) != null)
        var launched: Intent? = null
        val recordingContext = object : ContextWrapper(context) {
            override fun startActivity(intent: Intent) { launched = intent }
        }

        assertTrue(AndroidAppSettingsGateway(recordingContext).open())

        val intent = requireNotNull(launched)
        assertEquals("miui.intent.action.APP_PERM_EDITOR", intent.action)
        assertEquals("com.miui.securitycenter", intent.component?.packageName)
        assertEquals(context.packageName, intent.getStringExtra("extra_pkgname"))
        assertEquals(context.applicationInfo.uid, intent.getIntExtra("extra_package_uid", -1))
        assertNull(intent.data)
    }

    @Test fun blockedXiaomiEntryFallsBackToOwnAppDetails() {
        assertXiaomiFallback(SecurityException("Permission denied"))
    }

    @Test fun removedXiaomiEntryFallsBackToOwnAppDetails() {
        assertXiaomiFallback(ActivityNotFoundException("Entry unavailable"))
    }

    @Test fun deniedSettingsLaunchReturnsFailureWithoutCrashing() {
        val recordingContext = object : ContextWrapper(context) {
            override fun startActivity(intent: Intent) {
                throw SecurityException("Settings unavailable")
            }
        }
        assertFalse(AndroidAppSettingsGateway(recordingContext).open())
    }

    private fun assertXiaomiFallback(failure: RuntimeException) {
        org.junit.Assume.assumeTrue(context.packageManager.resolveActivity(
            Intent("miui.intent.action.APP_PERM_EDITOR").setPackage("com.miui.securitycenter"),
            PackageManager.MATCH_SYSTEM_ONLY,
        ) != null)
        val launches = mutableListOf<Intent>()
        val recordingContext = object : ContextWrapper(context) {
            override fun startActivity(intent: Intent) {
                launches.add(Intent(intent))
                if (intent.action == "miui.intent.action.APP_PERM_EDITOR") throw failure
            }
        }
        assertTrue(AndroidAppSettingsGateway(recordingContext).open())
        assertEquals(2, launches.size)
        assertEquals("miui.intent.action.APP_PERM_EDITOR", launches[0].action)
        assertEquals("android.settings.APPLICATION_DETAILS_SETTINGS", launches[1].action)
        assertEquals("package:${context.packageName}", launches[1].dataString)
        assertNotNull(launches[1].component)
    }

    @Test fun resultCallbackIsPrivateAndRequestsRefreshInsteadOfTrustingExtras() {
        val info = context.packageManager.getReceiverInfo(
            ComponentName(context, ShortcutPinResultReceiver::class.java), 0,
        )
        assertFalse(info.exported)
        val refreshed = CountDownLatch(1)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { refreshed.countDown() }
        }
        ContextCompat.registerReceiver(context, receiver,
            IntentFilter(ShortcutPinResultReceiver.REFRESH_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
        try {
            ShortcutPinResultReceiver.resultIntent(context).sendIntent(context, 0, null, null, null)
            assertTrue("Callback must request a fresh query", refreshed.await(5, TimeUnit.SECONDS))
        } finally {
            context.unregisterReceiver(receiver)
        }
    }
}
