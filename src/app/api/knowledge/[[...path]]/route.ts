import { NextRequest, NextResponse } from 'next/server';

/**
 * Knowledge API 代理路由
 * 
 * 将前端 /api/knowledge/* 请求转发到 Java 后端的 /knowledge/* 路径。
 */

function getBackendUrl(): string {
  if (process.env.BACKEND_API_URL) return process.env.BACKEND_API_URL;
  if (process.env.NEXT_PUBLIC_BACKEND_API_URL) return process.env.NEXT_PUBLIC_BACKEND_API_URL;
  return 'http://localhost:8080/api';
}

async function proxy(request: NextRequest, method: string) {
  const backendUrl = getBackendUrl();
  const backendPath = request.nextUrl.pathname.replace('/api/knowledge', '/knowledge');
  const targetUrl = `${backendUrl}${backendPath}${request.nextUrl.search}`;

  // ===== Header 构建 =====
  const headers = new Headers();
  const skipHeaders = new Set([
    'host', 'connection', 'content-length', 'transfer-encoding',
    'x-forwarded-for', 'x-forwarded-proto', 'x-forwarded-host',
    'x-real-ip', 'cf-connecting-ip', 'cf-ipcountry', 'cf-ray', 'cf-visitor',
    'x-middleware-request-', 'x-nextjs-data', 'x-invoke-output',
    'x-invoke-path', 'x-invoke-query', 'rsc', 'next-url',
  ]);

  // 1. 复制前端请求头
  request.headers.forEach((value, key) => {
    const lowerKey = key.toLowerCase();
    if (!skipHeaders.has(lowerKey) && !lowerKey.startsWith('x-middleware')) {
      headers.set(key, value);
    }
  });

  // 2. 设置 Host
  try {
    const backendHost = new URL(backendUrl).host;
    headers.set('Host', backendHost);
  } catch { /* ignore */ }

  // 3. 显式从 cookie 读取 session_id 并设置到 X-Session-Id
  const sessionIdFromCookie = request.cookies.get('session_id')?.value;
  if (sessionIdFromCookie && !headers.has('x-session-id')) {
    headers.set('X-Session-Id', sessionIdFromCookie);
  }

  // 4. 显式传递 Cookie header
  if (sessionIdFromCookie) {
    headers.set('Cookie', `session_id=${sessionIdFromCookie}`);
  }

  console.log(`[Knowledge Proxy] ${method} ${targetUrl}`);

  // ===== body 处理 =====
  const fetchOptions: RequestInit = {
    method,
    headers,
    redirect: 'manual',
    signal: AbortSignal.timeout(30000),
  };

  if (method !== 'GET' && method !== 'HEAD') {
    const contentType = request.headers.get('content-type') || '';
    if (contentType.includes('multipart/form-data')) {
      // multipart/form-data: 读取为 ArrayBuffer，保留原始 boundary
      fetchOptions.body = await request.arrayBuffer();
      headers.delete('content-type');
    } else if (contentType.includes('application/json')) {
      fetchOptions.body = await request.text();
      headers.set('content-type', 'application/json');
    } else {
      fetchOptions.body = await request.arrayBuffer();
      if (contentType) headers.set('content-type', contentType);
    }
  }

  try {
    const res = await fetch(targetUrl, fetchOptions);

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
  } catch (error) {
    console.error('[Knowledge Proxy] error:', error);
    return NextResponse.json(
      { success: false, error: '后端服务暂不可用', message: String(error), target: targetUrl },
      { status: 502 }
    );
  }
}

export async function GET(request: NextRequest) { return proxy(request, 'GET'); }
export async function POST(request: NextRequest) { return proxy(request, 'POST'); }
export async function PUT(request: NextRequest) { return proxy(request, 'PUT'); }
export async function DELETE(request: NextRequest) { return proxy(request, 'DELETE'); }
export async function PATCH(request: NextRequest) { return proxy(request, 'PATCH'); }
