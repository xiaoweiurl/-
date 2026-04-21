import { NextRequest, NextResponse } from 'next/server';
import { backendFetchFormData } from '@/lib/backend-proxy';

/**
 * POST - 上传图片
 * 代理到 Java 后端: POST /api/images/upload
 */
export async function POST(request: NextRequest) {
  try {
    const formData = await request.formData();
    const cookieHeader = request.headers.get('cookie') || '';
    const requestHeaders: Record<string, string | null> = {
      cookie: cookieHeader,
    };
    
    console.log('[API] 开始上传图片...');
    console.log('[API] Cookie:', cookieHeader ? '已传递' : '无');
    
    // 直接尝试上传，不预先检查后端可用性
    // 这样可以让真实的错误信息返回给用户
    const response = await backendFetchFormData('/images/upload', formData, requestHeaders);
    const result = await response.json();
    
    console.log('[API] 后端上传响应:', result);
    
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
    console.error('[API] 上传图片失败:', error);
    const errorMessage = error instanceof Error ? error.message : '上传图片失败';
    
    // 如果是网络错误，提示后端可能未启动
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
