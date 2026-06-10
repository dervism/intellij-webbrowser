package no.dervis.webbrowser.domain

/**
 * Per-host zoom-level memory: when the user adjusts zoom on, say, github.com,
 * any subsequent navigation to that host restores the same level. Hosts not
 * present in the map fall back to [ZoomLevel.DEFAULT].
 *
 * Pure map operations — the IDE service layer owns the actual mutable storage
 * and delegates remember/forget/lookup to these functions so the rules stay
 * unit-testable.
 */
object PerHostZoom {

    /** Read the stored level for [host]; returns [ZoomLevel.DEFAULT] when absent or [host] is null/blank. */
    fun lookup(map: Map<String, Double>, host: String?): Double =
        host?.takeIf { it.isNotBlank() }?.let { map[normalize(it)] } ?: ZoomLevel.DEFAULT

    /**
     * Return a new map with [host] mapped to [level]. The default level is
     * stored implicitly by *removing* the host — keeps the persisted state
     * minimal and means "no entry" and "default" are the same thing.
     * No-op (returns the same instance) when [host] is null/blank.
     */
    fun remember(map: Map<String, Double>, host: String?, level: Double): Map<String, Double> {
        val key = host?.takeIf { it.isNotBlank() }?.let(::normalize) ?: return map
        return if (level == ZoomLevel.DEFAULT) map - key else map + (key to level)
    }

    /** Lowercase + strip leading `www.` so `WWW.Foo.com` and `foo.com` share a slot. */
    private fun normalize(host: String): String =
        host.lowercase().removePrefix("www.")
}
