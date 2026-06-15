import { NextRequest, NextResponse } from 'next/server';

/**
 * 知识库对话代理路由
 * /api/knowledge/chat -> Java后端 /memory/chat (SSE流式)
 */

const BACKEND_BASE = process.env.NEXT_PUBLIC_BACKEND_API_URL || 'http://localhost:8080/api';

function getSessionId(request: NextRequest): string | null {
  const header = request.headers.get('x-session-id');
  if (header) return header;
  const cookie = request.cookies.get('session_id')?.value;
  if (cookie) return cookie;
  return null;
}

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

  const sessionId = getSessionId(request);
  if (sessionId) {
    headers.set('X-Session-Id', sessionId);
  }

  return headers;
}

export async function POST(request: NextRequest) {
  try {
    const body = await request.json();
    const { message, sessionId: clientSessionId } = body;

    if (!message) {
      return NextResponse.json({ error: '消息不能为空' }, { status: 400 });
    }

    // 构造请求参数 - 使用Java后端记忆库chat接口
    const params = new URLSearchParams();
    params.set('message', message);
    params.set('sessionId', clientSessionId || 'knowledge-session-' + Date.now());

    const backendUrl = `${BACKEND_BASE}/memory/chat?${params.toString()}`;

    const backendRes = await fetch(backendUrl, {
      headers: buildHeaders(request),
      signal: AbortSignal.timeout(300000),
    });

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
  } catch (error) {
    console.error('[Knowledge Chat Proxy] error:', error);
    return NextResponse.json({ error: '后端服务不可用' }, { status: 503 });
  }
}
