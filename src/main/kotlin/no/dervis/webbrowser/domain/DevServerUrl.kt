package no.dervis.webbrowser.domain

import arrow.core.Option
import arrow.core.raise.option

/**
 * A dev-server address read from run-console output that is safe to open without
 * asking: `http` or `https` on this machine's loopback interface.
 *
 * Console text can contain lines an attacker controls (request logs, echoed remote
 * data), so [parse] never hands the raw text on. It checks each part by hand,
 * without `java.net.URI` or DNS, and rebuilds the URL from the checked parts.
 * The query and fragment are dropped so tokens such as Jupyter's `?token=` are
 * never opened, persisted or logged.
 */
@JvmInline
value class DevServerUrl private constructor(val url: Url) {

    /** `host:port` (or just `host`), safe to write to the log. */
    val authority: String
        get() = url.value.substringAfter("://").substringBefore('/')

    companion object {
        private val SCHEMES = listOf("http", "https")
        private const val MAX_PATH_LENGTH = 256
        private const val MAX_PORT = 65_535
        private val PATH = Regex("(?:[A-Za-z0-9\\-._~/]|%[0-9A-Fa-f]{2})*")
        private val IPV4_OCTET = Regex("0|[1-9][0-9]{0,2}")

        /**
         * Accepts [candidate] only if every part passes, otherwise [arrow.core.None].
         * A port in [excludedPorts] is rejected, including the scheme's default
         * port when the candidate has no explicit one.
         */
        fun parse(candidate: String, excludedPorts: Set<Int>): Option<DevServerUrl> = option {
            ensure(candidate.all { it in '!'..'~' })
            val scheme = ensureNotNull(SCHEMES.firstOrNull { candidate.startsWith("$it://", ignoreCase = true) })
            val beforeQuery = candidate.substring(scheme.length + 3).takeWhile { it != '?' && it != '#' }
            val authority = beforeQuery.substringBefore('/')
            val path = beforeQuery.removePrefix(authority).ifEmpty { "/" }
            val (rawHost, rawPort) = ensureNotNull(splitAuthority(authority))
            val host = ensureNotNull(loopbackHost(rawHost))
            val port = rawPort?.let { ensureNotNull(parsePort(it)) }
            ensure((port ?: defaultPort(scheme)) !in excludedPorts)
            ensure(path.length <= MAX_PATH_LENGTH && PATH.matches(path))
            val rebuilt = "$scheme://$host${port?.let { ":$it" }.orEmpty()}$path"
            DevServerUrl(Url.parse(rebuilt).bind())
        }

        /**
         * Host and optional port text, or null when a bracketed IPv6 host is
         * malformed. Userinfo and extra colons need no rule of their own:
         * `user@localhost` is not a loopback host, and neither `1@evil` nor
         * `1:2` is a port.
         */
        private fun splitAuthority(authority: String): Pair<String, String?>? = when {
            authority.startsWith('[') -> {
                val host = authority.substringBefore(']', missingDelimiterValue = "") + "]"
                val rest = authority.removePrefix(host)
                when {
                    host == "]" -> null
                    rest.isEmpty() -> host to null
                    rest.startsWith(':') -> host to rest.drop(1)
                    else -> null
                }
            }
            ':' in authority -> authority.substringBefore(':') to authority.substringAfter(':')
            else -> authority to null
        }

        /** The canonical loopback host, or null for anything that isn't a literal loopback address. */
        private fun loopbackHost(host: String): String? = when (host.lowercase()) {
            "localhost", "0.0.0.0", "[::]" -> "localhost"
            "[::1]" -> "[::1]"
            else -> host.takeIf(::isLoopbackIpv4)
        }

        /** Dotted-decimal `127.x.x.x` with four octets, each 0–255 without leading zeros. */
        private fun isLoopbackIpv4(host: String): Boolean {
            val octets = host.split('.')
            return octets.size == 4 &&
                octets[0] == "127" &&
                octets.all { IPV4_OCTET.matches(it) && it.toInt() <= 255 }
        }

        /** 1–65535 written without leading zeros, or null. */
        private fun parsePort(text: String): Int? =
            text.takeIf { it.length in 1..5 && it.all(Char::isDigit) && !it.startsWith('0') }
                ?.toInt()
                ?.takeIf { it <= MAX_PORT }

        private fun defaultPort(scheme: String): Int = if (scheme == "https") 443 else 80
    }
}
