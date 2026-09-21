package io.github.captainrainbow.notificationhistoryhelper

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element

class PortraitManifestTest {
    @Test fun allAppOwnedScreensRequestPortraitWithoutLockingSystemScreens() {
        val document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(File("src/main/AndroidManifest.xml"))
        val activities = document.getElementsByTagName("activity")
        for (i in 0 until activities.length) {
            val activity = activities.item(i) as Element
            assertEquals(activity.getAttributeNS("http://schemas.android.com/apk/res/android", "name"),
                "portrait", activity.getAttributeNS("http://schemas.android.com/apk/res/android", "screenOrientation"))
        }
    }
}
