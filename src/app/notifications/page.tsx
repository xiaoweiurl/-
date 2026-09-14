'use client';

import React from 'react';
import { useRouter } from 'next/navigation';
import { 
  BellOff, Check, CheckCheck, Trash2, Loader2,
  ArrowLeft, RefreshCw
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Toaster } from '@/components/ui/sonner';
import { toast } from 'sonner';
import { cn } from '@/lib/utils';
import { useNotifications } from '@/contexts/NotificationContext';

export default function NotificationsPage() {
  const router = useRouter();
  const { 
    notifications, 
    unreadCount, 
    isLoading,
    fetchNotifications, 
    markAsRead, 
    markAllAsRead, 
    deleteNotification 
  } = useNotifications();
  
  const [filter, setFilter] = React.useState<'all' | 'unread'>('all');
  const [isRefreshing, setIsRefreshing] = React.useState(false);

  // 手动刷新
  const handleRefresh = async () => {
    setIsRefreshing(true);
    await fetchNotifications();
    setTimeout(() => setIsRefreshing(false), 500);
    toast.success('已刷新');
  };

  // 标记单个通知为已读
  const handleMarkAsRead = async (notificationId: string) => {
    await markAsRead(notificationId);
    toast.success('已标记为已读');
  };

  // 全部标记已读
  const handleMarkAllRead = async () => {
    await markAllAsRead();
    toast.success('已将所有通知标记为已读');
  };

  // 删除通知
  const handleDeleteNotification = async (notificationId: string) => {
    await deleteNotification(notificationId);
    toast.success('通知已删除');
  };

  // 清空已读通知
  const handleClearRead = async () => {
    if (!confirm('确定要清空所有已读通知吗？')) return;
    
    const readNotifications = notifications.filter(n => n.read);
    for (const n of readNotifications) {
      await deleteNotification(n.id);
    }
    toast.success(`已清除 ${readNotifications.length} 条通知`);
  };

  // 格式化时间
  const formatTime = (dateString: string) => {
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
      month: 'long',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  // 通知图标和颜色
  const getNotificationStyle = (type: string) => {
    const styles: Record<string, { icon: string; color: string; bgColor: string }> = {
      system: { icon: '📢', color: 'text-[#007aff]', bgColor: 'bg-[rgba(0,122,255,0.1)]' },
      upload: { icon: '📤', color: 'text-[#34c759]', bgColor: 'bg-[rgba(52,199,89,0.1)]' },
      album: { icon: '📁', color: 'text-[#ff9500]', bgColor: 'bg-[rgba(255,149,0,0.1)]' },
      share: { icon: '🔗', color: 'text-[#007aff]', bgColor: 'bg-[rgba(0,122,255,0.1)]' },
      comment: { icon: '💬', color: 'text-[#007aff]', bgColor: 'bg-[rgba(0,122,255,0.1)]' },
      like: { icon: '❤️', color: 'text-[#ff3b30]', bgColor: 'bg-[rgba(255,59,48,0.1)]' },
      warning: { icon: '⚠️', color: 'text-[#ff9500]', bgColor: 'bg-[rgba(255,149,0,0.1)]' },
    };
    return styles[type] || styles.system;
  };

  // 筛选后的通知
  const filteredNotifications = React.useMemo(() => {
    if (filter === 'unread') {
      return notifications.filter(n => !n.read);
    }
    return notifications;
  }, [notifications, filter]);

  // 返回主页
  const handleBack = () => {
    router.push('/');
  };

  if (isLoading && notifications.length === 0) {
    return (
      <div className="min-h-screen bg-[#f2f2f7] flex items-center justify-center">
        <Loader2 className="w-8 h-8 text-[#007aff] animate-spin" />
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-[#f2f2f7]">
      <Toaster position="top-center" richColors closeButton />
      
      {/* 顶部导航 */}
      <header className="bg-white border-b border-[#e5e5ea] sticky top-0 z-40">
        <div className="max-w-4xl mx-auto px-4 sm:px-6 h-16 flex items-center justify-between">
          <div className="flex items-center gap-2 sm:gap-4 min-w-0">
            <button 
              onClick={handleBack}
              className="text-[#8e8e93] hover:text-[#8e8e93] transition-colors shrink-0"
            >
              <ArrowLeft className="w-5 h-5" />
            </button>
            <h1 className="text-lg sm:text-xl font-semibold text-[#8e8e93] truncate">通知中心</h1>
            {unreadCount > 0 && (
              <span className="px-2 py-0.5 bg-[#ff3b30] text-white text-xs rounded-full animate-pulse">
                {unreadCount} 条未读
              </span>
            )}
          </div>
          <div className="flex items-center gap-2">
            <Button
              variant="ghost"
              size="sm"
              onClick={handleRefresh}
              disabled={isRefreshing}
              className="gap-2"
            >
              <RefreshCw className={cn("w-4 h-4", isRefreshing && "animate-spin")} />
              <span className="hidden sm:inline">刷新</span>
            </Button>
            {unreadCount > 0 && (
              <Button
                variant="outline"
                size="sm"
                onClick={handleMarkAllRead}
                className="gap-2"
              >
                <CheckCheck className="w-4 h-4" />
                <span className="hidden sm:inline">全部已读</span>
              </Button>
            )}
            <Button
              variant="outline"
              size="sm"
              onClick={handleClearRead}
              disabled={notifications.filter(n => n.read).length === 0}
              className="gap-2"
            >
              <Trash2 className="w-4 h-4" />
              <span className="hidden sm:inline">清空已读</span>
            </Button>
          </div>
        </div>
      </header>

      <main className="max-w-4xl mx-auto px-4 sm:px-6 py-6 sm:py-8">
        {/* 实时更新提示 */}
        <div className="flex items-center gap-2 mb-4 text-sm text-[#8e8e93]">
          <span className="w-2 h-2 bg-[#34c759] rounded-full animate-pulse" />
          实时更新中（每30秒自动刷新）
        </div>

        {/* 筛选标签 */}
        <div className="flex gap-2 mb-6">
          <button
            onClick={() => setFilter('all')}
            className={cn(
              "px-4 py-2 rounded-lg text-sm font-medium transition-colors",
              filter === 'all'
                ? "bg-[rgba(0,122,255,0.1)] text-[#007aff]"
                : "bg-white text-[#8e8e93] hover:bg-[rgba(118,118,128,0.08)]"
            )}
          >
            全部通知 ({notifications.length})
          </button>
          <button
            onClick={() => setFilter('unread')}
            className={cn(
              "px-4 py-2 rounded-lg text-sm font-medium transition-colors",
              filter === 'unread'
                ? "bg-[rgba(0,122,255,0.1)] text-[#007aff]"
                : "bg-white text-[#8e8e93] hover:bg-[rgba(118,118,128,0.08)]"
            )}
          >
            未读 ({unreadCount})
          </button>
        </div>

        {/* 通知列表 */}
        <div className="bg-white rounded-2xl border border-[#e5e5ea] overflow-hidden">
          {filteredNotifications.length === 0 ? (
            <div className="p-16 text-center">
              <BellOff className="w-16 h-16 text-[#1c1c1e] mx-auto mb-4" />
              <p className="text-[#8e8e93] text-lg">
                {filter === 'unread' ? '没有未读通知' : '暂无通知'}
              </p>
            </div>
          ) : (
            <div className="divide-y divide-[#e5e5ea]">
              {filteredNotifications.map((notification, index) => {
                const style = getNotificationStyle(notification.type);
                
                return (
                  <div
                    key={notification.id}
                    className={cn(
                      "p-4 sm:p-5 hover:bg-[#f2f2f7] transition-all",
                      !notification.read && "bg-[rgba(0,122,255,0.03)]",
                      notification.isNew && "animate-slide-in"
                    )}
                    style={{ animationDelay: `${index * 50}ms` }}
                  >
                    <div className="flex gap-3 sm:gap-4">
                      {/* 图标 */}
                      <div className={cn(
                        "w-10 h-10 sm:w-12 sm:h-12 rounded-xl flex items-center justify-center text-xl sm:text-2xl shrink-0",
                        style.bgColor
                      )}>
                        {style.icon}
                      </div>
                      
                      {/* 内容 */}
                      <div className="flex-1 min-w-0">
                        <div className="flex items-start justify-between gap-2 sm:gap-4">
                          <div className="min-w-0">
                            <h3 className={cn(
                              "text-sm sm:text-base font-medium truncate",
                              notification.read ? "text-[#8e8e93]" : "text-[#8e8e93]"
                            )}>
                              {notification.title}
                            </h3>
                            <p className="text-xs sm:text-sm text-[#8e8e93] mt-1 line-clamp-2">
                              {notification.message}
                            </p>
                          </div>
                          {!notification.read && (
                            <span className="w-2.5 h-2.5 bg-[#007aff] rounded-full shrink-0 mt-2 animate-pulse" />
                          )}
                        </div>
                        
                        <div className="flex items-center justify-between mt-3">
                          <span className="text-xs text-[#8e8e93]">
                            {formatTime(notification.createdAt)}
                          </span>
                          <div className="flex items-center gap-1 sm:gap-2">
                            {!notification.read && (
                              <Button
                                variant="ghost"
                                size="sm"
                                onClick={() => handleMarkAsRead(notification.id)}
                                className="h-8 px-2 sm:px-3 text-[#007aff] hover:text-[#007aff] hover:bg-[rgba(0,122,255,0.1)]"
                              >
                                <Check className="w-4 h-4 sm:mr-1" />
                                <span className="hidden sm:inline">标记已读</span>
                              </Button>
                            )}
                            <Button
                              variant="ghost"
                              size="sm"
                              onClick={() => handleDeleteNotification(notification.id)}
                              className="h-8 px-2 sm:px-3 text-[#8e8e93] hover:text-[#ff3b30] hover:bg-[rgba(255,59,48,0.1)]"
                            >
                              <Trash2 className="w-4 h-4" />
                            </Button>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {/* 底部提示 */}
        {filteredNotifications.length > 0 && (
          <p className="text-center text-sm text-[#8e8e93] mt-6">
            共 {filteredNotifications.length} 条通知
          </p>
        )}
      </main>
    </div>
  );
}
