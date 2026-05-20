import { NextRequest, NextResponse } from 'next/server';

const BACKEND_URL = process.env.NEXT_PUBLIC_BACKEND_API_URL || 'http://localhost:8080';

// 创建分享链接
export async function POST(request: NextRequest) {
  try {
    const body = await request.json();
    const sessionId = request.cookies.get('session_id')?.value;

    const response = await fetch(`${BACKEND_URL}/api/share`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Session-Id': sessionId || '',
      },
      body: JSON.stringify(body),
    });

    const data = await response.json();
    return NextResponse.json(data, { status: response.status });
  } catch (error) {
    console.error('Create share error:', error);
    return NextResponse.json({ error: '创建分享失败' }, { status: 500 });
  }
}

// 获取用户的分享链接列表
export async function GET(request: NextRequest) {
  try {
    const { searchParams } = new URL(request.url);
    const resourceType = searchParams.get('resourceType') || '';
    const page = searchParams.get('page') || '1';
    const pageSize = searchParams.get('pageSize') || '20';
    const sessionId = request.cookies.get('session_id')?.value;

    const params = new URLSearchParams();
    if (resourceType) params.append('resourceType', resourceType);
    params.append('page', page);
    params.append('pageSize', pageSize);

    const response = await fetch(`${BACKEND_URL}/api/share/my?${params.toString()}`, {
      headers: {
        'X-Session-Id': sessionId || '',
      },
    });

    const data = await response.json();
    return NextResponse.json(data, { status: response.status });
  } catch (error) {
    console.error('Get shares error:', error);
    return NextResponse.json({ error: '获取分享列表失败' }, { status: 500 });
  }
}
