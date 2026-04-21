import { NextRequest, NextResponse } from 'next/server';
import { backendFetch, handleBackendResponse } from '@/lib/backend-proxy';

/**
 * DELETE - 永久删除图片
 * 代理到 Java 后端: DELETE /api/images/{id}/permanent
 */
export async function DELETE(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  try {
    const { id } = await params;
    const cookieHeader = request.headers.get('cookie') || '';
    
    const response = await backendFetch(`/images/${id}/permanent`, {
      method: 'DELETE',
      requestHeaders: {
        cookie: cookieHeader,
      },
    });
    const result = await handleBackendResponse(response);
    
    return NextResponse.json(result, { status: result.success ? 200 : 500 });
  } catch (error) {
    console.error('[API] 永久删除图片失败:', error);
    return NextResponse.json(
      { success: false, error: error instanceof Error ? error.message : '永久删除失败' },
      { status: 500 }
    );
  }
}
