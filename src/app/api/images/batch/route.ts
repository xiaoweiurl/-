import { NextRequest, NextResponse } from 'next/server';
import { backendFetch, handleBackendResponse } from '@/lib/backend-proxy';

/**
 * @swagger
 * /api/images/batch:
 *   post:
 *     summary: 批量操作图片
 *     description: 对选中的图片进行批量操作（删除、收藏、移动）
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
 *               - operation
 *               - imageIds
 *             properties:
 *               operation:
 *                 type: string
 *                 enum: [delete, favorite, move]
 *                 description: 操作类型
 *               imageIds:
 *                 type: array
 *                 items:
 *                   type: integer
 *                 description: 图片ID列表
 *               targetAlbumId:
 *                 type: integer
 *                 description: 目标相册ID（移动操作时必填）
 *     responses:
 *       200:
 *         description: 操作成功
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *               properties:
 *                 success:
 *                   type: boolean
 *                   example: true
 *                 message:
 *                   type: string
 *                   example: 批量删除成功
 *       400:
 *         description: 参数错误
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *       500:
 *         description: 操作失败
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 */
export async function POST(request: NextRequest) {
  try {
    const body = await request.json();
    const { operation, imageIds, targetAlbumId } = body;
    const cookieHeader = request.headers.get('cookie') || '';
    
    if (!operation) {
      return NextResponse.json(
        { success: false, error: '请指定操作类型' },
        { status: 400 }
      );
    }
    
    if (!imageIds || !Array.isArray(imageIds) || imageIds.length === 0) {
      return NextResponse.json(
        { success: false, error: '请选择要操作的图片' },
        { status: 400 }
      );
    }
    
    const validOperations = ['delete', 'favorite', 'move'];
    if (!validOperations.includes(operation)) {
      return NextResponse.json(
        { success: false, error: '不支持的操作类型' },
        { status: 400 }
      );
    }
    
    if (operation === 'move' && !targetAlbumId) {
      return NextResponse.json(
        { success: false, error: '请选择目标相册' },
        { status: 400 }
      );
    }
    
    const response = await backendFetch('/images/batch', {
      method: 'POST',
      body: { operation, imageIds, targetAlbumId },
      requestHeaders: {
        cookie: cookieHeader,
      },
    });
    const result = await handleBackendResponse(response);
    
    return NextResponse.json(result, { status: result.success ? 200 : 500 });
  } catch (error) {
    console.error('[API] 批量操作失败:', error);
    return NextResponse.json(
      { success: false, error: error instanceof Error ? error.message : '批量操作失败' },
      { status: 500 }
    );
  }
}
