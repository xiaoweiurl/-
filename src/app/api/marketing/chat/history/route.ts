import { NextRequest, NextResponse } from 'next/server';

const BACKEND_API_URL = process.env.NEXT_PUBLIC_BACKEND_API_URL || '';

async function proxyRequest(request: NextRequest, method: string) {
  if (!BACKEND_API_URL) {
    return NextResponse.json({ error: '后端API未配置' }, { status: 500 });
  }

  const url = new URL(request.url);
  const sessionId = request.headers.get('x-session-id') || '';
  const targetPath = url.pathname.replace('/api/marketing', '');
  const targetUrl = `${BACKEND_API_URL}${targetPath}${url.search}`;

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    'X-Session-Id': sessionId,
  };

  const cookie = request.headers.get('cookie');
  if (cookie) headers['cookie'] = cookie;

  const fetchOptions: RequestInit = {
    method,
    headers,
  };

  if (method === 'POST' && request.body) {
    fetchOptions.body = await request.text();
  }

  const response = await fetch(targetUrl, fetchOptions);
  const data = await response.json();
  return NextResponse.json(data, { status: response.status });
}

export async function GET(request: NextRequest) {
  return proxyRequest(request, 'GET');
}

export async function DELETE(request: NextRequest) {
  return proxyRequest(request, 'DELETE');
}
