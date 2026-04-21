import { NextRequest, NextResponse } from 'next/server';
import { backendFetch } from '@/lib/backend-proxy';

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

    // Get ZIP file
    const zipData = await response.arrayBuffer();
    const zipBuffer = Buffer.from(zipData);

    return new NextResponse(zipBuffer, {
      status: 200,
      headers: {
        'Content-Type': 'application/zip',
        'Content-Disposition': `attachment; filename="album_${albumId}_export.zip"`,
        'Content-Length': zipBuffer.length.toString(),
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
