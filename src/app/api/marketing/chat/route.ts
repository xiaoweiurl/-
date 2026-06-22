import { NextRequest, NextResponse } from 'next/server';

const BACKEND_API_URL = process.env.BACKEND_API_URL || process.env.NEXT_PUBLIC_BACKEND_API_URL || 'http://localhost:8080/api';

async function proxyRequest(request: NextRequest, method: string) {
  if (!BACKEND_API_URL) {
    return NextResponse.json({ error: '后端API未配置' }, { status: 500 });
  }

  const url = new URL(request.url);
  const sessionId = request.headers.get('x-session-id') || '';

  // /api/marketing/chat → /marketing/chat
  // /api/marketing/chat/history → /marketing/chat/history
  // /api/marketing/chat/smart → /marketing/chat/smart
  let targetPath = url.pathname.replace('/api/marketing', '/marketing');
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

  if ((method === 'POST' || method === 'PUT' || method === 'PATCH') && request.body) {
    fetchOptions.body = await request.text();
  }

  const response = await fetch(targetUrl, fetchOptions);

  // SSE stream
  const contentType = response.headers.get('content-type') || '';
  if (contentType.includes('text/event-stream')) {
    return new NextResponse(response.body, {
      headers: {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache',
        'Connection': 'keep-alive',
      },
    });
  }

  const data = await response.json();
  return NextResponse.json(data, { status: response.status });
}

export async function POST(request: NextRequest) {
  if (!BACKEND_API_URL) {
    return NextResponse.json({ error: '后端API未配置' }, { status: 500 });
  }

  const sessionId = request.headers.get('x-session-id') || '';
  let body: Record<string, string> = {};
  try {
    body = await request.json();
  } catch {}

  const { message = '', userId = '', company = '' } = body;

  // POST /api/marketing/chat → GET /marketing/chat/smart?message=xxx&userId=xxx&company=xxx
  const params = new URLSearchParams({ message, userId, company });
  const targetUrl = `${BACKEND_API_URL}/marketing/chat/smart?${params.toString()}`;

  const headers: Record<string, string> = {
    'Accept': 'text/event-stream',
    'X-Session-Id': sessionId,
  };

  const response = await fetch(targetUrl, { method: 'GET', headers });

  return new NextResponse(response.body, {
    headers: {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache',
      'Connection': 'keep-alive',
    },
  });
}

export async function GET(request: NextRequest) {
  return proxyRequest(request, 'GET');
}

export async function DELETE(request: NextRequest) {
  return proxyRequest(request, 'DELETE');
}
