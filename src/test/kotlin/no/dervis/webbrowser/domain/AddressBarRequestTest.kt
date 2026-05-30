package no.dervis.webbrowser.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AddressBarRequestTest {

    // ---- empty / whitespace ---------------------------------------------------

    @Test
    fun `blank input returns None`() {
        assertTrue(AddressBarRequest.parse("").isNone())
        assertTrue(AddressBarRequest.parse("   ").isNone())
    }

    // ---- Navigate variant -----------------------------------------------------

    @Test
    fun `explicit http scheme yields Navigate`() {
        val request = AddressBarRequest.parse("http://example.com").getOrNull()
        assertTrue(request is AddressBarRequest.Navigate)
        assertEquals("http://example.com", request.target.value)
    }

    @Test
    fun `explicit https scheme yields Navigate`() {
        val request = AddressBarRequest.parse("https://example.com").getOrNull()
        assertTrue(request is AddressBarRequest.Navigate)
        assertEquals("https://example.com", request.target.value)
    }

    @Test
    fun `dotted host yields Navigate with http prefixed`() {
        val request = AddressBarRequest.parse("google.com").getOrNull()
        assertTrue(request is AddressBarRequest.Navigate)
        assertEquals("http://google.com", request.target.value)
    }

    @Test
    fun `path-only input yields Navigate (slash is a URL signal)`() {
        val request = AddressBarRequest.parse("test/path").getOrNull()
        assertTrue(request is AddressBarRequest.Navigate)
        assertEquals("http://test/path", request.target.value)
    }

    @Test
    fun `localhost yields Navigate`() {
        assertTrue(AddressBarRequest.parse("localhost").getOrNull() is AddressBarRequest.Navigate)
        assertTrue(AddressBarRequest.parse("localhost:3000").getOrNull() is AddressBarRequest.Navigate)
    }

    @Test
    fun `IPv4 host yields Navigate`() {
        assertTrue(AddressBarRequest.parse("127.0.0.1").getOrNull() is AddressBarRequest.Navigate)
        assertTrue(AddressBarRequest.parse("127.0.0.1:8080").getOrNull() is AddressBarRequest.Navigate)
    }

    @Test
    fun `host with explicit port yields Navigate`() {
        val request = AddressBarRequest.parse("myserver:8080").getOrNull()
        assertTrue(request is AddressBarRequest.Navigate)
        assertEquals("http://myserver:8080", request.target.value)
    }

    @Test
    fun `URL with query string yields Navigate (path-like input, not search)`() {
        val request = AddressBarRequest.parse("example.com/?q=hello").getOrNull()
        assertTrue(request is AddressBarRequest.Navigate)
        assertEquals("http://example.com/?q=hello", request.target.value)
    }

    // ---- Search variant -------------------------------------------------------

    @Test
    fun `single keyword yields Search via Startpage`() {
        val request = AddressBarRequest.parse("kotlin").getOrNull()
        assertTrue(request is AddressBarRequest.Search)
        assertEquals("kotlin", request.query)
        assertEquals("https://www.startpage.com/do/search?q=kotlin", request.target.value)
    }

    @Test
    fun `multi-word phrase yields Search and URL-encodes spaces as plus`() {
        val request = AddressBarRequest.parse("what is rust").getOrNull()
        assertTrue(request is AddressBarRequest.Search)
        assertEquals("what is rust", request.query)
        assertEquals(
            "https://www.startpage.com/do/search?q=what+is+rust",
            request.target.value,
        )
    }

    @Test
    fun `special characters in query are URL-encoded`() {
        val request = AddressBarRequest.parse("c++ template").getOrNull()
        assertTrue(request is AddressBarRequest.Search)
        assertEquals("c++ template", request.query)
        assertEquals(
            "https://www.startpage.com/do/search?q=c%2B%2B+template",
            request.target.value,
        )
    }

    @Test
    fun `surrounding whitespace is trimmed before classifying`() {
        val request = AddressBarRequest.parse("  test  ").getOrNull()
        assertTrue(request is AddressBarRequest.Search)
        assertEquals("test", request.query) // trimmed, raw query is the trimmed text
    }

    @Test
    fun `intent is preserved in the variant — Navigate vs Search are not interchangeable`() {
        // Same Url construction shape, but different intent.
        val nav = AddressBarRequest.parse("example.com").getOrNull()
        val search = AddressBarRequest.parse("example").getOrNull()
        assertTrue(nav is AddressBarRequest.Navigate)
        assertTrue(search is AddressBarRequest.Search)
        // Sealed variants must not equal each other even when the target overlaps in shape.
        assertTrue(nav != search)
    }
}
