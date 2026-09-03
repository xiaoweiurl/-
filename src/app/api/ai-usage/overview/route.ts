/**
 * @swagger
 * /api/ai-usage/overview:
 *   get:
 *     summary: AI 能力用量监控总览
 *     description: 转发到后端 Java API，返回真实调用统计（服务健康/模型用量/最近调用/今日用量/7日趋势/限流配置），无任何模拟数据
 *     tags: [AI能力中心]
 *     security:
 *       - cookieAuth: []
 *     responses:
 *       200:
 *         description: 成功获取用量监控数据
 *       503:
 *         description: 后端服务不可用
 */

import { NextRequest, NextResponse } from 'next/server';
import { backendFetch } from '@/lib/backend-proxy';

export async function GET(request: NextRequest) {
  try {
    const cookieHeader = request.headers.get('cookie') || '';
    const response = await backendFetch('/ai-usage/overview', {
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
      { success: false, message: '后端服务返回错误', data: null },
      { status: response.status }
    );
  } catch (error) {
    console.error('[ai-usage/overview] 请求失败:', error);
    return NextResponse.json(
      { success: false, message: '后端服务不可用', data: null },
      { status: 503 }
    );
  }
}
