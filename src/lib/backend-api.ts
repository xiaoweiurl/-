/**
 * 后端 Java API 客户端
 * 所有 API 调用都通过这个文件统一管理
 */

// 后端 API 基础 URL
const BACKEND_API_URL = process.env.NEXT_PUBLIC_BACKEND_API_URL || 'http://localhost:8080/api';

// 请求超时时间
const REQUEST_TIMEOUT = 30000;

/**
 * 通用请求方法
 */
async function request<T>(
  endpoint: string,
  options: RequestInit = {}
): Promise<{ success: boolean; data?: T; error?: string }> {
  const url = `${BACKEND_API_URL}${endpoint}`;
  
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), REQUEST_TIMEOUT);

  try {
    const response = await fetch(url, {
      ...options,
      signal: controller.signal,
      headers: {
        'Content-Type': 'application/json',
        ...options.headers,
      },
    });

    clearTimeout(timeoutId);

    if (!response.ok) {
      const errorData = await response.json().catch(() => ({ error: '请求失败' }));
      return { success: false, error: errorData.error || `HTTP ${response.status}` };
    }

    const data = await response.json();
    return { success: true, data };
  } catch (error) {
    clearTimeout(timeoutId);
    if (error instanceof Error && error.name === 'AbortError') {
      return { success: false, error: '请求超时' };
    }
    return { success: false, error: error instanceof Error ? error.message : '网络错误' };
  }
}

// ==================== 图片相关 API ====================

export interface ImageDTO {
  id: number;
  url: string;
  title: string;
  size: string;
  sizeInBytes: number;
  resolution: string;
  date: string;
  favorite: boolean;
  tags: string[];
  albumId: string;
  albumName: string;
  fileType: string;
  deleted: boolean;
  deletedAt?: string;
}

export interface UploadResponse {
  image: ImageDTO;
  aiClassified: boolean;
  aiRecognized: boolean;
}

export interface BatchUploadResponse {
  total: number;
  success: number;
  failed: number;
  images: UploadResponse[];
}

/**
 * 获取图片列表
 * @param params 查询参数
 */
export async function getImages(params?: {
  albumId?: string;
  favorite?: boolean;
  deleted?: boolean;
  page?: number;
  size?: number;
}): Promise<{ success: boolean; data?: ImageDTO[]; error?: string }> {
  const searchParams = new URLSearchParams();
  
  if (params?.albumId) searchParams.set('albumId', params.albumId);
  if (params?.favorite !== undefined) searchParams.set('favorite', String(params.favorite));
  if (params?.deleted !== undefined) searchParams.set('deleted', String(params.deleted));
  if (params?.page) searchParams.set('page', String(params.page));
  if (params?.size) searchParams.set('size', String(params.size));

  const query = searchParams.toString();
  return request<ImageDTO[]>(`/images${query ? `?${query}` : ''}`);
}

/**
 * 批量上传图片
 * @param formData 包含图片文件的 FormData
 */
export async function batchUploadImages(formData: FormData): Promise<{ success: boolean; data?: BatchUploadResponse; error?: string }> {
  const url = `${BACKEND_API_URL}/images/upload/batch`;
  
  try {
    const response = await fetch(url, {
      method: 'POST',
      body: formData,
    });

    if (!response.ok) {
      const errorData = await response.json().catch(() => ({ error: '上传失败' }));
      return { success: false, error: errorData.error || `HTTP ${response.status}` };
    }

    const data = await response.json();
    return { success: true, data };
  } catch (error) {
    return { success: false, error: error instanceof Error ? error.message : '网络错误' };
  }
}

/**
 * 移动图片到相册
 */
export async function moveImagesToAlbum(
  imageIds: number[],
  albumId: string
): Promise<{ success: boolean; error?: string }> {
  return request('/images/move', {
    method: 'POST',
    body: JSON.stringify({ imageIds, albumId }),
  });
}

/**
 * 标记/取消收藏
 */
export async function toggleFavorite(
  imageId: number,
  favorite: boolean
): Promise<{ success: boolean; error?: string }> {
  return request('/images/favorite', {
    method: 'POST',
    body: JSON.stringify({ imageId, favorite }),
  });
}

/**
 * 删除图片（移入回收站）
 */
export async function deleteImages(
  imageIds: number[]
): Promise<{ success: boolean; error?: string }> {
  return request('/images/delete', {
    method: 'POST',
    body: JSON.stringify({ imageIds }),
  });
}

/**
 * 恢复图片
 */
export async function restoreImages(
  imageIds: number[]
): Promise<{ success: boolean; error?: string }> {
  return request('/images/restore', {
    method: 'POST',
    body: JSON.stringify({ imageIds }),
  });
}

/**
 * 永久删除图片
 */
export async function permanentlyDeleteImages(
  imageIds: number[]
): Promise<{ success: boolean; error?: string }> {
  return request('/images/permanent-delete', {
    method: 'POST',
    body: JSON.stringify({ imageIds }),
  });
}

// ==================== 相册相关 API ====================

export interface AlbumDTO {
  id: string;
  name: string;
  description: string;
  imageCount: number;
}

/**
 * 获取所有相册
 */
export async function getAlbums(): Promise<{ success: boolean; data?: AlbumDTO[]; error?: string }> {
  return request<AlbumDTO[]>('/albums');
}

// ==================== AI 识别相关 API ====================

/**
 * AI 图片识别
 */
export async function recognizeImage(
  imageId: number
): Promise<{ success: boolean; data?: { albumId: string; albumName: string; tags: string[] }; error?: string }> {
  return request(`/ai/recognize/${imageId}`, {
    method: 'POST',
  });
}

/**
 * 批量 AI 图片识别
 */
export async function batchRecognizeImages(
  imageIds: number[]
): Promise<{ success: boolean; data?: { results: Array<{ imageId: number; albumId: string; albumName: string; tags: string[] }> }; error?: string }> {
  return request('/ai/recognize/batch', {
    method: 'POST',
    body: JSON.stringify({ imageIds }),
  });
}

// ==================== 健康检查 ====================

/**
 * 检查后端服务是否可用
 */
export async function checkBackendHealth(): Promise<boolean> {
  try {
    const response = await fetch(`${BACKEND_API_URL}/health`, {
      method: 'GET',
      signal: AbortSignal.timeout(5000),
    });
    return response.ok;
  } catch {
    return false;
  }
}
