# Parked items

Work that was considered and deliberately set aside, with the reason and what
picking it up again would take. Nothing here is scheduled; an item moves back to
the ROADMAP when the reason no longer holds.

## Exclude a random JCEF remote-debugging port from dev-server URL detection
Parked in 0.7.1. When JCEF remote debugging is on with a random port, *When the
server prints its URL* doesn't exclude that port, so a printed
`http://localhost:<that port>/` could open the DevTools endpoint in the panel. The
same gap covers a fixed debug port changed after the IDE started, because JCEF
reads that setting once at startup.

Why parked: the port is random and the detection opens at most one URL per run, so
it can't realistically be targeted. Looking the port up means calling JCEF, which can
start Chromium, raise IDE error reports or block the UI if done carelessly, and none of
it can be tested while the platform tests are blocked.

If picked up: ask JCEF only when it is already running (`JBCefApp.isStarted()`) and
the registry says debugging is on, wrap every call, exclude both the fixed and the
reported port, and check the port again just before opening, refusing while the
lookup hasn't answered.

## Device emulation through the DevTools protocol
Parked when the preview width presets were built. Instead of only narrowing the
browser component, the panel could ask Chromium to emulate a device, with its pixel
ratio, a mobile user agent and touch input, through JCEF's DevTools protocol client.

Why parked: that client accepts any DevTools method with raw JSON and reaches the
page, its cookies and storage, so it brings a powerful channel into the plugin for a
modest gain. The width presets cover the main need, checking layouts at a device
width.

If picked up: build it on top of the width presets, with a closed list of allowed
commands (`setDeviceMetricsOverride`, `clearDeviceMetricsOverride`,
`setUserAgentOverride` with built-in user agents, `setTouchEmulationEnabled`), typed
JSON, no generic sender, no event listeners, results never shown or logged, failures
handled, changes sent only when values change, overrides cleared on *Desktop*, and a
test that only the allowed commands can be sent.
