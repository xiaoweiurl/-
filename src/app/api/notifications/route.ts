import { NextRequest, NextResponse } from 'next/server';
import { userApi, getBackendUrl } from '@/lib/backend-proxy';

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

// 降级模式：内存中的通知存储
let fallbackNotifications: any[] = [
  {
    id: 'notif-welcome',
    type: 'info',
    title: '欢迎使用企业数智中台系统',
    content: '系统已就绪，您可以开始上传和管理您的知识内容。',
    read: false,
    createdAt: new Date(Date.now() - 3600000).toISOString(),
  },
  {
    id: 'notif-update',
    type: 'success',
    title: '系统更新完成',
    content: '新增了分享链接、存储统计等功能。',
    read: false,
    createdAt: new Date(Date.now() - 7200000).toISOString(),
  },
];

/**
 * GET /api/notifications
 * 获取通知列表
 */
export async function GET(request: NextRequest) {
  try {
    const { searchParams } = new URL(request.url);
    const limit = searchParams.get('limit') || '20';

    // 尝试调用后端
    try {
      const cookieHeader = request.headers.get('cookie') || '';
      const url = new URL('/user/notifications', getBackendUrl());
      url.searchParams.set('limit', limit);
      const response = await userApi.getNotifications({ cookie: cookieHeader });
      const { result, ok } = await safeParseResponse(response as any);
      if (ok) {
        return NextResponse.json(result);
      }
    } catch (backendError) {
      console.warn('[API] 后端不可用，使用降级模式:', backendError);
    }

    // 降级模式
    return NextResponse.json({
      success: true,
      data: fallbackNotifications.slice(0, parseInt(limit)),
      unreadCount: fallbackNotifications.filter(n => !n.read).length,
      message: '降级模式 - 模拟数据',
    });
  } catch (error) {
    console.error('[API] 获取通知列表失败:', error);
    return NextResponse.json(
      { success: false, message: '获取通知列表失败' },
      { status: 500 }
    );
  }
}

/**
 * POST /api/notifications
 * 创建通知
 */
export async function POST(request: NextRequest) {
  try {
    const body = await request.json();
    const notification = body.notification || body;

    // 支持前端传递的 message 或 content 字段
    const content = notification.content || notification.message;
    if (!notification.title || !content) {
      return NextResponse.json(
        { success: false, error: '缺少通知数据' },
        { status: 400 }
      );
    }

    // 尝试调用后端
    try {
      const cookieHeader = request.headers.get('cookie') || '';
      // 统一字段名：前端用 message，后端用 content
      const backendNotification = {
        ...notification,
        content: notification.content || notification.message,
      };
      const response = await userApi.createNotification(backendNotification, { cookie: cookieHeader });
      const { result, ok } = await safeParseResponse(response as any);
      if (ok) {
        return NextResponse.json(result);
      }
    } catch (backendError) {
      console.warn('[API] 后端不可用，使用降级模式:', backendError);
    }

    // 降级模式
    const newNotif = {
      id: `notif-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      type: notification.type || 'info',
      title: notification.title,
      content: content,  // 使用统一后的 content
      read: false,
      createdAt: new Date().toISOString(),
    };
    fallbackNotifications.unshift(newNotif);
    if (fallbackNotifications.length > 50) {
      fallbackNotifications = fallbackNotifications.slice(0, 50);
    }
    return NextResponse.json({
      success: true,
      data: newNotif,
      message: '通知创建成功（降级模式）',
    });
  } catch (error) {
    console.error('[API] 创建通知失败:', error);
    return NextResponse.json(
      { success: false, message: '创建失败' },
      { status: 500 }
    );
  }
}

/**
 * PATCH /api/notifications
 * 通知操作（标记已读、全部已读、清除）
 */
export async function PATCH(request: NextRequest) {
  try {
    const body = await request.json();
    const { action, notificationId } = body;

    // 尝试调用后端
    try {
      const cookieHeader = request.headers.get('cookie') || '';
      const requestHeaders = { cookie: cookieHeader };
      let response: Response;
      if (action === 'markRead' && notificationId) {
        response = await userApi.markNotificationRead(notificationId, requestHeaders);
      } else if (action === 'markAllRead') {
        response = await userApi.markAllNotificationsRead(requestHeaders);
      } else {
        // clearRead 等其他操作不支持
        throw new Error('不支持的操作');
      }
      const { result, ok } = await safeParseResponse(response as any);
      if (ok) {
        return NextResponse.json(result);
      }
    } catch (backendError) {
      console.warn('[API] 后端不可用，使用降级模式:', backendError);
    }

    // 降级模式
    if (action === 'markRead' && notificationId) {
      const notif = fallbackNotifications.find(n => n.id === notificationId);
      if (notif) notif.read = true;
    } else if (action === 'markAllRead') {
      fallbackNotifications.forEach(n => n.read = true);
    } else if (action === 'clearRead') {
      fallbackNotifications = fallbackNotifications.filter(n => !n.read);
    }

    return NextResponse.json({
      success: true,
      message: '操作成功（降级模式）',
      unreadCount: fallbackNotifications.filter(n => !n.read).length,
    });
  } catch (error) {
    console.error('[API] 通知操作失败:', error);
    return NextResponse.json(
      { success: false, message: '操作失败' },
      { status: 500 }
    );
  }
}
