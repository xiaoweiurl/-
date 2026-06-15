import { NextRequest, NextResponse } from 'next/server';

/**
 * 记忆库统一代理路由
 * 
 * 所有 /api/memory/* 请求代理到 Java 后端 http://localhost:8080/api/memory/*
 * - 支持 GET/POST/PUT/DELETE 全方法
 * - SSE 流式透传（/chat 接口）
 * - FormData 文件上传
 * - Session 传递（复制原始头 + 补充 X-Session-Id）
 * - 响应原样透传
 */

const BACKEND_BASE = process.env.NEXT_PUBLIC_BACKEND_API_URL || 'http://localhost:8080/api';

function getSessionId(request: NextRequest): string | null {
  const header = request.headers.get('x-session-id');
  if (header) return header;
  const cookie = request.cookies.get('session_id')?.value;
  if (cookie) return cookie;
  return null;
}

function buildBackendUrl(pathname: string, search: string): string {
  const memoryPath = pathname.replace('/api/memory', '/memory');
  return `${BACKEND_BASE}${memoryPath}${search}`;
}

function buildHeaders(request: NextRequest): Headers {
  const headers = new Headers();

  const skipHeaders = new Set([
    'host', 'connection', 'content-length', 'transfer-encoding',
    'x-forwarded-for', 'x-forwarded-proto', 'x-forwarded-host',
    'x-real-ip', 'cf-connecting-ip', 'cf-ipcountry', 'cf-ray', 'cf-visitor',
    'x-middleware-request-', 'x-nextjs-data', 'x-invoke-output',
    'x-invoke-path', 'x-invoke-query', 'rsc', 'next-url',
  ]);
  request.headers.forEach((value, key) => {
    const lowerKey = key.toLowerCase();
    if (!skipHeaders.has(lowerKey) && !lowerKey.startsWith('x-middleware')) {
      headers.set(key, value);
    }
  });

  const sessionId = getSessionId(request);
  if (sessionId) {
    headers.set('X-Session-Id', sessionId);
  }

  return headers;
}

// GET - 支持 SSE 流式透传
export async function GET(request: NextRequest) {
  const url = new URL(request.url);
  const isSSE = url.pathname.includes('/chat');
  const backendUrl = buildBackendUrl(url.pathname, url.search);

  const headers = buildHeaders(request);
  if (isSSE) {
    headers.set('Accept', 'text/event-stream');
  }

  try {
    const backendRes = await fetch(backendUrl, {
      headers,
      signal: isSSE ? AbortSignal.timeout(300000) : AbortSignal.timeout(15000),
    });

    if (isSSE) {
      const stream = backendRes.body;
      if (!stream) {
        return NextResponse.json({ error: '无法读取流' }, { status: 500 });
      }

      return new NextResponse(stream, {
        headers: {
          'Content-Type': 'text/event-stream',
          'Cache-Control': 'no-cache',
          'Connection': 'keep-alive',
          'Access-Control-Allow-Origin': request.headers.get('origin') || '*',
          'Access-Control-Allow-Credentials': 'true',
        },
      });
    }

    const text = await backendRes.text();

    return new NextResponse(text, {
      status: backendRes.status,
      headers: { 'Content-Type': 'application/json' },
    });
  } catch (error) {
    console.error('[Memory Proxy] GET error:', error);
    return NextResponse.json({ error: '后端服务不可用' }, { status: 503 });
  }
}

// POST - 支持 JSON 和 FormData
export async function POST(request: NextRequest) {
  const url = new URL(request.url);
  const backendUrl = buildBackendUrl(url.pathname, '');
  const contentType = request.headers.get('content-type') || '';

  const headers = buildHeaders(request);

  try {
    let res: Response;

    if (contentType.includes('multipart/form-data')) {
      const formData = await request.formData();
      res = await fetch(backendUrl, {
        method: 'POST',
        headers,
        body: formData,
        signal: AbortSignal.timeout(60000),
      });
    } else {
      const body = await request.json();
      headers.set('Content-Type', 'application/json');
      res = await fetch(backendUrl, {
        method: 'POST',
        headers,
        body: JSON.stringify(body),
        signal: AbortSignal.timeout(15000),
      });
    }

    const text = await res.text();

    return new NextResponse(text, {
      status: res.status,
      headers: { 'Content-Type': 'application/json' },
    });
  } catch (error) {
    console.error('[Memory Proxy] POST error:', error);
    return NextResponse.json({ error: '后端服务不可用' }, { status: 503 });
  }
}

// PUT
export async function PUT(request: NextRequest) {
  const url = new URL(request.url);
  const backendUrl = buildBackendUrl(url.pathname, '');

  try {
    const body = await request.json();
    const headers = buildHeaders(request);
    headers.set('Content-Type', 'application/json');

    const res = await fetch(backendUrl, {
      method: 'PUT',
      headers,
      body: JSON.stringify(body),
      signal: AbortSignal.timeout(15000),
    });

    const text = await res.text();

    return new NextResponse(text, {
      status: res.status,
      headers: { 'Content-Type': 'application/json' },
    });
  } catch (error) {
    console.error('[Memory Proxy] PUT error:', error);
    return NextResponse.json({ error: '后端服务不可用' }, { status: 503 });
  }
}

// DELETE
export async function DELETE(request: NextRequest) {
  const url = new URL(request.url);
  const backendUrl = buildBackendUrl(url.pathname, '');

  try {
    const res = await fetch(backendUrl, {
      method: 'DELETE',
      headers: buildHeaders(request),
      signal: AbortSignal.timeout(15000),
    });

    const text = await res.text();

    return new NextResponse(text, {
      status: res.status,
      headers: { 'Content-Type': 'application/json' },
    });
  } catch (error) {
    console.error('[Memory Proxy] DELETE error:', error);
    return NextResponse.json({ error: '后端服务不可用' }, { status: 503 });
  }
}
