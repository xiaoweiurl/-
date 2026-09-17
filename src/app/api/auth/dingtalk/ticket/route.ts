import { NextRequest, NextResponse } from 'next/server';
import { backendFetch, isBackendAvailable } from '@/lib/backend-proxy';
import { shouldUseSecureCookies } from '@/lib/next-runtime';

/**
 * 打样工作通知 magic ticket 核销：转发 Java POST /auth/dingtalk/ticket，并种 httpOnly session_id Cookie。
 */
export async function POST(request: NextRequest) {
  try {
    const backendAvailable = await isBackendAvailable();
    if (!backendAvailable) {
      return NextResponse.json(
        { success: false, error: '后端服务不可用，请稍后重试' },
        { status: 503 },
      );
    }

    const body = await request.json().catch(() => ({}));
    const ticket = typeof body.ticket === 'string' ? body.ticket.trim() : '';
    if (!ticket) {
      return NextResponse.json({ success: false, error: '缺少打样通知凭证' }, { status: 400 });
    }

    const goodsId = body.goodsId;
    const response = await backendFetch('/auth/dingtalk/ticket', {
      method: 'POST',
      body: { ticket, goodsId },
    });

    const sessionIdFromHeader = response.headers.get('X-Session-Id');
    const result = await response.json().catch(() => ({}));

    if (!response.ok || result.success === false) {
      return NextResponse.json(
        {
          success: false,
          error: result.error || result.message || '打样通知免登失败',
          message: result.message || result.error,
        },
        { status: response.status || 401 },
      );
    }

    const loginData = result.data || result;
    const finalSessionId = sessionIdFromHeader || loginData.sessionId;
    if (!finalSessionId) {
      return NextResponse.json({ success: false, error: '免登响应无效' }, { status: 500 });
    }

    const nextResponse = NextResponse.json({
      success: true,
      message: result.message || '钉钉免登成功',
      data: {
        sessionId: finalSessionId,
        user: loginData.user,
        expiresIn: loginData.expiresIn,
      },
    });

    const cookieSecure = shouldUseSecureCookies({
      cookieSecureEnv: process.env.COOKIE_SECURE,
      forwardedProto: request.headers.get('x-forwarded-proto'),
      requestProtocol: request.nextUrl.protocol,
    });
    const maxAge = Math.max(60, Math.floor((loginData.expiresIn || 24 * 60 * 60 * 1000) / 1000));
    nextResponse.cookies.set('session_id', finalSessionId, {
      httpOnly: true,
      secure: cookieSecure,
      sameSite: 'lax',
      maxAge,
      path: '/',
    });
    return nextResponse;
  } catch (error) {
    console.error('[API] 打样 ticket 免登失败:', error);
    return NextResponse.json(
      { success: false, error: error instanceof Error ? error.message : '打样通知免登失败' },
      { status: 500 },
    );
  }
}
