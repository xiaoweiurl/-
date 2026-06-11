import { NextRequest, NextResponse } from 'next/server';
import { getPool } from '@/lib/db';
import { EmbeddingClient, HeaderUtils } from 'coze-coding-dev-sdk';

// POST - 向量语义检索
export async function POST(request: NextRequest) {
  try {
    const { query, domainCode, topK = 10, minScore = 0.3 } = await request.json();

    if (!query) {
      return NextResponse.json({ error: '请提供 query 参数' }, { status: 400 });
    }

    // 1. 生成查询向量
    const customHeaders = HeaderUtils.extractForwardHeaders(request.headers);
    const embeddingClient = new EmbeddingClient(undefined, customHeaders);
    const queryEmbedding = await embeddingClient.embedText(query, { dimensions: 1024 });

    // 2. 在 pgvector 中做余弦相似度搜索
    const dbClient = await getPool().connect();
    try {
      const queryVector = `[${queryEmbedding.join(',')}]`;

      let domainFilter = '';
      const params: (string | number)[] = [];
      let paramIndex = 1;

      if (domainCode) {
        domainFilter = ` AND kc.domain_code = $${paramIndex++}`;
        params.push(domainCode);
      }

      const vectorParamIndex = paramIndex;

      const result = await dbClient.query(
        `SELECT 
          kc.id, kc.domain_code, kc.title, kc.content, kc.tags, kc.product_code,
          kc.source, kc.confidence, kc.status, kc.created_by, kc.created_at,
          kd.name as domain_name, kd.icon as domain_icon, kd.color as domain_color,
          ke.chunk_text,
          1 - (ke.embedding <=> $${vectorParamIndex}::vector) as similarity
        FROM knowledge_embeddings ke
        JOIN knowledge_cards kc ON ke.card_id = kc.id
        LEFT JOIN knowledge_domains kd ON kc.domain_code = kd.code
        WHERE kc.status = 'published'${domainFilter}
        ORDER BY ke.embedding <=> $${vectorParamIndex}::vector
        LIMIT $${vectorParamIndex + 1}`,
        [...params, queryVector, topK]
      );

      const results = result.rows
        .filter((row: Record<string, unknown>) => parseFloat(row.similarity as string) >= minScore)
        .map((row: Record<string, unknown>) => ({
          id: row.id,
          domainCode: row.domain_code,
          domainName: row.domain_name,
          domainIcon: row.domain_icon,
          domainColor: row.domain_color,
          title: row.title,
          content: row.content,
          tags: row.tags,
          productCode: row.product_code,
          source: row.source,
          confidence: row.confidence,
          createdBy: row.created_by,
          createdAt: row.created_at,
          chunkText: row.chunk_text,
          score: parseFloat(parseFloat(row.similarity as string).toFixed(4)),
        }));

      return NextResponse.json({
        success: true,
        query,
        results,
        total: results.length,
      });
    } finally {
      dbClient.release();
    }
  } catch (error: unknown) {
    const msg = error instanceof Error ? error.message : '未知错误';
    console.error('[Memory Search] 搜索失败:', msg);
    return NextResponse.json({ error: '语义检索失败', details: msg }, { status: 500 });
  }
}
