import { NextRequest, NextResponse } from 'next/server';
import { backendFetch, handleBackendResponse } from '@/lib/backend-proxy';

/**
 * POST - 批量删除图片
 * 代理到 Java 后端: POST /api/images/delete
 */
export async function POST(request: NextRequest) {
  try {
    const body = await request.json();
    const { imageIds, permanent } = body;
    const cookieHeader = request.headers.get('cookie') || '';
    
    if (!imageIds || !Array.isArray(imageIds) || imageIds.length === 0) {
      return NextResponse.json(
        { success: false, error: '请选择要删除的图片' },
        { status: 400 }
      );
    }
    
    const response = await backendFetch('/images/delete', {
      method: 'POST',
      body: { imageIds, permanent },
      requestHeaders: {
        cookie: cookieHeader,
      },
    });
    const result = await handleBackendResponse(response);
    
    return NextResponse.json(result, { status: result.success ? 200 : 500 });
  } catch (error) {
    console.error('[API] 删除图片失败:', error);
    return NextResponse.json(
      { success: false, error: error instanceof Error ? error.message : '删除图片失败' },
      { status: 500 }
    );
  }
}

/**
 * PATCH - 批量删除图片（兼容前端调用）
 * 代理到 Java 后端: POST /api/images/delete
 */
export async function PATCH(request: NextRequest) {
  return POST(request);
}
