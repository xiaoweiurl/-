import { NextRequest, NextResponse } from 'next/server';
import { backendFetchFormData } from '@/lib/backend-proxy';

/**
 * POST - 上传文档
 * 代理到 Java 后端: POST /api/documents/upload 或 POST /api/documents/upload/batch
 */
export async function POST(request: NextRequest) {
  try {
    const formData = await request.formData();
    const cookieHeader = request.headers.get('cookie') || '';
    const xSessionId = request.headers.get('x-session-id') || '';
    const requestHeaders: Record<string, string | null> = {
      cookie: cookieHeader,
      'x-session-id': xSessionId || null,
    };

    console.log('[API] 开始上传文档...');

    // 检查是批量上传还是单文件上传
    const files = formData.getAll('files');
    const endpoint = files.length > 1 ? '/documents/upload/batch' : '/documents/upload';

    const response = await backendFetchFormData(endpoint, formData, requestHeaders);
    const result = await response.json();

    console.log('[API] 后端上传文档响应:', result);

    if (result.code === 200 || result.success) {
      return NextResponse.json({
        success: true,
        message: result.message || '上传成功',
        data: result.data,
      });
    }

    return NextResponse.json(
      { success: false, error: result.message || '上传失败' },
      { status: 500 }
    );
  } catch (error) {
    console.error('[API] 上传文档失败:', error);
    const errorMessage = error instanceof Error ? error.message : '上传文档失败';

    if (errorMessage.includes('fetch') || errorMessage.includes('ECONNREFUSED')) {
      return NextResponse.json(
        { success: false, error: '无法连接到后端服务，请确保 Java 后端已在 localhost:8080 启动' },
        { status: 503 }
      );
    }

    return NextResponse.json(
      { success: false, error: errorMessage },
      { status: 500 }
    );
  }
}
