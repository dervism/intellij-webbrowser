package no.dervis.webbrowser.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UrlTest {

    @Test
    fun `parse returns None for blank input`() {
        assertTrue(Url.parse("").isNone())
    }

    @Test
    fun `parse returns None for whitespace-only input`() {
        assertTrue(Url.parse("   \t\n  ").isNone())
    }

    @Test
    fun `parse trims surrounding whitespace`() {
        assertEquals("http://localhost", Url.parse("  localhost  ").getOrNull()?.value)
    }

    @Test
    fun `parse prefixes http when no scheme is present`() {
        assertEquals("http://localhost:3000", Url.parse("localhost:3000").getOrNull()?.value)
    }

    @Test
    fun `parse keeps an explicit http scheme intact`() {
        assertEquals("http://example.com", Url.parse("http://example.com").getOrNull()?.value)
    }

    @Test
    fun `parse keeps an explicit https scheme intact`() {
        assertEquals("https://example.com", Url.parse("https://example.com").getOrNull()?.value)
    }

    @Test
    fun `host extracts the hostname`() {
        assertEquals("localhost", Url.parse("localhost:3000").getOrNull()?.host)
        assertEquals("example.com", Url.parse("https://example.com/path").getOrNull()?.host)
    }

    @Test
    fun `host returns null for a malformed URL`() {
        // URI cannot resolve a host from a path-only string after normalization.
        assertNull(Url.parse("not a url with spaces").getOrNull()?.host)
    }

    @Test
    fun `port returns the explicit port when given`() {
        assertEquals(3000, Url.parse("localhost:3000").getOrNull()?.port)
        assertEquals(8443, Url.parse("https://example.com:8443").getOrNull()?.port)
    }

    @Test
    fun `port defaults to 80 for http when none given`() {
        assertEquals(80, Url.parse("http://example.com").getOrNull()?.port)
    }

    @Test
    fun `port defaults to 443 for https when none given`() {
        assertEquals(443, Url.parse("https://example.com").getOrNull()?.port)
    }

    @Test
    fun `toString returns the normalized URL value`() {
        assertEquals("http://localhost:3000", Url.parse("localhost:3000").getOrNull()?.toString())
    }

    // ---- fromAddressBar: URL-like inputs are parsed as URLs ----

    @Test
    fun `fromAddressBar returns None for blank input`() {
        assertTrue(Url.fromAddressBar("").isNone())
        assertTrue(Url.fromAddressBar("   ").isNone())
    }

    @Test
    fun `fromAddressBar keeps an explicit scheme`() {
        assertEquals("http://example.com", Url.fromAddressBar("http://example.com").getOrNull()?.value)
        assertEquals("https://example.com", Url.fromAddressBar("https://example.com").getOrNull()?.value)
    }

    @Test
    fun `fromAddressBar treats domain-like input as URL`() {
        assertEquals("http://google.com", Url.fromAddressBar("google.com").getOrNull()?.value)
        assertEquals("http://example.com/path", Url.fromAddressBar("example.com/path").getOrNull()?.value)
    }

    @Test
    fun `fromAddressBar treats localhost as URL`() {
        assertEquals("http://localhost", Url.fromAddressBar("localhost").getOrNull()?.value)
        assertEquals("http://localhost:3000", Url.fromAddressBar("localhost:3000").getOrNull()?.value)
    }

    @Test
    fun `fromAddressBar treats IPv4 as URL`() {
        assertEquals("http://127.0.0.1", Url.fromAddressBar("127.0.0.1").getOrNull()?.value)
        assertEquals("http://127.0.0.1:8080", Url.fromAddressBar("127.0.0.1:8080").getOrNull()?.value)
    }

    @Test
    fun `fromAddressBar treats host with explicit port as URL`() {
        assertEquals("http://myserver:8080", Url.fromAddressBar("myserver:8080").getOrNull()?.value)
    }

    @Test
    fun `fromAddressBar treats path-only input as URL`() {
        // A slash in the input is a strong URL signal even without a dot.
        assertEquals("http://test/path", Url.fromAddressBar("test/path").getOrNull()?.value)
    }

    // ---- fromAddressBar: search routing ----

    @Test
    fun `fromAddressBar treats a single keyword as a Startpage search`() {
        assertEquals(
            "https://www.startpage.com/do/search?q=test",
            Url.fromAddressBar("test").getOrNull()?.value,
        )
    }

    @Test
    fun `fromAddressBar treats a multi-word phrase as a Startpage search`() {
        // application/x-www-form-urlencoded encodes spaces as '+'
        assertEquals(
            "https://www.startpage.com/do/search?q=what+is+rust",
            Url.fromAddressBar("what is rust").getOrNull()?.value,
        )
    }

    @Test
    fun `fromAddressBar URL-encodes special characters in the search query`() {
        assertEquals(
            "https://www.startpage.com/do/search?q=c%2B%2B+template",
            Url.fromAddressBar("c++ template").getOrNull()?.value,
        )
    }

    @Test
    fun `fromAddressBar trims surrounding whitespace before classifying`() {
        assertEquals(
            "https://www.startpage.com/do/search?q=test",
            Url.fromAddressBar("  test  ").getOrNull()?.value,
        )
    }
}
