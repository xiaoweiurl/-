import { NextRequest, NextResponse } from 'next/server';
import { imageApi, backendFetchFormData } from '@/lib/backend-proxy';

/**
 * 安全解析响应
 */
async function safeParseResponse(response: Response): Promise<{ result?: Record<string, unknown>; ok: boolean; status: number }> {
  const ok = response.ok;
  const status = response.status;
  
  const text = await response.text();
  
  if (!text) {
    return { ok, status, result: { data: [] } };
  }
  
  try {
    const parsed = JSON.parse(text);
    return { ok, status, result: parsed };
  } catch {
    return { ok, status, result: { data: [] } };
  }
}

/**
 * @swagger
 * /api/images:
 *   get:
 *     summary: 获取知识列表
 *     description: 获取知识（图片）列表，支持分页、筛选、排序
 *     tags: [知识管理]
 *     security:
 *       - cookieAuth: []
 *     parameters:
 *       - in: query
 *         name: page
 *         schema:
 *           type: integer
 *           default: 1
 *         description: 页码
 *       - in: query
 *         name: pageSize
 *         schema:
 *           type: integer
 *           default: 40
 *         description: 每页数量
 *       - in: query
 *         name: albumId
 *         schema:
 *           type: string
 *         description: 分类ID
 *       - in: query
 *         name: favorite
 *         schema:
 *           type: boolean
 *         description: 是否收藏
 *       - in: query
 *         name: deleted
 *         schema:
 *           type: boolean
 *         description: 是否已删除（回收站）
 *       - in: query
 *         name: onlyMainImage
 *         schema:
 *           type: boolean
 *         description: 仅返回主图
 *       - in: query
 *         name: sortBy
 *         schema:
 *           type: string
 *           enum: [date, name, size]
 *         description: 排序字段
 *       - in: query
 *         name: sortOrder
 *         schema:
 *           type: string
 *           enum: [asc, desc]
 *         description: 排序方向
 *       - in: query
 *         name: search
 *         schema:
 *           type: string
 *         description: 搜索关键词
 *       - in: query
 *         name: tag
 *         schema:
 *           type: string
 *         description: 按标签筛选
 *     responses:
 *       200:
 *         description: 成功获取知识列表
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *               properties:
 *                 success:
 *                   type: boolean
 *                   example: true
 *                 data:
 *                   type: object
 *                   properties:
 *                     list:
 *                       type: array
 *                       items:
 *                         $ref: '#/components/schemas/Image'
 *                     total:
 *                       type: integer
 *                       description: 总数
 *                     page:
 *                       type: integer
 *                       description: 当前页码
 *                     pageSize:
 *                       type: integer
 *                       description: 每页数量
 *       500:
 *         description: 服务器错误
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *   post:
 *     summary: 上传知识
 *     description: 上传新的知识（图片）到系统
 *     tags: [知识管理]
 *     security:
 *       - cookieAuth: []
 *     requestBody:
 *       required: true
 *       content:
 *         multipart/form-data:
 *           schema:
 *             type: object
 *             properties:
 *               file:
 *                 type: string
 *                 format: binary
 *                 description: 图片文件（支持 JPG、PNG、GIF、WebP）
 *               albumId:
 *                 type: string
 *                 description: 目标分类ID
 *               enableAI:
 *                 type: boolean
 *                 description: 是否启用AI分类
 *     responses:
 *       200:
 *         description: 上传成功
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
 *                   example: 上传成功
 *                 data:
 *                   type: object
 *                   $ref: '#/components/schemas/Image'
 *       500:
 *         description: 上传失败
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 */
export async function GET(request: NextRequest) {
  try {
    const { searchParams } = new URL(request.url);
    const cookieHeader = request.headers.get('cookie') || '';
    
    const params: Record<string, string | number | boolean | undefined> = {
      page: searchParams.get('page') ? parseInt(searchParams.get('page')!) : 1,
      pageSize: searchParams.get('pageSize') ? parseInt(searchParams.get('pageSize')!) : 40,
      albumId: searchParams.get('albumId') || undefined,
      favorite: searchParams.get('favorite') === 'true' ? true : undefined,
      deleted: searchParams.get('deleted') === 'true' ? true : undefined,
      onlyMainImage: searchParams.get('onlyMainImage') === 'true' ? true : undefined,
      onlyMine: searchParams.get('onlyMine') === 'true' ? true : undefined,
      includeDeleted: searchParams.get('includeDeleted') === 'true' ? true : undefined,
      sortBy: searchParams.get('sortBy') || undefined,
      sortOrder: searchParams.get('sortOrder') || undefined,
      search: searchParams.get('search') || searchParams.get('keyword') || undefined,
      tag: searchParams.get('tag') || undefined,
    };
    
    // 移除 undefined 值
    const cleanParams: Record<string, string | number | boolean> = {};
    for (const [key, value] of Object.entries(params)) {
      if (value !== undefined) {
        cleanParams[key] = value;
      }
    }
    
    const requestHeaders: Record<string, string | null> = {
      cookie: cookieHeader,
    };
    
    const response = await imageApi.list({
      ...cleanParams as Parameters<typeof imageApi.list>[0],
      requestHeaders,
    });
    const { ok, result } = await safeParseResponse(response);
    
    if (ok && result) {
      const data = result.data as Record<string, unknown>;
      
      return NextResponse.json({
        success: true,
        data: {
          list: data?.list || data?.content || [],
          total: data?.total || data?.totalElements || 0,
          page: data?.page || searchParams.get('page') || 1,
          pageSize: data?.pageSize || searchParams.get('pageSize') || 40,
        },
      });
    }
    
    // 后端不可用或返回错误
    return NextResponse.json({
      success: false,
      message: result?.message || '获取图片列表失败',
    }, { status: 500 });
  } catch (error) {
    console.error('[API] 获取图片列表失败:', error);
    return NextResponse.json({
      success: false,
      message: '获取图片列表失败',
    }, { status: 500 });
  }
}

export async function POST(request: NextRequest) {
  try {
    const formData = await request.formData();
    const cookieHeader = request.headers.get('cookie') || '';
    const requestHeaders: Record<string, string | null> = {
      cookie: cookieHeader,
    };
    
    const response = await backendFetchFormData('/images/upload', formData, requestHeaders);
    const { result } = await safeParseResponse(response);
    
    if (result && (result.code === 200 || result.success)) {
      return NextResponse.json({
        success: true,
        message: (result.message as string) || '上传成功',
        data: result.data,
      });
    }
    
    return NextResponse.json({
      success: false,
      message: result?.message || '上传失败',
    }, { status: 500 });
  } catch (error) {
    console.error('[API] 上传图片失败:', error);
    return NextResponse.json({
      success: false,
      message: '上传失败',
    }, { status: 500 });
  }
}
