package no.dervis.webbrowser.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BrowserTabsStateTest {

    private val a = TabId(1)
    private val b = TabId(2)
    private val c = TabId(3)

    // ---- construction ---------------------------------------------------------

    @Test
    fun `EMPTY has no tabs and no active id`() {
        assertTrue(BrowserTabsState.EMPTY.isEmpty)
        assertNull(BrowserTabsState.EMPTY.activeId)
        assertEquals(0, BrowserTabsState.EMPTY.size)
    }

    @Test
    fun `withInitial creates a single-tab state with that tab active`() {
        val state = BrowserTabsState.withInitial(a)
        assertEquals(listOf(a), state.tabs)
        assertEquals(a, state.activeId)
        assertEquals(1, state.size)
        assertFalse(state.isEmpty)
    }

    // ---- open ----------------------------------------------------------------

    @Test
    fun `open adds the tab and makes it active`() {
        val state = BrowserTabsState.EMPTY.open(a)
        assertEquals(listOf(a), state.tabs)
        assertEquals(a, state.activeId)
    }

    @Test
    fun `open multiple tabs preserves insertion order and activates the latest`() {
        val state = BrowserTabsState.EMPTY.open(a).open(b).open(c)
        assertEquals(listOf(a, b, c), state.tabs)
        assertEquals(c, state.activeId)
    }

    @Test
    fun `opening a tab that already exists just activates it (no duplicates)`() {
        val state = BrowserTabsState.EMPTY.open(a).open(b).open(a)
        assertEquals(listOf(a, b), state.tabs)
        assertEquals(a, state.activeId)
    }

    // ---- close ----------------------------------------------------------------

    @Test
    fun `closing a non-existent tab is a no-op`() {
        val state = BrowserTabsState.EMPTY.open(a)
        assertEquals(state, state.close(b))
    }

    @Test
    fun `closing a non-active tab leaves the active id unchanged`() {
        val state = BrowserTabsState.EMPTY.open(a).open(b).open(c) // active: c
        val next = state.close(a)
        assertEquals(listOf(b, c), next.tabs)
        assertEquals(c, next.activeId)
    }

    @Test
    fun `closing the active tab in the middle activates its right neighbour`() {
        val state = BrowserTabsState.EMPTY.open(a).open(b).open(c).activate(b)
        // tabs [a, b, c], active b → close b → active becomes c
        val next = state.close(b)
        assertEquals(listOf(a, c), next.tabs)
        assertEquals(c, next.activeId)
    }

    @Test
    fun `closing the active last tab activates the new last tab`() {
        val state = BrowserTabsState.EMPTY.open(a).open(b).open(c) // active: c
        val next = state.close(c)
        assertEquals(listOf(a, b), next.tabs)
        assertEquals(b, next.activeId)
    }

    @Test
    fun `closing the only tab returns the empty state`() {
        val state = BrowserTabsState.withInitial(a).close(a)
        assertTrue(state.isEmpty)
        assertNull(state.activeId)
    }

    // ---- activate ------------------------------------------------------------

    @Test
    fun `activate changes the active id but preserves tab order`() {
        val state = BrowserTabsState.EMPTY.open(a).open(b).activate(a)
        assertEquals(a, state.activeId)
        assertEquals(listOf(a, b), state.tabs)
    }

    @Test
    fun `activating a non-existent tab is a no-op`() {
        val state = BrowserTabsState.EMPTY.open(a).activate(c)
        assertEquals(a, state.activeId)
    }

    // ---- contains ------------------------------------------------------------

    @Test
    fun `contains reflects membership`() {
        val state = BrowserTabsState.EMPTY.open(a).open(b)
        assertTrue(state.contains(a))
        assertTrue(state.contains(b))
        assertFalse(state.contains(c))
    }

    // ---- reorder -------------------------------------------------------------

    @Test
    fun `reorder moves a tab forward in the list`() {
        val state = BrowserTabsState.EMPTY.open(a).open(b).open(c) // [a, b, c]
        val moved = state.reorder(0, 2)
        assertEquals(listOf(b, c, a), moved.tabs)
        assertEquals(c, moved.activeId) // active id untouched
    }

    @Test
    fun `reorder moves a tab backward in the list`() {
        val state = BrowserTabsState.EMPTY.open(a).open(b).open(c) // [a, b, c]
        val moved = state.reorder(2, 0)
        assertEquals(listOf(c, a, b), moved.tabs)
    }

    @Test
    fun `reorder with same index is a no-op`() {
        val state = BrowserTabsState.EMPTY.open(a).open(b)
        assertEquals(state, state.reorder(1, 1))
    }

    @Test
    fun `reorder clamps out-of-range indices`() {
        val state = BrowserTabsState.EMPTY.open(a).open(b).open(c)
        // -5 → 0, 99 → 2: equivalent to reorder(0, 2)
        val moved = state.reorder(-5, 99)
        assertEquals(listOf(b, c, a), moved.tabs)
    }

    @Test
    fun `reorder on empty state is a no-op`() {
        assertEquals(BrowserTabsState.EMPTY, BrowserTabsState.EMPTY.reorder(0, 1))
    }

    // ---- reorderedTo ---------------------------------------------------------

    @Test
    fun `reorderedTo applies a full permutation`() {
        val state = BrowserTabsState.EMPTY.open(a).open(b).open(c) // [a, b, c], active c
        val moved = state.reorderedTo(listOf(c, a, b))
        assertEquals(listOf(c, a, b), moved.tabs)
        assertEquals(c, moved.activeId) // active + pins preserved
    }

    @Test
    fun `reorderedTo ignores unknown ids and appends missing ones`() {
        val state = BrowserTabsState.EMPTY.open(a).open(b).open(c)
        // d is unknown; c omitted → c appended after the given order.
        val moved = state.reorderedTo(listOf(b, TabId(99), a))
        assertEquals(listOf(b, a, c), moved.tabs)
    }

    @Test
    fun `reorderedTo with the same order returns the same instance`() {
        val state = BrowserTabsState.EMPTY.open(a).open(b)
        assertEquals(state, state.reorderedTo(listOf(a, b)))
    }

    @Test
    fun `reorderedTo on empty state is a no-op`() {
        assertEquals(BrowserTabsState.EMPTY, BrowserTabsState.EMPTY.reorderedTo(listOf(a)))
    }

    // ---- pin / unpin / isPinned ----------------------------------------------

    @Test
    fun `pin marks a tab as pinned`() {
        val state = BrowserTabsState.EMPTY.open(a).open(b).pin(a)
        assertTrue(state.isPinned(a))
        assertFalse(state.isPinned(b))
    }

    @Test
    fun `pin on a non-existent tab is a no-op`() {
        val state = BrowserTabsState.EMPTY.open(a).pin(c)
        assertFalse(state.isPinned(c))
        assertTrue(state.pinned.isEmpty())
    }

    @Test
    fun `unpin removes the pin`() {
        val state = BrowserTabsState.EMPTY.open(a).pin(a).unpin(a)
        assertFalse(state.isPinned(a))
    }

    @Test
    fun `close drops the pin for the closed tab`() {
        val state = BrowserTabsState.EMPTY.open(a).open(b).pin(a).close(a)
        assertFalse(state.isPinned(a))
        assertEquals(setOf<TabId>(), state.pinned)
    }

    // ---- activateNext / activatePrevious -------------------------------------

    @Test
    fun `activateNext wraps from the last tab back to the first`() {
        val state = BrowserTabsState.EMPTY.open(a).open(b).open(c) // active: c
        val next = state.activateNext()
        assertEquals(a, next.activeId)
    }

    @Test
    fun `activatePrevious wraps from the first tab to the last`() {
        val state = BrowserTabsState.EMPTY.open(a).open(b).open(c).activate(a)
        val prev = state.activatePrevious()
        assertEquals(c, prev.activeId)
    }

    @Test
    fun `activateNext advances by one for normal cases`() {
        val state = BrowserTabsState.EMPTY.open(a).open(b).open(c).activate(a)
        assertEquals(b, state.activateNext().activeId)
    }

    @Test
    fun `activateNext is a no-op when there is fewer than two tabs`() {
        assertEquals(BrowserTabsState.EMPTY, BrowserTabsState.EMPTY.activateNext())
        val single = BrowserTabsState.withInitial(a)
        assertEquals(single, single.activateNext())
    }

    // ---- immutability sanity --------------------------------------------------

    @Test
    fun `operations return new instances and never mutate the receiver`() {
        val original = BrowserTabsState.EMPTY.open(a).open(b)
        val afterClose = original.close(a)
        // original must be untouched
        assertEquals(listOf(a, b), original.tabs)
        assertEquals(b, original.activeId)
        // and afterClose is independent
        assertEquals(listOf(b), afterClose.tabs)
    }
}
