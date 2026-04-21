import { NextRequest, NextResponse } from 'next/server';
import { backendFetch } from '@/lib/backend-proxy';

interface BatchDownloadRequest {
  images: Array<{
    productName: string;
    mainImageUrl: string;
    detailImageUrls?: string[];
    category?: string;
    description?: string;
  }>;
}

interface BatchDownloadResult {
  originalUrl: string;
  success: boolean;
  error?: string;
  imageId?: string;
}

/**
 * @swagger
 * /api/images/batch-download:
 *   post:
 *     summary: 批量下载网络图片
 *     description: 从网络URL批量下载图片到本地存储
 *     tags: [图片管理]
 *     security:
 *       - cookieAuth: []
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required:
 *               - images
 *             properties:
 *               images:
 *                 type: array
 *                 description: 图片列表
 *                 items:
 *                   type: object
 *                   properties:
 *                     productName:
 *                       type: string
 *                       description: 商品名称
 *                     mainImageUrl:
 *                       type: string
 *                       description: 主图URL
 *                     detailImageUrls:
 *                       type: array
 *                       items:
 *                         type: string
 *                       description: 详情图URL列表
 *                     category:
 *                       type: string
 *                       description: 分类
 *                     description:
 *                       type: string
 *                       description: 描述
 *     responses:
 *       200:
 *         description: 下载成功
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *               properties:
 *                 success:
 *                   type: boolean
 *                   example: true
 *                 message:
 *                   type: string
 *                   example: 下载成功
 *                 data:
 *                   type: array
 *                   items:
 *                     type: object
 *                     properties:
 *                       originalUrl:
 *                         type: string
 *                       success:
 *                         type: boolean
 *                       error:
 *                         type: string
 *                       imageId:
 *                         type: string
 *       400:
 *         description: 参数错误
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *       503:
 *         description: 后端服务不可用
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 */
export async function POST(request: NextRequest) {
  try {
    const body: BatchDownloadRequest = await request.json();
    const cookieHeader = request.headers.get('cookie') || '';

    console.log('[API] 开始批量下载网络图片...');
    console.log('[API] 接收到的数据:', JSON.stringify(body, null, 2));

    if (!body.images || body.images.length === 0) {
      console.log('[API] 错误：图片列表为空');
      return NextResponse.json(
        { success: false, error: '请提供要下载的图片列表' },
        { status: 400 }
      );
    }

    // 验证每个商品的数据
    const invalidItems = body.images.filter(item => !item.productName || !item.productName.trim());
    if (invalidItems.length > 0) {
      console.log('[API] 错误：发现无效的商品名称', invalidItems);
      return NextResponse.json(
        { success: false, error: `有 ${invalidItems.length} 个商品缺少名称` },
        { status: 400 }
      );
    }

    console.log('[API] 验证通过，发送到后端，商品数量:', body.images.length);

    // 调用后端API（注意：backendFetch会自动序列化body，不要再次JSON.stringify）
    const response = await backendFetch('/images/batch-download', {
      method: 'POST',
      body: body,  // 直接传递对象，不要JSON.stringify
      requestHeaders: {
        cookie: cookieHeader,
      },
    });

    const result = await response.json();

    console.log('[API] 后端批量下载响应:', result);

    if (result.code === 200 || result.success) {
      return NextResponse.json({
        success: true,
        message: result.message || '下载成功',
        data: result.data,
      });
    }

    return NextResponse.json(
      {
        success: false,
        error: result.message || '下载失败',
        data: result.data,
      },
      { status: response.status || 500 }
    );
  } catch (error) {
    console.error('[API] 批量下载图片失败:', error);
    const errorMessage = error instanceof Error ? error.message : '批量下载失败';

    // 如果是网络错误，提示后端可能未启动
    if (errorMessage.includes('fetch') || errorMessage.includes('ECONNREFUSED')) {
      return NextResponse.json(
        {
          success: false,
          error: '无法连接到后端服务，请确保 Java 后端已在 localhost:8080 启动',
        },
        { status: 503 }
      );
    }

    return NextResponse.json(
      { success: false, error: errorMessage },
      { status: 500 }
    );
  }
}
