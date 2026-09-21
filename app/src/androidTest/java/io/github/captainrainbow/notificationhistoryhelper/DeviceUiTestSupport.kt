package io.github.captainrainbow.notificationhistoryhelper

import android.app.KeyguardManager
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/** Test-only bootstrap: same app launch as tapping its icon; no permission changes. */
internal object DeviceUiTestSupport {
    fun hasRenderedShortcutStatus(button: android.widget.TextView): Boolean {
        // Unavailable is a valid terminal result, even though the action stays disabled.
        val status = androidx.core.view.ViewCompat.getStateDescription(button)?.toString()
        return !status.isNullOrBlank() && button.text.toString().endsWith("\n$status")
    }

    fun scenarioIntent(): android.content.Intent {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // ActivityScenario matches lifecycle events by intent, including identifier (API 29+).
        // Do not let a recreating shell bootstrap or a prior scenario match this launch.
        return requireNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
            .setIdentifier(java.util.UUID.randomUUID().toString())
    }

    fun bringAppToForeground() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        assertFalse("Unlock the phone before UI tests; no extra app permission is needed.",
            keyguard.isKeyguardLocked || keyguard.isDeviceLocked)
        // HyperOS can reject an instrumentation-process background start even when unlocked.
        // ADB shell is already authorized for these tests; this never ships in the app APK.
        val output = ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(
                "am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER " +
                    "-n io.github.captainrainbow.notificationhistoryhelper/.MainActivity",
            ),
        ).bufferedReader().use { it.readText() }
        assertTrue("Normal app launch failed: $output", output.contains("Status: ok"))
        instrumentation.waitForIdleSync()
    }
}
