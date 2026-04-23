import { NextRequest, NextResponse } from 'next/server';
import { backendFetch, handleBackendResponse } from '@/lib/backend-proxy';

/**
 * @swagger
 * /api/images/{id}/view:
 *   post:
 *     summary: 记录预览
 *     description: 记录图片预览，增加预览次数
 *     tags: [知识管理]
 *     security:
 *       - cookieAuth: []
 *     parameters:
 *       - in: path
 *         name: id
 *         required: true
 *         schema:
 *           type: string
 *         description: 知识ID
 *     responses:
 *       200:
 *         description: 操作成功
 */
export async function POST(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params;
  
  const response = await backendFetch(`/images/${id}/view`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
  });
  
  return handleBackendResponse(response);
}
