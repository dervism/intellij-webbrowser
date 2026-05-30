package no.dervis.webbrowser.ui

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Platform-framework tests for the themed SVG home page. Uses BasePlatformTestCase
 * because the placeholder reads theme colours via `UIUtil`, which needs the
 * IntelliJ Application to be initialised.
 */
class WebBrowserPlaceholderTest : BasePlatformTestCase() {

    fun testHtmlIsAValidDocument() {
        val html = WebBrowserPlaceholder.html()
        assertTrue("expected DOCTYPE declaration", html.startsWith("<!DOCTYPE html>"))
        assertTrue("expected closing <html> tag", html.contains("</html>"))
    }

    fun testHtmlCarriesTheBrandTitleAndSubtitle() {
        val html = WebBrowserPlaceholder.html()
        assertTrue("expected the plugin title", html.contains("<h1>Web Browser Panel</h1>"))
        assertTrue(
            "expected the subtitle",
            html.contains("Browse the web inside your IDE."),
        )
    }

    fun testHtmlEmbedsTheGlobeSvgIllustration() {
        val html = WebBrowserPlaceholder.html()
        assertTrue("expected an SVG root element", html.contains("<svg"))
        assertTrue("expected the globe group", html.contains("class=\"globe\""))
    }

    fun testHtmlIncludesTheBreathingAnimation() {
        val html = WebBrowserPlaceholder.html()
        assertTrue("expected pulse @keyframes for the globe", html.contains("@keyframes pulse"))
    }

    fun testHtmlEmbedsThemeDerivedColours() {
        val html = WebBrowserPlaceholder.html()
        // The CSS variables interpolated from UIUtil colours come out as 6-digit
        // hex literals like `background: #2b2d30`. Verify at least one such
        // literal appears in the rendered CSS (proves the theme path ran).
        val hexLiteralInCss = Regex("background:\\s*#[0-9a-f]{6}")
        assertTrue(
            "expected a theme-derived hex colour in CSS, got:\n${html.take(800)}",
            hexLiteralInCss.containsMatchIn(html),
        )
    }

    fun testHtmlIsIdempotentBetweenCalls() {
        // Two consecutive calls under the same theme should produce identical HTML.
        assertEquals(WebBrowserPlaceholder.html(), WebBrowserPlaceholder.html())
    }
}
