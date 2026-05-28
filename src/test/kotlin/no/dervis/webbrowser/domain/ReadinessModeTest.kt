package no.dervis.webbrowser.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReadinessModeTest {

    @Test
    fun `fromStorageId returns the matching enum value`() {
        assertEquals(ReadinessMode.WHEN_REACHABLE, ReadinessMode.fromStorageId("REACHABLE"))
        assertEquals(ReadinessMode.AFTER_DELAY, ReadinessMode.fromStorageId("DELAY"))
        assertEquals(ReadinessMode.ON_LAUNCH, ReadinessMode.fromStorageId("ONLAUNCH"))
    }

    @Test
    fun `fromStorageId returns DEFAULT for unknown ids`() {
        assertEquals(ReadinessMode.DEFAULT, ReadinessMode.fromStorageId("BOGUS"))
        assertEquals(ReadinessMode.DEFAULT, ReadinessMode.fromStorageId(""))
        assertEquals(ReadinessMode.DEFAULT, ReadinessMode.fromStorageId("reachable")) // case-sensitive
    }

    @Test
    fun `DEFAULT is WHEN_REACHABLE`() {
        assertEquals(ReadinessMode.WHEN_REACHABLE, ReadinessMode.DEFAULT)
    }

    @Test
    fun `all storage ids are unique`() {
        val ids = ReadinessMode.entries.map { it.storageId }
        assertEquals(ids.size, ids.toSet().size, "duplicate storageId among ReadinessMode entries")
    }

    @Test
    fun `all display names are non-blank`() {
        ReadinessMode.entries.forEach {
            assertTrue(it.displayName.isNotBlank(), "${it.name} has a blank displayName")
        }
    }

    @Test
    fun `round-trip preserves the enum value`() {
        ReadinessMode.entries.forEach { mode ->
            assertEquals(mode, ReadinessMode.fromStorageId(mode.storageId))
        }
    }
}
