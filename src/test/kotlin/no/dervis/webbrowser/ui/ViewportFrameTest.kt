package no.dervis.webbrowser.ui

import java.awt.Rectangle
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals

/** Plain JUnit tests for the frame that limits and centres a tab's browser. */
class ViewportFrameTest {

    private val browser = JPanel()

    private fun laidOut(frameWidth: (Int) -> Int): Rectangle {
        val frame = ViewportFrame(browser, frameWidth)
        frame.setSize(1000, 600)
        frame.doLayout()
        return browser.bounds
    }

    @Test
    fun `centres the browser at the frame width`() {
        assertEquals(Rectangle(312, 0, 375, 600), laidOut { 375 })
    }

    @Test
    fun `gives the browser the whole frame when the width is the available width`() {
        assertEquals(Rectangle(0, 0, 1000, 600), laidOut { it })
    }

    @Test
    fun `never makes the browser wider than the frame`() {
        assertEquals(Rectangle(0, 0, 1000, 600), laidOut { 5000 })
    }

    @Test
    fun `never gives the browser a negative width`() {
        assertEquals(0, laidOut { -20 }.width)
    }

    @Test
    fun `asks for the width again on every layout`() {
        var width = 375
        val frame = ViewportFrame(browser) { width }
        frame.setSize(1000, 600)
        frame.doLayout()
        width = 768
        frame.doLayout()
        assertEquals(Rectangle(116, 0, 768, 600), browser.bounds)
    }
}
