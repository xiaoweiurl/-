/**
 * 商品主图列表 API
 * 代理到 Java 后端: GET /api/products/main-images
 */

import { NextRequest, NextResponse } from 'next/server';
import { backendFetch } from '@/lib/backend-proxy';

/**
 * @swagger
 * /api/products/main-images:
 *   get:
 *     summary: 获取商品主图列表
 *     description: 获取所有商品的主图列表，支持分页和筛选
 *     tags: [商品管理]
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
 *       - in: query
 *         name: category
 *         schema:
 *           type: string
 *         description: 分类筛选
 *       - in: query
 *         name: keyword
 *         schema:
 *           type: string
 *         description: 关键词搜索
 *     responses:
 *       200:
 *         description: 成功获取商品主图列表
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
 *                         $ref: '#/components/schemas/ProductImage'
 *                     total:
 *                       type: integer
 *                       description: 总数量
 *                     page:
 *                       type: integer
 *                       description: 当前页码
 *                     pageSize:
 *                       type: integer
 *                       description: 每页数量
 *                     pagination:
 *                       type: object
 *                       properties:
 *                         hasMore:
 *                           type: boolean
 *                         totalPages:
 *                           type: integer
 *                         totalElements:
 *                           type: integer
 *       500:
 *         description: 获取失败
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 */

export async function GET(request: NextRequest) {
  try {
    const searchParams = request.nextUrl.searchParams;
    const page = searchParams.get('page') || '1';
    const pageSize = searchParams.get('pageSize') || '20';
    const category = searchParams.get('category') || '';
    const keyword = searchParams.get('keyword') || '';
    const cookieHeader = request.headers.get('cookie') || '';

    console.log('[Products] 获取商品主图列表，参数:', { page, pageSize, category, keyword });

    // 构建查询参数
    const params = new URLSearchParams();
    params.set('page', page);
    params.set('pageSize', pageSize);
    if (category) params.set('category', category);
    if (keyword) params.set('keyword', keyword);

    // 调用后端 API
    const response = await backendFetch(
      `/products/main-images?${params.toString()}`,
      {
        method: 'GET',
        requestHeaders: {
          cookie: cookieHeader,
        },
      }
    );

    // 安全解析响应
    const text = await response.text();
    if (!text || !response.ok) {
      return NextResponse.json({
        success: false,
        message: '获取商品列表失败',
      }, { status: response.status || 500 });
    }

    let data;
    try {
      data = JSON.parse(text);
    } catch {
      console.error('[Products] 解析响应失败');
      return NextResponse.json({
        success: false,
        message: '解析响应失败',
      }, { status: 500 });
    }

    console.log('[Products] 后端响应:', data);

    // 转换后端响应格式为前端期望的格式
    // 后端: { "code": 200, "message": "success", "data": { "list": [...], "total": ... } }
    // 前端: { "success": true, "data": { "list": [...], "total": ... } }
    return NextResponse.json({
      success: data.code === 200,
      data: {
        list: data.data?.list || [],
        total: data.data?.total || 0,
        page: data.data?.page || 1,
        pageSize: data.data?.pageSize || 20,
        pagination: {
          hasMore: data.data?.hasNext || false,
          totalPages: data.data?.totalPages || 0,
          totalElements: data.data?.total || 0,
        }
      }
    });
  } catch (error) {
    console.error('[Products] 获取商品主图列表异常:', error);
    return NextResponse.json({
      success: false,
      message: '获取商品列表失败',
    }, { status: 500 });
  }
}
