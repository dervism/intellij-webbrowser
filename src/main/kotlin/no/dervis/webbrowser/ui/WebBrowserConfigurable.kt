package no.dervis.webbrowser.ui

import com.intellij.execution.RunManager
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import no.dervis.webbrowser.detector.detectDevServers
import no.dervis.webbrowser.domain.DevServerDetector
import no.dervis.webbrowser.domain.ReadinessMode
import no.dervis.webbrowser.domain.SettingsValidation
import no.dervis.webbrowser.domain.WebBrowserSettingsSnapshot
import no.dervis.webbrowser.settings.WebBrowserProjectSettings
import no.dervis.webbrowser.settings.WebBrowserSettings
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.JTextArea
import javax.swing.SpinnerNumberModel

/**
 * Settings page under Settings > Tools > Web Browser Panel. Project-level so the
 * reload-on-save folder and run configuration can be picked from this project.
 */
class WebBrowserConfigurable(private val project: Project) : Configurable {

    private var homeField: JBTextField? = null
    private var projectHomeField: JBTextField? = null
    private var extField: JBTextField? = null
    private var watchFolderField: TextFieldWithBrowseButton? = null
    private var watchPatternsArea: JTextArea? = null
    private var detectStatusLabel: JBLabel? = null
    private var historyCountLabel: JBLabel? = null

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
        val projectHome = JBTextField(proj.projectHomeUrl, 40).also { projectHomeField = it }
        val ext = JBTextField(app.watchExtensions, 40).also { extField = it }

        val folder = TextFieldWithBrowseButton().also { watchFolderField = it }
        folder.text = proj.watchPath
        val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
            .withTitle("Select Folder to Watch")
            .withDescription("Saving files under this folder reloads the browser. Leave empty to watch the whole project.")
        folder.addBrowseFolderListener(project, descriptor)

        val patterns = JTextArea(proj.watchPatterns, 4, 40).apply {
            lineWrap = false
            toolTipText = "One glob per line, e.g. /project/src/**/*.{ts,tsx}. " +
                "When non-empty, replaces the folder + extensions above."
        }.also { watchPatternsArea = it }
        val patternsScroll = JBScrollPane(patterns)

