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
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.util.Alarm
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.ui.JBUI
import no.dervis.webbrowser.BrowserView
import no.dervis.webbrowser.WebBrowserController
import no.dervis.webbrowser.domain.AddressBarFilter
import no.dervis.webbrowser.domain.ReloadRule
import no.dervis.webbrowser.domain.Url
import no.dervis.webbrowser.domain.ViewSourceTransform
import no.dervis.webbrowser.domain.ZoomLevel
import no.dervis.webbrowser.settings.WebBrowserProjectSettings
import no.dervis.webbrowser.settings.WebBrowserSettings
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.callback.CefContextMenuParams
import org.cef.callback.CefMenuModel
import org.cef.handler.CefContextMenuHandlerAdapter
import org.cef.handler.CefDisplayHandlerAdapter
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.BoxLayout
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

    private val urlField = ExtendableTextField()

    private val reloadOnSaveCheck = JCheckBox("Reload on save")
    private val autoRefreshCheck = JCheckBox("Auto-refresh every")
    private val secondsSpinner = JSpinner()

    // Navigation state — tracked here because we drive borderless icons inside
    // the URL field (extensions) whose enabled look depends on these flags.
    private var canGoBack = false
    private var canGoForward = false

    // Zoom — tracked locally because CefBrowser.getZoomLevel() reads an async
    // cache that's usually still 0.0 right after a setter, so re-reading it
    // makes every adjustment overwrite the previous one with ±step instead of
    // accumulating.
    private var zoomLevel = 0.0

    private val backExtension = navExtension(AllIcons.Actions.Back, "Go back", { canGoBack }) {
        browser?.cefBrowser?.goBack()
    }
    private val forwardExtension = navExtension(AllIcons.Actions.Forward, "Go forward", { canGoForward }) {
        browser?.cefBrowser?.goForward()
    }
    private val reloadExtension = navExtension(AllIcons.Actions.Refresh, "Reload", { true }) {
        browser?.cefBrowser?.reload()
    }

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
            // JBCefBrowser.getBackgroundColor() returns JBColor.background() —
            // i.e. the IDE theme's panel colour — and that's what's painted
            // behind transparent <body> regions on the JCEF canvas. On a dark
            // theme that's dark grey, which makes minimal pages (the Astro
            // starter, blank HTML…) read as dark text on a dark canvas. Force
            // an opaque white wrapper so the canvas paints onto white.
            b.component.background = java.awt.Color.WHITE
            b.component.isOpaque = true
            urlField.text = settings.homeUrl

            wireHandlers(b)
            add(buildHeader(), BorderLayout.NORTH)
            add(b.component, BorderLayout.CENTER)

            // Ensure watchers/timers are torn down with the tool window.
            Disposer.register(parentDisposable) {
                refreshTimer?.stop()
                vfsConnection?.disconnect()
            }

            // Start on the themed SVG home page rather than auto-loading a URL
            // (which would surface Chromium's error page if the target is down).
            showPlaceholder()

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
                AddressBarFilter.displayable(url)?.let { shown ->
                    SwingUtilities.invokeLater { urlField.text = shown }
                }
            }
        }, b.cefBrowser)

        b.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            // Refresh the inline back/forward icons (which read these flags) when
            // the browser's navigation history changes.
            override fun onLoadingStateChange(
                cefBrowser: CefBrowser?,
                isLoading: Boolean,
                backAvailable: Boolean,
                forwardAvailable: Boolean,
            ) {
                SwingUtilities.invokeLater {
                    canGoBack = backAvailable
                    canGoForward = forwardAvailable
                    urlField.repaint()
                }
            }

            // Replace Chromium's error page with our themed SVG home page —
            // intentionally context-free, since the kind of page being loaded
            // (any URL on any host on any port) shouldn't be guessed at.
            override fun onLoadError(
                cefBrowser: CefBrowser?,
                frame: CefFrame?,
                errorCode: CefLoadHandler.ErrorCode?,
                errorText: String?,
                failedUrl: String?,
            ) {
                if (frame?.isMain != true) return
                if (errorCode == CefLoadHandler.ErrorCode.ERR_ABORTED) return
                SwingUtilities.invokeLater { showPlaceholder() }
            }

            // The JCEF canvas inherits the IDE's dark theme. Pages that don't
            // declare a `color-scheme` end up rendering with a dark canvas, so
            // anything that just paints dark text on a transparent <body>
            // (a minimal Astro/Vite/HTML starter, …) becomes unreadable. For
            // those pages we set `color-scheme: light` so the canvas, form
            // controls and scrollbars use light defaults — matching what a
            // normal external browser does. Pages that opt into dark mode
            // (color-scheme: dark, or dark light) are left alone, and we skip
            // data:/about: URLs so our SVG home page isn't touched.
            override fun onLoadEnd(cefBrowser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                if (frame?.isMain != true) return
                val url = cefBrowser?.url.orEmpty()
                if (url.startsWith("data:") || url.startsWith("about:")) return
                frame.executeJavaScript(FORCE_LIGHT_FOR_UNTHEMED_JS, url, 0)
            }
        }, b.cefBrowser)

        // Two things on the right-click menu: (1) make Chromium's built-in
        // "View Page Source" actually work (default JCEF leaves it as a no-op),
        // and (2) add a "Zoom ▸" submenu mirroring the toolbar dropdown.
        b.jbCefClient.addContextMenuHandler(object : CefContextMenuHandlerAdapter() {
            override fun onBeforeContextMenu(
                cefBrowser: CefBrowser?,
                frame: CefFrame?,
                params: CefContextMenuParams?,
                model: CefMenuModel?,
            ) {
                model ?: return
                model.addSeparator()
                val submenu = model.addSubMenu(CTX_ZOOM_GROUP, "Zoom") ?: return
                submenu.addItem(CTX_ZOOM_IN, "Zoom In")
                submenu.addItem(CTX_ZOOM_OUT, "Zoom Out")
                submenu.addItem(CTX_ZOOM_RESET, "Reset Zoom")
            }

            override fun onContextMenuCommand(
                cefBrowser: CefBrowser?,
                frame: CefFrame?,
                params: CefContextMenuParams?,
                commandId: Int,
                eventFlags: Int,
            ): Boolean = when (commandId) {
                CefMenuModel.MenuId.MENU_ID_VIEW_SOURCE -> {
                    val current = cefBrowser?.url.orEmpty()
                    ViewSourceTransform.targetFor(current)?.let { cefBrowser?.loadURL(it) }
                    true
                }
                CTX_ZOOM_IN -> { SwingUtilities.invokeLater { adjustZoom(+ZoomLevel.STEP) }; true }
                CTX_ZOOM_OUT -> { SwingUtilities.invokeLater { adjustZoom(-ZoomLevel.STEP) }; true }
                CTX_ZOOM_RESET -> { SwingUtilities.invokeLater { resetZoom() }; true }
                else -> false
            }
        }, b.cefBrowser)
    }

    // ---- Toolbar -------------------------------------------------------------

    // Three stacked rows so the URL field gets its own line (full width) instead
    // of being squeezed between the button groups and disappearing on narrow
    // tool windows.
    private fun buildHeader(): JComponent =
        JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(2)
            add(buildNavRow())
            add(buildUrlRow())
            add(buildReloadRow())
        }

    private fun buildNavRow(): JComponent {
        // Back / Forward / Reload now live inside the URL field (see buildUrlRow)
        // as borderless extensions, so this row only carries the three "command"
        // buttons: Home on the left, Zoom ▾ and Open in ▾ on the right.
        val homeButton = JButton("Home").apply {
            toolTipText = "Go to the configured home / dev-server URL"
            isFocusable = false
            addActionListener { navigate(WebBrowserSettings.getInstance().homeUrl) }
        }
        val zoomButton = JButton("Zoom", AllIcons.General.ArrowDown).apply {
            horizontalTextPosition = SwingConstants.LEFT
            iconTextGap = JBUI.scale(4)
            toolTipText = "Zoom in / out / reset"
            isFocusable = false
            addActionListener { showZoomPopup(this) }
        }
        val openInButton = JButton("Open in", AllIcons.General.ArrowDown).apply {
            horizontalTextPosition = SwingConstants.LEFT
            iconTextGap = JBUI.scale(4)
            toolTipText = "Open the current address in an external browser"
            isFocusable = false
            addActionListener { showOpenInPopup(this) }
        }

        val left = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(2), JBUI.scale(2))).apply {
            add(homeButton)
        }
        val right = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(4), JBUI.scale(2))).apply {
            add(zoomButton); add(openInButton)
        }
        return capHeight(
            JPanel(BorderLayout()).apply {
                add(left, BorderLayout.WEST)
                add(right, BorderLayout.EAST)
            },
        )
    }

    // ---- Zoom ----------------------------------------------------------------

    /** Adjust the JCEF zoom level by [delta] (clamped via [ZoomLevel.next]). */
    private fun adjustZoom(delta: Double) {
        val cefBrowser = browser?.cefBrowser ?: return
        zoomLevel = ZoomLevel.next(zoomLevel, delta)
        cefBrowser.zoomLevel = zoomLevel
    }

    /** Reset the zoom level back to 100%. */
    private fun resetZoom() {
        zoomLevel = ZoomLevel.DEFAULT
        browser?.cefBrowser?.zoomLevel = ZoomLevel.DEFAULT
    }

    private fun showZoomPopup(anchor: JComponent) {
        val actions = listOf(
            ZoomAction("Zoom In", AllIcons.Graph.ZoomIn) { adjustZoom(+ZoomLevel.STEP) },
            ZoomAction("Zoom Out", AllIcons.Graph.ZoomOut) { adjustZoom(-ZoomLevel.STEP) },
            ZoomAction("Reset Zoom", AllIcons.Graph.ActualZoom) { resetZoom() },
        )
        val step = object : BaseListPopupStep<ZoomAction>("Zoom", actions) {
            override fun getTextFor(value: ZoomAction): String = value.label
            override fun getIconFor(value: ZoomAction): Icon = value.icon
            override fun onChosen(selectedValue: ZoomAction, finalChoice: Boolean): PopupStep<*>? =
                doFinalStep { selectedValue.run() }
        }
        JBPopupFactory.getInstance().createListPopup(step).showUnderneathOf(anchor)
    }

    private data class ZoomAction(val label: String, val icon: Icon, val run: () -> Unit)

    private fun buildUrlRow(): JComponent {
        // Borderless icon "extensions" rendered inside the text field on the
        // left edge — no surrounding button chrome, native hover behaviour.
        urlField.addExtension(backExtension)
        urlField.addExtension(forwardExtension)
        urlField.addExtension(reloadExtension)
        urlField.addActionListener { navigate(urlField.text) }
        return capHeight(
            JPanel(BorderLayout()).apply {
                border = JBUI.Borders.empty(2, JBUI.scale(2))
                add(urlField, BorderLayout.CENTER)
            },
        )
    }

    /** Keep BoxLayout from stretching a row vertically past its preferred height. */
    private fun capHeight(panel: JPanel): JPanel = panel.apply {
        maximumSize = java.awt.Dimension(Int.MAX_VALUE, preferredSize.height)
    }

    private fun buildReloadRow(): JComponent {
        val settings = WebBrowserSettings.getInstance()

        reloadOnSaveCheck.toolTipText =
            "Reload when a watched file is saved. Set the folder & extensions in Settings → Tools → Web Browser Panel."
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

        return capHeight(
            JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(2))).apply {
                add(reloadOnSaveCheck)
                add(autoRefreshCheck)
                add(secondsSpinner)
                add(JBLabel("s"))
            },
        )
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

    /** Show the themed SVG home page — same view for first-open and load failures. */
    private fun showPlaceholder() {
        browser?.loadHTML(WebBrowserPlaceholder.html())
    }

    private fun navigate(raw: String) {
        // Address-bar semantics: URL-like inputs go to the URL, everything else
        // becomes a Startpage search for the verbatim text.
        Url.fromAddressBar(raw).onSome(::load)
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
        // Use the same address-bar interpretation as Enter, so if the user
        // typed a search query and hit "Open in…", the external browser also
        // gets the Startpage URL.
        val home = WebBrowserSettings.getInstance().homeUrl
        val url = Url.fromAddressBar(urlField.text).getOrNull()
            ?: Url.parse(home).getOrNull()
            ?: return
        service<BrowserLauncher>().browse(url.value, externalBrowser, project)
    }

    private data class OpenTarget(val label: String, val browser: WebBrowser?)

    /**
     * Build an [ExtendableTextComponent.Extension] that sits on the left side of
     * the URL field. [enabled] is sampled each time the field paints, so the
     * icon greys out as soon as `urlField.repaint()` is called after the state
     * flag flips. Clicks while disabled are silently dropped.
     */
    private fun navExtension(
        icon: Icon,
        tooltip: String,
        enabled: () -> Boolean,
        action: () -> Unit,
    ): ExtendableTextComponent.Extension = object : ExtendableTextComponent.Extension {
        override fun getIcon(hovered: Boolean): Icon =
            if (enabled()) icon else IconLoader.getDisabledIcon(icon)
        override fun getTooltip(): String = tooltip
        override fun isIconBeforeText(): Boolean = true
        override fun getActionOnClick(): Runnable = Runnable { if (enabled()) action() }
    }

    private companion object {
        const val RELOAD_DEBOUNCE_MS = 250

        // Zoom step / clamp range live in the pure domain ZoomLevel object.

        // Custom context-menu command IDs (must live in the user range so they
        // don't collide with Chromium's built-in commands).
        const val CTX_ZOOM_GROUP = CefMenuModel.MenuId.MENU_ID_USER_FIRST + 1
        const val CTX_ZOOM_IN = CefMenuModel.MenuId.MENU_ID_USER_FIRST + 2
        const val CTX_ZOOM_OUT = CefMenuModel.MenuId.MENU_ID_USER_FIRST + 3
        const val CTX_ZOOM_RESET = CefMenuModel.MenuId.MENU_ID_USER_FIRST + 4

        // After each main-frame load: if the page didn't opt into a color
        // scheme of its own (no `color-scheme` on <html>, no meta tag), set
        // `color-scheme: light` AND, when the body is transparent, paint it
        // white. The latter is what actually defeats the dark JCEF canvas
        // showing through on minimal pages (Astro starter, blank HTML, etc.).
        // No-op on pages that already specify a scheme or a body background.
        private val FORCE_LIGHT_FOR_UNTHEMED_JS = """
            (function () {
              var root = document.documentElement;
              var body = document.body;
              if (!root || !body) return;
              var declared = (root.style.colorScheme || '').trim() ||
                             (getComputedStyle(root).colorScheme || '').trim();
              if (declared && declared !== 'normal') return;
              root.style.colorScheme = 'light';
              var bg = getComputedStyle(body).backgroundColor;
              if (bg === 'rgba(0, 0, 0, 0)' || bg === 'transparent' || !bg) {
                body.style.backgroundColor = 'white';
              }
            })();
        """.trimIndent()
    }
}
