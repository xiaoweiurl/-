import { NextRequest, NextResponse } from 'next/server';
import { backendFetch } from '@/lib/backend-proxy';

/**
 * 登出接口
 * POST /api/auth/logout
 *
 * 从 cookie 或 X-Session-Id 头获取 sessionId，
 * 调用后端 POST /auth/logout 删除 Redis 中的 session，
 * 然后清除前端 cookie。
 */
export async function POST(request: NextRequest) {
  // 从 cookie 获取 session_id（HttpOnly cookie 在服务端可读）
  let sessionId = request.cookies.get('session_id')?.value;
  // 后备：从请求头获取
  if (!sessionId) {
    sessionId = request.headers.get('X-Session-Id') || '';
  }
  console.log('[API /auth/logout] sessionId:', sessionId ? sessionId.substring(0, 8) + '...' : '不存在');

  // 调用后端登出（POST /auth/logout）
  if (sessionId) {
    try {
      await backendFetch('/auth/logout', {
        method: 'POST',
        headers: { 'X-Session-Id': sessionId },
      });
      console.log('[API /auth/logout] 后端登出成功');
    } catch (error) {
      console.error('[API /auth/logout] 后端登出失败:', error);
    }
  } else {
    console.warn('[API /auth/logout] 未找到 sessionId，跳过后端登出');
  }

  // 清除 cookie
  const response = NextResponse.json({
    success: true,
    message: '已退出登录',
  });
  response.cookies.set('session_id', '', { maxAge: 0, path: '/' });
  response.cookies.set('user_role', '', { maxAge: 0, path: '/' });

  return response;
}
