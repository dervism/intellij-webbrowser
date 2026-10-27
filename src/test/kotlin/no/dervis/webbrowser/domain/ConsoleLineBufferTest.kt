package no.dervis.webbrowser.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class ConsoleLineBufferTest {

    @Test
    fun `emits complete lines in order`() {
        val (_, lines) = ConsoleLineBuffer().append("first\nsecond\n")
        assertEquals(listOf("first", "second"), lines)
    }

    @Test
    fun `holds a partial line until its newline arrives`() {
        val (buffer, early) = ConsoleLineBuffer().append("Local: http://localhost:51")
        assertEquals(emptyList(), early)

        val (_, lines) = buffer.append("73/\n")
        assertEquals(listOf("Local: http://localhost:5173/"), lines)
    }

    @Test
    fun `treats CR, LF and CRLF as line ends and skips empty lines`() {
        val (_, lines) = ConsoleLineBuffer().append("a\r\nb\rc\n\n")
        assertEquals(listOf("a", "b", "c"), lines)
    }

    @Test
    fun `keeps a line of exactly the maximum length`() {
        val line = "z".repeat(ConsoleLineBuffer.MAX_LINE_LENGTH)
        val (_, lines) = ConsoleLineBuffer().append("$line\n")
        assertEquals(listOf(line), lines)
    }

    @Test
    fun `drops a complete line longer than the maximum`() {
        val (_, lines) = ConsoleLineBuffer().append("y".repeat(5000) + "\nok\n")
        assertEquals(listOf("ok"), lines)
    }

    @Test
    fun `drops the tail of an overlong line that arrives in a later chunk`() {
        val (buffer, early) = ConsoleLineBuffer().append("x".repeat(5000))
        assertEquals(emptyList(), early)

        val (_, lines) = buffer.append("http://localhost:1/\nnext\n")
        assertEquals(listOf("next"), lines)
    }

    @Test
    fun `the unfinished line is the text after the last line end`() {
        val (buffer, _) = ConsoleLineBuffer().append("done\nLocal: http://localhost:51")
        assertEquals("Local: http://localhost:51", buffer.unfinishedLine())
        assertEquals(null, ConsoleLineBuffer().append("done\n").first.unfinishedLine())
    }

    @Test
    fun `there is no unfinished line while an overlong line is dropped`() {
        assertEquals(null, ConsoleLineBuffer().append("x".repeat(5000)).first.unfinishedLine())
    }

    @Test
    fun `keeps dropping an overlong line across several chunks`() {
        val (first, _) = ConsoleLineBuffer().append("x".repeat(5000))
        val (second, middle) = first.append(" http://localhost:1/")
        assertEquals(emptyList(), middle)

        val (_, lines) = second.append("\nnext\n")
        assertEquals(listOf("next"), lines)
    }
}
