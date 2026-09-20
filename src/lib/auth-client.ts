/**
 * 认证工具函数
 * 处理 sessionId 的获取和管理
 */

/** 登录/注册/分享等无需带会话拉业务数据的页面 */
export function isPublicAuthPage(pathname?: string): boolean {
  const path =
    pathname ??
    (typeof window !== 'undefined' ? window.location.pathname : '');
  if (!path) return false;
  return (
    path === '/login' ||
    path.startsWith('/login/') ||
    path === '/register' ||
    path.startsWith('/register/') ||
    path === '/forgot-password' ||
    path.startsWith('/forgot-password/') ||
    path.startsWith('/share/')
  );
}

/** 浏览器请求受保护接口时带上的会话头（httpOnly Cookie 仍靠 credentials: 'include'） */
export function getClientSessionHeaders(): Record<string, string> {
  const headers: Record<string, string> = {};
  if (typeof window === 'undefined') return headers;
  const sessionId = localStorage.getItem('session_id');
  if (sessionId) {
    headers['X-Session-Id'] = sessionId;
  }
  return headers;
}

// 获取 sessionId（从 localStorage 获取）
export function getSessionId(): string | null {
  const storedSession = localStorage.getItem('session_id');
  const expires = localStorage.getItem('session_expires');

  if (storedSession && expires) {
    // 检查是否过期
    if (Date.now() < parseInt(expires, 10)) {
      return storedSession;
    } else {
      // 已过期，清除
      clearSession();
    }
  }

  return null;
}

// 设置 sessionId
export function setSessionId(sessionId: string, maxAge: number): void {
  localStorage.setItem('session_id', sessionId);
  localStorage.setItem('session_expires', String(Date.now() + maxAge * 1000));
}

// 清除 session
export function clearSession(): void {
  localStorage.removeItem('session_id');
  localStorage.removeItem('session_expires');
}
