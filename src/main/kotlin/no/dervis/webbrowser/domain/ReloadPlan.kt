package no.dervis.webbrowser.domain

/**
 * How to refresh the page after a reload is asked for.
 *
 * - [HotSwapCss]: re-request the page's own stylesheets, keeping the page as it is.
 * - [KeepScroll]: reload, then bring the page back to where it was scrolled.
 * - [Plain]: an ordinary reload, for pages that aren't http or https (the
 *   placeholder, view-source), where no script is run.
 */
sealed interface ReloadPlan {
    data object HotSwapCss : ReloadPlan
    data object KeepScroll : ReloadPlan
    data object Plain : ReloadPlan

    companion object {
        /**
         * The plan for the files saved within one debounce window, for the page at
         * [pageUrl]. Stylesheets are swapped only when every saved file is `.css` and
         * the page is on this machine; any other file in the window means a reload.
         */
        fun forSaves(savedPaths: Collection<String>, pageUrl: String): ReloadPlan {
            val page = Url.live(pageUrl).getOrNull() ?: return Plain
            val cssOnly = savedPaths.isNotEmpty() && savedPaths.all { it.endsWith(".css", ignoreCase = true) }
            val local = page.host?.let(LoopbackHost::canonical) != null
            return if (cssOnly && local) HotSwapCss else KeepScroll
        }

        /** The plan for the Reload button and auto-refresh. */
        fun forReload(pageUrl: String): ReloadPlan = if (Url.live(pageUrl).isSome()) KeepScroll else Plain
    }
}
