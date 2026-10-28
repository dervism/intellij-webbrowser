package no.dervis.webbrowser.domain

/**
 * What counts as this machine: `localhost`, dotted-decimal `127.x.x.x` and `[::1]`,
 * written literally. `0.0.0.0` and `[::]` (what servers print when they listen on
 * every interface) mean `localhost`. No DNS lookup, no shorthand forms, nothing
 * that only starts like a loopback name.
 */
object LoopbackHost {

    private val IPV4_OCTET = Regex("0|[1-9][0-9]{0,2}")

    /** The canonical loopback host for [host], or null when it isn't a literal loopback address. */
    fun canonical(host: String): String? = when (host.lowercase()) {
        "localhost", "0.0.0.0", "[::]" -> "localhost"
        "[::1]" -> "[::1]"
        else -> host.takeIf(::isLoopbackIpv4)
    }

    /** Four octets, each 0–255 without leading zeros, starting with 127. */
    private fun isLoopbackIpv4(host: String): Boolean {
        val octets = host.split('.')
        return octets.size == 4 &&
            octets[0] == "127" &&
            octets.all { IPV4_OCTET.matches(it) && it.toInt() <= 255 }
    }
}
