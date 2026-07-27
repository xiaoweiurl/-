import { NextRequest, NextResponse } from 'next/server';
import { backendFetch, handleBackendResponse, isBackendAvailable } from '@/lib/backend-proxy';

/**
 * @swagger
 * /api/auth/forgot-password:
 *   get:
 *     summary: 根据用户名获取用户验证信息
 *     description: 找回密码第一步，根据用户名获取脱敏的邮箱/手机号用于身份验证
 *     tags: [认证]
 *     parameters:
 *       - in: query
 *         name: username
 *         required: true
 *         schema:
 *           type: string
 *         description: 用户名
 *     responses:
 *       200:
 *         description: 成功获取用户验证信息
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *               properties:
 *                 success:
 *                   type: boolean
 *                 data:
 *                   type: object
 *                   properties:
 *                     username:
 *                       type: string
 *                     maskedEmail:
 *                       type: string
 *                       description: 脱敏邮箱，如 a***@example.com
 *                     maskedPhone:
 *                       type: string
 *                       description: 脱敏手机号，如 138****1234
 *                     hasEmail:
 *                       type: boolean
 *                     hasPhone:
 *                       type: boolean
 *       404:
 *         description: 用户不存在
 *   post:
 *     summary: 重置密码
 *     description: 找回密码第二步，验证身份后设置新密码
 *     tags: [认证]
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required:
 *               - username
 *               - newPassword
 *               - confirmPassword
 *             properties:
 *               username:
 *                 type: string
 *                 description: 用户名
 *               verifyValue:
 *                 type: string
 *                 description: 验证值（邮箱或手机号原文）
 *               verifyType:
 *                 type: string
 *                 enum: [email, phone]
 *                 description: 验证类型
 *               newPassword:
 *                 type: string
 *                 description: 新密码（至少6位）
 *               confirmPassword:
 *                 type: string
 *                 description: 确认新密码
 *     responses:
 *       200:
 *         description: 密码重置成功
 *       400:
 *         description: 参数错误或验证失败
 *       500:
 *         description: 服务器错误
 */

export async function GET(request: NextRequest) {
  try {
    const { searchParams } = new URL(request.url);
    const username = searchParams.get('username');

    if (!username || !username.trim()) {
      return NextResponse.json(
        { success: false, error: '请输入用户名' },
        { status: 400 }
      );
    }

    // 检查后端是否可用
    const backendAvailable = await isBackendAvailable();
    if (!backendAvailable) {
      return NextResponse.json(
        { success: false, error: '后端服务不可用，无法找回密码，请联系管理员' },
        { status: 503 }
      );
    }

    // 调用后端获取用户验证信息
    const response = await backendFetch(`/auth/forgot-password/user-info?username=${encodeURIComponent(username.trim())}`, {
      method: 'GET',
    });

    const result = await handleBackendResponse(response);

    if (result.success && result.data) {
      return NextResponse.json(result);
    }

    return NextResponse.json(
      { success: false, error: result.error || '用户不存在' },
      { status: 404 }
    );
  } catch (error) {
    console.error('[API] 获取用户验证信息失败:', error);
    return NextResponse.json(
      { success: false, error: error instanceof Error ? error.message : '获取用户信息失败' },
      { status: 500 }
    );
  }
}

export async function POST(request: NextRequest) {
  try {
    const body = await request.json();
    const { username, verifyValue, verifyType, newPassword, confirmPassword } = body;

    // 参数验证
    if (!username || !username.trim()) {
      return NextResponse.json(
        { success: false, error: '请输入用户名' },
        { status: 400 }
      );
    }

    if (!verifyValue || !verifyValue.trim()) {
      return NextResponse.json(
        { success: false, error: '请输入验证信息（邮箱或手机号）' },
        { status: 400 }
      );
    }

    if (!newPassword || newPassword.length < 6) {
      return NextResponse.json(
        { success: false, error: '新密码长度至少6位' },
        { status: 400 }
      );
    }

    if (newPassword !== confirmPassword) {
      return NextResponse.json(
        { success: false, error: '两次输入的密码不一致' },
        { status: 400 }
      );
    }

    // 检查后端是否可用
    const backendAvailable = await isBackendAvailable();
    if (!backendAvailable) {
      return NextResponse.json(
        { success: false, error: '后端服务不可用，无法重置密码，请联系管理员' },
        { status: 503 }
      );
    }

    // 调用后端重置密码
    const response = await backendFetch('/auth/forgot-password/reset', {
      method: 'POST',
      body: {
        username,
        verifyValue,
        verifyType: verifyType || 'email',
        newPassword,
        confirmPassword,
      },
    });

    const result = await handleBackendResponse(response);

    if (result.success) {
      return NextResponse.json({
        success: true,
        message: '密码重置成功，请使用新密码登录',
      });
    }

    return NextResponse.json(
      { success: false, error: result.error || '密码重置失败' },
      { status: 400 }
    );
  } catch (error) {
    console.error('[API] 重置密码失败:', error);
    return NextResponse.json(
      { success: false, error: error instanceof Error ? error.message : '重置密码失败' },
      { status: 500 }
    );
  }
}
