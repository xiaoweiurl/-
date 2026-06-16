import { NextRequest, NextResponse } from 'next/server';

export const runtime = 'nodejs';

export async function POST(request: NextRequest) {
  const backendBase = process.env.BACKEND_API_URL || 'http://localhost:8080/api';
  const sessionId = request.headers.get('X-Session-Id') || request.cookies.get('session_id')?.value;

  const headers: Record<string, string> = {};
  if (sessionId) {
    headers['X-Session-Id'] = sessionId;
  }

  // 显式传递 cookie
  const sessionCookie = request.cookies.get('session_id');
  if (sessionCookie) {
    headers['Cookie'] = `session_id=${sessionCookie.value}`;
  }

  try {
    const body = await request.formData();
    const res = await fetch(`${backendBase}/knowledge/upload`, {
      method: 'POST',
      headers,
      body,
    });

    const text = await res.text();
    const data = text ? JSON.parse(text) : { success: true };
    return NextResponse.json(data, { status: res.status });
  } catch (err: unknown) {
    return NextResponse.json(
      { error: err instanceof Error ? err.message : '上传失败' },
      { status: 502 },
    );
  }
}
