/**
 * 商品图片 API
 * 代理到 Java 后端: GET /api/products/{id}/images
 */

import { NextRequest, NextResponse } from 'next/server';
import { backendFetch } from '@/lib/backend-proxy';

/**
 * @swagger
 * /api/products/{id}/images:
 *   get:
 *     summary: 获取商品所有图片
 *     description: 根据商品ID获取该商品的所有图片（主图+详情图）
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
 *         description: 成功获取商品图片列表
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
 *                     $ref: '#/components/schemas/ProductImage'
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

    console.log('[ProductImages] 获取商品所有图片，商品ID:', productId);

    // 调用后端 API
    const response = await backendFetch(`/products/${productId}/images`, {
      method: 'GET',
      requestHeaders: {
        cookie: cookieHeader,
      },
    });

    if (!response.ok) {
      const error = await response.text();
      console.error('[ProductImages] 获取商品图片失败:', error);
      return NextResponse.json(
        { success: false, error: '获取商品图片失败' },
        { status: response.status }
      );
    }

    const data = await response.json();

    console.log('[ProductImages] 获取商品图片成功，图片数量:', data.data?.length || 0);

    return NextResponse.json(data);
  } catch (error) {
    console.error('[ProductImages] 获取商品图片异常:', error);
    return NextResponse.json(
      { success: false, error: '获取商品图片失败' },
      { status: 500 }
    );
  }
}
