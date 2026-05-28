package no.dervis.webbrowser.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.browsers.BrowserLauncher
import com.intellij.ide.browsers.WebBrowser
import com.intellij.ide.browsers.WebBrowserManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.util.Alarm
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.ui.JBUI
import no.dervis.webbrowser.BrowserView
import no.dervis.webbrowser.WebBrowserController
import no.dervis.webbrowser.domain.ReloadRule
import no.dervis.webbrowser.domain.Url
import no.dervis.webbrowser.settings.WebBrowserProjectSettings
import no.dervis.webbrowser.settings.WebBrowserSettings
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefDisplayHandlerAdapter
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.Timer

/**
 * The tool-window UI: a toolbar (nav + address bar) plus a live-reload row, with an
 * embedded Chromium (JCEF) browser filling the rest. Implements [BrowserView] so the
 * controller can drive it. Falls back to a message if the IDE runtime lacks JCEF.
 */
class WebBrowserPanel(
    private val project: Project,
    private val parentDisposable: Disposable,
) : JPanel(BorderLayout()), BrowserView {

    private val urlField = JBTextField()
    private val backButton = iconButton(AllIcons.Actions.Back, "Go back")
    private val forwardButton = iconButton(AllIcons.Actions.Forward, "Go forward")
    private val reloadButton = iconButton(AllIcons.Actions.Refresh, "Reload")

    private val reloadOnSaveCheck = JCheckBox("Reload on save")
    private val autoRefreshCheck = JCheckBox("Auto-refresh every")
    private val secondsSpinner = JSpinner()

    private val browser: JBCefBrowser?

    // Live-reload plumbing.
    private val reloadAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, parentDisposable)
    private var vfsConnection: MessageBusConnection? = null
    private var refreshTimer: Timer? = null

    init {
        if (!JBCefApp.isSupported()) {
            browser = null
            add(
                JBLabel(
                    "The embedded browser (JCEF) is not available in this IDE runtime.",
                    SwingConstants.CENTER,
                ).apply { foreground = JBColor.GRAY },
                BorderLayout.CENTER,
            )
        } else {
            val settings = WebBrowserSettings.getInstance()
            val b = JBCefBrowser.createBuilder().build()
            Disposer.register(parentDisposable, b)
            browser = b
            urlField.text = settings.homeUrl

            wireHandlers(b)
            add(buildHeader(), BorderLayout.NORTH)
            add(b.component, BorderLayout.CENTER)

            // Ensure watchers/timers are torn down with the tool window.
            Disposer.register(parentDisposable) {
                refreshTimer?.stop()
                vfsConnection?.disconnect()
            }

            // Start on the themed SVG placeholder rather than auto-loading a URL
            // (which would show Chromium's error page if the dev server is down).
            showPlaceholder(null)

            // Register with the controller so run-config triggers can drive this view;
            // this also picks up any URL queued before the view existed.
            val controller = WebBrowserController.getInstance(project)
            controller.register(this)
            Disposer.register(parentDisposable) { controller.unregister(this) }

            // Apply persisted toggle defaults.
            if (settings.reloadOnSave) setReloadOnSave(true)
            if (settings.autoRefresh) setAutoRefresh(true, currentIntervalSeconds())
        }
    }

    // ---- BrowserView ---------------------------------------------------------

    /** Navigate the browser to [url] (the port driven by the controller). */
    override fun load(url: Url) {
        browser?.loadURL(url.value)
    }

    // ---- Browser callbacks ---------------------------------------------------

    private fun wireHandlers(b: JBCefBrowser) {
        // Keep the address bar in sync with the page's real URL. Ignore the
        // data:/about: URLs used by the placeholder so the bar keeps showing the
        // intended target.
        b.jbCefClient.addDisplayHandler(object : CefDisplayHandlerAdapter() {
            override fun onAddressChange(cefBrowser: CefBrowser?, frame: CefFrame?, url: String?) {
                if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
                    SwingUtilities.invokeLater { urlField.text = url }
                }
            }
        }, b.cefBrowser)

        b.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            // Enable/disable back & forward based on navigation history.
            override fun onLoadingStateChange(
                cefBrowser: CefBrowser?,
                isLoading: Boolean,
                canGoBack: Boolean,
                canGoForward: Boolean,
            ) {
                SwingUtilities.invokeLater {
                    backButton.isEnabled = canGoBack
                    forwardButton.isEnabled = canGoForward
                }
            }

            // Replace Chromium's error page with our themed SVG placeholder.
            override fun onLoadError(
                cefBrowser: CefBrowser?,
                frame: CefFrame?,
                errorCode: CefLoadHandler.ErrorCode?,
                errorText: String?,
                failedUrl: String?,
            ) {
                if (frame?.isMain != true) return
                if (errorCode == CefLoadHandler.ErrorCode.ERR_ABORTED) return
                SwingUtilities.invokeLater {
                    showPlaceholder(errorText?.takeIf { it.isNotBlank() } ?: "Couldn't load $failedUrl")
                }
            }
        }, b.cefBrowser)
    }

    // ---- Toolbar -------------------------------------------------------------

    private fun buildHeader(): JComponent =
        JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(2)
            add(buildNavRow(), BorderLayout.NORTH)
            add(buildReloadRow(), BorderLayout.SOUTH)
        }

    private fun buildNavRow(): JComponent {
        backButton.addActionListener { browser?.cefBrowser?.goBack() }
        forwardButton.addActionListener { browser?.cefBrowser?.goForward() }
        reloadButton.addActionListener { browser?.cefBrowser?.reload() }
        backButton.isEnabled = false
        forwardButton.isEnabled = false

        val homeButton = JButton("Home").apply {
            toolTipText = "Go to the configured home / dev-server URL"
            isFocusable = false
            addActionListener { navigate(WebBrowserSettings.getInstance().homeUrl) }
        }
        val openInButton = JButton("Open in", AllIcons.General.ArrowDown).apply {
            horizontalTextPosition = SwingConstants.LEFT
            iconTextGap = JBUI.scale(4)
            toolTipText = "Open the current address in an external browser"
            isFocusable = false
            addActionListener { showOpenInPopup(this) }
        }

        urlField.addActionListener { navigate(urlField.text) }

        val nav = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(2), JBUI.scale(2))).apply {
            add(backButton); add(forwardButton); add(reloadButton); add(homeButton)
        }
        val right = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(2), JBUI.scale(2))).apply {
            add(openInButton)
        }
        return JPanel(BorderLayout()).apply {
            add(nav, BorderLayout.WEST)
            add(urlField, BorderLayout.CENTER)
            add(right, BorderLayout.EAST)
        }
    }

    private fun buildReloadRow(): JComponent {
        val settings = WebBrowserSettings.getInstance()

        reloadOnSaveCheck.toolTipText =
            "Reload when a watched file is saved. Set the folder & extensions in Settings → Tools → Embedded Web Browser."
        reloadOnSaveCheck.isSelected = settings.reloadOnSave
        reloadOnSaveCheck.addActionListener {
            val on = reloadOnSaveCheck.isSelected
            settings.reloadOnSave = on
            setReloadOnSave(on)
        }

        autoRefreshCheck.toolTipText = "Reload the page on a fixed time interval."
        autoRefreshCheck.isSelected = settings.autoRefresh
        autoRefreshCheck.addActionListener {
            val on = autoRefreshCheck.isSelected
            settings.autoRefresh = on
            setAutoRefresh(on, currentIntervalSeconds())
        }

        secondsSpinner.model = SpinnerNumberModel(
            settings.reloadIntervalSeconds,
            WebBrowserSettings.MIN_INTERVAL,
            WebBrowserSettings.MAX_INTERVAL,
            1,
        )
        secondsSpinner.toolTipText = "Refresh interval in seconds"
        (secondsSpinner.editor as? JSpinner.NumberEditor)?.textField?.columns = 4
        secondsSpinner.addChangeListener {
            val s = currentIntervalSeconds()
            settings.reloadIntervalSeconds = s
            if (autoRefreshCheck.isSelected) setAutoRefresh(true, s)
        }

        return JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(2))).apply {
            add(reloadOnSaveCheck)
            add(autoRefreshCheck)
            add(secondsSpinner)
            add(JBLabel("s"))
        }
    }

    private fun currentIntervalSeconds(): Int =
        (secondsSpinner.value as? Int) ?: WebBrowserSettings.DEFAULT_INTERVAL_SECONDS

    // ---- Reload-on-save ------------------------------------------------------

    private fun setReloadOnSave(enabled: Boolean) {
        vfsConnection?.disconnect()
        vfsConnection = null
        if (!enabled) return

        val connection = ApplicationManager.getApplication().messageBus.connect(parentDisposable)
        connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: MutableList<out VFileEvent>) {
                val rule = currentReloadRule()
                if (events.any { it.isFileSave() && rule.matches(it.path) }) scheduleReload()
            }
        })
        vfsConnection = connection
    }

    /** The watch rule for the current settings (folder defaults to the project root). */
    private fun currentReloadRule(): ReloadRule {
        val root = WebBrowserProjectSettings.getInstance(project).watchPath
            .ifBlank { project.basePath.orEmpty() }
        return ReloadRule.of(root, WebBrowserSettings.getInstance().watchExtensions)
    }

    private fun VFileEvent.isFileSave(): Boolean =
        this is VFileContentChangeEvent || this is VFileCreateEvent

    private fun scheduleReload() {
        // Debounce: a single save can produce several VFS events / rapid saves.
        reloadAlarm.cancelAllRequests()
        reloadAlarm.addRequest({ browser?.cefBrowser?.reload() }, RELOAD_DEBOUNCE_MS)
    }

    // ---- Timed auto-refresh --------------------------------------------------

    private fun setAutoRefresh(enabled: Boolean, seconds: Int) {
        refreshTimer?.stop()
        refreshTimer = null
        if (!enabled) return

        refreshTimer = Timer(seconds.coerceAtLeast(1) * 1000) { browser?.cefBrowser?.reload() }
            .apply { isRepeats = true; start() }
    }

    // ---- Placeholder & navigation --------------------------------------------

    /** Show the themed SVG empty state. [errorText] non-null renders the error variant. */
    private fun showPlaceholder(errorText: String?) {
        browser?.loadHTML(WebBrowserPlaceholder.html(WebBrowserSettings.getInstance().homeUrl, errorText))
    }

    private fun navigate(raw: String) {
        Url.parse(raw).onSome(::load)
    }

    private fun showOpenInPopup(anchor: JComponent) {
        val targets = buildList {
            add(OpenTarget("System default browser", null))
            WebBrowserManager.getInstance().activeBrowsers.forEach { add(OpenTarget(it.name, it)) }
        }
        val step = object : BaseListPopupStep<OpenTarget>("Open In", targets) {
            override fun getTextFor(value: OpenTarget): String = value.label
            override fun onChosen(selectedValue: OpenTarget, finalChoice: Boolean): PopupStep<*>? =
                doFinalStep { openExternally(selectedValue.browser) }
        }
        JBPopupFactory.getInstance().createListPopup(step).showUnderneathOf(anchor)
    }

    private fun openExternally(externalBrowser: WebBrowser?) {
        val home = WebBrowserSettings.getInstance().homeUrl
        val url = Url.parse(urlField.text).getOrNull() ?: Url.parse(home).getOrNull() ?: return
        service<BrowserLauncher>().browse(url.value, externalBrowser, project)
    }

    private data class OpenTarget(val label: String, val browser: WebBrowser?)

    private fun iconButton(icon: Icon, tooltip: String): JButton =
        JButton(icon).apply {
            toolTipText = tooltip
            isFocusable = false
        }

    private companion object {
        const val RELOAD_DEBOUNCE_MS = 250
    }
}
