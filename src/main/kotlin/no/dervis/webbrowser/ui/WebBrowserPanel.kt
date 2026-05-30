package no.dervis.webbrowser.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.browsers.BrowserLauncher
import com.intellij.ide.browsers.WebBrowser
import com.intellij.ide.browsers.WebBrowserManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
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
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.jcef.JBCefApp
import com.intellij.util.Alarm
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.ui.JBUI
import no.dervis.webbrowser.BrowserView
import no.dervis.webbrowser.WebBrowserController
import no.dervis.webbrowser.domain.AddressBarRequest
import no.dervis.webbrowser.domain.BrowserTabsState
import no.dervis.webbrowser.domain.ReloadRule
import no.dervis.webbrowser.domain.TabId
import no.dervis.webbrowser.domain.Url
import no.dervis.webbrowser.domain.ZoomLevel
import no.dervis.webbrowser.settings.WebBrowserProjectSettings
import no.dervis.webbrowser.settings.WebBrowserSettings
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.util.concurrent.atomic.AtomicLong
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.Timer

/**
 * The tool-window UI. Holds multiple browser tabs (each a [BrowserTabPane]) and a
 * shared toolbar that routes nav / zoom / address-bar actions to the active tab.
 *
 * Implements [BrowserView] so the controller can load a URL into the active tab
 * from outside (e.g. when a run configuration starts). Falls back to a static
 * message if the IDE runtime lacks JCEF support.
 */
