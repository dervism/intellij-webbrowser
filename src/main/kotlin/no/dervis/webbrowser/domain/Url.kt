package no.dervis.webbrowser.domain

import arrow.core.Option
import arrow.core.raise.option
import java.net.URI

/**
 * A normalized web address with a scheme the plugin is allowed to load.
 *
 * There is no general-purpose parser. A caller picks [web] (`http` and `https`)
 * or [typed] (also `file`, for what the user types in the address bar), so every
 * place that loads a URL or hands one to the OS has to decide which schemes it
 * accepts. A `Url` therefore only ever holds an `http`, `https` or `file` URL.
 *
 * Url models a URL value; classifying an address-bar entry into navigation
 * vs search is the job of [AddressBarRequest], which delegates back here for
 * the URL construction.
 */
@JvmInline
value class Url private constructor(val value: String) {

    /** `http`, `https` or `file`, always lowercase. */
    val scheme: String
        get() = value.substringBefore("://")

    val host: String?
        get() = runCatching { URI(value).host }.getOrNull()

    /** The explicit port, or the scheme's default when none is given. */
    val port: Int
        get() = explicitPort() ?: if (scheme == "https") HTTPS_PORT else HTTP_PORT

    /** `scheme://host[:port]` without path, query or fragment, for log lines. */
    val redacted: String
        get() = "$scheme://${host.orEmpty()}${explicitPort()?.takeIf { host != null }?.let { ":$it" }.orEmpty()}"

    override fun toString(): String = value

    private fun explicitPort(): Int? = runCatching { URI(value).port }.getOrNull()?.takeIf { it != -1 }

    companion object {
        const val MAX_LENGTH = 8192
        private const val HTTP_PORT = 80
        private const val HTTPS_PORT = 443
        private val WEB_SCHEMES = setOf("http", "https")
        private val TYPED_SCHEMES = WEB_SCHEMES + "file"
        private val EXPLICIT_SCHEME = Regex("^([A-Za-z][A-Za-z0-9+.-]*)://")
        private val SCHEME_PREFIX = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")

        /** `http` and `https` only. For every URL the user didn't type in themselves. */
        fun web(raw: String): Option<Url> = from(raw, WEB_SCHEMES, MAX_LENGTH)

        /** What the user typed or picked in the editor: `http`, `https`, and also `file`. */
        fun typed(raw: String): Option<Url> = from(raw, TYPED_SCHEMES, MAX_LENGTH)

        /**
         * A URL Chromium reports for a page it has loaded: `http` and `https`, with no
         * length cap, because Chromium already accepted it and long app URLs are normal.
         * Anything stored or reloaded later goes through [web] instead.
         */
        fun live(raw: String): Option<Url> = from(raw, WEB_SCHEMES, Int.MAX_VALUE)

        /** Whether [raw] starts with `scheme://`, whatever the scheme. */
        internal fun hasExplicitScheme(raw: String): Boolean = EXPLICIT_SCHEME.containsMatchIn(raw)

        /** Whether [raw] names a scheme other than `http` or `https`, with or without `//`. */
        internal fun hasNonWebScheme(raw: String): Boolean =
            EXPLICIT_SCHEME.find(raw)?.let { it.groupValues[1].lowercase() !in WEB_SCHEMES } ?: hasSchemeWithoutSlashes(raw)

        /**
         * The scheme is read from the start of the input only, so `localhost:3000/?r=http://x`
         * is a host with a query, not an `http` URL hidden in the middle. Input without a
         * scheme is a host (with optional port and path) and gets `http://`; input that
         * starts with a slash or backslash is a local path and is rejected.
         */
        private fun from(raw: String, allowed: Set<String>, maxLength: Int): Option<Url> = option {
            val input = raw.trim()
            ensure(input.isNotEmpty() && input.length <= maxLength)
            ensure(input.none { it.isISOControl() || it.isWhitespace() })
            val explicit = EXPLICIT_SCHEME.find(input)
            if (explicit != null) {
                val scheme = explicit.groupValues[1].lowercase()
                val rest = input.substring(explicit.value.length)
                ensure(scheme in allowed && rest.isNotEmpty())
                ensure(scheme == "file" || !rest.startsWith('/'))
                Url("$scheme://$rest")
            } else {
                // A bare host needs a host: `/Users/me/x.html` or `//evil/x` is a path, not a URL.
                ensure(!input.startsWith('/') && !input.startsWith('\\') && !hasSchemeWithoutSlashes(input))
                Url("http://$input")
            }
        }

        /**
         * `word:` followed by anything but a digit is a scheme without `//`, such as
         * `javascript:`, `about:blank` or `mailto:`. A digit after the colon is a port.
         */
        private fun hasSchemeWithoutSlashes(input: String): Boolean =
            SCHEME_PREFIX.find(input)?.let { input.getOrNull(it.value.length)?.isDigit() != true } ?: false
    }
}
