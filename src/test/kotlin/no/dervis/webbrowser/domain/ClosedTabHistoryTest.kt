package no.dervis.webbrowser.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClosedTabHistoryTest {

    private fun entry(url: String, title: String = url) =
        ClosedTabHistory.Entry(Url.parse(url).getOrNull()!!, title)

    @Test
    fun `EMPTY is empty and has no top`() {
        val h = ClosedTabHistory.EMPTY
        assertTrue(h.isEmpty)
        assertEquals(0, h.size)
        assertNull(h.pop())
    }

    @Test
    fun `push adds an entry to the top`() {
        val h = ClosedTabHistory.EMPTY.push(entry("https://a.example"))
        assertEquals(1, h.size)
        val popped = h.pop()
        assertNotNull(popped)
        assertEquals("https://a.example", popped.first.url.value)
    }

    @Test
    fun `pop returns the most recently pushed entry`() {
        val h = ClosedTabHistory.EMPTY
            .push(entry("https://first.example"))
            .push(entry("https://second.example"))
        val (top, rest) = h.pop()!!
        assertEquals("https://second.example", top.url.value)
        assertEquals(1, rest.size)
        assertEquals("https://first.example", rest.pop()!!.first.url.value)
    }

    @Test
    fun `push past capacity drops the oldest entry`() {
        var h = ClosedTabHistory(emptyList(), capacity = 3)
        repeat(5) { i -> h = h.push(entry("https://e$i.example")) }
        assertEquals(3, h.size)
        // Oldest two (e0, e1) dropped. Stack top is the most recent.
        assertEquals("https://e4.example", h.pop()!!.first.url.value)
    }

    @Test
    fun `push capacity of zero keeps the history empty`() {
        val h = ClosedTabHistory(emptyList(), capacity = 0)
            .push(entry("https://a.example"))
        assertTrue(h.isEmpty)
    }

    @Test
    fun `pop on a single-entry stack returns the entry and an empty history`() {
        val h = ClosedTabHistory.EMPTY.push(entry("https://only.example"))
        val (_, rest) = h.pop()!!
        assertTrue(rest.isEmpty)
        assertNull(rest.pop())
    }

    @Test
    fun `entry preserves title separately from url`() {
        val h = ClosedTabHistory.EMPTY.push(entry("https://docs.example", "Docs"))
        val (top, _) = h.pop()!!
        assertEquals("Docs", top.title)
        assertEquals("https://docs.example", top.url.value)
    }
}
