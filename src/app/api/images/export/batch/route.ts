import { NextRequest, NextResponse } from 'next/server';
import { backendFetch } from '@/lib/backend-proxy';

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

    const zipData = await response.arrayBuffer();
    const zipBuffer = Buffer.from(zipData);

    return new NextResponse(zipBuffer, {
      status: 200,
      headers: {
        'Content-Type': 'application/zip',
        'Content-Disposition': 'attachment; filename="albums_export.zip"',
        'Content-Length': zipBuffer.length.toString(),
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
