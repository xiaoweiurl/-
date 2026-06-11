import { NextRequest, NextResponse } from 'next/server';
import { getPool } from '@/lib/db';
import { EmbeddingClient, HeaderUtils } from 'coze-coding-dev-sdk';

// GET - 获取知识卡片列表
export async function GET(request: NextRequest) {
  try {
    const { searchParams } = new URL(request.url);
    const domainCode = searchParams.get('domain') || '';
    const status = searchParams.get('status') || 'published';
    const page = parseInt(searchParams.get('page') || '1', 10);
    const pageSize = parseInt(searchParams.get('pageSize') || '20', 10);
    const keyword = searchParams.get('keyword') || '';

    const client = await getPool().connect();
    try {
      let whereClause = '1=1';
      const params: (string | number)[] = [];
      let paramIndex = 1;

      if (domainCode) {
        whereClause += ` AND kc.domain_code = $${paramIndex++}`;
        params.push(domainCode);
      }
      if (status) {
        whereClause += ` AND kc.status = $${paramIndex++}`;
        params.push(status);
      }
      if (keyword) {
        whereClause += ` AND (kc.title ILIKE $${paramIndex} OR kc.content ILIKE $${paramIndex})`;
        params.push(`%${keyword}%`);
        paramIndex++;
      }

      // 总数
      const countResult = await client.query(
        `SELECT COUNT(*) as total FROM knowledge_cards kc WHERE ${whereClause}`,
        params
      );
      const total = parseInt(countResult.rows[0].total, 10);

      // 分页
      const offset = (page - 1) * pageSize;
      const result = await client.query(
        `SELECT kc.*, kd.name as domain_name, kd.icon as domain_icon, kd.color as domain_color
        FROM knowledge_cards kc
        LEFT JOIN knowledge_domains kd ON kc.domain_code = kd.code
        WHERE ${whereClause}
        ORDER BY kc.created_at DESC
        LIMIT $${paramIndex++} OFFSET $${paramIndex++}`,
        [...params, pageSize, offset]
      );

      return NextResponse.json({
        success: true,
        cards: result.rows,
        total,
        page,
        pageSize,
      });
    } finally {
      client.release();
    }
  } catch (error: unknown) {
    const msg = error instanceof Error ? error.message : '未知错误';
    console.error('[Memory Cards] 获取失败:', msg);
    return NextResponse.json({ error: '获取知识卡片失败', details: msg }, { status: 500 });
  }
}

// POST - 创建知识卡片（含向量化）
export async function POST(request: NextRequest) {
  try {
    const body = await request.json();
    const {
      domainCode,
      title,
      content,
      tags = [],
      productCode,
      source,
      confidence = 'medium',
      createdBy,
    } = body;

    if (!domainCode || !title || !content || !createdBy) {
      return NextResponse.json({ error: '缺少必填字段: domainCode, title, content, createdBy' }, { status: 400 });
    }

    // 1. 生成向量嵌入
    const customHeaders = HeaderUtils.extractForwardHeaders(request.headers);
    const embeddingClient = new EmbeddingClient(undefined, customHeaders);
    const embedding = await embeddingClient.embedText(`${title}\n\n${content}`, { dimensions: 1024 });

    // 2. 插入知识卡片
    const dbClient = await getPool().connect();
    try {
      await dbClient.query('BEGIN');

      const cardResult = await dbClient.query(
        `INSERT INTO knowledge_cards (domain_code, title, content, tags, product_code, source, confidence, status, review_status, created_by)
         VALUES ($1, $2, $3, $4, $5, $6, $7, 'published', 'pending', $8)
         RETURNING *`,
        [domainCode, title, content, tags, productCode || null, source || null, confidence, createdBy]
      );

      const card = cardResult.rows[0];

      // 3. 插入向量嵌入
      const embeddingStr = `[${embedding.join(',')}]`;
      await dbClient.query(
        `INSERT INTO knowledge_embeddings (card_id, embedding, embedding_model, chunk_text, chunk_index)
         VALUES ($1, $2::vector, $3, $4, 0)`,
        [card.id, embeddingStr, 'doubao-embedding-vision-251215', `${title}\n\n${content}`]
      );

      // 4. 更新域卡片计数
      await dbClient.query(
        `UPDATE knowledge_domains SET card_count = card_count + 1, updated_at = CURRENT_TIMESTAMP WHERE code = $1`,
        [domainCode]
      );

      await dbClient.query('COMMIT');

      return NextResponse.json({ success: true, card });
    } catch (error) {
      await dbClient.query('ROLLBACK');
      throw error;
    } finally {
      dbClient.release();
    }
  } catch (error: unknown) {
    const msg = error instanceof Error ? error.message : '未知错误';
    console.error('[Memory Cards] 创建失败:', msg);
    return NextResponse.json({ error: '创建知识卡片失败', details: msg }, { status: 500 });
  }
}
