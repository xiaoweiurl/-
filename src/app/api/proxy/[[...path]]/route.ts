import { NextRequest, NextResponse } from 'next/server';

/**
 * 通用后端 API 代理
 * 
 * 核心策略：在代理层根据请求来源域名，动态替换响应中的 localhost:8080 URL
 * 
 * - 本地访问(localhost/127.0.0.1): 替换为相对路径 /api/uploads/xxx（前端同源请求，无CORS）
 * - 映射域名访问: 替换为 http://映射域名/api/uploads/xxx（Java后端8080映射到域名80端口）
 * 
 * 这样前端拿到的图片URL直接可用，不需要在前端做任何URL转换！
 */

// 后端地址探测结果缓存
let cachedBackendUrl: string | null = null;
let lastProbeTime = 0;
const PROBE_CACHE_TTL = 60000; // 1分钟缓存

/**
 * 根据请求来源获取可能的后端地址列表
 */
function getBackendCandidates(request: NextRequest): string[] {
  const candidates: string[] = [];
  
  // 1. 环境变量优先
  if (process.env.BACKEND_API_URL) {
    candidates.push(process.env.BACKEND_API_URL);
  }
  if (process.env.NEXT_PUBLIC_BACKEND_API_URL) {
    candidates.push(process.env.NEXT_PUBLIC_BACKEND_API_URL);
  }
  
  // 2. 总是尝试直连本地后端（代理运行在服务器上，可以直接访问localhost）
  candidates.push('http://localhost:8080/api');
  
  return [...new Set(candidates)];
}

/**
 * 探测可用的后端地址
 */
async function probeBackend(url: string): Promise<boolean> {
  try {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 5000);
    
    await fetch(`${url}/albums`, {
      method: 'GET',
      signal: controller.signal,
      headers: { 'Accept': 'application/json' },
    });
    
    clearTimeout(timeoutId);
    return true;
  } catch {
    return false;
  }
}

/**
 * 获取可用的后端地址（带缓存）
 */
async function getAvailableBackend(request: NextRequest): Promise<string | null> {
  const now = Date.now();
  
  if (cachedBackendUrl && (now - lastProbeTime) < PROBE_CACHE_TTL) {
    return cachedBackendUrl;
  }
  
  const candidates = getBackendCandidates(request);
  
  for (const url of candidates) {
    const available = await probeBackend(url);
    if (available) {
      cachedBackendUrl = url;
      lastProbeTime = now;
      console.log(`[Proxy] 后端可用: ${url}`);
      return url;
    }
    console.log(`[Proxy] 后端不可用: ${url}`);
  }
  
  console.error('[Proxy] 所有后端地址均不可用');
  return null;
}

/**
 * 根据请求的 Host 头判断当前访问方式，返回需要替换的目标前缀
 * 
 * 逻辑：
 * - 本地访问 (localhost:5000 / 127.0.0.1:5000): 返回空字符串
 *   → http://localhost:8080/api/uploads/xxx → /api/uploads/xxx (相对路径，前端同源请求)
 * - 映射域名访问 (xxx.gnway.cc:8000): 返回 http://映射域名
 *   → http://localhost:8080/api/uploads/xxx → http://xxx.gnway.cc/api/uploads/xxx (映射的80端口)
 */
function getReplacementPrefix(request: NextRequest): string {
  const host = request.headers.get('host') || '';
  const isLocal = host.startsWith('localhost') || host.startsWith('127.0.0.1') || host.startsWith('0.0.0.0');
  
  if (isLocal) {
    // 本地访问：替换为相对路径（去掉 http://localhost:8080 前缀）
    return '';
  }
  
  // 映射域名访问：替换为映射域名
  // Java后端(8080)映射到域名默认80端口，所以不需要端口号
  const hostname = host.split(':')[0];
  // 判断协议：映射服务一般用http，如果请求带了x-forwarded-proto则用它
  const forwardedProto = request.headers.get('x-forwarded-proto');
  const protocol = forwardedProto || 'http';
  
  return `${protocol}://${hostname}`;
}

/**
 * 改写响应体中的 localhost:8080 URL
 * 
 * 本地访问: http://localhost:8080/api/uploads/xxx.jpg → /api/uploads/xxx.jpg
 * 映射访问: http://localhost:8080/api/uploads/xxx.jpg → http://映射域名/api/uploads/xxx.jpg
 */
function rewriteResponseBody(body: string, replacementPrefix: string): string {
  // 匹配 http://localhost:8080 或 http://127.0.0.1:8080
  return body.replace(/https?:\/\/(?:localhost|127\.0\.0\.1):8080/g, replacementPrefix);
}

