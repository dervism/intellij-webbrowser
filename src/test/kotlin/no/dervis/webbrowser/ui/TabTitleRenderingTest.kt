package no.dervis.webbrowser.ui

import com.intellij.util.ui.UIUtil
import no.dervis.webbrowser.domain.TabId
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.plaf.basic.BasicHTML
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Plain JUnit check that a page title is shown as text, never rendered as Swing
 * HTML. It needs no IDE application, so it runs while the platform fixture can't.
 */
class TabTitleRenderingTest {

    private val strip = BrowserTabStrip(object : BrowserTabStrip.Callbacks {
        override fun onSelect(id: TabId) = Unit
        override fun onClose(id: TabId) = Unit
        override fun onTogglePin(id: TabId) = Unit
        override fun onCloseOthers(id: TabId) = Unit
        override fun onReorder(orderedIds: List<TabId>) = Unit
    })

    private fun titleLabel(text: String): JLabel =
        UIUtil.findComponentsOfType(strip.component, JLabel::class.java).single { it.text == text }

    @Test
    fun `a title starting with html is shown as text`() {
        strip.addTab(TabId(1), "<html><b>x</b>", JPanel(), pinned = false)
        val label = titleLabel("<html><b>x</b>")
        assertEquals(true, label.getClientProperty("html.disable"))
        assertNull(label.getClientProperty(BasicHTML.propertyKey), "Swing must not have built an HTML view")
        assertEquals("<html>&lt;html&gt;&lt;b&gt;x&lt;/b&gt;</html>", label.toolTipText)
    }

    @Test
    fun `control and bidi characters are removed from the shown title`() {
        strip.addTab(TabId(2), "My\u202E App\n", JPanel(), pinned = false)
        titleLabel("My App")
    }
}
