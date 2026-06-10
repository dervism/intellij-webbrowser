package no.dervis.webbrowser.ui

import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.components.JBList
import com.intellij.util.ui.JBUI
import no.dervis.webbrowser.domain.AddressBarHistory
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.DefaultListModel
import javax.swing.JTextField
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * Lightweight autocomplete dropdown attached to the address-bar text field.
 * The list is populated from [historyProvider] on every keystroke and shown
 * as a popup anchored under the field.
 *
 * Keys handled while the popup is open:
 *  - Down / Up  — move the selection inside the list
 *  - Enter      — fill the field with the highlighted entry and submit
 *  - Escape     — close the popup, leave the field text untouched
 *
 * The popup is recreated each time it's shown (cheap and avoids a stale
 * model after the underlying history changes). We never disturb the field's
 * own Enter binding when the popup is hidden, so submitting a freshly typed
 * URL works exactly as before.
 */
internal class AddressBarAutocomplete(
    private val field: JTextField,
    private val historyProvider: () -> AddressBarHistory,
    private val onAccept: (String) -> Unit,
) {

    private val model = DefaultListModel<String>()
    private val list = JBList(model)
    private var popup: JBPopup? = null
    private var suppressDocumentListener = false

    /**
     * Set the field's text WITHOUT triggering the suggestion dropdown. Used by
     * the toolbar for every programmatic update (restoring a session, syncing
     * the URL on tab switch, etc.) so the dropdown only ever appears in
     * response to the user actually typing.
     */
    fun setTextSilently(text: String) {
        suppressDocumentListener = true
        field.text = text
        suppressDocumentListener = false
    }

    init {
        list.visibleRowCount = 8
        field.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = refresh()
            override fun removeUpdate(e: DocumentEvent) = refresh()
            override fun changedUpdate(e: DocumentEvent) = refresh()
        })
        field.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (popup == null || popup?.isDisposed == true) return
                when (e.keyCode) {
                    KeyEvent.VK_DOWN -> { moveSelection(+1); e.consume() }
                    KeyEvent.VK_UP -> { moveSelection(-1); e.consume() }
                    KeyEvent.VK_ENTER -> {
                        list.selectedValue?.let {
                            suppressDocumentListener = true
                            field.text = it
                            suppressDocumentListener = false
                            close()
                            onAccept(it)
                            e.consume()
                        }
                    }
                    KeyEvent.VK_ESCAPE -> { close(); e.consume() }
                }
            }
        })
        field.addFocusListener(object : FocusAdapter() {
            override fun focusLost(e: FocusEvent) {
                // Defer the close so a click on the popup's list still gets
                // routed before the popup goes away.
                SwingUtilities.invokeLater { close() }
            }
        })
        list.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount != 1) return
                val choice = list.selectedValue ?: return
                suppressDocumentListener = true
                field.text = choice
                suppressDocumentListener = false
                close()
                onAccept(choice)
            }
        })
    }

    private fun refresh() {
        // Only suggest while the user is actually typing in the field. Programmatic
        // text changes — e.g. the toolbar syncing the URL when you switch tabs —
        // must NOT pop the dropdown (it would float over the tab strip).
        if (suppressDocumentListener || !field.isShowing || !field.isFocusOwner) {
            close(); return
        }
        val matches = historyProvider().suggest(field.text)
        if (matches.isEmpty()) { close(); return }
        model.clear()
        matches.forEach(model::addElement)
        list.selectedIndex = 0
        show()
    }

    private fun show() {
        if (popup?.isDisposed == false && popup?.isVisible == true) return
        val factory = JBPopupFactory.getInstance()
        val newPopup = factory.createComponentPopupBuilder(list, list)
            .setRequestFocus(false)
            .setCancelOnClickOutside(true)
            .setMinSize(java.awt.Dimension(field.width.coerceAtLeast(JBUI.scale(240)), 0))
            .createPopup()
        popup = newPopup
        newPopup.showUnderneathOf(field)
    }

    private fun close() {
        popup?.takeIf { !it.isDisposed }?.cancel()
        popup = null
    }

    private fun moveSelection(delta: Int) {
        if (model.size == 0) return
        val current = list.selectedIndex
        val next = ((current + delta) + model.size) % model.size
        list.selectedIndex = next
        list.ensureIndexIsVisible(next)
    }
}
