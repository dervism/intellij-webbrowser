package no.dervis.webbrowser.domain

/**
 * Pure snapshot of the browser tabs at a point in time — what gets persisted to
 * project settings and replayed when the tool window re-opens. The strings hold
 * raw URLs so the serialized shape is platform-neutral and trivially
 * round-trippable through IntelliJ's `PersistentStateComponent` machinery.
 *
 * Only http(s) URLs are restorable: a tab that was showing the placeholder, a
 * `view-source:` view, or a `data:` URL is silently dropped on capture, since
 * none of those have meaningful "go back to that page" semantics. Stored
 * entries are checked again on restore, because the stored file can be edited.
 */
data class TabSession(
    val urls: List<String>,
    val activeIndex: Int,
    val pinnedUrls: Set<String> = emptySet(),
) {

    val isEmpty: Boolean get() = urls.isEmpty()

    /**
     * The tabs to recreate, in order. Entries [Url.web] rejects are dropped, and the
     * pin flags and the active tab stay with the entries they belong to. At most
     * [TabLimits.MAX_RESTORED_TABS] tabs come back. When the active entry was dropped,
     * cut off or never valid, the first tab becomes active; with no tabs it is -1.
     */
    fun restorePlan(): RestorePlan {
        val kept = urls.withIndex()
            .mapNotNull { (index, raw) ->
                Url.web(raw).getOrNull()?.let { IndexedValue(index, RestoredTab(it, raw in pinnedUrls)) }
            }
            .take(TabLimits.MAX_RESTORED_TABS)
        val active = kept.indexOfFirst { it.index == activeIndex }
        return RestorePlan(
            tabs = kept.map { it.value },
            activeIndex = when {
                kept.isEmpty() -> -1
                active >= 0 -> active
                else -> 0
            },
        )
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

        private fun isRestorable(url: String): Boolean = Url.web(url).isSome()
    }
}

/** One tab to recreate on restore. */
data class RestoredTab(val url: Url, val pinned: Boolean)

/** The tabs to recreate, and which of them to activate (-1 when there are none). */
data class RestorePlan(val tabs: List<RestoredTab>, val activeIndex: Int)
