package no.dervis.webbrowser.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

/**
 * Application-level persisted settings for the embedded browser.
 * Registered automatically as a light service via [Service].
 */
@Service(Service.Level.APP)
@State(name = "WebBrowserSettings", storages = [Storage("intellijWebBrowser.xml")])
class WebBrowserSettings : PersistentStateComponent<WebBrowserSettings.State> {

    data class State(
        var homeUrl: String = DEFAULT_HOME_URL,
        var reloadIntervalSeconds: Int = DEFAULT_INTERVAL_SECONDS,
        var watchExtensions: String = DEFAULT_WATCH_EXTENSIONS,
        var reloadOnSave: Boolean = false,
        var autoRefresh: Boolean = false,
    )

    private var state = State()

    var homeUrl: String
        get() = state.homeUrl.ifBlank { DEFAULT_HOME_URL }
        set(value) {
            state.homeUrl = value.ifBlank { DEFAULT_HOME_URL }
        }

    /** Interval (seconds) for the timed auto-refresh feature. */
    var reloadIntervalSeconds: Int
        get() = state.reloadIntervalSeconds.coerceIn(MIN_INTERVAL, MAX_INTERVAL)
        set(value) {
            state.reloadIntervalSeconds = value.coerceIn(MIN_INTERVAL, MAX_INTERVAL)
        }

    /** Comma-separated file extensions that trigger reload-on-save. Blank = any file. */
    var watchExtensions: String
        get() = state.watchExtensions
        set(value) {
            state.watchExtensions = value
        }

    /** Last-used state of the "Reload on save" toggle (used as default for new tool windows). */
    var reloadOnSave: Boolean
        get() = state.reloadOnSave
        set(value) {
            state.reloadOnSave = value
        }

    /** Last-used state of the "Auto-refresh" toggle. */
    var autoRefresh: Boolean
        get() = state.autoRefresh
        set(value) {
            state.autoRefresh = value
        }

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    companion object {
        const val DEFAULT_HOME_URL = "http://localhost:3000"
        const val DEFAULT_INTERVAL_SECONDS = 5
        const val MIN_INTERVAL = 1
        const val MAX_INTERVAL = 86_400
        const val DEFAULT_WATCH_EXTENSIONS =
            "html,htm,css,scss,sass,less,js,mjs,cjs,jsx,ts,tsx,vue,svelte,astro,json"

        fun getInstance(): WebBrowserSettings = service()
    }
}
