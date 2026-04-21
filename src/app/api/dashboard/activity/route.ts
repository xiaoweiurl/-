/**
 * @swagger
 * /api/dashboard/activity:
 *   get:
 *     summary: 获取用户活跃度统计
 *     description: 转发到后端 Java API 获取用户活跃度数据
 *     tags: [仪表盘]
 *     security:
 *       - cookieAuth: []
 *     parameters:
 *       - in: query
 *         name: period
 *         schema:
 *           type: string
 *           enum: [week, month, year]
 *         description: 统计周期
 *     responses:
 *       200:
 *         description: 成功获取活跃度统计
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
    const period = searchParams.get('period') || 'week';
    const cookieHeader = request.headers.get('cookie') || '';

    // 转发请求到后端 Java API
    const response = await backendFetch(`/dashboard/activity?period=${period}`, {
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
      { success: false, message: '获取活跃度统计失败' },
      { status: response.status }
    );
  } catch (error) {
    console.error('[API] 获取活跃度统计失败:', error);
    return NextResponse.json(
      { success: false, message: '后端服务不可用' },
      { status: 503 }
    );
  }
}
