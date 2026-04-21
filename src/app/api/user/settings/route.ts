import { NextRequest, NextResponse } from 'next/server';
import { backendFetch, handleBackendResponse } from '@/lib/backend-proxy';

/**
 * @swagger
 * /api/user/settings:
 *   get:
 *     summary: 获取用户设置
 *     description: 获取当前用户的所有设置项
 *     tags: [用户管理]
 *     security:
 *       - cookieAuth: []
 *     responses:
 *       200:
 *         description: 成功获取用户设置
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
 *                     theme:
 *                       type: string
 *                       enum: [light, dark, system]
 *                       description: 主题模式
 *                     language:
 *                       type: string
 *                       description: 语言设置
 *                     pageSize:
 *                       type: integer
 *                       description: 每页显示数量
 *                     defaultSort:
 *                       type: string
 *                       description: 默认排序字段
 *                     aiRecognitionEnabled:
 *                       type: boolean
 *                       description: AI识别开关
 *                     emailNotifications:
 *                       type: boolean
 *                       description: 邮件通知开关
 *                     systemNotifications:
 *                       type: boolean
 *                       description: 系统通知开关
 *                     uploadNotifications:
 *                       type: boolean
 *                       description: 上传通知开关
 *                     autoPlayVideos:
 *                       type: boolean
 *                       description: 自动播放视频
 *                     highQualityPreviews:
 *                       type: boolean
 *                       description: 高质量预览
 *                     compactMode:
 *                       type: boolean
 *                       description: 紧凑模式
 *                     showFileInfo:
 *                       type: boolean
 *                       description: 显示文件信息
 *                     defaultView:
 *                       type: string
 *                       enum: [grid, list]
 *                       description: 默认视图
 *   put:
 *     summary: 更新用户设置
 *     description: 更新当前用户的设置项
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
 *               theme:
 *                 type: string
 *                 enum: [light, dark, system]
 *               language:
 *                 type: string
 *               pageSize:
 *                 type: integer
 *               defaultSort:
 *                 type: string
 *               aiRecognitionEnabled:
 *                 type: boolean
 *               emailNotifications:
 *                 type: boolean
 *               systemNotifications:
 *                 type: boolean
 *               uploadNotifications:
 *                 type: boolean
 *               autoPlayVideos:
 *                 type: boolean
 *               highQualityPreviews:
 *                 type: boolean
 *               compactMode:
 *                 type: boolean
 *               showFileInfo:
 *                 type: boolean
 *               defaultView:
 *                 type: string
 *                 enum: [grid, list]
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
 *   patch:
 *     summary: 部分更新用户设置
 *     description: 部分更新当前用户的设置项
 *     tags: [用户管理]
 *     security:
 *       - cookieAuth: []
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             description: 部分设置字段
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
    
    const response = await backendFetch('/user/settings', { requestHeaders: headers });
    const result = await handleBackendResponse(response);
    
    return NextResponse.json(result, { status: result.success ? 200 : 500 });
  } catch (error) {
    console.error('[API] 获取用户设置失败:', error);
    return NextResponse.json(
      { success: false, error: error instanceof Error ? error.message : '获取用户设置失败' },
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
    
    const response = await backendFetch('/user/settings', {
      method: 'PUT',
      body,
      requestHeaders: headers,
    });
    const result = await handleBackendResponse(response);
    
    return NextResponse.json(result, { status: result.success ? 200 : 500 });
  } catch (error) {
    console.error('[API] 更新用户设置失败:', error);
    return NextResponse.json(
      { success: false, error: error instanceof Error ? error.message : '更新用户设置失败' },
      { status: 500 }
    );
  }
}

export async function PATCH(request: NextRequest) {
  return PUT(request);
}
