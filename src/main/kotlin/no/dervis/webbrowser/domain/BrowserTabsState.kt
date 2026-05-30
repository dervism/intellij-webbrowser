package no.dervis.webbrowser.domain

/**
 * Pure aggregate describing which tabs are open and which one is active.
 * Every mutation returns a new state — the UI layer reconciles actual JCEF
 * browser instances against this state.
 *
 * Closing the active tab activates its right neighbour (or the new last tab
 * if it was at the end). Closing the only tab returns an empty state.
 */
data class BrowserTabsState(
    val tabs: List<TabId>,
    val activeId: TabId?,
) {
    val isEmpty: Boolean get() = tabs.isEmpty()
    val size: Int get() = tabs.size

    fun contains(id: TabId): Boolean = id in tabs

    /**
     * Open [id]. If it's already in the tab list, this just activates it.
     * Otherwise it's appended and becomes the active tab.
     */
    fun open(id: TabId): BrowserTabsState =
        if (id in tabs) copy(activeId = id)
        else copy(tabs = tabs + id, activeId = id)

    /**
     * Close [id]. If [id] is not present, returns this unchanged.
     * If [id] is the active tab, activates the right neighbour (or the new
     * last tab when closing the last position). If [id] is the only tab,
     * the new state is empty with no active id.
     */
    fun close(id: TabId): BrowserTabsState {
        val closedIdx = tabs.indexOf(id)
        if (closedIdx < 0) return this
        val remaining = tabs.toMutableList().also { it.removeAt(closedIdx) }
        val newActive: TabId? = when {
            remaining.isEmpty() -> null
            activeId != id -> activeId
            else -> remaining[closedIdx.coerceAtMost(remaining.size - 1)]
        }
        return BrowserTabsState(remaining, newActive)
    }

    /** Activate [id] if it exists; no-op otherwise. */
    fun activate(id: TabId): BrowserTabsState =
        if (id in tabs) copy(activeId = id) else this

    companion object {
        val EMPTY = BrowserTabsState(emptyList(), null)

        fun withInitial(id: TabId): BrowserTabsState = BrowserTabsState(listOf(id), id)
    }
}
