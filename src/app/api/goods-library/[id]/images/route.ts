import { NextRequest, NextResponse } from 'next/server';
import {
  getGoodsFolder,
  updateGoodsImageKey,
  uploadGoodsImage,
  deleteGoodsImage,
  signImageUrl,
  IMAGE_SLOTS,
  SLOT_LABELS,
  type ImageSlot,
} from '@/lib/goods-library';

const MAX_IMAGE_SIZE = 20 * 1024 * 1024; // 20MB

/**
 * 商品图片上传/替换 API
 * POST /api/goods-library/[id]/images
 *   multipart 字段：slot（main/side/detail/product）+ file
 *   - slot=main 对应「主图」，side=「侧面图」，detail=「细节」，product=「产品图」
 *   - OSS 目录 goods-library/{货号品名}/{slot}.{ext}，替换时清理旧图
 */
export async function POST(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> },
) {
  try {
    const { id } = await params;
    const folderId = Number(id);

    const folder = await getGoodsFolder(folderId);
    if (!folder) {
      return NextResponse.json({ success: false, message: '商品不存在' }, { status: 404 });
    }

    const formData = await request.formData();
    const slot = formData.get('slot') as string;
    const file = formData.get('file') as File | null;

    if (!slot || !IMAGE_SLOTS.includes(slot as ImageSlot)) {
      return NextResponse.json(
        { success: false, message: `无效的图片槽位，仅支持：${IMAGE_SLOTS.join('/')}` },
        { status: 400 },
      );
    }
    if (!file) {
      return NextResponse.json({ success: false, message: '没有上传文件' }, { status: 400 });
    }
    if (!file.type.startsWith('image/')) {
      return NextResponse.json({ success: false, message: '仅支持图片文件' }, { status: 400 });
    }
    if (file.size > MAX_IMAGE_SIZE) {
      return NextResponse.json({ success: false, message: '图片大小不能超过 20MB' }, { status: 400 });
    }

    const buffer = Buffer.from(await file.arrayBuffer());
    const ext = file.name.includes('.') ? file.name.substring(file.name.lastIndexOf('.') + 1) : 'jpg';

    // 上传到 OSS：goods-library/{货号品名}/{main|side|detail|product}.{ext}
    const newKey = await uploadGoodsImage(folder.folder_name, slot as ImageSlot, buffer, ext, file.type);

    // 更新数据库并清理旧图
    const oldKey = folder[`${slot}_image_key` as keyof typeof folder] as string | null;
    await updateGoodsImageKey(folderId, slot as ImageSlot, newKey);
    if (oldKey && oldKey !== newKey) {
      await deleteGoodsImage(oldKey);
    }

    const url = await signImageUrl(newKey);
    return NextResponse.json({
      success: true,
      data: { slot, label: SLOT_LABELS[slot as ImageSlot], key: newKey, url },
    });
  } catch (error: any) {
    console.error('[商品库] 图片上传失败:', error);
    return NextResponse.json(
      { success: false, message: error.message || '上传失败' },
      { status: 500 },
    );
  }
}

/**
 * 删除某个槽位的图片
 * DELETE /api/goods-library/[id]/images?slot=main
 */
export async function DELETE(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> },
) {
  try {
    const { id } = await params;
    const folderId = Number(id);
    const slot = request.nextUrl.searchParams.get('slot') as string;

    if (!slot || !IMAGE_SLOTS.includes(slot as ImageSlot)) {
      return NextResponse.json(
        { success: false, message: `无效的图片槽位，仅支持：${IMAGE_SLOTS.join('/')}` },
        { status: 400 },
      );
    }

    const folder = await getGoodsFolder(folderId);
    if (!folder) {
      return NextResponse.json({ success: false, message: '商品不存在' }, { status: 404 });
    }

    const oldKey = folder[`${slot}_image_key` as keyof typeof folder] as string | null;
    await updateGoodsImageKey(folderId, slot as ImageSlot, null);
    await deleteGoodsImage(oldKey);

    return NextResponse.json({ success: true, data: { slot } });
  } catch (error: any) {
    console.error('[商品库] 图片删除失败:', error);
    return NextResponse.json(
      { success: false, message: error.message || '删除失败' },
      { status: 500 },
    );
  }
}
