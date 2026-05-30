package no.dervis.webbrowser.domain

/**
 * JCEF zoom level value semantics. Levels are logarithmic (base ≈ 1.2):
 *   - `0.0` → 100%
 *   - `±1.0` ≈ ±20% (the [STEP])
 *   - `±5.0` ≈ 250% / 40% (the practical clamp range)
 *
 * The actual `CefBrowser.zoomLevel` setter is effectful; the pure parts —
 * constants, the clamped `next` calculation, and the in/out helpers — live here
 * so the panel just consults them.
 */
object ZoomLevel {

    const val DEFAULT = 0.0
    const val MIN = -5.0
    const val MAX = 5.0
    const val STEP = 1.0

    /** [current] + [delta], clamped to `[MIN, MAX]`. */
    fun next(current: Double, delta: Double): Double =
        (current + delta).coerceIn(MIN, MAX)

    fun zoomedIn(current: Double): Double = next(current, +STEP)

    fun zoomedOut(current: Double): Double = next(current, -STEP)
}
