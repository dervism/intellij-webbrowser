package no.dervis.webbrowser.ui

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import no.dervis.webbrowser.domain.TabId
import java.awt.Container
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * Structural tests for the hand-rolled [BrowserTabStrip]. These exist because
 * the close button and tab height were repeatedly missing under JTabbedPane /
 * JBTabs — here they're asserted to be present by construction, with dummy
 * Swing content (no JCEF needed).
 */
class BrowserTabStripTest : BasePlatformTestCase() {

    private val recordedCloses = mutableListOf<TabId>()
    private val recordedSelects = mutableListOf<TabId>()

    private val recordedReorders = mutableListOf<List<TabId>>()

    private val callbacks = object : BrowserTabStrip.Callbacks {
        override fun onSelect(id: TabId) { recordedSelects += id }
        override fun onClose(id: TabId) { recordedCloses += id }
        override fun onTogglePin(id: TabId) {}
        override fun onCloseOthers(id: TabId) {}
        override fun onReorder(orderedIds: List<TabId>) { recordedReorders += orderedIds }
    }

    fun testEachTabHasACloseButton() {
        val strip = BrowserTabStrip(callbacks)
        strip.addTab(TabId(1), "Example", JPanel(), pinned = false)
        val buttons = collect<JButton>(strip.component)
        assertTrue("expected a close button on the tab", buttons.isNotEmpty())
        assertTrue("close button should be visible", buttons.all { it.isVisible })
    }

    fun testCloseButtonFiresTheCloseCallback() {
        val strip = BrowserTabStrip(callbacks)
        strip.addTab(TabId(7), "Example", JPanel(), pinned = false)
        collect<JButton>(strip.component).first().doClick()
        assertEquals(listOf(TabId(7)), recordedCloses)
    }

    fun testTitleIsRendered() {
        val strip = BrowserTabStrip(callbacks)
        strip.addTab(TabId(1), "Hello World", JPanel(), pinned = false)
        val labels = collect<JLabel>(strip.component).mapNotNull { it.text }
        assertTrue("expected the tab title to be shown", labels.any { it == "Hello World" })
    }

    fun testStripHasARealHeight() {
        val strip = BrowserTabStrip(callbacks)
        strip.addTab(TabId(1), "Example", JPanel(), pinned = false)
        // The strip is not a 1px sliver: its preferred height clears a sane floor.
        assertTrue(
            "tab strip should have a real height, was ${strip.component.preferredSize.height}",
            strip.component.preferredSize.height >= 24,
        )
    }

    fun testRemoveTabDropsTheChip() {
        val strip = BrowserTabStrip(callbacks)
        strip.addTab(TabId(1), "One", JPanel(), pinned = false)
        strip.addTab(TabId(2), "Two", JPanel(), pinned = false)
        val before = collect<JButton>(strip.component).size
        strip.removeTab(TabId(1))
        val after = collect<JButton>(strip.component).size
        assertEquals("removing a tab should drop exactly one close button", before - 1, after)
    }

    fun testRemovingTheActiveTabSwitchesAwayFirst() {
        // Regression: removing the *visible* CardLayout card used to auto-advance
        // and reshape a (disposing) JCEF component. removeTab must switch to a
        // sibling before detaching — here we just assert it doesn't blow up and
        // leaves the survivor intact.
        val strip = BrowserTabStrip(callbacks)
        strip.addTab(TabId(1), "One", JPanel(), pinned = false)
        strip.addTab(TabId(2), "Two", JPanel(), pinned = false)
        strip.select(TabId(1))
        strip.removeTab(TabId(1))
        strip.select(TabId(2))
        assertEquals(1, collect<JButton>(strip.component).size)
    }

    fun testRemovingTheLastTabDoesNotThrow() {
        // Regression: closing the only tab left CardLayout auto-advancing off the
        // card being removed (→ reshape of a disposing JCEF component). The
        // placeholder card it switches to must absorb that.
        val strip = BrowserTabStrip(callbacks)
        strip.addTab(TabId(1), "Only", JPanel(), pinned = false)
        strip.select(TabId(1))
        strip.removeTab(TabId(1))
        assertEquals(0, collect<JButton>(strip.component).size)
    }

    fun testSetTitleUpdatesTheLabel() {
        val strip = BrowserTabStrip(callbacks)
        strip.addTab(TabId(1), "Old", JPanel(), pinned = false)
        strip.setTitle(TabId(1), "New")
        val labels = collect<JLabel>(strip.component).mapNotNull { it.text }
        assertTrue(labels.any { it == "New" })
        assertFalse(labels.any { it == "Old" })
    }

    private inline fun <reified T> collect(root: Container): List<T> {
        val out = mutableListOf<T>()
        val queue = ArrayDeque<Container>()
        queue += root
        while (queue.isNotEmpty()) {
            val c = queue.removeFirst()
            for (comp in c.components) {
                if (comp is T) out += comp
                if (comp is Container) queue += comp
            }
        }
        return out
    }
}
