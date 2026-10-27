package no.dervis.webbrowser.domain

/**
 * The width presets for the page preview. [DESKTOP] uses the whole panel, [MOBILE]
 * and [TABLET] have built-in CSS widths, and [CUSTOM] takes its width from a
 * [ViewportWidth].
 *
 * [storageId] is the stable key persisted in settings (decoupled from the enum
 * name so renames are safe).
 */
enum class ViewportPreset(val storageId: String) {
    DESKTOP("DESKTOP"),
    MOBILE("MOBILE"),
    TABLET("TABLET"),
    CUSTOM("CUSTOM"),
    ;

    /** The preview width this preset gives, with [custom] used by [CUSTOM]. */
    fun width(custom: ViewportWidth): PreviewWidth = when (this) {
        DESKTOP -> PreviewWidth.Full
        MOBILE -> PreviewWidth.Fixed(MOBILE_WIDTH)
        TABLET -> PreviewWidth.Fixed(TABLET_WIDTH)
        CUSTOM -> PreviewWidth.Fixed(custom.pixels)
    }

    companion object {
        const val MOBILE_WIDTH = 375
        const val TABLET_WIDTH = 768

        val DEFAULT: ViewportPreset = DESKTOP

        fun fromStorageId(id: String): ViewportPreset = entries.firstOrNull { it.storageId == id } ?: DEFAULT
    }
}
