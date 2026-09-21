package io.github.captainrainbow.notificationhistoryhelper

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CiSafetyTest {
    @Test
    fun externalActionsArePinnedToFullCommitShas() {
        val workflow = File("../.github/workflows/android.yml").readText()
        val actionReferences = Regex("""(?m)^\s*uses:\s*([^\s#]+)""")
            .findAll(workflow)
            .map { it.groupValues[1] }
            .toList()

        assertEquals(2, actionReferences.size)
        actionReferences.forEach { reference ->
            assertTrue(
                "$reference must use a full 40-character commit SHA",
                reference.substringAfter('@', missingDelimiterValue = "")
                    .matches(Regex("[0-9a-f]{40}")),
            )
        }
    }
}
