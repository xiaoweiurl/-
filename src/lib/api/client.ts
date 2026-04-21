/**
 * API 客户端配置
 * 用于连接 Java Spring Boot 后端
 */

// 后端 API 基础 URL
const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';

/**
 * 通用请求方法
 */
async function request<T>(
  endpoint: string,
  options: RequestInit = {}
): Promise<T> {
  const url = `${API_BASE_URL}${endpoint}`;
  
  const defaultHeaders: HeadersInit = {
    'Content-Type': 'application/json',
  };
  
  // 添加认证 token（如果有）
  if (typeof window !== 'undefined') {
    const token = localStorage.getItem('auth_token');
    if (token) {
      defaultHeaders['Authorization'] = `Bearer ${token}`;
    }
  }
  
  const config: RequestInit = {
    ...options,
    headers: {
      ...defaultHeaders,
      ...options.headers,
    },
  };
  
  const response = await fetch(url, config);
  
  if (!response.ok) {
    const error = await response.json().catch(() => ({ message: '请求失败' }));
    throw new Error(error.message || error.error || '请求失败');
  }
  
  return response.json();
}

// 导出基础请求方法
export async function get<T>(endpoint: string, params?: Record<string, unknown>): Promise<T> {
  const searchParams = params ? `?${new URLSearchParams(Object.entries(params).map(([k, v]) => [k, String(v)])).toString()}` : '';
  return request<T>(`${endpoint}${searchParams}`);
}

export async function post<T>(endpoint: string, data?: unknown): Promise<T> {
  return request<T>(endpoint, {
    method: 'POST',
    body: data ? JSON.stringify(data) : undefined,
  });
}

export async function put<T>(endpoint: string, data?: unknown): Promise<T> {
  return request<T>(endpoint, {
    method: 'PUT',
    body: data ? JSON.stringify(data) : undefined,
  });
}

export async function del<T>(endpoint: string): Promise<T> {
  return request<T>(endpoint, {
    method: 'DELETE',
  });
}

export async function upload<T>(endpoint: string, formData: FormData): Promise<T> {
  const url = `${API_BASE_URL}${endpoint}`;
  
  const headers: HeadersInit = {};
  
  // 添加认证 token（如果有）
  if (typeof window !== 'undefined') {
    const token = localStorage.getItem('auth_token');
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }
  }
  
  const response = await fetch(url, {
    method: 'POST',
    headers,
    body: formData,
  });
  
  if (!response.ok) {
    const error = await response.json().catch(() => ({ message: '上传失败' }));
    throw new Error(error.message || error.error || '上传失败');
  }
  
  return response.json();
}

/**
 * 图片相关 API
 */
