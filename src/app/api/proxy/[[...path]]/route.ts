import { NextRequest, NextResponse } from 'next/server';

/**
 * 通用后端 API 代理
 * 
 * 动态识别后端地址策略：
 * 1. 优先使用环境变量 BACKEND_API_URL（服务端专用，不在客户端暴露）
 * 2. 请求来自 localhost → 使用 http://localhost:8080/api
 * 3. 请求来自映射域名 → 同域名走 /api（浏览器直接请求同域后端）
 * 4. 缓存可用的后端地址，避免重复探测
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
  
  // 2. 根据 Host 头判断
  const host = request.headers.get('host') || '';
  const isLocalhost = host.startsWith('localhost') || host.startsWith('127.0.0.1') || host.startsWith('0.0.0.0');
  
  if (isLocalhost) {
    // 本地访问：Java后端就在 localhost:8080
    candidates.push('http://localhost:8080/api');
  } else {
    // 映射域名访问：
    // 用户的映射规则：8080 → 同域名:80, 5000 → 同域名:8000
    // 尝试同域名的 80 端口（默认端口，URL中不写端口）
    const hostname = host.split(':')[0];
    candidates.push(`http://${hostname}/api`);
    candidates.push(`http://${hostname}:8080/api`);
    // 也尝试 localhost（可能 Next.js 和 Java 在同一台机器）
    candidates.push('http://localhost:8080/api');
    // 尝试 https
    candidates.push(`https://${hostname}/api`);
  }
  
  // 去重
  return [...new Set(candidates)];
}

/**
 * 探测可用的后端地址
 */
async function probeBackend(url: string): Promise<boolean> {
  try {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 5000);
    
    const response = await fetch(`${url}/albums`, {
      method: 'GET',
      signal: controller.signal,
      headers: {
        'Accept': 'application/json',
      },
    });
    
    clearTimeout(timeoutId);
    // 200/401/403/404/500 都说明后端在运行（只是业务逻辑不同）
    // 只有网络错误才会抛异常
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
  
  // 缓存有效
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
    // 构建后端 URL：/api/proxy/auth/login → http://xxx/api/auth/login
    const path = request.nextUrl.pathname.replace('/api/proxy', '');
    const searchParams = request.nextUrl.search;
    const targetUrl = `${backendUrl}${path}${searchParams}`;

    // 复制请求头，过滤掉 Next.js/代理相关的头
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

    // 设置正确的 Host 头（匹配后端地址）
    try {
      const backendHost = new URL(backendUrl).host;
      headers.set('Host', backendHost);
    } catch {
      // ignore
    }

    // 确保 X-Session-Id 传递（从 cookie 或 header）
    const sessionIdFromCookie = request.cookies.get('session_id')?.value;
    if (sessionIdFromCookie && !headers.has('x-session-id')) {
      headers.set('X-Session-Id', sessionIdFromCookie);
    }

    // 构建请求选项
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 30000);

    const fetchOptions: RequestInit = {
      method,
      headers,
      redirect: 'manual',
      signal: controller.signal,
    };

    // 非 GET/HEAD 请求传递 body
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

    // 处理 SSE 流式响应
    const responseContentType = backendResponse.headers.get('content-type') || '';
    if (responseContentType.includes('text/event-stream')) {
      return new NextResponse(backendResponse.body, {
        status: backendResponse.status,
        headers: responseHeaders,
      });
    }

    // 普通响应
    const responseBody = await backendResponse.arrayBuffer();
    return new NextResponse(responseBody, {
      status: backendResponse.status,
      headers: responseHeaders,
    });
  } catch (error) {
    // 探测失败，清除缓存，下次请求重新探测
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
