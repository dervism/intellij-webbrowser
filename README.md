# IntelliJ-WebBrowser

An IntelliJ IDEA plugin that embeds a **Chromium-based web browser** (the IDE's
bundled JCEF runtime) in a tool window — handy for previewing a local web dev
server right next to your code.

> Note: the embedded view is plain Chromium (the IDE's bundled JCEF runtime), so it
> doesn't share your everyday browser's profile, extensions, or bookmarks. When you
> need those, the **Open in…** button hands the current page to your system default
> browser, or any browser configured under Settings → Tools → Web Browsers.

## Features

- A **Web Browser** tool window (right dock) with an embedded Chromium view
- A themed, **SVG empty-state** shown before a page is loaded and whenever a load
  fails (replaces Chromium's raw error page) — with a one-click link to your home URL
- Back / forward / reload and an editable address bar
- A configurable **home / dev-server URL** (default `http://localhost:3000`)
  under *Settings → Tools → IntelliJ-WebBrowser*
- **Open in…** — opens the current address in your system default browser, or any
  browser configured in the IDE (Settings → Tools → Web Browsers)
- **Live reload** (toolbar, second row):
  - **Reload on save** — reloads the page whenever a watched file in the project
    is saved (⌘S or IntelliJ autosave). Choose *which* files in
    *Settings → Tools → IntelliJ-WebBrowser*:
    - **Folder** — a directory picked from this project (blank = whole project)
    - **Extensions** — comma-separated list (blank = any file); defaults to common
      web extensions (`html, css, scss, js, ts, jsx, tsx, vue, svelte, …`)
  - **Auto-refresh every N s** — reloads on a fixed timer; default **5 s**, type
    any number of seconds
- **Open browser on run** — when a run configuration starts, open the tool window
  and navigate it to your app. Configure in *Settings → Tools → IntelliJ-WebBrowser*:
  - **Run configuration** — a specific one from this project, or *Any*
  - **URL** — what to open (blank = home URL)
  - **Open when** — *the URL is reachable* (polls host:port until the server
    accepts connections — the truest "after the app has loaded"; default),
    *after a fixed delay*, or *immediately on launch*

Toggle states and the interval are remembered between sessions.

## Requirements

- IntelliJ IDEA **2026.1** (build 261) or newer, running on a JCEF-enabled JBR
  (the default JetBrains Runtime includes JCEF)
- JDK 21+ available to build (this project compiles to Java 21 bytecode; it
  builds fine with the JDK 25.

## Build

The build targets a **local** IntelliJ install (so it doesn't download a full IDE
distribution). It defaults to `/Applications/IntelliJ IDEA.app`, but the path is
**not** hardcoded — override it any of these ways:

```bash
./gradlew buildPlugin                                   # uses the default path
./gradlew buildPlugin -PlocalIdePath="/path/to/IDE.app" # one-off override
LOCAL_IDE_PATH="/path/to/IDE.app" ./gradlew buildPlugin  # env var
```

…or set `localIdePath=...` in this project's `gradle.properties` (a commented
line is there) or in your `~/.gradle/gradle.properties`.

The installable plugin zip is produced at:

```
build/distributions/intellij-webbrowser-0.2.0.zip
```

## Try it without installing

Launch a sandbox IDE with the plugin loaded:

```bash
./gradlew runIde
```

## Install

**From the JetBrains Marketplace (recommended):** open *Settings → Plugins →
Marketplace*, search for **IntelliJ-WebBrowser**, click **Install**, and restart
the IDE. The **Web Browser** tool window then appears on the right tool-window bar.

**From a local build (for testing an unreleased version):** *Settings → Plugins →
⚙ → Install Plugin from Disk…*, pick the zip from `build/distributions/`, and restart.

## Author & license

- **Author:** Dervis Mansuroglu
- **Repository:** https://github.com/dervism/intellij-webbrowser
- **License:** [MIT](LICENSE)
