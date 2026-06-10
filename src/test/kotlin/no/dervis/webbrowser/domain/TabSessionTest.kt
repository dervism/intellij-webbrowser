package no.dervis.webbrowser.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TabSessionTest {

    @Test
    fun `EMPTY is empty and has safeActiveIndex of -1`() {
        assertTrue(TabSession.EMPTY.isEmpty)
        assertEquals(-1, TabSession.EMPTY.safeActiveIndex())
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
        assertEquals(1, session.safeActiveIndex())
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
    fun `safeActiveIndex clamps an out-of-range stored index`() {
        val session = TabSession(listOf("https://a.example"), activeIndex = 42)
        assertEquals(0, session.safeActiveIndex())
    }

    @Test
    fun `safeActiveIndex clamps a negative stored index`() {
        val session = TabSession(listOf("https://a.example", "https://b.example"), activeIndex = -3)
        assertEquals(0, session.safeActiveIndex())
    }

    @Test
    fun `resolvedUrls parses each entry through Url`() {
        val session = TabSession(listOf("https://a.example", "http://b.example"), activeIndex = 0)
        val urls = session.resolvedUrls()
        assertEquals(2, urls.size)
        assertEquals("https://a.example", urls[0].value)
        assertEquals("http://b.example", urls[1].value)
    }

    @Test
    fun `capture preserves the pinned subset`() {
        val session = TabSession.capture(
            rawUrls = listOf("https://a.example", "https://b.example", "https://c.example"),
            activeRawUrl = "https://a.example",
            pinnedRawUrls = setOf("https://a.example", "https://c.example"),
        )
        assertEquals(setOf("https://a.example", "https://c.example"), session.pinnedUrls)
        assertTrue(session.isPinnedAt(0))
        assertTrue(!session.isPinnedAt(1))
        assertTrue(session.isPinnedAt(2))
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

    @Test
    fun `isPinnedAt returns false for out-of-range indices`() {
        val session = TabSession(
            listOf("https://a.example"),
            activeIndex = 0,
            pinnedUrls = setOf("https://a.example"),
        )
        assertTrue(session.isPinnedAt(0))
        assertTrue(!session.isPinnedAt(1))
        assertTrue(!session.isPinnedAt(-1))
    }
}
