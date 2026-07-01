import { NextRequest, NextResponse } from 'next/server';
import { getBackendInternalUrl, getSessionId } from '@/lib/backend-proxy';

async function proxy(request: NextRequest, method: string) {
  const backendUrl = getBackendInternalUrl();
  const sessionId = getSessionId(Object.fromEntries(request.headers.entries()));
  const url = new URL(request.url);
  const path = url.pathname.replace(/^\/api\/data-models/, '/data-models');
  const targetUrl = `${backendUrl}${path}${url.search}`;

  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (sessionId) headers['Cookie'] = `session_id=${sessionId}`;

  try {
    const init: RequestInit = { method, headers };
    if (method !== 'GET' && method !== 'HEAD') {
      init.body = await request.text();
    }
    const res = await fetch(targetUrl, init);
    const text = await res.text();
    try {
      return NextResponse.json(JSON.parse(text));
    } catch {
      return new NextResponse(text, { status: res.status });
    }
  } catch (e: unknown) {
    return NextResponse.json({ success: false, error: String(e) }, { status: 502 });
  }
}

export async function GET(request: NextRequest) { return proxy(request, 'GET'); }
export async function POST(request: NextRequest) { return proxy(request, 'POST'); }
export async function PUT(request: NextRequest) { return proxy(request, 'PUT'); }
export async function DELETE(request: NextRequest) { return proxy(request, 'DELETE'); }
export async function PATCH(request: NextRequest) { return proxy(request, 'PATCH'); }
