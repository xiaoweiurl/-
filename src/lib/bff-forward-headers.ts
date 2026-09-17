/**
 * Headers the BFF must not copy from the browser onto the Java hop.
 *
 * Browser `Origin` / `Referer` / Fetch metadata are CORS signals. Forwarding
 * them makes Spring's CorsFilter treat a same-origin FRP request
 * (`http://ai.bonasoma.com/api/...` → Next → `localhost:8080`) as a forbidden
 * cross-origin call and return **403** before session auth runs.
 *
 * Cookie / X-Session-Id / Authorization are kept (session).
 */
const SKIP_EXACT = new Set([
  'host',
  'connection',
  'content-length',
  'transfer-encoding',
  'origin',
  'referer',
  'referrer',
  'x-forwarded-for',
  'x-forwarded-proto',
  'x-forwarded-host',
  'x-real-ip',
  'cf-connecting-ip',
  'cf-ipcountry',
  'cf-ray',
  'cf-visitor',
  'x-middleware-request',
  'x-nextjs-data',
  'x-invoke-output',
  'x-invoke-path',
  'x-invoke-query',
  'rsc',
  'next-url',
]);

export function shouldSkipBffForwardHeader(name: string): boolean {
  const lower = name.toLowerCase();
  if (SKIP_EXACT.has(lower)) return true;
  if (lower.startsWith('x-middleware')) return true;
  // Fetch metadata / Client Hints are browser-only; they can also trip CORS.
  if (lower.startsWith('sec-fetch-') || lower.startsWith('sec-ch-')) return true;
  return false;
}
