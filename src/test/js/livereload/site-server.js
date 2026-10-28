// Serves the test pages in site/ on 127.0.0.1, on a free port. `override` swaps a
// file's content for the rest of the run without touching the file on disk.
import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { extname } from 'node:path';
import { fileURLToPath } from 'node:url';

const TYPES = { '.html': 'text/html; charset=utf-8', '.css': 'text/css; charset=utf-8' };

export async function startSite(root) {
  const overrides = new Map();
  const server = createServer(async (request, response) => {
    // The URL parser drops `..` segments, so the path stays inside root.
    const path = new URL(request.url, 'http://site').pathname;
    try {
      const body = overrides.get(path) ?? await readFile(fileURLToPath(new URL(`.${path}`, root)));
      response.writeHead(200, { 'Content-Type': TYPES[extname(path)] ?? 'application/octet-stream' });
      response.end(body);
    } catch {
      response.writeHead(404).end();
    }
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  return {
    port: server.address().port,
    override: (path, body) => overrides.set(path, body),
    reset: () => overrides.clear(),
    close: () => new Promise(resolve => {
      server.closeAllConnections();
      server.close(resolve);
    }),
  };
}
