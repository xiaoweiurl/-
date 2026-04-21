/**
 * 图片 API 服务
 * 
 * 图片相关的接口调用
 */

import { get, post, put, del, upload } from './client';
import type {
  ApiResponse,
  PageResponse,
  ImageItem,
  ImageQueryParams,
  BatchOperationParams,
} from './types';

/**
 * 查询图片列表
 */
export async function getImages(params: ImageQueryParams): Promise<PageResponse<ImageItem>> {
  const response = await get<ApiResponse<PageResponse<ImageItem>>>('/images', params);
  return response.data;
}

/**
 * 获取图片详情
 */
export async function getImageById(id: string): Promise<ImageItem> {
  const response = await get<ApiResponse<ImageItem>>(`/images/${id}`);
  return response.data;
}

/**
 * 上传图片
 */
export async function uploadImage(
  file: File,
  title?: string,
  albumId?: string,
  tags?: string[]
): Promise<ImageItem> {
  const formData = new FormData();
  formData.append('file', file);
  if (title) formData.append('title', title);
  if (albumId) formData.append('albumId', albumId);
  if (tags) {
    tags.forEach((tag) => formData.append('tags', tag));
  }

  const response = await upload<ApiResponse<ImageItem>>('/images/upload', formData);
  return response.data;
}

/**
 * 更新图片信息
 */
export async function updateImage(
  id: string,
  data: {
    title?: string;
    albumId?: string;
    tags?: string[];
    description?: string;
  }
): Promise<ImageItem> {
  const response = await put<ApiResponse<ImageItem>>(`/images/${id}`, data);
  return response.data;
}

/**
 * 删除图片（移至回收站）
 */
export async function deleteImage(id: string): Promise<void> {
  await del<ApiResponse<void>>(`/images/${id}`);
}

/**
 * 永久删除图片
 */
export async function permanentDeleteImage(id: string): Promise<void> {
  await del<ApiResponse<void>>(`/images/${id}/permanent`);
}

/**
 * 恢复图片
 */
export async function restoreImage(id: string): Promise<ImageItem> {
  const response = await post<ApiResponse<ImageItem>>(`/images/${id}/restore`);
  return response.data;
}

/**
 * 切换收藏状态
 */
export async function toggleFavorite(id: string): Promise<ImageItem> {
  const response = await post<ApiResponse<ImageItem>>(`/images/${id}/favorite`);
  return response.data;
}

/**
 * 批量操作
 */
export async function batchOperation(params: BatchOperationParams): Promise<void> {
  await post<ApiResponse<void>>('/images/batch', params);
}

/**
 * 获取收藏图片
 */
export async function getFavorites(page = 1, pageSize = 20): Promise<PageResponse<ImageItem>> {
  const response = await get<ApiResponse<PageResponse<ImageItem>>>('/images/favorites', { page, pageSize });
  return response.data;
}

/**
 * 获取回收站图片
 */
export async function getTrash(page = 1, pageSize = 20): Promise<PageResponse<ImageItem>> {
  const response = await get<ApiResponse<PageResponse<ImageItem>>>('/images/trash', { page, pageSize });
  return response.data;
}

/**
 * 清空回收站
 */
export async function clearTrash(): Promise<void> {
  await del<ApiResponse<void>>('/images/trash');
}
