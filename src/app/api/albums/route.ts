import { NextRequest, NextResponse } from 'next/server';
import { backendFetch } from '@/lib/backend-proxy';

interface AlbumRequest {
  name: string;
  description?: string;
  matchingConfig?: string;
}

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
  
  // 空响应根据 HTTP 状态码判断成功与否
  if (!text) {
    return { ok, status, result: ok ? { success: true } : { success: false, message: '请求失败' } };
  }
  
  try {
    const parsed = JSON.parse(text);
    // 兼容 { code: 200, ... } 和 { success: true, ... } 两种格式
    return { ok, status, result: parsed };
  } catch {
    return { ok, status, result: { success: false, message: '解析响应失败' } };
  }
}

export async function POST(request: NextRequest) {
  try {
    const body: AlbumRequest = await request.json();
    const { name, description, matchingConfig } = body;

    if (!name || name.trim() === '') {
      return NextResponse.json(
        { success: false, message: '相册名称不能为空' },
        { status: 400 }
      );
    }

    const cookieHeader = request.headers.get('cookie') || '';

    const requestBody: Record<string, unknown> = { name, description: description || '' };
    if (matchingConfig) {
      requestBody.matchingConfig = matchingConfig;
    }

    const requestHeaders: Record<string, string | null> = {
      cookie: cookieHeader,
    };

    const response = await backendFetch('/albums', {
      method: 'POST',
      body: requestBody,
      requestHeaders,
    });

    const { result } = await safeParseResponse(response);

    if (result) {
      return NextResponse.json({
        ...result,
        success: result.code === 200 || result.success === true,
      });
    }

    return NextResponse.json({ success: false, message: '创建失败' }, { status: 500 });
  } catch (error) {
    console.error('创建相册失败:', error);
    return NextResponse.json(
      { success: false, message: '创建相册失败，请稍后重试' },
      { status: 500 }
    );
  }
}

export async function GET(request: NextRequest) {
  try {
    const cookieHeader = request.headers.get('cookie') || '';
    const requestHeaders: Record<string, string | null> = {
      cookie: cookieHeader,
    };

    const response = await backendFetch('/albums', {
      requestHeaders,
    });

    const { ok, result } = await safeParseResponse(response);

    if (result) {
      return NextResponse.json({
        ...result,
        success: result.code === 200 || result.success === true,
      });
    }

    return NextResponse.json({ success: false, message: '获取失败' }, { status: 500 });
  } catch (error) {
    console.error('获取相册列表失败:', error);
    return NextResponse.json(
      { success: false, message: '获取相册列表失败，请稍后重试' },
      { status: 500 }
    );
  }
}
