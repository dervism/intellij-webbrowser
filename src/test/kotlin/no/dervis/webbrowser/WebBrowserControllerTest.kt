package no.dervis.webbrowser

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import no.dervis.webbrowser.domain.Url

/**
 * Platform-framework tests for the project-scoped controller. The full open() flow
 * needs a registered "Web Browser" tool window in the fixture; here we cover the
 * directly observable surface (register / unregister / service identity).
 */
class WebBrowserControllerTest : BasePlatformTestCase() {

    private lateinit var controller: WebBrowserController

    override fun setUp() {
        super.setUp()
        controller = WebBrowserController.getInstance(project)
    }

    fun testGetInstanceReturnsTheSameProjectService() {
        assertSame(controller, WebBrowserController.getInstance(project))
    }

    fun testRegisterDoesNotInvokeLoadWhenNoUrlIsPending() {
        val loaded = mutableListOf<Url>()
        controller.register(BrowserView { loaded.add(it) })
        assertTrue("view.load must not be called when no URL is queued", loaded.isEmpty())
    }

    fun testRegisterAndUnregisterSameViewIsANoop() {
        // Smoke test: register/unregister a view and verify no exception is thrown.
        val view = BrowserView { fail("unregistered view must not be invoked") }
        controller.register(view)
        controller.unregister(view)
    }

    fun testUnregisterIgnoresUnknownView() {
        // Smoke test: unregistering a view we never registered must not throw.
        controller.unregister(BrowserView { /* never invoked */ })
    }

    fun testReplacingTheActiveViewDropsTheOldOne() {
        val first = BrowserView { fail("first view should not receive loads after being replaced") }
        controller.register(first)
        val secondReceived = mutableListOf<Url>()
        controller.register(BrowserView { secondReceived.add(it) })
        // unregister the first AFTER it was replaced — should still be safe.
        controller.unregister(first)
        assertTrue(secondReceived.isEmpty())
    }
}
