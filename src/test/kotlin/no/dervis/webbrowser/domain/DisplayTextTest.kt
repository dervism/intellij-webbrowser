package no.dervis.webbrowser.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class DisplayTextTest {

    // Invisible characters are built from code points, never written literally in source.
    private fun char(code: Int) = String(Character.toChars(code))

    @Test
    fun `clean keeps ordinary text`() {
        assertEquals("My App", DisplayText.clean("My App"))
    }

    @Test
    fun `clean turns line breaks and tabs into single spaces`() {
        assertEquals("a b", DisplayText.clean("a\r\n\tb"))
    }

    @Test
    fun `clean removes other control characters`() {
        assertEquals("abcd", DisplayText.clean("a\u0000b\u007Fc\u0085d"))
    }

    @Test
    fun `clean removes every bidi control`() {
        val bidi = listOf(0x061C, 0x200E, 0x200F) + (0x202A..0x202E) + (0x2066..0x2069)
        assertEquals(12, bidi.size)
        bidi.forEach { code ->
            assertEquals("ab", DisplayText.clean("a${char(code)}b"), "U+%04X must be removed".format(code))
        }
    }

    @Test
    fun `clean removes zero-width characters and the byte order mark`() {
        listOf(0x200B, 0x2060, 0xFEFF).forEach { code ->
            assertEquals("ab", DisplayText.clean("a${char(code)}b"), "U+%04X must be removed".format(code))
        }
    }

    @Test
    fun `clean collapses and trims spaces`() {
        assertEquals("a b", DisplayText.clean("  a   b  "))
    }

    @Test
    fun `clean cuts to the maximum with an ellipsis`() {
        assertEquals("a".repeat(299) + "…", DisplayText.clean("a".repeat(301)))
        assertEquals("a".repeat(300), DisplayText.clean("a".repeat(300)))
    }

    @Test
    fun `clean never splits a surrogate pair at the cut`() {
        val emoji = char(0x1F600)
        assertEquals("a".repeat(298) + "…", DisplayText.clean("a".repeat(298) + emoji + "b"))
    }

    @Test
    fun `clean leaves markup as text`() {
        assertEquals("<html><b>x</b>", DisplayText.clean("<html><b>x</b>"))
    }

    @Test
    fun `clean of only invisible characters is empty`() {
        assertEquals("", DisplayText.clean("\u0000" + char(0x202E) + char(0x200B)))
    }

    @Test
    fun `htmlTooltip escapes markup and wraps it`() {
        assertEquals(
            "<html>&lt;html&gt;&lt;img src=x&gt;&amp;&quot;&#39;</html>",
            DisplayText.htmlTooltip("<html><img src=x>&\"'"),
        )
    }

    @Test
    fun `htmlTooltip cleans before escaping`() {
        assertEquals("<html>a b</html>", DisplayText.htmlTooltip("a\nb"))
    }
}
