import { NextRequest, NextResponse } from 'next/server';

const BACKEND_API_URL = process.env.BACKEND_API_URL || process.env.NEXT_PUBLIC_BACKEND_API_URL || 'http://localhost:8080/api';

export async function GET(request: NextRequest) {
  try {
    const { searchParams } = new URL(request.url);
    const query = searchParams.toString();
    const sessionId = request.headers.get('x-session-id');

    const res = await fetch(`${BACKEND_API_URL}/knowledge/position-cards${query ? '?' + query : ''}`, {
      headers: {
        'Content-Type': 'application/json',
        ...(sessionId ? { 'x-session-id': sessionId } : {}),
      },
    });

    const data = await res.json();
    return NextResponse.json(data, { status: res.status });
  } catch (error) {
    // 降级模式：返回空列表
    return NextResponse.json({ data: [], total: 0, page: 1, size: 20 });
  }
}

export async function POST(request: NextRequest) {
  try {
    const body = await request.json();
    const sessionId = request.headers.get('x-session-id');

    const res = await fetch(`${BACKEND_API_URL}/knowledge/position-cards`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(sessionId ? { 'x-session-id': sessionId } : {}),
      },
      body: JSON.stringify(body),
    });

    const data = await res.json();
    return NextResponse.json(data, { status: res.status });
  } catch (error) {
    return NextResponse.json({ error: '后端API未配置' }, { status: 500 });
  }
}

export async function PUT(request: NextRequest) {
  try {
    const body = await request.json();
    const sessionId = request.headers.get('x-session-id');
    const { id, ...updateData } = body;

    const res = await fetch(`${BACKEND_API_URL}/knowledge/position-cards/${id}`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        ...(sessionId ? { 'x-session-id': sessionId } : {}),
      },
      body: JSON.stringify(updateData),
    });

    const data = await res.json();
    return NextResponse.json(data, { status: res.status });
  } catch (error) {
    return NextResponse.json({ error: '后端API未配置' }, { status: 500 });
  }
}

export async function DELETE(request: NextRequest) {
  try {
    const { searchParams } = new URL(request.url);
    const id = searchParams.get('id');
    const sessionId = request.headers.get('x-session-id');

    const res = await fetch(`${BACKEND_API_URL}/knowledge/position-cards/${id}`, {
      method: 'DELETE',
      headers: {
        'Content-Type': 'application/json',
        ...(sessionId ? { 'x-session-id': sessionId } : {}),
      },
    });

    const data = await res.json();
    return NextResponse.json(data, { status: res.status });
  } catch (error) {
    return NextResponse.json({ error: '后端API未配置' }, { status: 500 });
  }
}
