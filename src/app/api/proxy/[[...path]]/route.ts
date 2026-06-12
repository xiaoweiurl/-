import { NextRequest, NextResponse } from 'next/server';

/**
 * 通用后端 API 代理
 * 所有前端请求通过 /api/proxy/... 转发到 Java 后端
 * 本地: http://localhost:8080/api/...
 * 生产: 由环境变量 NEXT_PUBLIC_BACKEND_API_URL 指定
 * 彻底解决外网映射访问时的 CORS 和 Private Network Access 问题
 */

// 服务端读取后端地址（非 NEXT_PUBLIC_ 前缀，确保只在服务端执行）
const BACKEND_URL = process.env.BACKEND_API_URL || process.env.NEXT_PUBLIC_BACKEND_API_URL || 'http://localhost:8080/api';

async function proxyRequest(request: NextRequest, method: string) {
  try {
    // 构建后端 URL：/api/proxy/auth/login → http://localhost:8080/api/auth/login
    const path = request.nextUrl.pathname.replace('/api/proxy', '');
    const searchParams = request.nextUrl.search;
    const backendUrl = `${BACKEND_URL}${path}${searchParams}`;

    // 复制请求头，过滤掉 Next.js/host 相关的头（避免后端拒绝）
    const headers = new Headers();
    const skipHeaders = new Set([
      'host', 'connection', 'content-length', 'transfer-encoding',
      'x-forwarded-for', 'x-forwarded-proto', 'x-forwarded-host',
      'x-real-ip', 'cf-connecting-ip', 'cf-ipcountry', 'cf-ray', 'cf-visitor',
    ]);
    request.headers.forEach((value, key) => {
      const lowerKey = key.toLowerCase();
      if (!skipHeaders.has(lowerKey)) {
        headers.set(key, value);
      }
    });

    // 设置合理的 Host 头
    try {
      const backendHost = new URL(BACKEND_URL).host;
      headers.set('Host', backendHost);
    } catch {
      // ignore
    }

    // 确保 X-Session-Id 传递（从 cookie 或 header）
    const sessionIdFromCookie = request.cookies.get('session_id')?.value;
    if (sessionIdFromCookie && !headers.has('x-session-id')) {
      headers.set('X-Session-Id', sessionIdFromCookie);
    }

    // 构建请求选项
    const fetchOptions: RequestInit = {
      method,
      headers,
      redirect: 'manual',
      // @ts-expect-error Node.js fetch 支持 signal timeout
      timeout: 30000,
    };

    // 非 GET/HEAD 请求传递 body
    if (method !== 'GET' && method !== 'HEAD') {
      const contentType = request.headers.get('content-type') || '';
      
      if (contentType.includes('multipart/form-data')) {
        // FormData：直接透传原始 body
        fetchOptions.body = await request.arrayBuffer();
      } else if (contentType.includes('application/json') || contentType.includes('text/')) {
        fetchOptions.body = await request.text();
      } else {
        fetchOptions.body = await request.arrayBuffer();
      }
    }

    console.log(`[Proxy] ${method} → ${backendUrl} (backend=${BACKEND_URL})`);

    const backendResponse = await fetch(backendUrl, fetchOptions);

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

    // 处理 SSE 流式响应
    const responseContentType = backendResponse.headers.get('content-type') || '';
    if (responseContentType.includes('text/event-stream')) {
      return new NextResponse(backendResponse.body, {
        status: backendResponse.status,
        headers: responseHeaders,
      });
    }

    // 普通响应
    const responseBody = await backendResponse.arrayBuffer();
    return new NextResponse(responseBody, {
      status: backendResponse.status,
      headers: responseHeaders,
    });
  } catch (error) {
    console.error('[Proxy] 请求转发失败:', error);
    const message = error instanceof Error ? error.message : '代理请求失败';
    return NextResponse.json(
      { success: false, error: message, message: '后端服务不可用，请确认 Java 后端已启动' },
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
