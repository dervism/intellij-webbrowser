package no.dervis.webbrowser.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.JBPopupMenu
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import no.dervis.webbrowser.domain.TabId
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants
import javax.swing.SwingUtilities

/**
 * A hand-rolled browser tab strip. `JTabbedPane` (custom tab components break
 * under `SCROLL_TAB_LAYOUT`) and `JBTabs` (rendered thin, no close button in
 * this tool-window context) both fought us on the basics, so this owns its
 * rendering end-to-end.
 *
 * Each tab is a custom-painted [Chip] (rounded active/hover background + accent
 * underline) laid out with [BorderLayout] so the close × — pinned to the east —
 * is *always* visible no matter how long the title is. The strip itself uses a
 * null layout so a dragged chip can follow the cursor while the others slide to
 * make room. The active tab's content lives in a [CardLayout] below, so the
 * heavyweight JCEF browsers stay parented across switches.
 */
internal class BrowserTabStrip(private val callbacks: Callbacks) {

    /** Events the host panel reacts to. All fire on the EDT. */
    interface Callbacks {
        fun onSelect(id: TabId)
        fun onClose(id: TabId)
        fun onTogglePin(id: TabId)
        fun onCloseOthers(id: TabId)
        fun onReorder(orderedIds: List<TabId>)
    }

    // Insertion order == left-to-right visual order.
    private val chips = LinkedHashMap<TabId, Chip>()
    private val contents = HashMap<TabId, JComponent>()
    private var activeId: TabId? = null
    private var shownId: TabId? = null

    // Drag state.
    private var dragId: TabId? = null
    private var dragGrabOffset = 0   // cursor x within the chip when grabbed
    private var dragCursorX = 0      // current cursor x in strip coords
    private var dragMoved = false

    private val strip = object : JPanel(null) {
        override fun getPreferredSize(): Dimension =
            Dimension(totalWidth(), JBUI.scale(TAB_HEIGHT))
        override fun getMinimumSize(): Dimension = preferredSize
        override fun doLayout() = layoutChips()
    }.apply { isOpaque = false }

