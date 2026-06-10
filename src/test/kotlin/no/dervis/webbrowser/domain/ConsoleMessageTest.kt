package no.dervis.webbrowser.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class ConsoleMessageTest {

    @Test
    fun `levelFor maps CEF severities to our three buckets`() {
        assertEquals(ConsoleMessage.Level.INFO, ConsoleMessage.levelFor(-1)) // default
        assertEquals(ConsoleMessage.Level.INFO, ConsoleMessage.levelFor(0))  // verbose
        assertEquals(ConsoleMessage.Level.INFO, ConsoleMessage.levelFor(1))  // debug
        assertEquals(ConsoleMessage.Level.INFO, ConsoleMessage.levelFor(2))  // info
        assertEquals(ConsoleMessage.Level.WARNING, ConsoleMessage.levelFor(3))
        assertEquals(ConsoleMessage.Level.ERROR, ConsoleMessage.levelFor(4))
        assertEquals(ConsoleMessage.Level.ERROR, ConsoleMessage.levelFor(5)) // fatal
    }

    @Test
    fun `formatted shows level message and origin when source and line are present`() {
        val m = ConsoleMessage(
            ConsoleMessage.Level.ERROR,
            "x is not defined",
            "http://localhost:3000/app.js",
            42,
        )
        assertEquals("[error] x is not defined  (http://localhost:3000/app.js:42)", m.formatted())
    }

    @Test
    fun `formatted omits line number when none was reported`() {
        val m = ConsoleMessage(
            ConsoleMessage.Level.INFO,
            "hi",
            "http://localhost:3000/app.js",
            0,
        )
        assertEquals("[info] hi  (http://localhost:3000/app.js)", m.formatted())
    }

    @Test
    fun `formatted omits parens when source is blank`() {
        val m = ConsoleMessage(ConsoleMessage.Level.WARNING, "heads up", "", 0)
        assertEquals("[warning] heads up", m.formatted())
    }
}
