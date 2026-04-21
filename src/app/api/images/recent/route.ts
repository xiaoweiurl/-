import { NextRequest, NextResponse } from 'next/server';
import { backendFetch, handleBackendResponse } from '@/lib/backend-proxy';

/**
 * GET - 获取最近上传的图片（7天内）
 * 代理到 Java 后端: GET /api/images/recent
 */
export async function GET(request: NextRequest) {
  try {
    const { searchParams } = new URL(request.url);
    const page = searchParams.get('page') || '1';
    const pageSize = searchParams.get('pageSize') || '20';
    const cookieHeader = request.headers.get('cookie') || '';
    const requestHeaders: Record<string, string | null> = {
      cookie: cookieHeader,
    };
    
    const response = await backendFetch(`/images/recent?page=${page}&pageSize=${pageSize}`, { requestHeaders });
    const result = await handleBackendResponse(response);
    
    if (result.success && result.data) {
      const data = result.data as Record<string, unknown>;
      return NextResponse.json({
        success: true,
        data: {
          list: data.list || data.content || [],
          total: data.total || data.totalElements || 0,
          page: data.page || page,
          pageSize: data.pageSize || pageSize,
        },
      });
    }
    
    return NextResponse.json(result, { status: result.success ? 200 : 500 });
  } catch (error) {
    console.error('[API] 获取最近上传失败:', error);
    return NextResponse.json(
      { success: false, error: error instanceof Error ? error.message : '获取最近上传失败' },
      { status: 500 }
    );
  }
}
