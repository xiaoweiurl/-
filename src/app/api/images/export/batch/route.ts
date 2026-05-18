import { NextRequest, NextResponse } from 'next/server';
import { backendFetch } from '@/lib/backend-proxy';

// 设置最大执行时间为 10 分钟（适用于大文件导出）
export const maxDuration = 600;

export async function POST(request: NextRequest) {
  try {
    const albumIds = await request.json();
    const cookieHeader = request.headers.get('cookie') || '';

    if (!albumIds || !Array.isArray(albumIds) || albumIds.length === 0) {
      return NextResponse.json(
        { success: false, message: 'Please provide album IDs' },
        { status: 400 }
      );
    }

    const response = await backendFetch('/images/export/batch', {
      method: 'POST',
      body: albumIds,
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
        'Content-Disposition': 'attachment; filename="albums_export.zip"',
        // 不设置 Content-Length，使用分块传输
        'Transfer-Encoding': 'chunked',
      },
    });
  } catch (error) {
    console.error('Batch export failed:', error);
    return NextResponse.json(
      { success: false, message: 'Batch export failed' },
      { status: 500 }
    );
  }
}
