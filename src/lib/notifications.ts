// 通知数据模型
export interface Notification {
  id: string;
  type: 'system' | 'upload' | 'album' | 'share' | 'comment' | 'like' | 'warning';
  title: string;
  message: string;
  userId: string; // 接收通知的用户ID，'all' 表示所有用户
  read: boolean;
  createdAt: string;
  data?: Record<string, unknown>; // 附加数据
}

// 通知类型配置
export const NOTIFICATION_TYPES = {
  system: { label: '系统通知', icon: '📢', color: 'blue' },
  upload: { label: '上传通知', icon: '📤', color: 'green' },
  album: { label: '相册通知', icon: '📁', color: 'amber' },
  share: { label: '分享通知', icon: '🔗', color: 'purple' },
  comment: { label: '评论通知', icon: '💬', color: 'cyan' },
  like: { label: '点赞通知', icon: '❤️', color: 'red' },
  warning: { label: '警告通知', icon: '⚠️', color: 'orange' },
} as const;

// 使用全局变量存储通知
declare global {
   
  var __notificationsStore: Notification[] | undefined;
}

// 生成通知ID
export function generateNotificationId(): string {
  return `notif-${Date.now()}-${Math.random().toString(36).substring(2, 8)}`;
}

// 获取或初始化通知存储
export function getNotificationsStore(): Notification[] {
  if (!globalThis.__notificationsStore) {
    globalThis.__notificationsStore = [
      // 示例通知数据
      {
        id: 'notif-1',
        type: 'system',
        title: '欢迎使用图片管理系统',
        message: '感谢您使用我们的图片管理系统，开始上传您的第一张图片吧！',
        userId: 'all',
        read: false,
        createdAt: new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString(), // 2小时前
      },
      {
        id: 'notif-2',
        type: 'upload',
        title: '上传成功',
        message: '5张新图片上传成功，AI已自动识别并添加标签。',
        userId: 'all',
        read: false,
        createdAt: new Date(Date.now() - 30 * 60 * 1000).toISOString(), // 30分钟前
        data: { imageCount: 5 },
      },
      {
        id: 'notif-3',
        type: 'album',
        title: '相册更新',
        message: '相册"户外服装"已更新，新增了3张图片。',
        userId: 'all',
        read: true,
        createdAt: new Date(Date.now() - 2 * 24 * 60 * 60 * 1000).toISOString(), // 2天前
        data: { albumId: 'album-1', albumName: '户外服装' },
      },
      {
        id: 'notif-4',
        type: 'system',
        title: '系统维护通知',
        message: '系统将于今晚22:00-23:00进行例行维护，届时服务可能短暂中断。',
        userId: 'all',
        read: true,
        createdAt: new Date(Date.now() - 3 * 24 * 60 * 60 * 1000).toISOString(), // 3天前
      },
    ];
  }
  return globalThis.__notificationsStore;
}

// 获取用户的通知（包括发给所有人的通知）
export function getUserNotifications(userId: string): Notification[] {
  const store = getNotificationsStore();
  return store.filter(n => n.userId === 'all' || n.userId === userId);
}

// 获取未读通知数量
export function getUnreadCount(userId: string): number {
  return getUserNotifications(userId).filter(n => !n.read).length;
}

// 标记通知为已读
export function markAsRead(notificationId: string, userId: string): boolean {
  const store = getNotificationsStore();
  const notification = store.find(n => n.id === notificationId);
  
  if (!notification) return false;
  
  // 验证用户权限
  if (notification.userId !== 'all' && notification.userId !== userId) {
    return false;
  }
  
  notification.read = true;
  return true;
}

// 标记所有通知为已读
export function markAllAsRead(userId: string): number {
  const store = getNotificationsStore();
  let count = 0;
  
  for (const notification of store) {
    if ((notification.userId === 'all' || notification.userId === userId) && !notification.read) {
      notification.read = true;
      count++;
    }
  }
  
  return count;
}

// 删除通知
export function deleteNotification(notificationId: string, userId: string): boolean {
  const store = getNotificationsStore();
  const index = store.findIndex(n => n.id === notificationId);
  
  if (index === -1) return false;
  
  // 验证用户权限
  const notification = store[index];
  if (notification.userId !== 'all' && notification.userId !== userId) {
    return false;
  }
  
  store.splice(index, 1);
  return true;
}

// 清空所有已读通知
export function clearReadNotifications(userId: string): number {
  const store = getNotificationsStore();
  let count = 0;
  
  for (let i = store.length - 1; i >= 0; i--) {
    const notification = store[i];
    if ((notification.userId === 'all' || notification.userId === userId) && notification.read) {
      store.splice(i, 1);
      count++;
    }
  }
  
  return count;
}

// 创建新通知
export function createNotification(
  type: Notification['type'],
  title: string,
  message: string,
  userId: string = 'all',
  data?: Record<string, unknown>
): Notification {
  const notification: Notification = {
    id: generateNotificationId(),
    type,
    title,
    message,
    userId,
    read: false,
    createdAt: new Date().toISOString(),
    data,
  };
  
  const store = getNotificationsStore();
  store.unshift(notification); // 新通知放在最前面
  
  return notification;
}

// 格式化时间显示
export function formatNotificationTime(dateString: string): string {
  const date = new Date(dateString);
  const now = new Date();
  const diff = now.getTime() - date.getTime();
  
  const minutes = Math.floor(diff / (1000 * 60));
  const hours = Math.floor(diff / (1000 * 60 * 60));
  const days = Math.floor(diff / (1000 * 60 * 60 * 24));
  
  if (minutes < 1) return '刚刚';
  if (minutes < 60) return `${minutes}分钟前`;
  if (hours < 24) return `${hours}小时前`;
  if (days < 7) return `${days}天前`;
  
  return date.toLocaleDateString('zh-CN', {
    month: 'short',
    day: 'numeric',
  });
}
