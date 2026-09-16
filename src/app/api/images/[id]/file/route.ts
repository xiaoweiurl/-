import { NextRequest, NextResponse } from 'next/server';
import { backendFetch } from '@/lib/backend-proxy';

// 后端静态资源 URL
const BACKEND_STATIC_URL = process.env.NEXT_PUBLIC_BACKEND_STATIC_URL || 'http://localhost:8080';

/**
 * 图片文件代理 - 用于解决跨域和认证问题
 * 统一通过此代理下载图片，前端不直接访问后端或外部 URL
 */
export async function GET(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  try {
    const { id } = await params;
    const cookieHeader = request.headers.get('cookie') || '';
    const sessionId = request.headers.get('x-session-id') || '';

    // 首先获取图片详情
    let imageUrl: string | null = null;
    let originalUrl: string | null = null;
    let imageTitle: string = 'image';

    try {
      const detailResponse = await backendFetch(`/images/${id}`, {
        method: 'GET',
        requestHeaders: { 
          cookie: cookieHeader,
          'X-Session-Id': sessionId,
        },
      });

      if (detailResponse.ok) {
        const detail = await detailResponse.json();
        const data = detail.data || detail;
        imageUrl = data.thumbnailUrl || data.url || null;
        originalUrl = data.originalUrl || null;
        imageTitle = data.title || 'image';
      } else {
        console.warn('[Image File] 获取图片详情失败, status:', detailResponse.status);
      }
    } catch (fetchError) {
      console.warn('[Image File] 后端不可用，无法获取图片详情:', (fetchError as Error).message);
    }

    if (!imageUrl) {
      return NextResponse.json(
        { success: false, error: '图片地址不存在' },
        { status: 404 }
      );
    }

    // 如果是沙箱 URL，不兼容
    if (imageUrl.includes('sandbox/coze_coding/file/proxy')) {
      console.warn('[Image File] 旧格式 URL，无法转换:', imageUrl);
      return NextResponse.json(
        { success: false, error: '图片路径格式不支持，请重新上传' },
        { status: 410 }
      );
    }

    // 如果是相对路径，拼接后端地址
    if (imageUrl.startsWith('/uploads/')) {
      imageUrl = BACKEND_STATIC_URL + imageUrl;
    } else if (imageUrl.startsWith('/api/uploads/')) {
      // 后端相对路径格式，拼接后端地址
      imageUrl = BACKEND_STATIC_URL + imageUrl;
    }


    // 尝试获取图片文件
    let imageResponse: Response | null = null;
    
    try {
      imageResponse = await fetch(imageUrl, {
        signal: AbortSignal.timeout(30000),
      });
    } catch (fetchError) {
      console.warn('[Image File] 主URL获取失败:', (fetchError as Error).message);
    }

    // 主 URL 失败时，尝试 originalUrl 降级
    if ((!imageResponse || !imageResponse.ok) && originalUrl && originalUrl !== imageUrl) {
      try {
        imageResponse = await fetch(originalUrl, {
          signal: AbortSignal.timeout(30000),
        });
      } catch (fallbackError) {
        console.warn('[Image File] originalUrl 降级也失败:', (fallbackError as Error).message);
      }
    }

    if (!imageResponse || !imageResponse.ok) {
      const status = imageResponse?.status || 502;
      console.error('[Image File] 获取图片文件失败, status:', status);
      return NextResponse.json(
        { success: false, error: status === 502 ? '后端服务不可用' : '获取图片文件失败' },
        { status }
      );
    }

    // 获取图片数据并返回
    const imageBuffer = await imageResponse.arrayBuffer();
    const contentType = imageResponse.headers.get('content-type') || 'image/jpeg';
    const ext = contentType.includes('png') ? 'png' : contentType.includes('webp') ? 'webp' : 'jpg';

    return new NextResponse(imageBuffer, {
      headers: {
        'Content-Type': contentType,
        'Content-Disposition': `attachment; filename="${imageTitle}.${ext}"`,
        'Cache-Control': 'public, max-age=3600',
      },
    });
  } catch (error) {
    console.error('[Image File] 未预期错误:', error);
    return NextResponse.json(
      { success: false, error: '服务器内部错误' },
      { status: 500 }
    );
  }
}
