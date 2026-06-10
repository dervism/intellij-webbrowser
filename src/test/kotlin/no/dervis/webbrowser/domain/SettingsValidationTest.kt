package no.dervis.webbrowser.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsValidationTest {

    private fun snapshot(
        homeUrl: String = "http://localhost:3000",
        projectHomeUrl: String = "",
        watchExtensions: String = "html,css",
        watchPath: String = "",
        watchPatterns: String = "",
        openOnRun: Boolean = false,
        runConfigName: String = "",
        openUrl: String = "",
        readiness: ReadinessMode = ReadinessMode.DEFAULT,
        readinessSeconds: Int = 30,
    ) = WebBrowserSettingsSnapshot(
        homeUrl, projectHomeUrl, watchExtensions, watchPath, watchPatterns,
        openOnRun, runConfigName, openUrl, readiness, readinessSeconds,
    )

    @Test
    fun `a sensible snapshot has no issues`() {
        assertTrue(SettingsValidation.validate(snapshot()).isEmpty())
    }

    @Test
    fun `empty homeUrl is flagged as required`() {
        val issues = SettingsValidation.validate(snapshot(homeUrl = ""))
        assertEquals(1, issues.size)
        assertEquals(SettingsValidation.Field.HOME_URL, issues[0].field)
    }

    @Test
    fun `whitespace homeUrl is flagged as required`() {
        val issues = SettingsValidation.validate(snapshot(homeUrl = "   "))
        assertEquals(1, issues.size)
        assertEquals(SettingsValidation.Field.HOME_URL, issues[0].field)
    }

    @Test
    fun `blank projectHomeUrl is allowed (means use default)`() {
        assertTrue(SettingsValidation.validate(snapshot(projectHomeUrl = "")).isEmpty())
        assertTrue(SettingsValidation.validate(snapshot(projectHomeUrl = "   ")).isEmpty())
    }

    @Test
    fun `blank openUrl is allowed`() {
        assertTrue(SettingsValidation.validate(snapshot(openUrl = "")).isEmpty())
    }

    @Test
    fun `unparseable watchPatterns is flagged`() {
        val issues = SettingsValidation.validate(snapshot(watchPatterns = "[unbalanced"))
        assertEquals(1, issues.size)
        assertEquals(SettingsValidation.Field.WATCH_PATTERNS, issues[0].field)
    }

    @Test
    fun `mixed valid and bad pattern lines is fine when at least one compiles`() {
        // The validator only flags the all-bad case — partial typos in the
        // textarea shouldn't block Save.
        assertTrue(
            SettingsValidation.validate(
                snapshot(watchPatterns = "/project/src/**/*.ts\n[bad"),
            ).isEmpty(),
        )
    }

    @Test
    fun `localhost-with-port-only homeUrl is accepted`() {
        // Url.parse defaults the scheme to http://, so bare host:port works
        // exactly the way it does in the address bar.
        assertTrue(SettingsValidation.validate(snapshot(homeUrl = "localhost:5173")).isEmpty())
    }

    @Test
    fun `every input field can produce an issue independently`() {
        val issues = SettingsValidation.validate(
            snapshot(homeUrl = "", watchPatterns = "[bad"),
        )
        val fields = issues.map { it.field }.toSet()
        assertTrue(SettingsValidation.Field.HOME_URL in fields)
        assertTrue(SettingsValidation.Field.WATCH_PATTERNS in fields)
    }
}
