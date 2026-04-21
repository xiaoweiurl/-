/**
 * 用户 API 服务
 * 
 * 用户相关的接口调用
 */

import { get, post } from './client';
import type { ApiResponse, User, Notification } from './types';

/**
 * 获取当前用户信息
 */
export async function getCurrentUser(): Promise<User> {
  const response = await get<ApiResponse<User>>('/user');
  return response.data;
}

/**
 * 获取用户统计信息
 */
export async function getUserStats(): Promise<{
  storageUsed: number;
  storageLimit: number;
  storagePercent: number;
  imageCount: number;
  albumCount: number;
  favoriteCount: number;
}> {
  const response = await get<ApiResponse<{
    storageUsed: number;
    storageLimit: number;
    storagePercent: number;
    imageCount: number;
    albumCount: number;
    favoriteCount: number;
  }>>('/user/stats');
  return response.data;
}

/**
 * 获取通知列表
 */
export async function getNotifications(): Promise<Notification[]> {
  const response = await get<ApiResponse<Notification[]>>('/user/notifications');
  return response.data;
}

/**
 * 获取未读通知数量
 */
export async function getUnreadCount(): Promise<number> {
  const response = await get<ApiResponse<number>>('/user/notifications/unread-count');
  return response.data;
}

/**
 * 标记通知为已读
 */
export async function markNotificationRead(id: string): Promise<void> {
  await post<ApiResponse<void>>(`/user/notifications/${id}/read`);
}
