package no.dervis.webbrowser.domain

import arrow.core.Option
import arrow.core.none
import arrow.core.some
import java.net.URI

/**
 * A normalized web address. Construction guarantees a scheme is present, so the
 * rest of the application never has to re-validate or re-parse raw user input.
 *
 * Url models a URL value; classifying an address-bar entry into navigation
 * vs search is the job of [AddressBarRequest], which delegates back here for
 * the URL construction.
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

        /** Normalize raw input, defaulting the scheme to `http`. [None] when blank. */
        fun parse(raw: String): Option<Url> {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return none()
            return Url(if ("://" in trimmed) trimmed else "http://$trimmed").some()
        }
    }
}
