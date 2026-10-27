package no.dervis.webbrowser.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.Messages
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.util.messages.Topic
import no.dervis.webbrowser.domain.ViewportPreset
import no.dervis.webbrowser.domain.ViewportWidth
import no.dervis.webbrowser.settings.WebBrowserSettings
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JList

/** Told when the preview width setting changes. */
internal fun interface ViewportListener {
    fun previewWidthChanged()
}

/**
 * The "Width" dropdown for the page preview. The preset is one setting for every
 * tab and project, so a change is published on [TOPIC]: every open panel's picker,
 * including the one the change came from, then shows the new preset and calls
 * [onChanged] to re-lay out its tabs.
 *
 * Every label is built from the preset and a clamped width, never from stored text.
 * The dropdown takes no keyboard focus, like the other buttons in the row, so arrow
 * keys meant for the page don't change the width.
 */
internal class ViewportPicker(
    private val project: Project,
    parentDisposable: Disposable,
    private val onChanged: () -> Unit,
) {
    private val combo = ComboBox<ViewportPreset>()

    // Set while the dropdown is changed in code, so its listener doesn't react.
    private var updating = false

    val component: JComponent get() = combo

    init {
        combo.renderer = object : SimpleListCellRenderer<ViewportPreset>() {
            override fun customize(
                list: JList<out ViewportPreset>,
                value: ViewportPreset?,
                index: Int,
                selected: Boolean,
                hasFocus: Boolean,
            ) {
                text = value?.let(::label).orEmpty()
            }
        }
        combo.toolTipText = "Preview width, in CSS pixels at 100% page zoom"
        combo.isFocusable = false
        combo.accessibleContext.accessibleName = "Preview width"
        showStoredPreset()
        combo.addActionListener {
            val preset = combo.selectedItem as? ViewportPreset
            if (!updating && preset != null) choose(preset)
        }
        ApplicationManager.getApplication().messageBus.connect(parentDisposable)
            .subscribe(TOPIC, ViewportListener {
                showStoredPreset()
                onChanged()
            })
    }

    /**
     * Shows the stored preset without reacting to it. The model is rebuilt so the
     * dropdown measures its width again when the Custom label has changed.
     */
    private fun showStoredPreset() {
        updating = true
        try {
            combo.model = DefaultComboBoxModel(ViewportPreset.entries.toTypedArray())
            combo.selectedItem = WebBrowserSettings.getInstance().viewportPreset
            combo.revalidate()
        } finally {
            updating = false
        }
    }

    /** Custom asks for a width once the dropdown has closed; cancelling keeps the stored preset. */
    private fun choose(preset: ViewportPreset) {
        if (preset != ViewportPreset.CUSTOM) return apply(preset)
        ApplicationManager.getApplication().invokeLater({
            val settings = WebBrowserSettings.getInstance()
            val width = askCustomWidth(settings.customViewportWidth)
            if (width == null) {
                showStoredPreset()
            } else {
                settings.customViewportWidth = width
                apply(preset)
            }
        }, project.disposed)
    }

    private fun apply(preset: ViewportPreset) {
        WebBrowserSettings.getInstance().viewportPreset = preset
        ApplicationManager.getApplication().messageBus.syncPublisher(TOPIC).previewWidthChanged()
    }

    private fun askCustomWidth(current: ViewportWidth): ViewportWidth? {
        val isValid = { text: String? -> ViewportWidth.parse(text.orEmpty()).isSome() }
        return Messages.showInputDialog(
            project,
            "Width in CSS pixels (${ViewportWidth.MIN}–${ViewportWidth.MAX}):",
            "Custom Preview Width",
            null,
            current.pixels.toString(),
            object : InputValidator {
                override fun checkInput(inputString: String?): Boolean = isValid(inputString)
                override fun canClose(inputString: String?): Boolean = isValid(inputString)
            },
        )?.let { ViewportWidth.parse(it).getOrNull() }
    }

    private fun label(preset: ViewportPreset): String = when (preset) {
        ViewportPreset.DESKTOP -> "Desktop"
        ViewportPreset.MOBILE -> "Mobile ${ViewportPreset.MOBILE_WIDTH}"
        ViewportPreset.TABLET -> "Tablet ${ViewportPreset.TABLET_WIDTH}"
        ViewportPreset.CUSTOM -> "Custom ${WebBrowserSettings.getInstance().customViewportWidth.pixels}…"
    }

    companion object {
        val TOPIC: Topic<ViewportListener> =
            Topic.create("Web Browser Panel preview width", ViewportListener::class.java)
    }
}
