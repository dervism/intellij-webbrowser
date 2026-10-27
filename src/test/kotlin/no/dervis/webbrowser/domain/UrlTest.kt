package no.dervis.webbrowser.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UrlTest {

    private fun web(raw: String): String? = Url.web(raw).getOrNull()?.value
    private fun typed(raw: String): String? = Url.typed(raw).getOrNull()?.value

    @Test
    fun `blank input is rejected`() {
        assertTrue(Url.web("").isNone())
        assertTrue(Url.web("   \t\n  ").isNone())
        assertTrue(Url.typed("").isNone())
    }

    @Test
    fun `web accepts http, https and bare hosts`() {
        assertEquals("http://example.com", web("http://example.com"))
        assertEquals("https://example.com", web("https://example.com"))
        assertEquals("http://localhost", web("localhost"))
        assertEquals("http://localhost:3000", web("localhost:3000"))
        assertEquals("http://example.com/a", web("example.com/a"))
        assertEquals("http://[::1]:3000", web("[::1]:3000"))
    }

    @Test
    fun `scheme is read only from the start`() {
        assertEquals("http://localhost:3000/?r=http://x", web("localhost:3000/?r=http://x"))
    }

    @Test
    fun `scheme is case-insensitive and lowercased`() {
        val url = Url.web("HTTP://Example.com").getOrNull()
        assertEquals("http://Example.com", url?.value)
        assertEquals("http", url?.scheme)
    }

    @Test
    fun `web rejects other schemes`() {
        listOf("file:///tmp/a.html", "ftp://x", "chrome://crash", "view-source:http://x").forEach {
            assertNull(web(it), "web must reject $it")
        }
    }

    @Test
    fun `typed accepts file`() {
        val url = Url.typed("file:///tmp/a.html").getOrNull()
        assertEquals("file:///tmp/a.html", url?.value)
        assertEquals("file", url?.scheme)
        assertNull(typed("ftp://x"))
    }

    @Test
    fun `schemes without slashes are rejected by both`() {
        listOf("javascript:alert(1)", "data:text/html,x", "about:blank", "mailto:a@b", "file:/x", "localhost:").forEach {
            assertNull(web(it), "web must reject $it")
            assertNull(typed(it), "typed must reject $it")
        }
    }

    @Test
    fun `http needs a host`() {
        listOf("http://", "http:///x", "https://").forEach {
            assertNull(web(it), "web must reject $it")
        }
    }

    @Test
    fun `control characters and inner whitespace are rejected`() {
        listOf("http://a b", "http://a\u0000b", "http://a\nb", "http://a\u0085b", "http://a\u007Fb").forEach {
            assertNull(web(it), "web must reject ${it.replace("\n", "\\n")}")
            assertNull(typed(it))
        }
    }

    @Test
    fun `a path without a host is rejected`() {
        listOf("/Users/me/index.html", "//evil.example/x", "\\\\server\\share").forEach {
            assertNull(web(it), "web must reject $it")
            assertNull(typed(it), "typed must reject $it")
        }
    }

    @Test
    fun `live accepts long http URLs that Chromium reports`() {
        val long = "http://a/" + "x".repeat(20_000)
        assertEquals(long, Url.live(long).getOrNull()?.value)
    }

    @Test
    fun `live still rejects other schemes and control characters`() {
        assertNull(Url.live("file:///x").getOrNull())
        assertNull(Url.live("http://a\u0000b").getOrNull())
    }

    @Test
    fun `surrounding whitespace is trimmed`() {
        assertEquals("http://localhost:3000", web("  http://localhost:3000\n"))
    }

    @Test
    fun `international and IPv6 URLs are accepted`() {
        assertEquals("https://bücher.example/straße", web("https://bücher.example/straße"))
        assertEquals("http://[::1]:3000/", web("http://[::1]:3000/"))
    }

    @Test
    fun `length cap`() {
        val atCap = "http://a/" + "x".repeat(Url.MAX_LENGTH - 9)
        assertEquals(8192, atCap.length)
        assertEquals(atCap, web(atCap))
        assertNull(web(atCap + "x"))
    }

    @Test
    fun `redacted keeps scheme, host and port only`() {
        assertEquals("https://example.com:8443", Url.web("https://example.com:8443/p?token=1").getOrNull()?.redacted)
        assertEquals("http://localhost", Url.web("http://localhost/x").getOrNull()?.redacted)
        assertEquals("file://", Url.typed("file:///tmp/a").getOrNull()?.redacted)
    }

    @Test
    fun `host is extracted from a URL`() {
        assertEquals("localhost", Url.web("localhost:3000").getOrNull()?.host)
        assertEquals("example.com", Url.web("https://example.com/path").getOrNull()?.host)
    }

    @Test
    fun `input with spaces has no Url and so no host`() {
        assertNull(Url.web("not a url with spaces").getOrNull()?.host)
    }

    @Test
    fun `port is the explicit port when given`() {
        assertEquals(3000, Url.web("localhost:3000").getOrNull()?.port)
        assertEquals(8443, Url.web("https://example.com:8443").getOrNull()?.port)
        assertEquals(8443, Url.web("https://example.com:8443/x").getOrNull()?.port)
    }

    @Test
    fun `port defaults to the scheme default`() {
        assertEquals(80, Url.web("http://example.com").getOrNull()?.port)
        assertEquals(443, Url.web("https://example.com").getOrNull()?.port)
    }

    @Test
    fun `toString returns the normalized URL value`() {
        assertEquals("http://localhost:3000", Url.web("localhost:3000").getOrNull()?.toString())
    }

    @Test
    fun `query string and fragment are kept`() {
        assertEquals("http://example.com/path?q=1&r=2#section", web("example.com/path?q=1&r=2#section"))
    }
}
