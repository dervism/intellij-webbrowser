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
import no.dervis.webbrowser.domain.WebBrowserSettingsSnapshot
import no.dervis.webbrowser.settings.WebBrowserProjectSettings
import no.dervis.webbrowser.settings.WebBrowserSettings
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

/**
 * Settings page under Settings > Tools > Web Browser Panel. Project-level so the
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

    override fun getDisplayName(): String = "Web Browser Panel"

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
        // Extend SimpleListCellRenderer rather than calling the deprecated
        // `create(String, Function)` static factory.
        readyCombo.renderer = object : SimpleListCellRenderer<ReadinessMode>() {
            override fun customize(
                list: JList<out ReadinessMode>,
                value: ReadinessMode?,
                index: Int,
                selected: Boolean,
                hasFocus: Boolean,
            ) {
                text = value?.displayName.orEmpty()
            }
        }
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

    // All three lifecycle methods route through immutable snapshots so the
    // "what does the form have vs what's stored" comparison and projection are
    // one-liners. The mapping back to the IntelliJ services is the only place
    // that touches mutable state.
    override fun isModified(): Boolean = readForm() != readStored()

    override fun apply() = writeStored(readForm())

    override fun reset() = writeForm(readStored())

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

    // ---- Snapshot read / write helpers --------------------------------------

    private fun readStored(): WebBrowserSettingsSnapshot {
        val app = WebBrowserSettings.getInstance()
        val proj = WebBrowserProjectSettings.getInstance(project)
        return WebBrowserSettingsSnapshot(
            homeUrl = app.homeUrl,
            watchExtensions = app.watchExtensions,
            watchPath = proj.watchPath,
            openOnRun = proj.openOnRun,
            runConfigName = proj.runConfigName,
            openUrl = proj.openUrl,
            readiness = proj.readiness,
            readinessSeconds = proj.readinessSeconds,
        )
    }

    private fun readForm(): WebBrowserSettingsSnapshot = WebBrowserSettingsSnapshot(
        homeUrl = homeField?.text?.trim().orEmpty(),
        watchExtensions = extField?.text?.trim().orEmpty(),
        watchPath = watchFolderField?.text?.trim().orEmpty(),
        openOnRun = openOnRunCheck?.isSelected ?: false,
        runConfigName = selectedConfigName(),
        openUrl = openUrlField?.text?.trim().orEmpty(),
        readiness = selectedReadiness(),
        readinessSeconds = (secondsSpinner?.value as? Int) ?: WebBrowserProjectSettings.DEFAULT_WAIT_SECONDS,
    )

    private fun writeStored(snapshot: WebBrowserSettingsSnapshot) {
        val app = WebBrowserSettings.getInstance()
        val proj = WebBrowserProjectSettings.getInstance(project)
        app.homeUrl = snapshot.homeUrl
        app.watchExtensions = snapshot.watchExtensions
        proj.watchPath = snapshot.watchPath
        proj.openOnRun = snapshot.openOnRun
        proj.runConfigName = snapshot.runConfigName
        proj.openUrl = snapshot.openUrl
        proj.readiness = snapshot.readiness
        proj.readinessSeconds = snapshot.readinessSeconds
    }

    private fun writeForm(snapshot: WebBrowserSettingsSnapshot) {
        homeField?.text = snapshot.homeUrl
        extField?.text = snapshot.watchExtensions
        watchFolderField?.text = snapshot.watchPath
        openOnRunCheck?.isSelected = snapshot.openOnRun
        runConfigCombo?.selectedItem =
            if (snapshot.runConfigName.isBlank()) ANY_CONFIG else snapshot.runConfigName
        openUrlField?.text = snapshot.openUrl
        readinessCombo?.selectedItem = snapshot.readiness
        secondsSpinner?.value = snapshot.readinessSeconds
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
