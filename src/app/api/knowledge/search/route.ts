/**
 * /api/knowledge/search - 代理到 Java 后端 /memory/search
 * 语义检索知识卡片（PostgreSQL向量搜索）
 */
import { NextRequest, NextResponse } from 'next/server';

function getSessionId(request: NextRequest): string | null {
  const header = request.headers.get('x-session-id');
  if (header) return header;
  const cookie = request.cookies.get('session_id')?.value;
  if (cookie) return cookie;
  return null;
}

function getBackendUrl(): string {
  const envUrl = process.env.NEXT_PUBLIC_BACKEND_API_URL;
  return envUrl ? envUrl.replace(/\/$/, '') : 'http://localhost:8080/api';
}

export async function POST(request: NextRequest) {
  try {
    const body = await request.json();
    const sessionId = getSessionId(request);

    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (sessionId) headers['X-Session-Id'] = sessionId;

    const backendUrl = getBackendUrl();
    const res = await fetch(`${backendUrl}/memory/search`, {
      method: 'POST',
      headers,
      body: JSON.stringify(body),
      signal: AbortSignal.timeout(30000),
    });

    const data = await res.text();
    return new Response(data, {
      status: res.status,
      headers: { 'Content-Type': 'application/json' },
    });
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '未知错误';
    console.error('[Knowledge Search] 代理失败:', msg);
    return NextResponse.json({ error: '搜索失败', details: msg }, { status: 500 });
  }
}

export async function GET(request: NextRequest) {
  try {
    const { searchParams } = new URL(request.url);
    const query = searchParams.get('query');
    const topK = searchParams.get('topK') || '5';
    const minScore = searchParams.get('minScore') || '0.3';
    const domainCode = searchParams.get('domainCode') || '';

    if (!query) {
      return NextResponse.json({ error: '请提供 query 参数' }, { status: 400 });
    }

    const sessionId = getSessionId(request);
    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (sessionId) headers['X-Session-Id'] = sessionId;

    const backendUrl = getBackendUrl();
    const res = await fetch(`${backendUrl}/memory/search`, {
      method: 'POST',
      headers,
      body: JSON.stringify({ query, domainCode: domainCode || undefined, minScore: parseFloat(minScore), limit: parseInt(topK) }),
      signal: AbortSignal.timeout(30000),
    });

    const data = await res.json();

    // 统一返回格式，兼容前端
    if (data.success && data.results) {
      return NextResponse.json({
        success: true,
        chunks: data.results.map((r: Record<string, unknown>) => ({
          content: r.content || r.chunkText || '',
          score: r.score || 0,
          docId: r.cardId || r.id,
          domain: r.domainCode,
          title: r.title,
        })),
      });
    }

    return NextResponse.json(data, { status: res.status });
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '未知错误';
    console.error('[Knowledge Search] 代理失败:', msg);
    return NextResponse.json({ error: '搜索失败', details: msg }, { status: 500 });
  }
}
