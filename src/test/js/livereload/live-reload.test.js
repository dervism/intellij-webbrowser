// Runs the live-reload scripts from ui/LiveReloadScripts.kt in a real Chromium.
// Start it with `./gradlew liveReloadBrowserTest`, which exports the compiled
// scripts to build/livereload-scripts first.
import { after, before, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { chromium } from 'playwright';
import { startSite } from './site-server.js';

const scriptsDir = process.env.LIVE_RELOAD_SCRIPTS;
assert.ok(scriptsDir, 'LIVE_RELOAD_SCRIPTS is not set: run ./gradlew liveReloadBrowserTest');
const script = (name) => readFileSync(join(scriptsDir, name), 'utf8');
const SAVE = script('save.js');
const RESTORE = script('restore.js');
const HOT_SWAP = script('hotswap.js');
// Read from the script, so a renamed key can't make the planted-entry tests pass on nothing.
const KEY = RESTORE.match(/sessionStorage\.getItem\('([^']+)'\)/)[1];
const PARAM = 'webBrowserPanelReload';

let site;
let browser;

before(async () => {
  site = await startSite(new URL('./site/', import.meta.url));
  browser = await chromium.launch({
    channel: 'chromium',
    // evil.test stands in for a host that isn't this machine.
    args: ['--host-resolver-rules=MAP evil.test 127.0.0.1'],
  });
});

after(async () => {
  await browser?.close();
  await site?.close();
});

// ---- helpers ------------------------------------------------------------------

const url = (host, path) => `http://${host}:${site.port}/${path}`;
const local = (path) => url('127.0.0.1', path);
const sleep = (ms) => new Promise(resolve => setTimeout(resolve, ms));

/** Runs [body] with a page in a fresh browser context, so no storage is shared. */
async function withPage(body) {
  const context = await browser.newContext({ viewport: { width: 800, height: 600 } });
  try {
    return await body(await context.newPage());
  } finally {
    await context.close();
  }
}

const scrollY = (page) => page.evaluate('window.scrollY');
const scrollTo = (page, y) => page.evaluate(`window.scrollTo(0, ${y})`);
const savedEntry = (page) => page.evaluate(key => sessionStorage.getItem(key), KEY);
const plant = (page, entry) => page.evaluate(([key, value]) => sessionStorage.setItem(key, value), [KEY, entry]);
const sheets = (page) => page.evaluate(`[...document.querySelectorAll('link[rel~="stylesheet"]')].map(l => l.href)`);
const background = (page) => page.evaluate('getComputedStyle(document.body).backgroundColor');

/** Polls [read] until it returns [expected] or [ms] pass, and returns the last value. */
async function eventually(read, expected, ms = 3000) {
  const deadline = Date.now() + ms;
  let value = await read();
  while (value !== expected && Date.now() < deadline) {
    await sleep(50);
    value = await read();
  }
  return value;
}

/** Runs [js], which reloads the page, and waits for the new page's load event. */
async function runAndReload(page, js) {
  const loaded = page.waitForEvent('load');
  await page.evaluate(js).catch(() => {}); // the page goes away under the script
  await loaded;
}

/** Runs the hot swap and tells whether the page reloaded within [ms]. */
async function hotSwapReloads(page, ms = 1000) {
  await page.evaluate('window.__probe = 1');
  const loaded = page.waitForEvent('load', { timeout: ms }).then(() => true, () => false);
  await page.evaluate(HOT_SWAP).catch(() => {});
  const reloaded = await loaded;
  assert.equal(await page.evaluate('window.__probe'), reloaded ? undefined : 1);
  return reloaded;
}

/** True when nothing keeps scrolling the page after a scroll of its own. */
async function scrollsFreely(page) {
  await scrollTo(page, 500);
  await sleep(400);
  return (await scrollY(page)) === 500;
}

function addLink(page, href) {
  return page.evaluate(h => new Promise(done => {
    const link = document.createElement('link');
    link.rel = 'stylesheet';
    link.href = h;
    link.onload = link.onerror = () => done();
    document.head.appendChild(link);
  }), href);
}

function addFrame(page, attributes) {
  return page.evaluate(a => new Promise(done => {
    const frame = document.createElement('iframe');
    Object.assign(frame, a);
    frame.onload = () => done();
    document.body.prepend(frame);
  }), attributes);
}

// ---- restoring the scroll position --------------------------------------------

describe('restoring the scroll position', () => {
  test('a reload comes back to where the page was scrolled', () => withPage(async page => {
    await page.goto(local('index.html'));
    await scrollTo(page, 2000);
    await runAndReload(page, SAVE);
    assert.equal(await scrollY(page), 0, "the test page turns off Chromium's own restore");
    await page.evaluate(RESTORE);
    assert.equal(await eventually(() => scrollY(page), 2000), 2000);
    assert.equal(await savedEntry(page), null, 'the saved position is deleted once restored');
  }));

  test('content that renders after the load is waited for', () => withPage(async page => {
    await page.goto(local('late.html'));
    await sleep(800);
    await scrollTo(page, 2500);
    assert.equal(await scrollY(page), 2500);
    await runAndReload(page, SAVE);
    await page.evaluate(RESTORE);
    assert.equal(await eventually(() => scrollY(page), 2500), 2500);
  }));

  test('scrolling during the restore stops it', () => withPage(async page => {
    await page.goto(local('late.html'));
    await sleep(800);
    await scrollTo(page, 2500);
    await runAndReload(page, SAVE);
    await page.evaluate(RESTORE);
    await page.evaluate("window.dispatchEvent(new WheelEvent('wheel'))");
    await sleep(1200);
    assert.equal(await scrollY(page), 0);
  }));

  test('a wheel event the page stops still stops the restore', () => withPage(async page => {
    await page.goto(local('late.html'));
    await sleep(800);
    await scrollTo(page, 2500);
    await runAndReload(page, SAVE);
    await page.evaluate(RESTORE);
    await page.evaluate(`(() => {
      const heading = document.querySelector('h1');
      heading.addEventListener('wheel', e => e.stopPropagation());
      heading.dispatchEvent(new WheelEvent('wheel', { bubbles: true }));
    })()`);
    await sleep(1200);
    assert.equal(await scrollY(page), 0);
  }));

  test('after the user scrolls, the next reload keeps the new position', () => withPage(async page => {
    await page.goto(local('late.html'));
    await sleep(800);
    await scrollTo(page, 2500);
    await runAndReload(page, SAVE);
    await page.evaluate(RESTORE);
    await page.evaluate("window.dispatchEvent(new WheelEvent('wheel'))");
    await sleep(800);
    await scrollTo(page, 300);
    await runAndReload(page, SAVE);
    await page.evaluate(RESTORE);
    assert.equal(await eventually(() => scrollY(page), 300), 300);
    await sleep(1200);
    assert.equal(await scrollY(page), 300);
  }));

  test('a planted entry for this page is restored', () => withPage(async page => {
    await page.goto(local('index.html'));
    await plant(page, JSON.stringify({ x: 0, y: 1500, href: local('index.html'), t: Date.now() }));
    await page.evaluate(RESTORE);
    assert.equal(await eventually(() => scrollY(page), 1500), 1500);
  }));

  const ignored = {
    'an entry for another page': () => JSON.stringify({ x: 0, y: 1500, href: local('other.html'), t: Date.now() }),
    'an entry older than 10 seconds': () => JSON.stringify({ x: 0, y: 1500, href: local('index.html'), t: Date.now() - 60000 }),
    'an entry from the future': () => JSON.stringify({ x: 0, y: 1500, href: local('index.html'), t: Date.now() + 60000 }),
    'an entry with infinite or non-numeric positions': () => JSON.stringify({ x: '1e999', y: 'abc', href: local('index.html'), t: Date.now() }),
    'broken JSON': () => '{not json',
    'an entry that is a number': () => '42',
    'a null entry': () => 'null',
  };
  for (const [name, entry] of Object.entries(ignored)) {
    test(`${name} is ignored and deleted`, () => withPage(async page => {
      await page.goto(local('index.html'));
      await plant(page, entry());
      await page.evaluate(RESTORE);
      await sleep(300);
      assert.equal(await scrollY(page), 0);
      assert.equal(await savedEntry(page), null);
      assert.ok(await scrollsFreely(page), 'the restore gave up at once');
    }));
  }

  test('a negative position scrolls to the top and stops', () => withPage(async page => {
    await page.goto(local('index.html'));
    await plant(page, JSON.stringify({ x: -50, y: -900, href: local('index.html'), t: Date.now() }));
    await page.evaluate(RESTORE);
    await sleep(300);
    assert.equal(await scrollY(page), 0);
    assert.ok(await scrollsFreely(page), 'the restore stopped once at the top');
  }));
});

// ---- saving before a reload -----------------------------------------------------

describe('saving the position before a reload', () => {
  test('a save while the page is still loading keeps the earlier position', () => withPage(async page => {
    // The image holds the load event back on every load.
    await page.route('**/slow.png', async route => {
      await sleep(1500);
      await route.fulfill({ status: 404 });
    });
    await page.goto(local('slow.html'));
    await scrollTo(page, 2000);
    let parsed = page.waitForEvent('domcontentloaded');
    await page.evaluate(SAVE).catch(() => {});
    await parsed;
    assert.notEqual(await page.evaluate('document.readyState'), 'complete');
    const loaded = page.waitForEvent('load');
    parsed = page.waitForEvent('domcontentloaded');
    await page.evaluate(SAVE).catch(() => {});
    await parsed;
    await loaded;
    await page.evaluate(RESTORE);
    assert.equal(await eventually(() => scrollY(page), 2000), 2000);
  }));

  test('a reload during a restore keeps the real position', () => withPage(async page => {
    await page.goto(local('late.html'));
    await sleep(800);
    await scrollTo(page, 2500);
    await runAndReload(page, SAVE);
    await page.evaluate(RESTORE);
    await sleep(300);
    assert.equal(await scrollY(page), 0, 'the content has not rendered yet');
    await runAndReload(page, SAVE);
    await page.evaluate(RESTORE);
    assert.equal(await eventually(() => scrollY(page), 2500), 2500);
  }));

  test('a save after a finished restore records the new position', () => withPage(async page => {
    await page.goto(local('index.html'));
    await scrollTo(page, 2000);
    await runAndReload(page, SAVE);
    await page.evaluate(RESTORE);
    assert.equal(await eventually(() => scrollY(page), 2000), 2000);
    await scrollTo(page, 1000);
    // A wheel event left over from the finished restore must not delete the new save.
    await runAndReload(page, `${SAVE}\nwindow.dispatchEvent(new WheelEvent('wheel'));`);
    await page.evaluate(RESTORE);
    assert.equal(await eventually(() => scrollY(page), 1000), 1000);
  }));

  test('a saved position stays on its own origin', () => withPage(async page => {
    await page.goto(url('localhost', 'index.html'));
    await plant(page, JSON.stringify({ x: 0, y: 1700, href: local('index.html'), t: Date.now() }));
    await page.goto(local('index.html'));
    await page.evaluate(RESTORE);
    await sleep(300);
    assert.equal(await scrollY(page), 0);
  }));
});

// ---- swapping stylesheets --------------------------------------------------------

describe('swapping stylesheets', () => {
  test('the page stays as it is and gets one fresh copy each time', () => withPage(async page => {
    await page.goto(local('index.html'));
    await scrollTo(page, 1200);
    const requests = [];
    page.on('request', request => request.url().includes('style.css') && requests.push(request.url()));
    assert.equal(await hotSwapReloads(page), false);
    assert.equal(await hotSwapReloads(page), false);
    assert.equal(await scrollY(page), 1200);
    const links = await sheets(page);
    assert.equal(links.length, 1, JSON.stringify(links));
    assert.equal(links[0].split(`${PARAM}=`).length, 2, 'one cache-busting parameter');
    assert.equal(requests.length, 2, JSON.stringify(requests));
    assert.equal(await background(page), 'rgb(255, 251, 230)');
  }));

  test('an edited stylesheet applies without a reload', () => withPage(async page => {
    const original = readFileSync(new URL('./site/style.css', import.meta.url), 'utf8');
    try {
      await page.goto(local('index.html'));
      site.override('/style.css', `${original}\nbody { background-color: rgb(1, 2, 3); }\n`);
      assert.equal(await hotSwapReloads(page), false);
      assert.equal(await eventually(() => background(page), 'rgb(1, 2, 3)'), 'rgb(1, 2, 3)');
    } finally {
      site.reset();
    }
  }));

  test("the rest of the stylesheet's query is kept as written", () => withPage(async page => {
    await page.goto(local('nocss.html'));
    await addLink(page, local('style.css?a=b%20c&flag&x=%2F'));
    assert.equal(await hotSwapReloads(page), false);
    await sleep(300);
    assert.equal(await hotSwapReloads(page), false);
    await sleep(300);
    const links = await sheets(page);
    assert.equal(links.length, 1, JSON.stringify(links));
    assert.match(links[0], new RegExp(`/style\\.css\\?a=b%20c&flag&x=%2F&${PARAM}=\\d+$`));
  }));

  test('swaps that overlap a slow stylesheet leave one copy', () => withPage(async page => {
    await page.route('**/style.css?*', async route => {
      await sleep(800);
      await route.continue();
    });
    await page.goto(local('index.html'));
    for (let i = 0; i < 3; i++) {
      await page.evaluate(HOT_SWAP);
      await sleep(300);
    }
    await sleep(1500);
    const links = await sheets(page);
    assert.equal(links.length, 1, JSON.stringify(links));
    assert.equal(await background(page), 'rgb(255, 251, 230)');
  }));

  test('a stylesheet from another loopback origin is swapped', () => withPage(async page => {
    await page.goto(local('nocss.html'));
    await addLink(page, url('localhost', 'style.css'));
    assert.equal(await hotSwapReloads(page), false);
    await sleep(300);
    const links = await sheets(page);
    assert.equal(links.length, 1, JSON.stringify(links));
    assert.ok(links[0].startsWith(`${url('localhost', 'style.css')}?${PARAM}=`), links[0]);
  }));

  test("a stylesheet from a host that isn't this machine is left alone", () => withPage(async page => {
    await page.goto(url('localhost', 'index.html'));
    await addLink(page, url('evil.test', 'style.css'));
    assert.equal(await hotSwapReloads(page), false);
    await sleep(300);
    const links = await sheets(page);
    assert.equal(links.length, 2, JSON.stringify(links));
    assert.ok(links.includes(url('evil.test', 'style.css')));
    assert.ok(links.some(href => href.startsWith(`${url('localhost', 'style.css')}?${PARAM}=`)));
  }));

  test("blank frames and frames from other origins don't stop the swap", () => withPage(async page => {
    await page.goto(local('index.html'));
    await addFrame(page, { srcdoc: '<p>ad</p>' });
    await addFrame(page, { src: url('evil.test', 'nocss.html') });
    assert.equal(await hotSwapReloads(page), false);
    await sleep(300);
    const links = await sheets(page);
    assert.equal(links.length, 1, JSON.stringify(links));
    assert.ok(links[0].includes(`${PARAM}=`));
  }));

  test('the script counts only loopback hosts as this machine', () => {
    const loopback = new RegExp(HOT_SWAP.match(/\/(\^\(localhost.*\$)\/\.test\(url\.hostname\)/)[1]);
    for (const host of ['localhost', '127.0.0.1', '127.1.2.3', '0.0.0.0', '[::1]', '[::]']) {
      assert.ok(loopback.test(host), host);
    }
    for (const host of ['localhost.evil.com', 'evil.com', '127.0.0.1.nip.io', 'foo.localhost', 'example.com', '[::2]']) {
      assert.ok(!loopback.test(host), host);
    }
  });
});

// ---- reloading instead of swapping ----------------------------------------------

describe('reloading instead of swapping', () => {
  test('a page without a stylesheet reloads and keeps its scroll position', () => withPage(async page => {
    await page.goto(local('nocss.html'));
    await scrollTo(page, 1500);
    assert.equal(await hotSwapReloads(page), true);
    await page.evaluate(RESTORE);
    assert.equal(await eventually(() => scrollY(page), 1500), 1500);
  }));

  test("a page whose stylesheets aren't on this machine reloads", () => withPage(async page => {
    await page.goto(url('localhost', 'nocss.html'));
    await addLink(page, url('evil.test', 'style.css'));
    assert.equal(await hotSwapReloads(page), true);
  }));

  test("a page that isn't on this machine reloads and keeps its scroll position", () => withPage(async page => {
    await page.goto(url('evil.test', 'index.html'));
    await scrollTo(page, 900);
    assert.equal(await hotSwapReloads(page), true);
    assert.ok((await sheets(page)).every(href => !href.includes(PARAM)));
    await page.evaluate(RESTORE);
    assert.equal(await eventually(() => scrollY(page), 900), 900);
  }));

  test("a page that isn't on this machine reloads even with a stylesheet from it", () => withPage(async page => {
    await page.goto(url('evil.test', 'nocss.html'));
    await addLink(page, local('style.css'));
    assert.equal(await hotSwapReloads(page), true);
  }));

  test('a page showing a page of its own origin in a frame reloads', () => withPage(async page => {
    await page.goto(local('index.html'));
    await addFrame(page, { src: local('nocss.html') });
    assert.equal(await hotSwapReloads(page), true);
  }));
});
