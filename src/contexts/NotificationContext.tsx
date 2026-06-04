'use client';

import React, { createContext, useContext, useState, useEffect, useCallback, useRef } from 'react';

export interface Notification {
  id: string;
  type: 'system' | 'upload' | 'album' | 'share' | 'comment' | 'like' | 'warning' | 'success' | 'delete' | 'download' | 'document';
  title: string;
  message: string;
  read: boolean;
  createdAt: string;
  isNew?: boolean; // 标记新通知，用于动画
}

interface NotificationContextType {
  notifications: Notification[];
  unreadCount: number;
  isLoading: boolean;
  fetchNotifications: () => Promise<void>;
  markAsRead: (id: string) => Promise<void>;
  markAllAsRead: () => Promise<void>;
  deleteNotification: (id: string) => Promise<void>;
  clearNewFlag: (id: string) => void;
  addNotification: (notification: Omit<Notification, 'id' | 'read' | 'createdAt' | 'isNew'>) => void;
}

const NotificationContext = createContext<NotificationContextType | null>(null);

// 轮询间隔（毫秒）
const POLL_INTERVAL = 30000; // 30秒

export function NotificationProvider({ children }: { children: React.ReactNode }) {
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [isLoading, setIsLoading] = useState(false);
  const previousIdsRef = useRef<Set<string>>(new Set());

  // 使用 ref 存储轮询 interval ID
  const pollIntervalRef = useRef<NodeJS.Timeout | null>(null);

  // 获取通知 - 每次调用时重新读取最新状态
  const fetchNotifications = useCallback(async () => {
    try {
      const res = await fetch('/api/notifications?limit=20', {
        credentials: 'include',  // 发送 Cookie 以便 API route 获取 sessionId
      });
      
      if (!res.ok) {
        console.error('获取通知失败: HTTP', res.status);
        return;
      }
      
      const text = await res.text();
      if (!text) {
        console.error('获取通知失败: 响应为空');
        return;
      }
      
      const data = JSON.parse(text);
      
      if (data.success) {
        const newNotifications: Notification[] = data.data || [];
        const currentIds = new Set(newNotifications.map((n: Notification) => n.id));
        const previousIds = previousIdsRef.current;
        
        // 找出新通知
        const newIds = [...currentIds].filter(id => !previousIds.has(id));
        
        // 标记新通知
        const notificationsWithNewFlag = newNotifications.map((n: Notification) => ({
          ...n,
          isNew: newIds.includes(n.id),
        }));
        
        setNotifications(notificationsWithNewFlag);
        setUnreadCount(data.unreadCount || 0);
        previousIdsRef.current = currentIds;
        
        // 3秒后移除新通知标记
        if (newIds.length > 0) {
          setTimeout(() => {
            setNotifications(prev => 
              prev.map(n => ({ ...n, isNew: false }))
            );
          }, 3000);
        }
      }
    } catch (error) {
      console.error('获取通知失败:', error);
    }
  }, []);

  // 初始加载和轮询
  useEffect(() => {
    // 立即获取一次
    fetchNotifications();
    
    // 设置轮询
    pollIntervalRef.current = setInterval(() => {
      fetchNotifications();
    }, POLL_INTERVAL);
    
    // 清理函数
    return () => {
      if (pollIntervalRef.current) {
        clearInterval(pollIntervalRef.current);
        pollIntervalRef.current = null;
      }
    };
  }, [fetchNotifications]);

  // 标记单个通知为已读
  const markAsRead = useCallback(async (id: string) => {
    try {
      const res = await fetch('/api/notifications', {
        method: 'PATCH',
        credentials: 'include',  // 发送 Cookie 以便 API route 获取 sessionId
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ action: 'markRead', notificationId: id }),
      });
      const data = await res.json();
      
      if (data.success) {
        setNotifications(prev => 
          prev.map(n => n.id === id ? { ...n, read: true } : n)
        );
        setUnreadCount(data.unreadCount || 0);
      }
    } catch (error) {
      console.error('标记已读失败:', error);
    }
  }, []);

  // 标记所有通知为已读
  const markAllAsRead = useCallback(async () => {
    try {
      const res = await fetch('/api/notifications', {
        method: 'PATCH',
        credentials: 'include',  // 发送 Cookie 以便 API route 获取 sessionId
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ action: 'markAllRead' }),
      });
      const data = await res.json();
      
      if (data.success) {
        setNotifications(prev => prev.map(n => ({ ...n, read: true })));
        setUnreadCount(0);
      }
    } catch (error) {
      console.error('标记已读失败:', error);
    }
  }, []);

  // 删除通知
  const deleteNotification = useCallback(async (id: string) => {
    try {
      const res = await fetch(`/api/notifications?id=${id}`, {
        method: 'DELETE',
        credentials: 'include',  // 发送 Cookie 以便 API route 获取 sessionId
      });
      const data = await res.json();
      
      if (data.success) {
        setNotifications(prev => prev.filter(n => n.id !== id));
        setUnreadCount(data.unreadCount || 0);
      }
    } catch (error) {
      console.error('删除通知失败:', error);
    }
  }, []);

  // 清除新通知标记
  const clearNewFlag = useCallback((id: string) => {
    setNotifications(prev => 
      prev.map(n => n.id === id ? { ...n, isNew: false } : n)
    );
  }, []);

  // 添加新通知（本地 + 服务端同步）
  const addNotification = useCallback(async (notification: Omit<Notification, 'id' | 'read' | 'createdAt' | 'isNew'>) => {
    // 先同步到服务端，确保数据持久化
    try {
      const res = await fetch('/api/notifications', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          notification: {
            type: notification.type,
            title: notification.title,
            content: notification.message,  // 后端用 content 字段
          },
        }),
      });
      
      const data = await res.json();
      
      if (data.success && data.data) {
        // 使用服务端返回的通知（包含正确的ID和时间戳）
        const serverNotification: Notification = {
          ...data.data,
          isNew: true,
        };
        
        setNotifications(prev => [serverNotification, ...prev]);
        setUnreadCount(prev => prev + 1);
        
        // 3秒后移除新通知标记
        setTimeout(() => {
          setNotifications(prev => 
            prev.map(n => n.id === serverNotification.id ? { ...n, isNew: false } : n)
          );
        }, 3000);
      }
    } catch (err) {
      console.error('创建通知失败:', err);
      // 如果服务端同步失败，仅本地创建
      const localNotification: Notification = {
        ...notification,
        id: `notif-local-${Date.now()}`,
        read: false,
        createdAt: new Date().toISOString(),
        isNew: true,
      };
      
      setNotifications(prev => [localNotification, ...prev]);
      setUnreadCount(prev => prev + 1);
    }
  }, []);

  return (
    <NotificationContext.Provider
      value={{
        notifications,
        unreadCount,
        isLoading,
        fetchNotifications,
        markAsRead,
        markAllAsRead,
        deleteNotification,
        clearNewFlag,
        addNotification,
      }}
    >
      {children}
    </NotificationContext.Provider>
  );
}

export function useNotifications() {
  const context = useContext(NotificationContext);
  if (!context) {
    throw new Error('useNotifications must be used within a NotificationProvider');
  }
  return context;
}
