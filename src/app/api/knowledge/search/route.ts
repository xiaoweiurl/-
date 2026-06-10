import { NextRequest, NextResponse } from 'next/server';
import { KnowledgeClient, Config } from 'coze-coding-dev-sdk';

export async function GET(request: NextRequest) {
  try {
    const { searchParams } = new URL(request.url);
    const query = searchParams.get('query');
    const topK = parseInt(searchParams.get('topK') || '5', 10);
    const minScore = parseFloat(searchParams.get('minScore') || '0.3');
    const tableNamesStr = searchParams.get('tableNames') || '';

    if (!query) {
      return NextResponse.json({ error: '请提供 query 参数' }, { status: 400 });
    }

    const config = new Config();
    const client = new KnowledgeClient(config);

    const tableNames = tableNamesStr ? tableNamesStr.split(',').filter(Boolean) : undefined;
    const response = await client.search(query, tableNames, topK, minScore);

    if (response.code === 0) {
      return NextResponse.json({
        success: true,
        chunks: response.chunks.map((chunk: { content: string; score: number; doc_id?: string }) => ({
          content: chunk.content,
          score: chunk.score,
          docId: chunk.doc_id,
        })),
      });
    } else {
      return NextResponse.json({ error: response.msg || '搜索失败' }, { status: 500 });
    }
  } catch (error: unknown) {
    const msg = error instanceof Error ? error.message : '未知错误';
    console.error('[Knowledge Search] 搜索失败:', msg);
    return NextResponse.json({ error: '搜索失败', details: msg }, { status: 500 });
  }
}
