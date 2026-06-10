# Changelog

All notable changes to the **Web Browser Panel** IntelliJ plugin are recorded
here. The format loosely follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
versions match the artifact published to the JetBrains Marketplace.

## [0.5.0] – [0.5.7] — 2026-06

The 0.5 line turns the panel into a proper in-IDE dev browser: in-page search,
DevTools and a console, a full-featured tab strip, project-aware settings, and
editor integration.

### Added — Dev-loop power tools
- **Find in page** (`Ctrl/Cmd+F`) — inline find bar with next / previous and
  the standard Enter / Shift+Enter / Esc keys.
- **DevTools** — open the full Chromium DevTools in its own window from a
  toolbar button or with `F12`.
- **Browser console** — a bottom panel shows the active tab's
  `console.log/warn/error` output in real time, level-coloured, with an
  auto-scroll buffer capped at ~200 kB. Toggle from the settings row or
  with `Ctrl+\``.
- **Hard reload** bypassing the disk cache (`Ctrl/Cmd+Shift+R`), plus a
  **Stop** button — the reload icon becomes a stop icon while a page loads
  and cancels the load on click.

### Added — A full tab strip
- **Drag to reorder**, **pin** tabs, and a right-click menu with *Pin* /
  *Close* / *Close Other Tabs*. Pinned tabs survive *Close Other* and
  round-trip across restarts.
- **Overflow** — the tab strip scrolls horizontally when there are more tabs
  than fit, instead of wrapping onto a second row.
- **Reopen closed tab** (`Ctrl/Cmd+Shift+T`), **middle-click to close**,
  **New Tab** (`Ctrl/Cmd+T`), and **Ctrl+Tab** / **Ctrl+Shift+Tab** cycling.
- **Persisted session** — your open tabs are restored when the project
  re-opens (view-source and placeholder tabs are skipped).

### Added — Project-aware settings
- **Per-project home URL** overriding the application default; used by the
  *Home* button and *Open in…*.
- **Glob watch patterns** — a power-user alternative to the
  folder + extensions field, e.g. `/project/src/**/*.{ts,tsx}`, one per line.
- **Per-host zoom memory** — a site's zoom level is remembered and reapplied
  next time you visit that host.
- **Detect from project** — a button that scans for Storybook / Next.js /
  Vite / `package.json` dev scripts and pre-fills the URL, watch patterns,
  and a run-config hint.
- **Settings validation** — *Apply* refuses invalid input (empty URL,
  unparseable globs) and focuses the offending field.

### Added — Address bar & editor integration
- **History-backed autocomplete** on the address bar — a per-project LRU of
  visited URLs suggested as you type (arrow keys navigate, Enter accepts,
  Esc dismisses).
- **Open URL in Web Browser Panel** — an editor right-click action
  (`Ctrl/Cmd+Shift+B`) opens the URL under the caret, or the selected text,
  in the panel instead of the OS browser.

### Changed — Internals
- Tab state is owned by a single `TabRegistry` collaborator (the
  "every TabId has a live JCEF pane" invariant is enforced by construction),
  and the tab strip is a custom `BrowserTabStrip` component (the platform
  `JTabbedPane` / `JBTabs` couldn't render a usable close button or height in
  this tool-window context).
- Find-in-page extracted into a `FindOverlay` component; new pure-domain
  types `ConsoleMessage`, `DevServerDetector`, `SettingsValidation`,
  `AddressBarHistory`, `TabSession`, and per-host-zoom / glob helpers, each
  covered by unit tests.

## [0.2.8] – [0.4.3]

A series of releases focused on a richer browsing experience and a tidier
toolbar.

### Added — Tabs
- The tool window now supports multiple tabs. Open new tabs from the
  *New Tab* button; close them with the `×` on each tab.
- Links that would normally open in a separate window
  (`target="_blank"`) now open as a new tab inside the panel instead of
  escaping the IDE.
- Each tab keeps its own URL, navigation history, and zoom level; switching
  tabs updates the toolbar to match.

### Added — Zoom
- *Zoom ▾* dropdown on the toolbar offers *Zoom In*, *Zoom Out*, and *Reset
  Zoom*, with a matching *Zoom ▸* submenu on the right-click context menu.
  Zoom is per-tab and steps reliably in both directions.

