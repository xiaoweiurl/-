import { NextRequest, NextResponse } from 'next/server';
import { getBackendInternalUrl, getSessionId } from '@/lib/backend-proxy';

export async function GET(request: NextRequest) {
  const sessionId = getSessionId(Object.fromEntries(request.headers.entries()));
  const backendUrl = getBackendInternalUrl();

  try {
    const url = `${backendUrl}/ops/metrics`;
    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (sessionId) headers['x-session-id'] = sessionId;

    const res = await fetch(url, { headers, signal: AbortSignal.timeout(15000) });
    const data = await res.json();
    return NextResponse.json(data, { status: res.status });
  } catch (error) {
    console.error('[API] /api/ops/metrics proxy error:', error);
    return NextResponse.json({
      success: false,
      error: '无法连接运维服务',
      hint: `当前后端地址: ${backendUrl}，请确认Java后端是否运行，或修改 .env.local 中的 NEXT_PUBLIC_BACKEND_API_URL`
    }, { status: 502 });
  }
}
