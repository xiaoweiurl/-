import { NextRequest, NextResponse } from 'next/server';
import { imageApi, handleBackendResponse } from '@/lib/backend-proxy';

/**
 * GET - 筛选图片
 * 代理到 Java 后端: GET /api/images/filter
 */
export async function GET(request: NextRequest) {
  try {
    const cookieHeader = request.headers.get('cookie') || '';
    const { searchParams } = new URL(request.url);
    
    const requestHeaders: Record<string, string | null> = {
      cookie: cookieHeader,
    };
    
    const params: Record<string, string | number | boolean | undefined> = {
      tag: searchParams.get('tag') || undefined,
      albumId: searchParams.get('albumId') || undefined,
      favorite: searchParams.get('favorite') === 'true' ? true : undefined,
      keyword: searchParams.get('keyword') || searchParams.get('search') || undefined,
      sortBy: searchParams.get('sortBy') || undefined,
      sortOrder: searchParams.get('sortOrder') || undefined,
      page: searchParams.get('page') ? parseInt(searchParams.get('page')!) : 1,
      pageSize: searchParams.get('pageSize') ? parseInt(searchParams.get('pageSize')!) : 40,
    };
    
    // 移除 undefined 值
    const cleanParams: Record<string, string | number | boolean> = {};
    for (const [key, value] of Object.entries(params)) {
      if (value !== undefined) {
        cleanParams[key] = value;
      }
    }
    
    const response = await imageApi.filter({
      ...cleanParams as Parameters<typeof imageApi.filter>[0],
      requestHeaders,
    });
    const result = await handleBackendResponse(response);
    
    return NextResponse.json(result, { status: result.success ? 200 : 500 });
  } catch (error) {
    console.error('[API] 筛选图片失败:', error);
    return NextResponse.json(
      { success: false, error: error instanceof Error ? error.message : '筛选图片失败' },
      { status: 500 }
    );
  }
}
