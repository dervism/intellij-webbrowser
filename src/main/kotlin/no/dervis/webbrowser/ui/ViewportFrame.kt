package no.dervis.webbrowser.ui

import com.intellij.ui.JBColor
import java.awt.Container
import java.awt.Dimension
import java.awt.Graphics
import java.awt.LayoutManager
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Holds one tab's browser and limits it to the width the current viewport preset
 * asks for, centred, at full height. [browserWidth] is asked again on every layout,
 * so changing the preset only needs a revalidate; the browser component is never
 * removed and re-added. When the browser is narrower than the frame, a thin line
 * marks each edge.
 *
 * The width is always kept within 0 and the frame's own width, whatever
 * [browserWidth] returns, so the browser can never be sized beyond the panel.
 */
internal class ViewportFrame(
    private val browser: JComponent,
    private val browserWidth: (available: Int) -> Int,
) : JPanel() {

    init {
        layout = CenteredWidthLayout()
        add(browser)
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        if (browser.width in 1 until width) {
            g.color = JBColor.border()
            g.drawLine(browser.x - 1, 0, browser.x - 1, height)
            g.drawLine(browser.x + browser.width, 0, browser.x + browser.width, height)
        }
    }

    private inner class CenteredWidthLayout : LayoutManager {
        override fun layoutContainer(parent: Container) {
            val insets = parent.insets
            val available = (parent.width - insets.left - insets.right).coerceAtLeast(0)
            val width = browserWidth(available).coerceIn(0, available)
            val height = (parent.height - insets.top - insets.bottom).coerceAtLeast(0)
            browser.setBounds(insets.left + (available - width) / 2, insets.top, width, height)
        }

        override fun preferredLayoutSize(parent: Container): Dimension = browser.preferredSize

        override fun minimumLayoutSize(parent: Container): Dimension = Dimension(0, 0)

        override fun addLayoutComponent(name: String?, comp: java.awt.Component?) = Unit

        override fun removeLayoutComponent(comp: java.awt.Component?) = Unit
    }
}