    private val stripScroll = JBScrollPane(
        strip,
        ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED,
    ).apply {
        border = JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0)
        isOpaque = false
        viewport.isOpaque = false
        val h = JBUI.scale(TAB_HEIGHT + 7)
        minimumSize = Dimension(0, h)
        preferredSize = Dimension(0, h)
        maximumSize = Dimension(Int.MAX_VALUE, h)
    }

    private val cards = CardLayout()
    // A permanent empty card we can switch to when the last tab is closing, so
    // CardLayout never has to auto-advance off a card that's being removed.
    private val contentPanel = JPanel(cards).apply {
        add(JPanel().apply { isOpaque = false }, PLACEHOLDER_CARD)
    }

    /** The whole strip + content area, ready to drop into a BorderLayout.CENTER. */
    val component: JComponent = JPanel(BorderLayout()).apply {
        add(stripScroll, BorderLayout.NORTH)
        add(contentPanel, BorderLayout.CENTER)
    }

    // ---- Public API ----------------------------------------------------------

    fun addTab(id: TabId, title: String, content: JComponent, pinned: Boolean) {
        val chip = Chip(id).apply {
            setTitle(title)
            setPinned(pinned)
            refreshActive(id == activeId)
        }
        chips[id] = chip
        contents[id] = content
        strip.add(chip)
        contentPanel.add(content, id.value.toString())
        relayout()
    }

    fun removeTab(id: TabId) {
        val chip = chips.remove(id) ?: return
        val content = contents.remove(id)
        // Switch the visible card to a sibling (or the empty placeholder when
        // this is the last tab) BEFORE detaching this one, so CardLayout doesn't
        // auto-advance + validate, which reshapes a JCEF component that may be
        // disposing ("Already disposed").
        if (shownId == id) {
            val sibling = chips.keys.firstOrNull()
            cards.show(contentPanel, sibling?.value?.toString() ?: PLACEHOLDER_CARD)
            shownId = sibling
        }
        strip.remove(chip)
        content?.let(contentPanel::remove)
        relayout()
    }

    /** Show [id]'s content and highlight its chip. */
    fun select(id: TabId) {
        if (!chips.containsKey(id)) return
        activeId = id
        if (shownId != id) {
            cards.show(contentPanel, id.value.toString())
            shownId = id
        }
        chips.forEach { (cid, chip) -> chip.refreshActive(cid == id) }
        scrollChipVisible(id)
    }

    fun setTitle(id: TabId, title: String) {
        chips[id]?.setTitle(title)
        relayout()
    }

    fun setPinned(id: TabId, pinned: Boolean) {
        chips[id]?.setPinned(pinned)
    }

    /** Reorder chips + content order to match [orderedIds]. */
    fun setOrder(orderedIds: List<TabId>) {
        val present = orderedIds.filter(chips::containsKey)
        if (present == chips.keys.toList()) return
        val reordered = LinkedHashMap<TabId, Chip>(chips.size)
        present.forEach { id -> chips[id]?.let { reordered[id] = it } }
        chips.forEach { (id, c) -> if (id !in reordered) reordered[id] = c }
        chips.clear()
        chips.putAll(reordered)
        relayout()
    }

    // ---- Layout --------------------------------------------------------------

    private fun totalWidth(): Int {
        val gap = JBUI.scale(GAP)
        return chips.values.sumOf { it.preferredSize.width + gap }
    }

    /** Position every chip; the dragged one follows the cursor, the rest sit in slots. */
    private fun layoutChips() {
        val h = JBUI.scale(TAB_HEIGHT)
        val gap = JBUI.scale(GAP)
        var x = 0
        for ((id, chip) in chips) {
            val w = chip.preferredSize.width
            if (id == dragId) {
                val maxX = (totalWidth() - w).coerceAtLeast(0)
                chip.setBounds((dragCursorX - dragGrabOffset).coerceIn(0, maxX), 0, w, h)
            } else {
                chip.setBounds(x, 0, w, h)
            }
            x += w + gap
        }
        dragId?.let { chips[it]?.let { c -> strip.setComponentZOrder(c, 0) } }
    }

    private fun relayout() {
        strip.revalidate()
        strip.repaint()
    }

    private fun scrollChipVisible(id: TabId) {
        val chip = chips[id] ?: return
        SwingUtilities.invokeLater { strip.scrollRectToVisible(chip.bounds) }
    }

    // ---- Drag ----------------------------------------------------------------

    private fun beginDrag(id: TabId, grabX: Int) {
        if (chips[id]?.pinned == true) return // pinned tabs are locked
        dragId = id
        dragGrabOffset = grabX
        dragMoved = false
    }

    private fun updateDrag(cursorXInStrip: Int) {
        val dragging = dragId ?: return
        dragCursorX = cursorXInStrip
        dragMoved = true
        // Reorder by absolute cursor position (works both directions): the
        // dragged chip lands where its grabbed point falls among the others.
        val order = chips.keys.toList()
        val others = order.filter { it != dragging }
        val centerX = cursorXInStrip - dragGrabOffset + (chips[dragging]?.preferredSize?.width ?: 0) / 2
        var insertAt = others.count { id ->
            val c = chips[id] ?: return@count false
            (c.x + c.width / 2) < centerX
        }
        val pinnedCount = others.count { chips[it]?.pinned == true }
        insertAt = if (chips[dragging]?.pinned == true) insertAt.coerceAtMost(pinnedCount)
        else insertAt.coerceAtLeast(pinnedCount)
        val newOrder = others.toMutableList().apply { add(insertAt.coerceIn(0, size), dragging) }
        if (newOrder != order) reorderChips(newOrder)
        layoutChips() // reposition immediately (dragged follows cursor)
        strip.repaint()
    }

    private fun reorderChips(newOrder: List<TabId>) {
        val reordered = LinkedHashMap<TabId, Chip>(chips.size)
        newOrder.forEach { id -> chips[id]?.let { reordered[id] = it } }
        chips.clear()
        chips.putAll(reordered)
    }

    private fun endDrag() {
        val dragging = dragId ?: return
        dragId = null
        relayout()
        if (dragMoved) callbacks.onReorder(chips.keys.toList())
    }

    private fun showMenu(id: TabId, e: MouseEvent) {
        val chip = chips[id] ?: return
        val menu = JBPopupMenu()
        menu.add(JMenuItem(if (chip.pinned) "Unpin Tab" else "Pin Tab").apply {
            addActionListener { callbacks.onTogglePin(id) }
        })
        menu.add(JMenuItem("Close Tab").apply { addActionListener { callbacks.onClose(id) } })
        menu.add(JMenuItem("Close Other Tabs").apply {
            isEnabled = chips.size > 1
            addActionListener { callbacks.onCloseOthers(id) }
        })
        menu.show(e.component, e.x, e.y)
    }

    // ---- Chip ----------------------------------------------------------------

    private inner class Chip(val id: TabId) : JPanel(BorderLayout(JBUI.scale(4), 0)) {
        private val titleLabel = JLabel()
        private val closeButton = JButton(AllIcons.Actions.Close).apply {
            toolTipText = "Close tab"
            isBorderPainted = false
            isContentAreaFilled = false
            isFocusPainted = false
            isFocusable = false
            isOpaque = false
            isRolloverEnabled = true
            border = JBUI.Borders.empty()
            preferredSize = Dimension(JBUI.scale(16), JBUI.scale(16))
            addActionListener { callbacks.onClose(id) }
        }
        var pinned = false; private set
        private var hovered = false
        private var fullTitle = ""

        init {
            isOpaque = false
            border = JBUI.Borders.empty(0, 10, 0, 6)
            // Always the normal text colour — readable on the subtle, panel-derived
            // active/hover backgrounds below (the platform's underlinedTab* pair
            // came out invisible in some themes).
            titleLabel.foreground = JBColor.foreground()
            add(titleLabel, BorderLayout.CENTER)
            add(closeButton, BorderLayout.EAST)
            val mouse = object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent) { hovered = true; repaint() }
                override fun mouseExited(e: MouseEvent) { hovered = false; repaint() }
                override fun mousePressed(e: MouseEvent) {
                    if (e.isPopupTrigger) { showMenu(id, e); return }
                    if (e.button == MouseEvent.BUTTON1) {
                        callbacks.onSelect(id)
                        val chipX = SwingUtilities.convertPoint(e.component, e.point, this@Chip).x
                        beginDrag(id, chipX)
                    }
                }
                override fun mouseDragged(e: MouseEvent) {
                    if (dragId != id) return
                    val stripX = SwingUtilities.convertPoint(e.component, e.point, strip).x
                    updateDrag(stripX)
                }
                override fun mouseReleased(e: MouseEvent) {
                    if (e.isPopupTrigger) { showMenu(id, e); return }
                    if (dragId == id) endDrag()
                }
                override fun mouseClicked(e: MouseEvent) {
                    if (e.button == MouseEvent.BUTTON2) callbacks.onClose(id)
                }
            }
            // Listen on the chip and the title (the title covers most of the chip).
            addMouseListener(mouse); addMouseMotionListener(mouse)
            titleLabel.addMouseListener(mouse); titleLabel.addMouseMotionListener(mouse)
        }

        fun setTitle(title: String) {
            fullTitle = title
            titleLabel.text = ellipsize(title)
            titleLabel.toolTipText = title
        }

        fun setPinned(value: Boolean) {
            pinned = value
            titleLabel.icon = if (value) AllIcons.General.Pin else null
        }

        /** Repaint after the active state changed (title colour stays constant). */
        fun refreshActive(active: Boolean) = repaint()

        override fun getPreferredSize(): Dimension {
            val natural = super.getPreferredSize()
            val w = natural.width.coerceIn(JBUI.scale(MIN_TAB_WIDTH), JBUI.scale(MAX_TAB_WIDTH))
            return Dimension(w, JBUI.scale(TAB_HEIGHT))
        }

        override fun getMinimumSize(): Dimension = preferredSize
        override fun getMaximumSize(): Dimension = preferredSize

        override fun paintComponent(g: Graphics) {
            val active = id == activeId
            if (active || hovered) {
                val g2 = g.create() as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val arc = JBUI.scale(8)
                val top = JBUI.scale(2)
                g2.color = if (active) activeBackground() else hoverBackground()
                g2.fillRoundRect(0, top, width - 1, height - top, arc, arc)
                if (active) {
                    g2.color = JBUI.CurrentTheme.DefaultTabs.underlineColor()
                    val uh = JBUI.scale(2)
                    g2.fillRect(JBUI.scale(2), height - uh, width - JBUI.scale(4), uh)
                }
                g2.dispose()
            }
            super.paintComponent(g)
        }
    }

    /** A subtle highlight derived from the panel background, so [JBColor.foreground] stays readable. */
    private fun activeBackground(): Color {
        val panel = UIUtil.getPanelBackground()
        return if (ColorUtil.isDark(panel)) ColorUtil.brighter(panel, 3) else ColorUtil.darker(panel, 2)
    }

    private fun hoverBackground(): Color {
        val panel = UIUtil.getPanelBackground()
        return if (ColorUtil.isDark(panel)) ColorUtil.brighter(panel, 1) else ColorUtil.darker(panel, 1)
    }

    private fun ellipsize(title: String): String =
        if (title.length <= MAX_TITLE_CHARS) title else title.take(MAX_TITLE_CHARS - 1).trimEnd() + "…"

    private companion object {
        const val TAB_HEIGHT = 30
        const val GAP = 4
        const val MIN_TAB_WIDTH = 90
        const val MAX_TAB_WIDTH = 200
        const val MAX_TITLE_CHARS = 20
        const val PLACEHOLDER_CARD = "__webbrowser_empty__"
    }
}
