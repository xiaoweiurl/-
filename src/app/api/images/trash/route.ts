import { NextRequest, NextResponse } from 'next/server';
import { backendFetch, handleBackendResponse } from '@/lib/backend-proxy';

/**
 * @swagger
 * /api/images/trash:
 *   get:
 *     summary: 获取回收站图片列表
 *     description: 获取已删除的图片列表（软删除）
 *     tags: [图片管理]
 *     security:
 *       - cookieAuth: []
 *     parameters:
 *       - in: query
 *         name: page
 *         schema:
 *           type: integer
 *           default: 1
 *         description: 页码
 *       - in: query
 *         name: pageSize
 *         schema:
 *           type: integer
 *           default: 20
 *         description: 每页数量
 *     responses:
 *       200:
 *         description: 成功获取回收站列表
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
 *                     list:
 *                       type: array
 *                       items:
 *                         $ref: '#/components/schemas/Image'
 *                     total:
 *                       type: integer
 *                     page:
 *                       type: integer
 *                     pageSize:
 *                       type: integer
 *       500:
 *         description: 获取失败
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *   post:
 *     summary: 恢复回收站图片
 *     description: 将选中的图片从回收站恢复
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
 *             properties:
 *               imageIds:
 *                 type: array
 *                 items:
 *                   type: integer
 *                 description: 要恢复的图片ID列表
 *     responses:
 *       200:
 *         description: 恢复成功
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
 *         description: 恢复失败
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *   delete:
 *     summary: 清空回收站
 *     description: 永久删除回收站中的所有图片
 *     tags: [图片管理]
 *     security:
 *       - cookieAuth: []
 *     responses:
 *       200:
 *         description: 清空成功
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *               properties:
 *                 success:
 *                   type: boolean
 *                   example: true
 *       500:
 *         description: 清空失败
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 */
export async function GET(request: NextRequest) {
  try {
    const { searchParams } = new URL(request.url);
    const page = searchParams.get('page') || '1';
    const pageSize = searchParams.get('pageSize') || '20';
    const cookieHeader = request.headers.get('cookie') || '';
    
    const requestHeaders: Record<string, string | null> = {
      cookie: cookieHeader,
    };
    
    const response = await backendFetch(`/images/trash?page=${page}&pageSize=${pageSize}`, { requestHeaders });
    const result = await handleBackendResponse(response);
    
    if (result.success && result.data) {
      const data = result.data as Record<string, unknown>;
      return NextResponse.json({
        success: true,
        data: {
          list: data.list || data.content || [],
          total: data.total || data.totalElements || 0,
          page: data.page || page,
          pageSize: data.pageSize || pageSize,
        },
      });
    }
    
    return NextResponse.json(result, { status: result.success ? 200 : 500 });
  } catch (error) {
    console.error('[API] 获取回收站失败:', error);
    return NextResponse.json(
      { success: false, error: error instanceof Error ? error.message : '获取回收站失败' },
      { status: 500 }
    );
  }
}
export async function POST(request: NextRequest) {
  try {
    const body = await request.json();
    const { imageIds } = body;
    const cookieHeader = request.headers.get('cookie') || '';
    
    if (!imageIds || !Array.isArray(imageIds) || imageIds.length === 0) {
      return NextResponse.json(
        { success: false, error: '请选择要恢复的图片' },
        { status: 400 }
      );
    }
    
    const response = await backendFetch('/images/trash/restore', {
      method: 'POST',
      body: { imageIds },
      requestHeaders: {
        cookie: cookieHeader,
      },
    });
    const result = await handleBackendResponse(response);
    
    return NextResponse.json(result, { status: result.success ? 200 : 500 });
  } catch (error) {
    console.error('[API] 恢复图片失败:', error);
    return NextResponse.json(
      { success: false, error: error instanceof Error ? error.message : '恢复图片失败' },
      { status: 500 }
    );
  }
}

export async function DELETE(request: NextRequest) {
  try {
    const cookieHeader = request.headers.get('cookie') || '';
    
    const response = await backendFetch('/images/trash', {
      method: 'DELETE',
      requestHeaders: {
        cookie: cookieHeader,
      },
    });
    const result = await handleBackendResponse(response);
    
    return NextResponse.json(result, { status: result.success ? 200 : 500 });
  } catch (error) {
    console.error('[API] 清空回收站失败:', error);
    return NextResponse.json(
      { success: false, error: error instanceof Error ? error.message : '清空回收站失败' },
      { status: 500 }
    );
  }
}
