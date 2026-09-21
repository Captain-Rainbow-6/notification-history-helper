package io.github.captainrainbow.notificationhistoryhelper

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element

class ShortcutIconResourceTest {
    @Test fun shortcutReusesOriginalArtworkAndHasItsOwnBadge() {
        val icon = File("src/main/res/drawable/ic_shortcut.xml")
        assertTrue("Shortcut must have a separate icon resource", icon.isFile)
        val root = parse(icon)
        assertEquals("adaptive-icon", root.tagName)
        assertEquals("@drawable/ic_launcher_background", attribute(root, "background"))
        assertEquals("@drawable/ic_shortcut_foreground", attribute(root, "foreground"))
        val layers = parse(File("src/main/res/drawable/ic_shortcut_foreground.xml"))
        val items = layers.getElementsByTagName("item")
        assertEquals(2, items.length)
        assertEquals(1, (items.item(0) as Element).getElementsByTagName("vector").length)
        assertEquals("@drawable/ic_shortcut_badge", androidAttribute(items.item(1) as Element, "drawable"))
        assertEquals("@drawable/ic_launcher_foreground", attribute(parse(File("src/main/res/drawable/ic_launcher.xml")), "foreground"))
        // ShortcutInfo has no public icon getter. Check the resource wiring here instead.
        val gateway = File("src/main/java/io/github/captainrainbow/notificationhistoryhelper/AndroidHistoryShortcutGateway.kt").readText()
        assertTrue(gateway.contains("Icon.createWithResource(context, R.drawable.ic_shortcut)"))
    }

    private fun parse(file: File): Element = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
    }.newDocumentBuilder().parse(file).documentElement

    private fun attribute(root: Element, tag: String) =
        androidAttribute(root.getElementsByTagName(tag).item(0) as Element, "drawable")

    private fun androidAttribute(element: Element, name: String) =
        element.getAttributeNS("http://schemas.android.com/apk/res/android", name)
}
