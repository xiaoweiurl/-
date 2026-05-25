import { NextRequest, NextResponse } from 'next/server';
import { backendFetch, handleBackendResponse, isBackendAvailable } from '@/lib/backend-proxy';
import { loginSchema } from '@/lib/api-schemas';

/**
 * @swagger
 * /api/auth/login:
 *   get:
 *     summary: 验证登录状态
 *     description: 检查当前用户的登录状态和会话有效性
 *     tags: [认证]
 *     security:
 *       - cookieAuth: []
 *     responses:
 *       200:
 *         description: 已登录
 *       401:
 *         description: 未登录
 *   post:
 *     summary: 用户登录
 *     description: 使用用户名和密码登录系统
 *     tags: [认证]
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required:
 *               - username
 *               - password
 *             properties:
 *               username:
 *                 type: string
 *                 description: 用户名
 *               password:
 *                 type: string
 *                 description: 密码
 *               rememberMe:
 *                 type: boolean
 *                 description: 记住我（7天有效期）
 *     responses:
 *       200:
 *         description: 登录成功
 *       401:
 *         description: 用户名或密码错误
 *   delete:
 *     summary: 用户登出
 *     description: 清除会话，退出登录
 *     tags: [认证]
 *     responses:
 *       200:
 *         description: 登出成功
 */

export async function GET(request: NextRequest) {
  try {
    // 检查后端是否可用
    const backendAvailable = await isBackendAvailable();
    
    if (!backendAvailable) {
      console.error('[API] 后端服务不可用');
      return NextResponse.json({
        success: false,
        error: '后端服务不可用，请稍后重试',
      }, { status: 503 });
    }
    
    // 从 cookie 获取 session_id
    const cookieHeader = request.headers.get('cookie') || '';
    const sessionId = extractSessionIdFromCookie(cookieHeader);
    console.log('[API] 验证会话，sessionId:', sessionId?.substring(0, 8) + '...');
    
    // 调用后端验证会话
    const response = await backendFetch('/auth/session', {
      requestHeaders: {
        cookie: cookieHeader,
      },
    });
    
    console.log('[API] 验证会话，后端响应状态:', response.status);
    
    const result = await handleBackendResponse(response);
    console.log('[API] 验证会话，结果:', result);
    
    return NextResponse.json(result, { status: result.success ? 200 : 401 });
  } catch (error) {
    console.error('[API] 验证会话失败:', error);
    return NextResponse.json(
      { success: false, error: error instanceof Error ? error.message : '验证会话失败' },
      { status: 500 }
    );
  }
}

/**
 * 从 cookie 字符串中提取 session_id
 */
function extractSessionIdFromCookie(cookieHeader: string): string | null {
  if (!cookieHeader) return null;
  const match = cookieHeader.match(/session_id=([^;]+)/);
  return match ? match[1] : null;
}

export async function POST(request: NextRequest) {
  try {
    const body = await request.json();
    
    // 输入验证
    const validation = loginSchema.safeParse(body);
    if (!validation.success) {
      return NextResponse.json({
        success: false,
        error: validation.error.issues[0]?.message || '输入参数无效',
      }, { status: 400 });
    }
    
    const { username, password, rememberMe } = validation.data;
    
    // 检查后端是否可用
    const backendAvailable = await isBackendAvailable();
    
    if (!backendAvailable) {
      console.error('[API] 后端服务不可用');
      return NextResponse.json({
        success: false,
        error: '后端服务不可用，请稍后重试',
      }, { status: 503 });
    }
    
    // 调用后端登录
    const response = await backendFetch('/auth/login', {
      method: 'POST',
      body: { username, password, rememberMe },
    });
    
    // 打印后端响应信息
    console.log('[API] 登录，后端响应状态:', response.status);
    
    // 从响应头获取 sessionId（更可靠的方式）
    const sessionIdFromHeader = response.headers.get('X-Session-Id');
    console.log('[API] 登录，从响应头 X-Session-Id 获取:', sessionIdFromHeader?.substring(0, 8) + '...');
    
    const result = await response.json();
    
    if (result.success || result.code === 200) {
      // 优先使用从响应头获取的 sessionId
      const finalSessionId = sessionIdFromHeader || result.data?.sessionId;
      const user = result.data?.user;
      
      if (!finalSessionId) {
        console.error('[API] 后端响应缺少 sessionId');
        return NextResponse.json({
          success: false,
          error: '登录响应无效',
        }, { status: 500 });
      }
      
      console.log('[API] 登录成功，使用 sessionId:', finalSessionId.substring(0, 8) + '...');
      console.log('[API] 即将设置 cookie，domain:', undefined, 'sameSite:', 'lax');
      
      // 创建响应并直接设置 cookie
      const response = NextResponse.json({
        success: true,
        message: result.message || '登录成功',
        data: {
          sessionId: finalSessionId,
          user,
        },
      });
      
      // 判断是否使用 HTTPS（包括 ngrok）
      const requestUrl = new URL(request.url);
      const isHttps = requestUrl.protocol === 'https:' || 
                      request.headers.get('x-forwarded-proto') === 'https' ||
                      request.headers.get('host')?.includes('ngrok');
      
      console.log('[API] 协议检测 - isHttps:', isHttps, 'host:', request.headers.get('host'));
      
      // 设置 session cookie（使用 ResponseCookies API）
      response.cookies.set('session_id', finalSessionId, {
        httpOnly: true,
        secure: isHttps, // HTTPS 或 ngrok 时必须设置 Secure
        sameSite: 'lax',
        maxAge: rememberMe ? 7 * 24 * 60 * 60 : 24 * 60 * 60,
        path: '/',
      });
      
      // 打印 cookie 设置后的响应头
      const setCookieHeader = response.headers.get('set-cookie');
      console.log('[API] 设置的 Set-Cookie 头:', setCookieHeader);
      
      // 如果响应头为空，手动添加
      if (!setCookieHeader) {
        const cookieValue = `session_id=${finalSessionId}; Path=/; HttpOnly; Max-Age=${rememberMe ? 7 * 24 * 60 * 60 : 24 * 60 * 60}; SameSite=Lax`;
        response.headers.append('Set-Cookie', cookieValue);
        console.log('[API] 手动添加 Set-Cookie:', cookieValue);
      }
      
      return response;
    }
    
    // 处理后端返回的错误
    return NextResponse.json(
      { success: false, error: result.error || result.message || '登录失败' },
      { status: 401 }
    );
  } catch (error) {
    console.error('[API] 登录失败:', error);
    return NextResponse.json(
      { success: false, error: error instanceof Error ? error.message : '登录失败' },
      { status: 500 }
    );
  }
}

export async function DELETE() {
  // 调用后端登出
  try {
    await backendFetch('/auth/logout', { method: 'POST' });
  } catch (error) {
    console.error('[API] 登出失败:', error);
  }
  
  // 返回响应（前端会清除 localStorage）
  return NextResponse.json({
    success: true,
    message: '已退出登录',
  });
}
