import { NextRequest, NextResponse } from 'next/server';

const BACKEND_API_URL = process.env.NEXT_PUBLIC_BACKEND_API_URL || '';

export async function GET(request: NextRequest) {
  if (!BACKEND_API_URL) {
    return NextResponse.json({ error: '后端API未配置' }, { status: 500 });
  }

  const sessionId = request.headers.get('x-session-id') || '';
  const url = new URL(request.url);
  const { userId, company } = Object.fromEntries(url.searchParams);

  const params = new URLSearchParams();
  if (userId) params.set('userId', userId);
  if (company) params.set('company', company);

  const targetUrl = `${BACKEND_API_URL}/marketing/chat/history?${params.toString()}`;

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    'X-Session-Id': sessionId,
  };
  const cookie = request.headers.get('cookie');
  if (cookie) headers['cookie'] = cookie;

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
  const { userId, company } = Object.fromEntries(url.searchParams);

  const params = new URLSearchParams();
  if (userId) params.set('userId', userId);
  if (company) params.set('company', company);

  const targetUrl = `${BACKEND_API_URL}/marketing/chat/history?${params.toString()}`;

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    'X-Session-Id': sessionId,
  };
  const cookie = request.headers.get('cookie');
  if (cookie) headers['cookie'] = cookie;

  const response = await fetch(targetUrl, { method: 'DELETE', headers });
  const data = await response.json();
  return NextResponse.json(data, { status: response.status });
}
