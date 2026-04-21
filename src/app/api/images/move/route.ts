import { NextRequest, NextResponse } from 'next/server';
import { backendFetch, handleBackendResponse } from '@/lib/backend-proxy';

/**
 * POST - 移动图片
 * 代理到 Java 后端: POST /api/images/move
 */
export async function POST(request: NextRequest) {
  try {
    const body = await request.json();
    const { imageIds, targetAlbumId } = body;
    const cookieHeader = request.headers.get('cookie') || '';
    
    if (!imageIds || !Array.isArray(imageIds) || imageIds.length === 0) {
      return NextResponse.json(
        { success: false, error: '请选择要移动的图片' },
        { status: 400 }
      );
    }
    
    if (!targetAlbumId) {
      return NextResponse.json(
        { success: false, error: '请选择目标相册' },
        { status: 400 }
      );
    }
    
    const response = await backendFetch('/images/move', {
      method: 'POST',
      body: { imageIds, targetAlbumId },
      requestHeaders: {
        cookie: cookieHeader,
      },
    });
    
    // 打印后端原始响应以便调试
    const responseText = await response.clone().text();
    console.log('[API] 移动图片，后端原始响应:', responseText);
    
    const result = await handleBackendResponse(response);
    console.log('[API] 移动图片，处理后的结果:', result);
    
    return NextResponse.json(result, { status: result.success ? 200 : 500 });
  } catch (error) {
    console.error('[API] 移动图片失败:', error);
    return NextResponse.json(
      { success: false, error: error instanceof Error ? error.message : '移动图片失败' },
      { status: 500 }
    );
  }
}
