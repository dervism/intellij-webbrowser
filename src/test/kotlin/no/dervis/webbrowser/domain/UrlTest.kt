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

    // ---- additional edge cases ----

    @Test
    fun `parse preserves query string and hash fragment`() {
        assertEquals(
            "http://example.com/path?q=1&r=2#section",
            Url.parse("example.com/path?q=1&r=2#section").getOrNull()?.value,
        )
    }

    @Test
    fun `port reports the explicit non-default port even on https`() {
        assertEquals(8443, Url.parse("https://example.com:8443/x").getOrNull()?.port)
    }
}
