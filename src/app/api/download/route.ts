import { NextRequest, NextResponse } from 'next/server';

/**
 * 图片下载代理 - 无需后端参与，直接通过URL下载图片
 * 解决前端跨域问题：浏览器 fetch 外部URL受CORS限制，服务端不受限
 *
 * 参数：
 * - url: 图片URL（必填，需encodeURIComponent）
 * - title: 文件名（可选）
 */
export async function GET(request: NextRequest) {
  try {
    const url = request.nextUrl.searchParams.get('url');
    const title = request.nextUrl.searchParams.get('title') || 'image';

    if (!url) {
      return NextResponse.json(
        { success: false, error: '缺少url参数' },
        { status: 400 }
      );
    }

    // 安全校验：只允许 http/https 协议
    if (!url.startsWith('http://') && !url.startsWith('https://') && !url.startsWith('/')) {
      return NextResponse.json(
        { success: false, error: '不支持的URL协议' },
        { status: 400 }
      );
    }

    // 如果是相对路径（如 /api/uploads/...），拼接当前域名
    let targetUrl = url;
    if (url.startsWith('/')) {
      const host = request.headers.get('host') || `localhost:${process.env.DEPLOY_RUN_PORT || 5000}`;
      const protocol = request.headers.get('x-forwarded-proto') || 'http';
      targetUrl = `${protocol}://${host}${url}`;
    }

    console.log('[Download Proxy] 下载图片:', targetUrl.substring(0, 100));

    const response = await fetch(targetUrl, {
      signal: AbortSignal.timeout(30000),
      headers: {
        'Accept': 'image/*, */*',
      },
    });

    if (!response.ok) {
      console.error('[Download Proxy] 获取图片失败, status:', response.status, 'url:', targetUrl.substring(0, 80));
      return NextResponse.json(
        { success: false, error: `获取图片失败 (${response.status})` },
        { status: response.status }
      );
    }

    const imageBuffer = await response.arrayBuffer();
    const contentType = response.headers.get('content-type') || 'image/jpeg';
    const ext = contentType.includes('png') ? 'png'
      : contentType.includes('webp') ? 'webp'
      : contentType.includes('gif') ? 'gif'
      : 'jpg';

    return new NextResponse(imageBuffer, {
      headers: {
        'Content-Type': contentType,
        'Content-Disposition': `attachment; filename="${title}.${ext}"`,
        'Cache-Control': 'no-cache',
      },
    });
  } catch (error) {
    console.error('[Download Proxy] 错误:', error);
    const message = error instanceof Error ? error.message : '未知错误';
    return NextResponse.json(
      { success: false, error: `下载失败: ${message}` },
      { status: 500 }
    );
  }
}
