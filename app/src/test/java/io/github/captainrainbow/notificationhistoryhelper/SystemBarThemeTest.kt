package io.github.captainrainbow.notificationhistoryhelper

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class SystemBarThemeTest {
    @Test fun windowOwnsThePageBackgroundWithoutAnOpaqueDuplicateInTheLayout() {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res/layout/activity_main.xml"))
        assertEquals("The window already draws page_background", "",
            document.documentElement.getAttribute("android:background"))
        val theme = File("src/main/res/values/themes.xml").readText()
        assertTrue(theme.contains("<item name=\"android:windowBackground\">@color/page_background</item>"))
    }

    @Test fun statusBarNeverInheritsTheAccentBehindLightOrDarkIcons() {
        for (folder in listOf("values", "values-night")) {
            // Night uses the base DayNight theme unless it has an explicit override.
            val themeFile = File("src/main/res/$folder/themes.xml").takeIf(File::exists)
                ?: File("src/main/res/values/themes.xml")
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(themeFile)
            val items = document.getElementsByTagName("item")
            val statusBarColor = (0 until items.length).map { items.item(it) as Element }
                .firstOrNull { it.getAttribute("name") == "android:statusBarColor" }?.textContent
            assertEquals(folder, "@android:color/transparent", statusBarColor)
        }
    }
}
