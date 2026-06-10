package no.dervis.webbrowser.ui

import com.intellij.icons.AllIcons
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.Box
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * The in-tool-window find-in-page bar. Encapsulates its own UI and forwards
 * every user action (type, next, previous, close) through a small
 * [Dispatcher] interface so the host panel doesn't have to know anything
 * about Swing focus / text fields.
 *
 * The overlay only knows *what to ask of the active tab* — it has no
 * reference to BrowserTabPane. That separation kept WebBrowserPanel's
 * `init` from being one giant blob.
 */
internal class FindOverlay(private val dispatcher: Dispatcher) {

    /** Hooks back to whoever owns the browser tabs. */
    interface Dispatcher {
        /** Run a find against the active tab. */
        fun runFind(query: String, forward: Boolean, findNext: Boolean)
        /** Clear highlights in the active tab and reset find state. */
        fun stopFind()
        /** The overlay's close button was clicked or Esc was hit. */
        fun onCloseRequested()
    }

    private val field = JBTextField()

    val component: JComponent = build()

    val isVisible: Boolean get() = component.isVisible

    fun show() {
        if (!component.isVisible) component.isVisible = true
        field.requestFocusInWindow()
        field.selectAll()
    }

    fun hide() {
        dispatcher.stopFind()
        field.text = ""
        component.isVisible = false
    }

    private fun build(): JComponent {
        field.toolTipText =
            "Find on page — Enter for next, Shift+Enter for previous, Esc to close"
        field.columns = 24
        field.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = trigger(findNext = false)
            override fun removeUpdate(e: DocumentEvent) = trigger(findNext = false)
            override fun changedUpdate(e: DocumentEvent) = trigger(findNext = false)
        })
        field.addActionListener { trigger(findNext = true) }

        val prev = iconButton(AllIcons.Actions.PreviousOccurence, "Previous match (Shift+Enter)") {
            trigger(forward = false, findNext = true)
        }
        val next = iconButton(AllIcons.Actions.NextOccurence, "Next match (Enter)") {
            trigger(forward = true, findNext = true)
        }
        val close = iconButton(AllIcons.Actions.Close, "Close find (Esc)") {
            dispatcher.onCloseRequested()
        }

        val row = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), JBUI.scale(2))).apply {
            isVisible = false
            add(JBLabel("Find:"))
            add(field)
            add(prev)
            add(next)
            add(Box.createHorizontalGlue())
            add(close)
            maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
        }
        return row
    }

    private fun iconButton(icon: javax.swing.Icon, tooltip: String, action: () -> Unit): JButton =
        JButton(icon).apply {
            toolTipText = tooltip
            isFocusable = false
            isBorderPainted = false
            isContentAreaFilled = false
            addActionListener { action() }
        }

    private fun trigger(forward: Boolean = true, findNext: Boolean) {
        dispatcher.runFind(field.text, forward, findNext)
    }
}
