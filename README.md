# Embedded Web Browser

An IntelliJ IDEA plugin that embeds a **Chromium-based web browser** (the IDE's
bundled JCEF runtime) in a tool window — handy for previewing a local web dev
server right next to your code.

> Note: the embedded view is plain Chromium (the IDE's bundled JCEF runtime), so it
> doesn't share your everyday browser's profile, extensions, or bookmarks. When you
> need those, the **Open in…** button hands the current page to your system default
> browser, or any browser configured under Settings → Tools → Web Browsers.

## Features

### Browse
- **Embedded browser** — a Chromium (JCEF) view in a right-dock **Web Browser** tool window, with back / forward / reload and an editable address bar.
- **Themed empty state** — a clean, theme-aware SVG screen before anything loads and when a page fails to load, in place of Chromium's raw error page.
- **Open in…** — hand the current page to your system default browser, or any browser configured under *Settings → Tools → Web Browsers*.

### Live reload
- **Reload on save** — reload when a watched file is saved (⌘S or IntelliJ autosave). Pick the **folder** to watch (default: the whole project) and the **extensions** (default: common web files like `html, css, scss, js, ts, jsx, tsx, vue, svelte`).
- **Auto-refresh** — reload on a fixed timer; default **5 s**, set any interval.

### Open on run
- When a run configuration starts, open the tool window and navigate it to your app. Choose **which** configuration (or *Any*), the **URL** (blank = home), and **when** to open: *the URL is reachable* (polls the host:port until the server responds — the truest "after the app has loaded"; default), *after a fixed delay*, or *immediately on launch*.

### Configuration
All options live under *Settings → Tools → Embedded Web Browser*, including the **home / dev-server URL** (default `http://localhost:3000`). Toggle states and the refresh interval persist between sessions.

## Requirements

- IntelliJ IDEA **2026.1** (build 261) or newer, running on a JCEF-enabled
  JetBrains Runtime (the default JBR includes JCEF).
- A JDK to build with: the plugin compiles to Java 21 bytecode, so **JDK 21 or
  newer** works.

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
Marketplace*, search for **Embedded Web Browser**, click **Install**, and restart
the IDE. The **Web Browser** tool window then appears on the right tool-window bar.

**From a local build (for testing an unreleased version):** *Settings → Plugins →
⚙ → Install Plugin from Disk…*, pick the zip from `build/distributions/`, and restart.

## Author & license

- **Author:** Dervis Mansuroglu
- **Repository:** https://github.com/dervism/intellij-webbrowser
- **License:** [MIT](LICENSE)
