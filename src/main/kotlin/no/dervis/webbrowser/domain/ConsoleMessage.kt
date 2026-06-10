package no.dervis.webbrowser.domain

/**
 * One line out of Chromium's `window.console` channel. Pure value object so the
 * console panel's formatting + filtering rules can be tested without standing
 * up an embedded browser.
 *
 * [Level] mirrors the CEF log-severity enum (info / warning / error are the
 * three buckets we care about; `debug` and `verbose` collapse into [INFO]).
 */
data class ConsoleMessage(
    val level: Level,
    val text: String,
    val source: String,
    val line: Int,
) {
    enum class Level { INFO, WARNING, ERROR }

    /** A single-line `[level] message  (source:line)` rendering for plain log/text views. */
    fun formatted(): String {
        val origin = formattedOrigin()
        return if (origin.isEmpty()) "[${level.name.lowercase()}] $text"
        else "[${level.name.lowercase()}] $text  ($origin)"
    }

    private fun formattedOrigin(): String {
        val s = source.trim()
        return when {
            s.isEmpty() -> ""
            line > 0 -> "$s:$line"
            else -> s
        }
    }

    companion object {
        /** Map CEF's `LOGSEVERITY_*` int values to our [Level] buckets. */
        fun levelFor(cefSeverity: Int): Level = when (cefSeverity) {
            // CEF: -1 default, 0 verbose, 1 debug, 2 info, 3 warning, 4 error, 5 fatal
            in Int.MIN_VALUE..2 -> Level.INFO
            3 -> Level.WARNING
            else -> Level.ERROR
        }
    }
}