### Changed — Compact toolbar
- Back, forward, and reload are borderless icons inside the URL field, so
  the address bar stays usable even on narrow tool windows.
- A *Settings ▾* toggle collapses the secondary row (*Reload on save*,
  *Auto-refresh*, *Zoom*, *Open in*) into a single line that hides by
  default, and remembers your choice between sessions.

### Changed — Auto-refresh dropdown
- The auto-refresh interval is a compact dropdown of presets (1, 2, 3, 5,
  10, 30, 60 seconds) instead of a free-form number field. Any previously
  configured interval is rounded to the nearest preset.

### Fixed
- *View Page Source* from the right-click menu now works and opens in its
  own tab so you can dismiss it by closing the tab.
- Placeholder and blank tabs are labelled *New Tab* instead of an internal
  identifier.
- Removed a stray bright frame that could appear around tab content under
  some themes.

## [0.2.7]
- Renamed the plugin to **Web Browser Panel** to avoid a Marketplace name
  collision. Plugin id, settings, and behaviour are unchanged.

## [0.2.6]
- **Smart address bar**: typing a search phrase (e.g. `what is rust`) and
  pressing Enter now runs a [Startpage](https://www.startpage.com/) search
  instead of trying to load it as a URL. URL-like inputs (with a scheme, a
  `/`, a dot, `localhost`, an IP, or `host:port`) still load normally. The
  same routing applies when you use *Open in…*.

## [0.2.5]
- **Fix: minimal pages no longer render dark.** The 0.2.4 attempt set
  `color-scheme: light` via JS after the page rendered, which Chromium
  ignored for the already-painted canvas. The browser's Swing wrapper is
  now forced opaque white, and untheme pages additionally get a white
  `body` background. Pages that opt into a color scheme of their own
  (`color-scheme: dark` / `dark light`) are still left alone.

## [0.2.4]
- **Fix: dark canvas on minimal pages.** The embedded JCEF browser used to
  inherit the IDE's dark theme as its canvas, so pages that didn't declare
  a `color-scheme` (a minimal Astro/Vite starter, a stripped-down HTML
  page, …) rendered dark text on a dark background and were unreadable.
  The plugin now sets `color-scheme: light` on the root element of any
  page that doesn't opt into a scheme of its own; pages that intentionally
  support dark mode (`dark` or `dark light`) are left alone.

## [0.2.3]
- Brighter tool-window stripe icon on dark themes (added a `_dark.svg`
  variant using the standard IntelliJ icon foreground so it no longer
  reads as washed-out next to the platform's own icons).

## [0.2.2]
- **Fix: "Open browser on run" now actually fires.** The
  `ExecutionListener` was registered as a project listener even though the
  topic is application-level, so on recent IDE builds it silently never
  received `processStarted` events. Now registered as an application
  listener.
- **Robust port probing.** The "wait until URL is reachable" mode now
  walks every resolved IP for the hostname (matters on macOS where
  `localhost` can resolve to `::1` first while many dev servers bind only
  to `127.0.0.1`).
- **UI:** the address bar is now on its own line below the navigation
  buttons, so it no longer disappears when the tool window gets narrow.
- Added INFO-level logging in the open-on-run flow so future "nothing
  happened" investigations leave a trail in `idea.log`.

## [0.2.1]
- **Friendlier load-failure view:** when a page fails to load, the
  embedded view falls back to the same themed home page shown on first
  open — no more cryptic browser error messages or assumptions about what
  kind of site you're loading.
- Subtle pulse animation on the home-page globe for a touch of life.

## [0.2.0]
- **New — Open in…:** a dropdown to open the current page in the system
  default browser or any IDE-configured browser.
- **New — Open browser on run:** automatically open the tool window when a
  run configuration starts, waiting until the URL is reachable.
- **New — Live reload:** reload on file save (selectable folder &
  extensions) plus timed auto-refresh.
- **New — Themed empty state:** a themed SVG placeholder in place of
  Chromium's error page.

## [0.1.0]
- Initial release: embedded Chromium browser tool window with an address
  bar, navigation, and a configurable home URL.

[0.5.7]: https://github.com/dervism/intellij-webbrowser/releases/tag/v0.5.7
[0.5.0]: https://github.com/dervism/intellij-webbrowser/releases/tag/v0.5.0
