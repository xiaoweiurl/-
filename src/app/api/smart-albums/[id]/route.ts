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
    return { ok, status, result: { data: null } };
  }

  try {
    const parsed = JSON.parse(text);
    return { ok, status, result: parsed };
  } catch {
    return { ok, status, result: { data: null } };
  }
}

/**
 * @swagger
 * /api/smart-albums/{id}:
 *   get:
 *     summary: 获取智能相册详情
 *     description: 获取指定智能相册的详细信息
 *     tags: [智能相册]
 *     security:
 *       - cookieAuth: []
 *     parameters:
 *       - in: path
 *         name: id
 *         required: true
 *         schema:
 *           type: string
 *         description: 智能相册ID
 *     responses:
 *       200:
 *         description: 成功获取智能相册详情
 *       404:
 *         description: 智能相册不存在
 *       500:
 *         description: 获取失败
 *
 *   put:
 *     summary: 更新智能相册
 *     description: 更新指定智能相册的信息和匹配配置
 *     tags: [智能相册]
 *     security:
 *       - cookieAuth: []
 *     parameters:
 *       - in: path
 *         name: id
 *         required: true
 *         schema:
 *           type: string
 *         description: 智能相册ID
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             properties:
 *               name:
 *                 type: string
 *               description:
 *                 type: string
 *               matchingConfig:
 *                 type: object
 *                 properties:
 *                   mode:
 *                     type: string
 *                     enum: [contains, exact, startsWith, endsWith, regex, fuzzy]
 *                   caseSensitive:
 *                     type: boolean
 *                   synonyms:
 *                     type: array
 *     responses:
 *       200:
 *         description: 更新成功
 *       400:
 *         description: 参数错误或无法修改系统相册
 *       404:
 *         description: 智能相册不存在
 *       500:
 *         description: 更新失败
 *
 *   delete:
 *     summary: 删除智能相册
 *     description: 删除指定的智能相册（系统预置相册无法删除）
 *     tags: [智能相册]
 *     security:
 *       - cookieAuth: []
 *     parameters:
 *       - in: path
 *         name: id
 *         required: true
 *         schema:
 *           type: string
 *         description: 智能相册ID
 *     responses:
 *       200:
 *         description: 删除成功
 *       400:
 *         description: 无法删除系统相册
 *       404:
 *         description: 智能相册不存在
 *       500:
 *         description: 删除失败
 */

export async function GET(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  try {
    const { id } = await params;
    const cookieHeader = request.headers.get('cookie') || '';

    // TODO: 后端实现后，转发到后端 API
    // const response = await backendFetch(`/smart-albums/${id}`, {
    //   requestHeaders: { cookie: cookieHeader },
    // });
    // const { result } = await safeParseResponse(response);
    // return NextResponse.json(result);

    return NextResponse.json({
      success: false,
      message: '暂不支持获取单个智能相册详情'
    }, { status: 501 });
  } catch (error) {
    console.error('获取智能相册详情失败:', error);
    return NextResponse.json(
      { success: false, message: '获取智能相册详情失败' },
      { status: 500 }
    );
  }
}

export async function PUT(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  try {
    const { id } = await params;
    const cookieHeader = request.headers.get('cookie') || '';
    const body = await request.json();
    const { name, description, matchingConfig } = body;

    // 检查是否为系统相册
    if (id.startsWith('smart-') && !id.startsWith('smart-user-')) {
      return NextResponse.json(
        { success: false, message: '不能修改系统预置相册' },
        { status: 400 }
      );
    }

    // TODO: 后端实现后，转发到后端 API
    // const response = await backendFetch(`/smart-albums/${id}`, {
    //   method: 'PUT',
    //   body: { name, description, matchingConfig },
    //   requestHeaders: { cookie: cookieHeader },
    // });
    // const { result } = await safeParseResponse(response);
    // return NextResponse.json(result);

    return NextResponse.json({
      success: true,
      data: {
        id,
        name,
        description,
        matchingConfig,
        updatedAt: new Date().toISOString(),
      },
      message: '更新成功'
    });
  } catch (error) {
    console.error('更新智能相册失败:', error);
    return NextResponse.json(
      { success: false, message: '更新智能相册失败' },
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

    // 检查是否为系统相册
    if (id.startsWith('smart-') && !id.startsWith('smart-user-')) {
      return NextResponse.json(
        { success: false, message: '不能删除系统预置相册' },
        { status: 400 }
      );
    }

    // TODO: 后端实现后，转发到后端 API
    // const response = await backendFetch(`/smart-albums/${id}`, {
    //   method: 'DELETE',
    //   requestHeaders: { cookie: cookieHeader },
    // });
    // const { result } = await safeParseResponse(response);
    // return NextResponse.json(result);

    return NextResponse.json({
      success: true,
      message: '删除成功'
    });
  } catch (error) {
    console.error('删除智能相册失败:', error);
    return NextResponse.json(
      { success: false, message: '删除智能相册失败' },
      { status: 500 }
    );
  }
}
