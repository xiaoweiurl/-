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
    const { searchParams } = new URL(request.url);
    const page = searchParams.get('page') || '1';
    const pageSize = searchParams.get('pageSize') || '10';
    const qs = new URLSearchParams({ page, pageSize });

    const url = `${backendUrl}/api/backup?${qs}`;
    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (sessionId) headers['x-session-id'] = sessionId;

    const res = await fetch(url, { headers });
    const data = await res.json();
    return NextResponse.json(data, { status: res.status });
  } catch (error) {
    console.error('[API] /api/ops/backups GET proxy error:', error);
    return NextResponse.json({ success: false, error: '无法连接运维服务' }, { status: 502 });
  }
}

export async function POST(request: NextRequest) {
  const sessionId = extractSessionId(request);
  const backendUrl = process.env.NEXT_PUBLIC_BACKEND_API_URL;
  if (!backendUrl) {
    return NextResponse.json({ success: false, error: '后端 API 未配置' }, { status: 500 });
  }

  try {
    const body = await request.json();
    const url = `${backendUrl}/api/backup`;
    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (sessionId) headers['x-session-id'] = sessionId;

    const res = await fetch(url, { method: 'POST', headers, body: JSON.stringify(body) });
    const data = await res.json();
    return NextResponse.json(data, { status: res.status });
  } catch (error) {
    console.error('[API] /api/ops/backups POST proxy error:', error);
    return NextResponse.json({ success: false, error: '无法连接运维服务' }, { status: 502 });
  }
}
