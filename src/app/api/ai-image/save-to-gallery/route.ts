import { NextRequest, NextResponse } from 'next/server';
import { backendFetch, handleBackendResponse } from '@/lib/backend-proxy';

export async function POST(request: NextRequest) {
  try {
    const body = await request.json();
    const requestHeaders = {
      'cookie': request.headers.get('cookie'),
      'x-session-id': request.headers.get('x-session-id'),
    };

    const response = await backendFetch('/ai-image/save-to-gallery', {
      method: 'POST',
      body,
      requestHeaders,
    });

    const result = await handleBackendResponse(response);
    return NextResponse.json(result);
  } catch (error) {
    console.error('[AI Image] save-to-gallery error:', error);
    return NextResponse.json(
      { success: false, error: '保存失败', message: error instanceof Error ? error.message : '未知错误' },
      { status: 500 }
    );
  }
}
