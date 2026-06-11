import { NextResponse } from 'next/server';
import { getPool } from '@/lib/db';

export async function GET() {
  try {
    const client = await getPool().connect();
    try {
      const result = await client.query(
        `SELECT d.*, 
          (SELECT COUNT(*) FROM knowledge_cards WHERE domain_code = d.code AND status = 'published') as published_count,
          (SELECT COUNT(*) FROM knowledge_cards WHERE domain_code = d.code) as total_count
        FROM knowledge_domains d ORDER BY d.sort_order`
      );
      return NextResponse.json({ success: true, domains: result.rows });
    } finally {
      client.release();
    }
  } catch (error: unknown) {
    const msg = error instanceof Error ? error.message : '未知错误';
    console.error('[Memory Domains] 获取失败:', msg);
    return NextResponse.json({ error: '获取知识域失败', details: msg }, { status: 500 });
  }
}
