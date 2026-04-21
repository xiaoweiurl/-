import { NextRequest, NextResponse } from 'next/server';
import { userApi } from '@/lib/backend-proxy';

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
 * /api/notifications:
 *   get:
 *     summary: 获取通知列表
 *     description: 获取当前用户的通知列表
 *     tags: [通知]
 *     security:
 *       - cookieAuth: []
 *     parameters:
 *       - in: query
 *         name: limit
 *         schema:
 *           type: integer
 *           default: 20
 *         description: 返回通知数量限制
 *     responses:
 *       200:
 *         description: 成功获取通知列表
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
 *                     $ref: '#/components/schemas/Notification'
 *                 unreadCount:
 *                   type: integer
 *                   description: 未读通知数量
 *       500:
 *         description: 获取失败
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *   patch:
 *     summary: 通知操作
 *     description: 执行通知相关操作（标记已读、全部已读、清除等）
 *     tags: [通知]
 *     security:
 *       - cookieAuth: []
 *     requestBody:
 *       required: true
 *       content:
 *         application/json:
 *           schema:
 *             type: object
 *             properties:
 *               action:
 *                 type: string
 *                 enum: [markRead, markAllRead, clearRead]
 *                 description: 操作类型
 *               notificationId:
 *                 type: string
 *                 description: 通知ID（markRead操作时必填）
 *     responses:
 *       200:
 *         description: 操作成功
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
 *                   example: 已标记为已读
 *                 unreadCount:
 *                   type: integer
 *                   description: 剩余未读数量
 *       400:
 *         description: 参数错误
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 *       500:
 *         description: 操作失败
 *         content:
 *           application/json:
 *             schema:
 *               $ref: '#/components/schemas/ErrorResponse'
 */

export async function GET(request: NextRequest) {
  try {
    // 从请求中提取 sessionId（通过 cookie）
    const headers: Record<string, string | null> = {
      'cookie': request.headers.get('cookie'),
      'x-session-id': request.headers.get('x-session-id'),
    };
    
    const { searchParams } = new URL(request.url);
    const limit = parseInt(searchParams.get('limit') || '20');
    
    const response = await userApi.getNotifications(headers);
    const { result } = await safeParseResponse(response);
    
    // 转换通知数据格式
    const notifications = Array.isArray(result?.data) 
      ? (result.data as Record<string, unknown>[]).map((n) => ({
          id: String(n.id || ''),
          type: (n.type as 'system' | 'upload' | 'share' | 'comment') || 'system',
          title: String(n.title || ''),
          message: String(n.content || n.message || ''),
          userId: String(n.userId || 'all'),
          read: Boolean(n.read),
          createdAt: String(n.createdAt || new Date().toISOString()),
        }))
      : [];
    
    const unreadCount = notifications.filter((n) => !n.read).length;
    
    return NextResponse.json({
      success: true,
      data: notifications.slice(0, limit),
      unreadCount,
    });
  } catch (error) {
    console.error('[API] 获取通知列表失败:', error);
    return NextResponse.json({
      success: false,
      message: '获取通知列表失败',
    }, { status: 500 });
  }
}

export async function PATCH(request: NextRequest) {
  try {
    const headers: Record<string, string | null> = {
      'cookie': request.headers.get('cookie'),
      'x-session-id': request.headers.get('x-session-id'),
    };
    const body = await request.json();
    const { action, notificationId } = body;
    
    if (action === 'markRead' && notificationId) {
      await userApi.markNotificationRead(notificationId, headers);
      return NextResponse.json({ success: true, message: '操作成功' });
    }
    
    if (action === 'markAllRead') {
      await userApi.markAllNotificationsRead(headers);
      return NextResponse.json({ success: true, message: '操作成功' });
    }
    
    return NextResponse.json({ success: false, message: '未知操作' }, { status: 400 });
  } catch (error) {
    console.error('[API] 通知操作失败:', error);
    return NextResponse.json({ success: false, message: '操作失败' }, { status: 500 });
  }
}

export async function DELETE(request: NextRequest) {
  try {
    const cookieHeader = request.headers.get('cookie') || '';
    const { searchParams } = new URL(request.url);
    const notificationId = searchParams.get('id');
    
    if (!notificationId) {
      return NextResponse.json({ success: false, message: '缺少通知ID' }, { status: 400 });
    }
    
    await userApi.deleteNotification(notificationId);
    return NextResponse.json({ success: true, message: '删除成功' });
  } catch (error) {
    console.error('[API] 删除通知失败:', error);
    return NextResponse.json({ success: false, message: '删除失败' }, { status: 500 });
  }
}

export async function POST(request: NextRequest) {
  try {
    const cookieHeader = request.headers.get('cookie') || '';
    const body = await request.json().catch(() => ({}));
    const { notification } = body;
    
    if (!notification) {
      return NextResponse.json({ success: false, error: '缺少通知数据' }, { status: 400 });
    }
    
    // 构造通知数据
    const notificationData = {
      type: notification.type || 'system',
      title: notification.title || '新通知',
      content: notification.content || notification.message || '',
      resourceId: notification.resourceId,
      data: notification.data,
    };
    
    // 调用后端创建通知
    const response = await userApi.createNotification(notificationData, {
      cookie: cookieHeader,
    });
    
    // 解析后端响应
    const responseData = await response.json().catch(() => null);
    
    // 构造返回给前端的通知对象
    const now = new Date().toISOString();
    const returnedNotification = {
      id: responseData?.data?.id || `notif-${Date.now()}`,
      type: notificationData.type,
      title: notificationData.title,
      message: notificationData.content,
      read: false,
      createdAt: responseData?.data?.createdAt || now,
    };
    
    return NextResponse.json({ 
      success: true, 
      message: '创建成功',
      data: returnedNotification 
    });
  } catch (error) {
    console.error('[API] 创建通知失败:', error);
    return NextResponse.json({ success: false, message: '创建失败' }, { status: 500 });
  }
}
