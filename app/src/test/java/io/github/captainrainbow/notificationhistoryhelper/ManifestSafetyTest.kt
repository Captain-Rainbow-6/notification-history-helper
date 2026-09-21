package io.github.captainrainbow.notificationhistoryhelper

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element

class ManifestSafetyTest {
    private val backupDomains = setOf(
        "root",
        "file",
        "database",
        "sharedpref",
        "external",
        "device_root",
        "device_file",
        "device_database",
        "device_sharedpref",
    )

    @Test
    fun manifestExposesNoPermissionsAndDisablesCleartextTraffic() {
        val document = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }.newDocumentBuilder().parse(File("src/main/AndroidManifest.xml"))

        assertEquals(0, document.getElementsByTagName("uses-permission").length)

        val application = document.getElementsByTagName("application").item(0)
        assertEquals("false", androidAttribute(application as Element, "usesCleartextTraffic"))
    }

    @Test
    fun manifestLimitsDiscoveryToRequiredSystemPages() {
        val document = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }.newDocumentBuilder().parse(File("src/main/AndroidManifest.xml"))

        val queries = document.getElementsByTagName("queries")
        assertEquals(1, queries.length)

        val actions = (queries.item(0) as Element).getElementsByTagName("action")
        val packages = (queries.item(0) as Element).getElementsByTagName("package")
        assertEquals(1, packages.length)
        assertEquals("com.miui.securitycenter", androidAttribute(packages.item(0) as Element, "name"))
        val actionNames = buildSet {
            for (index in 0 until actions.length) {
                add(
                    actions.item(index).attributes.getNamedItemNS(
                        "http://schemas.android.com/apk/res/android",
                        "name",
                    ).nodeValue,
                )
            }
        }
        assertEquals(setOf(
            "android.settings.NOTIFICATION_HISTORY",
            "android.settings.APPLICATION_DETAILS_SETTINGS",
        ), actionNames)
    }

    @Test
    fun manifestExcludesAppDataFromCloudBackupAndDeviceTransfer() {
        val document = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }.newDocumentBuilder().parse(File("src/main/AndroidManifest.xml"))
        val application = document.getElementsByTagName("application").item(0) as Element

        assertEquals("@xml/backup_rules", androidAttribute(application, "fullBackupContent"))
        assertEquals("@xml/data_extraction_rules", androidAttribute(application, "dataExtractionRules"))

        val legacyRules = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res/xml/backup_rules.xml"))
        assertCompleteBackupExclusions(legacyRules.documentElement as Element)

        val modernRules = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res/xml/data_extraction_rules.xml"))
        val cloudBackup = modernRules.getElementsByTagName("cloud-backup").item(0) as Element
        val deviceTransfer = modernRules.getElementsByTagName("device-transfer").item(0) as Element
        assertCompleteBackupExclusions(cloudBackup)
        assertCompleteBackupExclusions(deviceTransfer)
    }

    private fun assertCompleteBackupExclusions(parent: Element) {
        val exclusions = parent.getElementsByTagName("exclude")
        assertEquals(backupDomains.size, exclusions.length)
        val domains = buildSet {
            for (index in 0 until exclusions.length) {
                val exclusion = exclusions.item(index) as Element
                assertEquals(".", exclusion.getAttribute("path"))
                add(exclusion.getAttribute("domain"))
            }
        }
        assertEquals(backupDomains, domains)
    }

    private fun androidAttribute(element: Element, name: String): String? =
        element.attributes.getNamedItemNS(
            "http://schemas.android.com/apk/res/android",
            name,
        )?.nodeValue
}
