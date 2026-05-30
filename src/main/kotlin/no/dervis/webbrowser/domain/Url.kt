package no.dervis.webbrowser.domain

import arrow.core.Option
import arrow.core.none
import arrow.core.some
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * A normalized web address. Construction guarantees a scheme is present, so the
 * rest of the application never has to re-validate or re-parse raw user input.
 */
@JvmInline
value class Url private constructor(val value: String) {

    val host: String?
        get() = runCatching { URI(value).host }.getOrNull()

    /** The explicit port, or the scheme's default when none is given. */
    val port: Int
        get() = runCatching {
            val uri = URI(value)
            when {
                uri.port != -1 -> uri.port
                value.startsWith("https") -> HTTPS_PORT
                else -> HTTP_PORT
            }
        }.getOrDefault(HTTP_PORT)

    override fun toString(): String = value

    companion object {
        private const val HTTP_PORT = 80
        private const val HTTPS_PORT = 443
        private const val SEARCH_PREFIX = "https://www.startpage.com/do/search?q="

        /** Normalize raw input, defaulting the scheme to `http`. [None] when blank. */
        fun parse(raw: String): Option<Url> {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return none()
            return Url(if ("://" in trimmed) trimmed else "http://$trimmed").some()
        }

        /**
         * Interpret an address-bar input: when it looks like a URL (explicit
         * scheme, a path, a host with a dot, localhost, IP, host:port, …) it's
         * parsed as a URL. Otherwise it's routed to a Startpage search for the
         * verbatim text. [None] when blank.
         */
        fun fromAddressBar(raw: String): Option<Url> {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return none()
            return if (looksLikeUrl(trimmed)) {
                parse(trimmed)
            } else {
                val q = URLEncoder.encode(trimmed, StandardCharsets.UTF_8)
                Url("$SEARCH_PREFIX$q").some()
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
