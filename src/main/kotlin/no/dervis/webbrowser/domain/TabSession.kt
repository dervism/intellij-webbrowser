package no.dervis.webbrowser.domain

/**
 * Pure snapshot of the browser tabs at a point in time — what gets persisted to
 * project settings and replayed when the tool window re-opens. The strings hold
 * raw URLs so the serialized shape is platform-neutral and trivially
 * round-trippable through IntelliJ's `PersistentStateComponent` machinery.
 *
 * Only http(s) URLs are restorable: a tab that was showing the placeholder, a
 * `view-source:` view, or a `data:` URL is silently dropped on capture, since
 * none of those have meaningful "go back to that page" semantics.
 *
 * The active index is clamped to a valid range on rebuild so a stale or
 * truncated session can never select an out-of-bounds tab.
 */
data class TabSession(
    val urls: List<String>,
    val activeIndex: Int,
    val pinnedUrls: Set<String> = emptySet(),
) {

    val isEmpty: Boolean get() = urls.isEmpty()

    /** Resolved Url values for each persisted entry, in tab order. */
    fun resolvedUrls(): List<Url> = urls.mapNotNull { Url.parse(it).getOrNull() }

    /** Whether the persisted url at [index] should be restored as pinned. */
    fun isPinnedAt(index: Int): Boolean =
        urls.getOrNull(index)?.let { it in pinnedUrls } ?: false

    /** Active index clamped into the valid range; `-1` when the session is empty. */
    fun safeActiveIndex(): Int = when {
        urls.isEmpty() -> -1
        else -> activeIndex.coerceIn(0, urls.size - 1)
    }

    companion object {
        val EMPTY = TabSession(emptyList(), -1)

        /**
         * Build a session from the currently-open tab URLs. [rawUrls] is in tab
         * order; entries are kept only when they look like real http(s) URLs.
         * [activeRawUrl] is the URL of the currently active tab — its position
         * in the filtered list becomes [activeIndex] (defaults to `0` when the
         * active tab was dropped). [pinnedRawUrls] is the subset of [rawUrls]
         * that should round-trip as pinned; entries not in the kept list are
         * dropped from the pinned set silently.
         */
        fun capture(
            rawUrls: List<String>,
            activeRawUrl: String?,
            pinnedRawUrls: Set<String> = emptySet(),
        ): TabSession {
            val kept = rawUrls.filter(::isRestorable)
            if (kept.isEmpty()) return EMPTY
            val active = activeRawUrl
                ?.takeIf(::isRestorable)
                ?.let(kept::indexOf)
                ?.takeIf { it >= 0 }
                ?: 0
            return TabSession(kept, active, pinnedRawUrls.filter(::isRestorable).toSet())
        }

        private fun isRestorable(url: String): Boolean =
            url.startsWith("http://") || url.startsWith("https://")
    }
}
