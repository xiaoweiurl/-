import { NextRequest, NextResponse } from 'next/server';
import { backendFetch } from '@/lib/backend-proxy';

export const runtime = 'nodejs';
export const maxDuration = 60;

/**
 * POST /api/images/watermark-remove
 * 代理到 Java 后端去水印接口
 */
export async function POST(request: NextRequest) {
  try {
    const body = await request.json();
    const cookieHeader = request.headers.get('cookie') || '';

    console.log('[Watermark Remove] 代理请求到 Java 后端');

    const response = await backendFetch('/images/watermark-remove', {
      method: 'POST',
      body,
      requestHeaders: { cookie: cookieHeader },
    });

    if (response.ok) {
      const result = await response.json();
      console.log('[Watermark Remove] Java 后端处理成功');
      return NextResponse.json(result);
    } else {
      const errorText = await response.text();
      console.error('[Watermark Remove] Java 后端返回错误:', response.status, errorText);
      return NextResponse.json(
        { success: false, error: errorText || '去水印失败' },
        { status: response.status }
      );
    }
  } catch (error) {
    console.error('[Watermark Remove] Error:', error);
    const errorMessage = error instanceof Error ? error.message : '服务器内部错误';
    return NextResponse.json(
      { success: false, error: errorMessage },
      { status: 500 }
    );
  }
}