export const imageApi = {
  /**
   * 查询图片列表
   */
  getImages: (params?: {
    keyword?: string;
    albumId?: string;
    fileType?: string;
    favorite?: boolean;
    sortBy?: string;
    sortOrder?: string;
    page?: number;
    pageSize?: number;
  }) => {
    const searchParams = new URLSearchParams();
    if (params) {
      Object.entries(params).forEach(([key, value]) => {
        if (value !== undefined && value !== null) {
          searchParams.append(key, String(value));
        }
      });
    }
    return request<{ success: boolean; data: any; total: number }>(`/images?${searchParams}`);
  },
  
  /**
   * 获取图片详情
   */
  getImage: (id: string) => {
    return request<{ success: boolean; data: any }>(`/images/${id}`);
  },
  
  /**
   * 上传图片
   */
  uploadImage: async (file: File, options?: {
    title?: string;
    albumId?: string;
    tags?: string[];
  }) => {
    const formData = new FormData();
    formData.append('file', file);
    if (options?.title) formData.append('title', options.title);
    if (options?.albumId) formData.append('albumId', options.albumId);
    if (options?.tags) options.tags.forEach(tag => formData.append('tags', tag));
    
    const url = `${API_BASE_URL}/images/upload`;
    const response = await fetch(url, {
      method: 'POST',
      body: formData,
    });
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: '上传失败' }));
      throw new Error(error.message || '上传失败');
    }
    
    return response.json();
  },
  
  /**
   * 更新图片
   */
  updateImage: (id: string, data: {
    title?: string;
    albumId?: string;
    tags?: string[];
    description?: string;
  }) => {
    return request<{ success: boolean; data: any }>(`/images/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    });
  },
  
  /**
   * 删除图片
   */
  deleteImage: (id: string) => {
    return request<{ success: boolean }>(`/images/${id}`, {
      method: 'DELETE',
    });
  },
  
  /**
   * 永久删除图片
   */
  permanentDelete: (id: string) => {
    return request<{ success: boolean }>(`/images/${id}/permanent`, {
      method: 'DELETE',
    });
  },
  
  /**
   * 恢复图片
   */
  restoreImage: (id: string) => {
    return request<{ success: boolean }>(`/images/${id}/restore`, {
      method: 'POST',
    });
  },
  
  /**
   * 切换收藏状态
   */
  toggleFavorite: (id: string) => {
    return request<{ success: boolean; data: any }>(`/images/${id}/favorite`, {
      method: 'POST',
    });
  },
  
  /**
   * 批量操作
   */
  batchOperation: (operation: 'delete' | 'favorite' | 'move', imageIds: string[], targetAlbumId?: string) => {
    return request<{ success: boolean }>('/images/batch', {
      method: 'POST',
      body: JSON.stringify({
        operation,
        imageIds,
        targetAlbumId,
      }),
    });
  },
  
  /**
   * 获取收藏图片
   */
  getFavorites: (page: number = 1, pageSize: number = 20) => {
    return request<{ success: boolean; data: any }>(`/images/favorites?page=${page}&pageSize=${pageSize}`);
  },
  
  /**
   * 获取回收站图片
   */
  getTrash: (page: number = 1, pageSize: number = 20) => {
    return request<{ success: boolean; data: any }>(`/images/trash?page=${page}&pageSize=${pageSize}`);
  },
  
  /**
   * 清空回收站
   */
  clearTrash: () => {
    return request<{ success: boolean }>('/images/trash', {
      method: 'DELETE',
    });
  },
};

/**
 * 相册相关 API
 */
export const albumApi = {
  /**
   * 获取所有相册
   */
  getAlbums: () => {
    return request<{ success: boolean; data: any[] }>('/albums');
  },
  
  /**
   * 获取相册详情
   */
  getAlbum: (id: string) => {
    return request<{ success: boolean; data: any }>(`/albums/${id}`);
  },
  
  /**
   * 创建相册
   */
  createAlbum: (name: string, description?: string) => {
    return request<{ success: boolean; data: any }>('/albums', {
      method: 'POST',
      body: JSON.stringify({ name, description }),
    });
  },
  
  /**
   * 更新相册
   */
  updateAlbum: (id: string, name?: string, description?: string) => {
    return request<{ success: boolean; data: any }>(`/albums/${id}`, {
      method: 'PUT',
      body: JSON.stringify({ name, description }),
    });
  },
  
  /**
   * 删除相册
   */
  deleteAlbum: (id: string) => {
    return request<{ success: boolean }>(`/albums/${id}`, {
      method: 'DELETE',
    });
  },
};

/**
 * 用户相关 API
 */
export const userApi = {
  /**
   * 登录
   */
  login: (username: string, password: string) => {
    return request<{ success: boolean; data: { user: any; token: string } }>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    });
  },
  
  /**
   * 登出
   */
  logout: () => {
    return request<{ success: boolean }>('/auth/logout', {
      method: 'POST',
    });
  },
  
  /**
   * 获取当前用户信息
   */
  getCurrentUser: () => {
    return request<{ success: boolean; data: any }>('/users/me');
  },
  
  /**
   * 获取用户统计
   */
  getStats: () => {
    return request<{ success: boolean; data: any }>('/users/stats');
  },
};

export default {
  image: imageApi,
  album: albumApi,
  user: userApi,
};
