package no.dervis.webbrowser.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import no.dervis.webbrowser.domain.AddressBarHistory
import no.dervis.webbrowser.domain.BrowsingData
import no.dervis.webbrowser.domain.LegacyBrowsingMigration
import no.dervis.webbrowser.domain.TabSession

/**
 * Per-user browsing state for a project: the open tabs and the address-bar history.
 * Stored in the workspace file (`.idea/workspace.xml`), which is not shared through
 * version control, so one person's browsing never reaches the team.
 *
 * Versions before 0.7.0 kept this in the shared project file. When the service
 * starts for a user for the first time, it moves that data over (see
 * [LegacyBrowsingMigration]).
 */
@Service(Service.Level.PROJECT)
@State(name = "WebBrowserWorkspace", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class WebBrowserWorkspaceState(private val project: Project) : PersistentStateComponent<WebBrowserWorkspaceState.State> {

    data class State(
        // Plain mutable types — the IDE's XML serializer can't round-trip Kotlin's
        // read-only collection types.
        var persistedTabUrls: ArrayList<String> = ArrayList(),
        var persistedActiveIndex: Int = 0,
        var persistedPinnedUrls: ArrayList<String> = ArrayList(),
        // Address-bar autocomplete history, most recent first.
        var addressBarHistory: ArrayList<String> = ArrayList(),
        // Set once the shared file's old tabs and history have been moved here.
        var legacyMigrated: Boolean = false,
    )

    private var state = State()

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

    /** Current address-bar history, checked and capped as it is read. */
    fun addressBarHistory(): AddressBarHistory = AddressBarHistory.of(state.addressBarHistory)

    /** Push [url] onto the address-bar history (most recent first). */
    fun recordAddressBarUrl(url: String) {
        state.addressBarHistory = ArrayList(addressBarHistory().record(url).entries)
    }

    /** Number of URLs currently remembered for address-bar autocomplete. */
    fun addressBarHistorySize(): Int = addressBarHistory().size

    /** Forget every remembered address-bar URL. */
    fun clearAddressBarHistory() {
        state.addressBarHistory = ArrayList()
    }

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    /** Runs after the stored state is loaded (or found missing), before anyone reads it. */
    override fun initializeComponent() = migrateLegacyOnce()

    /**
     * Moves the shared file's old tabs and history here, once. After that the old
     * fields are ignored, so a teammate on an older version writing them again
     * doesn't start a tug-of-war over the shared file.
     */
    private fun migrateLegacyOnce() {
        if (state.legacyMigrated) return
        val legacy = WebBrowserProjectSettings.getInstance(project).takeLegacyBrowsingData()
        val migrated = LegacyBrowsingMigration.migrate(legacy, BrowsingData(tabSession(), addressBarHistory()))
        saveTabSession(migrated.session)
        state.addressBarHistory = ArrayList(migrated.history.entries)
        state.legacyMigrated = true
    }

    companion object {
        fun getInstance(project: Project): WebBrowserWorkspaceState = project.service()
    }
}
