package no.dervis.webbrowser.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import no.dervis.webbrowser.domain.OpenOnRunPolicy
import no.dervis.webbrowser.domain.ReadinessMode

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
        var openOnRun: Boolean = false,
        var runConfigName: String = "",
        var openUrl: String = "",
        var readinessMode: String = ReadinessMode.DEFAULT.storageId,
        var readinessSeconds: Int = DEFAULT_WAIT_SECONDS,
    )

    private var state = State()

    /** Absolute path to watch for reload-on-save. Blank = whole project. */
    var watchPath: String
        get() = state.watchPath
        set(value) { state.watchPath = value }

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
