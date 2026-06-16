import { NextRequest, NextResponse } from 'next/server';

/**
 * 调试接口：测试知识库代理的 session 传递
 * GET /api/knowledge/debug/session
 */
export async function GET(request: NextRequest) {
  // 收集前端传来的认证信息
  const xSessionId = request.headers.get('X-Session-Id');
  const cookieSessionId = request.cookies.get('session_id')?.value;
  const allCookies = request.cookies.getAll().map(c => `${c.name}=${c.value}`);
  const relevantHeaders: Record<string, string> = {};
  request.headers.forEach((value, key) => {
    if (key.toLowerCase().includes('session') || key.toLowerCase().includes('cookie') || key.toLowerCase().includes('auth')) {
      relevantHeaders[key] = value;
    }
  });

  // 尝试转发到后端测试
  const BACKEND_URL = process.env.NEXT_PUBLIC_BACKEND_API_URL || 'http://localhost:8080/api';
  let backendStatus = 'unknown';
  let backendResponse = '';
  
  try {
    const testHeaders: Record<string, string> = {};
    if (xSessionId) testHeaders['X-Session-Id'] = xSessionId;
    if (cookieSessionId) {
      testHeaders['X-Session-Id'] = testHeaders['X-Session-Id'] || cookieSessionId;
      testHeaders['Cookie'] = `session_id=${cookieSessionId}`;
    }

    const res = await fetch(`${BACKEND_URL}/auth/session`, {
      method: 'GET',
      headers: testHeaders,
      signal: AbortSignal.timeout(5000),
    });
    backendStatus = `${res.status}`;
    backendResponse = await res.text();
  } catch (e) {
    backendStatus = 'error';
    backendResponse = e instanceof Error ? e.message : String(e);
  }

  return NextResponse.json({
    frontend: {
      xSessionId: xSessionId || '(none)',
      cookieSessionId: cookieSessionId || '(none)',
      allCookies,
      relevantHeaders,
    },
    backend: {
      url: `${BACKEND_URL}/auth/session`,
      status: backendStatus,
      response: backendResponse ? backendResponse.substring(0, 500) : '(empty)',
    },
  });
}
