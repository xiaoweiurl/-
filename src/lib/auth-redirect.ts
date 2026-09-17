/**
 * Login return-path helpers.
 * DingTalk work-notice deep links must survive /login, otherwise the sampler
 * lands on the portal / goods-library list instead of /sampler/{id}.
 */

export const RETURN_URL_QUERY = 'returnUrl';
export const RETURN_URL_STORAGE_KEY = 'login_return_url';

export function samplerFormPath(goodsId: string | number): string {
  return `/sampler/${goodsId}`;
}

/** Relative same-origin path only. Blocks protocol-relative and /login loops. */
export function isSafeReturnPath(path: string | null | undefined): path is string {
  if (!path) return false;
  const trimmed = path.trim();
  if (!trimmed.startsWith('/')) return false;
  if (trimmed.startsWith('//') || trimmed.startsWith('/\\')) return false;
  if (trimmed.includes('://')) return false;
  if (trimmed.includes('\\')) return false;
  if (/[\u0000-\u001f]/.test(trimmed)) return false;
  const pathname = trimmed.split(/[?#]/)[0];
  if (pathname === '/login' || pathname.startsWith('/login/')) return false;
  return true;
}

export function parseReturnPath(raw: string | null | undefined): string | null {
  if (raw == null || raw === '') return null;
  let decoded = raw;
  try {
    decoded = decodeURIComponent(raw);
  } catch {
    decoded = raw;
  }
  return isSafeReturnPath(decoded) ? decoded : null;
}

export function loginHref(returnPath?: string): string {
  const path =
    returnPath ??
    (typeof window !== 'undefined'
      ? `${window.location.pathname}${window.location.search}`
      : '');
  const safe = isSafeReturnPath(path) ? path : null;
  if (!safe) return '/login';
  return `/login?${RETURN_URL_QUERY}=${encodeURIComponent(safe)}`;
}

export function readReturnPath(): string | null {
  if (typeof window === 'undefined') return null;
  const fromQuery = parseReturnPath(
    new URLSearchParams(window.location.search).get(RETURN_URL_QUERY),
  );
  if (fromQuery) {
    sessionStorage.setItem(RETURN_URL_STORAGE_KEY, fromQuery);
    return fromQuery;
  }
  return parseReturnPath(sessionStorage.getItem(RETURN_URL_STORAGE_KEY));
}

export function clearReturnPath(): void {
  if (typeof window === 'undefined') return;
  sessionStorage.removeItem(RETURN_URL_STORAGE_KEY);
}

/** Read and clear so a later portal click cannot reuse a stale sampler URL. */
export function takeReturnPath(): string | null {
  const path = readReturnPath();
  if (path) clearReturnPath();
  return path;
}
