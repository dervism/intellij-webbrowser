# Roadmap

Candidate features for improving the Web Browser Panel plugin, in rough
priority order.

## 1. Live-reload polish: keep scroll position and hot-swap CSS
On reload, restore the previous scroll position. On a CSS-only change,
re-inject the stylesheet instead of doing a full page reload, so scroll, form
and SPA-route state survive while you work on styles.

## 2. Color-scheme toggle (light, dark, auto)
Add a toolbar toggle that forces the page's `prefers-color-scheme`, so you can
flip the preview between light and dark. It builds on the plugin's existing
color-scheme handling, which already forces `color-scheme: light` on pages that
don't declare one.

## 3. Surface runtime JS errors to the IDE
Promote `error`-level console messages (uncaught exceptions) to the IDE's
Notifications tool window, so you see runtime breakage without watching the
console panel.

## 4. Quick-links bar for per-project dev-server shortcuts
Add one-click chips for a project's common URLs (app, Storybook, Vite, API
docs). The run-console URL detection added in 0.6.0 could fill in the links as
servers start.

## 5. Lazy tab restore
Restore only the active tab right away, and load each other tab the first time
you select it. A large session would then open quickly and use far less memory,
since every tab is a full embedded Chromium browser.

## 6. Open on run for scripts that run in the terminal
Support Shell Script run configurations that have *Execute in the terminal*
turned on. Those runs are typed into a Terminal tab instead of being started as a
run process, so today the plugin never sees them start or what they print;
supporting them means following the terminal's own output.
