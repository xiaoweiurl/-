/**
 * 认证工具函数
 * 处理 sessionId 的获取和管理
 */

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
