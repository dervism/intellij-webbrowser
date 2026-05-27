package no.dervis.webbrowser.ui

import com.intellij.execution.RunManager
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import no.dervis.webbrowser.domain.ReadinessMode
import no.dervis.webbrowser.settings.WebBrowserProjectSettings
import no.dervis.webbrowser.settings.WebBrowserSettings
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

/**
 * Settings page under Settings > Tools > IntelliJ-WebBrowser. Project-level so the
 * reload-on-save folder and run configuration can be picked from this project.
 */
class WebBrowserConfigurable(private val project: Project) : Configurable {

    private var homeField: JBTextField? = null
    private var extField: JBTextField? = null
    private var watchFolderField: TextFieldWithBrowseButton? = null

    private var openOnRunCheck: JCheckBox? = null
    private var runConfigCombo: ComboBox<String>? = null
    private var openUrlField: JBTextField? = null
    private var readinessCombo: ComboBox<ReadinessMode>? = null
    private var secondsSpinner: JSpinner? = null

    override fun getDisplayName(): String = "IntelliJ-WebBrowser"

    override fun createComponent(): JComponent {
        val app = WebBrowserSettings.getInstance()
        val proj = WebBrowserProjectSettings.getInstance(project)

        val home = JBTextField(app.homeUrl, 40).also { homeField = it }
        val ext = JBTextField(app.watchExtensions, 40).also { extField = it }

        val folder = TextFieldWithBrowseButton().also { watchFolderField = it }
        folder.text = proj.watchPath
        val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
            .withTitle("Select Folder to Watch")
            .withDescription("Saving files under this folder reloads the browser. Leave empty to watch the whole project.")
        folder.addBrowseFolderListener(project, descriptor)

        val openCheck = JCheckBox("Open the Web Browser when a run configuration starts")
            .also { openOnRunCheck = it }
        openCheck.isSelected = proj.openOnRun

        val configNames = listOf(ANY_CONFIG) + RunManager.getInstance(project).allSettings.map { it.name }
        val configCombo = ComboBox(configNames.toTypedArray()).also { runConfigCombo = it }
        configCombo.setMinimumAndPreferredWidth(JBUI.scale(280))
        // Keep a stored-but-deleted config name visible so the choice isn't lost.
        if (proj.runConfigName.isNotBlank() && proj.runConfigName !in configNames) {
            configCombo.addItem(proj.runConfigName)
        }
        configCombo.selectedItem = if (proj.runConfigName.isBlank()) ANY_CONFIG else proj.runConfigName

        val urlField = JBTextField(proj.openUrl, 40).also { openUrlField = it }

        val readyCombo = ComboBox(ReadinessMode.entries.toTypedArray()).also { readinessCombo = it }
        readyCombo.renderer = SimpleListCellRenderer.create("") { it.displayName }
        readyCombo.setMinimumAndPreferredWidth(JBUI.scale(320))
        readyCombo.selectedItem = proj.readiness

        val secs = JSpinner(SpinnerNumberModel(proj.readinessSeconds, 1, 3600, 1)).also { secondsSpinner = it }

        return FormBuilder.createFormBuilder()
            .addLabeledComponent("Home / dev-server URL:", home, 1, false)
            .addLabeledComponent("Reload-on-save folder:", folder, 1, false)
            .addLabeledComponent("Reload-on-save extensions:", ext, 1, false)
            .addComponent(
                JBLabel("Leave the folder empty to watch the whole project. Empty extensions = any file.")
                    .apply { foreground = JBColor.GRAY },
            )
            .addSeparator()
            .addComponent(JBLabel("<html><b>Open browser on run</b></html>"))
            .addComponent(openCheck)
            .addLabeledComponent("Run configuration:", configCombo, 1, false)
            .addLabeledComponent("Open URL (blank = home):", urlField, 1, false)
            .addLabeledComponent("Open when:", readyCombo, 1, false)
            .addLabeledComponent("Wait up to / delay (seconds):", secs, 1, false)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    override fun isModified(): Boolean {
        val app = WebBrowserSettings.getInstance()
        val proj = WebBrowserProjectSettings.getInstance(project)
        return homeField?.text?.trim() != app.homeUrl ||
            extField?.text?.trim() != app.watchExtensions ||
            watchFolderField?.text?.trim() != proj.watchPath ||
            openOnRunCheck?.isSelected != proj.openOnRun ||
            selectedConfigName() != proj.runConfigName ||
            openUrlField?.text?.trim() != proj.openUrl ||
            selectedReadiness() != proj.readiness ||
            (secondsSpinner?.value as? Int) != proj.readinessSeconds
    }

    override fun apply() {
        val app = WebBrowserSettings.getInstance()
        val proj = WebBrowserProjectSettings.getInstance(project)
        homeField?.let { app.homeUrl = it.text.trim() }
        extField?.let { app.watchExtensions = it.text.trim() }
        watchFolderField?.let { proj.watchPath = it.text.trim() }
        openOnRunCheck?.let { proj.openOnRun = it.isSelected }
        proj.runConfigName = selectedConfigName()
        openUrlField?.let { proj.openUrl = it.text.trim() }
        proj.readiness = selectedReadiness()
        (secondsSpinner?.value as? Int)?.let { proj.readinessSeconds = it }
    }

    override fun reset() {
        val app = WebBrowserSettings.getInstance()
        val proj = WebBrowserProjectSettings.getInstance(project)
        homeField?.text = app.homeUrl
        extField?.text = app.watchExtensions
        watchFolderField?.text = proj.watchPath
        openOnRunCheck?.isSelected = proj.openOnRun
        runConfigCombo?.selectedItem = if (proj.runConfigName.isBlank()) ANY_CONFIG else proj.runConfigName
        openUrlField?.text = proj.openUrl
        readinessCombo?.selectedItem = proj.readiness
        secondsSpinner?.value = proj.readinessSeconds
    }

    override fun disposeUIResources() {
        homeField = null
        extField = null
        watchFolderField = null
        openOnRunCheck = null
        runConfigCombo = null
        openUrlField = null
        readinessCombo = null
        secondsSpinner = null
    }

    private fun selectedConfigName(): String {
        val selected = runConfigCombo?.selectedItem as? String ?: return ""
        return if (selected == ANY_CONFIG) "" else selected
    }

    private fun selectedReadiness(): ReadinessMode =
        readinessCombo?.selectedItem as? ReadinessMode ?: ReadinessMode.DEFAULT

    private companion object {
        const val ANY_CONFIG = "(Any configuration)"
    }
}
