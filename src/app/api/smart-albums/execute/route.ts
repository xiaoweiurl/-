import { NextRequest, NextResponse } from 'next/server';
import { backendFetch } from '@/lib/backend-proxy';

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
 * /api/smart-albums/execute:
 *   post:
 *     summary: 执行智能相册匹配
 *     description: 根据智能相册的 matchingConfig 筛选匹配的图片
 *     tags: [智能相册]
 *     security:
 *       - cookieAuth: []
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             properties:
 *               albumId:
 *                 type: string
 *                 description: 智能相册ID（如果提供，则使用相册的 matchingConfig）
 *               matchingConfig:
 *                 type: object
 *                 description: 匹配配置（如果不提供 albumId，则必须使用此字段）
 *                 properties:
 *                   mode:
 *                     type: string
 *                     enum: [contains, exact, startsWith, endsWith, regex, fuzzy]
 *                   caseSensitive:
 *                     type: boolean
 *                   synonyms:
 *                     type: array
 *                     items:
 *                       type: object
 *                       properties:
 *                         keywords:
 *                           type: array
 *                           items: { type: string }
 *                         targetKeyword:
 *                           type: string
 *               page:
 *                 type: integer
 *                 default: 1
 *               pageSize:
 *                 type: integer
 *                 default: 20
 *     responses:
 *       200:
 *         description: 成功筛选图片
 *         content:
 *           application/json:
 *             schema:
 *               type: object
 *               properties:
 *                 success:
 *                   type: boolean
 *                 data:
 *                   type: array
 *                   items:
 *                     type: object
 *                     description: 图片对象
 *                 total:
 *                   type: integer
 *                   description: 符合条件的图片总数
 *                 page:
 *                   type: integer
 *                 pageSize:
 *                   type: integer
 *       400:
 *         description: 参数错误（缺少 albumId 或 matchingConfig）
 *       500:
 *         description: 执行失败
 */

export async function POST(request: NextRequest) {
  try {
    const cookieHeader = request.headers.get('cookie') || '';
    const body = await request.json();
    const { albumId, matchingConfig, page = 1, pageSize = 20 } = body;

    if (!albumId && !matchingConfig) {
      return NextResponse.json(
        { success: false, message: '必须提供 albumId 或 matchingConfig' },
        { status: 400 }
      );
    }

    // 获取相册配置
    let executeConfig = matchingConfig;
    let albumName = '';

    if (albumId && !matchingConfig) {
      // 从预置相册中查找配置
      const presetAlbums: Record<string, { name: string; matchingConfig: Record<string, unknown>; isSystem?: boolean }> = {
        'smart-recent': {
          name: '最近添加',
          isSystem: true,
          matchingConfig: { mode: 'contains' }
        },
        'smart-favorites': {
          name: '我的收藏',
          isSystem: true,
          matchingConfig: { mode: 'contains' }
        }
      };

      const album = presetAlbums[albumId];
      if (!album) {
        return NextResponse.json(
          { success: false, message: '未找到相册配置' },
          { status: 404 }
        );
      }

      executeConfig = album.matchingConfig;
      albumName = album.name;
    }

    // TODO: 后端实现后，转发到后端 API
    // const response = await backendFetch('/smart-albums/execute', {
    //   method: 'POST',
    //   body: { albumId, matchingConfig: executeConfig, page, pageSize },
    //   requestHeaders: { cookie: cookieHeader },
    // });
    // const { result } = await safeParseResponse(response);
    // return NextResponse.json(result);

    // 方案2：获取所有图片，在前端执行匹配（当前实现）
    const response = await backendFetch(`/images?page=1&pageSize=9999`, {
      requestHeaders: { cookie: cookieHeader },
    });

    const { result } = await safeParseResponse(response);

    if (!result || (!result.success && (result as any).code !== 200)) {
      return NextResponse.json(
        { success: false, message: '获取图片列表失败' },
        { status: 500 }
      );
    }

    // 获取图片列表
    const resultData = (result.result || result) as any;
    const images = (resultData?.list || resultData?.data || []) as any[];

    // 导入匹配引擎并执行筛选
    const { filterImagesBySmartAlbum, matchesAlbumConfig } = await import('@/lib/smart-album-engine');

    let filteredImages: any[] = [];

    if (albumId === 'smart-recent') {
      // 最近30天
      const thirtyDaysAgo = new Date();
      thirtyDaysAgo.setDate(thirtyDaysAgo.getDate() - 30);
      filteredImages = images.filter((img: any) => {
        const imgDate = new Date(img.createdAt);
        return imgDate >= thirtyDaysAgo;
      });
    } else if (albumId === 'smart-favorites') {
      // 收藏的图片
      filteredImages = images.filter((img: any) => img.favorite);
    } else {
      // 基于 matchingConfig 匹配
      filteredImages = images.filter((img: any) => {
        const textToMatch = img.title || '';
        return matchesAlbumConfig(textToMatch, albumName, executeConfig);
      });
    }

    // 分页
    const total = filteredImages.length;
    const start = (page - 1) * pageSize;
    const end = start + pageSize;
    const paginatedImages = filteredImages.slice(start, end);

    return NextResponse.json({
      success: true,
      data: paginatedImages,
      total,
      page,
      pageSize,
      totalPages: Math.ceil(total / pageSize),
    });
  } catch (error) {
    console.error('执行智能相册匹配失败:', error);
    return NextResponse.json(
      { success: false, message: '执行智能相册匹配失败' },
      { status: 500 }
    );
  }
}
