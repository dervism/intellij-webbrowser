package no.dervis.webbrowser.domain

import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertEquals

class PreviewWidthTest {

    @Test
    fun `full width is the whole panel`() {
        assertEquals(1200, PreviewWidth.Full.browserWidth(available = 1200, ideScale = 1.25))
    }

    @Test
    fun `a fixed width is used as is at IDE scale 1`() {
        assertEquals(375, PreviewWidth.Fixed(375).browserWidth(available = 1200, ideScale = 1.0))
    }

    @Test
    fun `a fixed width wider than the panel gets the panel width`() {
        assertEquals(600, PreviewWidth.Fixed(768).browserWidth(available = 600, ideScale = 1.0))
    }

    @Test
    fun `the IDE scale is compensated`() {
        assertEquals(468, PreviewWidth.Fixed(375).browserWidth(available = 4000, ideScale = 1.25))
        assertEquals(562, PreviewWidth.Fixed(375).browserWidth(available = 4000, ideScale = 1.5))
        assertEquals(750, PreviewWidth.Fixed(375).browserWidth(available = 4000, ideScale = 2.0))
    }

    @Test
    fun `the page sees exactly the fixed width at every common IDE scale`() {
        // JCEF shows the page ceil(componentWidth / scale) CSS pixels; the scale comes
        // from a Float setting, widened to Double the same way JCEF widens it.
        listOf(1.0f, 1.1f, 1.25f, 1.5f, 1.75f, 2.0f).forEach { floatScale ->
            val scale = floatScale.toDouble()
            (ViewportWidth.MIN..ViewportWidth.MAX).forEach { css ->
                val component = PreviewWidth.Fixed(css).browserWidth(available = 100_000, ideScale = scale)
                assertEquals(css, ceil(component / scale).toInt(), "css=$css scale=$floatScale")
            }
        }
    }

    @Test
    fun `a broken IDE scale counts as 1`() {
        listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach {
            assertEquals(375, PreviewWidth.Fixed(375).browserWidth(available = 2000, ideScale = it), "scale $it")
        }
    }

    @Test
    fun `no width is ever negative`() {
        assertEquals(0, PreviewWidth.Fixed(375).browserWidth(available = -10, ideScale = 1.0))
        assertEquals(0, PreviewWidth.Full.browserWidth(available = -10, ideScale = 1.0))
    }
}
