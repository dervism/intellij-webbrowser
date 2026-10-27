package no.dervis.webbrowser.domain

/** The tab session and history versions before 0.7.0 kept in the shared project file. */
data class LegacyBrowsingData(
    val tabUrls: List<String>,
    val activeIndex: Int,
    val pinnedUrls: Set<String>,
    val history: List<String>,
)

/** The tab session and history as the per-user workspace file keeps them. */
data class BrowsingData(val session: TabSession, val history: AddressBarHistory)

/**
 * Moves tabs and history from the shared project file into the per-user workspace
 * file, once per user. The shared file can be edited by anyone with commit access,
 * so its data gets the same checks as anything else read from storage, and at most
 * [TabLimits.MAX_MIGRATED_TABS] tabs come across.
 */
object LegacyBrowsingMigration {

    /**
     * Tabs: [current] wins when it already has a session; otherwise the legacy tabs
     * [Url.web] accepts, cut to the limit, with pins and the active tab kept on the
     * tabs that remain. History: [current] first, then legacy, through
     * [AddressBarHistory.of].
     */
    fun migrate(legacy: LegacyBrowsingData, current: BrowsingData): BrowsingData =
        BrowsingData(
            session = if (current.session.isEmpty) legacySession(legacy) else current.session,
            history = AddressBarHistory.of(current.history.entries + legacy.history),
        )

    private fun legacySession(legacy: LegacyBrowsingData): TabSession {
        val kept = legacy.tabUrls.filter { Url.web(it).isSome() }.take(TabLimits.MAX_MIGRATED_TABS)
        return TabSession.capture(
            rawUrls = kept,
            activeRawUrl = legacy.tabUrls.getOrNull(legacy.activeIndex)?.takeIf { it in kept },
            pinnedRawUrls = legacy.pinnedUrls.filter { it in kept }.toSet(),
        )
    }
}
