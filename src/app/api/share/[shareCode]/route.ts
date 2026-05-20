import { NextRequest, NextResponse } from 'next/server';

const BACKEND_URL = process.env.NEXT_PUBLIC_BACKEND_API_URL || 'http://localhost:8080';

// 访问分享链接
export async function GET(
  request: NextRequest,
  { params }: { params: Promise<{ shareCode: string }> }
) {
  try {
    const { shareCode } = await params;
    const { searchParams } = new URL(request.url);
    const password = searchParams.get('password') || '';

    const paramsStr = password ? `?password=${encodeURIComponent(password)}` : '';

    const response = await fetch(`${BACKEND_URL}/api/share/access/${shareCode}${paramsStr}`, {
      headers: {
        'Content-Type': 'application/json',
      },
    });

    const data = await response.json();
    return NextResponse.json(data, { status: response.status });
  } catch (error) {
    console.error('Access share error:', error);
    return NextResponse.json({ error: '访问分享失败' }, { status: 500 });
  }
}
