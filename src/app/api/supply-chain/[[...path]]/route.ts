import { NextRequest, NextResponse } from 'next/server';

const getBackendUrl = () => process.env.NEXT_PUBLIC_BACKEND_API_URL || 'http://localhost:8080/api';

function getSessionId(request: NextRequest): string | null {
  const headerSession = request.headers.get('x-session-id');
  if (headerSession) return headerSession;
  const cookies = request.headers.get('cookie');
  if (cookies) {
    const match = cookies.match(/session_id=([^;]+)/);
    if (match) return match[1];
  }
  return null;
}

// ====== 路由处理：纯代理到 Java 后端 ======

export async function GET(request: NextRequest) {
  const sessionId = getSessionId(request);
  if (!sessionId) {
    return NextResponse.json({ error: '未登录' }, { status: 401 });
  }

  const url = new URL(request.url);
  const { pathname, search } = url;
  const path = pathname.replace('/api/supply-chain', '');

  try {
    const backendRes = await fetch(`${getBackendUrl()}/supply-chain${path}${search}`, {
      headers: { 'X-Session-Id': sessionId, 'Content-Type': 'application/json' },
      signal: AbortSignal.timeout(10000),
    });
    const data = await backendRes.json();
    return NextResponse.json(data, { status: backendRes.status });
  } catch {
    return NextResponse.json({ error: '后端服务不可用' }, { status: 503 });
  }
}

export async function POST(request: NextRequest) {
  const sessionId = getSessionId(request);
  if (!sessionId) {
    return NextResponse.json({ error: '未登录' }, { status: 401 });
  }

  const { pathname } = new URL(request.url);
  const path = pathname.replace('/api/supply-chain', '');

  try {
    const contentType = request.headers.get('content-type') || '';
    if (contentType.includes('multipart/form-data')) {
      const formData = await request.formData();
      const res = await fetch(`${getBackendUrl()}/supply-chain${path}`, {
        method: 'POST',
        headers: { 'X-Session-Id': sessionId },
        body: formData,
        signal: AbortSignal.timeout(30000),
      });
      const data = await res.json();
      return NextResponse.json(data, { status: res.status });
    }
    const body = await request.json();
    const res = await fetch(`${getBackendUrl()}/supply-chain${path}`, {
      method: 'POST',
      headers: { 'X-Session-Id': sessionId, 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
      signal: AbortSignal.timeout(10000),
    });
    const data = await res.json();
    return NextResponse.json(data, { status: res.status });
  } catch {
    return NextResponse.json({ error: '后端服务不可用' }, { status: 503 });
  }
}

export async function PUT(request: NextRequest) {
  const sessionId = getSessionId(request);
  if (!sessionId) {
    return NextResponse.json({ error: '未登录' }, { status: 401 });
  }

  const { pathname } = new URL(request.url);
  const path = pathname.replace('/api/supply-chain', '');

  try {
    const body = await request.json();
    const res = await fetch(`${getBackendUrl()}/supply-chain${path}`, {
      method: 'PUT',
      headers: { 'X-Session-Id': sessionId, 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
      signal: AbortSignal.timeout(10000),
    });
    const data = await res.json();
    return NextResponse.json(data, { status: res.status });
  } catch {
    return NextResponse.json({ error: '后端服务不可用' }, { status: 503 });
  }
}

export async function DELETE(request: NextRequest) {
  const sessionId = getSessionId(request);
  if (!sessionId) {
    return NextResponse.json({ error: '未登录' }, { status: 401 });
  }

  const { pathname } = new URL(request.url);
  const path = pathname.replace('/api/supply-chain', '');

  try {
    const res = await fetch(`${getBackendUrl()}/supply-chain${path}`, {
      method: 'DELETE',
      headers: { 'X-Session-Id': sessionId },
      signal: AbortSignal.timeout(10000),
    });
    const data = await res.json();
    return NextResponse.json(data, { status: res.status });
  } catch {
    return NextResponse.json({ error: '后端服务不可用' }, { status: 503 });
  }
}
