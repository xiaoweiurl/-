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
 * /api/smart-albums:
 *   get:
 *     summary: 获取智能相册列表
 *     description: 获取当前用户的所有智能相册（包括系统预置和用户创建的），基于 matchingConfig 自动分类
 *     tags: [智能相册]
 *     security:
 *       - cookieAuth: []
 *     responses:
 *       200:
 *         description: 成功获取智能相册列表
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
 *                     type: object
 *                     properties:
 *                       id:
 *                         type: string
 *                       name:
 *                         type: string
 *                       description:
 *                         type: string
 *                       matchingConfig:
 *                         type: object
 *                         properties:
 *                           mode:
 *                             type: string
 *                             enum: [contains, exact, startsWith, endsWith, regex, fuzzy]
 *                           caseSensitive:
 *                             type: boolean
 *                           synonyms:
 *                             type: array
 *                             items:
 *                               type: object
 *                               properties:
 *                                 keywords:
 *                                   type: array
 *                                   items: { type: string }
 *                                 targetKeyword:
 *                                   type: string
 *                       isSystem:
 *                         type: boolean
 *                       imageCount:
 *                         type: integer
 *       500:
 *         description: 获取失败
 *
 *   post:
 *     summary: 创建智能相册
 *     description: 创建一个新的智能相册，基于 matchingConfig 自动匹配图片
 *     tags: [智能相册]
 *     security:
 *       - cookieAuth: []
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             required: [name, matchingConfig]
 *             properties:
 *               name:
 *                 type: string
 *                 description: 相册名称（用于匹配文件名）
 *               description:
 *                 type: string
 *                 description: 相册描述
 *               matchingConfig:
 *                 type: object
 *                 description: 匹配配置
 *                 properties:
 *                   mode:
 *                     type: string
 *                     enum: [contains, exact, startsWith, endsWith, regex, fuzzy]
 *                     description: 匹配模式
 *                   caseSensitive:
 *                     type: boolean
 *                     description: 是否区分大小写
 *                   synonyms:
 *                     type: array
 *                     description: 同义词配置
 *                     items:
 *                       type: object
 *                       properties:
 *                         keywords:
 *                           type: array
 *                           items: { type: string }
 *                         targetKeyword:
 *                           type: string
 *     responses:
 *       200:
 *         description: 创建成功
 *       400:
 *         description: 参数错误
 *       500:
 *         description: 创建失败
 */

// 系统预置智能相册
const PRESET_SMART_ALBUMS = [
  {
    id: 'smart-recent',
    name: '最近添加',
    description: '最近30天内添加的图片',
    isSystem: true,
    matchingConfig: {
      mode: 'contains',
    }
  },
  {
    id: 'smart-favorites',
    name: '我的收藏',
    description: '所有收藏的图片',
    isSystem: true,
    matchingConfig: {
      mode: 'contains',
    }
  }
];

export async function GET(request: NextRequest) {
  try {
    const cookieHeader = request.headers.get('cookie') || '';

    // TODO: 后端实现后，转发到后端 API
    // const response = await backendFetch('/smart-albums', {
    //   requestHeaders: { cookie: cookieHeader },
    // });
    // const { result } = await safeParseResponse(response);
    // return NextResponse.json(result);

    // 临时方案：返回预置相册
    return NextResponse.json({
      success: true,
      data: PRESET_SMART_ALBUMS,
      message: '获取成功'
    });
  } catch (error) {
    console.error('获取智能相册列表失败:', error);
    return NextResponse.json(
      { success: false, message: '获取智能相册列表失败' },
      { status: 500 }
    );
  }
}

export async function POST(request: NextRequest) {
  try {
    const cookieHeader = request.headers.get('cookie') || '';
    const body = await request.json();
    const { name, description, matchingConfig } = body;

    if (!name || !matchingConfig) {
      return NextResponse.json(
        { success: false, message: '相册名称和匹配配置不能为空' },
        { status: 400 }
      );
    }

    // TODO: 后端实现后，转发到后端 API
    // const response = await backendFetch('/smart-albums', {
    //   method: 'POST',
    //   body: { name, description, matchingConfig },
    //   requestHeaders: { cookie: cookieHeader },
    // });
    // const { result } = await safeParseResponse(response);
    // return NextResponse.json(result);

    // 临时方案：创建新相册并返回
    const newAlbum = {
      id: `smart-${Date.now()}`,
      name,
      description: description || '',
      matchingConfig,
      isSystem: false,
      imageCount: 0,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };

    return NextResponse.json({
      success: true,
      data: newAlbum,
      message: '创建成功'
    });
  } catch (error) {
    console.error('创建智能相册失败:', error);
    return NextResponse.json(
      { success: false, message: '创建智能相册失败' },
      { status: 500 }
    );
  }
}
