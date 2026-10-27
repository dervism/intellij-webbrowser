package no.dervis.webbrowser.domain

import arrow.core.Option
import arrow.core.none
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Classified intent behind an address-bar entry. URL-like inputs become a
 * [Navigate]; anything else becomes a [Search] against Startpage.
 *
 * The richer type lets the caller distinguish "the user wanted to navigate"
 * from "the user wanted to search" — both share a [target] [Url] (because
 * navigation and search both end up loading *some* URL in the browser), but
 * the variant carries the original intent for logging, analytics, future UI
 * affordances, etc.
 *
 * Pure: no IntelliJ or Swing dependencies.
 */
sealed interface AddressBarRequest {

    /** The URL to actually load in the embedded browser. */
    val target: Url

    /** URL-like input: explicit scheme, a `/`, a dotted host, localhost, IP, or `host:port`. */
    data class Navigate(override val target: Url) : AddressBarRequest

    /** Free-text input routed to a Startpage search. [query] is the unencoded text. */
    data class Search(val query: String, override val target: Url) : AddressBarRequest

    companion object {
        private const val SEARCH_PREFIX = "https://www.startpage.com/do/search?q="

        /**
         * Classify a raw address-bar entry. Returns [None] when the input is blank,
         * or when it looks like a URL or a local path that [Url.typed] rejects (such
         * as `data:`, `ftp://` or `C:\Users\…`): that input is neither loaded nor
         * sent to the search engine. Input that starts with `scheme://` is always
         * treated as a URL; spaces in it are encoded, as browsers do.
         */
        fun parse(raw: String): Option<AddressBarRequest> {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return none()
            return when {
                Url.hasExplicitScheme(trimmed) -> Url.typed(trimmed.replace(" ", "%20")).map(::Navigate)
                looksLikeUrl(trimmed) -> Url.typed(trimmed).map(::Navigate)
                else -> Url.web("$SEARCH_PREFIX${URLEncoder.encode(trimmed, StandardCharsets.UTF_8)}")
                    .map { Search(trimmed, it) }
            }
        }

        /**
         * What "Open in" hands to another browser: the address-bar entry when it gives
         * an `http` or `https` URL, the [home] URL only when the address bar is empty,
         * and nothing otherwise, so a typed `file://` page is never swapped for home.
         */
        fun externalTarget(addressBarText: String, home: Option<Url>): Option<Url> =
            if (addressBarText.isBlank()) home else parse(addressBarText).flatMap { Url.web(it.target.value) }

        private fun looksLikeUrl(s: String): Boolean {
            if (s.any { it.isWhitespace() }) return false
            if ('/' in s || '\\' in s) return true
            return runCatching {
                val uri = URI("http://$s")
                val host = uri.host
                host != null &&
                    (host.equals("localhost", ignoreCase = true) || "." in host || uri.port != -1)
            }.getOrDefault(false)
        }
    }
}
