import { NextRequest, NextResponse } from 'next/server';

const BACKEND_URL = process.env.NEXT_PUBLIC_BACKEND_API_URL || 'http://localhost:8080/api';

export async function POST(request: NextRequest) {
  try {
    const body = await request.text();

    const targetUrl = `${BACKEND_URL}/ai-image/generate`;

    const response = await fetch(targetUrl, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body,
    });

    const data = await response.text();
    return new NextResponse(data, {
      status: response.status,
      headers: { 'Content-Type': 'application/json' },
    });
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : '代理请求失败';
    return NextResponse.json(
      { success: false, error: '代理请求失败', message, target: `${BACKEND_URL}/ai-image/generate` },
      { status: 502 }
    );
  }
}
