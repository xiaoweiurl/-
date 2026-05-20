import { NextRequest, NextResponse } from 'next/server';

const BACKEND_URL = process.env.NEXT_PUBLIC_BACKEND_API_URL || 'http://localhost:8080';

// 获取存储统计
export async function GET(request: NextRequest) {
  try {
    const sessionId = request.cookies.get('session_id')?.value;

    const response = await fetch(`${BACKEND_URL}/api/storage/stats`, {
      headers: {
        'X-Session-Id': sessionId || '',
      },
    });

    const data = await response.json();
    return NextResponse.json(data, { status: response.status });
  } catch (error) {
    console.error('Get storage stats error:', error);
    return NextResponse.json({ error: '获取存储统计失败' }, { status: 500 });
  }
}
