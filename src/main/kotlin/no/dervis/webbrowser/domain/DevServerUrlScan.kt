package no.dervis.webbrowser.domain

/** The output streams a dev-server URL is read from. The IDE's own system messages are not one of them. */
enum class ConsoleStream { STDOUT, STDERR }

/**
 * Reads a run configuration's console output, one chunk at a time, until it
 * finds the first [DevServerUrl]. Immutable: [feed] returns the next [Step].
 *
 * Each stream is joined into lines separately, so stdout and stderr pieces never
 * combine into a URL neither stream printed. Terminal escape sequences are removed
 * before a line is read, and a URL only counts when it starts a word, so
 * `view-source:http://…` is not read as `http://…`.
 *
 * The scan gives up after [MAX_SCANNED_CHARS] of output. Each chunk counts as at
 * least [MIN_CHUNK_COST] characters: joining a line costs a copy of its unfinished
 * part per chunk, so output that arrives one character at a time must use up the
 * budget sooner.
 */
class DevServerUrlScan private constructor(
    private val excludedPorts: Set<Int>,
    private val buffers: Map<ConsoleStream, ConsoleLineBuffer>,
    private val scannedChars: Long,
) {
    fun feed(stream: ConsoleStream, text: String): Step {
        val (buffer, lines) = buffers.getOrElse(stream) { ConsoleLineBuffer() }.append(text)
        val found = lines.firstNotNullOfOrNull(::firstUrlIn)
        val scanned = scannedChars + maxOf(text.length, MIN_CHUNK_COST)
        return when {
            found != null -> Step.Found(found)
            scanned >= MAX_SCANNED_CHARS -> Step.Exhausted
            else -> Step.Continue(DevServerUrlScan(excludedPorts, buffers + (stream to buffer), scanned))
        }
    }

    /**
     * The first URL on [stream]'s unfinished last line, read without changing anything.
     * It is for a server that prints its URL without a line end, and must only be used
     * once that stream has gone quiet, because an unfinished line can be cut anywhere.
     * A tail that ends inside an escape sequence is still arriving and gives null, and a
     * last word that ends in `:` (a host whose port hasn't arrived) is skipped.
     */
    fun scanTail(stream: ConsoleStream): DevServerUrl? {
        val tail = buffers[stream]?.unfinishedLine() ?: return null
        if (TerminalEscapes.endsInsideSequence(tail)) return null
        val words = wordsOf(tail)
        val settled = if (words.lastOrNull()?.endsWith(':') == true) words.dropLast(1) else words
        return settled.firstNotNullOfOrNull(::urlIn)
    }

    private fun firstUrlIn(line: String): DevServerUrl? = wordsOf(line).firstNotNullOfOrNull(::urlIn)

    private fun urlIn(word: String): DevServerUrl? =
        DevServerUrl.parse(trimClosers(word.trimStart(*WORD_OPENERS)), excludedPorts).getOrNull()

    sealed interface Step {
        /** The first acceptable URL. The scan is over. */
        data class Found(val url: DevServerUrl) : Step

        /** Nothing yet; feed the next chunk to [scan]. */
        data class Continue(val scan: DevServerUrlScan) : Step

        /** The output budget is used up. The scan is over. */
        data object Exhausted : Step
    }

    companion object {
        const val MAX_SCANNED_CHARS = 1_048_576L
        private const val MIN_CHUNK_COST = 64
        private val WORD_OPENERS = charArrayOf('(', '<', '[', '"', '\'')
        private val WORD_CLOSERS = charArrayOf('.', ',', ';', ':', '!', '?', ')', '>', ']', '}', '"', '\'')

        // ASCII whitespace plus the Unicode space, line and paragraph separators. These only
        // split words, after escape sequences are stripped; they never end a line, so a hidden
        // payload can't be cut loose from its escape sequence.
        private val WORD_SEPARATORS = Regex("[\\s\\p{Z}]+")

        /** The words of one line: escape sequences are stripped first, then the line is split. */
        private fun wordsOf(line: String): List<String> =
            TerminalEscapes.strip(line).split(WORD_SEPARATORS).filter(String::isNotEmpty)

        /**
         * Trailing punctuation off a word, except a `]` that closes a `[` in it (an IPv6
         * host). The brackets are counted once and the word is walked back by index, so
         * the cost stays linear in the word's length.
         */
        private fun trimClosers(word: String): String {
            val opens = word.count { it == '[' }
            tailrec fun keptLength(length: Int, closes: Int): Int {
                val last = word.getOrNull(length - 1) ?: return length
                val closesBracket = last == ']' && opens >= closes
                return if (last in WORD_CLOSERS && !closesBracket) {
                    keptLength(length - 1, if (last == ']') closes - 1 else closes)
                } else {
                    length
                }
            }
            return word.substring(0, keptLength(word.length, word.count { it == ']' }))
        }

        fun start(excludedPorts: Set<Int>): DevServerUrlScan = DevServerUrlScan(excludedPorts, emptyMap(), 0)
    }
}

/**
 * Removes ANSI/VT escape sequences from one console line. CSI sequences (colours)
 * go. So do the string sequences (OSC, DCS, SOS, PM and APC) together with their
 * payload, which is where an OSC 8 hyperlink keeps its hidden target. Remaining
 * control characters become spaces. Possessive quantifiers keep matching linear
 * in the line length.
 */
internal object TerminalEscapes {
    private val SEQUENCE = Regex(
        "\u001B\\[[0-?]*+[ -/]*+[@-~]" +
            "|\u001B[\\]PX^_][^\u0007\u001B]*+(?:\u0007|\u001B\\\\)?" +
            "|\u001B[@-Z\\\\-_]",
    )
    private val CONTROL = Regex("[\u0000-\u001F\u007F]")

    // A sequence cut off at the end of the text: a bare ESC, a CSI without its final
    // byte, or a string sequence (OSC, DCS, SOS, PM, APC) without its terminator.
    private val UNFINISHED = Regex("\u001B(?:\\[[0-?]*+[ -/]*+|[\\]PX^_][^\u0007\u001B]*+)?\\z")

    fun strip(line: String): String = line.replace(SEQUENCE, "").replace(CONTROL, " ")

    /** Whether [text] ends in the middle of an escape sequence, so more of it is still to come. */
    fun endsInsideSequence(text: String): Boolean = UNFINISHED.containsMatchIn(text)
}
