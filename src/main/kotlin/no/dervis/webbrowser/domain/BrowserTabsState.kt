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
    val pinned: Set<TabId> = emptySet(),
) {
    val isEmpty: Boolean get() = tabs.isEmpty()
    val size: Int get() = tabs.size

    fun contains(id: TabId): Boolean = id in tabs
    fun isPinned(id: TabId): Boolean = id in pinned

    /**
     * Open [id]. If it's already in the tab list, this just activates it.
     * Otherwise it's appended (after any pinned tabs, before unpinned tabs
     * would be ambiguous so we keep the simple rule: new tabs append).
     */
    fun open(id: TabId): BrowserTabsState =
        if (id in tabs) copy(activeId = id)
        else copy(tabs = tabs + id, activeId = id)

    /**
     * Close [id]. If [id] is not present, returns this unchanged.
     * If [id] is the active tab, activates the right neighbour (or the new
     * last tab when closing the last position). If [id] is the only tab,
     * the new state is empty with no active id. Closing a pinned tab also
     * drops its pin.
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
        return BrowserTabsState(remaining, newActive, pinned - id)
    }

    /** Activate [id] if it exists; no-op otherwise. */
    fun activate(id: TabId): BrowserTabsState =
        if (id in tabs) copy(activeId = id) else this

    /**
     * Move the tab at [fromIndex] to [toIndex]. Indices are clamped into the
     * valid range; a no-op move (same index) returns this unchanged. Active
     * tab and pin set follow the moved id, so visual order matches state.
     */
    fun reorder(fromIndex: Int, toIndex: Int): BrowserTabsState {
        if (tabs.isEmpty()) return this
        val from = fromIndex.coerceIn(0, tabs.lastIndex)
        val to = toIndex.coerceIn(0, tabs.lastIndex)
        if (from == to) return this
        val moved = tabs.toMutableList()
        val item = moved.removeAt(from)
        moved.add(to, item)
        return copy(tabs = moved)
    }

    /**
     * Reorder the tab list to match [order]. Ids in [order] that aren't open
     * are ignored; open ids missing from [order] are appended in their current
     * relative order (defensive — the caller should pass a full permutation).
     * Active id and pin set are preserved.
     */
    fun reorderedTo(order: List<TabId>): BrowserTabsState {
        if (tabs.isEmpty()) return this
        val known = order.filter { it in tabs }
        val missing = tabs.filter { it !in known }
        val next = known + missing
        return if (next == tabs) this else copy(tabs = next)
    }

    /** Mark [id] as pinned. No-op when [id] isn't in the tab list. */
    fun pin(id: TabId): BrowserTabsState =
        if (id in tabs) copy(pinned = pinned + id) else this

    /** Remove the pin from [id]. No-op when [id] isn't pinned. */
    fun unpin(id: TabId): BrowserTabsState = copy(pinned = pinned - id)

    /** Activate the tab one step right of the active one; wraps at the end. */
    fun activateNext(): BrowserTabsState = step(+1)

    /** Activate the tab one step left of the active one; wraps at the start. */
    fun activatePrevious(): BrowserTabsState = step(-1)

    private fun step(direction: Int): BrowserTabsState {
        if (tabs.size < 2) return this
        val current = tabs.indexOf(activeId).takeIf { it >= 0 } ?: return this
        val next = (current + direction).mod(tabs.size)
        return copy(activeId = tabs[next])
    }

    companion object {
        val EMPTY = BrowserTabsState(emptyList(), null, emptySet())

        fun withInitial(id: TabId): BrowserTabsState =
            BrowserTabsState(listOf(id), id, emptySet())
    }
}
