import { NextRequest, NextResponse } from 'next/server';

const BACKEND_API_URL = process.env.BACKEND_API_URL || process.env.NEXT_PUBLIC_BACKEND_API_URL || 'http://localhost:8080/api';

export async function GET(request: NextRequest) {
  if (!BACKEND_API_URL) {
    return NextResponse.json({ error: '后端API未配置' }, { status: 500 });
  }

  const sessionId = request.headers.get('x-session-id') || '';
  const url = new URL(request.url);

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    'X-Session-Id': sessionId,
  };
  const cookie = request.headers.get('cookie');
  if (cookie) headers['cookie'] = cookie;

  const targetUrl = `${BACKEND_API_URL}/marketing/chat/history${url.search}`;

  const response = await fetch(targetUrl, { method: 'GET', headers });
  const data = await response.json();
  return NextResponse.json(data, { status: response.status });
}

export async function DELETE(request: NextRequest) {
  if (!BACKEND_API_URL) {
    return NextResponse.json({ error: '后端API未配置' }, { status: 500 });
  }

  const sessionId = request.headers.get('x-session-id') || '';
  const url = new URL(request.url);

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    'X-Session-Id': sessionId,
  };
  const cookie = request.headers.get('cookie');
  if (cookie) headers['cookie'] = cookie;

  const targetUrl = `${BACKEND_API_URL}/marketing/chat/history${url.search}`;

  const response = await fetch(targetUrl, { method: 'DELETE', headers });
  const data = await response.json();
  return NextResponse.json(data, { status: response.status });
}
