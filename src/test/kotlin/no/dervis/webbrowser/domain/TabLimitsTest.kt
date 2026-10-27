package no.dervis.webbrowser.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TabLimitsTest {

    @Test
    fun `popups are allowed below the ceiling`() {
        assertTrue(TabLimits.allowsPopup(49))
    }

    @Test
    fun `popups are refused at and above the ceiling`() {
        assertFalse(TabLimits.allowsPopup(50))
        assertFalse(TabLimits.allowsPopup(51))
    }
}
