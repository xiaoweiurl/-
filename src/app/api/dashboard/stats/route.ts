/**
 * @swagger
 * /api/dashboard/stats:
 *   get:
 *     summary: 获取仪表盘统计数据
 *     description: 转发到后端 Java API 获取系统的各项统计数据
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
 *         description: 成功获取统计数据
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
    const period = searchParams.get('period') || 'month';
    const cookieHeader = request.headers.get('cookie') || '';

    // 转发请求到后端 Java API
    const response = await backendFetch('/dashboard/stats', {
      method: 'GET',
      requestHeaders: { cookie: cookieHeader },
    });

    if (response.ok) {
      const result = await response.json();
      // 转换后端格式 {code, message, data} 为前端格式 {success, message, data}
      return NextResponse.json({
        success: result.code === 200,
        message: result.message,
        data: result.data,
      });
    }

    // 后端返回错误
    console.error('[Dashboard] 后端返回错误:', response.status);
    return NextResponse.json(
      { success: false, message: '后端服务异常，请稍后重试' },
      { status: 503 }
    );
  } catch (error) {
    console.error('[Dashboard Stats] Error:', error);
    return NextResponse.json(
      { success: false, message: '获取统计数据失败，后端服务可能不可用' },
      { status: 503 }
    );
  }
}
