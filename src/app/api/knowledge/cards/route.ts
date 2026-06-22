import { NextRequest, NextResponse } from 'next/server';

const BACKEND_API_URL = process.env.BACKEND_API_URL || process.env.NEXT_PUBLIC_BACKEND_API_URL || 'http://localhost:8080/api';

export async function GET(request: NextRequest) {
  try {
    const sessionId = request.cookies.get('session_id')?.value || request.headers.get('x-session-id');
    const searchParams = request.nextUrl.searchParams;
    const queryString = searchParams.toString();
    const url = `${BACKEND_API_URL}/knowledge/cards${queryString ? '?' + queryString : ''}`;

    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
    };
    if (sessionId) headers['X-Session-Id'] = sessionId;

    const res = await fetch(url, { headers, cache: 'no-store' });
    const data = await res.json();
    return NextResponse.json(data, { status: res.status });
  } catch (error) {
    console.error('Proxy knowledge cards GET error:', error);
    return NextResponse.json({ success: false, error: '后端服务不可用' }, { status: 503 });
  }
}

export async function POST(request: NextRequest) {
  try {
    const sessionId = request.cookies.get('session_id')?.value || request.headers.get('x-session-id');
    const body = await request.json();
    const url = `${BACKEND_API_URL}/knowledge/cards`;

    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
    };
    if (sessionId) headers['X-Session-Id'] = sessionId;

    const res = await fetch(url, {
      method: 'POST',
      headers,
      body: JSON.stringify(body),
    });
    const data = await res.json();
    return NextResponse.json(data, { status: res.status });
  } catch (error) {
    console.error('Proxy knowledge cards POST error:', error);
    return NextResponse.json({ success: false, error: '后端服务不可用' }, { status: 503 });
  }
}
