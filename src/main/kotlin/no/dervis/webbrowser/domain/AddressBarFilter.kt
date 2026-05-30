package no.dervis.webbrowser.domain

/**
 * Decides whether a URL the browser navigated to should be reflected in the
 * address bar. Anything without an `http`/`https` scheme is suppressed — that
 * hides `data:` URLs (our SVG home page), `about:blank`, `view-source:` and
 * any other internal navigation from "leaking" into the visible address.
 */
object AddressBarFilter {

    /**
     * Returns [url] when it should be shown in the address bar, `null` otherwise.
     * Designed so callers can chain with `?.let { ... }` and skip a separate
     * null-check.
     */
    fun displayable(url: String?): String? =
        if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) url
        else null
}
