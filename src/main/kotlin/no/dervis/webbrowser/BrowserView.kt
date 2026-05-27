package no.dervis.webbrowser

import no.dervis.webbrowser.domain.Url

/**
 * Port for the browser surface the application drives. Implemented by the
 * tool-window panel; lets [WebBrowserController] stay decoupled from the UI.
 */
fun interface BrowserView {
    fun load(url: Url)
}
