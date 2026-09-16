/**
 * Next.js custom-server runtime helpers.
 *
 * Company FRP access must run production Next (no Fast Refresh / HMR).
 * Historically `COZE_PROJECT_ENV !== 'PROD'` treated a missing env as
 * development, which made webpack-hmr reconnect and full-page reload
 * every few seconds behind the tunnel.
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

function cozeEnv(env: NodeJS.ProcessEnv): string {
  return (env.COZE_PROJECT_ENV || '').trim().toUpperCase();
}

/**
 * Whether the custom Next server should enable development mode (HMR / Fast Refresh).
 *
 * Production (`false`) when any of:
 * - `NODE_ENV === 'production'`
 * - `COZE_PROJECT_ENV=PROD`
 * - `NEXT_DISABLE_HMR` is truthy
 *
 * Missing `COZE_PROJECT_ENV` is NOT treated as development when `NODE_ENV=production`.
 * `pnpm start` / `start:win` set both production flags. Unspecified env defaults to
 * development so `tsx watch` / `pnpm dev` keep HMR.
 */
export function isNextDevMode(env: NodeJS.ProcessEnv = process.env): boolean {
  const coze = cozeEnv(env);
  const nodeEnv = env.NODE_ENV;
  const disableHmr = envFlag(env.NEXT_DISABLE_HMR) === true;

  if (disableHmr) return false;
  if (nodeEnv === 'production' || coze === 'PROD') return false;
  if (nodeEnv === 'development' || coze === 'DEV') return true;
  return true;
}

/**
 * Hostname passed to `next({ hostname })`.
 *
 * Linux often sets `HOSTNAME` to the machine name; using that for Next HMR
 * makes the client connect to an unresolvable host through FRP.
 * Honor `NEXT_HOSTNAME` / `HOST`, then `HOSTNAME` only when it looks like an
 * explicit bind/public host (IP, localhost, 0.0.0.0, or a dotted name).
 */
export function resolveNextHostname(
  dev: boolean,
  env: NodeJS.ProcessEnv = process.env,
): string {
  const explicit = env.NEXT_HOSTNAME || env.HOST;
  if (explicit && explicit.trim()) return explicit.trim();

  const hostname = (env.HOSTNAME || '').trim();
  if (hostname && isExplicitHostname(hostname)) {
    return hostname;
  }

  return dev ? 'localhost' : '0.0.0.0';
}

function isExplicitHostname(value: string): boolean {
  if (value === '0.0.0.0' || value === '::' || value === 'localhost' || value === '::1') {
    return true;
  }
  if (/^\d{1,3}(\.\d{1,3}){3}$/.test(value)) return true;
  return value.includes('.');
}

/** Default Coze sandbox origin plus comma-separated `ALLOWED_DEV_ORIGINS`. */
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
