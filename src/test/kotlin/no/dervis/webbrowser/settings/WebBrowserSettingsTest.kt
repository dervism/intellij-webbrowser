package no.dervis.webbrowser.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Platform-framework tests for the application-level settings service. Uses
 * BasePlatformTestCase so the IntelliJ Application and service container are
 * available; [setUp]/[tearDown] reset state so tests don't bleed into each other.
 */
class WebBrowserSettingsTest : BasePlatformTestCase() {

    private lateinit var settings: WebBrowserSettings

    override fun setUp() {
        super.setUp()
        settings = WebBrowserSettings.getInstance()
        settings.loadState(WebBrowserSettings.State())
    }

    override fun tearDown() {
        try {
            settings.loadState(WebBrowserSettings.State())
        } finally {
            super.tearDown()
        }
    }

    fun testDefaultHomeUrl() {
        assertEquals(WebBrowserSettings.DEFAULT_HOME_URL, settings.homeUrl)
    }

    fun testBlankHomeUrlFallsBackToDefault() {
        settings.homeUrl = ""
        assertEquals(WebBrowserSettings.DEFAULT_HOME_URL, settings.homeUrl)
    }

    fun testSettingHomeUrlPersists() {
        settings.homeUrl = "http://localhost:4200"
        assertEquals("http://localhost:4200", settings.homeUrl)
    }

    fun testDefaultReloadInterval() {
        assertEquals(WebBrowserSettings.DEFAULT_INTERVAL_SECONDS, settings.reloadIntervalSeconds)
    }

    fun testReloadIntervalClampsToMin() {
        settings.reloadIntervalSeconds = -10
        assertEquals(WebBrowserSettings.MIN_INTERVAL, settings.reloadIntervalSeconds)
    }

    fun testReloadIntervalClampsToMax() {
        settings.reloadIntervalSeconds = Int.MAX_VALUE
        assertEquals(WebBrowserSettings.MAX_INTERVAL, settings.reloadIntervalSeconds)
    }

    fun testReloadOnSavePersists() {
        assertFalse(settings.reloadOnSave)
        settings.reloadOnSave = true
        assertTrue(settings.reloadOnSave)
    }

    fun testAutoRefreshPersists() {
        assertFalse(settings.autoRefresh)
        settings.autoRefresh = true
        assertTrue(settings.autoRefresh)
    }

    fun testWatchExtensionsDefaultIncludesCommonWebExtensions() {
        assertTrue(settings.watchExtensions.contains("html"))
        assertTrue(settings.watchExtensions.contains("css"))
        assertTrue(settings.watchExtensions.contains("ts"))
    }

    fun testStateSaveAndLoadRoundtrip() {
        settings.homeUrl = "http://localhost:8080"
        settings.reloadIntervalSeconds = 10
        settings.reloadOnSave = true
        settings.autoRefresh = true
        settings.watchExtensions = "vue,svelte"

        val saved = settings.state

        val reloaded = WebBrowserSettings()
        reloaded.loadState(saved)

        assertEquals("http://localhost:8080", reloaded.homeUrl)
        assertEquals(10, reloaded.reloadIntervalSeconds)
        assertTrue(reloaded.reloadOnSave)
        assertTrue(reloaded.autoRefresh)
        assertEquals("vue,svelte", reloaded.watchExtensions)
    }

    fun testGetInstanceReturnsSameSingleton() {
        assertSame(WebBrowserSettings.getInstance(), WebBrowserSettings.getInstance())
    }
}
