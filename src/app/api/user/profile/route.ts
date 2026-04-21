import { NextRequest, NextResponse } from 'next/server';
import { backendFetch, handleBackendResponse } from '@/lib/backend-proxy';

/**
 * @swagger
 * /api/user/profile:
 *   get:
 *     summary: 获取当前用户资料
 *     description: 获取当前登录用户的详细信息
 *     tags: [用户管理]
 *     security:
 *       - cookieAuth: []
 *     responses:
 *       200:
 *         description: 成功获取用户资料
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *               properties:
 *                 success:
 *                   type: boolean
 *                   example: true
 *                 data:
 *                   type: object
 *                   properties:
 *                     id:
 *                       type: string
 *                       description: 用户ID
 *                     username:
 *                       type: string
 *                       description: 用户名
 *                     email:
 *                       type: string
 *                       description: 邮箱
 *                     avatarUrl:
 *                       type: string
 *                       nullable: true
 *                       description: 头像URL
 *                     nickname:
 *                       type: string
 *                       description: 昵称
 *                     bio:
 *                       type: string
 *                       description: 个人简介
 *                     phone:
 *                       type: string
 *                       description: 手机号
 *                     role:
 *                       type: string
 *                       enum: [admin, user]
 *                       description: 用户角色
 *                     membership:
 *                       type: string
 *                       description: 会员等级
 *                     storageUsed:
 *                       type: integer
 *                       description: 已使用存储空间（字节）
 *                     storageLimit:
 *                       type: integer
 *                       description: 存储空间限制（字节）
 *       500:
 *         description: 获取失败
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *   put:
 *     summary: 更新用户资料
 *     description: 更新当前登录用户的个人资料
 *     tags: [用户管理]
 *     security:
 *       - cookieAuth: []
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             properties:
 *               username:
 *                 type: string
 *                 description: 用户名
 *               nickname:
 *                 type: string
 *                 description: 昵称
 *               email:
 *                 type: string
 *                 format: email
 *                 description: 邮箱
 *               avatar:
 *                 type: string
 *                 description: 头像URL
 *               bio:
 *                 type: string
 *                 description: 个人简介
 *               phone:
 *                 type: string
 *                 description: 手机号
 *     responses:
 *       200:
 *         description: 更新成功
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *               properties:
 *                 success:
 *                   type: boolean
 *                   example: true
 *                 data:
 *                   $ref: '#/components/schemas/User'
 *       500:
 *         description: 更新失败
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *   patch:
 *     summary: 部分更新用户资料
 *     description: 部分更新当前登录用户的个人资料
 *     tags: [用户管理]
 *     security:
 *       - cookieAuth: []
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             properties:
 *               username:
 *                 type: string
 *               nickname:
 *                 type: string
 *               email:
 *                 type: string
 *                 format: email
 *     responses:
 *       200:
 *         description: 更新成功
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *               properties:
 *                 success:
 *                   type: boolean
 *                   example: true
 *       500:
 *         description: 更新失败
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 */

export async function GET(request: NextRequest) {
  try {
    const headers: Record<string, string | null> = {
      'cookie': request.headers.get('cookie'),
      'x-session-id': request.headers.get('x-session-id'),
    };
    const response = await backendFetch('/user', { requestHeaders: headers });
    const result = await handleBackendResponse(response);
    
    if (result.success && result.data) {
      const user = result.data as Record<string, unknown>;
      
      return NextResponse.json({
        success: true,
        data: {
          id: user.id || 'user-1',
          username: user.username || 'User',
          email: user.email || 'user@example.com',
          avatarUrl: user.avatarUrl || user.avatar || null,
          nickname: user.nickname || user.username || 'User',
          bio: user.bio || '',
          phone: user.phone || '',
          role: user.role || 'user',
          membership: user.membership || 'free',
          storageUsed: user.storageUsed || 0,
          storageLimit: user.storageLimit || 5368709120,
        },
      });
    }
    
    return NextResponse.json(result, { status: 500 });
  } catch (error) {
    console.error('[API] 获取用户资料失败:', error);
    return NextResponse.json(
      { success: false, error: error instanceof Error ? error.message : '获取用户资料失败' },
      { status: 500 }
    );
  }
}

export async function PUT(request: NextRequest) {
  try {
    const headers: Record<string, string | null> = {
      'cookie': request.headers.get('cookie'),
      'x-session-id': request.headers.get('x-session-id'),
    };
    const body = await request.json();
    
    const response = await backendFetch('/user/profile', {
      method: 'PUT',
      body: {
        username: body.username,
        nickname: body.nickname,
        email: body.email,
        avatar: body.avatar,
        bio: body.bio,
        phone: body.phone,
      },
      requestHeaders: headers,
    });
    const result = await handleBackendResponse(response);
    
    return NextResponse.json(result, { status: result.success ? 200 : 500 });
  } catch (error) {
    console.error('[API] 更新用户资料失败:', error);
    return NextResponse.json(
      { success: false, error: error instanceof Error ? error.message : '更新用户资料失败' },
      { status: 500 }
    );
  }
}

export async function PATCH(request: NextRequest) {
  return PUT(request);
}
