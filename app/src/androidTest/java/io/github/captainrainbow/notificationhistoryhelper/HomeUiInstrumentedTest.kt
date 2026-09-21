package io.github.captainrainbow.notificationhistoryhelper

import android.content.Context
import android.content.pm.ShortcutManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class HomeUiInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var originalLanguage: LanguageMode
    private lateinit var originalTheme: ThemeMode
    private var originalNight = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM

    @Before fun rememberUserPreferences() {
        originalLanguage = AppLanguage.mode(context)
        originalTheme = AppAppearance.mode(context)
        originalNight = AppCompatDelegate.getDefaultNightMode()
    }

    @After fun restoreUserPreferences() {
        instrumentation.runOnMainSync {
            AppLanguage.select(context, originalLanguage)
            AppLanguage.prepare(context)
            AppAppearance.select(context, originalTheme)
            AppCompatDelegate.setDefaultNightMode(originalNight)
        }
        instrumentation.waitForIdleSync()
    }

    private fun launch(): ActivityScenario<MainActivity> {
        DeviceUiTestSupport.bringAppToForeground()
        return ActivityScenario.launch(DeviceUiTestSupport.scenarioIntent())
    }

    @Test fun unsupportedShortcutStatusIsAResultEvenWithADisabledButton() {
        instrumentation.runOnMainSync {
            val button = TextView(context)
            button.isEnabled = false
            button.text = context.getString(R.string.add_shortcut) + "\n" +
                context.getString(R.string.shortcut_unavailable_status)
            androidx.core.view.ViewCompat.setStateDescription(button,
                context.getString(R.string.shortcut_unavailable_status))
            assertTrue(DeviceUiTestSupport.hasRenderedShortcutStatus(button))
        }
    }

    @Test fun clickableButtonWithoutAStatusIsNotAQueryResult() {
        instrumentation.runOnMainSync {
            val button = TextView(context)
            button.isEnabled = true
            button.setText(R.string.add_shortcut)
            assertFalse(DeviceUiTestSupport.hasRenderedShortcutStatus(button))
        }
    }

    @Test fun testLaunchCannotMatchTheBootstrapOrAnotherScenariosLifecycle() {
        val first = DeviceUiTestSupport.scenarioIntent()
        val second = DeviceUiTestSupport.scenarioIntent()
        val bootstrap = android.content.Intent(android.content.Intent.ACTION_MAIN)
            .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
            .setComponent(android.content.ComponentName(context, MainActivity::class.java))
        assertEquals(bootstrap.component, first.component)
        assertNotEquals("Each scenario must own a separate lifecycle identity", first.identifier, second.identifier)
        assertNotEquals("The shell bootstrap must not be tracked by ActivityScenario", bootstrap.identifier, first.identifier)
    }

    @Test fun repeatedLaunchAndHelpAfterAppearanceChanges() {
        repeat(6) { round ->
            instrumentation.runOnMainSync {
                AppLanguage.select(context, if (round % 2 == 0) LanguageMode.CHINESE else LanguageMode.ENGLISH)
                AppAppearance.select(context, if (round % 2 == 0) ThemeMode.DARK else ThemeMode.LIGHT)
            }
            launch().use { scenario ->
                awaitShortcutReady(scenario)
                onView(withId(R.id.shortcutHelpButton)).perform(click())
                onView(withId(R.id.sheetTitle)).check(matches(isDisplayed()))
                onView(withId(R.id.closeSheetButton)).perform(click())
                scenario.recreate()
                awaitShortcutReady(scenario)
                scenario.onActivity { assertFalse(it.isFinishing || it.isDestroyed) }
            }
        }
    }

    @Test fun existingShortcutShowsDialogWithoutDuplicateInlineHint() {
        // Use an existing user-created shortcut; never create a fixture on the home screen.
        val manager = context.getSystemService(ShortcutManager::class.java)
        val originalIds = manager.pinnedShortcuts.map { it.id }.toSet()
        assumeTrue("This check requires an already pinned history shortcut",
            originalIds.contains("system-notification-history"))
        launch().use { scenario ->
            awaitShortcutReady(scenario)
            repeat(2) {
                onView(withId(R.id.addShortcutButton)).perform(scrollTo(), click())
                awaitShortcutReady(scenario)
                onView(withId(R.id.sheetTitle)).check(matches(isDisplayed()))
                onView(withId(R.id.closeSheetButton)).perform(click())
                onView(withId(R.id.shortcutStatusText))
                    .check(matches(withEffectiveVisibility(Visibility.GONE)))
                onView(withId(R.id.shortcutFeedbackGroup))
                    .check(matches(withEffectiveVisibility(Visibility.GONE)))
                onView(withId(R.id.closeShortcutMessageButton))
                    .check(matches(withEffectiveVisibility(Visibility.GONE)))
                scenario.recreate()
                awaitShortcutReady(scenario)
                onView(withId(R.id.shortcutStatusText))
                    .check(matches(withEffectiveVisibility(Visibility.GONE)))
                scenario.onActivity { activity ->
                    assertEquals(activity.getString(R.string.shortcut_added_status),
                        androidx.core.view.ViewCompat.getStateDescription(
                            activity.findViewById(R.id.addShortcutButton)))
                }
            }
        }
        assertEquals(originalIds, manager.pinnedShortcuts.map { it.id }.toSet())
        assertEquals("Existing shortcut must receive the new icon without creating another one",
            ShortcutPresentationPolicy.ICON_REVISION,
            manager.pinnedShortcuts.single { it.id == "system-notification-history" }
                .extras?.getInt(AndroidHistoryShortcutGateway.ICON_REVISION_KEY))
    }

    private fun awaitShortcutReady(scenario: ActivityScenario<MainActivity>) {
        val deadline = SystemClock.uptimeMillis() + 5000
        var ready = false
        while (!ready && SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity { ready = it.findViewById<View>(R.id.addShortcutButton).isEnabled }
            if (!ready) SystemClock.sleep(50)
        }
        assertTrue("Shortcut status check must finish", ready)
    }

    @Test fun refreshingShortcutStatusDoesNotFlashCheckingText() {
        launch().use { scenario ->
            awaitShortcutReady(scenario)
            val observed = mutableListOf<String>()
            val previousLabels = mutableSetOf<String>()
            scenario.onActivity { activity ->
                for (id in listOf(R.id.addShortcutButton, R.id.appSettingsButton)) {
                    previousLabels.add(activity.findViewById<TextView>(id).text.toString())
                    activity.findViewById<TextView>(id).doAfterTextChanged {
                        observed.add(it.toString())
                    }
                }
            }
            // Returning to the app refreshes both states without changing permissions or icons.
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            awaitShortcutReady(scenario)
            scenario.onActivity {
                assertTrue("A completed refresh must update the button states", observed.isNotEmpty())
                assertTrue("An unchanged device must keep its last labels throughout refresh",
                    observed.all { it in previousLabels })
            }
        }
    }

    @Test fun questionMarksOpenSeparateHelpWithoutLeavingTheApp() {
        launch().use {
            for ((button, title) in listOf(
                R.id.historyHelpButton to R.string.open_history,
                R.id.shortcutHelpButton to R.string.add_shortcut,
                R.id.permissionHelpButton to R.string.open_app_settings,
            )) {
                onView(withId(button)).perform(click())
                onView(withId(R.id.sheetTitle)).check(matches(isDisplayed()))
                var expectedTitle = ""
                it.onActivity { activity ->
                    expectedTitle = activity.getString(title)
                    val helpButton = activity.findViewById<View>(button)
                    assertTrue(helpButton.width >= (48 * activity.resources.displayMetrics.density).toInt())
                    assertTrue(helpButton.height >= (48 * activity.resources.displayMetrics.density).toInt())
                }
                onView(withId(R.id.sheetTitle)).check(matches(withText(expectedTitle)))
                onView(withId(R.id.sheetBody)).check(matches(isDisplayed()))
                onView(withId(R.id.closeSheetButton)).perform(click())
                onView(withId(R.id.headlineText)).check(matches(isDisplayed()))
            }
        }
    }

    @Test fun languageChoiceRecreatesUiAndRemainsSavedAcrossRelaunch() {
        // A checked radio button does not emit a selection change. Establish a
        // different starting language instead of depending on the user's choice.
        // rememberUserPreferences/restoreUserPreferences preserve that choice.
        instrumentation.runOnMainSync {
            AppLanguage.select(context, LanguageMode.ENGLISH)
            AppLanguage.prepare(context)
        }
        var expectedNight = Configuration.UI_MODE_NIGHT_NO
        launch().use { scenario ->
            scenario.onActivity {
                expectedNight = it.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            }
            awaitAppearance(scenario, "Notification History Helper", expectedNight)
            onView(withId(R.id.settingsButton)).perform(click())
            onView(withId(R.id.chineseLanguage)).check(matches(isNotChecked()))
            onView(withId(R.id.chineseLanguage)).perform(click())
            awaitAppearance(scenario, "历史通知助手", expectedNight)
            onView(withId(R.id.headlineText)).check(matches(withText("历史通知助手")))
            assertEquals(LanguageMode.CHINESE, AppLanguage.mode(context))
        }
        launch().use { scenario ->
            awaitAppearance(scenario, "历史通知助手", expectedNight)
            onView(withId(R.id.headlineText)).check(matches(withText("历史通知助手")))
            onView(withId(R.id.settingsButton)).perform(click())
            onView(withId(R.id.englishLanguage)).check(matches(isNotChecked()))
            onView(withId(R.id.englishLanguage)).perform(click())
            awaitAppearance(scenario, "Notification History Helper", expectedNight)
            onView(withId(R.id.headlineText)).check(matches(withText("Notification History Helper")))
            assertEquals(LanguageMode.ENGLISH, AppLanguage.mode(context))
        }
        launch().use { scenario ->
            awaitAppearance(scenario, "Notification History Helper", expectedNight)
            onView(withId(R.id.headlineText)).check(matches(withText("Notification History Helper")))
            assertEquals(LanguageMode.ENGLISH, AppLanguage.mode(context))
        }
    }

    @Test fun aboutExplainsTheAppAndShowsInstalledVersion() {
        launch().use {
            onView(withId(R.id.aboutButton)).perform(click())
            onView(withId(R.id.sheetTitle)).check(matches(isDisplayed()))
            val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName!!
            onView(withId(R.id.sheetBody)).check { view, error ->
                if (error != null) throw error
                assertTrue((view as TextView).text.contains(version))
                if (InstrumentationRegistry.getArguments().getString("renderAboutPreview") == "true") {
                    saveView(view.rootView, "about-preview.png")
                }
            }
        }
    }

    @Test fun themeChoiceAppliesAndPersistsWithoutChangingLanguage() {
        for ((choice, button) in listOf(
            ThemeMode.LIGHT to R.id.lightTheme,
            ThemeMode.DARK to R.id.darkTheme,
            ThemeMode.SYSTEM to R.id.systemTheme,
        )) {
            val expectedNight = when (choice) {
                ThemeMode.LIGHT -> Configuration.UI_MODE_NIGHT_NO
                ThemeMode.DARK -> Configuration.UI_MODE_NIGHT_YES
                ThemeMode.SYSTEM -> android.content.res.Resources.getSystem().configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK
            }
            launch().use { scenario ->
                var title = ""
                scenario.onActivity { title = it.getString(R.string.headline) }
                onView(withId(R.id.settingsButton)).perform(click())
                onView(withId(button)).perform(scrollTo(), click())
                awaitAppearance(scenario, title, expectedNight)
                assertEquals(choice, AppAppearance.mode(context))
                assertEquals(originalLanguage, AppLanguage.mode(context))
            }
            launch().use { scenario ->
                var title = ""
                scenario.onActivity { title = it.getString(R.string.headline) }
                awaitAppearance(scenario, title, expectedNight)
                onView(withId(R.id.settingsButton)).perform(click())
                onView(withId(button)).perform(scrollTo()).check(matches(isChecked()))
                onView(withId(R.id.closeSheetButton)).perform(scrollTo(), click())
            }
        }
    }

    @Test fun pressingEitherTargetActivatesFeedbackAcrossTheWholeCard() {
        launch().use { scenario ->
            scenario.onActivity { activity ->
                for ((rowId, targets) in listOf(
                    R.id.blueActionRow to listOf(R.id.openHistoryButton, R.id.historyHelpButton),
                    R.id.yellowActionRow to listOf(R.id.addShortcutButton, R.id.shortcutHelpButton),
                    R.id.orangeActionRow to listOf(R.id.appSettingsButton, R.id.permissionHelpButton),
                )) {
                    val row = activity.findViewById<View>(rowId)
                    val feedback = row.foreground
                    assertTrue("The whole card must own the ripple", feedback is RippleDrawable)
                    assertFalse("The card must not replace the two click targets", row.isClickable)
                    for (targetId in targets) {
                        val target = activity.findViewById<View>(targetId)
                        try {
                            target.isPressed = true
                            val bitmap = Bitmap.createBitmap(row.width, row.height, Bitmap.Config.ARGB_8888)
                            row.draw(Canvas(bitmap))
                            bitmap.recycle()
                            assertTrue("Child press must activate shared feedback",
                                feedback.state.contains(android.R.attr.state_pressed))
                            val help = activity.findViewById<View>(targets.last())
                            assertEquals(row.width, feedback.bounds.width())
                            assertEquals(row.height, feedback.bounds.height())
                            assertTrue("Feedback must extend across the question mark",
                                feedback.bounds.contains(help.left + help.width / 2, help.top + help.height / 2))
                        } finally {
                            target.isPressed = false
                        }
                        assertFalse(feedback.state.contains(android.R.attr.state_pressed))
                    }
                }
            }
        }
    }

    @Test fun shortcutButtonShowsStatusAndHelpOffersOnlyAnExplicitRetry() {
        launch().use { scenario ->
            val deadline = SystemClock.uptimeMillis() + 5000
            var statusReady = false
            while (!statusReady && SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity { activity ->
                    val button = activity.findViewById<TextView>(R.id.addShortcutButton)
                    statusReady = DeviceUiTestSupport.hasRenderedShortcutStatus(button)
                }
                if (!statusReady) SystemClock.sleep(50)
            }
            assertTrue("Shortcut status must become visible on the action itself", statusReady)
            scenario.onActivity { activity ->
                val button = activity.findViewById<TextView>(R.id.addShortcutButton)
                val status = button.text.toString().substringAfterLast('\n')
                assertTrue(listOf(R.string.shortcut_added_status, R.string.shortcut_not_added_status,
                    R.string.shortcut_pending_status, R.string.shortcut_unknown_status,
                    R.string.shortcut_unavailable_status, R.string.shortcut_permission_required).map(activity::getString).contains(status))
                assertEquals(activity.getString(R.string.add_shortcut), button.contentDescription)
                assertEquals(status, androidx.core.view.ViewCompat.getStateDescription(button))
                val settings = activity.findViewById<TextView>(R.id.appSettingsButton)
                assertTrue(listOf(R.string.shortcut_permission_allowed, R.string.shortcut_permission_denied_status,
                    R.string.shortcut_permission_status).map(activity::getString)
                    .contains(settings.text.toString().substringAfterLast('\n')))
            }
            onView(withId(R.id.shortcutHelpButton)).perform(click())
            onView(withId(R.id.sheetSecondaryAction)).check(matches(isDisplayed()))
            onView(withId(R.id.sheetSecondaryAction)).check(matches(isEnabled()))
            // Do not activate Retry: device tests must not create or remove desktop icons.
            onView(withId(R.id.closeSheetButton)).perform(click())
        }
    }

    @Test fun nightUsesUnfilledCardsAndLightUsesThreeDistinctFills() {
        instrumentation.runOnMainSync {
            for (night in listOf(false, true)) {
                val configuration = Configuration(context.resources.configuration).apply {
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                        if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                }
                val themed = ContextThemeWrapper(context.createConfigurationContext(configuration), R.style.Theme_RecallArchive)
                val layout = LayoutInflater.from(themed).inflate(R.layout.activity_main, null)
                for ((id, expected) in listOf(
                    R.id.blueActionRow to 0xFF0058B8.toInt(),
                    R.id.yellowActionRow to 0xFFFFDA00.toInt(),
                    R.id.orangeActionRow to 0xFFFF7A00.toInt(),
                )) {
                    val drawable = layout.findViewById<View>(id).background as GradientDrawable
                    assertEquals(if (night) 0xFF090B0F.toInt() else expected, drawable.color!!.defaultColor)
                }
            }
        }
    }

    @Test fun captureAppOnlyLightDarkAndLargeTextForVisualReview() {
        instrumentation.runOnMainSync {
            AppLanguage.select(context, LanguageMode.CHINESE)
            AppLanguage.prepare(context)
            AppAppearance.select(context, ThemeMode.LIGHT)
        }
        launch().use { scenario ->
            awaitAppearance(scenario, "历史通知助手", Configuration.UI_MODE_NIGHT_NO)
            scenario.onActivity { saveView(it.window.decorView, "home-zh-light.png") }
            if (InstrumentationRegistry.getArguments().getString("renderNoticePreview") == "true") {
                captureSyntheticNotice(scenario, "notice-zh-light.png")
            }
            instrumentation.runOnMainSync {
                AppLanguage.select(context, LanguageMode.ENGLISH)
                AppAppearance.select(context, ThemeMode.DARK)
            }
            awaitAppearance(scenario, "Notification History Helper", Configuration.UI_MODE_NIGHT_YES)
            onView(withId(R.id.headlineText)).check(matches(withText("Notification History Helper")))
            scenario.onActivity { saveView(it.window.decorView, "home-en-dark.png") }
            if (InstrumentationRegistry.getArguments().getString("renderNoticePreview") == "true") {
                captureSyntheticNotice(scenario, "notice-en-dark.png")
            }
        }
        instrumentation.runOnMainSync {
            // A synthetic small viewport; never changes the phone's font or display settings.
            val configuration = Configuration(context.resources.configuration).apply {
                fontScale = 1.6f
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
                setLocale(java.util.Locale.ENGLISH)
            }
            val themed = ContextThemeWrapper(context.createConfigurationContext(configuration), R.style.Theme_RecallArchive)
            val view = LayoutInflater.from(themed).inflate(R.layout.activity_main, null)
            // This detached preview has no Window; production uses the Window background.
            view.setBackgroundColor(themed.getColor(R.color.page_background))
            // Include the new status lines in the small-screen stress case. Full-size
            // status text is conservative compared with the production 0.75-size span.
            view.findViewById<TextView>(R.id.addShortcutButton).text =
                themed.getString(R.string.add_shortcut) + "\n" + themed.getString(R.string.shortcut_pending_status)
            view.findViewById<TextView>(R.id.appSettingsButton).text =
                themed.getString(R.string.open_app_settings) + "\n" + themed.getString(R.string.shortcut_permission_status)
            val density = themed.resources.displayMetrics.density
            val width = (320 * density).toInt()
            val height = (640 * density).toInt()
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, width, height)
            for (id in listOf(R.id.openHistoryButton, R.id.addShortcutButton, R.id.appSettingsButton)) {
                val label = view.findViewById<TextView>(id)
                assertNotNull(label.layout)
                for (line in 0 until label.layout.lineCount) assertEquals(0, label.layout.getEllipsisCount(line))
                assertTrue(label.layout.height <= label.height - label.compoundPaddingTop - label.compoundPaddingBottom)
            }
            saveView(view, "home-en-large-text.png")
        }
    }

    private fun awaitAppearance(scenario: ActivityScenario<MainActivity>, title: String, night: Int) {
        // Framework locale changes arrive asynchronously, independently of Espresso's idle queue.
        // Wait for observable UI state, not a fixed delay; a missing refresh must still fail.
        val deadline = SystemClock.uptimeMillis() + 5000
        var ready = false
        var observed = ""
        while (!ready && SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity { activity ->
                val actualTitle = activity.findViewById<TextView>(R.id.headlineText).text.toString()
                val actualNight = activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                observed = "title=$actualTitle, resolvedTitle=${activity.getString(R.string.headline)}, night=$actualNight, locales=" +
                    activity.resources.configuration.locales.toLanguageTags()
                ready = actualTitle == title && actualNight == night
            }
            if (!ready) SystemClock.sleep(50)
        }
        assertTrue("Appearance did not settle: $observed", ready)
    }

    private fun captureSyntheticNotice(scenario: ActivityScenario<MainActivity>, name: String) {
        awaitShortcutReady(scenario)
        scenario.onActivity { activity ->
            // Preview fixture only: never change a permission to obtain this state.
            MainActivity::class.java.getDeclaredMethod("showShortcutMessage", Integer::class.java)
                .apply { isAccessible = true }.invoke(activity, R.string.shortcut_permission_denied)
        }
        instrumentation.waitForIdleSync()
        scenario.onActivity { saveView(it.window.decorView, name) }
        onView(withId(R.id.closeShortcutMessageButton)).perform(scrollTo(), click())
    }

    private fun saveView(view: View, name: String) {
        assertTrue("View must be laid out", view.width > 0 && view.height > 0)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        // Only this app's View tree is rendered: no screenshots of system notifications or other apps.
        // App-private cache is readable with run-as on debug builds, without granting
        // storage permissions or relying on the shell's scoped-storage mount namespace.
        val directory = File(context.cacheDir, "ui-review")
        check(directory.exists() || directory.mkdirs())
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
