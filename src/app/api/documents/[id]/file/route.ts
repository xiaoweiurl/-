import { NextRequest, NextResponse } from 'next/server';

// 后端 API 地址
const BACKEND_API_URL = process.env.NEXT_PUBLIC_BACKEND_API_URL || 'http://localhost:8080/api';
const BACKEND_BASE_URL = BACKEND_API_URL.replace(/\/api$/, '');

/**
 * 文档文件代理 - 用于解决跨域和认证问题
 */
export async function GET(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  try {
    const { id } = await params;
    const cookieHeader = request.headers.get('cookie') || '';
    const sessionId = request.headers.get('x-session-id') || '';

    // 获取文档详情
    const detailResponse = await fetch(`${BACKEND_API_URL}/documents/${id}`, {
      method: 'GET',
      headers: {
        'Cookie': cookieHeader,
        'X-Session-Id': sessionId,
      },
      credentials: 'include',
    });

    if (!detailResponse.ok) {
      return NextResponse.json(
        { success: false, error: '获取文档详情失败' },
        { status: detailResponse.status }
      );
    }

    const detail = await detailResponse.json();
    const doc = detail.data || detail;

    // 获取文档文件
    const fileResponse = await fetch(`${BACKEND_BASE_URL}/api/documents/${id}/file`, {
      headers: {
        'Cookie': cookieHeader,
        'X-Session-Id': sessionId,
      },
      credentials: 'include',
    });

    if (!fileResponse.ok) {
      return NextResponse.json(
        { success: false, error: '获取文档文件失败' },
        { status: fileResponse.status }
      );
    }

    // 获取内容类型
    const contentType = fileResponse.headers.get('content-type') || doc.contentType || 'application/octet-stream';
    
    // 获取文件内容 - 使用 Uint8Array 替代 Buffer（兼容 Next.js 15+）
    const arrayBuffer = await fileResponse.arrayBuffer();
    const uint8Array = new Uint8Array(arrayBuffer);

    // 返回文件
    return new NextResponse(uint8Array, {
      headers: {
        'Content-Type': contentType,
        'Content-Disposition': `inline; filename="${encodeURIComponent(doc.originalName || doc.name)}"`,
        'Cache-Control': 'public, max-age=3600',
      },
    });

  } catch (error) {
    console.error('文档文件代理失败:', error);
    return NextResponse.json(
      { success: false, error: '文档文件获取失败' },
      { status: 500 }
    );
  }
}
