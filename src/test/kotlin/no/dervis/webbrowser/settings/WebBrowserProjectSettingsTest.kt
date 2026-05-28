package no.dervis.webbrowser.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import no.dervis.webbrowser.domain.ReadinessMode

/**
 * Platform-framework tests for the project-level settings service.
 */
class WebBrowserProjectSettingsTest : BasePlatformTestCase() {

    private lateinit var settings: WebBrowserProjectSettings

    override fun setUp() {
        super.setUp()
        settings = WebBrowserProjectSettings.getInstance(project)
        settings.loadState(WebBrowserProjectSettings.State())
    }

    override fun tearDown() {
        try {
            settings.loadState(WebBrowserProjectSettings.State())
        } finally {
            super.tearDown()
        }
    }

    fun testDefaults() {
        assertEquals("", settings.watchPath)
        assertFalse(settings.openOnRun)
        assertEquals("", settings.runConfigName)
        assertEquals("", settings.openUrl)
        assertEquals(ReadinessMode.DEFAULT, settings.readiness)
        assertEquals(WebBrowserProjectSettings.DEFAULT_WAIT_SECONDS, settings.readinessSeconds)
    }

    fun testReadinessEnumRoundtripsThroughStorageId() {
        ReadinessMode.entries.forEach { mode ->
            settings.readiness = mode
            assertEquals(mode, settings.readiness)
        }
    }

    fun testReadinessSecondsClampsToMin() {
        settings.readinessSeconds = -5
        assertEquals(WebBrowserProjectSettings.MIN_WAIT, settings.readinessSeconds)
    }

    fun testReadinessSecondsClampsToMax() {
        settings.readinessSeconds = Int.MAX_VALUE
        assertEquals(WebBrowserProjectSettings.MAX_WAIT, settings.readinessSeconds)
    }

    fun testOpenOnRunPolicyReflectsCurrentValues() {
        settings.openOnRun = true
        settings.runConfigName = "  DevServer  "
        settings.readiness = ReadinessMode.AFTER_DELAY
        settings.readinessSeconds = 12

        val policy = settings.openOnRunPolicy()

        assertTrue(policy.enabled)
        assertEquals("DevServer", policy.runConfigName) // trimmed
        assertEquals(ReadinessMode.AFTER_DELAY, policy.readiness)
        assertEquals(12, policy.waitSeconds)
    }

    fun testOpenOnRunPolicyForDisabledTrigger() {
        val policy = settings.openOnRunPolicy()
        assertFalse(policy.enabled)
        assertFalse(policy.isTriggeredBy("Anything"))
    }

    fun testStateSaveAndLoadRoundtrip() {
        settings.watchPath = "/p/src"
        settings.openOnRun = true
        settings.runConfigName = "DevServer"
        settings.openUrl = "http://localhost:4200"
        settings.readiness = ReadinessMode.AFTER_DELAY
        settings.readinessSeconds = 45

        val saved = settings.state

        val reloaded = WebBrowserProjectSettings()
        reloaded.loadState(saved)

        assertEquals("/p/src", reloaded.watchPath)
        assertTrue(reloaded.openOnRun)
        assertEquals("DevServer", reloaded.runConfigName)
        assertEquals("http://localhost:4200", reloaded.openUrl)
        assertEquals(ReadinessMode.AFTER_DELAY, reloaded.readiness)
        assertEquals(45, reloaded.readinessSeconds)
    }

    fun testUnknownStorageIdResolvesToDefaultReadiness() {
        val brokenState = WebBrowserProjectSettings.State(readinessMode = "BOGUS")
        settings.loadState(brokenState)
        assertEquals(ReadinessMode.DEFAULT, settings.readiness)
    }
}
