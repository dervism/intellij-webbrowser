package no.dervis.webbrowser.domain

import kotlin.math.floor

/**
 * How wide the page preview is: the whole panel, or a fixed width in CSS pixels
 * at 100% page zoom.
 */
sealed interface PreviewWidth {

    /**
     * How wide the browser component should be, in Swing pixels, inside a panel
     * [available] pixels wide. Never negative and never wider than the panel.
     */
    fun browserWidth(available: Int, ideScale: Double): Int

    /** The whole panel. */
    data object Full : PreviewWidth {
        override fun browserWidth(available: Int, ideScale: Double): Int = available.coerceAtLeast(0)
    }

    /**
     * A fixed width of [css] CSS pixels. JCEF shows the page
     * `ceil(componentWidth / scale)` CSS pixels wide, where the scale is the IDE's UI
     * scale; `floor(css * ideScale)` makes that exactly [css].
     *
     * This assumes JCEF's default setup, off-screen rendering with the Java runtime
     * handling HiDPI. With IDE-managed HiDPI (mostly Linux) or windowed JCEF, JCEF
     * uses a different scale and the page can come out narrower or wider. The calls
     * that would tell us which setup is active are internal IntelliJ API.
     */
    data class Fixed(val css: Int) : PreviewWidth {
        override fun browserWidth(available: Int, ideScale: Double): Int {
            val scale = ideScale.takeIf { it.isFinite() && it > 0 } ?: 1.0
            return minOf(floor(css * scale).toInt(), available.coerceAtLeast(0))
        }
    }
}
