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

    // ---- scheme rules -----------------------------------------------------------

    @Test
    fun `a typed URL with spaces navigates with the spaces encoded`() {
        val request = AddressBarRequest.parse("http://localhost:3000/my page.html").getOrNull()
        assertTrue(request is AddressBarRequest.Navigate, "expected Navigate, got $request")
        assertEquals("http://localhost:3000/my%20page.html", request.target.value)
    }

    @Test
    fun `a local path is neither loaded nor searched`() {
        assertTrue(AddressBarRequest.parse("/Users/me/index.html").isNone())
        assertTrue(AddressBarRequest.parse("C:\\Users\\me\\index.html").isNone())
    }

    @Test
    fun `IPv6 and international hosts navigate`() {
        val ipv6 = AddressBarRequest.parse("[::1]:3000").getOrNull()
        assertTrue(ipv6 is AddressBarRequest.Navigate, "expected Navigate, got $ipv6")
        assertEquals("http://[::1]:3000", ipv6.target.value)
        val idn = AddressBarRequest.parse("bücher.example/straße").getOrNull()
        assertTrue(idn is AddressBarRequest.Navigate, "expected Navigate, got $idn")
        assertEquals("http://bücher.example/straße", idn.target.value)
    }

    // ---- "Open in" target ----------------------------------------------------------

    private val home = Url.web("http://localhost:3000")

    private fun external(text: String) = AddressBarRequest.externalTarget(text, home).getOrNull()?.value

    @Test
    fun `external target is the home URL only when the address bar is empty`() {
        assertEquals("http://localhost:3000", external("  "))
    }

    @Test
    fun `external target is the address-bar URL when it is http or https`() {
        assertEquals("http://example.com", external("example.com"))
        assertEquals("https://www.startpage.com/do/search?q=some+words", external("some words"))
    }

    @Test
    fun `external target refuses a non-web address-bar entry instead of using the home URL`() {
        assertEquals(null, external("file:///tmp/a.html"))
        assertEquals(null, external("ftp://x.com"))
    }

    @Test
    fun `scheme in the query string does not count as a scheme`() {
        val request = AddressBarRequest.parse("localhost:3000/?r=http://x").getOrNull()
        assertTrue(request is AddressBarRequest.Navigate, "expected Navigate, got $request")
        assertEquals("http://localhost:3000/?r=http://x", request.target.value)
    }

    @Test
    fun `typed file URL navigates`() {
        val request = AddressBarRequest.parse("file:///tmp/a.html").getOrNull()
        assertTrue(request is AddressBarRequest.Navigate, "expected Navigate, got $request")
        assertEquals("file:///tmp/a.html", request.target.value)
    }

    @Test
    fun `URL-looking input with a rejected scheme gives None`() {
        assertTrue(AddressBarRequest.parse("data:text/html,x").isNone())
        assertTrue(AddressBarRequest.parse("ftp://x.com").isNone())
    }

    @Test
    fun `javascript input is searched, not navigated`() {
        val request = AddressBarRequest.parse("javascript:alert(1)").getOrNull()
        assertTrue(request is AddressBarRequest.Search, "expected Search, got $request")
    }
}
