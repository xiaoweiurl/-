import { NextRequest, NextResponse } from 'next/server';
import { backendFetch } from '@/lib/backend-proxy';

// 设置最大执行时间为 10 分钟（适用于大文件导出）
export const maxDuration = 600;

export async function GET(
  request: NextRequest,
  { params }: { params: Promise<{ albumId: string }> }
) {
  try {
    const { albumId } = await params;
    const cookieHeader = request.headers.get('cookie') || '';

    const response = await backendFetch(`/images/export/${albumId}`, {
      method: 'GET',
      requestHeaders: {
        cookie: cookieHeader,
      },
    });

    if (!response.ok) {
      const errorData = await response.json().catch(() => ({}));
      return NextResponse.json(
        { success: false, message: errorData.message || 'Export failed' },
        { status: response.status }
      );
    }

    // 使用流式响应，避免大文件占用过多内存
    const stream = new ReadableStream({
      async start(controller) {
        const reader = response.body?.getReader();
        if (!reader) {
          controller.close();
          return;
        }

        try {
          while (true) {
            const { done, value } = await reader.read();
            if (done) break;
            controller.enqueue(value);
          }
        } catch (error) {
          console.error('Stream read error:', error);
        } finally {
          controller.close();
        }
      },
    });

    return new NextResponse(stream, {
      status: 200,
      headers: {
        'Content-Type': 'application/zip',
        'Content-Disposition': `attachment; filename="album_${albumId}_export.zip"`,
        'Transfer-Encoding': 'chunked',
      },
    });
  } catch (error) {
    console.error('Export failed:', error);
    return NextResponse.json(
      { success: false, message: 'Export failed' },
      { status: 500 }
    );
  }
}
