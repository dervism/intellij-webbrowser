package no.dervis.webbrowser.domain

/**
 * Bounded, immutable stack of recently-closed tabs. Drives the
 * "Reopen Closed Tab" (Ctrl/Cmd+Shift+T) shortcut.
 *
 * Pure value object: no IntelliJ types. Entries store a [Url] (where to navigate
 * back to) plus an optional [title] for nicer display when the reopen is shown
 * in a menu later — currently we just navigate and let JCEF set the title.
 *
 * Capacity defaults to [DEFAULT_CAPACITY]; pushing past the cap silently drops
 * the oldest entry, so an aggressive open/close cycle can't grow the stack.
 */
data class ClosedTabHistory(
    val entries: List<Entry>,
    val capacity: Int = DEFAULT_CAPACITY,
) {
    data class Entry(val url: Url, val title: String)

    val isEmpty: Boolean get() = entries.isEmpty()
    val size: Int get() = entries.size

    /** Push a closed-tab record onto the top of the stack. */
    fun push(entry: Entry): ClosedTabHistory {
        val capped = (entries + entry).takeLast(capacity)
        return copy(entries = capped)
    }

    /**
     * Pop the most-recently-closed entry. Returns the popped entry plus the new
     * history. Returns `null` when the stack is empty — callers can treat that
     * as a no-op trigger.
     */
    fun pop(): Pair<Entry, ClosedTabHistory>? {
        val top = entries.lastOrNull() ?: return null
        return top to copy(entries = entries.dropLast(1))
    }

    companion object {
        const val DEFAULT_CAPACITY = 10
        val EMPTY = ClosedTabHistory(emptyList())
    }
}
