import { NextRequest, NextResponse } from 'next/server';

const BACKEND_API_URL = process.env.BACKEND_API_URL || process.env.NEXT_PUBLIC_BACKEND_API_URL || 'http://localhost:8080/api';

async function getSessionHeaders(request: NextRequest): Promise<Record<string, string>> {
  const sessionId = request.headers.get('x-session-id') || '';
  const headers: Record<string, string> = {
    'X-Session-Id': sessionId,
  };
  const cookie = request.headers.get('cookie');
  if (cookie) headers['cookie'] = cookie;
  return headers;
}

export async function POST(request: NextRequest) {
  if (!BACKEND_API_URL) {
    return NextResponse.json({ error: '后端API未配置' }, { status: 500 });
  }

  const headers = await getSessionHeaders(request);
  let body: Record<string, string> = {};
  try {
    body = await request.json();
  } catch {}

  const { message = '' } = body;

  // Forward to backend POST /marketing/chat (SSE stream)
  headers['Content-Type'] = 'application/json';
  headers['Accept'] = 'text/event-stream';

  const targetUrl = `${BACKEND_API_URL}/marketing/chat`;

  const response = await fetch(targetUrl, {
    method: 'POST',
    headers,
    body: JSON.stringify({ message }),
  });

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

  // Non-streaming response
  const data = await response.json();
  return NextResponse.json(data, { status: response.status });
}

export async function GET(request: NextRequest) {
  if (!BACKEND_API_URL) {
    return NextResponse.json({ error: '后端API未配置' }, { status: 500 });
  }

  const headers = await getSessionHeaders(request);
  headers['Content-Type'] = 'application/json';

  const url = new URL(request.url);
  const targetUrl = `${BACKEND_API_URL}/marketing/chat/history${url.search}`;

  const response = await fetch(targetUrl, { method: 'GET', headers });
  const data = await response.json();
  return NextResponse.json(data, { status: response.status });
}

export async function DELETE(request: NextRequest) {
  if (!BACKEND_API_URL) {
    return NextResponse.json({ error: '后端API未配置' }, { status: 500 });
  }

  const headers = await getSessionHeaders(request);
  headers['Content-Type'] = 'application/json';

  const url = new URL(request.url);
  const targetUrl = `${BACKEND_API_URL}/marketing/chat/history${url.search}`;

  const response = await fetch(targetUrl, { method: 'DELETE', headers });
  const data = await response.json();
  return NextResponse.json(data, { status: response.status });
}
