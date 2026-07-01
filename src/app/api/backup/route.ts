import { NextRequest, NextResponse } from 'next/server';
import { getBackendInternalUrl, getSessionId } from '@/lib/backend-proxy';

export async function GET(request: NextRequest) {
  const sessionId = getSessionId(Object.fromEntries(request.headers.entries()));
  const backendUrl = getBackendInternalUrl();
  const { searchParams } = new URL(request.url);
  const queryString = searchParams.toString();
  const path = `/backup${queryString ? '?' + queryString : ''}`;

  try {
    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (sessionId) headers['x-session-id'] = sessionId;

    const res = await fetch(`${backendUrl}${path}`, { headers, signal: AbortSignal.timeout(10000) });
    const data = await res.json();
    return NextResponse.json(data, { status: res.status });
  } catch (error) {
    console.error('[API] /api/backup proxy error:', error);
    return NextResponse.json({ success: false, error: '无法连接备份服务' }, { status: 502 });
  }
}

export async function POST(request: NextRequest) {
  const sessionId = getSessionId(Object.fromEntries(request.headers.entries()));
  const backendUrl = getBackendInternalUrl();

  try {
    const body = await request.json();
    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (sessionId) headers['x-session-id'] = sessionId;

    const res = await fetch(`${backendUrl}/backup`, {
      method: 'POST',
      headers,
      body: JSON.stringify(body),
      signal: AbortSignal.timeout(30000),
    });
    const data = await res.json();
    return NextResponse.json(data, { status: res.status });
  } catch (error) {
    console.error('[API] /api/backup POST proxy error:', error);
    return NextResponse.json({ success: false, error: '无法连接备份服务' }, { status: 502 });
  }
}

export async function DELETE(request: NextRequest) {
  const sessionId = getSessionId(Object.fromEntries(request.headers.entries()));
  const backendUrl = getBackendInternalUrl();
  const { searchParams } = new URL(request.url);
  const backupId = searchParams.get('id');

  if (!backupId) {
    return NextResponse.json({ success: false, error: '缺少备份ID' }, { status: 400 });
  }

  try {
    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (sessionId) headers['x-session-id'] = sessionId;

    const res = await fetch(`${backendUrl}/backup/${backupId}`, {
      method: 'DELETE',
      headers,
      signal: AbortSignal.timeout(10000),
    });
    const data = await res.json();
    return NextResponse.json(data, { status: res.status });
  } catch (error) {
    console.error('[API] /api/backup DELETE proxy error:', error);
    return NextResponse.json({ success: false, error: '无法连接备份服务' }, { status: 502 });
  }
}
