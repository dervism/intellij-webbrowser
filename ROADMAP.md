# Roadmap

Candidate features for improving the Web Browser Panel plugin, in rough
priority order.

## 1. Auto-detect the dev-server URL from the run console
When *open browser on run* fires, watch the run configuration's console output
and open the URL the dev server prints (Vite, Astro, Next, …). The browser then
follows whatever port the server picked, so you no longer hardcode the
open-on-run URL per project.

## 2. Responsive viewport and device presets
Add a width dropdown (Mobile, Tablet, Desktop, custom) that limits the preview
to a device width and centers it in the panel. You can then check responsive
layouts next to your code without resizing the tool window or leaving the IDE.

## 3. Live-reload polish: keep scroll position and hot-swap CSS
On reload, restore the previous scroll position. On a CSS-only change,
re-inject the stylesheet instead of doing a full page reload, so scroll, form
and SPA-route state survive while you work on styles.

## 4. Color-scheme toggle (light, dark, auto)
Add a toolbar toggle that forces the page's `prefers-color-scheme`, so you can
flip the preview between light and dark. It builds on the plugin's existing
color-scheme handling, which already forces `color-scheme: light` on pages that
don't declare one.

## 5. Surface runtime JS errors to the IDE
Promote `error`-level console messages (uncaught exceptions) to the IDE's
Notifications tool window, so you see runtime breakage without watching the
console panel.

## 6. Quick-links bar for per-project dev-server shortcuts
Add one-click chips for a project's common URLs (app, Storybook, Vite, API
docs). Auto-detection (item 1) could fill in the links as servers start.
