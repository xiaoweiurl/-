import { NextRequest, NextResponse } from 'next/server';
import { aiApi, handleBackendResponse } from '@/lib/backend-proxy';

/**
 * @swagger
 * /api/ai/recognize:
 *   post:
 *     summary: AI 识别图片
 *     description: 使用 AI 能力识别图片内容，支持关键词匹配和视觉识别
 *     tags: [AI 识别]
 *     security:
 *       - cookieAuth: []
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required:
 *               - imageUrls
 *             properties:
 *               imageUrls:
 *                 type: array
 *                 items:
 *                   type: string
 *                 description: 要识别的图片URL列表
 *               useKeywordMatch:
 *                 type: boolean
 *                 default: true
 *                 description: 是否使用关键词匹配
 *               useVisionRecognition:
 *                 type: boolean
 *                 default: true
 *                 description: 是否使用视觉识别（豆包Vision模型）
 *     responses:
 *       200:
 *         description: 识别成功
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
 *                     category:
 *                       type: string
 *                       description: 识别的分类
 *                     tags:
 *                       type: array
 *                       items:
 *                         type: string
 *                       description: 识别的标签
 *                     confidence:
 *                       type: number
 *                       description: 置信度
 *                     description:
 *                       type: string
 *                       description: 图片描述
 *       400:
 *         description: 参数错误
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *       500:
 *         description: 识别失败
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
    const { imageUrls, useKeywordMatch, useVisionRecognition } = body;
    
    if (!imageUrls || !Array.isArray(imageUrls) || imageUrls.length === 0) {
      return NextResponse.json(
        { success: false, error: '请提供要识别的图片' },
        { status: 400 }
      );
    }
    
    const response = await aiApi.recognize(imageUrls, useKeywordMatch, useVisionRecognition, requestHeaders);
    const result = await handleBackendResponse(response);
    
    return NextResponse.json(result, { status: result.success ? 200 : 500 });
  } catch (error) {
    console.error('[API] AI 识别失败:', error);
    return NextResponse.json(
      { success: false, error: error instanceof Error ? error.message : 'AI 识别失败' },
      { status: 500 }
    );
  }
}
