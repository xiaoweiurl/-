import { NextRequest, NextResponse } from 'next/server';
import { getBackendInternalUrl, getSessionId } from '@/lib/backend-proxy';

export async function GET(request: NextRequest) {
  const sessionId = getSessionId(Object.fromEntries(request.headers.entries()));
  const backendUrl = getBackendInternalUrl();
  const { searchParams } = new URL(request.url);
  const queryString = searchParams.toString();
  const path = `/ops/errors${queryString ? '?' + queryString : ''}`;

  try {
    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (sessionId) headers['x-session-id'] = sessionId;

    const res = await fetch(`${backendUrl}${path}`, { headers, signal: AbortSignal.timeout(10000) });
    const data = await res.json();
    return NextResponse.json(data, { status: res.status });
  } catch (error) {
    console.error('[API] /api/ops/errors proxy error:', error);
    return NextResponse.json({ success: false, error: '无法连接运维服务' }, { status: 502 });
  }
}

export async function PATCH(request: NextRequest) {
  const sessionId = getSessionId(Object.fromEntries(request.headers.entries()));
  const backendUrl = getBackendInternalUrl();

  try {
    const body = await request.json();
    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (sessionId) headers['x-session-id'] = sessionId;

    const res = await fetch(`${backendUrl}/ops/errors`, {
      method: 'PATCH',
      headers,
      body: JSON.stringify(body),
      signal: AbortSignal.timeout(10000),
    });
    const data = await res.json();
    return NextResponse.json(data, { status: res.status });
  } catch (error) {
    console.error('[API] /api/ops/errors PATCH proxy error:', error);
    return NextResponse.json({ success: false, error: '无法连接运维服务' }, { status: 502 });
  }
}
