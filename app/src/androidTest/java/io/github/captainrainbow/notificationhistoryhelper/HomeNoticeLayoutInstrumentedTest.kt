package io.github.captainrainbow.notificationhistoryhelper

import android.content.res.Configuration
import android.graphics.Rect
import android.os.SystemClock
import android.text.Spanned
import android.text.style.ClickableSpan
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.action.GeneralClickAction
import androidx.test.espresso.action.Tap
import androidx.test.espresso.action.Press
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class HomeNoticeLayoutInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val actionIds = listOf(R.id.blueActionRow, R.id.yellowActionRow, R.id.orangeActionRow)
    private val textIds = listOf(R.id.statusText, R.id.shortcutStatusText, R.id.errorText)

    @Test fun noticesUseTheSpaceAboveActionsWithoutMovingThem() {
        instrumentation.runOnMainSync {
            for (locale in listOf(Locale.SIMPLIFIED_CHINESE, Locale.ENGLISH)) {
                for (night in listOf(false, true)) {
                    val root = inflate(locale, night, 1f)
                    val density = root.resources.displayMetrics.density
                    val width = (400 * density).toInt()
                    val height = (840 * density).toInt()
                    val group = root.findViewById<View>(R.id.shortcutFeedbackGroup)
                    textIds.forEach { root.findViewById<View>(it).visibility = View.GONE }
                    group.visibility = View.GONE
                    measure(root, width, height)
                    val baseline = actionIds.map { bounds(root, it) }
                    for ((id, message) in listOf(
                        R.id.shortcutStatusText to R.string.shortcut_permission_denied,
                        R.id.statusText to R.string.status_unsupported,
                        R.id.errorText to R.string.app_settings_failed,
                    )) {
                        val text = root.findViewById<TextView>(id)
                        text.text = root.context.getString(message) + " " + root.context.getString(R.string.notice_help)
                        text.visibility = View.VISIBLE
                        group.visibility = View.VISIBLE
                        measure(root, width, height)
                        assertEquals("Showing a notice must not move the actions: $locale/$night/$id",
                            baseline, actionIds.map { bounds(root, it) })
                        assertTrue("Every notice belongs above the blue button",
                            bounds(root, id).bottom <= bounds(root, R.id.blueActionRow).top)
                        assertTrue(text.height >= text.layout.height + text.compoundPaddingTop + text.compoundPaddingBottom)
                        val card = bounds(root, R.id.shortcutFeedbackGroup)
                        val blue = bounds(root, R.id.blueActionRow)
                        val yellow = bounds(root, R.id.yellowActionRow)
                        assertEquals("Notice and actions must share the same left edge", blue.left, card.left)
                        assertEquals("Notice and actions must share the same right edge", blue.right, card.right)
                        assertEquals("Notice gap must match the gap between actions",
                            yellow.top - blue.bottom, blue.top - card.bottom)
                        assertTrue("Notice must have a visible card background", group.background != null)
                        assertTrue("Notice needs a button-sized minimum height", card.height() >= (100 * density).toInt())
                        val close = bounds(root, R.id.closeShortcutMessageButton)
                        val question = bounds(root, R.id.historyHelpButton)
                        assertEquals("Close target must align with the question marks", question.centerX(), close.centerX())
                        assertTrue("Close target must be vertically centered", kotlin.math.abs(card.centerY() - close.centerY()) <= 1)
                        assertFalse("Notice background is not an action", group.isClickable || group.isLongClickable)
                        text.visibility = View.GONE
                        group.visibility = View.GONE
                        measure(root, width, height)
                        assertEquals("Closing a notice must not move the actions", baseline,
                            actionIds.map { bounds(root, it) })
                    }
                }
            }
        }
    }

    @Test fun largeTextNoticeRemainsReadableAboveScrollableActions() {
        instrumentation.runOnMainSync {
            val root = inflate(Locale.ENGLISH, false, 1.6f)
            val density = root.resources.displayMetrics.density
            val text = root.findViewById<TextView>(R.id.shortcutStatusText)
            text.text = root.context.getString(R.string.shortcut_permission_denied) + " " +
                root.context.getString(R.string.notice_help)
            text.visibility = View.VISIBLE
            root.findViewById<View>(R.id.shortcutFeedbackGroup).visibility = View.VISIBLE
            measure(root, (320 * density).toInt(), (640 * density).toInt())
            assertTrue(bounds(root, R.id.shortcutStatusText).bottom <= bounds(root, R.id.blueActionRow).top)
            assertTrue("Small screens must scroll instead of covering the actions",
                (root as ScrollView).getChildAt(0).height > root.height)
            assertTrue(text.height >= text.layout.height + text.compoundPaddingTop + text.compoundPaddingBottom)
            for (line in 0 until text.layout.lineCount) assertEquals(0, text.layout.getEllipsisCount(line))
            val close = root.findViewById<View>(R.id.closeShortcutMessageButton)
            assertTrue(close.width >= (48 * density).toInt())
            assertTrue(close.height >= (48 * density).toInt())
        }
    }

    @Test fun dismissingExistingPermissionNoticeKeepsAllActionsInPlace() {
        // Only use the user's current state; never change permissions or desktop icons.
        assumeTrue(AndroidShortcutPermissionGateway(context).inspect() == ShortcutPermissionState.DENIED)
        assumeTrue(!AndroidHistoryShortcutGateway(context).isPinned())
        DeviceUiTestSupport.bringAppToForeground()
        ActivityScenario.launch<MainActivity>(DeviceUiTestSupport.scenarioIntent()).use { scenario ->
            val deadline = SystemClock.uptimeMillis() + 5000
            var ready = false
            while (!ready && SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity { ready = DeviceUiTestSupport.hasRenderedShortcutStatus(it.findViewById(R.id.addShortcutButton)) }
                if (!ready) SystemClock.sleep(50)
            }
            assertTrue("Status must finish loading", ready)
            var baseline: List<Rect> = emptyList()
            scenario.onActivity {
                val root = it.findViewById<ViewGroup>(R.id.homeScroll)
                assertEquals("Denied permission alone must not open a notice", View.GONE,
                    root.findViewById<View>(R.id.shortcutFeedbackGroup).visibility)
                baseline = actionIds.map { id -> bounds(root, id) }
            }
            // Known denial is checked by the app before requestPinShortcut: creates no icon.
            onView(withId(R.id.addShortcutButton)).perform(click())
            waitForNotice(scenario, View.VISIBLE)
            onView(withId(R.id.closeShortcutMessageButton)).perform(click())
            scenario.onActivity {
                val root = it.findViewById<ViewGroup>(R.id.homeScroll)
                assertEquals(View.GONE, root.findViewById<View>(R.id.shortcutFeedbackGroup).visibility)
                assertEquals(baseline, actionIds.map { id -> bounds(root, id) })
            }
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            waitForShortcutStatus(scenario)
            waitForNotice(scenario, View.GONE)
        }
    }

    @Test fun reopeningDoesNotRestoreAnEarlierUnconfirmedAttempt() {
        DeviceUiTestSupport.bringAppToForeground()
        ActivityScenario.launch<MainActivity>(DeviceUiTestSupport.scenarioIntent()).use { scenario ->
            waitForShortcutStatus(scenario)
            waitForNotice(scenario, View.GONE)
            scenario.onActivity { activity ->
                // A deterministic unconfirmed result, even on an emulator with an old icon.
                // No actual request is issued and no existing shortcut is removed.
                val notPinned = object : HistoryShortcutGateway {
                    override fun isSupported() = true
                    override fun isPinned() = false
                    override fun requestPin(): Boolean = error("This test must not request an icon")
                }
                MainActivity::class.java.getDeclaredField("shortcuts").apply { isAccessible = true }
                    .set(activity, HistoryShortcutController(AndroidNotificationHistoryGateway(activity), notPinned))
                MainActivity::class.java.getDeclaredField("lastPinOutcome").apply { isAccessible = true }
                    .set(activity, PinOutcome.CONFIRM_ON_LAUNCHER)
                MainActivity::class.java.getDeclaredMethod("showShortcutMessage", Integer::class.java)
                    .apply { isAccessible = true }.invoke(activity, R.string.shortcut_not_confirmed)
            }
            waitForNotice(scenario, View.VISIBLE)
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            waitForShortcutStatus(scenario)
            waitForNotice(scenario, View.GONE)
            scenario.onActivity { activity ->
                MainActivity::class.java.getDeclaredField("lastPinOutcome").apply { isAccessible = true }
                    .set(activity, PinOutcome.CONFIRM_ON_LAUNCHER)
                MainActivity::class.java.getDeclaredMethod("showShortcutMessage", Integer::class.java)
                    .apply { isAccessible = true }.invoke(activity, R.string.shortcut_not_confirmed)
            }
            scenario.recreate()
            waitForShortcutStatus(scenario)
            waitForNotice(scenario, View.GONE)
        }
    }

    @Test fun passiveUnsupportedHistoryCheckAlsoKeepsNoticeHidden() {
        DeviceUiTestSupport.bringAppToForeground()
        ActivityScenario.launch<MainActivity>(DeviceUiTestSupport.scenarioIntent()).use { scenario ->
            waitForShortcutStatus(scenario)
            scenario.onActivity { activity ->
                MainActivity::class.java.getDeclaredMethod("render", SupportState::class.java, OpenOutcome::class.java)
                    .apply { isAccessible = true }.invoke(activity, SupportState.UNSUPPORTED, null)
                assertEquals(View.GONE, activity.findViewById<View>(R.id.shortcutFeedbackGroup).visibility)
            }
        }
    }

    @Test fun inlineHelpOpensTheRightSheetButNoticeBodyDoesNothing() {
        DeviceUiTestSupport.bringAppToForeground()
        ActivityScenario.launch<MainActivity>(DeviceUiTestSupport.scenarioIntent()).use { scenario ->
            val deadline = SystemClock.uptimeMillis() + 5000
            var ready = false
            while (!ready && SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity { ready = DeviceUiTestSupport.hasRenderedShortcutStatus(it.findViewById(R.id.addShortcutButton)) }
                if (!ready) SystemClock.sleep(50)
            }
            assertTrue("Status must finish loading", ready)
            // Synthetic failures use the existing renderer: no permission, settings,
            // notification or home-screen changes are needed to exercise the card.
            for ((settingsNotice, expectedTitle) in listOf(
                false to R.string.add_shortcut,
                true to R.string.open_app_settings,
            )) {
                val textId = R.id.shortcutStatusText
                var title = ""
                var linkStart = -1
                scenario.onActivity { activity ->
                    val shortcut = MainActivity::class.java.getDeclaredMethod("showShortcutMessage", Integer::class.java)
                        .apply { isAccessible = true }
                    MainActivity::class.java.getDeclaredField("checkedSettings").apply { isAccessible = true }
                        .setBoolean(activity, settingsNotice)
                    shortcut.invoke(activity, R.string.shortcut_permission_denied)
                    title = activity.getString(expectedTitle)
                    val text = activity.findViewById<TextView>(textId)
                    val spans = text.text as? Spanned
                    assertNotNull("Notice must contain an inline help link", spans)
                    val link = spans!!.getSpans(0, spans.length, ClickableSpan::class.java).singleOrNull()
                    assertNotNull("Only the help phrase should be clickable", link)
                    linkStart = spans.getSpanStart(link)
                    assertTrue("Explanation must remain outside the link", linkStart > 0)
                    assertEquals("Help must be the sentence suffix", spans.length, spans.getSpanEnd(link))
                }
                onView(withId(textId)).perform(scrollTo(), tapTextOffset(1))
                onView(withId(R.id.sheetTitle)).check(doesNotExist())
                onView(withId(textId)).perform(tapTextOffset(linkStart + 1))
                onView(withId(R.id.sheetTitle)).check(matches(withText(title)))
                onView(withId(R.id.closeSheetButton)).perform(click())
                onView(withId(R.id.closeShortcutMessageButton)).perform(scrollTo(), click())
                scenario.onActivity { activity ->
                    assertEquals(View.GONE, activity.findViewById<View>(R.id.shortcutFeedbackGroup).visibility)
                }
            }
        }
    }

    private fun waitForShortcutStatus(scenario: ActivityScenario<MainActivity>) {
        val deadline = SystemClock.uptimeMillis() + 5000
        var ready = false
        while (!ready && SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity { ready = DeviceUiTestSupport.hasRenderedShortcutStatus(it.findViewById(R.id.addShortcutButton)) }
            if (!ready) SystemClock.sleep(50)
        }
        assertTrue("Status must finish loading", ready)
    }

    private fun waitForNotice(scenario: ActivityScenario<MainActivity>, visibility: Int) {
        val deadline = SystemClock.uptimeMillis() + 5000
        var actual = -1
        while (actual != visibility && SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity { actual = it.findViewById<View>(R.id.shortcutFeedbackGroup).visibility }
            if (actual != visibility) SystemClock.sleep(50)
        }
        assertEquals("Notice visibility", visibility, actual)
    }

    private fun tapTextOffset(offset: Int) = GeneralClickAction(Tap.SINGLE, { view ->
        val text = view as TextView
        val line = text.layout.getLineForOffset(offset)
        val location = IntArray(2).also(text::getLocationOnScreen)
        floatArrayOf(
            location[0] + text.totalPaddingLeft - text.scrollX + text.layout.getPrimaryHorizontal(offset) + 1f,
            location[1] + text.totalPaddingTop - text.scrollY +
                (text.layout.getLineTop(line) + text.layout.getLineBottom(line)) / 2f,
        )
    }, Press.FINGER, android.view.InputDevice.SOURCE_TOUCHSCREEN, android.view.MotionEvent.BUTTON_PRIMARY)

    private fun inflate(locale: Locale, night: Boolean, fontScale: Float): ViewGroup {
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(locale)
            this.fontScale = fontScale
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        val themed = ContextThemeWrapper(context.createConfigurationContext(configuration), R.style.Theme_RecallArchive)
        return LayoutInflater.from(themed).inflate(R.layout.activity_main, null) as ViewGroup
    }

    private fun measure(root: View, width: Int, height: Int) {
        root.requestLayout()
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, width, height)
    }

    private fun bounds(root: ViewGroup, id: Int): Rect {
        val view = root.findViewById<View>(id)
        val rectangle = Rect(0, 0, view.width, view.height)
        root.offsetDescendantRectToMyCoords(view, rectangle)
        return rectangle
    }
}
