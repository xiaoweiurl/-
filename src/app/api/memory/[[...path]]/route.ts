import { NextRequest, NextResponse } from 'next/server';

/**
 * 记忆库统一代理路由
 * 
 * 所有 /api/memory/* 请求代理到 Java 后端 http://localhost:8080/api/memory/*
 * - 支持 GET/POST/PUT/DELETE 全方法
 * - SSE 流式透传（/chat 接口）
 * - FormData 文件上传
 * - Session 传递
 * - 响应中 localhost:8080 URL 动态替换（映射访问时）
 */

const BACKEND_BASE = process.env.NEXT_PUBLIC_BACKEND_API_URL || 'http://localhost:8080/api';

function getSessionId(request: NextRequest): string | null {
  // 1. 从请求头获取（前端直接传的 X-Session-Id）
  const headerSession = request.headers.get('x-session-id');
  if (headerSession) return headerSession;
  // 2. 从 cookie 获取（兜底）
  const cookie = request.cookies.get('session_id')?.value;
  if (cookie) return cookie;
  return null;
}

/**
 * 根据请求来源判断是否需要替换 localhost URL
 * 本地访问: localhost/127.0.0.1 → 替换为相对路径
 * 映射访问: 其他域名 → 替换为映射域名
 */
function getUrlReplacement(request: NextRequest): { pattern: RegExp; replacement: string } | null {
  const host = request.headers.get('host') || '';
  const isLocal = host.startsWith('localhost') || host.startsWith('127.0.0.1');

  if (isLocal) {
    // 本地: http://localhost:8080/api/uploads/xxx → /api/uploads/xxx
    return {
      pattern: /http:\/\/localhost:8080/g,
      replacement: '',
    };
  } else {
    // 映射: http://localhost:8080/api/uploads/xxx → http://域名/api/uploads/xxx
    const origin = request.headers.get('origin') || `http://${host}`;
    return {
      pattern: /http:\/\/localhost:8080/g,
      replacement: origin,
    };
  }
}

/**
 * 替换响应体中的 localhost:8080 URL
 */
function rewriteBody(body: string, request: NextRequest): string {
  const repl = getUrlReplacement(request);
  if (!repl) return body;
  return body.replace(repl.pattern, repl.replacement);
}

/**
 * 构建后端请求 URL
 */
function buildBackendUrl(pathname: string, search: string): string {
  const memoryPath = pathname.replace('/api/memory', '/memory');
  return `${BACKEND_BASE}${memoryPath}${search}`;
}

/**
 * 构建后端请求头
 */
function buildBackendHeaders(sessionId: string | null, extra?: Record<string, string>): Record<string, string> {
  const headers: Record<string, string> = { ...extra };
  if (sessionId) headers['X-Session-Id'] = sessionId;
  return headers;
}

// GET - 支持 SSE 流式透传
export async function GET(request: NextRequest) {
  const sessionId = getSessionId(request);
  if (!sessionId) {
    return NextResponse.json({ error: '未登录' }, { status: 401 });
  }

  const url = new URL(request.url);
  const isSSE = url.pathname.includes('/chat');
  const backendUrl = buildBackendUrl(url.pathname, url.search);

  try {
    const backendRes = await fetch(backendUrl, {
      headers: buildBackendHeaders(sessionId, {
        'Accept': isSSE ? 'text/event-stream' : 'application/json',
      }),
      signal: isSSE ? AbortSignal.timeout(300000) : AbortSignal.timeout(15000),
    });

    if (isSSE) {
      // SSE 流式透传
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

    // 普通响应 - 替换 localhost URL
    const text = await backendRes.text();
    const rewritten = rewriteBody(text, request);

    return new NextResponse(rewritten, {
      status: backendRes.status,
      headers: { 'Content-Type': 'application/json' },
    });
  } catch (error) {
    console.error('[Memory Proxy] GET 请求失败:', error);
    return NextResponse.json({ error: '后端服务不可用' }, { status: 503 });
  }
}

// POST - 支持 JSON 和 FormData
export async function POST(request: NextRequest) {
  const sessionId = getSessionId(request);
  if (!sessionId) {
    return NextResponse.json({ error: '未登录' }, { status: 401 });
  }

  const url = new URL(request.url);
  const backendUrl = buildBackendUrl(url.pathname, '');
  const contentType = request.headers.get('content-type') || '';

  try {
    let res: Response;

    if (contentType.includes('multipart/form-data')) {
      const formData = await request.formData();
      res = await fetch(backendUrl, {
        method: 'POST',
        headers: buildBackendHeaders(sessionId),
        body: formData,
        signal: AbortSignal.timeout(60000),
      });
    } else {
      const body = await request.json();
      res = await fetch(backendUrl, {
        method: 'POST',
        headers: buildBackendHeaders(sessionId, { 'Content-Type': 'application/json' }),
        body: JSON.stringify(body),
        signal: AbortSignal.timeout(15000),
      });
    }

    const text = await res.text();
    const rewritten = rewriteBody(text, request);

    return new NextResponse(rewritten, {
      status: res.status,
      headers: { 'Content-Type': 'application/json' },
    });
  } catch (error) {
    console.error('[Memory Proxy] POST 请求失败:', error);
    return NextResponse.json({ error: '后端服务不可用' }, { status: 503 });
  }
}

// PUT
export async function PUT(request: NextRequest) {
  const sessionId = getSessionId(request);
  if (!sessionId) {
    return NextResponse.json({ error: '未登录' }, { status: 401 });
  }

  const url = new URL(request.url);
  const backendUrl = buildBackendUrl(url.pathname, '');

  try {
    const body = await request.json();
    const res = await fetch(backendUrl, {
      method: 'PUT',
      headers: buildBackendHeaders(sessionId, { 'Content-Type': 'application/json' }),
      body: JSON.stringify(body),
      signal: AbortSignal.timeout(15000),
    });

    const text = await res.text();
    const rewritten = rewriteBody(text, request);

    return new NextResponse(rewritten, {
      status: res.status,
      headers: { 'Content-Type': 'application/json' },
    });
  } catch (error) {
    console.error('[Memory Proxy] PUT 请求失败:', error);
    return NextResponse.json({ error: '后端服务不可用' }, { status: 503 });
  }
}

// DELETE
export async function DELETE(request: NextRequest) {
  const sessionId = getSessionId(request);
  if (!sessionId) {
    return NextResponse.json({ error: '未登录' }, { status: 401 });
  }

  const url = new URL(request.url);
  const backendUrl = buildBackendUrl(url.pathname, url.search);

  try {
    const res = await fetch(backendUrl, {
      method: 'DELETE',
      headers: buildBackendHeaders(sessionId),
      signal: AbortSignal.timeout(15000),
    });

    const text = await res.text();
    const rewritten = rewriteBody(text, request);

    return new NextResponse(rewritten, {
      status: res.status,
      headers: { 'Content-Type': 'application/json' },
    });
  } catch (error) {
    console.error('[Memory Proxy] DELETE 请求失败:', error);
    return NextResponse.json({ error: '后端服务不可用' }, { status: 503 });
  }
}
