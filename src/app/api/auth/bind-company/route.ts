import { NextRequest, NextResponse } from 'next/server';

const BACKEND_API_URL = process.env.BACKEND_API_URL || process.env.NEXT_PUBLIC_BACKEND_API_URL || 'http://localhost:8080/api';

export async function POST(request: NextRequest) {
  try {
    const body = await request.json();
    const { userId, company } = body;

    if (!userId || !company) {
      return NextResponse.json({ success: false, error: '缺少必要参数' }, { status: 400 });
    }

    const sessionId = request.cookies.get('session_id')?.value ||
      request.headers.get('x-session-id') ||
      '';

    const response = await fetch(`${BACKEND_API_URL}/auth/bind-company`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'x-session-id': sessionId,
      },
      body: JSON.stringify({ userId, company }),
    });

    const data = await response.json();
    return NextResponse.json(data, { status: response.status });
  } catch {
    // 后端不可用时，前端 localStorage 仍可工作
    return NextResponse.json({ success: true, message: '降级模式：公司绑定仅保存到本地' });
  }
}
