package no.dervis.webbrowser

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import no.dervis.webbrowser.domain.Url

/**
 * Bridges external triggers (e.g. the run-configuration listener) to the browser
 * tool window: shows it and navigates the view, creating the view first if the
 * tool window hasn't been opened yet. All methods must be called on the EDT.
 *
 * Also the parent [Disposable] for per-run work such as the dev-server URL watch,
 * so that work ends when the project closes or the plugin unloads.
 */
@Service(Service.Level.PROJECT)
class WebBrowserController(private val project: Project) : Disposable {

    private var view: BrowserView? = null
    private var pending: Url? = null

    /** Called by the view when it is created. Picks up any URL queued before it existed. */
    fun register(view: BrowserView) {
        this.view = view
        pending?.let {
            pending = null
            view.load(it)
        }
    }

    fun unregister(view: BrowserView) {
        if (this.view === view) this.view = null
    }

    /**
     * Show the tool window and navigate it to [url]. With [focus] the tool window
     * is activated and takes keyboard focus; without it, it is only shown and
     * focus stays where it was.
     */
    fun open(url: Url, focus: Boolean = true) {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID) ?: return
        val reveal: (Runnable?) -> Unit = if (focus) toolWindow::activate else toolWindow::show
        val current = view
        if (current != null) {
            reveal(Runnable { current.load(url) })
        } else {
            // Tool window not opened yet: queue the URL, then showing it creates
            // the view, which picks the URL up via register().
            pending = url
            reveal(null)
        }
    }

    override fun dispose() = Unit

    companion object {
        const val TOOL_WINDOW_ID = "Web Browser"

        fun getInstance(project: Project): WebBrowserController = project.service()
    }
}
