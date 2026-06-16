import { NextRequest, NextResponse } from 'next/server';

const BACKEND_URL = process.env.NEXT_PUBLIC_BACKEND_API_URL || 'http://localhost:8080/api';

function getSessionId(request: NextRequest): string | null {
  const header = request.headers.get('x-session-id');
  if (header) return header;
  const cookie = request.cookies.get('session_id')?.value;
  if (cookie) return cookie;
  return null;
}

function buildHeaders(request: NextRequest): Record<string, string> {
  const headers: Record<string, string> = {};
  const skipHeaders = new Set([
    'host', 'connection', 'content-length', 'transfer-encoding',
    'content-type', // fetch 发送 FormData 时会自动计算 boundary，不能手动传旧值
    'accept-encoding',
    'x-forwarded-for', 'x-forwarded-proto', 'x-forwarded-host',
    'x-real-ip', 'cf-connecting-ip', 'cf-ipcountry', 'cf-ray', 'cf-visitor',
    'x-middleware-request-', 'x-nextjs-data', 'x-invoke-output',
    'x-invoke-path', 'x-invoke-query', 'rsc', 'next-url',
  ]);
  request.headers.forEach((value, key) => {
    const lowerKey = key.toLowerCase();
    if (!skipHeaders.has(lowerKey) && !lowerKey.startsWith('x-middleware')) {
      headers[key] = value;
    }
  });

  const sessionId = getSessionId(request);
  if (sessionId) {
    headers['X-Session-Id'] = sessionId;
  }

  return headers;
}

async function proxy(request: NextRequest, method: string) {
  const backendPath = request.nextUrl.pathname.replace('/api/knowledge', '/knowledge');
  const url = BACKEND_URL + backendPath + request.nextUrl.search;

  const headers = buildHeaders(request);

  let body: BodyInit | undefined;
  if (method !== 'GET' && method !== 'HEAD') {
    const ct = request.headers.get('content-type') || '';
    if (ct.includes('multipart/form-data')) {
      body = await request.formData();
    } else if (ct.includes('application/json')) {
      body = await request.text();
      headers['content-type'] = 'application/json';
    } else {
      body = await request.arrayBuffer();
      if (ct) headers['content-type'] = ct;
    }
  }

  try {
    const res = await fetch(url.toString(), {
      method,
      headers,
      body,
      cache: 'no-store',
    });

    const responseHeaders = new Headers();
    res.headers.forEach((value, key) => {
      if (!['content-encoding', 'transfer-encoding'].includes(key.toLowerCase())) {
        responseHeaders.set(key, value);
      }
    });

    return new NextResponse(res.body, {
      status: res.status,
      statusText: res.statusText,
      headers: responseHeaders,
    });
  } catch {
    return NextResponse.json({ error: '后端服务暂不可用' }, { status: 502 });
  }
}

export async function GET(request: NextRequest) { return proxy(request, 'GET'); }
export async function POST(request: NextRequest) { return proxy(request, 'POST'); }
export async function PUT(request: NextRequest) { return proxy(request, 'PUT'); }
export async function PATCH(request: NextRequest) { return proxy(request, 'PATCH'); }
export async function DELETE(request: NextRequest) { return proxy(request, 'DELETE'); }
