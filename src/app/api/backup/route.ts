import { NextRequest, NextResponse } from 'next/server';

const BACKEND_URL = process.env.NEXT_PUBLIC_BACKEND_API_URL || 'http://localhost:8080';

// 创建备份或获取备份列表
export async function GET(request: NextRequest) {
  try {
    const { searchParams } = new URL(request.url);
    const page = searchParams.get('page') || '1';
    const pageSize = searchParams.get('pageSize') || '20';
    const sessionId = request.cookies.get('session_id')?.value;

    const response = await fetch(
      `${BACKEND_URL}/api/backup/list?page=${page}&pageSize=${pageSize}`,
      {
        headers: {
          'X-Session-Id': sessionId || '',
        },
      }
    );

    const data = await response.json();
    return NextResponse.json(data, { status: response.status });
  } catch (error) {
    console.error('Get backup list error:', error);
    return NextResponse.json({ error: '获取备份列表失败' }, { status: 500 });
  }
}

// 创建备份
export async function POST(request: NextRequest) {
  try {
    const body = await request.json();
    const sessionId = request.cookies.get('session_id')?.value;

    const response = await fetch(`${BACKEND_URL}/api/backup/create`, {
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
    console.error('Create backup error:', error);
    return NextResponse.json({ error: '创建备份失败' }, { status: 500 });
  }
}
