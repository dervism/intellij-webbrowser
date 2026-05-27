package no.dervis.webbrowser

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import no.dervis.webbrowser.domain.Url

/**
 * Bridges external triggers (e.g. the run-configuration listener) to the browser
 * tool window: activates it and navigates the view, creating the view first if the
 * tool window hasn't been opened yet. All methods must be called on the EDT.
 */
@Service(Service.Level.PROJECT)
class WebBrowserController(private val project: Project) {

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

    /** Activate the tool window and navigate it to [url]. */
    fun open(url: Url) {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID) ?: return
        val current = view
        if (current != null) {
            toolWindow.activate { current.load(url) }
        } else {
            // Tool window not opened yet: queue the URL, then activating it creates
            // the view, which picks the URL up via register().
            pending = url
            toolWindow.activate(null)
        }
    }

    companion object {
        const val TOOL_WINDOW_ID = "Web Browser"

        fun getInstance(project: Project): WebBrowserController = project.service()
    }
}
