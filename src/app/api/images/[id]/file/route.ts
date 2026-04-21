import { NextRequest, NextResponse } from 'next/server';
import { backendFetch } from '@/lib/backend-proxy';

// 后端静态资源 URL
const BACKEND_STATIC_URL = process.env.NEXT_PUBLIC_BACKEND_STATIC_URL || 'http://localhost:8080';

/**
 * 图片文件代理 - 用于解决跨域和认证问题
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
    const detailResponse = await backendFetch(`/images/${id}`, {
      method: 'GET',
      requestHeaders: { 
        cookie: cookieHeader,
        'X-Session-Id': sessionId,
      },
    });

    if (!detailResponse.ok) {
      return NextResponse.json(
        { success: false, error: '获取图片详情失败' },
        { status: detailResponse.status }
      );
    }

    const detail = await detailResponse.json();
    const data = detail.data || detail;
    
    // 优先使用 thumbnailUrl，可能是 jpg/png 格式
    let imageUrl = data.thumbnailUrl || data.url;
    
    if (!imageUrl) {
      return NextResponse.json(
        { success: false, error: '图片地址不存在' },
        { status: 404 }
      );
    }

    // 如果是相对路径，拼接后端地址
    if (imageUrl.startsWith('/uploads/')) {
      imageUrl = BACKEND_STATIC_URL + imageUrl;
    }

    // 如果是沙箱 URL，尝试转换（兼容旧数据）
    if (imageUrl.includes('sandbox/coze_coding/file/proxy')) {
      // 旧格式 URL 无法在本地环境访问，尝试从后端获取图片数据
      // 这里可以添加转换逻辑或返回占位图
      console.warn('[Image File] 旧格式 URL，无法转换:', imageUrl);
      return NextResponse.json(
        { success: false, error: '图片路径格式不支持，请重新上传' },
        { status: 410 }
      );
    }

    // 从后端获取图片文件
    const imageResponse = await fetch(imageUrl, {
      headers: {
        'Cookie': cookieHeader,
        'X-Session-Id': sessionId,
      },
      credentials: 'include',
    });

    if (!imageResponse.ok) {
      return NextResponse.json(
        { success: false, error: '获取图片文件失败' },
        { status: imageResponse.status }
      );
    }

    // 获取图片数据并返回
    const imageBuffer = await imageResponse.arrayBuffer();
    const contentType = imageResponse.headers.get('content-type') || 'image/jpeg';

    return new NextResponse(imageBuffer, {
      headers: {
        'Content-Type': contentType,
        'Cache-Control': 'public, max-age=3600',
      },
    });
  } catch (error) {
    console.error('[Image File] Error:', error);
    return NextResponse.json(
      { success: false, error: '服务器内部错误' },
      { status: 500 }
    );
  }
}
