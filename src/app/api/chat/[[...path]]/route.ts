/**
 * /api/chat/* 代理路由 - 转发到 Java 后端 /api/chat/*
 *
 * 接口:
 *   GET  /api/chat/smart?message=xxx&sessionId=xxx  → AI智能对话(SSE流式, 双库检索)
 *   GET  /api/chat/history?sessionId=xxx            → 获取对话历史
 *   DELETE /api/chat/history?sessionId=xxx          → 清空对话历史
 */
import { NextRequest, NextResponse } from 'next/server';

// 获取后端候选地址
function getBackendCandidates(request?: NextRequest): string[] {
  const candidates: string[] = [];
  const envUrl = process.env.NEXT_PUBLIC_BACKEND_API_URL;
  if (envUrl) candidates.push(envUrl.replace(/\/$/, ''));
  candidates.push('http://localhost:8080/api');
  return [...new Set(candidates)];
}

// 探测后端
async function probeBackend(candidates: string[]): Promise<string | null> {
  for (const url of candidates) {
    try {
      const res = await fetch(`${url}/albums`, {
        method: 'GET',
        signal: AbortSignal.timeout(3000),
        headers: { 'Accept': 'application/json' },
      });
      if (res.ok || res.status === 401) return url;
    } catch { /* next */ }
  }
  return null;
}

// 获取Session ID（与 /api/proxy 保持一致）
function getSessionId(request: NextRequest): string | null {
  // 1. 从请求头获取（前端直接传的 X-Session-Id）
  const header = request.headers.get('x-session-id');
  if (header) return header;
  // 2. 从 cookie 获取（兜底）
  const cookie = request.cookies.get('session_id')?.value;
  if (cookie) return cookie;
  return null;
}

// 构建转发请求头（与 /api/proxy 保持一致：复制原始头 + 补充session）
function buildHeaders(request: NextRequest): Record<string, string> {
  const headers: Record<string, string> = {};
  
  // 复制原始请求头（跳过不应转发的头）
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
      headers[key] = value;
    }
  });
  
  // 确保 X-Session-Id 存在（统一大写，避免重复key）
  const sessionId = getSessionId(request);
  if (sessionId) {
    delete headers['x-session-id'];  // 先删小写版本
    headers['X-Session-Id'] = sessionId;  // 统一用大写
  }

  // SSE请求不传Content-Type（让浏览器自动处理）
  const accept = request.headers.get('accept') || '';
  if (accept.includes('text/event-stream')) {
    delete headers['content-type'];
    delete headers['Content-Type'];
  }

  return headers;
}

// 判断是否SSE请求
function isSSERequest(request: NextRequest): boolean {
  const url = new URL(request.url);
  return url.pathname.includes('/smart') || url.pathname.includes('/chat/smart');
}

// 动态替换响应中的localhost URL
function rewriteUrls(text: string, request: NextRequest): string {
  const host = request.headers.get('host') || 'localhost:5000';
  const isLocal = host.startsWith('localhost') || host.startsWith('127.0.0.1');

  if (isLocal) {
    // 本地: localhost:8080/xxx → /xxx (相对路径)
    return text.replace(/http:\/\/localhost:8080\//g, '/');
  } else {
    // 映射域名: localhost:8080/xxx → http://域名/xxx
    const protocol = request.headers.get('x-forwarded-proto') || 'http';
    const domain = host.split(':')[0];
    return text.replace(/http:\/\/localhost:8080\//g, `${protocol}://${domain}/`);
  }
}

// GET 请求处理 (对话SSE + 历史查询)
export async function GET(request: NextRequest) {
  const url = new URL(request.url);
  const pathParts = url.pathname.replace('/api/chat/', '');
  const backendPath = `/chat/${pathParts}`;
  const query = url.searchParams.toString();

  const candidates = getBackendCandidates(request);
  const backendUrl = await probeBackend(candidates);

  if (!backendUrl) {
    return NextResponse.json(
      { success: false, error: '后端服务不可用', message: '请确认 Java 后端已启动' },
      { status: 502 }
    );
  }

  const targetUrl = `${backendUrl}${backendPath}${query ? '?' + query : ''}`;
  const headers = buildHeaders(request);
  const sse = isSSERequest(request);
  
  // 调试日志
  console.log('[Chat Proxy] target:', targetUrl);
  console.log('[Chat Proxy] X-Session-Id:', headers['X-Session-Id'] || headers['x-session-id'] || 'MISSING');

  try {
    const backendRes = await fetch(targetUrl, {
      method: 'GET',
      headers: {
        ...headers,
        'Accept': sse ? 'text/event-stream' : 'application/json',
      },
      signal: AbortSignal.timeout(sse ? 180000 : 30000),
    });

    if (!backendRes.ok) {
      const errText = await backendRes.text();
      return NextResponse.json(
        { success: false, error: `后端返回 ${backendRes.status}`, detail: errText },
        { status: backendRes.status }
      );
    }

    if (sse) {
      // SSE流式透传
      const reader = backendRes.body?.getReader();
      if (!reader) return NextResponse.json({ error: 'SSE流为空' }, { status: 500 });

      const encoder = new TextEncoder();
      const stream = new ReadableStream({
        async start(controller) {
          try {
            while (true) {
              const { done, value } = await reader.read();
              if (done) { controller.close(); return; }
              const text = new TextDecoder().decode(value);
              controller.enqueue(encoder.encode(rewriteUrls(text, request)));
            }
          } catch (e) {
            console.error('[Chat Proxy] SSE流中断:', e);
            controller.close();
          }
        },
      });

      return new Response(stream, {
        headers: {
          'Content-Type': 'text/event-stream',
          'Cache-Control': 'no-cache',
          'Connection': 'keep-alive',
          'Access-Control-Allow-Origin': '*',
        },
      });
    } else {
      // 普通JSON响应
      const data = await backendRes.text();
      const rewritten = rewriteUrls(data, request);
      return new Response(rewritten, {
        status: backendRes.status,
        headers: { 'Content-Type': 'application/json' },
      });
    }
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '未知错误';
    console.error('[Chat Proxy] 请求失败:', msg);
    return NextResponse.json({ success: false, error: msg }, { status: 500 });
  }
}

// DELETE 请求处理 (清空历史)
export async function DELETE(request: NextRequest) {
  const url = new URL(request.url);
  const pathParts = url.pathname.replace('/api/chat/', '');
  const backendPath = `/chat/${pathParts}`;
  const query = url.searchParams.toString();

  const candidates = getBackendCandidates(request);
  const backendUrl = await probeBackend(candidates);

  if (!backendUrl) {
    return NextResponse.json({ success: false, error: '后端服务不可用' }, { status: 502 });
  }

  const targetUrl = `${backendUrl}${backendPath}${query ? '?' + query : ''}`;

  try {
    const backendRes = await fetch(targetUrl, {
      method: 'DELETE',
      headers: buildHeaders(request),
      signal: AbortSignal.timeout(10000),
    });

    const data = await backendRes.text();
    return new Response(data, {
      status: backendRes.status,
      headers: { 'Content-Type': 'application/json' },
    });
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '未知错误';
    return NextResponse.json({ success: false, error: msg }, { status: 500 });
  }
}