async function proxyRequest(request: NextRequest, method: string) {
  const backendUrl = await getAvailableBackend(request);
  
  if (!backendUrl) {
    const candidates = getBackendCandidates(request);
    return NextResponse.json(
      {
        success: false,
        error: `后端服务不可用，已尝试: ${candidates.join(', ')}`,
        message: '后端服务不可用，请确认 Java 后端已启动'
      },
      { status: 502 }
    );
  }
  
  try {
    const path = request.nextUrl.pathname.replace('/api/proxy', '');
    const searchParams = request.nextUrl.search;
    const targetUrl = `${backendUrl}${path}${searchParams}`;

    // 复制请求头
    const headers = new Headers();
    const skipHeaders = new Set([
      'host', 'connection', 'content-length', 'transfer-encoding',
      'x-forwarded-for', 'x-forwarded-proto', 'x-forwarded-host',
      'x-real-ip', 'cf-connecting-ip', 'cf-ipcountry', 'cf-ray', 'cf-visitor',
      'x-middleware-request-', 'x-nextjs-data', 'x-invoke-output',
      'x-invoke-path', 'x-invoke-query', 'rsc', 'next-url',
    ]);
    request.headers.forEach((value, key) => {
      const lowerKey = key.toLowerCase();
      if (!skipHeaders.has(lowerKey) && !lowerKey.startsWith('x-middleware')) {
        headers.set(key, value);
      }
    });

    try {
      const backendHost = new URL(backendUrl).host;
      headers.set('Host', backendHost);
    } catch {}

    const sessionIdFromCookie = request.cookies.get('session_id')?.value;
    if (sessionIdFromCookie && !headers.has('x-session-id')) {
      headers.set('X-Session-Id', sessionIdFromCookie);
    }

    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 30000);

    const fetchOptions: RequestInit = {
      method,
      headers,
      redirect: 'manual',
      signal: controller.signal,
    };

    if (method !== 'GET' && method !== 'HEAD') {
      const contentType = request.headers.get('content-type') || '';
      if (contentType.includes('multipart/form-data')) {
        fetchOptions.body = await request.arrayBuffer();
        headers.delete('content-type');
      } else if (contentType.includes('application/json') || contentType.includes('text/')) {
        fetchOptions.body = await request.text();
      } else {
        fetchOptions.body = await request.arrayBuffer();
      }
    }

    console.log(`[Proxy] ${method} → ${targetUrl}`);

    const backendResponse = await fetch(targetUrl, fetchOptions);
    clearTimeout(timeoutId);

    console.log(`[Proxy] ← ${backendResponse.status} ${backendResponse.statusText}`);

    // 构建响应头
    const responseHeaders = new Headers();
    const responseSkipHeaders = new Set([
      'transfer-encoding', 'connection', 'keep-alive',
    ]);
    backendResponse.headers.forEach((value, key) => {
      const lowerKey = key.toLowerCase();
      if (!responseSkipHeaders.has(lowerKey)) {
        responseHeaders.set(key, value);
      }
    });

    // SSE 流式响应：透传流，并确保必要的头信息
    const responseContentType = backendResponse.headers.get('content-type') || '';
    if (responseContentType.includes('text/event-stream')) {
      // SSE 必须关闭缓存，保持连接
      responseHeaders.set('Cache-Control', 'no-cache');
      responseHeaders.set('Connection', 'keep-alive');
      // 确保CORS头存在（映射访问时可能需要）
      if (!responseHeaders.has('access-control-allow-origin')) {
        responseHeaders.set('Access-Control-Allow-Origin', request.headers.get('origin') || '*');
      }
      if (!responseHeaders.has('access-control-allow-credentials')) {
        responseHeaders.set('Access-Control-Allow-Credentials', 'true');
      }
      return new NextResponse(backendResponse.body, {
        status: backendResponse.status,
        headers: responseHeaders,
      });
    }

    // 计算URL替换前缀（根据请求来源判断是本地还是映射访问）
    const replacementPrefix = getReplacementPrefix(request);
    
    // 普通响应：改写响应体中的 localhost URL
    const responseBody = await backendResponse.arrayBuffer();
    const contentType = backendResponse.headers.get('content-type') || '';
    
    if (contentType.includes('application/json') || contentType.includes('text/')) {
      // 文本/JSON 响应：根据请求来源动态替换 localhost URL
      const bodyText = new TextDecoder().decode(responseBody);
      const rewrittenBody = rewriteResponseBody(bodyText, replacementPrefix);
      
      if (replacementPrefix !== '') {
        console.log(`[Proxy] URL替换: localhost:8080 → ${replacementPrefix || '(相对路径)'}`);
      }
      
      return new NextResponse(rewrittenBody, {
        status: backendResponse.status,
        headers: responseHeaders,
      });
    }
    
    // 二进制响应（图片等）：直接返回
    return new NextResponse(responseBody, {
      status: backendResponse.status,
      headers: responseHeaders,
    });
  } catch (error) {
    cachedBackendUrl = null;
    lastProbeTime = 0;
    
    const errMsg = error instanceof Error ? error.message : '代理请求失败';
    console.error(`[Proxy] 请求转发失败:`, errMsg);
    return NextResponse.json(
      {
        success: false,
        error: `后端请求失败: ${errMsg}`,
        message: '后端服务不可用，请确认 Java 后端已启动'
      },
      { status: 502 }
    );
  }
}

export async function GET(request: NextRequest) {
  return proxyRequest(request, 'GET');
}

export async function POST(request: NextRequest) {
  return proxyRequest(request, 'POST');
}

export async function PUT(request: NextRequest) {
  return proxyRequest(request, 'PUT');
}

export async function PATCH(request: NextRequest) {
  return proxyRequest(request, 'PATCH');
}

export async function DELETE(request: NextRequest) {
  return proxyRequest(request, 'DELETE');
}
