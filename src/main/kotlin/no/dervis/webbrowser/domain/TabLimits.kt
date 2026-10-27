package no.dervis.webbrowser.domain

/**
 * How many tabs input the user didn't choose may create. Every tab is a full
 * embedded Chromium browser, and restore creates all of them at once. The numbers
 * are judgement calls, set high enough that normal use shouldn't reach them.
 * Tabs the user opens themselves (new tab, reopen closed tab) are not limited.
 */
object TabLimits {

    /** A page's popup is ignored while this many tabs or more are open. */
    const val POPUP_CEILING = 50

    /** Tabs taken over once from the shared project file, which anyone with commit access can edit. */
    const val MAX_MIGRATED_TABS = 20

    /** Tabs restored from the per-user workspace file; guards against a corrupted or hand-edited file. */
    const val MAX_RESTORED_TABS = 100

    fun allowsPopup(openTabs: Int): Boolean = openTabs < POPUP_CEILING
}
