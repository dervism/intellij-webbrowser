package no.dervis.webbrowser.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AddressBarHistoryTest {

    @Test
    fun `EMPTY records nothing and suggests nothing`() {
        assertTrue(AddressBarHistory.EMPTY.isEmpty)
        assertEquals(emptyList(), AddressBarHistory.EMPTY.suggest(""))
    }

    @Test
    fun `record stores http and https URLs at the front`() {
        val h = AddressBarHistory.EMPTY
            .record("https://a.example")
            .record("http://b.example")
        assertEquals(listOf("http://b.example", "https://a.example"), h.entries)
    }

    @Test
    fun `record dedupes existing entries case-insensitively`() {
        val h = AddressBarHistory.EMPTY
            .record("https://Example.com")
            .record("https://other.example")
            .record("https://EXAMPLE.COM")
        // Newest casing wins, older copy is dropped.
        assertEquals(listOf("https://EXAMPLE.COM", "https://other.example"), h.entries)
    }

    @Test
    fun `record drops the LRU entry when capacity is exceeded`() {
        var h = AddressBarHistory(emptyList(), capacity = 2)
        h = h.record("https://a.example").record("https://b.example").record("https://c.example")
        assertEquals(listOf("https://c.example", "https://b.example"), h.entries)
    }

    @Test
    fun `record ignores blank and non-http URLs`() {
        val h = AddressBarHistory.EMPTY
            .record("")
            .record("   ")
            .record("about:blank")
            .record("data:text/html,x")
            .record("ftp://example.com")
        assertTrue(h.isEmpty)
    }

    @Test
    fun `record on zero-capacity history is a no-op`() {
        val h = AddressBarHistory(emptyList(), capacity = 0).record("https://a.example")
        assertTrue(h.isEmpty)
    }

    @Test
    fun `suggest returns the most-recent entries when query is empty`() {
        val h = AddressBarHistory.EMPTY
            .record("https://a.example")
            .record("https://b.example")
            .record("https://c.example")
        assertEquals(
            listOf("https://c.example", "https://b.example", "https://a.example"),
            h.suggest(""),
        )
    }

    @Test
    fun `suggest filters case-insensitively`() {
        val h = AddressBarHistory.EMPTY
            .record("https://api.example.com/v1/items")
            .record("https://docs.example.com")
            .record("https://other.com")
        val matches = h.suggest("EXAMPLE")
        // The newest non-matching entry (other.com) is filtered out;
        // the remaining matches stay in MRU order.
        assertEquals(
            listOf("https://docs.example.com", "https://api.example.com/v1/items"),
            matches,
        )
    }

    @Test
    fun `suggest respects the limit`() {
        var h = AddressBarHistory.EMPTY
        repeat(10) { i -> h = h.record("https://h$i.example") }
        assertEquals(3, h.suggest("", limit = 3).size)
    }

    @Test
    fun `suggest returns empty when limit is zero`() {
        val h = AddressBarHistory.EMPTY.record("https://a.example")
        assertEquals(emptyList(), h.suggest("a", limit = 0))
    }
}
