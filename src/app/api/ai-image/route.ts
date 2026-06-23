import { NextRequest, NextResponse } from 'next/server';

const BACKEND_URL = process.env.NEXT_PUBLIC_BACKEND_API_URL || 'http://localhost:8080/api';

function getSessionId(request: NextRequest): string | undefined {
  const header = request.headers.get('x-session-id');
  if (header) return header;
  const cookie = request.cookies.get('session_id')?.value;
  if (cookie) return cookie;
  return undefined;
}

function buildHeaders(request: NextRequest, contentType?: string): Headers {
  const headers = new Headers();
  const sessionId = getSessionId(request);

  request.headers.forEach((value, key) => {
    const lower = key.toLowerCase();
    if (['host', 'connection', 'content-length', 'accept-encoding'].includes(lower)) return;
    headers.set(key, value);
  });

  if (sessionId) {
    headers.set('X-Session-Id', sessionId);
    headers.set('Cookie', `session_id=${sessionId}`);
  }

  if (contentType) {
    headers.set('Content-Type', contentType);
  } else {
    headers.set('Content-Type', 'application/json');
  }

  try {
    const backendUrl = new URL(BACKEND_URL);
    headers.set('Host', backendUrl.host);
  } catch {}

  return headers;
}

export async function POST(request: NextRequest) {
  try {
    const body = await request.text();
    const contentType = request.headers.get('content-type') || 'application/json';
    const targetUrl = `${BACKEND_URL}/ai-image/generate`;
    const headers = buildHeaders(request, contentType);

    const response = await fetch(targetUrl, {
      method: 'POST',
      headers,
      body,
    });

    const data = await response.text();
    return new NextResponse(data, {
      status: response.status,
      headers: { 'Content-Type': 'application/json' },
    });
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : '代理请求失败';
    return NextResponse.json(
      { success: false, error: '代理请求失败', message, target: `${BACKEND_URL}/ai-image/generate` },
      { status: 502 }
    );
  }
}

export async function GET(request: NextRequest) {
  try {
    const targetUrl = `${BACKEND_URL}/ai-image/models`;
    const headers = buildHeaders(request);

    const response = await fetch(targetUrl, {
      method: 'GET',
      headers,
    });

    const data = await response.text();
    return new NextResponse(data, {
      status: response.status,
      headers: { 'Content-Type': 'application/json' },
    });
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : '代理请求失败';
    return NextResponse.json(
      { success: false, error: '代理请求失败', message, target: `${BACKEND_URL}/ai-image/models` },
      { status: 502 }
    );
  }
}
