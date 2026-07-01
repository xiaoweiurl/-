import { NextRequest, NextResponse } from 'next/server';
import { getBackendInternalUrl, getSessionId } from '@/lib/backend-proxy';

export async function GET(request: NextRequest) {
  const sessionId = getSessionId(Object.fromEntries(request.headers.entries()));
  const backendUrl = getBackendInternalUrl();

  try {
    const { searchParams } = new URL(request.url);
    const page = searchParams.get('page') || '1';
    const pageSize = searchParams.get('pageSize') || '30';
    const url = `${backendUrl}/audit?page=${page}&pageSize=${pageSize}`;

    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (sessionId) headers['x-session-id'] = sessionId;

    const res = await fetch(url, { headers, signal: AbortSignal.timeout(15000) });
    const data = await res.json();
    return NextResponse.json(data, { status: res.status });
  } catch (error) {
    console.error('[API] /api/audit proxy error:', error);
    return NextResponse.json({
      success: false,
      error: '无法连接审计服务',
      hint: `当前后端地址: ${backendUrl}，请确认Java后端是否运行`
    }, { status: 502 });
  }
}
