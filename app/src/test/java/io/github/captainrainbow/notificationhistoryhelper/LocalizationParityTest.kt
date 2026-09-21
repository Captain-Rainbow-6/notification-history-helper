package io.github.captainrainbow.notificationhistoryhelper

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalizationParityTest {
    @Test
    fun chineseAndEnglishResourcesHaveTheSameKeys() {
        val english = resourceNames(File("src/main/res/values/strings.xml"))
        val chinese = resourceNames(File("src/main/res/values-zh-rCN/strings.xml"))

        assertEquals(english, chinese)
    }

    private fun resourceNames(file: File): Set<String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val strings = document.getElementsByTagName("string")
        return buildSet {
            for (index in 0 until strings.length) {
                add(strings.item(index).attributes.getNamedItem("name").nodeValue)
            }
        }
    }
}
