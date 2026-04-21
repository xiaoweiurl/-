/**
 * 商品详情 API
 * 代理到 Java 后端: GET /api/products/{id}
 * 代理到 Java 后端: GET /api/products/{id}/images
 */

import { NextRequest, NextResponse } from 'next/server';
import { backendFetch, handleBackendResponse } from '@/lib/backend-proxy';

/**
 * @swagger
 * /api/products/{id}:
 *   get:
 *     summary: 获取商品详情
 *     description: 根据商品ID获取商品详细信息（包含所有图片）
 *     tags: [商品管理]
 *     security:
 *       - cookieAuth: []
 *     parameters:
 *       - in: path
 *         name: id
 *         required: true
 *         schema:
 *           type: integer
 *         description: 商品ID
 *     responses:
 *       200:
 *         description: 成功获取商品详情
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *               properties:
 *                 success:
 *                   type: boolean
 *                   example: true
 *                 data:
 *                   $ref: '#/components/schemas/Product'
 *       404:
 *         description: 商品不存在
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *       500:
 *         description: 获取失败
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 */
export async function GET(
  request: NextRequest,
  context: { params: Promise<{ id: string }> }
) {
  try {
    const params = await context.params;
    const productId = params.id;
    const cookieHeader = request.headers.get('cookie') || '';

    console.log('[Products] 获取商品详情，商品ID:', productId);

    // 调用后端 API
    const response = await backendFetch(`/products/${productId}`, {
      method: 'GET',
      requestHeaders: {
        cookie: cookieHeader,
      },
    });

    // 使用统一的响应处理函数
    const result = await handleBackendResponse(response);

    if (!result.success) {
      console.error('[Products] 获取商品详情失败:', result.error);
      return NextResponse.json(
        { success: false, error: result.error || '获取商品详情失败' },
        { status: response.status }
      );
    }

    console.log('[Products] 获取商品详情成功，图片数量:', (result.data as any)?.images?.length || 0);

    return NextResponse.json({
      success: true,
      data: result.data,
    });
  } catch (error) {
    console.error('[Products] 获取商品详情异常:', error);
    return NextResponse.json(
      { success: false, error: '获取商品详情失败' },
      { status: 500 }
    );
  }
}
