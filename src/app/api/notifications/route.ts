import { NextRequest, NextResponse } from 'next/server';
import { userApi } from '@/lib/backend-proxy';

/**
 * 通知协议适配层（非纯转发）：
 * 前端使用 action 语义（markRead / markAllRead）与 ?id= 查询参数，
 * Java 后端使用 REST 路径（/user/notifications/{id}/read 等），在此做映射。
 * 鉴权统一由 Java 后端完成，本层仅透传 Cookie；后端不可用直接返回 503，不做任何 Mock。
 */

async function parseBackend(response: Response): Promise<{ result: unknown; ok: boolean; status: number }> {
  const ok = response.ok;
  const status = response.status;
  const text = await response.text();
  if (!text) return { result: { success: ok, data: null }, ok, status };
  try {
    return { result: JSON.parse(text), ok, status };
  } catch {
    return { result: { success: false, message: '后端响应格式错误' }, ok: false, status };
  }
}

function backendUnavailable() {
  return NextResponse.json(
    { success: false, message: '后端服务不可用，请稍后重试' },
    { status: 503 },
  );
}

function cookieHeaders(request: NextRequest) {
  return { cookie: request.headers.get('cookie') || '' };
}

/**
 * GET /api/notifications?limit=20
 * 获取通知列表
 */
export async function GET(request: NextRequest) {
  try {
    const response = await userApi.getNotifications(cookieHeaders(request));
    const { result, ok, status } = await parseBackend(response);
    return NextResponse.json(result as object, { status: ok ? 200 : status });
  } catch (error) {
    console.error('[API] 获取通知列表失败:', error);
    return backendUnavailable();
  }
}

/**
 * DELETE /api/notifications?id=xxx
 * 删除单条通知
 */
export async function DELETE(request: NextRequest) {
  try {
    const id = new URL(request.url).searchParams.get('id');
    if (!id) {
      return NextResponse.json({ success: false, message: '缺少通知ID' }, { status: 400 });
    }
    const response = await userApi.deleteNotification(id, cookieHeaders(request));
    const { result, ok, status } = await parseBackend(response);
    return NextResponse.json(result as object, { status: ok ? 200 : status });
  } catch (error) {
    console.error('[API] 删除通知失败:', error);
    return backendUnavailable();
  }
}

/**
 * POST /api/notifications
 * 创建通知（前端用 message 字段，后端用 content 字段，在此统一）
 */
export async function POST(request: NextRequest) {
  try {
    const body = await request.json();
    const notification = body.notification || body;
    const content = notification.content || notification.message;
    if (!notification.title || !content) {
      return NextResponse.json({ success: false, error: '缺少通知数据' }, { status: 400 });
    }

    const backendNotification = { ...notification, content };
    const response = await userApi.createNotification(backendNotification, cookieHeaders(request));
    const { result, ok, status } = await parseBackend(response);
    return NextResponse.json(result as object, { status: ok ? 200 : status });
  } catch (error) {
    console.error('[API] 创建通知失败:', error);
    return backendUnavailable();
  }
}

/**
 * PATCH /api/notifications
 * 通知操作：{ action: 'markRead', notificationId } / { action: 'markAllRead' }
 */
export async function PATCH(request: NextRequest) {
  try {
    const body = await request.json();
    const { action, notificationId } = body;

    let response: Response;
    if (action === 'markRead' && notificationId) {
      response = await userApi.markNotificationRead(notificationId, cookieHeaders(request));
    } else if (action === 'markAllRead') {
      response = await userApi.markAllNotificationsRead(cookieHeaders(request));
    } else {
      return NextResponse.json({ success: false, message: '不支持的操作' }, { status: 400 });
    }

    const { result, ok, status } = await parseBackend(response);
    return NextResponse.json(result as object, { status: ok ? 200 : status });
  } catch (error) {
    console.error('[API] 通知操作失败:', error);
    return backendUnavailable();
  }
}
