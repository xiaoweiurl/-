import { NextRequest, NextResponse } from 'next/server';
import { imageApi, handleBackendResponse } from '@/lib/backend-proxy';

/**
 * @swagger
 * /api/images/classify:
 *   post:
 *     summary: 分类图片
 *     description: 将选中的图片移动到指定分类，支持AI智能分类
 *     tags: [图片管理]
 *     security:
 *       - cookieAuth: []
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required:
 *               - imageIds
 *               - targetCategory
 *             properties:
 *               imageIds:
 *                 type: array
 *                 items:
 *                   type: integer
 *                 description: 图片ID列表
 *               targetCategory:
 *                 type: string
 *                 description: 目标分类名称
 *               useAI:
 *                 type: boolean
 *                 description: 是否使用AI分类
 *     responses:
 *       200:
 *         description: 分类成功
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *               properties:
 *                 success:
 *                   type: boolean
 *                   example: true
 *       400:
 *         description: 参数错误
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *       500:
 *         description: 分类失败
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 */
export async function POST(request: NextRequest) {
  try {
    const cookieHeader = request.headers.get('cookie') || '';
    const requestHeaders: Record<string, string | null> = {
      cookie: cookieHeader,
    };
    const body = await request.json();
    const { imageIds, targetCategory, useAI } = body;
    
    if (!imageIds || !Array.isArray(imageIds) || imageIds.length === 0) {
      return NextResponse.json(
        { success: false, error: '请选择要分类的图片' },
        { status: 400 }
      );
    }
    
    if (!targetCategory) {
      return NextResponse.json(
        { success: false, error: '请选择目标分类' },
        { status: 400 }
      );
    }
    
    const response = await imageApi.classify(imageIds, targetCategory, useAI, requestHeaders);
    const result = await handleBackendResponse(response);
    
    return NextResponse.json(result, { status: result.success ? 200 : 500 });
  } catch (error) {
    console.error('[API] 分类图片失败:', error);
    return NextResponse.json(
      { success: false, error: error instanceof Error ? error.message : '分类图片失败' },
      { status: 500 }
    );
  }
}
