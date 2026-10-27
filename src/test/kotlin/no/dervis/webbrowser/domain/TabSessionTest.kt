package no.dervis.webbrowser.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TabSessionTest {

    @Test
    fun `EMPTY is empty and restores no active tab`() {
        assertTrue(TabSession.EMPTY.isEmpty)
        assertEquals(-1, TabSession.EMPTY.restorePlan().activeIndex)
    }

    @Test
    fun `capture filters out non-restorable URLs`() {
        val session = TabSession.capture(
            rawUrls = listOf(
                "https://example.com",
                "data:text/html,<h1>placeholder</h1>",
                "view-source:https://example.com",
                "http://localhost:3000",
                "about:blank",
            ),
            activeRawUrl = "https://example.com",
        )
        assertEquals(listOf("https://example.com", "http://localhost:3000"), session.urls)
    }

    @Test
    fun `capture keeps an uppercase scheme`() {
        assertEquals(listOf("HTTP://a"), TabSession.capture(listOf("HTTP://a"), null).urls)
    }

    @Test
    fun `capture preserves the index of the active tab after filtering`() {
        val session = TabSession.capture(
            rawUrls = listOf(
                "data:text/html,placeholder",
                "https://a.example",
                "https://b.example",
            ),
            activeRawUrl = "https://b.example",
        )
        // Filtered urls = [a, b]; b is index 1.
        assertEquals(1, session.activeIndex)
        assertEquals(1, session.restorePlan().activeIndex)
    }

    @Test
    fun `capture defaults activeIndex to 0 when the active URL was dropped`() {
        val session = TabSession.capture(
            rawUrls = listOf("https://a.example", "https://b.example"),
            activeRawUrl = "data:text/html,placeholder",
        )
        assertEquals(0, session.activeIndex)
    }

    @Test
    fun `capture returns EMPTY when no URLs are restorable`() {
        val session = TabSession.capture(
            rawUrls = listOf("data:text/html,a", "view-source:x", "about:blank"),
            activeRawUrl = "data:text/html,a",
        )
        assertEquals(TabSession.EMPTY, session)
    }

    @Test
    fun `restorePlan falls back to the first tab for an out-of-range stored index`() {
        val session = TabSession(listOf("https://a.example"), activeIndex = 42)
        assertEquals(0, session.restorePlan().activeIndex)
    }

    @Test
    fun `restorePlan falls back to the first tab for a negative stored index`() {
        val session = TabSession(listOf("https://a.example", "https://b.example"), activeIndex = -3)
        assertEquals(0, session.restorePlan().activeIndex)
    }

    private fun plannedUrls(plan: RestorePlan) = plan.tabs.map { it.url.value }

    @Test
    fun `restorePlan turns each entry into a Url`() {
        val plan = TabSession(listOf("https://a.example", "http://b.example"), activeIndex = 0).restorePlan()
        assertEquals(listOf("https://a.example", "http://b.example"), plannedUrls(plan))
        assertEquals(0, plan.activeIndex)
    }

    @Test
    fun `restorePlan drops rejected entries and keeps pins on the right tabs`() {
        val session = TabSession(
            urls = listOf("http://a", "file:///x", "http://b", "javascript:alert(1)", "http://c"),
            activeIndex = 4,
            pinnedUrls = setOf("http://b"),
        )
        val plan = session.restorePlan()
        assertEquals(listOf("http://a", "http://b", "http://c"), plannedUrls(plan))
        assertEquals(listOf(false, true, false), plan.tabs.map { it.pinned })
        assertEquals(2, plan.activeIndex)
    }

    @Test
    fun `restorePlan falls back to the first tab when the active entry is dropped`() {
        val plan = TabSession(listOf("http://a", "file:///x", "http://b"), activeIndex = 1).restorePlan()
        assertEquals(0, plan.activeIndex)
    }

    @Test
    fun `restorePlan restores at most 100 tabs`() {
        val urls = (1..150).map { "http://site$it.example" }
        val plan = TabSession(urls, activeIndex = 120).restorePlan()
        assertEquals(100, plan.tabs.size)
        assertEquals("http://site100.example", plan.tabs.last().url.value)
        assertEquals(0, plan.activeIndex)
    }

    @Test
    fun `restorePlan of an empty or all-rejected session is empty`() {
        listOf(TabSession.EMPTY, TabSession(listOf("file:///x", "about:blank"), activeIndex = 0)).forEach {
            val plan = it.restorePlan()
            assertEquals(emptyList(), plan.tabs)
            assertEquals(-1, plan.activeIndex)
        }
    }

    @Test
    fun `capture has no tab limit`() {
        val urls = (1..150).map { "http://site$it.example" }
        assertEquals(150, TabSession.capture(urls, null).urls.size)
    }

    @Test
    fun `capture preserves the pinned subset`() {
        val session = TabSession.capture(
            rawUrls = listOf("https://a.example", "https://b.example", "https://c.example"),
            activeRawUrl = "https://a.example",
            pinnedRawUrls = setOf("https://a.example", "https://c.example"),
        )
        assertEquals(setOf("https://a.example", "https://c.example"), session.pinnedUrls)
        assertEquals(listOf(true, false, true), session.restorePlan().tabs.map { it.pinned })
    }

    @Test
    fun `capture drops pinned entries that aren't restorable`() {
        val session = TabSession.capture(
            rawUrls = listOf("https://a.example"),
            activeRawUrl = "https://a.example",
            pinnedRawUrls = setOf("https://a.example", "data:text/html,x"),
        )
        assertEquals(setOf("https://a.example"), session.pinnedUrls)
    }
}
