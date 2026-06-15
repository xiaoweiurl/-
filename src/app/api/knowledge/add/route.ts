import { NextRequest, NextResponse } from 'next/server';

/**
 * 知识库文档导入代理路由
 * /api/knowledge/add -> Java后端 /memory/cards
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
    const { title, content, domain, tags } = body;

    if (!title || !content) {
      return NextResponse.json({ error: '标题和内容不能为空' }, { status: 400 });
    }

    // 转交给Java后端记忆库的知识卡片接口
    const backendUrl = `${BACKEND_BASE}/memory/cards`;

    const backendRes = await fetch(backendUrl, {
      method: 'POST',
      headers: buildHeaders(request),
      body: JSON.stringify({
        title,
        content,
        domain: domain || 'product',
        tags: tags || [],
      }),
      signal: AbortSignal.timeout(60000),
    });

    const text = await backendRes.text();

    return new NextResponse(text, {
      status: backendRes.status,
      headers: { 'Content-Type': 'application/json' },
    });
  } catch (error) {
    console.error('[Knowledge Add Proxy] error:', error);
    return NextResponse.json({ error: '后端服务不可用' }, { status: 503 });
  }
}