class WebBrowserPanel(
    private val project: Project,
    private val parentDisposable: Disposable,
) : JPanel(BorderLayout()), BrowserView {

    private val urlField = ExtendableTextField()

    private val reloadOnSaveCheck = JCheckBox("Reload on save")
    private val autoRefreshCheck = JCheckBox("Auto-refresh")
    private val intervalCombo = ComboBox(AUTO_REFRESH_PRESETS)

    // Tabs --------------------------------------------------------------------

    private val tabbedPane = JBTabbedPane()
    private val tabs = mutableMapOf<TabId, BrowserTabPane>()
    private val nextTabId = AtomicLong(0)
    private var tabsState: BrowserTabsState = BrowserTabsState.EMPTY
    private val tabSupported: Boolean = JBCefApp.isSupported()

    // Live-reload plumbing.
    private val reloadAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, parentDisposable)
    private var vfsConnection: MessageBusConnection? = null
    private var refreshTimer: Timer? = null

    // Reflected from the active tab; the inline extensions read these via the
    // navExtension factory so they grey out as soon as we repaint the field.
    private var canGoBack: Boolean = false
    private var canGoForward: Boolean = false

    private val backExtension = navExtension(AllIcons.Actions.Back, "Go back", { canGoBack }) {
        activeTab()?.goBack()
    }
    private val forwardExtension = navExtension(AllIcons.Actions.Forward, "Go forward", { canGoForward }) {
        activeTab()?.goForward()
    }
    private val reloadExtension = navExtension(AllIcons.Actions.Refresh, "Reload", { true }) {
        activeTab()?.reload()
    }

    // Collapsible settings row state — toggle is in the nav row, persisted
    // across sessions in WebBrowserSettings.
    private var settingsExpanded = false
    private lateinit var settingsToggleButton: JButton
    private lateinit var settingsRow: JComponent

    private val tabCallbacks = object : BrowserTabPane.Callbacks {
        override fun onAddressChange(id: TabId, displayableUrl: String) {
            if (id == tabsState.activeId) urlField.text = displayableUrl
        }
        override fun onLoadingStateChange(id: TabId, canGoBack: Boolean, canGoForward: Boolean) {
            if (id == tabsState.activeId) {
                this@WebBrowserPanel.canGoBack = canGoBack
                this@WebBrowserPanel.canGoForward = canGoForward
                urlField.repaint()
            }
        }
        override fun onLoadError(id: TabId) {
            tabs[id]?.loadHtml(WebBrowserPlaceholder.html())
        }
        override fun onTitleChange(id: TabId, title: String) {
            setTabTitle(id, title)
        }
        override fun onPopupRequested(parent: TabId, url: Url) {
            openInNewTab(url)
        }
        override fun onViewSourceRequested(parent: TabId, sourceUrl: String) {
            openSourceInNewTab(sourceUrl)
        }
    }

    init {
        if (!tabSupported) {
            add(
                JBLabel(
                    "The embedded browser (JCEF) is not available in this IDE runtime.",
                    SwingConstants.CENTER,
                ).apply { foreground = JBColor.GRAY },
                BorderLayout.CENTER,
            )
        } else {
            val settings = WebBrowserSettings.getInstance()
            urlField.text = settings.homeUrl
            settingsExpanded = settings.settingsPanelExpanded

            add(buildHeader(), BorderLayout.NORTH)
            add(tabbedPane, BorderLayout.CENTER)

            // Switching tabs has to refresh the shared toolbar to reflect the
            // new active tab's URL / nav state / zoom level.
            tabbedPane.addChangeListener { handleTabSelectionChanged() }

            // Open the initial tab (which shows the placeholder).
            openInitialTab()

            // Ensure watchers/timers are torn down with the tool window. The
            // per-tab JBCefBrowsers are already registered with parentDisposable
            // inside BrowserTabPane, so we don't dispose them here.
            Disposer.register(parentDisposable) {
                refreshTimer?.stop()
                vfsConnection?.disconnect()
            }

            // Register with the controller so run-config triggers can drive
            // this view; this also picks up any URL queued before the view existed.
            val controller = WebBrowserController.getInstance(project)
            controller.register(this)
            Disposer.register(parentDisposable) { controller.unregister(this) }

            // Apply persisted toggle defaults.
            if (settings.reloadOnSave) setReloadOnSave(true)
            if (settings.autoRefresh) setAutoRefresh(true, currentIntervalSeconds())
        }
    }

    // ---- BrowserView ---------------------------------------------------------

    /** Load [url] into the currently active tab. */
    override fun load(url: Url) {
        activeTab()?.load(url)
    }

    // ---- Tab management ------------------------------------------------------

    private fun openInitialTab() {
        val tab = newTab()
        tabsState = BrowserTabsState.withInitial(tab.id)
        tab.loadHtml(WebBrowserPlaceholder.html())
        tabbedPane.selectedIndex = tabbedPane.indexOfComponent(tab.component)
    }

    /** Create a new tab, load [url] in it, and make it active. */
    private fun openInNewTab(url: Url) {
        val tab = newTab()
        tabsState = tabsState.open(tab.id)
        tab.load(url)
        tabbedPane.selectedIndex = tabbedPane.indexOfComponent(tab.component)
    }

    /**
     * Open a `view-source:<url>` target in a fresh tab. Loading it inline used
     * to scramble the parent tab's history (Back ended up on the placeholder),
     * and there was no way to dismiss source view. As its own tab the user can
     * just close it to return to the rendered page.
     */
    private fun openSourceInNewTab(sourceUrl: String) {
        val tab = newTab()
        tabsState = tabsState.open(tab.id)
        tab.loadRaw(sourceUrl)
        tabbedPane.selectedIndex = tabbedPane.indexOfComponent(tab.component)
    }

    /** Create a new tab with the home page (placeholder). */
    private fun openBlankTab() {
        val tab = newTab()
        tabsState = tabsState.open(tab.id)
        tab.loadHtml(WebBrowserPlaceholder.html())
        tabbedPane.selectedIndex = tabbedPane.indexOfComponent(tab.component)
    }

    private fun newTab(): BrowserTabPane {
        val tab = BrowserTabPane(TabId(nextTabId.getAndIncrement()), parentDisposable, tabCallbacks)
        tabs[tab.id] = tab
        val title = "New Tab"
        tabbedPane.addTab(title, tab.component)
        val idx = tabbedPane.indexOfComponent(tab.component)
        tabbedPane.setTabComponentAt(idx, buildTabHeader(tab.id, title))
        return tab
    }

    private fun closeTab(id: TabId) {
        val tab = tabs.remove(id) ?: return
        val idx = tabbedPane.indexOfComponent(tab.component)
        if (idx >= 0) tabbedPane.removeTabAt(idx)
        tabsState = tabsState.close(id)
        Disposer.dispose(tab.browser)
        // Never leave the user with zero tabs — open a fresh blank one.
        if (tabsState.isEmpty) openBlankTab()
    }

    private fun activeTab(): BrowserTabPane? = tabsState.activeId?.let(tabs::get)

    private fun handleTabSelectionChanged() {
        val idx = tabbedPane.selectedIndex
        if (idx < 0) return
        val selected = tabbedPane.getComponentAt(idx)
        val newActive = tabs.values.firstOrNull { it.component === selected } ?: return
        if (newActive.id == tabsState.activeId) return
        tabsState = tabsState.activate(newActive.id)
        refreshToolbarForActive(newActive)
    }

    private fun refreshToolbarForActive(tab: BrowserTabPane) {
        canGoBack = tab.canGoBack
        canGoForward = tab.canGoForward
        urlField.text = tab.currentUrl().takeIf { it.startsWith("http://") || it.startsWith("https://") }
            ?: WebBrowserSettings.getInstance().homeUrl
        urlField.repaint()
    }

    private fun buildTabHeader(id: TabId, title: String): JComponent {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0)).apply {
            isOpaque = false
            border = JBUI.Borders.empty()
        }
        panel.add(JLabel(title).apply { putClientProperty(TAB_LABEL_KEY, true) })
        panel.add(
            JButton(AllIcons.Actions.Close).apply {
                toolTipText = "Close tab"
                isBorderPainted = false
                isContentAreaFilled = false
                isFocusPainted = false
                isOpaque = false
                preferredSize = Dimension(JBUI.scale(16), JBUI.scale(16))
                border = JBUI.Borders.empty()
                addActionListener { closeTab(id) }
            },
        )
        return panel
    }

    private fun setTabTitle(id: TabId, title: String) {
        val tab = tabs[id] ?: return
        val idx = tabbedPane.indexOfComponent(tab.component)
        if (idx < 0) return
        val header = tabbedPane.getTabComponentAt(idx) as? JPanel ?: return
        // The first component in the header is the label (see buildTabHeader).
        val label = header.components.firstOrNull { it is JLabel && it.getClientProperty(TAB_LABEL_KEY) == true } as? JLabel
        label?.text = title.take(MAX_TAB_TITLE_CHARS)
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
            settingsRow = buildSettingsRow().also { it.isVisible = settingsExpanded }
            add(settingsRow)
        }

    private fun buildNavRow(): JComponent {
        val homeButton = JButton("Home").apply {
            toolTipText = "Go to the configured home / dev-server URL"
            isFocusable = false
            addActionListener { navigate(WebBrowserSettings.getInstance().homeUrl) }
        }
        val newTabButton = JButton("New Tab", AllIcons.General.Add).apply {
            horizontalTextPosition = SwingConstants.RIGHT
            iconTextGap = JBUI.scale(4)
            toolTipText = "Open a new tab"
            isFocusable = false
            addActionListener { openBlankTab() }
        }
        settingsToggleButton = JButton("Settings", settingsToggleIcon()).apply {
            horizontalTextPosition = SwingConstants.LEFT
            iconTextGap = JBUI.scale(4)
            toolTipText = "Show / hide reload, auto-refresh, zoom and Open-in"
            isFocusable = false
            addActionListener { toggleSettings() }
        }

        val left = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(2), JBUI.scale(2))).apply {
            add(homeButton); add(newTabButton)
        }
        val right = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(4), JBUI.scale(2))).apply {
            add(settingsToggleButton)
        }
        return capHeight(
            JPanel(BorderLayout()).apply {
                add(left, BorderLayout.WEST)
                add(right, BorderLayout.EAST)
            },
        )
    }

    private fun settingsToggleIcon(): Icon =
        if (settingsExpanded) AllIcons.General.ArrowUp else AllIcons.General.ArrowDown

    private fun toggleSettings() {
        settingsExpanded = !settingsExpanded
        settingsRow.isVisible = settingsExpanded
        settingsToggleButton.icon = settingsToggleIcon()
        WebBrowserSettings.getInstance().settingsPanelExpanded = settingsExpanded
        revalidate()
        repaint()
    }

    private fun buildUrlRow(): JComponent {
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
        maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
    }

    private fun buildSettingsRow(): JComponent {
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

        // Dropdown of preset intervals. The "s" suffix is baked into the
        // renderer so we don't need a standalone label next to it — that's
        // what keeps the row compact enough for the Zoom button to fit.
        intervalCombo.renderer = object : SimpleListCellRenderer<Int>() {
            override fun customize(
                list: javax.swing.JList<out Int>,
                value: Int?,
                index: Int,
                selected: Boolean,
                hasFocus: Boolean,
            ) {
                text = value?.let { "$it s" }.orEmpty()
            }
        }
        intervalCombo.selectedItem = snapToPreset(settings.reloadIntervalSeconds)
        intervalCombo.toolTipText = "Refresh interval"
        // The widest entry is "60 s" — clamp the field width so the dropdown
        // doesn't claim ~120 px of toolbar space it doesn't need.
        intervalCombo.preferredSize = Dimension(JBUI.scale(70), intervalCombo.preferredSize.height)
        intervalCombo.maximumSize = intervalCombo.preferredSize
        intervalCombo.addActionListener {
            val s = currentIntervalSeconds()
            settings.reloadIntervalSeconds = s
            if (autoRefreshCheck.isSelected) setAutoRefresh(true, s)
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

        // Single flow row, left-aligned: live-reload toggles + interval combo,
        // then a small strut, then view-control buttons. Avoids the previous
        // BorderLayout WEST/EAST overlap that hid the Zoom button on narrow
        // tool windows.
        return capHeight(
            JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(2))).apply {
                add(reloadOnSaveCheck)
                add(autoRefreshCheck)
                add(intervalCombo)
                add(Box.createHorizontalStrut(JBUI.scale(8)))
                add(zoomButton)
                add(openInButton)
            },
        )
    }

    private fun currentIntervalSeconds(): Int =
        (intervalCombo.selectedItem as? Int) ?: WebBrowserSettings.DEFAULT_INTERVAL_SECONDS

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
        reloadAlarm.addRequest({ activeTab()?.reload() }, RELOAD_DEBOUNCE_MS)
    }

    // ---- Timed auto-refresh --------------------------------------------------

    private fun setAutoRefresh(enabled: Boolean, seconds: Int) {
        refreshTimer?.stop()
        refreshTimer = null
        if (!enabled) return

        refreshTimer = Timer(seconds.coerceAtLeast(1) * 1000) { activeTab()?.reload() }
            .apply { isRepeats = true; start() }
    }

    // ---- Zoom ----------------------------------------------------------------

    private fun showZoomPopup(anchor: JComponent) {
        val actions = listOf(
            ZoomAction("Zoom In", AllIcons.Graph.ZoomIn) { activeTab()?.adjustZoom(+ZoomLevel.STEP) },
            ZoomAction("Zoom Out", AllIcons.Graph.ZoomOut) { activeTab()?.adjustZoom(-ZoomLevel.STEP) },
            ZoomAction("Reset Zoom", AllIcons.Graph.ActualZoom) { activeTab()?.resetZoom() },
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

    // ---- Navigation helpers --------------------------------------------------

    private fun navigate(raw: String) {
        // Address-bar semantics: URL-like inputs go to the URL, everything else
        // becomes a Startpage search for the verbatim text. AddressBarRequest
        // captures the user's intent (Navigate vs Search) — we currently just
        // load the resulting target either way.
        AddressBarRequest.parse(raw).onSome { load(it.target) }
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
        val url = AddressBarRequest.parse(urlField.text).getOrNull()?.target
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
        const val MAX_TAB_TITLE_CHARS = 24
        const val TAB_LABEL_KEY = "wbp.tab.label"

        // Preset intervals offered by the auto-refresh dropdown. Skewed toward
        // the fast end for active dev work, with a couple of longer entries for
        // set-and-forget dashboards.
        val AUTO_REFRESH_PRESETS: Array<Int> = arrayOf(1, 2, 3, 5, 10, 30, 60)

        /**
         * Pick the preset closest to [seconds]. Used when loading a persisted
         * value that came from the old free-form spinner (e.g. 15 s) — the user
         * sees a non-empty selection instead of a blank combo, and rounding
         * preserves their intent within the precision the dropdown offers.
         */
        fun snapToPreset(seconds: Int): Int =
            AUTO_REFRESH_PRESETS.minByOrNull { kotlin.math.abs(it - seconds) }
                ?: WebBrowserSettings.DEFAULT_INTERVAL_SECONDS
    }
}
