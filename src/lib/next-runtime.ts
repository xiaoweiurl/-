/**
 * Next.js custom-server runtime helpers.
 *
 * Client HMR / Fast Refresh is permanently off: the custom server always
 * starts Next with `dev: false`. File-watch process restarts (`tsx watch`)
 * are also not used. Code changes require `pnpm run build` and a manual
 * frontend restart. This stops webpack-hmr reconnect loops (full page
 * reload every few seconds) on FRP and locally.
 *
 * Historically `COZE_PROJECT_ENV !== 'PROD'` treated a missing env as
 * development and enabled HMR.
 */

const TRUTHY = new Set(['1', 'true', 'yes', 'on']);
const FALSY = new Set(['0', 'false', 'no', 'off']);

function envFlag(value: string | undefined): boolean | undefined {
  if (value == null || value.trim() === '') return undefined;
  const normalized = value.trim().toLowerCase();
  if (TRUTHY.has(normalized)) return true;
  if (FALSY.has(normalized)) return false;
  return undefined;
}

/**
 * Always `false`: Next must never enable Fast Refresh / webpack-hmr.
 * Env flags cannot turn HMR back on (including NODE_ENV=development).
 */
export function isNextDevMode(_env: NodeJS.ProcessEnv = process.env): boolean {
  return false;
}

/**
 * Hostname passed to `next({ hostname })`.
 *
 * Linux often sets `HOSTNAME` to the machine name. Honor `NEXT_HOSTNAME` /
 * `HOST`, then `HOSTNAME` only when it looks like an explicit bind/public
 * host (IP, localhost, 0.0.0.0, or a dotted name).
 */
export function resolveNextHostname(
  _dev: boolean,
  env: NodeJS.ProcessEnv = process.env,
): string {
  const explicit = env.NEXT_HOSTNAME || env.HOST;
  if (explicit && explicit.trim()) return explicit.trim();

  const hostname = (env.HOSTNAME || '').trim();
  if (hostname && isExplicitHostname(hostname)) {
    return hostname;
  }

  return '0.0.0.0';
}

function isExplicitHostname(value: string): boolean {
  if (value === '0.0.0.0' || value === '::' || value === 'localhost' || value === '::1') {
    return true;
  }
  if (/^\d{1,3}(\.\d{1,3}){3}$/.test(value)) return true;
  return value.includes('.');
}

/** Coze sandbox origin plus comma-separated `ALLOWED_DEV_ORIGINS` (unused while HMR is off). */
export function parseAllowedDevOrigins(
  env: NodeJS.ProcessEnv = process.env,
): string[] {
  const defaults = ['*.dev.coze.site'];
  const extra = (env.ALLOWED_DEV_ORIGINS || '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean);
  return [...new Set([...defaults, ...extra])];
}

/**
 * Session cookie `Secure` flag.
 *
 * Do **not** key this off `NODE_ENV=production` alone: intranet FRP is often
 * HTTP, and Secure cookies would be dropped by the browser (login regression).
 *
 * Resolution order:
 * 1. explicit `COOKIE_SECURE=true|false`
 * 2. `X-Forwarded-Proto` / request protocol is https
 * 3. otherwise false (HTTP)
 */
export function shouldUseSecureCookies(opts: {
  cookieSecureEnv?: string;
  forwardedProto?: string | null;
  requestProtocol?: string | null;
}): boolean {
  const explicit = envFlag(opts.cookieSecureEnv);
  if (explicit !== undefined) return explicit;

  const forwarded = (opts.forwardedProto || '').split(',')[0].trim().toLowerCase();
  if (forwarded === 'https') return true;

  const proto = (opts.requestProtocol || '').replace(/:$/, '').trim().toLowerCase();
  return proto === 'https';
}
