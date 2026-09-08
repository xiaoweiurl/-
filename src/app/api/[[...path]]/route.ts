import { NextRequest, NextResponse } from 'next/server';

/**
 * 统一 BFF 代理（根级 optional catch-all）
 *
 * 所有没有专属 route.ts 的 /api/* 请求统一由此转发到 Java 后端。
 * 设计原则：
 * - 鉴权统一：仅透传 Cookie / X-Session-Id 给 Java，由 Java 后端 SessionIdAuthFilter 统一鉴权
 * - 流式透传：响应体以流方式管道回浏览器，SSE（text/event-stream）与大文件下载不占用 Node 内存
 * - multipart 原样透传：请求体以 arrayBuffer 转发，保留原始 Content-Type boundary
 * - Set-Cookie 多值透传：登录等接口的种 Cookie 行为不被代理层吞掉
 * - 禁止降级：后端不可达直接返回 503，不做任何 Mock
 */

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

const DEFAULT_TIMEOUT_MS = 60_000;          // 普通请求 60s
const STREAM_TIMEOUT_MS = 10 * 60_000;      // SSE / 大文件 10min
const STREAM_PATH_PATTERN = /\/(chat|smart|file|download|export|import|batch-download)\b/;

function getBackendBase(): string {
  const url = process.env.BACKEND_API_URL
    || process.env.NEXT_PUBLIC_BACKEND_API_URL
    || 'http://localhost:8080/api';
  return url.replace(/\/+$/, '');
}

/** 需要跳过的请求头（hop-by-hop / 框架内部头） */
const SKIP_REQUEST_HEADERS = new Set([
  'host', 'connection', 'content-length', 'transfer-encoding',
  'x-forwarded-for', 'x-forwarded-proto', 'x-forwarded-host', 'x-real-ip',
  'cf-connecting-ip', 'cf-ipcountry', 'cf-ray', 'cf-visitor',
  'x-middleware-request', 'x-nextjs-data', 'x-invoke-output', 'x-invoke-path', 'x-invoke-query',
  'rsc', 'next-url',
]);

function buildForwardHeaders(request: NextRequest): Headers {
  const headers = new Headers();

  request.headers.forEach((value, key) => {
    const lower = key.toLowerCase();
    if (SKIP_REQUEST_HEADERS.has(lower) || lower.startsWith('x-middleware')) return;
    headers.set(key, value);
  });

  // 会话：仅接受 X-Session-Id 请求头或 session_id Cookie（与 Java 侧口径一致），统一转为 X-Session-Id
  const headerSession = request.headers.get('x-session-id');
  const cookieSession = request.cookies.get('session_id')?.value;
  const sessionId = headerSession || cookieSession;
  if (sessionId) {
    headers.set('X-Session-Id', sessionId);
  }

  return headers;
}

async function forward(request: NextRequest): Promise<NextResponse> {
  const url = new URL(request.url);
  const backendUrl = `${getBackendBase()}${url.pathname}${url.search}`;

  const isSSE = (request.headers.get('accept') || '').includes('text/event-stream')
    || STREAM_PATH_PATTERN.test(url.pathname);
  const timeoutMs = isSSE ? STREAM_TIMEOUT_MS : DEFAULT_TIMEOUT_MS;

  const headers = buildForwardHeaders(request);

  let body: ArrayBuffer | undefined;
  if (request.method !== 'GET' && request.method !== 'HEAD') {
    body = await request.arrayBuffer();
  }

  let backendResponse: Response;
  try {
    backendResponse = await fetch(backendUrl, {
      method: request.method,
      headers,
      body,
      // @ts-expect-error Node 18+ 流式请求体需要 duplex
      duplex: 'half',
      redirect: 'manual',
      signal: AbortSignal.timeout(timeoutMs),
    });
  } catch (error) {
    console.error(`[BFF] 后端请求失败: ${request.method} ${url.pathname}`, error instanceof Error ? error.message : error);
    return NextResponse.json(
      { success: false, error: '后端服务不可用，请稍后重试' },
      { status: 503 },
    );
  }

  // 流式透传响应体：SSE / 文件下载均不落入内存
  const responseHeaders = new Headers();
  const copyHeaders = [
    'content-type', 'content-disposition', 'content-length', 'content-encoding',
    'cache-control', 'etag', 'last-modified', 'expires', 'location',
  ];
  for (const name of copyHeaders) {
    const value = backendResponse.headers.get(name);
    if (value) responseHeaders.set(name, value);
  }

  // Set-Cookie 多值透传
  const anyHeaders = backendResponse.headers as unknown as { getSetCookie?: () => string[] };
  const setCookies = typeof anyHeaders.getSetCookie === 'function'
    ? anyHeaders.getSetCookie()
    : (backendResponse.headers.get('set-cookie') ? [backendResponse.headers.get('set-cookie') as string] : []);
  for (const cookie of setCookies) {
    if (cookie) responseHeaders.append('set-cookie', cookie);
  }

  return new NextResponse(backendResponse.body, {
    status: backendResponse.status,
    statusText: backendResponse.statusText,
    headers: responseHeaders,
  });
}

export async function GET(request: NextRequest) { return forward(request); }
export async function POST(request: NextRequest) { return forward(request); }
export async function PUT(request: NextRequest) { return forward(request); }
export async function PATCH(request: NextRequest) { return forward(request); }
export async function DELETE(request: NextRequest) { return forward(request); }
export async function HEAD(request: NextRequest) { return forward(request); }
export async function OPTIONS(request: NextRequest) { return forward(request); }