        val detectButton = JButton("Detect from project").apply {
            toolTipText = "Scan this project for Storybook / Next.js / Vite / npm dev scripts and pre-fill the URL + watch patterns."
            addActionListener { runDetection() }
        }
        val detectStatus = JBLabel("").apply { foreground = JBColor.GRAY }
        detectStatusLabel = detectStatus
        val detectRow = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0)).apply {
            add(detectButton); add(detectStatus)
        }

        // Address-bar autocomplete history — an immediate "Clear" action (it
        // doesn't go through Apply, since it's an action, not a stored setting).
        val historyCount = JBLabel(historyCountText(proj)).apply { foreground = JBColor.GRAY }
        historyCountLabel = historyCount
        val clearHistoryButton = JButton("Clear address-bar history").apply {
            toolTipText = "Forget every URL remembered for the address-bar autocomplete dropdown."
            addActionListener {
                proj.clearAddressBarHistory()
                historyCount.text = historyCountText(proj)
            }
        }
        val historyRow = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0)).apply {
            add(clearHistoryButton); add(historyCount)
        }

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
            .addLabeledComponent("Default home / dev-server URL:", home, 1, false)
            .addLabeledComponent("Per-project home URL (overrides default):", projectHome, 1, false)
            .addComponent(detectRow)
            .addComponent(historyRow)
            .addSeparator()
            .addComponent(JBLabel("<html><b>Reload on save</b></html>"))
            .addLabeledComponent("Reload-on-save folder:", folder, 1, false)
            .addLabeledComponent("Reload-on-save extensions:", ext, 1, false)
            .addLabeledComponent("Watch patterns (one glob per line):", patternsScroll, 1, false)
            .addComponent(
                JBLabel(
                    "<html>Leave the folder empty to watch the whole project. Empty extensions = any file." +
                        "<br>Patterns override folder + extensions when non-empty " +
                        "(e.g. <code>/project/src/**/*.{ts,tsx}</code>).</html>",
                ).apply { foreground = JBColor.GRAY },
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

    /**
     * Refuse to save obviously broken input. Each issue surfaces a
     * [ConfigurationException] that IntelliJ shows in a banner above the form
     * with focus moved to the offending control.
     */
    override fun apply() {
        val snapshot = readForm()
        val issues = SettingsValidation.validate(snapshot)
        if (issues.isNotEmpty()) {
            val first = issues.first()
            val component = focusTarget(first.field)
            throw ConfigurationException(first.message).also {
                if (component != null) component.requestFocusInWindow()
            }
        }
        writeStored(snapshot)
    }

    override fun reset() = writeForm(readStored())

    private fun focusTarget(field: SettingsValidation.Field): JComponent? = when (field) {
        SettingsValidation.Field.HOME_URL -> homeField
        SettingsValidation.Field.PROJECT_HOME_URL -> projectHomeField
        SettingsValidation.Field.RUN_CONFIG -> runConfigCombo
        SettingsValidation.Field.OPEN_URL -> openUrlField
        SettingsValidation.Field.WATCH_PATTERNS -> watchPatternsArea
    }

    /**
     * Hand the project off to [detectDevServers] (which walks the VFS to
     * build a [DevServerDetector.ProjectShape]), then apply the top
     * suggestion's URL + watch patterns to the form fields. The user still
     * has to hit *Apply* — we never write to the services directly.
     */
    private fun runDetection() {
        val status = detectStatusLabel ?: return
        val suggestion = detectDevServers(project).firstOrNull()
        if (suggestion == null) {
            status.text = "Nothing recognisable detected."
            return
        }
        projectHomeField?.text = suggestion.homeUrl
        watchPatternsArea?.text = suggestion.watchPatternsText()
        val runHint = suggestion.runConfigHint?.let { " · run-config hint: \"$it\"" } ?: ""
        status.text = "Detected: ${suggestion.label}$runHint"
    }

    private fun historyCountText(proj: WebBrowserProjectSettings): String =
        when (val n = proj.addressBarHistorySize()) {
            0 -> "No remembered URLs."
            1 -> "1 remembered URL."
            else -> "$n remembered URLs."
        }

    override fun disposeUIResources() {
        homeField = null
        projectHomeField = null
        extField = null
        watchFolderField = null
        watchPatternsArea = null
        detectStatusLabel = null
        historyCountLabel = null
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
            projectHomeUrl = proj.projectHomeUrl,
            watchExtensions = app.watchExtensions,
            watchPath = proj.watchPath,
            watchPatterns = proj.watchPatterns,
            openOnRun = proj.openOnRun,
            runConfigName = proj.runConfigName,
            openUrl = proj.openUrl,
            readiness = proj.readiness,
            readinessSeconds = proj.readinessSeconds,
        )
    }

    private fun readForm(): WebBrowserSettingsSnapshot = WebBrowserSettingsSnapshot(
        homeUrl = homeField?.text?.trim().orEmpty(),
        projectHomeUrl = projectHomeField?.text?.trim().orEmpty(),
        watchExtensions = extField?.text?.trim().orEmpty(),
        watchPath = watchFolderField?.text?.trim().orEmpty(),
        // Trim trailing whitespace only — leading whitespace inside a pattern
        // line is meaningful (a tab character is valid in a path). The
        // line-by-line parser inside WatchGlobs trims each line anyway.
        watchPatterns = watchPatternsArea?.text?.trimEnd().orEmpty(),
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
        proj.projectHomeUrl = snapshot.projectHomeUrl
        app.watchExtensions = snapshot.watchExtensions
        proj.watchPath = snapshot.watchPath
        proj.watchPatterns = snapshot.watchPatterns
        proj.openOnRun = snapshot.openOnRun
        proj.runConfigName = snapshot.runConfigName
        proj.openUrl = snapshot.openUrl
        proj.readiness = snapshot.readiness
        proj.readinessSeconds = snapshot.readinessSeconds
    }

    private fun writeForm(snapshot: WebBrowserSettingsSnapshot) {
        homeField?.text = snapshot.homeUrl
        projectHomeField?.text = snapshot.projectHomeUrl
        extField?.text = snapshot.watchExtensions
        watchFolderField?.text = snapshot.watchPath
        watchPatternsArea?.text = snapshot.watchPatterns
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
