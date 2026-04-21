import { NextRequest, NextResponse } from 'next/server';
import { backendFetchFormData } from '@/lib/backend-proxy';

/**
 * @swagger
 * /api/user/avatar:
 *   post:
 *     summary: 上传用户头像
 *     description: 上传并更新当前用户的头像图片
 *     tags: [用户管理]
 *     security:
 *       - cookieAuth: []
 *     requestBody:
 *       required: true
 *       content:
 *         multipart/form-data:
 *           schema:
 *             type: object
 *             properties:
 *               file:
 *                 type: string
 *                 format: binary
 *                 description: 头像图片文件（最大2MB，支持JPG/PNG/GIF）
 *     responses:
 *       200:
 *         description: 上传成功
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *               properties:
 *                 success:
 *                   type: boolean
 *                   example: true
 *                 avatarUrl:
 *                   type: string
 *                   description: 新头像URL
 *       400:
 *         description: 文件无效（类型错误或超过大小限制）
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *       503:
 *         description: 后端服务不可用
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 */
export async function POST(request: NextRequest) {
  try {
    // 获取表单数据
    const formData = await request.formData();
    const file = formData.get('file') as File;
    
    if (!file) {
      return NextResponse.json(
        { success: false, error: '请选择要上传的文件' },
        { status: 400 }
      );
    }
    
    // 验证文件类型
    if (!file.type.startsWith('image/')) {
      return NextResponse.json(
        { success: false, error: '请上传图片文件' },
        { status: 400 }
      );
    }
    
    // 验证文件大小（最大2MB）
    if (file.size > 2 * 1024 * 1024) {
      return NextResponse.json(
        { success: false, error: '图片大小不能超过2MB' },
        { status: 400 }
      );
    }
    
    // 创建新的 FormData 用于转发
    const backendFormData = new FormData();
    backendFormData.append('file', file);
    
    // 从请求中提取 sessionId
    const headers: Record<string, string | null> = {
      'cookie': request.headers.get('cookie'),
      'x-session-id': request.headers.get('x-session-id'),
    };
    
    const response = await backendFetchFormData('/user/avatar', backendFormData, headers);
    const result = await response.json();
    
    return NextResponse.json(result, { status: response.status });
  } catch (error) {
    console.error('[API] 上传头像失败:', error);
    return NextResponse.json(
      { success: false, error: error instanceof Error ? error.message : '上传头像失败' },
      { status: 500 }
    );
  }
}
