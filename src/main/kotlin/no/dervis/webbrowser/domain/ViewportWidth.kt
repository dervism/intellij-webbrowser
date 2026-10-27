package no.dervis.webbrowser.domain

import arrow.core.Option
import arrow.core.raise.option

/**
 * A custom preview width in CSS pixels, always between [MIN] and [MAX]. Every
 * width that reaches the layout goes through here, so a stored value can never
 * make the browser component zero-sized, negative or huge.
 *
 * [parse] is for what the user types and refuses anything out of range; [of] is
 * for stored values and clamps them, so a hand-edited setting is still safe.
 */
@JvmInline
value class ViewportWidth private constructor(val pixels: Int) {

    companion object {
        const val MIN = 240
        const val MAX = 3840
        private const val MAX_DIGITS = 4

        val DEFAULT = ViewportWidth(1024)

        /** A stored width, clamped into range. */
        fun of(pixels: Int): ViewportWidth = ViewportWidth(pixels.coerceIn(MIN, MAX))

        /** A typed width: ASCII digits only, in range, or nothing. */
        fun parse(text: String): Option<ViewportWidth> = option {
            val digits = text.trim()
            ensure(digits.length in 1..MAX_DIGITS && digits.all { it in '0'..'9' })
            val pixels = digits.toInt()
            ensure(pixels in MIN..MAX)
            ViewportWidth(pixels)
        }
    }
}
