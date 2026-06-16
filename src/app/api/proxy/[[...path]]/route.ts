import { NextRequest, NextResponse } from 'next/server';

/**
 * 通用后端 API 代理
 * 
 * 职责：将前端 /api/proxy/* 请求转发到 Java 后端，透传请求头和响应。
 */

// 后端地址探测结果缓存
let cachedBackendUrl: string | null = null;
let lastProbeTime = 0;
const PROBE_CACHE_TTL = 60000; // 1分钟缓存

function getBackendCandidates(): string[] {
  const candidates: string[] = [];
  if (process.env.BACKEND_API_URL) {
    candidates.push(process.env.BACKEND_API_URL);
  }
  if (process.env.NEXT_PUBLIC_BACKEND_API_URL) {
    candidates.push(process.env.NEXT_PUBLIC_BACKEND_API_URL);
  }
  candidates.push('http://localhost:8080/api');
  return [...new Set(candidates)];
}

async function probeBackend(url: string): Promise<boolean> {
  try {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 5000);
    await fetch(`${url}/albums`, {
      method: 'GET',
      signal: controller.signal,
      headers: { 'Accept': 'application/json' },
    });
    clearTimeout(timeoutId);
    return true;
  } catch {
    return false;
  }
}

async function getAvailableBackend(): Promise<string | null> {
  const now = Date.now();
  if (cachedBackendUrl && (now - lastProbeTime) < PROBE_CACHE_TTL) {
    return cachedBackendUrl;
  }

  const candidates = getBackendCandidates();
  for (const url of candidates) {
    const available = await probeBackend(url);
    if (available) {
      cachedBackendUrl = url;
      lastProbeTime = now;
      console.log(`[Proxy] 后端可用: ${url}`);
      return url;
    }
    console.log(`[Proxy] 后端不可用: ${url}`);
  }

  console.error('[Proxy] 所有后端地址均不可用');
  return null;
}

async function proxyRequest(request: NextRequest, method: string) {
  const backendUrl = await getAvailableBackend();

  if (!backendUrl) {
    const candidates = getBackendCandidates();
    return NextResponse.json(
      {
        success: false,
        error: `后端服务不可用，已尝试: ${candidates.join(', ')}`,
        message: '后端服务不可用，请确认 Java 后端已启动',
      },
      { status: 502 }
    );
  }

  try {
    const path = request.nextUrl.pathname.replace('/api/proxy', '');
    const searchParams = request.nextUrl.search;
    const targetUrl = `${backendUrl}${path}${searchParams}`;

    // 复制请求头
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

    try {
      const backendHost = new URL(backendUrl).host;
      headers.set('Host', backendHost);
    } catch { /* ignore */ }

    const sessionIdFromCookie = request.cookies.get('session_id')?.value;
    if (sessionIdFromCookie && !headers.has('x-session-id')) {
      headers.set('X-Session-Id', sessionIdFromCookie);
    }

    // 显式传递 cookie（Next.js request.headers 中可能不包含 cookie）
    if (sessionIdFromCookie) {
      headers.set('Cookie', `session_id=${sessionIdFromCookie}`);
    }

    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 30000);

    const fetchOptions: RequestInit = {
      method,
      headers,
      redirect: 'manual',
      signal: controller.signal,
    };

    if (method !== 'GET' && method !== 'HEAD') {
      const contentType = request.headers.get('content-type') || '';
      if (contentType.includes('multipart/form-data')) {
        fetchOptions.body = await request.arrayBuffer();
        headers.delete('content-type');
      } else if (contentType.includes('application/json') || contentType.includes('text/')) {
        fetchOptions.body = await request.text();
      } else {
        fetchOptions.body = await request.arrayBuffer();
      }
    }

    console.log(`[Proxy] ${method} → ${targetUrl}`);

    const backendResponse = await fetch(targetUrl, fetchOptions);
    clearTimeout(timeoutId);

    console.log(`[Proxy] ← ${backendResponse.status} ${backendResponse.statusText}`);

    // 构建响应头
    const responseHeaders = new Headers();
    const responseSkipHeaders = new Set([
      'transfer-encoding', 'connection', 'keep-alive',
    ]);
    backendResponse.headers.forEach((value, key) => {
      const lowerKey = key.toLowerCase();
      if (!responseSkipHeaders.has(lowerKey)) {
        responseHeaders.set(key, value);
      }
    });

    // SSE 流式响应：透传流
    const responseContentType = backendResponse.headers.get('content-type') || '';
    if (responseContentType.includes('text/event-stream')) {
      responseHeaders.set('Cache-Control', 'no-cache');
      responseHeaders.set('Connection', 'keep-alive');
      if (!responseHeaders.has('access-control-allow-origin')) {
        responseHeaders.set('Access-Control-Allow-Origin', request.headers.get('origin') || '*');
      }
      if (!responseHeaders.has('access-control-allow-credentials')) {
        responseHeaders.set('Access-Control-Allow-Credentials', 'true');
      }
      return new NextResponse(backendResponse.body, {
        status: backendResponse.status,
        headers: responseHeaders,
      });
    }

    // 普通响应：直接透传
    const responseBody = await backendResponse.arrayBuffer();
    return new NextResponse(responseBody, {
      status: backendResponse.status,
      headers: responseHeaders,
    });
  } catch (error) {
    cachedBackendUrl = null;
    lastProbeTime = 0;

    const errMsg = error instanceof Error ? error.message : '代理请求失败';
    console.error(`[Proxy] 请求转发失败:`, errMsg);
    return NextResponse.json(
      {
        success: false,
        error: `后端请求失败: ${errMsg}`,
        message: '后端服务不可用，请确认 Java 后端已启动',
      },
      { status: 502 }
    );
  }
}

export async function GET(request: NextRequest) {
  return proxyRequest(request, 'GET');
}

export async function POST(request: NextRequest) {
  return proxyRequest(request, 'POST');
}

export async function PUT(request: NextRequest) {
  return proxyRequest(request, 'PUT');
}

export async function PATCH(request: NextRequest) {
  return proxyRequest(request, 'PATCH');
}

export async function DELETE(request: NextRequest) {
  return proxyRequest(request, 'DELETE');
}
