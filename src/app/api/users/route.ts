import { NextRequest, NextResponse } from 'next/server';
import { adminApi, backendFetch, handleBackendResponse } from '@/lib/backend-proxy';

/**
 * 从请求中提取 sessionId（支持 cookie 和 header）
 */
function extractSessionId(request: NextRequest): Record<string, string | null> {
  // 1. 直接从 header 获取 X-Session-Id
  const xSessionId = request.headers.get('x-session-id');
  if (xSessionId) {
    return { 'x-session-id': xSessionId, 'cookie': null };
  }
  
  // 2. 从 cookie 中提取 session_id
  const cookieHeader = request.headers.get('cookie') || '';
  const sessionIdMatch = cookieHeader.match(/session_id=([^;]+)/);
  if (sessionIdMatch) {
    return { 'x-session-id': sessionIdMatch[1], 'cookie': cookieHeader };
  }
  
  return { 'x-session-id': null, 'cookie': cookieHeader };
}

/**
 * @swagger
 * /api/users:
 *   get:
 *     summary: 获取用户列表
 *     description: 获取所有用户列表（仅管理员）
 *     tags: [用户管理]
 *     security:
 *       - cookieAuth: []
 *     responses:
 *       200:
 *         description: 成功获取用户列表
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *               properties:
 *                 success:
 *                   type: boolean
 *                   example: true
 *                 data:
 *                   type: array
 *                   items:
 *                     $ref: '#/components/schemas/User'
 *       500:
 *         description: 获取失败
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *   post:
 *     summary: 创建用户
 *     description: 创建新用户（仅管理员）
 *     tags: [用户管理]
 *     security:
 *       - cookieAuth: []
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
 *               email:
 *                 type: string
 *                 format: email
 *                 description: 邮箱
 *               role:
 *                 type: string
 *                 enum: [admin, user]
 *                 description: 用户角色
 *     responses:
 *       200:
 *         description: 创建成功
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *               properties:
 *                 success:
 *                   type: boolean
 *                   example: true
 *       500:
 *         description: 创建失败
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 */
export async function GET(request: NextRequest) {
  try {
    const headers = extractSessionId(request);
    console.log('[API] /api/users - sessionId from header:', headers['x-session-id']?.substring(0, 8) + '...');
    const response = await adminApi.getUsers(headers);
    const result = await handleBackendResponse(response);
    
    return NextResponse.json(result, { status: result.success ? 200 : 500 });
  } catch (error) {
    console.error('[API] 获取用户列表失败:', error);
    return NextResponse.json(
      { success: false, error: error instanceof Error ? error.message : '获取用户列表失败' },
      { status: 500 }
    );
  }
}

/**
 * @swagger
 * POST - 创建用户
 * 代理到 Java 后端: POST /api/admin/users
 */
export async function POST(request: NextRequest) {
  try {
    const body = await request.json();
    const headers = extractSessionId(request);
    console.log('[API] /api/users POST - sessionId:', headers['x-session-id']?.substring(0, 8) + '...');
    const response = await backendFetch('/admin/users', {
      method: 'POST',
      body,
      requestHeaders: headers,
    });
    const result = await handleBackendResponse(response);
    
    return NextResponse.json(result, { status: result.success ? 200 : 500 });
  } catch (error) {
    console.error('[API] 创建用户失败:', error);
    return NextResponse.json(
      { success: false, error: error instanceof Error ? error.message : '创建用户失败' },
      { status: 500 }
    );
  }
}
