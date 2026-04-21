import { NextRequest, NextResponse } from 'next/server';
import { backendFetch, handleBackendResponse } from '@/lib/backend-proxy';

/**
 * GET - 获取所有用户列表（管理员）
 */
export async function GET(request: NextRequest) {
  try {
    const headers: Record<string, string | null> = {
      'cookie': request.headers.get('cookie'),
      'x-session-id': request.headers.get('x-session-id'),
    };
    const response = await backendFetch('/admin/users', { requestHeaders: headers });
    const result = await handleBackendResponse(response);
    
    return NextResponse.json(result, { status: result.success ? 200 : 500 });
  } catch (error) {
    console.error('[API] 获取用户列表失败:', error);
    return NextResponse.json(
      { success: false, error: error instanceof Error ? error.message : '获取用户列表失败' },
      { status: 500 }
    );
  }
}
