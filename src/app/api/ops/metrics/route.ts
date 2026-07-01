import { NextRequest, NextResponse } from 'next/server';
import { getBackendInternalUrl, getSessionId } from '@/lib/backend-proxy';

export async function GET(request: NextRequest) {
  const sessionId = getSessionId(Object.fromEntries(request.headers.entries()));
  const backendUrl = getBackendInternalUrl();

  try {
    const url = `${backendUrl}/ops/metrics`;
    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (sessionId) headers['x-session-id'] = sessionId;

    const res = await fetch(url, { headers, signal: AbortSignal.timeout(10000) });
    const data = await res.json();
    return NextResponse.json(data, { status: res.status });
  } catch (error) {
    console.error('[API] /api/ops/metrics proxy error:', error);
    return NextResponse.json({ success: false, error: '无法连接运维服务' }, { status: 502 });
  }
}
