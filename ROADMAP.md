# Roadmap

Candidate features for improving the Web Browser Panel plugin, in rough
priority order.

## 1. Harden URL handling and stored data
The 0.6.0 security review found four issues in older code: restoring the tab
session doesn't re-check the URL scheme, page titles are rendered as Swing HTML,
tab URLs and address-bar history live in the shared `.idea` file, and `Url.parse`
treats `://` anywhere in the input as a scheme. The first two let a shared
project file or a web page affect what the IDE loads or shows; the other two are
privacy and correctness fixes.

## 2. Dev-server URL detection follow-ups
Exclude the JCEF remote-debugging port when it is set to a random port, which
needs an asynchronous lookup through JCEF. Also handle three edge cases from the
0.6.0 code review: a URL at the very end of the output with no line break,
`http://[::1]` with no port or path, and URLs separated by non-breaking spaces.

## 3. Responsive viewport and device presets
Add a width dropdown (Mobile, Tablet, Desktop, custom) that limits the preview
to a device width and centers it in the panel. You can then check responsive
layouts next to your code without resizing the tool window or leaving the IDE.

## 4. Live-reload polish: keep scroll position and hot-swap CSS
On reload, restore the previous scroll position. On a CSS-only change,
re-inject the stylesheet instead of doing a full page reload, so scroll, form
and SPA-route state survive while you work on styles.

## 5. Color-scheme toggle (light, dark, auto)
Add a toolbar toggle that forces the page's `prefers-color-scheme`, so you can
flip the preview between light and dark. It builds on the plugin's existing
color-scheme handling, which already forces `color-scheme: light` on pages that
don't declare one.

## 6. Surface runtime JS errors to the IDE
Promote `error`-level console messages (uncaught exceptions) to the IDE's
Notifications tool window, so you see runtime breakage without watching the
console panel.

## 7. Quick-links bar for per-project dev-server shortcuts
Add one-click chips for a project's common URLs (app, Storybook, Vite, API
docs). The run-console URL detection added in 0.6.0 could fill in the links as
servers start.
