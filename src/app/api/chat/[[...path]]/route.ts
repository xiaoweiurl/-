import { NextRequest, NextResponse } from 'next/server';

// Java 后端配置
const BACKEND_HOST = process.env.NEXT_PUBLIC_BACKEND_HOST || 'localhost:8080';
const backendUrl = `http://${BACKEND_HOST}/api`;

// 获取 sessionId
function getSessionId(req: NextRequest): string | null {
  // 1. 从请求头获取
  const header = req.headers.get('x-session-id');
  if (header) return header;
  // 2. 从 cookie 获取（兜底）
  const cookie = req.cookies.get('session_id')?.value;
  if (cookie) return cookie;
  return null;
}

// 构建转发请求头（与 /api/proxy 完全一致：复制原始头 + 补充session）
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

  // 确保 X-Session-Id 存在（Headers.set 大小写不敏感，同名会被覆盖）
  const sessionId = getSessionId(request);
  if (sessionId) {
    headers.set('X-Session-Id', sessionId);
  }

  // SSE请求不传Content-Type
  const accept = request.headers.get('accept') || '';
  if (accept.includes('text/event-stream')) {
    headers.delete('content-type');
  }

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
  const url = new URL(request.url);
  const pathParts = url.pathname.replace('/api/chat', '');
  const backendPath = '/chat' + pathParts;
  const query = url.searchParams.toString();
  const targetUrl = `${backendUrl}${backendPath}${query ? '?' + query : ''}`;

  const headers = buildHeaders(request);
  const sse = isSSERequest(request);

  if (contentType) {
    headers.set('Content-Type', contentType);
  }

  console.log('[Chat Proxy] target:', targetUrl);
  console.log('[Chat Proxy] X-Session-Id:', headers.get('x-session-id') || 'MISSING');

  try {
    const backendRes = await fetch(targetUrl, {
      method,
      headers,
      body: body || undefined,
      signal: AbortSignal.timeout(sse ? 180000 : 30000),
    });

    if (sse && backendRes.ok) {
      // SSE 流式透传
      const responseHeaders = new Headers(backendRes.headers);
      responseHeaders.set('Content-Type', 'text/event-stream');
      responseHeaders.set('Cache-Control', 'no-cache');
      responseHeaders.set('Connection', 'keep-alive');
      responseHeaders.set('Access-Control-Allow-Origin', '*');

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

    const responseBody = await backendRes.text();
    const responseHeaders = new Headers();
    responseHeaders.set('Content-Type', 'application/json');
    responseHeaders.set('Access-Control-Allow-Origin', '*');

    return new NextResponse(responseBody, {
      status: backendRes.status,
      headers: responseHeaders,
    });
  } catch (error) {
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
