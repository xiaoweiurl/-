import { NextRequest, NextResponse } from 'next/server';

const BACKEND_URL = process.env.NEXT_PUBLIC_BACKEND_API_URL || 'http://localhost:8080/api';

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

export async function GET(request: NextRequest) {
  const sessionId = getSessionId(request);
  if (!sessionId) {
    return NextResponse.json({ error: '未登录' }, { status: 401 });
  }

  const { pathname, search } = new URL(request.url);
  const backendPath = pathname.replace('/api/supply-chain', '/supply-chain');
  const res = await fetch(`${BACKEND_URL}${backendPath}${search}`, {
    headers: {
      'X-Session-Id': sessionId,
      'Content-Type': 'application/json',
    },
  });

  const data = await res.json();
  return NextResponse.json(data, { status: res.status });
}

export async function POST(request: NextRequest) {
  const sessionId = getSessionId(request);
  if (!sessionId) {
    return NextResponse.json({ error: '未登录' }, { status: 401 });
  }

  const contentType = request.headers.get('content-type') || '';

  // 文件上传
  if (contentType.includes('multipart/form-data')) {
    const formData = await request.formData();
    const { pathname } = new URL(request.url);
    const backendPath = pathname.replace('/api/supply-chain', '/supply-chain');

    const res = await fetch(`${BACKEND_URL}${backendPath}`, {
      method: 'POST',
      headers: { 'X-Session-Id': sessionId },
      body: formData,
    });

    const data = await res.json();
    return NextResponse.json(data, { status: res.status });
  }

  // JSON请求
  const body = await request.json();
  const { pathname } = new URL(request.url);
  const backendPath = pathname.replace('/api/supply-chain', '/supply-chain');

  const res = await fetch(`${BACKEND_URL}${backendPath}`, {
    method: 'POST',
    headers: {
      'X-Session-Id': sessionId,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(body),
  });

  const data = await res.json();
  return NextResponse.json(data, { status: res.status });
}

export async function PUT(request: NextRequest) {
  const sessionId = getSessionId(request);
  if (!sessionId) {
    return NextResponse.json({ error: '未登录' }, { status: 401 });
  }

  const body = await request.json();
  const { pathname } = new URL(request.url);
  const backendPath = pathname.replace('/api/supply-chain', '/supply-chain');

  const res = await fetch(`${BACKEND_URL}${backendPath}`, {
    method: 'PUT',
    headers: {
      'X-Session-Id': sessionId,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(body),
  });

  const data = await res.json();
  return NextResponse.json(data, { status: res.status });
}

export async function DELETE(request: NextRequest) {
  const sessionId = getSessionId(request);
  if (!sessionId) {
    return NextResponse.json({ error: '未登录' }, { status: 401 });
  }

  const { pathname } = new URL(request.url);
  const backendPath = pathname.replace('/api/supply-chain', '/supply-chain');

  const res = await fetch(`${BACKEND_URL}${backendPath}`, {
    method: 'DELETE',
    headers: { 'X-Session-Id': sessionId },
  });

  const data = await res.json();
  return NextResponse.json(data, { status: res.status });
}
