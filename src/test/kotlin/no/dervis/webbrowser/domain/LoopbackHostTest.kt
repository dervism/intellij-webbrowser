package no.dervis.webbrowser.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LoopbackHostTest {

    @Test
    fun `literal loopback hosts are canonical`() {
        assertEquals("localhost", LoopbackHost.canonical("localhost"))
        assertEquals("localhost", LoopbackHost.canonical("LocalHost"))
        assertEquals("127.0.0.1", LoopbackHost.canonical("127.0.0.1"))
        assertEquals("127.1.20.255", LoopbackHost.canonical("127.1.20.255"))
        assertEquals("[::1]", LoopbackHost.canonical("[::1]"))
    }

    @Test
    fun `wildcard bind addresses mean localhost`() {
        assertEquals("localhost", LoopbackHost.canonical("0.0.0.0"))
        assertEquals("localhost", LoopbackHost.canonical("[::]"))
    }

    @Test
    fun `anything else is not loopback`() {
        listOf(
            "localhost.evil.com", "127.0.0.1.nip.io", "foo.localhost", "127.1", "127.0.0.01",
            "127.0.0.256", "0x7f.1", "[::ffff:127.0.0.1]", "example.com", "",
            "127.0.0.1.5", "10.0.0.1", "192.168.1.127",
        ).forEach { assertNull(LoopbackHost.canonical(it), "must reject $it") }
    }
}
