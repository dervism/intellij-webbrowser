package no.dervis.webbrowser.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class ReloadPlanTest {

    private val devServer = "http://localhost:5173/app/"

    // ---- saves -------------------------------------------------------------

    @Test
    fun `css-only saves on a local dev server swap the stylesheets`() {
        assertEquals(ReloadPlan.HotSwapCss, ReloadPlan.forSaves(listOf("/p/src/a.css", "/p/src/b.CSS"), devServer))
        assertEquals(ReloadPlan.HotSwapCss, ReloadPlan.forSaves(listOf("/p/a.css"), "http://127.0.0.1:8000/"))
        assertEquals(ReloadPlan.HotSwapCss, ReloadPlan.forSaves(listOf("/p/a.css"), "http://[::1]:3000/"))
    }

    @Test
    fun `app state in the query doesn't stop the swap`() {
        listOf("http://localhost:5173/?filter={%22a%22:1}", "http://localhost:5173/#/a|b", "http://localhost:5173/?x=%zz")
            .forEach { assertEquals(ReloadPlan.HotSwapCss, ReloadPlan.forSaves(listOf("/p/a.css"), it), "page $it") }
    }

    @Test
    fun `any non-css file in the same window means a full reload`() {
        assertEquals(ReloadPlan.KeepScroll, ReloadPlan.forSaves(listOf("/p/a.css", "/p/app.js"), devServer))
    }

    @Test
    fun `scss and less saves reload, the dev server compiles them`() {
        assertEquals(ReloadPlan.KeepScroll, ReloadPlan.forSaves(listOf("/p/a.scss"), devServer))
        assertEquals(ReloadPlan.KeepScroll, ReloadPlan.forSaves(listOf("/p/a.less"), devServer))
    }

    @Test
    fun `css saves never swap stylesheets on a page that isn't local`() {
        assertEquals(ReloadPlan.KeepScroll, ReloadPlan.forSaves(listOf("/p/a.css"), "https://example.com/"))
        assertEquals(ReloadPlan.KeepScroll, ReloadPlan.forSaves(listOf("/p/a.css"), "http://localhost.evil.com/"))
    }

    @Test
    fun `no saved paths means a full reload`() {
        assertEquals(ReloadPlan.KeepScroll, ReloadPlan.forSaves(emptyList(), devServer))
    }

    @Test
    fun `pages that aren't http or https get a plain reload`() {
        listOf("data:text/html,x", "view-source:http://localhost:5173/", "about:blank", "file:///tmp/a.html", "")
            .forEach { assertEquals(ReloadPlan.Plain, ReloadPlan.forSaves(listOf("/p/a.css"), it), "page $it") }
    }

    // ---- reload button and auto-refresh -------------------------------------

    @Test
    fun `a reload keeps the scroll position on http and https pages`() {
        assertEquals(ReloadPlan.KeepScroll, ReloadPlan.forReload(devServer))
        assertEquals(ReloadPlan.KeepScroll, ReloadPlan.forReload("https://example.com/long-page"))
    }

    @Test
    fun `a reload of any other page is plain`() {
        assertEquals(ReloadPlan.Plain, ReloadPlan.forReload("data:text/html,x"))
        assertEquals(ReloadPlan.Plain, ReloadPlan.forReload("view-source:http://localhost:5173/"))
    }
}
