import { NextRequest, NextResponse } from 'next/server';

const BACKEND_API_URL = process.env.BACKEND_API_URL || process.env.NEXT_PUBLIC_BACKEND_API_URL || 'http://localhost:8080/api';

export async function GET(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
  try {
    const { id } = await params;
    const sessionId = request.cookies.get('session_id')?.value || request.headers.get('x-session-id');
    const url = `${BACKEND_API_URL}/knowledge/cards/${id}`;

    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (sessionId) headers['X-Session-Id'] = sessionId;

    const res = await fetch(url, { headers, cache: 'no-store' });
    const data = await res.json();
    return NextResponse.json(data, { status: res.status });
  } catch (error) {
    console.error('Proxy knowledge card GET error:', error);
    return NextResponse.json({ success: false, error: '后端服务不可用' }, { status: 503 });
  }
}

export async function PUT(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
  try {
    const { id } = await params;
    const sessionId = request.cookies.get('session_id')?.value || request.headers.get('x-session-id');
    const body = await request.json();
    const url = `${BACKEND_API_URL}/knowledge/cards/${id}`;

    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (sessionId) headers['X-Session-Id'] = sessionId;

    const res = await fetch(url, {
      method: 'PUT',
      headers,
      body: JSON.stringify(body),
    });
    const data = await res.json();
    return NextResponse.json(data, { status: res.status });
  } catch (error) {
    console.error('Proxy knowledge card PUT error:', error);
    return NextResponse.json({ success: false, error: '后端服务不可用' }, { status: 503 });
  }
}

export async function DELETE(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
  try {
    const { id } = await params;
    const sessionId = request.cookies.get('session_id')?.value || request.headers.get('x-session-id');
    const url = `${BACKEND_API_URL}/knowledge/cards/${id}`;

    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (sessionId) headers['X-Session-Id'] = sessionId;

    const res = await fetch(url, { method: 'DELETE', headers });
    const data = await res.json();
    return NextResponse.json(data, { status: res.status });
  } catch (error) {
    console.error('Proxy knowledge card DELETE error:', error);
    return NextResponse.json({ success: false, error: '后端服务不可用' }, { status: 503 });
  }
}
