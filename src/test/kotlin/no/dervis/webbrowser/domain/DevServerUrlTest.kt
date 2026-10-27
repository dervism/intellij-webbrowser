package no.dervis.webbrowser.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DevServerUrlTest {

    private fun parsed(candidate: String, excludedPorts: Set<Int> = emptySet()): String? =
        DevServerUrl.parse(candidate, excludedPorts).getOrNull()?.url?.value

    private fun assertRejected(candidate: String, excludedPorts: Set<Int> = emptySet()) =
        assertNull(parsed(candidate, excludedPorts), "expected '$candidate' to be rejected")

    @Test
    fun `accepts localhost with a port and path`() {
        assertEquals("http://localhost:5173/", parsed("http://localhost:5173/"))
        assertEquals("http://localhost:5173/app/", parsed("http://localhost:5173/app/"))
    }

    @Test
    fun `adds a root path when none is printed`() {
        assertEquals("http://localhost:3000/", parsed("http://localhost:3000"))
    }

    @Test
    fun `accepts https`() {
        assertEquals("https://localhost:8443/", parsed("https://localhost:8443/"))
    }

    @Test
    fun `lowercases the scheme and localhost`() {
        assertEquals("http://localhost:5173/", parsed("HTTP://LocalHost:5173/"))
    }

    @Test
    fun `accepts dotted-decimal 127 addresses`() {
        assertEquals("http://127.0.0.1:8000/", parsed("http://127.0.0.1:8000/"))
        assertEquals("http://127.1.20.255:8000/", parsed("http://127.1.20.255:8000/"))
    }

    @Test
    fun `accepts the IPv6 loopback`() {
        assertEquals("http://[::1]:4321/", parsed("http://[::1]:4321/"))
    }

    @Test
    fun `maps wildcard bind addresses to localhost`() {
        assertEquals("http://localhost:8000/", parsed("http://0.0.0.0:8000"))
        assertEquals("http://localhost:8000/", parsed("http://[::]:8000/"))
    }

    @Test
    fun `accepts a URL without a port`() {
        assertEquals("http://localhost/", parsed("http://localhost/"))
    }

    @Test
    fun `drops the query and fragment`() {
        assertEquals("http://localhost:8888/tree", parsed("http://localhost:8888/tree?token=abc123"))
        assertEquals("http://localhost:3000/", parsed("http://localhost:3000/#/home"))
        assertEquals("http://localhost:3000/", parsed("http://localhost:3000?x=1"))
    }

    @Test
    fun `keeps valid percent-encoding in the path`() {
        assertEquals("http://localhost:3000/a%20b", parsed("http://localhost:3000/a%20b"))
    }

    @Test
    fun `authority holds only host and port`() {
        val url = DevServerUrl.parse("http://localhost:8888/tree?token=abc123", emptySet()).getOrNull()
        assertEquals("localhost:8888", url?.authority)
    }

    @Test
    fun `rejects hosts that are not loopback`() {
        assertRejected("https://nextjs.org/telemetry")
        assertRejected("http://192.168.1.5:5173/")
        assertRejected("http://10.0.0.1:3000/")
        assertRejected("http://169.254.169.254/")
        assertRejected("http://128.0.0.1:3000/")
    }

    @Test
    fun `rejects schemes other than http and https`() {
        assertRejected("ws://127.0.0.1:9229/abc")
        assertRejected("javascript://%0Aalert(1)")
        assertRejected("file:///etc/passwd")
        assertRejected("view-source:http://localhost:3000/")
        assertRejected("chrome://crash")
        assertRejected("ftp://localhost/")
    }

    @Test
    fun `rejects userinfo`() {
        assertRejected("http://localhost:5173@evil.com/")
        assertRejected("http://user@localhost:5173/")
    }

    @Test
    fun `rejects backslashes anywhere`() {
        assertRejected("http://evil.com\\@localhost/")
        assertRejected("http://localhost\\evil.com/")
        assertRejected("http://localhost:3000/a\\b")
    }

    @Test
    fun `rejects hosts that only start like localhost`() {
        assertRejected("http://localhost.evil.com:1/")
        assertRejected("http://127.0.0.1.nip.io:1/")
        assertRejected("http://foo.localhost:1/")
        assertRejected("http://localhost.:1/")
    }

    @Test
    fun `rejects non-canonical IPv4 forms`() {
        assertRejected("http://0x7f.1:1/")
        assertRejected("http://2130706433:1/")
        assertRejected("http://127.1:1/")
        assertRejected("http://0177.0.0.1:1/")
        assertRejected("http://127.0.0.01:1/")
        assertRejected("http://127.0.0.256:1/")
    }

    @Test
    fun `rejects IPv6 forms other than the loopback`() {
        assertRejected("http://[::ffff:7f00:1]:1/")
        assertRejected("http://[::ffff:127.0.0.1]:1/")
        assertRejected("http://[0:0:0:0:0:0:0:1]:1/")
        assertRejected("http://[::1:1/")
    }

    @Test
    fun `rejects non-ASCII text`() {
        assertRejected("http://ｌｏｃａｌｈｏｓｔ:1/")
        assertRejected("http://localhost:3000/é")
        assertRejected("http://localhost:５１７３/")
    }

    @Test
    fun `rejects ports outside 1 to 65535 or with leading zeros`() {
        assertRejected("http://localhost:0/")
        assertRejected("http://localhost:65536/")
        assertRejected("http://localhost:05173/")
        assertRejected("http://localhost:/")
        assertRejected("http://localhost:abc/")
        assertRejected("http://localhost:99999999999/")
    }

    @Test
    fun `accepts the highest valid port`() {
        assertEquals("http://localhost:65535/", parsed("http://localhost:65535/"))
    }

    @Test
    fun `rejects an excluded port, including the scheme default`() {
        assertRejected("http://localhost:63342/api/x", setOf(63342))
        assertRejected("http://localhost/", setOf(80))
        assertRejected("https://localhost/", setOf(443))
    }

    @Test
    fun `rejects paths with characters outside the allowed set`() {
        assertRejected("http://localhost:3000/a b")
        assertRejected("http://localhost:3000/<script>")
        assertRejected("http://localhost:3000/a'b")
        assertRejected("http://localhost:3000/@vite/client")
        assertRejected("http://localhost:3000/a\u0000")
    }

    @Test
    fun `rejects malformed percent-encoding`() {
        assertRejected("http://localhost:3000/%zz")
        assertRejected("http://localhost:3000/%4")
    }

    @Test
    fun `rejects a path longer than 256 characters`() {
        val longPath = "/" + "a".repeat(256)
        assertRejected("http://localhost:3000$longPath")
        assertEquals("http://localhost:3000/" + "a".repeat(255), parsed("http://localhost:3000/" + "a".repeat(255)))
    }

    @Test
    fun `rejects an empty host`() {
        assertRejected("http://:3000/")
        assertRejected("http:///")
        assertRejected("http://")
    }
}
