import { NextRequest, NextResponse } from 'next/server';
import { backendFetch } from '@/lib/backend-proxy';

interface ApiResult {
  success: boolean;
  message?: string;
  data?: unknown;
  code?: number;
}

/**
 * 安全解析响应
 */
async function safeParseResponse(response: Response): Promise<{ result?: ApiResult; ok: boolean; status: number }> {
  const ok = response.ok;
  const status = response.status;
  
  const text = await response.text();
  
  if (!text) {
    return { ok, status, result: { success: true } };
  }
  
  try {
    const parsed = JSON.parse(text);
    return { ok, status, result: parsed };
  } catch {
    return { ok, status, result: { success: false, message: '解析响应失败' } };
  }
}

export async function PUT(request: NextRequest) {
  try {
    const body = await request.json();
    const { mode } = body;
    const cookieHeader = request.headers.get('cookie') || '';

    // Validate mode
    const validModes = ['contains', 'exact', 'startsWith', 'endsWith', 'regex', 'fuzzy'];
    if (!mode || !validModes.includes(mode)) {
      return NextResponse.json(
        { success: false, message: 'Invalid mode' },
        { status: 400 }
      );
    }

    const response = await backendFetch('/albums/matching-mode', {
      method: 'PUT',
      body: { mode },
      requestHeaders: {
        cookie: cookieHeader,
      },
    });

    const { result } = await safeParseResponse(response);

    return NextResponse.json({
      ...result,
      success: result?.code === 200 || result?.success === true,
    });
  } catch (error) {
    console.error('Batch update matching mode failed:', error);
    return NextResponse.json(
      { success: false, message: 'Batch update matching mode failed' },
      { status: 500 }
    );
  }
}
