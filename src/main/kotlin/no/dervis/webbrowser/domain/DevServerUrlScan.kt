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

    private fun firstUrlIn(line: String): DevServerUrl? =
        TerminalEscapes.strip(line)
            .split(' ')
            .firstNotNullOfOrNull { word ->
                DevServerUrl.parse(word.trimStart(*WORD_OPENERS).trimEnd(*WORD_CLOSERS), excludedPorts).getOrNull()
            }

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

    fun strip(line: String): String = line.replace(SEQUENCE, "").replace(CONTROL, " ")
}
