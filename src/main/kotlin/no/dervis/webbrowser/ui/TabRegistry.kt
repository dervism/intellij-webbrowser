package no.dervis.webbrowser.ui

import no.dervis.webbrowser.domain.BrowserTabsState
import no.dervis.webbrowser.domain.TabId

/**
 * UI-layer aggregate that owns the canonical pairing of [BrowserTabsState]
 * (the pure aggregate of which tabs exist, which is active, which are pinned)
 * with the live JCEF [BrowserTabPane] instances.
 *
 * Previously the [WebBrowserPanel] kept these in two separate fields:
 *
 * ```
 *   private var tabsState: BrowserTabsState   // ordering / active / pinned
 *   private val tabs: MutableMap<TabId, …>    // JCEF panes
 * ```
 *
 * …and the invariant *"every TabId in `tabsState` has a pane in `tabs`"* lived
 * only in the panel's comments. Routing every mutation through here makes the
 * pairing impossible to break by accident: there is no public way to add a
 * [TabId] to the state without also handing over its pane.
 *
 * The registry holds **no Swing components** itself — the [BrowserTabPane]
 * stays the owner of the JCEF browser and the tab-strip component is still
 * managed by [WebBrowserPanel]. Think of this as the in-memory tab model;
 * the panel still does the JTabbedPane wiring on top of it.
 */
internal class TabRegistry {

    private var stateRef: BrowserTabsState = BrowserTabsState.EMPTY
    private val panes = LinkedHashMap<TabId, BrowserTabPane>()

    /** Read-only view of the underlying domain aggregate. */
    val state: BrowserTabsState get() = stateRef

    val isEmpty: Boolean get() = stateRef.isEmpty
    val size: Int get() = stateRef.size

    fun pane(id: TabId): BrowserTabPane? = panes[id]

    /** The currently active pane, or `null` when no tab is selected. */
    fun active(): BrowserTabPane? = stateRef.activeId?.let(panes::get)

    /** Every live pane, in insertion order. */
    fun all(): List<BrowserTabPane> = panes.values.toList()

    /** The TabId at [index] in current order, or `null` if out of range. */
    fun idAt(index: Int): TabId? = stateRef.tabs.getOrNull(index)

    /** The index of [id] in current order, or `-1` if absent. */
    fun indexOf(id: TabId): Int = stateRef.tabs.indexOf(id)

    // ---- Mutations -----------------------------------------------------------
    //
    // All mutators return the new BrowserTabsState so callers can pattern-match
    // on what changed (e.g. the panel uses it to decide if a different tab is
    // now active and the toolbar needs refreshing).

    /** Add [pane] as a new tab and make it active. */
    fun add(pane: BrowserTabPane): BrowserTabsState {
        panes[pane.id] = pane
        stateRef = if (stateRef.isEmpty)
            BrowserTabsState.withInitial(pane.id)
        else
            stateRef.open(pane.id)
        return stateRef
    }

    /**
     * Remove the pane for [id]. Returns the removed pane (so the caller can
     * dispose it), or `null` if [id] wasn't registered.
     */
    fun remove(id: TabId): BrowserTabPane? {
        val pane = panes.remove(id) ?: return null
        stateRef = stateRef.close(id)
        return pane
    }

    /** Activate [id] if it exists; no-op otherwise. */
    fun activate(id: TabId): BrowserTabsState {
        stateRef = stateRef.activate(id)
        return stateRef
    }

    /** Cycle active tab forward; wraps at the end. */
    fun activateNext(): BrowserTabsState {
        stateRef = stateRef.activateNext()
        return stateRef
    }

    /** Cycle active tab backward; wraps at the start. */
    fun activatePrevious(): BrowserTabsState {
        stateRef = stateRef.activatePrevious()
        return stateRef
    }

    /**
     * Move the tab at [fromIndex] to [toIndex]. Updates both the domain order
     * and the internal pane ordering (so [all] reflects the new sequence).
     */
    fun reorder(fromIndex: Int, toIndex: Int): BrowserTabsState {
        val previous = stateRef
        stateRef = stateRef.reorder(fromIndex, toIndex)
        if (stateRef.tabs != previous.tabs) {
            // Re-key the LinkedHashMap so iteration order matches the state.
            val rekeyed = LinkedHashMap<TabId, BrowserTabPane>(panes.size)
            stateRef.tabs.forEach { id -> panes[id]?.let { rekeyed[id] = it } }
            panes.clear()
            panes.putAll(rekeyed)
        }
        return stateRef
    }

    /**
     * Resync the tab order to [orderedIds] — used after the platform tab
     * component reports a drag-reorder. Re-keys the pane map so iteration
     * order stays aligned with the model.
     */
    fun syncOrder(orderedIds: List<TabId>): BrowserTabsState {
        val previous = stateRef
        stateRef = stateRef.reorderedTo(orderedIds)
        if (stateRef.tabs != previous.tabs) {
            val rekeyed = LinkedHashMap<TabId, BrowserTabPane>(panes.size)
            stateRef.tabs.forEach { id -> panes[id]?.let { rekeyed[id] = it } }
            panes.clear()
            panes.putAll(rekeyed)
        }
        return stateRef
    }

    fun pin(id: TabId): BrowserTabsState {
        stateRef = stateRef.pin(id)
        return stateRef
    }

    fun unpin(id: TabId): BrowserTabsState {
        stateRef = stateRef.unpin(id)
        return stateRef
    }

    fun isPinned(id: TabId): Boolean = stateRef.isPinned(id)
}
