import { NextRequest, NextResponse } from 'next/server';
import { backendFetch } from '@/lib/backend-proxy';

interface AlbumUpdateRequest {
  name?: string;
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

export async function PUT(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  try {
    const { id } = await params;
    const body: AlbumUpdateRequest = await request.json();
    const cookieHeader = request.headers.get('cookie') || '';

    const response = await backendFetch(`/albums/${id}`, {
      method: 'PUT',
      body: {
        name: body.name,
        description: body.description,
        matchingConfig: body.matchingConfig,
      },
      requestHeaders: {
        cookie: cookieHeader,
      },
    });

    const { result } = await safeParseResponse(response);

    if (result) {
      return NextResponse.json({
        ...result,
        success: result.code === 200 || result.success === true,
      });
    }

    return NextResponse.json({ success: false, message: '更新失败' }, { status: 500 });
  } catch (error) {
    console.error('Update album failed:', error);
    return NextResponse.json(
      { success: false, message: 'Update album failed' },
      { status: 500 }
    );
  }
}

export async function DELETE(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  try {
    const { id } = await params;
    const cookieHeader = request.headers.get('cookie') || '';

    const response = await backendFetch(`/albums/${id}`, {
      method: 'DELETE',
      requestHeaders: {
        cookie: cookieHeader,
      },
    });

    const { result } = await safeParseResponse(response);

    if (result) {
      return NextResponse.json({
        ...result,
        success: result.code === 200 || result.success === true,
      });
    }

    return NextResponse.json({ success: false, message: '删除失败' }, { status: 500 });
  } catch (error) {
    console.error('Delete album failed:', error);
    return NextResponse.json(
      { success: false, message: 'Delete album failed' },
      { status: 500 }
    );
  }
}
