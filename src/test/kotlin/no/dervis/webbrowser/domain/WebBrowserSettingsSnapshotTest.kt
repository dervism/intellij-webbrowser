package no.dervis.webbrowser.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class WebBrowserSettingsSnapshotTest {

    private val sample = WebBrowserSettingsSnapshot(
        homeUrl = "http://localhost:3000",
        watchExtensions = "html,css,ts",
        watchPath = "/p/src",
        openOnRun = true,
        runConfigName = "dev",
        openUrl = "",
        readiness = ReadinessMode.WHEN_REACHABLE,
        readinessSeconds = 30,
    )

    @Test
    fun `two snapshots with identical fields are equal`() {
        val twin = sample.copy()
        assertEquals(sample, twin)
        assertEquals(sample.hashCode(), twin.hashCode())
    }

    @Test
    fun `changing any single field breaks equality`() {
        // Exhaustive single-field diff — this is the whole point of using the
        // snapshot for isModified.
        assertNotEquals(sample, sample.copy(homeUrl = "http://localhost:4321"))
        assertNotEquals(sample, sample.copy(watchExtensions = "vue"))
        assertNotEquals(sample, sample.copy(watchPath = ""))
        assertNotEquals(sample, sample.copy(openOnRun = false))
        assertNotEquals(sample, sample.copy(runConfigName = "build"))
        assertNotEquals(sample, sample.copy(openUrl = "http://x"))
        assertNotEquals(sample, sample.copy(readiness = ReadinessMode.AFTER_DELAY))
        assertNotEquals(sample, sample.copy(readinessSeconds = 15))
    }

    @Test
    fun `copy with no overrides yields an equal snapshot`() {
        assertEquals(sample, sample.copy())
    }

    @Test
    fun `accessors expose every constructor field`() {
        assertEquals("http://localhost:3000", sample.homeUrl)
        assertEquals("html,css,ts", sample.watchExtensions)
        assertEquals("/p/src", sample.watchPath)
        assertTrue(sample.openOnRun)
        assertEquals("dev", sample.runConfigName)
        assertEquals("", sample.openUrl)
        assertEquals(ReadinessMode.WHEN_REACHABLE, sample.readiness)
        assertEquals(30, sample.readinessSeconds)
    }
}
