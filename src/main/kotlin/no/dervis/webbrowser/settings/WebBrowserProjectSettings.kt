package no.dervis.webbrowser.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import no.dervis.webbrowser.domain.AddressBarHistory
import no.dervis.webbrowser.domain.OpenOnRunPolicy
import no.dervis.webbrowser.domain.ReadinessMode
import no.dervis.webbrowser.domain.TabSession

/**
 * Project-level settings: the reload-on-save folder, and the "open the browser
 * when a run configuration starts" trigger. Stored in .idea/intellijWebBrowser.xml.
 *
 * Persists primitives (for IDE serialization) but exposes domain types.
 */
@Service(Service.Level.PROJECT)
@State(name = "WebBrowserProjectSettings", storages = [Storage("intellijWebBrowser.xml")])
class WebBrowserProjectSettings : PersistentStateComponent<WebBrowserProjectSettings.State> {

    data class State(
        var watchPath: String = "",
        var watchPatterns: String = "",
        var projectHomeUrl: String = "",
        var openOnRun: Boolean = false,
        var runConfigName: String = "",
        var openUrl: String = "",
        var readinessMode: String = ReadinessMode.DEFAULT.storageId,
        var readinessSeconds: Int = DEFAULT_WAIT_SECONDS,
        // Restored on next tool-window open. Plain mutable types — the IDE's
        // XML serializer can't round-trip Kotlin's read-only collection types.
        var persistedTabUrls: ArrayList<String> = ArrayList(),
        var persistedActiveIndex: Int = 0,
        var persistedPinnedUrls: ArrayList<String> = ArrayList(),
        // Address-bar autocomplete history, MRU first. Bounded by
        // [AddressBarHistory.DEFAULT_CAPACITY] when written.
        var addressBarHistory: ArrayList<String> = ArrayList(),
    )

    private var state = State()

    /** Absolute path to watch for reload-on-save. Blank = whole project. */
    var watchPath: String
        get() = state.watchPath
        set(value) { state.watchPath = value }

    /**
     * Power-user override: newline-separated [glob patterns][no.dervis.webbrowser.domain.WatchGlobs].
     * When non-blank, replaces the simple folder + extension scheme entirely.
     */
    var watchPatterns: String
        get() = state.watchPatterns
        set(value) { state.watchPatterns = value }

    /**
     * Per-project override for the Home / dev-server URL. Blank means "use the
     * application-wide setting" — see
     * [no.dervis.webbrowser.domain.HomeUrlResolver].
     */
    var projectHomeUrl: String
        get() = state.projectHomeUrl
        set(value) { state.projectHomeUrl = value }

    /** Whether to open the browser when a run configuration starts. */
    var openOnRun: Boolean
        get() = state.openOnRun
        set(value) { state.openOnRun = value }

    /** Run configuration name that triggers opening. Blank = any configuration. */
    var runConfigName: String
        get() = state.runConfigName
        set(value) { state.runConfigName = value }

    /** URL to open on run. Blank = use the application home URL. */
    var openUrl: String
        get() = state.openUrl
        set(value) { state.openUrl = value }

    var readiness: ReadinessMode
        get() = ReadinessMode.fromStorageId(state.readinessMode)
        set(value) { state.readinessMode = value.storageId }

    /** Poll timeout (REACHABLE) or delay (DELAY), in seconds. */
    var readinessSeconds: Int
        get() = state.readinessSeconds.coerceIn(MIN_WAIT, MAX_WAIT)
        set(value) { state.readinessSeconds = value.coerceIn(MIN_WAIT, MAX_WAIT) }

    /** Snapshot of the open-on-run rule as a pure domain value object. */
    fun openOnRunPolicy(): OpenOnRunPolicy =
        OpenOnRunPolicy(openOnRun, runConfigName.trim(), readiness, readinessSeconds)

    /** Read the persisted tab session, ready to be replayed on tool-window open. */
    fun tabSession(): TabSession =
        TabSession(
            state.persistedTabUrls.toList(),
            state.persistedActiveIndex,
            state.persistedPinnedUrls.toSet(),
        )

    /** Persist the current tabs so the next IDE / project open can restore them. */
    fun saveTabSession(session: TabSession) {
        state.persistedTabUrls = ArrayList(session.urls)
        state.persistedActiveIndex = session.activeIndex
        state.persistedPinnedUrls = ArrayList(session.pinnedUrls)
    }

    /** Current address-bar history snapshot. */
    fun addressBarHistory(): AddressBarHistory =
        AddressBarHistory(state.addressBarHistory.toList())

    /** Push [url] onto the per-project address-bar history (MRU). */
    fun recordAddressBarUrl(url: String) {
        val updated = addressBarHistory().record(url)
        state.addressBarHistory = ArrayList(updated.entries)
    }

    /** Number of URLs currently remembered for address-bar autocomplete. */
    fun addressBarHistorySize(): Int = state.addressBarHistory.size

    /** Forget every remembered address-bar URL. */
    fun clearAddressBarHistory() {
        state.addressBarHistory = ArrayList()
    }

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    companion object {
        const val MIN_WAIT = 1
        const val MAX_WAIT = 3600
        const val DEFAULT_WAIT_SECONDS = 30

        fun getInstance(project: Project): WebBrowserProjectSettings = project.service()
    }
}
