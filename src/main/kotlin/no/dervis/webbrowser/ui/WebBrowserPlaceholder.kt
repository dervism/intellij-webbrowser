package no.dervis.webbrowser.ui

import com.intellij.util.ui.UIUtil
import java.awt.Color

/**
 * Builds the themed "home" page shown in the browser before a page is loaded and
 * whenever a load fails. Rendered as HTML with an inline SVG so it fully controls
 * the look and adapts to the current IDE theme.
 *
 * Deliberately context-free: no URL, no error text. The same friendly view is
 * shown for the initial empty state and for failed loads — a load failure isn't
 * worth surfacing a cryptic browser error, and any assumption about the kind of
 * page the user might be loading (a dev server, a specific port, …) would be
 * presumptuous.
 */
object WebBrowserPlaceholder {

    fun html(): String {
        val bg = hex(UIUtil.getPanelBackground())
        val fg = hex(UIUtil.getLabelForeground())
        val muted = hex(UIUtil.getContextHelpForeground())
        val stroke = hex(blend(UIUtil.getLabelForeground(), UIUtil.getPanelBackground(), 0.55))
        val accent = "#3574F0"

        return """
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8"/>
<title>New Tab</title>
<style>
  html, body { height: 100%; margin: 0; }
  body {
    display: flex; align-items: center; justify-content: center;
    background: $bg; color: $fg;
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
    -webkit-font-smoothing: antialiased;
  }
  .wrap { text-align: center; padding: 24px; max-width: 460px; }
  .art { margin-bottom: 22px; }
  h1 { font-size: 18px; font-weight: 600; margin: 0; }
  .sub { font-size: 13px; line-height: 1.5; color: $muted; margin: 10px 0 0; }
  /* A very gentle breathing pulse on the globe makes the page feel alive
     without being a distraction. */
  .globe { animation: pulse 4s ease-in-out infinite; transform-origin: center; }
  @keyframes pulse {
    0%, 100% { opacity: 0.75; }
    50%      { opacity: 1.00; }
  }
</style>
</head>
<body>
  <div class="wrap">
    <div class="art">${svg(accent, stroke)}</div>
    <h1>Web Browser Panel</h1>
    <p class="sub">Browse the web inside your IDE.</p>
  </div>
</body>
</html>
        """.trimIndent()
    }

    private fun svg(accent: String, stroke: String): String = """
<svg width="132" height="132" viewBox="0 0 132 132" fill="none" xmlns="http://www.w3.org/2000/svg">
  <rect x="16" y="26" width="100" height="80" rx="9" fill="none" stroke="$stroke" stroke-width="2.5"/>
  <line x1="16" y1="44" x2="116" y2="44" stroke="$stroke" stroke-width="2.5"/>
  <circle cx="28" cy="35" r="2.8" fill="#FF5F57"/>
  <circle cx="38" cy="35" r="2.8" fill="#FEBC2E"/>
  <circle cx="48" cy="35" r="2.8" fill="#28C840"/>
  <g class="globe" stroke="$accent" stroke-width="2.8" fill="none">
    <circle cx="66" cy="75" r="20"/>
    <ellipse cx="66" cy="75" rx="8.2" ry="20"/>
    <line x1="46" y1="75" x2="86" y2="75"/>
    <line x1="66" y1="55" x2="66" y2="95" opacity="0.5"/>
  </g>
</svg>
    """.trimIndent()

    private fun hex(c: Color): String = "#%02x%02x%02x".format(c.red, c.green, c.blue)

    /** Mix [a] toward [b] by [ratio] (0 = all a, 1 = all b). */
    private fun blend(a: Color, b: Color, ratio: Double): Color {
        val r = (a.red * (1 - ratio) + b.red * ratio).toInt()
        val g = (a.green * (1 - ratio) + b.green * ratio).toInt()
        val bl = (a.blue * (1 - ratio) + b.blue * ratio).toInt()
        return Color(r.coerceIn(0, 255), g.coerceIn(0, 255), bl.coerceIn(0, 255))
    }
}
