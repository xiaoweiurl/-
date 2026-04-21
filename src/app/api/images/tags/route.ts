import { NextRequest, NextResponse } from 'next/server';
import { imageApi } from '@/lib/backend-proxy';

/**
 * @swagger
 * /api/images/tags:
 *   get:
 *     summary: 获取所有图片标签
 *     description: 获取系统中所有图片的标签列表
 *     tags: [图片管理]
 *     security:
 *       - cookieAuth: []
 *     responses:
 *       200:
 *         description: 成功获取标签列表
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
 *                     type: string
 *                   example: ["电子产品", "服装", "食品"]
 *       500:
 *         description: 获取失败
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 */

export async function GET(request: NextRequest) {
  try {
    const cookieHeader = request.headers.get('cookie') || '';
    console.log('[API] 获取标签，Cookie:', cookieHeader.substring(0, 50) + '...');
    
    const requestHeaders: Record<string, string | null> = {
      cookie: cookieHeader,
    };
    
    const response = await imageApi.getTags(requestHeaders);
    const ok = response.ok;
    const status = response.status;
    
    console.log('[API] 获取标签，后端响应状态:', status);
    
    // 先读取响应体文本
    const text = await response.text();
    
    // 处理空响应
    if (!text) {
      console.warn('[API] 获取标签，响应为空');
      return NextResponse.json({
        success: true,
        data: [],
      });
    }
    
    // 解析 JSON
    let result;
    try {
      result = JSON.parse(text);
    } catch {
      console.error('[API] 解析标签响应失败');
      return NextResponse.json({
        success: false,
        message: '解析响应失败',
      }, { status: 500 });
    }
    
    // 检查响应状态
    if (!ok) {
      console.error('[API] 获取标签失败:', result.message || `HTTP ${status}`);
      return NextResponse.json({
        success: false,
        message: result?.message || '获取标签失败',
      }, { status: 401 });  // 返回 401 让前端知道需要重新登录
    }
    
    // 提取数据
    const tags = Array.isArray(result.data) ? result.data : [];
    
    return NextResponse.json({
      success: true,
      data: tags,
    });
  } catch (error) {
    console.error('[API] 获取标签异常:', error);
    return NextResponse.json({
      success: false,
      message: error instanceof Error ? error.message : '获取标签失败',
    }, { status: 500 });
  }
}
