import { NextRequest, NextResponse } from 'next/server';

const BACKEND_URL = process.env.NEXT_PUBLIC_BACKEND_API_URL || 'http://localhost:8080';

async function proxy(request: NextRequest, method: string) {
  const segments = request.nextUrl.pathname.replace('/api/knowledge', '').split('/').filter(Boolean);
  const backendPath = '/knowledge' + (segments.length > 0 ? '/' + segments.join('/') : '');
  const url = new URL(backendPath + request.nextUrl.search, BACKEND_URL);

  const headers: Record<string, string> = {};
  const skipHeaders = new Set([
    'host', 'connection', 'content-length', 'cookie',
    'accept-encoding', 'transfer-encoding', 'content-type'
  ]);
  request.headers.forEach((value, key) => {
    if (!skipHeaders.has(key.toLowerCase())) {
      headers[key] = value;
    }
  });

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
