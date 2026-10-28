# Live-reload browser checks

These tests run the scripts in `ui/LiveReloadScripts.kt` in a real Chromium through
[Playwright](https://playwright.dev). The JVM tests can't run JavaScript, and the
rules that keep the scripts safe live in the scripts themselves: a saved position
is only used for the same page and only for 10 seconds, and stylesheets are only
swapped on a page served from this machine.

Run them after changing the scripts:

```bash
./gradlew liveReloadBrowserTest
```

You need Node.js 20 or newer, with npm. The task:

1. compiles the plugin and writes the three scripts to `build/livereload-scripts/`,
2. installs the pinned Playwright with `npm ci --ignore-scripts`,
3. downloads Playwright's Chromium on the first run (about 190 MB, cached
   afterwards), and
4. runs `live-reload.test.js` with Node's built-in test runner.

The tests serve the pages in `site/` on 127.0.0.1 from a free port, and give each
test its own browser context. `evil.test` is mapped to 127.0.0.1 to stand in for a
host that isn't this machine.

The task isn't part of `./gradlew check`, `buildPlugin` or the release script.
