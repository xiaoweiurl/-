import { NextRequest, NextResponse } from 'next/server';

function extractSessionId(request: NextRequest): string | null {
  const xSessionId = request.headers.get('x-session-id');
  if (xSessionId) return xSessionId;
  const cookieHeader = request.headers.get('cookie') || '';
  const match = cookieHeader.match(/session_id=([^;]+)/);
  return match ? match[1] : null;
}

export async function GET(request: NextRequest) {
  const sessionId = extractSessionId(request);

  const backendUrl = process.env.NEXT_PUBLIC_BACKEND_API_URL;
  if (!backendUrl) {
    return NextResponse.json({ success: false, error: '后端 API 未配置' }, { status: 500 });
  }

  try {
    const url = `${backendUrl}/api/ops/metrics`;
    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (sessionId) headers['x-session-id'] = sessionId;

    const res = await fetch(url, { headers });
    const data = await res.json();
    return NextResponse.json(data, { status: res.status });
  } catch (error) {
    console.error('[API] /api/ops/metrics proxy error:', error);
    return NextResponse.json({ success: false, error: '无法连接运维服务' }, { status: 502 });
  }
}
