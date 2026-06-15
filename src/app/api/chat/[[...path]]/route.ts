import { NextRequest, NextResponse } from 'next/server';

/**
 * Chat API 代理路由
 * 
 * 与 /api/proxy 使用相同的后端探测和URL重写策略：
 * - 自动探测后端可用性
 * - 根据请求来源域名动态替换 localhost:8080 URL
 * - SSE 流式透传
 * - 支持 CORS
 */

// 后端地址探测结果缓存（与 /api/proxy 共享逻辑）
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
  // 总是尝试直连本地后端
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
      return url;
    }
  }

  // 缓存失败结果（短缓存）
  cachedBackendUrl = null;
  lastProbeTime = now;
  return null;
}

// 获取 sessionId
function getSessionId(req: NextRequest): string | null {
  const header = req.headers.get('x-session-id');
  if (header) return header;
  const cookie = req.cookies.get('session_id')?.value;
  if (cookie) return cookie;
  return null;
}

// 根据请求的 Host 头判断当前访问方式
function getReplacementPrefix(request: NextRequest): string {
  const host = request.headers.get('host') || '';
  const isLocal = host.startsWith('localhost') || host.startsWith('127.0.0.1') || host.startsWith('0.0.0.0');

  if (isLocal) {
    return ''; // 本地访问：替换为相对路径
  }

  // 映射域名访问：替换为映射域名
  const hostname = host.split(':')[0];
  const forwardedProto = request.headers.get('x-forwarded-proto');
  const protocol = forwardedProto || 'http';
  return `${protocol}://${hostname}`;
}

// 改写响应体中的 localhost:8080 URL
function rewriteResponseBody(body: string, replacementPrefix: string): string {
  return body.replace(/https?:\/\/(?:localhost|127\.0\.0\.1):8080/g, replacementPrefix);
}

// 构建转发请求头
function buildHeaders(request: NextRequest, contentType?: string | null): Headers {
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

  // 确保 X-Session-Id 存在
  const sessionId = getSessionId(request);
  if (sessionId) {
    headers.set('X-Session-Id', sessionId);
  }

  // SSE请求不传Content-Type
  const accept = request.headers.get('accept') || '';
  if (accept.includes('text/event-stream')) {
    headers.delete('content-type');
  }

  if (contentType) {
    headers.set('Content-Type', contentType);
  }

  // 设置正确的 Host 头
  try {
    const backendUrl = cachedBackendUrl || 'http://localhost:8080/api';
    const backendHost = new URL(backendUrl).host;
    headers.set('Host', backendHost);
  } catch { /* ignore */ }

  return headers;
}

// 判断是否SSE请求
function isSSERequest(request: NextRequest): boolean {
  const url = new URL(request.url);
  return url.pathname.includes('/smart') || url.pathname.includes('/chat/smart');
}

async function proxyRequest(
  request: NextRequest,
  method: string,
  body?: ReadableStream<Uint8Array> | null,
  contentType?: string | null
): Promise<NextResponse> {
  // 自动探测后端
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

  const url = new URL(request.url);
  const pathParts = url.pathname.replace('/api/chat', '');
  const backendPath = '/chat' + pathParts;
  const query = url.searchParams.toString();
  const targetUrl = `${backendUrl}${backendPath}${query ? '?' + query : ''}`;

  const headers = buildHeaders(request, contentType);
  const sse = isSSERequest(request);

  console.log('[Chat Proxy] target:', targetUrl);
  console.log('[Chat Proxy] X-Session-Id:', headers.get('x-session-id') || 'MISSING');

  try {
    const backendRes = await fetch(targetUrl, {
      method,
      headers,
      body: body || undefined,
      signal: AbortSignal.timeout(sse ? 600000 : 30000), // SSE: 10分钟, 普通: 30秒
    });

    if (sse && backendRes.ok) {
      // SSE 流式透传
      const responseHeaders = new Headers();
      // 复制后端响应头（排除不需要的）
      const responseSkipHeaders = new Set(['transfer-encoding', 'connection', 'keep-alive']);
      backendRes.headers.forEach((value, key) => {
        if (!responseSkipHeaders.has(key.toLowerCase())) {
          responseHeaders.set(key, value);
        }
      });
      responseHeaders.set('Content-Type', 'text/event-stream');
      responseHeaders.set('Cache-Control', 'no-cache');
      responseHeaders.set('Connection', 'keep-alive');
      // CORS 头
      if (!responseHeaders.has('access-control-allow-origin')) {
        responseHeaders.set('Access-Control-Allow-Origin', request.headers.get('origin') || '*');
      }
      if (!responseHeaders.has('access-control-allow-credentials')) {
        responseHeaders.set('Access-Control-Allow-Credentials', 'true');
      }

      const readable = new ReadableStream({
        async start(controller) {
          const reader = backendRes.body?.getReader();
          if (!reader) {
            controller.close();
            return;
          }
          try {
            while (true) {
              const { done, value } = await reader.read();
              if (done) break;
              controller.enqueue(value);
            }
          } catch (e) {
            console.error('[Chat Proxy] SSE error:', e);
          } finally {
            controller.close();
          }
        },
      });

      return new NextResponse(readable, {
        status: backendRes.status,
        headers: responseHeaders,
      });
    }

    // 普通响应：根据请求来源动态替换 localhost URL
    const replacementPrefix = getReplacementPrefix(request);
    const responseBody = await backendRes.text();
    const responseContentType = backendRes.headers.get('content-type') || '';

    let finalBody = responseBody;
    if (responseContentType.includes('application/json') || responseContentType.includes('text/')) {
      finalBody = rewriteResponseBody(responseBody, replacementPrefix);
    }

    const responseHeaders = new Headers();
    const responseSkipHeaders = new Set(['transfer-encoding', 'connection', 'keep-alive']);
    backendRes.headers.forEach((value, key) => {
      if (!responseSkipHeaders.has(key.toLowerCase())) {
        responseHeaders.set(key, value);
      }
    });
    responseHeaders.set('Content-Type', responseContentType || 'application/json');
    if (!responseHeaders.has('access-control-allow-origin')) {
      responseHeaders.set('Access-Control-Allow-Origin', request.headers.get('origin') || '*');
    }

    if (replacementPrefix !== '') {
      console.log(`[Chat Proxy] URL替换: localhost:8080 → ${replacementPrefix}`);
    }

    return new NextResponse(finalBody, {
      status: backendRes.status,
      headers: responseHeaders,
    });
  } catch (error) {
    // 清除后端缓存
    cachedBackendUrl = null;
    lastProbeTime = 0;

    console.error('[Chat Proxy] error:', error);
    return NextResponse.json(
      { success: false, error: '代理请求失败', message: String(error) },
      { status: 502 }
    );
  }
}

export async function GET(request: NextRequest) {
  return proxyRequest(request, 'GET');
}

export async function POST(request: NextRequest) {
  const contentType = request.headers.get('content-type');
  return proxyRequest(request, 'POST', request.body, contentType);
}

export async function PUT(request: NextRequest) {
  const contentType = request.headers.get('content-type');
  return proxyRequest(request, 'PUT', request.body, contentType);
}

export async function DELETE(request: NextRequest) {
  return proxyRequest(request, 'DELETE');
}

export async function PATCH(request: NextRequest) {
  const contentType = request.headers.get('content-type');
  return proxyRequest(request, 'PATCH', request.body, contentType);
}
