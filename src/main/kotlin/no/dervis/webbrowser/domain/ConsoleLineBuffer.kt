package no.dervis.webbrowser.domain

/**
 * Joins run-console output chunks into whole lines. A process can split a line
 * across chunks (coloured output arrives in pieces), so the unfinished tail is
 * kept until its line end arrives. CR, LF and CRLF all end a line.
 *
 * Immutable: [append] returns the next buffer and the lines the chunk completed.
 * A line longer than [MAX_LINE_LENGTH] is dropped whole, including the part that
 * arrives in later chunks, so a cut-off prefix is never read as a shorter line.
 */
class ConsoleLineBuffer private constructor(
    private val pending: String,
    private val discarding: Boolean,
) {
    constructor() : this(pending = "", discarding = false)

    fun append(chunk: String): Pair<ConsoleLineBuffer, List<String>> {
        val parts = (pending + chunk).split('\n', '\r')
        val tail = parts.last()
        val ended = parts.dropLast(1).let { if (discarding) it.drop(1) else it }
        val lines = ended.filter { it.isNotEmpty() && it.length <= MAX_LINE_LENGTH }
        val stillDiscarding = (discarding && parts.size == 1) || tail.length > MAX_LINE_LENGTH
        val next = if (stillDiscarding) ConsoleLineBuffer("", discarding = true) else ConsoleLineBuffer(tail, false)
        return next to lines
    }

    companion object {
        const val MAX_LINE_LENGTH = 4096
    }
}
