# Web Browser Panel

An IntelliJ IDEA plugin that embeds a **Chromium-based web browser** (the IDE's
bundled JCEF runtime) in a tool window — handy for previewing a local web dev
server right next to your code.

> Note: the embedded view is plain Chromium (the IDE's bundled JCEF runtime), so it
> doesn't share your everyday browser's profile, extensions, or bookmarks. When you
> need those, the **Open in…** button hands the current page to your system default
> browser, or any browser configured under Settings → Tools → Web Browsers.

## Why another web browser plugin?

The plugin was purpose built for my own needs, because I wanted to stay in my editor while coding on laptops with smaller screens and also because certain files in my repoes wheren't part of HMR. The plugin watches selected file types and automatically reloads the files in the web browser panel on save. The panel is also automatically viewed when a run configuration has been ran by the user (see the plugin settings: Settings → Tools → Web Browser Panel).

## Features

### Browse
- **Embedded browser** — a Chromium (JCEF) view in a right-dock **Web Browser** tool window, with back / forward / reload, an editable address bar, and **zoom in / out / reset**.
- **Tabs** — open multiple pages side by side; `target="_blank"` links open in a new tab instead of escaping to an external window. Drag tabs to reorder, **pin** important ones to keep them across restarts, right-click for *Pin / Close / Close Other*, and use **Ctrl+Tab / Ctrl+Shift+Tab** to cycle. Closed tabs can be reopened, and your full tab session is restored when the project re-opens.
- **Smart address bar** — typing a URL navigates; typing a phrase runs a Startpage search. Clicking into the field selects the whole address, so you can immediately retype it. **History-backed autocomplete** suggests previously visited URLs from this project as you type (arrow keys / Enter / Esc); the history can be cleared from settings.
- **Find in page** — Ctrl/Cmd+F opens an inline find bar with next / previous and Esc-to-close.
- **DevTools** — open Chromium DevTools in its own window from the settings row, the page's right-click menu, or with F12.
- **Browser console** — a togglable bottom panel inside the tool window shows the active tab's `window.console` output in real time, with level-coloured lines. Toggle from the settings row or with Ctrl+`. F12 (or the right-click menu) still opens the full DevTools when you need it.
- **Hard reload** — Ctrl/Cmd+Shift+R reloads bypassing the disk cache. The reload icon doubles as a **stop** button while a page is loading.
- **Themed empty state** — a clean, theme-aware SVG screen before anything loads and when a page fails to load, in place of Chromium's raw error page.
- **Right-click menu** — Chromium's standard context menu, plus an **Open DevTools** item and a **Zoom** submenu. **View Page Source** opens in a new tab so you can close it to dismiss source view.
- **Per-host zoom memory** — adjust zoom on a site and it's restored automatically next time you visit the same host.
- **Open in…** — hand the current page to your system default browser, or any browser configured under *Settings → Tools → Web Browsers*.

### Live reload
- **Reload on save** — reload when a watched file is saved (⌘S or IntelliJ autosave). Pick the **folder** to watch (default: the whole project) and the **extensions** (default: common web files like `html, css, scss, js, ts, jsx, tsx, vue, svelte`).
- **Watch patterns** — for finer control, supply one glob per line (e.g. `/project/src/**/*.{ts,tsx}`). Patterns override the simple folder + extensions scheme when present.
- **Auto-refresh** — reload on a fixed timer; pick from preset intervals (1, 2, 3, 5, 10, 30, 60 seconds).

### Open on run
- When a run configuration starts, open the tool window and navigate it to your app. Choose **which** configuration (or *Any*), the **URL** (blank = home), and **when** to open: *the URL is reachable* (polls the host:port until the server responds — the truest "after the app has loaded"; default), *after a fixed delay*, or *immediately on launch*.

### IDE integration
- **Open URL in Web Browser Panel** — right-click a URL in the editor (or select one) and route it to the panel instead of the OS browser. Bound to Ctrl/Cmd+Shift+B.

### Configuration
The settings page lives under *Settings → Tools → Web Browser Panel*, in three groups:

- **URLs & history** — the **default home / dev-server URL** (application-wide, `http://localhost:3000` out of the box) and an optional **per-project home URL** that overrides it. **Clear address-bar history** forgets the autocomplete suggestions (with a live count of how many are stored).
- **Reload on save** — the **folder** to watch (default: the whole project) and the file **extensions**, or — for finer control — newline-separated **watch patterns** (globs like `/project/src/**/*.{ts,tsx}`) that override the folder + extensions when set.
- **Open browser on run** — enable opening the panel when a run configuration starts, then choose **which** configuration (or *Any*), the **URL** (blank = home), and **when** to open (the URL is reachable / after a delay / immediately).

**Invalid input** (empty URL, unparseable globs, …) is refused at *Apply* time with focus moved to the offending field. Most in-panel toggles — *Reload on save*, *Auto-refresh* and its interval, the collapsible *Settings* row — plus your tab session persist between sessions.

## Keyboard shortcuts

| Shortcut | Action |
| --- | --- |
| Ctrl/Cmd+F | Find in page |
| Esc | Close find bar |
| Ctrl/Cmd+T | New tab |
| Ctrl/Cmd+Shift+T | Reopen last closed tab |
| Ctrl/Cmd+Shift+R | Hard reload (bypass cache) |
| Ctrl+Tab / Ctrl+Shift+Tab | Cycle to next / previous tab |
| Ctrl+` | Toggle browser console |
| F12 | Open DevTools |
| Middle-click on a tab | Close the tab |
| Right-click on a tab | Pin / Close / Close Other |
| Drag a tab | Reorder |
| Ctrl/Cmd+Shift+B | Open URL under caret in the panel (editor) |

## Requirements

- IntelliJ IDEA **2026.2** (build 262) or newer, running on a JCEF-enabled
  JetBrains Runtime (the default JBR includes JCEF). (2026.1 is supported by
  plugin version 0.5.7 and earlier.)
- A JDK to build with: **JDK 26**. IntelliJ 2026.2 ships some platform classes
  (the JCEF module) as Java 25 bytecode, so the build toolchain must be able to
  read them; the build is pinned to the installed JDK 26 in `build.gradle.kts`.
  The plugin itself still compiles to **Java 21** bytecode, so it runs on the
  IDE's JBR 25.

## Build

The build compiles against a **local** IntelliJ installation, so it doesn't
download a full IDE distribution. By default it looks for the standard macOS
location `/Applications/IntelliJ IDEA.app`; on other platforms — or for a
JetBrains Toolbox install — point it elsewhere via any of:

```bash
./gradlew buildPlugin                                # use the default path
./gradlew buildPlugin -PlocalIdePath="/path/to/IDE"  # one-off override
LOCAL_IDE_PATH="/path/to/IDE" ./gradlew buildPlugin  # environment variable
```

…or set `localIdePath=...` in the project's `gradle.properties` (a commented
example is included) or in `~/.gradle/gradle.properties`.

The installable plugin zip lands in `build/distributions/`.

## Try it without installing

Launch a sandbox IDE with the plugin loaded:

```bash
./gradlew runIde
```

## Install

**From the JetBrains Marketplace (recommended):** open *Settings → Plugins →
Marketplace*, search for **Web Browser Panel**, click **Install**, and restart
the IDE. The **Web Browser** tool window then appears on the right tool-window bar.

**From a local build (for testing an unreleased version):** *Settings → Plugins →
⚙ → Install Plugin from Disk…*, pick the zip from `build/distributions/`, and restart.

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for the per-version notes. The same notes
also appear on the Marketplace under *What's New*.

## Contributing

Issues are welcome — see the *Bug report* and *Feature request* templates
when you open one. Pull requests are welcome too; the codebase aims for
strong DDD + functional-style Kotlin, and the domain layer is covered by
plain-JVM unit tests under `domainTest` (JaCoCo measures coverage there).

## Author & license

- **Author:** Dervis Mansuroglu
- **Repository:** https://github.com/dervism/intellij-webbrowser
- **License:** [MIT](LICENSE)
