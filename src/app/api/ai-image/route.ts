import { NextRequest, NextResponse } from 'next/server';
import { backendFetch, handleBackendResponse } from '@/lib/backend-proxy';

export async function POST(request: NextRequest) {
  try {
    const body = await request.json();
    const requestHeaders = {
      'cookie': request.headers.get('cookie'),
      'x-session-id': request.headers.get('x-session-id'),
    };

    const response = await backendFetch('/ai-image/generate', {
      method: 'POST',
      body,
      requestHeaders,
      timeout: 300000, // 5 minutes for image generation
    });

    const result = await handleBackendResponse(response);
    return NextResponse.json(result);
  } catch (error) {
    console.error('[AI Image] generate error:', error);
    return NextResponse.json(
      { success: false, error: '生成失败', message: error instanceof Error ? error.message : '未知错误' },
      { status: 500 }
    );
  }
}

export async function GET(request: NextRequest) {
  try {
    const requestHeaders = {
      'cookie': request.headers.get('cookie'),
      'x-session-id': request.headers.get('x-session-id'),
    };

    const response = await backendFetch('/ai-image/models', {
      requestHeaders,
    });

    const result = await handleBackendResponse(response);
    return NextResponse.json(result);
  } catch (error) {
    console.error('[AI Image] get models error:', error);
    return NextResponse.json(
      { success: false, error: '获取模型列表失败', message: error instanceof Error ? error.message : '未知错误' },
      { status: 500 }
    );
  }
}
