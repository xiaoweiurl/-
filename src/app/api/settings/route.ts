import { NextRequest, NextResponse } from 'next/server';
import { backendFetch, handleBackendResponse } from '@/lib/backend-proxy';

/**
 * @swagger
 * /api/settings:
 *   get:
 *     summary: 获取系统设置
 *     description: 获取当前用户的系统设置，支持获取单个或所有设置
 *     tags: [系统设置]
 *     security:
 *       - cookieAuth: []
 *     parameters:
 *       - in: query
 *         name: key
 *         schema:
 *           type: string
 *         description: 设置键名（不传则返回所有设置）
 *     responses:
 *       200:
 *         description: 成功获取设置
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *               properties:
 *                 success:
 *                   type: boolean
 *                   example: true
 *                 data:
 *                   oneOf:
 *                     - type: object
 *                       description: 所有设置
 *                     - type: object
 *                       properties:
 *                         key:
 *                           type: string
 *                         value:
 *                           type: string
 *   post:
 *     summary: 更新单个设置
 *     description: 更新指定的系统设置
 *     tags: [系统设置]
 *     security:
 *       - cookieAuth: []
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required:
 *               - key
 *               - value
 *             properties:
 *               key:
 *                 type: string
 *                 description: 设置键名
 *               value:
 *                 type: string
 *                 description: 设置值
 *     responses:
 *       200:
 *         description: 更新成功
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
 *   put:
 *     summary: 批量更新设置
 *     description: 批量更新多个系统设置
 *     tags: [系统设置]
 *     security:
 *       - cookieAuth: []
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             description: 设置键值对对象
 *     responses:
 *       200:
 *         description: 更新成功
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *               properties:
 *                 success:
 *                   type: boolean
 *                   example: true
 *       500:
 *         description: 更新失败
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 */

export async function GET(request: NextRequest) {
  const cookieHeader = request.headers.get('cookie') || '';
  const requestHeaders: Record<string, string | null> = {
    cookie: cookieHeader,
  };
  const { searchParams } = new URL(request.url);
  const key = searchParams.get('key');

  try {
    const response = await backendFetch('/settings', { requestHeaders });
    const result = await handleBackendResponse(response);
    
    if (result.success && result.data) {
      const data = result.data as Record<string, string>;
      
      if (key) {
        return NextResponse.json({
          success: true,
          data: { key, value: data[key] || null }
        });
      } else {
        return NextResponse.json({
          success: true,
          data: data
        });
      }
    }
    
    // 如果后端返回失败
    return NextResponse.json({
      success: false,
      message: result.error || '获取设置失败',
    }, { status: 500 });
  } catch (error) {
    console.error('[API] 获取设置失败:', error);
    return NextResponse.json({
      success: false,
      error: error instanceof Error ? error.message : '获取设置失败'
    }, { status: 500 });
  }
}

export async function POST(request: NextRequest) {
  const cookieHeader = request.headers.get('cookie') || '';
  const requestHeaders: Record<string, string | null> = {
    cookie: cookieHeader,
  };
  
  try {
    const body = await request.json();
    const { key, value } = body;

    if (!key) {
      return NextResponse.json({
        success: false,
        error: '缺少设置键名'
      }, { status: 400 });
    }

    const response = await backendFetch('/settings', {
      method: 'POST',
      body: { key, value },
      requestHeaders,
    });
    
    const result = await handleBackendResponse(response);
    
    if (result.success) {
      return NextResponse.json({
        success: true,
        data: { key, value }
      });
    }
    
    return NextResponse.json(result, { status: 500 });
  } catch (error) {
    console.error('[API] 更新设置失败:', error);
    return NextResponse.json({
      success: false,
      error: error instanceof Error ? error.message : '更新设置失败'
    }, { status: 500 });
  }
}

export async function PUT(request: NextRequest) {
  const cookieHeader = request.headers.get('cookie') || '';
  const requestHeaders: Record<string, string | null> = {
    cookie: cookieHeader,
  };
  
  try {
    const body = await request.json();
    
    if (typeof body !== 'object' || body === null) {
      return NextResponse.json({
        success: false,
        error: '无效的设置数据'
      }, { status: 400 });
    }

    const response = await backendFetch('/settings', {
      method: 'PUT',
      body: body,
      requestHeaders,
    });
    
    const result = await handleBackendResponse(response);
    
    if (result.success) {
      return NextResponse.json({
        success: true,
        data: result.data
      });
    }
    
    return NextResponse.json(result, { status: 500 });
  } catch (error) {
    console.error('[API] 批量更新设置失败:', error);
    return NextResponse.json({
      success: false,
      error: error instanceof Error ? error.message : '批量更新设置失败'
    }, { status: 500 });
  }
}
