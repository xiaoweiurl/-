import { NextRequest, NextResponse } from 'next/server';

/**
 * 通用后端 API 代理
 * 所有前端请求通过 /api/proxy/... 转发到 Java 后端 http://localhost:8080/api/...
 * 彻底解决外网映射访问时的 CORS 和 Private Network Access 问题
 */

const BACKEND_URL = process.env.NEXT_PUBLIC_BACKEND_API_URL || 'http://localhost:8080/api';

async function proxyRequest(request: NextRequest, method: string) {
  try {
    // 构建后端 URL：/api/proxy/auth/login → http://localhost:8080/api/auth/login
    const path = request.nextUrl.pathname.replace('/api/proxy', '');
    const searchParams = request.nextUrl.search;
    const backendUrl = `${BACKEND_URL}${path}${searchParams}`;

    // 复制请求头，过滤掉 Next.js 特有的头
    const headers = new Headers();
    const skipHeaders = ['host', 'connection', 'content-length', 'transfer-encoding'];
    request.headers.forEach((value, key) => {
      const lowerKey = key.toLowerCase();
      if (!skipHeaders.includes(lowerKey)) {
        headers.set(key, value);
      }
    });

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

    console.log(`[Proxy] ${method} ${backendUrl}`);

    const backendResponse = await fetch(backendUrl, fetchOptions);

    // 构建响应头
    const responseHeaders = new Headers();
    const responseSkipHeaders = ['transfer-encoding', 'connection', 'keep-alive'];
    backendResponse.headers.forEach((value, key) => {
      const lowerKey = key.toLowerCase();
      if (!responseSkipHeaders.includes(lowerKey)) {
        responseHeaders.set(key, value);
      }
    });

    // 处理 SSE 流式响应
    const responseContentType = backendResponse.headers.get('content-type') || '';
    if (responseContentType.includes('text/event-stream')) {
      // SSE 响应：直接透传流
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
      { success: false, error: message, message: '后端服务不可用' },
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
