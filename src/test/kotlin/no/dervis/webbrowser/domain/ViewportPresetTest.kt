package no.dervis.webbrowser.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class ViewportPresetTest {

    private val custom = ViewportWidth.of(1000)

    @Test
    fun `desktop is the full panel width`() {
        assertEquals(PreviewWidth.Full, ViewportPreset.DESKTOP.width(custom))
    }

    @Test
    fun `mobile and tablet have fixed CSS widths`() {
        assertEquals(PreviewWidth.Fixed(375), ViewportPreset.MOBILE.width(custom))
        assertEquals(PreviewWidth.Fixed(768), ViewportPreset.TABLET.width(custom))
    }

    @Test
    fun `custom uses the custom width`() {
        assertEquals(PreviewWidth.Fixed(1000), ViewportPreset.CUSTOM.width(custom))
    }

    @Test
    fun `fromStorageId round-trips and falls back to desktop`() {
        ViewportPreset.entries.forEach { assertEquals(it, ViewportPreset.fromStorageId(it.storageId)) }
        assertEquals(ViewportPreset.DESKTOP, ViewportPreset.fromStorageId("BOGUS"))
        assertEquals(ViewportPreset.DESKTOP, ViewportPreset.fromStorageId(""))
    }
}
