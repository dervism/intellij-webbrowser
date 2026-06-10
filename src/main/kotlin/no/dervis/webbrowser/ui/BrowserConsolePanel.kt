package no.dervis.webbrowser.ui

import com.intellij.icons.AllIcons
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import no.dervis.webbrowser.domain.ConsoleMessage
import java.awt.BorderLayout
import java.awt.Color
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants
import javax.swing.JTextPane

/**
 * Bottom-of-the-tool-window console drain: every `window.console.*` line from
 * the active tab is appended here in real time. Toggled from the settings row.
 *
 * Kept deliberately simple — no virtual scroll, no per-tab filtering, no
 * regex grep. The intent is to close the "I have to leave the IDE just to
 * read a stack trace" gap, not duplicate DevTools' Console panel (F12 already
 * opens the real thing).
 */
internal class BrowserConsolePanel {

    private val output = JTextPane().apply {
        isEditable = false
        font = Font(Font.MONOSPACED, Font.PLAIN, JBUI.scaleFontSize(12f))
    }
    private val emptyLabel = JBLabel(
        "Browser console output appears here when the active tab logs to window.console.",
    ).apply { foreground = JBColor.GRAY; border = JBUI.Borders.empty(6, 8) }

    private val scroll = JBScrollPane(output).apply {
        border = BorderFactory.createEmptyBorder()
    }

    val component: JComponent = JPanel(BorderLayout()).apply {
        border = JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0)
        add(buildHeader(), BorderLayout.NORTH)
        add(scroll, BorderLayout.CENTER)
        // Start with the placeholder; the scroll pane swaps in once messages arrive.
        scroll.setViewportView(emptyLabel)
    }

    /**
     * Append [message] to the console. Auto-scrolls to the bottom when the
     * user was already there (typical case while watching live output).
     */
    fun append(message: ConsoleMessage) {
        if (scroll.viewport.view !== output) scroll.setViewportView(output)
        val doc = output.styledDocument
        val attrs = SimpleAttributeSet().also {
            StyleConstants.setForeground(it, colorFor(message.level))
        }
        doc.insertString(doc.length, message.formatted() + "\n", attrs)
        // Trim ancient lines so a long-running session doesn't grow unbounded.
        if (doc.length > MAX_CHARS) {
            doc.remove(0, doc.length - MAX_CHARS)
        }
        output.caretPosition = doc.length
    }

    /** Wipe everything that's been logged so far. */
    fun clear() {
        output.text = ""
        scroll.setViewportView(emptyLabel)
    }

    private fun buildHeader(): JComponent {
        val title = JBLabel("Browser Console").apply {
            border = JBUI.Borders.empty(2, 6)
        }
        val clearBtn = JButton(AllIcons.Actions.GC).apply {
            toolTipText = "Clear console"
            isFocusable = false
            isBorderPainted = false
            isContentAreaFilled = false
            addActionListener { clear() }
        }
        return JPanel(BorderLayout()).apply {
            add(title, BorderLayout.WEST)
            add(
                JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(2), 0)).apply {
                    add(clearBtn)
                },
                BorderLayout.EAST,
            )
        }
    }

    private fun colorFor(level: ConsoleMessage.Level): Color = when (level) {
        ConsoleMessage.Level.INFO -> JBColor.foreground()
        ConsoleMessage.Level.WARNING -> JBColor(Color(0xB0_72_00), Color(0xE6_B0_50))
        ConsoleMessage.Level.ERROR -> JBColor(Color(0xC7_22_2E), Color(0xFF_6B_6B))
    }

    private companion object {
        const val MAX_CHARS = 200_000 // ~2 MB cap on the text-pane buffer
    }
}
