import { NextRequest, NextResponse } from 'next/server';
import { listGoodsFolders, createGoodsFolder } from '@/lib/goods-library';

/**
 * 商品库 API
 * GET  /api/goods-library?keyword=xxx  - 文件夹列表（封面=主图签名URL）
 * POST /api/goods-library              - 创建商品文件夹（货号+品名自动命名）
 */
export async function GET(request: NextRequest) {
  try {
    const keyword = request.nextUrl.searchParams.get('keyword') || undefined;
    const folders = await listGoodsFolders(keyword);
    return NextResponse.json({ success: true, data: folders });
  } catch (error: any) {
    console.error('[商品库] 列表查询失败:', error);
    return NextResponse.json(
      { success: false, message: error.message || '查询失败' },
      { status: 500 },
    );
  }
}

export async function POST(request: NextRequest) {
  try {
    const body = await request.json();
    const sessionId = request.cookies.get('session_id')?.value || null;
    const folder = await createGoodsFolder(body, sessionId);
    return NextResponse.json({ success: true, data: folder });
  } catch (error: any) {
    console.error('[商品库] 创建失败:', error);
    return NextResponse.json(
      { success: false, message: error.message || '创建失败' },
      { status: 500 },
    );
  }
}
