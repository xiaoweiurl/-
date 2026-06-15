import { NextRequest, NextResponse } from 'next/server';

/**
 * 知识库搜索代理路由
 * /api/knowledge/search -> Java后端 /memory/search
 */

const BACKEND_BASE = process.env.NEXT_PUBLIC_BACKEND_API_URL || 'http://localhost:8080/api';

function getSessionId(request: NextRequest): string | null {
  const header = request.headers.get('x-session-id');
  if (header) return header;
  const cookie = request.cookies.get('session_id')?.value;
  if (cookie) return cookie;
  return null;
}

function getUrlReplacement(request: NextRequest): { pattern: RegExp; replacement: string } | null {
  const host = request.headers.get('host') || '';
  const isLocal = host.startsWith('localhost') || host.startsWith('127.0.0.1');
  if (isLocal) {
    return { pattern: /http:\/\/localhost:8080/g, replacement: '' };
  }
  const origin = request.headers.get('origin') || `http://${host}`;
  return { pattern: /http:\/\/localhost:8080/g, replacement: origin };
}

function rewriteBody(body: string, request: NextRequest): string {
  const repl = getUrlReplacement(request);
  if (!repl) return body;
  return body.replace(repl.pattern, repl.replacement);
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

export async function GET(request: NextRequest) {
  try {
    const { searchParams } = new URL(request.url);
    const query = searchParams.get('query');
    const domain = searchParams.get('domain');

    if (!query) {
      return NextResponse.json({ error: '搜索关键词不能为空' }, { status: 400 });
    }

    const backendUrl = new URL(`${BACKEND_BASE}/memory/search`);
    backendUrl.searchParams.set('query', query);
    if (domain) {
      backendUrl.searchParams.set('domain', domain);
    }

    const backendRes = await fetch(backendUrl.toString(), {
      headers: buildHeaders(request),
      signal: AbortSignal.timeout(15000),
    });

    const text = await backendRes.text();
    const rewritten = rewriteBody(text, request);

    return new NextResponse(rewritten, {
      status: backendRes.status,
      headers: { 'Content-Type': 'application/json' },
    });
  } catch (error) {
    console.error('[Knowledge Search Proxy] error:', error);
    return NextResponse.json({ error: '后端服务不可用' }, { status: 503 });
  }
}
