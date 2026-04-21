import { NextRequest, NextResponse } from 'next/server';
import { categoryApi, handleBackendResponse } from '@/lib/backend-proxy';

/**
 * GET - 获取所有分类
 * 代理到 Java 后端: GET /api/categories
 */
export async function GET(request: NextRequest) {
  try {
    const cookieHeader = request.headers.get('cookie') || '';
    const requestHeaders: Record<string, string | null> = {
      cookie: cookieHeader,
    };
    const response = await categoryApi.list(requestHeaders);
    const result = await handleBackendResponse(response);
    
    if (result.success && result.data) {
      // 转换分类数据格式
      const categories = Array.isArray(result.data) ? result.data.map((c: Record<string, unknown>) => ({
        id: c.id,
        name: c.name,
        description: c.description,
        imageCount: c.imageCount || 0,
        coverUrl: c.coverUrl,
        sortOrder: c.sortOrder,
      })) : [];
      
      return NextResponse.json({
        success: true,
        data: categories,
      });
    }
    
    return NextResponse.json(result, { status: result.success ? 200 : 500 });
  } catch (error) {
    console.error('[API] 获取分类列表失败:', error);
    return NextResponse.json(
      { success: false, error: error instanceof Error ? error.message : '获取分类列表失败' },
      { status: 500 }
    );
  }
}
