package no.dervis.webbrowser.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefBrowser
import no.dervis.webbrowser.domain.AddressBarFilter
import no.dervis.webbrowser.domain.TabId
import no.dervis.webbrowser.domain.Url
import no.dervis.webbrowser.domain.ViewSourceTransform
import no.dervis.webbrowser.domain.ZoomLevel
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.callback.CefContextMenuParams
import org.cef.callback.CefMenuModel
import org.cef.handler.CefContextMenuHandlerAdapter
import org.cef.handler.CefDisplayHandlerAdapter
import org.cef.handler.CefLifeSpanHandlerAdapter
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefLoadHandlerAdapter
import javax.swing.SwingUtilities

/**
 * One browser tab: a single JBCefBrowser plus its handlers and per-tab state
 * (zoom level, navigation availability, last displayed URL).
 *
 * Cross-tab concerns surface via [Callbacks] so the parent panel can
 *   - keep the (shared) toolbar in sync with the active tab,
 *   - swap in the placeholder on load errors,
 *   - update the tab title from the page title,
 *   - open a new tab when this one encounters a `target="_blank"` navigation.
 */
internal class BrowserTabPane(
    val id: TabId,
    parentDisposable: Disposable,
    private val callbacks: Callbacks,
) {

    /** Hooks back into the panel. All called on the EDT. */
    interface Callbacks {
        fun onAddressChange(id: TabId, displayableUrl: String)
        fun onLoadingStateChange(id: TabId, canGoBack: Boolean, canGoForward: Boolean)
        fun onLoadError(id: TabId)
        fun onTitleChange(id: TabId, title: String)
        fun onPopupRequested(parent: TabId, url: Url)
        /**
         * "View Page Source" was chosen from the context menu. Implementations
         * should open [sourceUrl] (already prefixed with `view-source:`) in a
         * fresh tab so the user can close that tab to dismiss source view
         * without disturbing the parent tab's history.
         */
        fun onViewSourceRequested(parent: TabId, sourceUrl: String)
    }

    val browser: JBCefBrowser = JBCefBrowser.createBuilder().build()
    val component get() = browser.component

    var canGoBack: Boolean = false; private set
    var canGoForward: Boolean = false; private set
    var zoomLevel: Double = ZoomLevel.DEFAULT; private set

    init {
        Disposer.register(parentDisposable, browser)
        // We intentionally don't force an opaque white background on the
        // wrapper here: that used to defeat JCEF's dark-canvas leak on
        // transparent <body> pages, but it also showed as a bright frame
        // inside JBTabbedPane's content insets. The `FORCE_LIGHT_FOR_UNTHEMED_JS`
        // injection below already handles the canvas leak by setting
        // `body.backgroundColor = 'white'` whenever the body is transparent.
        wireHandlers()
    }

    // ---- Browser operations ---------------------------------------------------

    fun load(url: Url) { browser.loadURL(url.value) }
    /** Load a URL that isn't a regular [Url] (e.g. `view-source:…`). */
    fun loadRaw(rawUrl: String) { browser.loadURL(rawUrl) }
    fun loadHtml(html: String) { browser.loadHTML(html) }
    fun goBack() { browser.cefBrowser.goBack() }
    fun goForward() { browser.cefBrowser.goForward() }
    fun reload() { browser.cefBrowser.reload() }
    fun currentUrl(): String = browser.cefBrowser.url.orEmpty()

    fun adjustZoom(delta: Double) {
        zoomLevel = ZoomLevel.next(zoomLevel, delta)
        browser.cefBrowser.zoomLevel = zoomLevel
    }

    fun resetZoom() {
        zoomLevel = ZoomLevel.DEFAULT
        browser.cefBrowser.zoomLevel = ZoomLevel.DEFAULT
    }

    // ---- Handler wiring ------------------------------------------------------

    private fun wireHandlers() {
        browser.jbCefClient.addDisplayHandler(object : CefDisplayHandlerAdapter() {
            override fun onAddressChange(cefBrowser: CefBrowser?, frame: CefFrame?, url: String?) {
                AddressBarFilter.displayable(url)?.let { shown ->
                    SwingUtilities.invokeLater { callbacks.onAddressChange(id, shown) }
                }
            }
            override fun onTitleChange(cefBrowser: CefBrowser?, title: String?) {
                // JCEF synthesises an internal pseudo-URL as the "title" when
                // loadHTML(...) is used on a page without a <title> tag — that
                // surfaces as ugly strings like "1908504379#url=about:blank".
                // Only forward titles that came from a real http(s) page; for
                // anything else (data:, about:, blank URL) fall back to a
                // sensible default the tab strip can show.
                val url = cefBrowser?.url.orEmpty()
                val effective = when {
                    url.startsWith("http://") || url.startsWith("https://") ->
                        title?.takeIf { it.isNotBlank() } ?: FALLBACK_TAB_TITLE
                    url.startsWith("view-source:") -> VIEW_SOURCE_TAB_TITLE
                    else -> FALLBACK_TAB_TITLE
                }
                SwingUtilities.invokeLater { callbacks.onTitleChange(id, effective) }
            }
        }, browser.cefBrowser)

        browser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadingStateChange(
                cefBrowser: CefBrowser?,
                isLoading: Boolean,
                backAvailable: Boolean,
                forwardAvailable: Boolean,
            ) {
                SwingUtilities.invokeLater {
                    canGoBack = backAvailable
                    canGoForward = forwardAvailable
                    callbacks.onLoadingStateChange(id, backAvailable, forwardAvailable)
                }
            }

            override fun onLoadError(
                cefBrowser: CefBrowser?,
                frame: CefFrame?,
                errorCode: CefLoadHandler.ErrorCode?,
                errorText: String?,
                failedUrl: String?,
            ) {
                if (frame?.isMain != true) return
                if (errorCode == CefLoadHandler.ErrorCode.ERR_ABORTED) return
                SwingUtilities.invokeLater { callbacks.onLoadError(id) }
            }

            // Force light scheme on untheme pages — see WebBrowserPanel for the rationale.
            override fun onLoadEnd(cefBrowser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                if (frame?.isMain != true) return
                val url = cefBrowser?.url.orEmpty()
                if (url.startsWith("data:") || url.startsWith("about:")) return
                frame.executeJavaScript(FORCE_LIGHT_FOR_UNTHEMED_JS, url, 0)
            }
        }, browser.cefBrowser)

        browser.jbCefClient.addContextMenuHandler(object : CefContextMenuHandlerAdapter() {
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
                    // Open the source view in a NEW tab. Loading `view-source:`
                    // inline used to wedge the navigation history (Back went all
                    // the way to the placeholder, and there was no obvious way
                    // out of source view). With a new tab the user just closes
                    // it to return to the rendered page.
                    ViewSourceTransform.targetFor(current)?.let { sourceUrl ->
                        SwingUtilities.invokeLater {
                            callbacks.onViewSourceRequested(id, sourceUrl)
                        }
                    }
                    true
                }
                CTX_ZOOM_IN -> { SwingUtilities.invokeLater { adjustZoom(+ZoomLevel.STEP) }; true }
                CTX_ZOOM_OUT -> { SwingUtilities.invokeLater { adjustZoom(-ZoomLevel.STEP) }; true }
                CTX_ZOOM_RESET -> { SwingUtilities.invokeLater { resetZoom() }; true }
                else -> false
            }
        }, browser.cefBrowser)

        // THE tabs hook: intercept target="_blank" / window.open() popups and
        // hand the URL to the panel so it can spawn a real in-IDE tab instead
        // of letting JCEF launch an external native window.
        browser.jbCefClient.addLifeSpanHandler(object : CefLifeSpanHandlerAdapter() {
            override fun onBeforePopup(
                cefBrowser: CefBrowser?,
                frame: CefFrame?,
                targetUrl: String?,
                targetFrameName: String?,
            ): Boolean {
                if (!targetUrl.isNullOrBlank()) {
                    Url.parse(targetUrl).onSome { url ->
                        SwingUtilities.invokeLater { callbacks.onPopupRequested(id, url) }
                    }
                }
                return true // cancel the default popup creation
            }
        }, browser.cefBrowser)
    }

    private companion object {
        const val FALLBACK_TAB_TITLE = "New Tab"
        const val VIEW_SOURCE_TAB_TITLE = "View Source"

        const val CTX_ZOOM_GROUP = CefMenuModel.MenuId.MENU_ID_USER_FIRST + 1
        const val CTX_ZOOM_IN = CefMenuModel.MenuId.MENU_ID_USER_FIRST + 2
        const val CTX_ZOOM_OUT = CefMenuModel.MenuId.MENU_ID_USER_FIRST + 3
        const val CTX_ZOOM_RESET = CefMenuModel.MenuId.MENU_ID_USER_FIRST + 4

        // Force `color-scheme: light` on pages that don't declare one, and a
        // white body background when the body is transparent. Same JS the old
        // single-browser panel used.
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
