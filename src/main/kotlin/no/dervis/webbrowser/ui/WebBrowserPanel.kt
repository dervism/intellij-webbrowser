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
import com.intellij.openapi.ui.Splitter
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.jcef.JBCefApp
import com.intellij.util.Alarm
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.ui.JBUI
import no.dervis.webbrowser.BrowserView
import no.dervis.webbrowser.WebBrowserController
import no.dervis.webbrowser.domain.AddressBarRequest
import no.dervis.webbrowser.domain.ClosedTabHistory
import no.dervis.webbrowser.domain.ConsoleMessage
import no.dervis.webbrowser.domain.HomeUrlResolver
import no.dervis.webbrowser.domain.ReloadRule
import no.dervis.webbrowser.domain.TabId
import no.dervis.webbrowser.domain.TabSession
import no.dervis.webbrowser.domain.Url
import no.dervis.webbrowser.domain.WatchGlobs
import no.dervis.webbrowser.domain.ZoomLevel
import no.dervis.webbrowser.settings.WebBrowserProjectSettings
import no.dervis.webbrowser.settings.WebBrowserSettings
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Toolkit
import java.awt.event.KeyEvent
import java.net.URI
import java.util.concurrent.atomic.AtomicLong
import javax.swing.AbstractAction
import javax.swing.ActionMap
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.InputMap
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.KeyStroke
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
    // The JBTabbedPane is the visible strip; TabRegistry owns the underlying
    // model (which TabId exists, which is active, which are pinned, plus the
    // matching JCEF panes). Keep the registry as the single source of truth —
    // anywhere this file used to ask "is `tabs[id]` still there?", ask the
    // registry instead.
    // Hand-rolled tab strip — full control over the close button, tab height,
    // and pinned-tab drag lock (JTabbedPane and JBTabs both fought us on those).
    private val tabStrip = BrowserTabStrip(object : BrowserTabStrip.Callbacks {
        override fun onSelect(id: TabId) = activateTab(id)
        override fun onClose(id: TabId) = closeTab(id)
        override fun onTogglePin(id: TabId) = togglePin(id)
        override fun onCloseOthers(id: TabId) = closeOtherTabs(id)
        override fun onReorder(orderedIds: List<TabId>) {
            registry.syncOrder(orderedIds)
            persistSession()
        }
    })
    private val registry = TabRegistry()
    private val nextTabId = AtomicLong(0)
    private val tabSupported: Boolean = JBCefApp.isSupported()

    // Live-reload plumbing.
    private val reloadAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, parentDisposable)
    private var vfsConnection: MessageBusConnection? = null
    private var refreshTimer: Timer? = null

    // Reflected from the active tab; the inline extensions read these via the
    // navExtension factory so they grey out as soon as we repaint the field.
    private var canGoBack: Boolean = false
    private var canGoForward: Boolean = false
    private var isLoading: Boolean = false

    private val backExtension = navExtension(AllIcons.Actions.Back, "Go back", { canGoBack }) {
        activeTab()?.goBack()
    }
    private val forwardExtension = navExtension(AllIcons.Actions.Forward, "Go forward", { canGoForward }) {
        activeTab()?.goForward()
    }
    // Reload / stop is the same slot in the URL field — Chromium-style. Icon
    // and click action swap when the page is mid-load.
    private val reloadExtension = object : ExtendableTextComponent.Extension {
        override fun getIcon(hovered: Boolean): Icon =
            if (isLoading) AllIcons.Actions.Suspend else AllIcons.Actions.Refresh
        override fun getTooltip(): String = if (isLoading) "Stop loading" else "Reload"
        override fun isIconBeforeText(): Boolean = true
        override fun getActionOnClick(): Runnable = Runnable {
            if (isLoading) activeTab()?.stopLoad() else activeTab()?.reload()
        }
    }

    // Collapsible settings row state — toggle is in the nav row, persisted
    // across sessions in WebBrowserSettings.
    private var settingsExpanded = false
    private lateinit var settingsToggleButton: JButton
    private lateinit var settingsRow: JComponent

    // Address-bar autocomplete; built in buildUrlRow. Held so programmatic URL
    // updates can route through setTextSilently and not pop the dropdown.
    private lateinit var addressAutocomplete: AddressBarAutocomplete

    // Find-in-page overlay. Self-contained component; we just call show/hide
    // and forward the dispatcher calls to the active tab.
    private val findOverlay: FindOverlay = FindOverlay(object : FindOverlay.Dispatcher {
        override fun runFind(query: String, forward: Boolean, findNext: Boolean) {
            val tab = activeTab() ?: return
            if (query.isEmpty()) tab.stopFind()
            else tab.find(query, forward = forward, matchCase = false, findNext = findNext)
        }
        override fun stopFind() { activeTab()?.stopFind() }
        override fun onCloseRequested() { hideFindOverlay() }
    })

    // Browser console drain (toggled via the settings row button + Ctrl+`).
    private val consolePanel = BrowserConsolePanel()
    private lateinit var consoleSplitter: Splitter
    private var consoleVisible: Boolean = false

    // Closed-tab stack for Ctrl/Cmd+Shift+T. Per-panel only — not persisted.
    private var closedTabs: ClosedTabHistory = ClosedTabHistory.EMPTY

    // Set while we're rebuilding tabs from the persisted session, so the
    // open/close events fired during restore don't immediately overwrite the
    // session we're trying to restore.
    private var isRestoring: Boolean = false

    private val tabCallbacks = object : BrowserTabPane.Callbacks {
        override fun onAddressChange(id: TabId, displayableUrl: String) {
            if (id == registry.state.activeId) addressAutocomplete.setTextSilently(displayableUrl)
            // Per-host zoom: each tab gets the user's remembered zoom level
            // for its current host as soon as the address settles.
            registry.pane(id)?.let { tab ->
                val host = hostOf(displayableUrl)
                val zoom = WebBrowserSettings.getInstance().zoomFor(host)
                if (zoom != tab.zoomLevel) tab.setZoom(zoom)
            }
            persistSession()
        }
        override fun onLoadingStateChange(
            id: TabId,
            canGoBack: Boolean,
            canGoForward: Boolean,
            isLoading: Boolean,
        ) {
            if (id == registry.state.activeId) {
                this@WebBrowserPanel.canGoBack = canGoBack
                this@WebBrowserPanel.canGoForward = canGoForward
                this@WebBrowserPanel.isLoading = isLoading
                urlField.repaint()
            }
        }
        override fun onLoadError(id: TabId) {
            registry.pane(id)?.loadHtml(WebBrowserPlaceholder.html())
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
        override fun onConsoleMessage(id: TabId, message: ConsoleMessage) {
            // Only surface console output for the currently active tab —
            // background tabs spamming logs into a panel the user isn't
            // looking at would be more confusing than useful.
            if (id == registry.state.activeId) consolePanel.append(message)
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
            urlField.text = effectiveHomeUrl()
            settingsExpanded = settings.settingsPanelExpanded

            add(buildHeader(), BorderLayout.NORTH)
            consoleSplitter = Splitter(true, 0.75f).apply {
                firstComponent = tabStrip.component
                secondComponent = consolePanel.component.also { it.isVisible = false }
            }
            add(consoleSplitter, BorderLayout.CENTER)

            // Restore the previously persisted tab session, or fall back to a
            // single placeholder tab on a fresh project.
            restoreTabSessionOrInitial()

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

            // Keyboard shortcuts that act on whichever tab is focused: find,
            // hard reload, reopen-closed-tab, devtools.
            registerKeyBindings()
        }
    }

    // ---- BrowserView ---------------------------------------------------------

    /** Load [url] into the currently active tab. */
    override fun load(url: Url) {
        activeTab()?.load(url)
    }

    // ---- Tab management ------------------------------------------------------

    // The tab whose content is currently shown; used to clear the console only
    // on a genuine switch (not on re-selecting the same tab).
    private var shownTab: TabId? = null

    /** The canonical "pinned tabs first" order: pinned in their relative order, then the rest. */
    private fun pinnedFirstOrder(): List<TabId> {
        val state = registry.state
        val (pinned, rest) = state.tabs.partition { state.isPinned(it) }
        return pinned + rest
    }

    /** Re-assert the pinned-first grouping in both the model and the strip. */
    private fun enforcePinnedOrder() {
        val order = pinnedFirstOrder()
        registry.syncOrder(order)
        tabStrip.setOrder(order)
    }

    private fun openInitialTab() {
        val tab = newTab()
        tab.loadHtml(WebBrowserPlaceholder.html())
        activateTab(tab.id)
    }

    /**
     * Replay the persisted [TabSession] on the project, or fall back to the
     * single placeholder tab when there's nothing to restore. Wrapped in
     * [isRestoring] so the address-change callbacks fired during load don't
     * try to persist a half-built session back.
     */
    private fun restoreTabSessionOrInitial() {
        val session = WebBrowserProjectSettings.getInstance(project).tabSession()
        val urls = session.resolvedUrls()
        if (urls.isEmpty()) {
            openInitialTab()
            return
        }
        isRestoring = true
        try {
            val createdTabs = urls.mapIndexed { idx, url ->
                val tab = newTab()
                if (session.isPinnedAt(idx)) {
                    registry.pin(tab.id)
                    tabStrip.setPinned(tab.id, true)
                }
                tab.load(url)
                tab
            }
            val activeIdx = session.safeActiveIndex().coerceIn(0, createdTabs.lastIndex)
            activateTab(createdTabs[activeIdx].id)
        } finally {
            isRestoring = false
        }
        enforcePinnedOrder()
    }

    /** Create a new tab, load [url] in it, and make it active. */
    private fun openInNewTab(url: Url) {
        val tab = newTab()
        tab.load(url)
        activateTab(tab.id)
        persistSession()
    }

    /** Reopen the most-recently-closed tab (Ctrl/Cmd+Shift+T). No-op when empty. */
    private fun reopenClosedTab() {
        val (entry, rest) = closedTabs.pop() ?: return
        closedTabs = rest
        openInNewTab(entry.url)
    }

    /**
     * Open a `view-source:<url>` target in a fresh tab. Loading it inline used
     * to scramble the parent tab's history (Back ended up on the placeholder),
     * and there was no way to dismiss source view. As its own tab the user can
     * just close it to return to the rendered page.
     */
    private fun openSourceInNewTab(sourceUrl: String) {
        val tab = newTab()
        tab.loadRaw(sourceUrl)
        activateTab(tab.id)
        // No persist: view-source tabs aren't restored (only http(s) URLs are).
    }

    /** Create a new tab with the home page (placeholder). */
    private fun openBlankTab() {
        val tab = newTab()
        tab.loadHtml(WebBrowserPlaceholder.html())
        activateTab(tab.id)
        persistSession()
    }

    /**
     * Capture the current tab strip into [TabSession] and store it in the
     * project settings. Skips persistence while we're restoring (otherwise the
     * intermediate states during replay would clobber the session we're
     * loading from).
     */
    private fun persistSession() {
        if (isRestoring) return
        val state = registry.state
        val rawUrls = state.tabs.mapNotNull { registry.pane(it)?.currentUrl() }
        val activeUrl = state.activeId?.let { registry.pane(it)?.currentUrl() }
        val pinnedUrls = state.pinned.mapNotNull { registry.pane(it)?.currentUrl() }.toSet()
        val session = TabSession.capture(rawUrls, activeUrl, pinnedUrls)
        WebBrowserProjectSettings.getInstance(project).saveTabSession(session)
    }

    /** Best-effort host extraction; tolerates malformed URLs by returning `null`. */
    private fun hostOf(rawUrl: String): String? =
        runCatching { URI(rawUrl).host }.getOrNull()

    private fun newTab(): BrowserTabPane {
        val tab = BrowserTabPane(TabId(nextTabId.getAndIncrement()), parentDisposable, tabCallbacks)
        registry.add(tab)
        tabStrip.addTab(tab.id, "New Tab", tab.component, pinned = false)
        return tab
    }

    /** Make [id] the active tab: update the model, show its content, sync the toolbar. */
    private fun activateTab(id: TabId) {
        registry.activate(id)
        tabStrip.select(id)
        registry.pane(id)?.let { refreshToolbarForActive(it) }
        if (shownTab != id) {
            consolePanel.clear()
            shownTab = id
        }
    }

    /** Close [id]: record it for reopen, dispose the browser, reconcile, activate a neighbour. */
    private fun closeTab(id: TabId) {
        val pane = registry.pane(id) ?: return
        val current = pane.currentUrl()
        if (current.startsWith("http://") || current.startsWith("https://")) {
            Url.parse(current).onSome { url ->
                closedTabs = closedTabs.push(ClosedTabHistory.Entry(url, url.value))
            }
        }
        tabStrip.removeTab(id)
        registry.remove(id)
        Disposer.dispose(pane.browser)
        if (registry.isEmpty) {
            // Never leave the user with zero tabs — open a fresh blank one.
            if (!isRestoring) openBlankTab()
        } else {
            // Activate the right-neighbour BrowserTabsState.close chose.
            registry.state.activeId?.let { activateTab(it) }
        }
        if (!isRestoring) persistSession()
    }

    /** Close every tab except [keepId], honouring "pinned tabs stay open". */
    private fun closeOtherTabs(keepId: TabId) {
        val state = registry.state
        val victims = state.tabs.filter { it != keepId && !state.isPinned(it) }
        victims.forEach(::closeTab)
    }

    private fun togglePin(id: TabId) {
        val nowPinned = !registry.isPinned(id)
        if (nowPinned) registry.pin(id) else registry.unpin(id)
        tabStrip.setPinned(id, nowPinned)
        enforcePinnedOrder()
        persistSession()
    }

    private fun activeTab(): BrowserTabPane? = registry.active()

    private fun refreshToolbarForActive(tab: BrowserTabPane) {
        canGoBack = tab.canGoBack
        canGoForward = tab.canGoForward
        isLoading = tab.isLoading
        val text = tab.currentUrl().takeIf { it.startsWith("http://") || it.startsWith("https://") }
            ?: effectiveHomeUrl()
        addressAutocomplete.setTextSilently(text)
        urlField.repaint()
        // The find overlay is global — close it on tab switch so a stale
        // query doesn't carry across tabs.
        if (findOverlay.isVisible) hideFindOverlay()
    }

    /** The home / dev-server URL, with the per-project override taking precedence. */
    private fun effectiveHomeUrl(): String =
        HomeUrlResolver.effective(
            WebBrowserProjectSettings.getInstance(project).projectHomeUrl,
            WebBrowserSettings.getInstance().homeUrl,
        )

    private fun setTabTitle(id: TabId, title: String) {
        tabStrip.setTitle(id, title)
    }

    // ---- Toolbar -------------------------------------------------------------

    // Four stacked rows so the URL field gets its own line (full width) instead
    // of being squeezed between the button groups and disappearing on narrow
    // tool windows. Find overlay is hidden until Ctrl/Cmd+F.
    private fun buildHeader(): JComponent =
        JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(2)
            add(buildNavRow())
            add(buildUrlRow())
            add(findOverlay.component)
            settingsRow = buildSettingsRow().also { it.isVisible = settingsExpanded }
            add(settingsRow)
        }

    private fun buildNavRow(): JComponent {
        val homeButton = JButton("Home").apply {
            toolTipText = "Go to the configured home / dev-server URL"
            isFocusable = false
            addActionListener { navigate(effectiveHomeUrl()) }
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
        // Select the whole URL when the field gains focus, so a click lets you
        // immediately type a new address (like every browser's address bar).
        // Deferred to invokeLater so it runs after the click positions the caret.
        urlField.addFocusListener(object : java.awt.event.FocusAdapter() {
            override fun focusGained(e: java.awt.event.FocusEvent) {
                SwingUtilities.invokeLater { if (urlField.isFocusOwner) urlField.selectAll() }
            }
        })
        // Address-bar autocomplete: as the user types, suggest previously
        // visited URLs from this project's history. The dropdown installs its
        // own listeners on the field — we just hand it the data source and a
        // callback for "user picked one".
        addressAutocomplete = AddressBarAutocomplete(
            field = urlField,
            historyProvider = {
                WebBrowserProjectSettings.getInstance(project).addressBarHistory()
            },
            onAccept = { navigate(it) },
        )
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
        val devToolsButton = JButton(AllIcons.Toolwindows.ToolWindowDebugger).apply {
            toolTipText = "Open DevTools (F12)"
            isFocusable = false
            addActionListener { activeTab()?.openDevTools() }
        }
        val consoleToggle = JButton(AllIcons.Debugger.Console).apply {
            toolTipText = "Show / hide browser console"
            isFocusable = false
            addActionListener { toggleConsole() }
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
                add(devToolsButton)
                add(consoleToggle)
            },
        )
    }

    private fun toggleConsole() {
        consoleVisible = !consoleVisible
        consolePanel.component.isVisible = consoleVisible
        consoleSplitter.proportion = if (consoleVisible) 0.7f else 1.0f
        consoleSplitter.revalidate()
        consoleSplitter.repaint()
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
                if (events.any { it.isFileSave() && saveTriggersReload(it.path) }) scheduleReload()
            }
        })
        vfsConnection = connection
    }

    /**
     * Decide whether a saved file should trigger reload. Glob patterns (when
     * set) take precedence; otherwise we fall back to the simple
     * folder + extensions [ReloadRule].
     */
    private fun saveTriggersReload(savedPath: String): Boolean {
        val proj = WebBrowserProjectSettings.getInstance(project)
        val globs = WatchGlobs.parse(proj.watchPatterns)
        if (!globs.isEmpty) return globs.matches(savedPath)
        val root = proj.watchPath.ifBlank { project.basePath.orEmpty() }
        return ReloadRule.of(root, WebBrowserSettings.getInstance().watchExtensions).matches(savedPath)
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
            ZoomAction("Zoom In", AllIcons.Graph.ZoomIn) { applyZoomDelta(+ZoomLevel.STEP) },
            ZoomAction("Zoom Out", AllIcons.Graph.ZoomOut) { applyZoomDelta(-ZoomLevel.STEP) },
            ZoomAction("Reset Zoom", AllIcons.Graph.ActualZoom) { resetActiveZoom() },
        )
        val step = object : BaseListPopupStep<ZoomAction>("Zoom", actions) {
            override fun getTextFor(value: ZoomAction): String = value.label
            override fun getIconFor(value: ZoomAction): Icon = value.icon
            override fun onChosen(selectedValue: ZoomAction, finalChoice: Boolean): PopupStep<*>? =
                doFinalStep { selectedValue.run() }
        }
        JBPopupFactory.getInstance().createListPopup(step).showUnderneathOf(anchor)
    }

    private fun applyZoomDelta(delta: Double) {
        val tab = activeTab() ?: return
        tab.adjustZoom(delta)
        rememberActiveHostZoom(tab)
    }

    private fun resetActiveZoom() {
        val tab = activeTab() ?: return
        tab.resetZoom()
        rememberActiveHostZoom(tab)
    }

    private fun rememberActiveHostZoom(tab: BrowserTabPane) {
        val host = hostOf(tab.currentUrl()) ?: return
        WebBrowserSettings.getInstance().rememberZoom(host, tab.zoomLevel)
    }

    private data class ZoomAction(val label: String, val icon: Icon, val run: () -> Unit)

    // ---- Navigation helpers --------------------------------------------------

    private fun navigate(raw: String) {
        // Address-bar semantics: URL-like inputs go to the URL, everything else
        // becomes a Startpage search for the verbatim text. AddressBarRequest
        // captures the user's intent (Navigate vs Search) — we currently just
        // load the resulting target either way.
        AddressBarRequest.parse(raw).onSome { request ->
            load(request.target)
            // Record the resolved URL in the project's history so the next
            // address-bar autocomplete sees it. Records only http(s); the
            // history's own filter quietly drops anything else.
            WebBrowserProjectSettings.getInstance(project)
                .recordAddressBarUrl(request.target.value)
        }
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
        val url = AddressBarRequest.parse(urlField.text).getOrNull()?.target
            ?: Url.parse(effectiveHomeUrl()).getOrNull()
            ?: return
        service<BrowserLauncher>().browse(url.value, externalBrowser, project)
    }

    private data class OpenTarget(val label: String, val browser: WebBrowser?)

    // ---- Find-in-page --------------------------------------------------------

    private fun showFindOverlay() {
        findOverlay.show()
        revalidate()
        repaint()
    }

    private fun hideFindOverlay() {
        findOverlay.hide()
        revalidate()
        repaint()
    }

    // ---- Keyboard shortcuts --------------------------------------------------

    /**
     * Register the in-panel shortcuts. Scope is
     * [JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT] so the bindings fire
     * whenever any descendant (URL field, find field, the JCEF canvas itself
     * via Swing focus) has focus.
     */
    private fun registerKeyBindings() {
        val menuMask = Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx
        val inputMap: InputMap = getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
        val actionMap: ActionMap = actionMap

        fun bind(name: String, stroke: KeyStroke, action: () -> Unit) {
            inputMap.put(stroke, name)
            actionMap.put(name, object : AbstractAction() {
                override fun actionPerformed(e: java.awt.event.ActionEvent) = action()
            })
        }

        bind("wbp.find", KeyStroke.getKeyStroke(KeyEvent.VK_F, menuMask)) { showFindOverlay() }
        bind("wbp.hideFind", KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0)) {
            if (findOverlay.isVisible) hideFindOverlay()
        }
        bind(
            "wbp.hardReload",
            KeyStroke.getKeyStroke(KeyEvent.VK_R, menuMask or KeyEvent.SHIFT_DOWN_MASK),
        ) { activeTab()?.reloadIgnoreCache() }
        bind(
            "wbp.reopenClosed",
            KeyStroke.getKeyStroke(KeyEvent.VK_T, menuMask or KeyEvent.SHIFT_DOWN_MASK),
        ) { reopenClosedTab() }
        bind("wbp.devTools", KeyStroke.getKeyStroke(KeyEvent.VK_F12, 0)) {
            activeTab()?.openDevTools()
        }
        bind(
            "wbp.newTab",
            KeyStroke.getKeyStroke(KeyEvent.VK_T, menuMask),
        ) { openBlankTab() }
        // Ctrl+Tab / Ctrl+Shift+Tab cycle the active tab. We bind Ctrl
        // (not menuMask) for both keymaps because Cmd+Tab on macOS is owned by
        // the OS — Ctrl+Tab is the convention every browser uses there too.
        bind(
            "wbp.nextTab",
            KeyStroke.getKeyStroke(KeyEvent.VK_TAB, KeyEvent.CTRL_DOWN_MASK),
        ) { cycleTab(forward = true) }
        bind(
            "wbp.prevTab",
            KeyStroke.getKeyStroke(KeyEvent.VK_TAB, KeyEvent.CTRL_DOWN_MASK or KeyEvent.SHIFT_DOWN_MASK),
        ) { cycleTab(forward = false) }
        // Ctrl+` toggles the browser console panel (matches DevTools' shortcut).
        bind(
            "wbp.toggleConsole",
            KeyStroke.getKeyStroke(KeyEvent.VK_BACK_QUOTE, KeyEvent.CTRL_DOWN_MASK),
        ) { toggleConsole() }
    }

    /**
     * Activate the next / previous tab (with wrap-around). We compute the next
     * id from the current order and select it; the [TabsListener] then updates
     * the active state and toolbar, so we don't pre-mutate the registry here.
     */
    private fun cycleTab(forward: Boolean) {
        val state = registry.state
        if (state.size < 2) return
        val current = state.tabs.indexOf(state.activeId).takeIf { it >= 0 } ?: return
        val nextIdx = (current + if (forward) 1 else -1).mod(state.tabs.size)
        activateTab(state.tabs[nextIdx])
    }

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
