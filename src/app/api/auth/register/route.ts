import { NextRequest, NextResponse } from 'next/server';
import { backendFetch, isBackendAvailable } from '@/lib/backend-proxy';
import { shouldUseSecureCookies } from '@/lib/next-runtime';

export async function POST(request: NextRequest) {
  try {
    const backendAvailable = await isBackendAvailable();

    if (!backendAvailable) {
      return NextResponse.json(
        { success: false, error: '后端服务不可用' },
        { status: 503 }
      );
    }

    const body = await request.json();

    const response = await backendFetch('/auth/register', {
      method: 'POST',
      body: JSON.stringify(body),
    });

    const payload = await response.json();
    const sessionIdFromHeader = response.headers.get('X-Session-Id');

    if (response.status === 409) {
      const candidates = payload?.data?.candidates || payload?.candidates || [];
      return NextResponse.json(
        {
          success: false,
          error: payload.message || payload.error || '找到多名同名员工',
          candidates,
        },
        { status: 409 }
      );
    }

    if (!response.ok || payload.success === false) {
      return NextResponse.json(
        { success: false, error: payload.error || payload.message || '注册失败' },
        { status: response.status || 400 }
      );
    }

    const loginData = payload.data || payload;
    const finalSessionId = sessionIdFromHeader || loginData.sessionId;
    const user = loginData.user;

    const nextResponse = NextResponse.json({
      success: true,
      message: payload.message || '注册成功',
      data: {
        sessionId: finalSessionId,
        user,
      },
    });

    if (finalSessionId) {
      const cookieSecure = shouldUseSecureCookies({
        cookieSecureEnv: process.env.COOKIE_SECURE,
        forwardedProto: request.headers.get('x-forwarded-proto'),
        requestProtocol: request.nextUrl.protocol,
      });
      const maxAge = 7 * 24 * 60 * 60;
      nextResponse.cookies.set('session_id', finalSessionId, {
        httpOnly: true,
        secure: cookieSecure,
        sameSite: 'lax',
        maxAge,
        path: '/',
      });
    }

    return nextResponse;
  } catch (error) {
    console.error('注册代理错误:', error);
    return NextResponse.json(
      { success: false, error: '注册服务暂时不可用' },
      { status: 500 }
    );
  }
}
