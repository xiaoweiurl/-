/**
 * BFF 转发用的会话解析。
 *
 * 登录入口（/api/auth/login）把 session_id 种为 httpOnly Cookie，这是同源请求的权威来源。
 * 部分前端仍会把 localStorage 的 session_id 写成 X-Session-Id；若 header 优先，
 * 过期/失效的 localStorage 会覆盖仍有效的 Cookie，Java 直接 401。
 *
 * 规则：Cookie 与 header 都有且不一致 → 用 Cookie；仅 header（curl / 非浏览器）→ 用 header。
 */
export function resolveBffSessionId(
  headerSession: string | null | undefined,
  cookieSession: string | null | undefined,
): string | null {
  const cookie = cookieSession?.trim() || '';
  const header = headerSession?.trim() || '';
  if (cookie && header && cookie !== header) {
    console.warn('[BFF] X-Session-Id 与 session_id Cookie 不一致，优先使用 Cookie');
  }
  return cookie || header || null;
}
