package no.dervis.webbrowser.domain

/**
 * Makes text that a web page or a project file controls safe to show in Swing,
 * where a string starting with `<html>` is rendered as HTML.
 *
 * [clean] removes what could change how the text reads (control characters,
 * bidi overrides and zero-width characters) and caps the length. It does not
 * escape markup, so callers that put the result in a label must also turn HTML
 * rendering off. [htmlTooltip] is for tooltips, which may be rendered as HTML
 * whatever the text looks like: it escapes the text and wraps it, so it always
 * shows literally.
 */
object DisplayText {

    const val MAX_LENGTH = 300

    private val LINE_BREAKS = setOf('\n', '\r', '\t')

    // Built from code points so the source never holds the invisible characters themselves.
    private val INVISIBLE: Set<Char> =
        (listOf(0x061C, 0x200B, 0x200E, 0x200F, 0x2060, 0xFEFF) + (0x202A..0x202E) + (0x2066..0x2069))
            .map(::Char)
            .toSet()

    private val WHITESPACE_RUN = Regex("(?U)\\s+")
    private val HTML_ESCAPES = mapOf('&' to "&amp;", '<' to "&lt;", '>' to "&gt;", '"' to "&quot;", '\'' to "&#39;")

    /**
     * [raw] with line breaks and tabs turned into spaces, other control characters
     * and invisible characters removed, runs of spaces collapsed, and the ends
     * trimmed. Longer than [MAX_LENGTH], it is cut (never inside a surrogate pair)
     * and ends with "…".
     */
    fun clean(raw: String): String {
        val visible = raw
            .map { if (it in LINE_BREAKS) ' ' else it }
            .filterNot { it.isISOControl() || it in INVISIBLE }
            .joinToString("")
            .replace(WHITESPACE_RUN, " ")
            .trim()
        if (visible.length <= MAX_LENGTH) return visible
        val cut = visible.take(MAX_LENGTH - 1).let { if (it.last().isHighSurrogate()) it.dropLast(1) else it }
        return cut.trimEnd() + "…"
    }

    /** [raw], cleaned and escaped, inside `<html>…</html>`. */
    fun htmlTooltip(raw: String): String =
        "<html>" + clean(raw).map { HTML_ESCAPES[it] ?: it.toString() }.joinToString("") + "</html>"
}
