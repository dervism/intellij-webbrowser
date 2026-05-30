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

        /** Classify a raw address-bar entry. Returns [None] when the input is blank. */
        fun parse(raw: String): Option<AddressBarRequest> {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return none()
            return if (looksLikeUrl(trimmed)) {
                Url.parse(trimmed).map(::Navigate)
            } else {
                val encoded = URLEncoder.encode(trimmed, StandardCharsets.UTF_8)
                Url.parse("$SEARCH_PREFIX$encoded").map { Search(trimmed, it) }
            }
        }

        private fun looksLikeUrl(s: String): Boolean {
            if ("://" in s) return true
            if (s.any { it.isWhitespace() }) return false
            if ('/' in s) return true
            return runCatching {
                val uri = URI("http://$s")
                val host = uri.host
                host != null &&
                    (host.equals("localhost", ignoreCase = true) || "." in host || uri.port != -1)
            }.getOrDefault(false)
        }
    }
}
