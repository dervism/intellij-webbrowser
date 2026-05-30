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
