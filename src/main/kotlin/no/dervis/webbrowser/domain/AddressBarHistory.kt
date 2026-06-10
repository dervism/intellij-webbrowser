package no.dervis.webbrowser.domain

/**
 * Per-project, bounded LRU of URLs the user has navigated to. Drives the
 * autocomplete dropdown attached to the address bar.
 *
 * Entries are stored in most-recently-used-first order. Recording an entry
 * that's already present moves it to the front rather than duplicating it.
 * Capacity defaults to [DEFAULT_CAPACITY]; recording past the cap drops the
 * least-recently-used entry, so an aggressive browsing session can't blow
 * settings serialization budgets.
 */
data class AddressBarHistory(
    val entries: List<String>,
    val capacity: Int = DEFAULT_CAPACITY,
) {
    val isEmpty: Boolean get() = entries.isEmpty()
    val size: Int get() = entries.size

    /**
     * Move [url] to the front of the list, dropping any older copy and
     * dropping the LRU tail if we'd exceed [capacity]. Blank inputs and
     * non-http(s) URLs are ignored — restoring them later would be useless.
     */
    fun record(url: String): AddressBarHistory {
        val normalised = url.trim()
        if (!isRecordable(normalised)) return this
        if (capacity <= 0) return this
        val withoutDup = entries.filterNot { it.equals(normalised, ignoreCase = true) }
        val moved = listOf(normalised) + withoutDup
        return copy(entries = moved.take(capacity))
    }

    /**
     * Return the most-recent entries whose value contains [query]
     * case-insensitively, capped at [limit] results. Used to feed the
     * autocomplete dropdown — empty [query] returns the most-recent
     * entries verbatim.
     */
    fun suggest(query: String, limit: Int = DEFAULT_SUGGESTION_LIMIT): List<String> {
        if (entries.isEmpty() || limit <= 0) return emptyList()
        val q = query.trim()
        if (q.isEmpty()) return entries.take(limit)
        return entries.asSequence()
            .filter { it.contains(q, ignoreCase = true) }
            .take(limit)
            .toList()
    }

    companion object {
        const val DEFAULT_CAPACITY = 50
        const val DEFAULT_SUGGESTION_LIMIT = 8
        val EMPTY = AddressBarHistory(emptyList())

        private fun isRecordable(url: String): Boolean =
            url.isNotEmpty() && (url.startsWith("http://") || url.startsWith("https://"))
    }
}
