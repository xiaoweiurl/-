import { NextRequest, NextResponse } from 'next/server';
import { backendFetch, getBackendUrl } from '@/lib/backend-proxy';

/**
 * @swagger
 * /api/images/{id}:
 *   get:
 *     summary: 获取图片详情
 *     description: 获取单张图片的详细信息
 *     tags: [图片]
 *     security:
 *       - cookieAuth: []
 *     parameters:
 *       - in: path
 *         name: id
 *         required: true
 *         schema:
 *           type: string
 *         description: 图片ID
 *     responses:
 *       200:
 *         description: 成功获取图片详情
 *       404:
 *         description: 图片不存在
 *       401:
 *         description: 未登录
 */

export async function GET(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  try {
    const { id } = await params;
    const cookieHeader = request.headers.get('cookie') || '';

    // 转发到后端 Java API
    const response = await backendFetch(`/images/${id}`, {
      method: 'GET',
      requestHeaders: { cookie: cookieHeader },
    });

    if (response.ok) {
      const result = await response.json();
      return NextResponse.json(result);
    }

    // 后端返回错误，返回相应状态码
    const errorText = await response.text();
    return NextResponse.json(
      { success: false, error: errorText || '获取图片详情失败' },
      { status: response.status }
    );
  } catch (error) {
    const backendUrl = getBackendUrl();
    const errorMessage = error instanceof Error ? error.message : String(error);
    console.error('[Image Detail] Error:', errorMessage);
    return NextResponse.json(
      { 
        success: false, 
        error: `无法连接到后端服务 (${backendUrl})`,
        detail: errorMessage
      },
      { status: 500 }
    );
  }
}

export async function DELETE(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  try {
    const { id } = await params;
    const cookieHeader = request.headers.get('cookie') || '';

    const response = await backendFetch(`/images/${id}`, {
      method: 'DELETE',
      requestHeaders: { cookie: cookieHeader },
    });

    if (response.ok) {
      const result = await response.json();
      return NextResponse.json(result);
    }

    const errorText = await response.text();
    return NextResponse.json(
      { success: false, error: errorText || '删除图片失败' },
      { status: response.status }
    );
  } catch (error) {
    const backendUrl = getBackendUrl();
    const errorMessage = error instanceof Error ? error.message : String(error);
    console.error('[Image Delete] Error:', errorMessage);
    return NextResponse.json(
      { 
        success: false, 
        error: `无法连接到后端服务 (${backendUrl})`,
        detail: errorMessage
      },
      { status: 500 }
    );
  }
}
