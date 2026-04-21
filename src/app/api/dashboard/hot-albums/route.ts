/**
 * @swagger
 * /api/dashboard/hot-albums:
 *   get:
 *     summary: 获取热门相册列表
 *     description: 转发到后端 Java API 获取热门相册排行
 *     tags: [仪表盘]
 *     security:
 *       - cookieAuth: []
 *     parameters:
 *       - in: query
 *         name: limit
 *         schema:
 *           type: integer
 *           default: 10
 *         description: 返回数量限制
 *     responses:
 *       200:
 *         description: 成功获取热门相册
 *       401:
 *         description: 未登录
 *       503:
 *         description: 后端服务不可用
 */

import { NextRequest, NextResponse } from 'next/server';
import { backendFetch } from '@/lib/backend-proxy';

export async function GET(request: NextRequest) {
  try {
    const { searchParams } = new URL(request.url);
    const limit = searchParams.get('limit') || '10';
    const cookieHeader = request.headers.get('cookie') || '';

    // 转发请求到后端 Java API
    const response = await backendFetch(`/dashboard/hot-albums?limit=${limit}`, {
      method: 'GET',
      requestHeaders: { cookie: cookieHeader },
    });

    if (response.ok) {
      const result = await response.json();
      return NextResponse.json({
        success: result.code === 200,
        message: result.message,
        data: result.data,
      });
    }

    return NextResponse.json(
      { success: false, message: '获取热门相册失败' },
      { status: response.status }
    );
  } catch (error) {
    console.error('[API] 获取热门相册失败:', error);
    return NextResponse.json(
      { success: false, message: '后端服务不可用' },
      { status: 503 }
    );
  }
}
