import { NextRequest, NextResponse } from 'next/server';
import {
  getGoodsFolder,
  updateGoodsFolder,
  deleteGoodsFolder,
  deleteGoodsImage,
} from '@/lib/goods-library';

/**
 * 单个商品文件夹 API
 * GET    /api/goods-library/[id]  - 详情（含图片签名URL）
 * PUT    /api/goods-library/[id]  - 更新信息/备注（货号品名变化时重算文件夹名）
 * DELETE /api/goods-library/[id]  - 删除（同步清理 OSS 图片）
 */
export async function GET(
  _request: NextRequest,
  { params }: { params: Promise<{ id: string }> },
) {
  try {
    const { id } = await params;
    const folder = await getGoodsFolder(Number(id));
    if (!folder) {
      return NextResponse.json({ success: false, message: '商品不存在' }, { status: 404 });
    }
    return NextResponse.json({ success: true, data: folder });
  } catch (error: any) {
    console.error('[商品库] 详情查询失败:', error);
    return NextResponse.json(
      { success: false, message: error.message || '查询失败' },
      { status: 500 },
    );
  }
}

export async function PUT(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> },
) {
  try {
    const { id } = await params;
    const body = await request.json();
    const folder = await updateGoodsFolder(Number(id), body);
    if (!folder) {
      return NextResponse.json({ success: false, message: '商品不存在' }, { status: 404 });
    }
    return NextResponse.json({ success: true, data: folder });
  } catch (error: any) {
    console.error('[商品库] 更新失败:', error);
    return NextResponse.json(
      { success: false, message: error.message || '更新失败' },
      { status: 500 },
    );
  }
}

export async function DELETE(
  _request: NextRequest,
  { params }: { params: Promise<{ id: string }> },
) {
  try {
    const { id } = await params;
    const folder = await deleteGoodsFolder(Number(id));
    if (!folder) {
      return NextResponse.json({ success: false, message: '商品不存在' }, { status: 404 });
    }
    // 同步清理 OSS 上的四张图片
    await Promise.all([
      deleteGoodsImage(folder.main_image_key),
      deleteGoodsImage(folder.side_image_key),
      deleteGoodsImage(folder.detail_image_key),
      deleteGoodsImage(folder.product_image_key),
    ]);
    return NextResponse.json({ success: true, data: { id: folder.id } });
  } catch (error: any) {
    console.error('[商品库] 删除失败:', error);
    return NextResponse.json(
      { success: false, message: error.message || '删除失败' },
      { status: 500 },
    );
  }
}
