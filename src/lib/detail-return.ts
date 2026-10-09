import { parseReturnPath } from '@/lib/auth-redirect';

/** Query flag on goods-library and sampler links opened from a chat. */
export const DETAIL_FROM_QUERY = 'from';

export const CHAT_PAGE_RETURN = '/chat';
export const SUPPLY_CHAIN_CHAT_RETURN = '/supply-chain?tab=chat';
export const GOODS_LIBRARY_LIST = '/goods-library';

export type DetailBackAction =
  | { type: 'back' }
  | { type: 'push'; href: string }
  | { type: 'stay' };

export interface DetailBackContext {
  search: string;
  pathname: string;
  referrer: string;
  origin: string;
  historyLength: number;
  /** Where to go when this visit has no in-app history. Null stays put. */
  fallback: string | null;
}

/**
 * Same-origin return path for the in-app back control.
 * Blocks open redirects and path traversal. Chat and supply-chain links use this.
 */
export function detailReturnPath(raw: string | null | undefined): string | null {
  const safe = parseReturnPath(raw);
  if (!safe) return null;
  const pathname = safe.split(/[?#]/)[0];
  if (pathname.includes('..')) return null;
  if (pathname.startsWith('/api') || pathname.startsWith('/_next')) return null;
  return safe;
}

/** Append `from` so a later back control can reopen the chat when history is empty. */
export function withDetailReturn(href: string | null | undefined, returnTo?: string | null): string | null {
  if (!href) return null;
  const from = detailReturnPath(returnTo);
  if (!from) return href;
  let url: URL;
  try {
    url = new URL(href, 'http://n.local');
  } catch {
    return href;
  }
  if (url.origin !== 'http://n.local') return href;
  const fromPath = from.split(/[?#]/)[0];
  if (url.pathname === fromPath) return href;
  url.searchParams.set(DETAIL_FROM_QUERY, from);
  return `${url.pathname}${url.search}${url.hash}`;
}

type ReferrerKind = 'same-app' | 'cross' | 'empty' | 'self';

function referrerKind(referrer: string, origin: string, pathname: string): ReferrerKind {
  if (!referrer) return 'empty';
  try {
    const url = new URL(referrer);
    if (url.origin !== origin) return 'cross';
    if (url.pathname === pathname) return 'self';
    return 'same-app';
  } catch {
    return 'cross';
  }
}

function referrerPath(referrer: string, origin: string): string | null {
  if (!referrer) return null;
  try {
    const url = new URL(referrer);
    if (url.origin !== origin) return null;
    return url.pathname;
  } catch {
    return null;
  }
}

/**
 * One back step from a goods or sampler detail page.
 * A chat link records `from`. If the previous document is that chat, history
 * restores it. If the previous document is the goods list, push the chat instead.
 * List browsing has no `from` and still returns through history, or to the list
 * when this visit has no in-app history.
 */
export function detailBackAction(ctx: DetailBackContext): DetailBackAction {
  const from = detailReturnPath(new URLSearchParams(ctx.search).get(DETAIL_FROM_QUERY));
  const fromPath = from ? from.split(/[?#]/)[0] : null;
  const kind = referrerKind(ctx.referrer, ctx.origin, ctx.pathname);
  const refPath = referrerPath(ctx.referrer, ctx.origin);
  const here = ctx.pathname;

  if (from && fromPath && fromPath !== here && refPath === GOODS_LIBRARY_LIST) {
    return { type: 'push', href: from };
  }
  if (from && fromPath && fromPath !== here && ctx.historyLength > 1 && kind !== 'cross' && (refPath === fromPath || kind === 'empty' || kind === 'self')) {
    return { type: 'back' };
  }
  if (from && fromPath && fromPath !== here && (ctx.historyLength <= 1 || kind === 'cross')) {
    return { type: 'push', href: from };
  }
  if (ctx.historyLength > 1 && kind !== 'cross') {
    return { type: 'back' };
  }
  if (ctx.fallback) return { type: 'push', href: ctx.fallback };
  return { type: 'stay' };
}

export function readDetailBackContext(fallback: string | null): DetailBackContext {
  return {
    search: window.location.search,
    pathname: window.location.pathname,
    referrer: document.referrer,
    origin: window.location.origin,
    historyLength: window.history.length,
    fallback,
  };
}

export function performDetailBack(
  router: { back: () => void; push: (href: string) => void },
  action: DetailBackAction,
): void {
  if (action.type === 'back') router.back();
  else if (action.type === 'push') router.push(action.href);
}
