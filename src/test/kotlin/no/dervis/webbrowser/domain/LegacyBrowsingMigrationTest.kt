package no.dervis.webbrowser.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class LegacyBrowsingMigrationTest {

    private val nothingYet = BrowsingData(TabSession.EMPTY, AddressBarHistory.EMPTY)

    private fun legacy(
        tabs: List<String> = emptyList(),
        active: Int = 0,
        pinned: Set<String> = emptySet(),
        history: List<String> = emptyList(),
    ) = LegacyBrowsingData(tabs, active, pinned, history)

    @Test
    fun `ordinary 0_6_0 data comes through unchanged`() {
        val result = LegacyBrowsingMigration.migrate(
            legacy(
                tabs = listOf("http://localhost:3000/", "http://localhost:6006/"),
                active = 1,
                pinned = setOf("http://localhost:6006/"),
                history = listOf("http://localhost:3000/"),
            ),
            nothingYet,
        )
        assertEquals(listOf("http://localhost:3000/", "http://localhost:6006/"), result.session.urls)
        assertEquals(setOf("http://localhost:6006/"), result.session.pinnedUrls)
        assertEquals(1, result.session.activeIndex)
        assertEquals(listOf("http://localhost:3000/"), result.history.entries)
    }

    @Test
    fun `hostile legacy entries are dropped`() {
        val result = LegacyBrowsingMigration.migrate(
            legacy(tabs = listOf("file:///etc/hosts", "javascript:alert(1)", "http://a\u0000", "http://ok")),
            nothingYet,
        )
        assertEquals(listOf("http://ok"), result.session.urls)
    }

    @Test
    fun `at most 20 legacy tabs are migrated, with pins and active tab kept`() {
        val tabs = (1..500).map { "http://site$it.example/" }
        val kept = LegacyBrowsingMigration.migrate(
            legacy(tabs = tabs, active = 10, pinned = setOf("http://site3.example/")),
            nothingYet,
        ).session
        assertEquals(tabs.take(20), kept.urls)
        assertEquals(setOf("http://site3.example/"), kept.pinnedUrls)
        assertEquals(10, kept.activeIndex)

        val activeBeyondCut = LegacyBrowsingMigration.migrate(legacy(tabs = tabs, active = 300), nothingYet).session
        assertEquals(0, activeBeyondCut.activeIndex)
    }

    @Test
    fun `pins on tabs beyond the cut are not migrated`() {
        val tabs = (1..30).map { "http://site$it.example/" }
        val kept = LegacyBrowsingMigration.migrate(
            legacy(tabs = tabs, pinned = setOf("http://site2.example/", "http://site25.example/")),
            nothingYet,
        ).session
        assertEquals(setOf("http://site2.example/"), kept.pinnedUrls)
    }

    @Test
    fun `workspace tabs win over legacy tabs`() {
        val current = BrowsingData(TabSession(listOf("http://mine"), 0), AddressBarHistory.EMPTY)
        val result = LegacyBrowsingMigration.migrate(legacy(tabs = listOf("http://theirs")), current)
        assertEquals(current.session, result.session)
    }

    @Test
    fun `history merges workspace first, then legacy, through the same checks`() {
        val current = BrowsingData(TabSession.EMPTY, AddressBarHistory(listOf("http://a")))
        val result = LegacyBrowsingMigration.migrate(
            legacy(history = listOf("http://b", "file:///x", "http://A")),
            current,
        )
        assertEquals(listOf("http://a", "http://b"), result.history.entries)
    }

    @Test
    fun `empty legacy data leaves current data as it is`() {
        val current = BrowsingData(
            TabSession(listOf("http://mine"), 0, setOf("http://mine")),
            AddressBarHistory(listOf("http://a", "http://b")),
        )
        assertEquals(current, LegacyBrowsingMigration.migrate(legacy(), current))
    }
}
