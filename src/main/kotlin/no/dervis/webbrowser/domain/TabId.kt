package no.dervis.webbrowser.domain

/**
 * Opaque identifier for an open browser tab. The actual JCEF browser handle
 * lives at the UI edge keyed by this value; the domain only ever references
 * tabs by their id.
 */
@JvmInline
value class TabId(val value: Long) {
    override fun toString(): String = "tab:$value"
}
