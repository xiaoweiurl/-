import { NextRequest, NextResponse } from 'next/server';
import { backendFetch } from '@/lib/backend-proxy';

/**
 * @swagger
 * /api/images/{id}/set-main:
 *   post:
 *     summary: 设为主图
 *     description: 将当前图片设为商品主图，原主图自动变为详情图
 *     tags: [图片管理]
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
 *         description: 设置成功
 *       404:
 *         description: 图片不存在
 */
export async function POST(
    request: NextRequest,
    { params }: { params: Promise<{ id: string }> }
) {
    try {
        const { id } = await params;
        const cookieHeader = request.headers.get('cookie') || '';

        const response = await backendFetch(`/images/${id}/set-main`, {
            method: 'POST',
            requestHeaders: {
                cookie: cookieHeader,
            },
        });

        const data = await response.json();
        return NextResponse.json(data, { status: response.status });
    } catch (error) {
        console.error('[API] 设为主图失败:', error);
        return NextResponse.json({
            success: false,
            message: '系统异常，请稍后重试',
        }, { status: 500 });
    }
}
