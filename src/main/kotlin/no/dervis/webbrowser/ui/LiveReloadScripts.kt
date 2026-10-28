package no.dervis.webbrowser.ui

/**
 * The scripts that keep the page in place across reloads.
 *
 * Each is a compile-time constant: none contains a file name, URL, setting or
 * anything read from the page, and none sends anything back to the IDE. They only
 * touch state the page could change itself, and treat everything they read from
 * the page as untrusted.
 */
internal object LiveReloadScripts {

    private const val SCROLL_KEY = "__webBrowserPanelScroll"
    private const val RELOAD_PARAM = "webBrowserPanelReload"
    private const val PENDING_ATTRIBUTE = "data-web-browser-panel-pending"

    // readEntry(): the position saved for this exact page in the last 10 seconds,
    // or null.
    private const val READ_ENTRY = """
        function readEntry() {
          var entry = null;
          try { entry = JSON.parse(sessionStorage.getItem('$SCROLL_KEY')); } catch (e) {}
          if (!entry || typeof entry !== 'object' || entry.href !== location.href) return null;
          var age = Date.now() - Number(entry.t);
          return age >= 0 && age <= 10000 ? entry : null;
        }
    """

    // saveAndReload(): saves the scroll position, then reloads from inside the page,
    // so the save always comes first. A position still waiting to be restored is
    // left as it is: until the restore is done, the page isn't back there yet.
    private const val SAVE_AND_RELOAD = """
        function saveAndReload() {
          try {
            if (!readEntry()) {
              sessionStorage.setItem('$SCROLL_KEY', JSON.stringify(
                { x: window.scrollX, y: window.scrollY, href: location.href, t: Date.now() }));
            }
          } catch (e) {}
          location.reload();
        }
    """

    /** Remembers the scroll position and reloads the page. */
    const val SAVE_SCROLL_AND_RELOAD = "(function () {$READ_ENTRY$SAVE_AND_RELOAD saveAndReload(); })();"

    /**
     * Runs after every load of an http(s) page. When a position was saved for this
     * exact page within the last 10 seconds, it scrolls back there, trying again
     * for up to 2 seconds while content renders, and stops as soon as the user
     * scrolls, clicks or types. The saved position is deleted once it's done.
     */
    const val RESTORE_SCROLL = """(function () {$READ_ENTRY
        var entry = readEntry();
        var x = entry ? Number(entry.x) : NaN, y = entry ? Number(entry.y) : NaN;
        var done = false, tries = 0;
        function finish() {
          if (done) return;
          done = true;
          try { sessionStorage.removeItem('$SCROLL_KEY'); } catch (e) {}
        }
        if (!isFinite(x) || !isFinite(y)) { finish(); return; }
        x = Math.max(0, x); y = Math.max(0, y);
        ['wheel', 'touchstart', 'keydown', 'mousedown'].forEach(function (type) {
          window.addEventListener(type, finish, { once: true, passive: true, capture: true });
        });
        (function restore() {
          if (done) return;
          window.scrollTo(x, y);
          var there = Math.abs(window.scrollX - x) < 2 && Math.abs(window.scrollY - y) < 2;
          if (there || ++tries >= 20) finish(); else setTimeout(restore, 100);
        })();
    })();"""

    /**
     * Re-requests the page's stylesheets: every `link rel=stylesheet` in the page
     * served from this machine gets a fresh copy with one cache-busting parameter,
     * swapped in once it has loaded. A copy still loading from an earlier swap is
     * dropped first. Only on a page served from this machine; anywhere else, when
     * the page has no such stylesheet, or when it shows a page of its own origin in
     * a frame (whose stylesheets this can't see), it reloads and keeps the scroll
     * position instead.
     */
    const val HOT_SWAP_CSS = """(function () {$READ_ENTRY$SAVE_AND_RELOAD
        function isLocal(href) {
          try {
            var url = new URL(href);
            return /^https?:$/.test(url.protocol) &&
              /^(localhost|0\.0\.0\.0|127\.\d{1,3}\.\d{1,3}\.\d{1,3}|\[::1?\])$/.test(url.hostname);
          } catch (e) { return false; }
        }
        // A frame on another origin throws here, and a blank one has origin "null".
        var framed = Array.prototype.some.call(window, function (frame) {
          try { return frame.location.origin === location.origin; } catch (e) { return false; }
        });
        Array.prototype.forEach.call(document.querySelectorAll('link[$PENDING_ATTRIBUTE]'), function (link) {
          link.remove();
        });
        var links = !isLocal(location.href) || framed ? [] : Array.prototype.filter.call(
          document.querySelectorAll('link[rel~="stylesheet"][href]'),
          function (link) { return isLocal(link.href); });
        if (links.length === 0) {
          saveAndReload();
          return;
        }
        var stamp = String(Date.now());
        links.forEach(function (link) {
          var url = new URL(link.href);
          var query = url.search.slice(1).split('&').filter(function (part) {
            return part !== '' && part.split('=')[0] !== '$RELOAD_PARAM';
          });
          query.push('$RELOAD_PARAM=' + stamp);
          url.search = '?' + query.join('&');
          var fresh = link.cloneNode(false);
          fresh.href = url.href;
          fresh.setAttribute('$PENDING_ATTRIBUTE', '');
          fresh.addEventListener('load', function () {
            if (!fresh.isConnected) return;
            fresh.removeAttribute('$PENDING_ATTRIBUTE');
            link.remove();
          }, { once: true });
          fresh.addEventListener('error', function () { fresh.remove(); }, { once: true });
          link.after(fresh);
        });
    })();"""
}
