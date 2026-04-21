/**
 * 相册 API 服务
 * 
 * 相册相关的接口调用
 */

import { get, post, put, del } from './client';
import type { ApiResponse, Album } from './types';

/**
 * 获取所有相册
 */
export async function getAlbums(): Promise<Album[]> {
  const response = await get<ApiResponse<Album[]>>('/albums');
  return response.data;
}

/**
 * 获取相册详情
 */
export async function getAlbumById(id: string): Promise<Album> {
  const response = await get<ApiResponse<Album>>(`/albums/${id}`);
  return response.data;
}

/**
 * 创建相册
 */
export async function createAlbum(data: { name: string; description?: string }): Promise<Album> {
  const response = await post<ApiResponse<Album>>('/albums', data);
  return response.data;
}

/**
 * 更新相册
 */
export async function updateAlbum(
  id: string,
  data: { name?: string; description?: string }
): Promise<Album> {
  const response = await put<ApiResponse<Album>>(`/albums/${id}`, data);
  return response.data;
}

/**
 * 删除相册
 */
export async function deleteAlbum(id: string): Promise<void> {
  await del<ApiResponse<void>>(`/albums/${id}`);
}
