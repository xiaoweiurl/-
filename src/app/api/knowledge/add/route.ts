/**
 * /api/knowledge/add - 代理到 Java 后端 /memory/upload 或 /memory/cards
 * 知识文档导入（不再使用 Coze SDK）
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
    const backendUrl = getBackendUrl();

    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (sessionId) headers['X-Session-Id'] = sessionId;

    // 文本内容导入 → 创建知识卡片
    if (body.content) {
      const cardBody = {
        domainCode: body.domainCode || 'product',
        title: body.title || '导入的文本知识',
        content: body.content,
        source: 'knowledge_base_import',
        tags: body.tags || ['知识库导入'],
      };

      const res = await fetch(`${backendUrl}/memory/cards`, {
        method: 'POST',
        headers,
        body: JSON.stringify(cardBody),
        signal: AbortSignal.timeout(30000),
      });

      const data = await res.json();
      if (data.success) {
        return NextResponse.json({
          success: true,
          doc_ids: [data.card?.id || Date.now().toString()],
          message: '知识卡片创建成功',
        });
      }
      return NextResponse.json(data, { status: res.status });
    }

    // URL 导入 → 创建知识卡片（URL作为内容）
    if (body.url) {
      const cardBody = {
        domainCode: body.domainCode || 'product',
        title: body.url,
        content: `来源URL: ${body.url}\n请通过文档上传功能导入该URL的完整内容`,
        source: 'url_import',
        tags: ['URL导入'],
      };

      const res = await fetch(`${backendUrl}/memory/cards`, {
        method: 'POST',
        headers,
        body: JSON.stringify(cardBody),
        signal: AbortSignal.timeout(30000),
      });

      const data = await res.json();
      if (data.success) {
        return NextResponse.json({
          success: true,
          doc_ids: [data.card?.id || Date.now().toString()],
          message: 'URL知识卡片创建成功',
        });
      }
      return NextResponse.json(data, { status: res.status });
    }

    return NextResponse.json({ error: '请提供 content 或 url' }, { status: 400 });
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '未知错误';
    console.error('[Knowledge Add] 导入失败:', msg);
    return NextResponse.json({ error: '导入失败', details: msg }, { status: 500 });
  }
}
