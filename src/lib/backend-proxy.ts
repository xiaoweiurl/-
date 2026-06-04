/**
 * 后端 API 代理工具
 * 所有请求必须通过 Java 后端处理
 */

// 后端 API 基础 URL
const BACKEND_API_URL = process.env.NEXT_PUBLIC_BACKEND_API_URL || 'http://localhost:8080/api';

/**
 * 获取 sessionId（从 localStorage 或请求头）
 * @param requestHeaders 可选的请求头对象（用于服务端 API route）
 */
export function getSessionId(requestHeaders?: Record<string, string | null>): string | null {
  // 服务端调用：从请求头获取
  if (requestHeaders) {
    // 从 cookie 中提取 session_id
    const cookie = requestHeaders['cookie'];
    if (cookie && typeof cookie === 'string') {
      const match = cookie.match(/session_id=([^;]+)/);
      if (match) return match[1];
    }
    // 直接从 header 获取 X-Session-Id
    const xSessionId = requestHeaders['x-session-id'];
    if (xSessionId && typeof xSessionId === 'string') return xSessionId;
    return null;
  }
  
  // 客户端调用：从 localStorage 获取
  if (typeof window === 'undefined') return null;
  return localStorage.getItem('session_id');
}

// 后端可用性缓存
let backendAvailableCache: boolean | null = null;
let lastCheckTime = 0;
const CACHE_TTL = 30000; // 30秒缓存

/**
 * 检查后端服务是否可用
 */
export async function isBackendAvailable(): Promise<boolean> {
  const now = Date.now();
  
  try {
    // 调用后端健康检查端点（如果存在）
    // 使用 OPTIONS 请求避免发送 body
    const response = await fetch(`${BACKEND_API_URL}/albums`, {
      method: 'OPTIONS',
      signal: AbortSignal.timeout(3000),
    });
    backendAvailableCache = response.ok || response.status === 400; // 400 表示后端可达但参数错误
    lastCheckTime = now;
    console.log(`[Backend] 后端服务可用: ${BACKEND_API_URL} (status: ${response.status})`);
    return true;
  } catch (error) {
    backendAvailableCache = false;
    lastCheckTime = now;
    console.log(`[Backend] 后端服务不可用: ${BACKEND_API_URL}`, error instanceof Error ? error.message : error);
    return false;
  }
}

/**
 * 获取后端 API URL
 */
export function getBackendUrl(): string {
  return BACKEND_API_URL;
}

/**
 * 后端请求配置
 */
interface BackendRequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  body?: unknown;
  headers?: Record<string, string | undefined>;
  timeout?: number;
  credentials?: RequestCredentials;
  /**
   * 请求头对象（用于服务端 API route 从请求中获取 sessionId）
   */
  requestHeaders?: Record<string, string | null>;
}

/**
 * 发送请求到后端
 */
export async function backendFetch(
  endpoint: string,
  options: BackendRequestOptions = {}
): Promise<Response> {
  const url = `${BACKEND_API_URL}${endpoint}`;

  // 初始化 fetchOptions
  const fetchOptions: RequestInit = {
    method: options.method || 'GET',
    signal: options.timeout ? AbortSignal.timeout(options.timeout) : undefined,
    // 跨域请求配置
    mode: 'cors',
    credentials: 'include',
  };

  // 构建 headers，过滤掉 undefined 值
  const inputHeaders = options.headers || {};
  const headers: Record<string, string> = {};
  
  for (const [key, value] of Object.entries(inputHeaders)) {
    if (value !== undefined) {
      headers[key] = value;
    }
  }

  // 如果是POST/PUT/PATCH请求，自动添加Content-Type
  if (options.body && options.method !== 'GET' && !headers['Content-Type']) {
    headers['Content-Type'] = 'application/json';
  }

  // 获取 sessionId（支持多种方式）
  // 1. 从 headers 中直接获取 X-Session-Id
  // 2. 从 requestHeaders 中获取（兼容旧方式）
  let sessionId: string | null | undefined = headers['X-Session-Id'];
  if (!sessionId) {
    sessionId = getSessionId(options.requestHeaders);
  }

  // 添加 sessionId 到请求头（关键！）
  if (sessionId) {
    headers['X-Session-Id'] = sessionId;
  }

  fetchOptions.headers = headers;

  // 序列化body
  if (options.body && options.method !== 'GET') {
    fetchOptions.body = typeof options.body === 'string' ? options.body : JSON.stringify(options.body);
  }

  console.log(`[Backend] ${options.method || 'GET'} ${url}`);
  if (sessionId) {
    console.log(`[Backend] X-Session-Id: ${sessionId.substring(0, 8)}...`);
  }

  try {
    const response = await fetch(url, fetchOptions);
    return response;
  } catch (error) {
    console.error(`[Backend] 请求失败: ${url}`, error);
    throw error;
  }
}

/**
 * 发送 FormData 请求到后端
 */
export async function backendFetchFormData(
  endpoint: string,
  formData: FormData,
  requestHeaders?: Record<string, string | null>
): Promise<Response> {
  const url = `${BACKEND_API_URL}${endpoint}`;
  
  // 获取 sessionId（支持服务端和客户端）
  const sessionId = getSessionId(requestHeaders);
  
  console.log(`[Backend] POST (FormData) ${url}`);
  if (sessionId) {
    console.log(`[Backend] X-Session-Id: ${sessionId.substring(0, 8)}...`);
  }
  
  const fetchOptions: RequestInit = {
    method: 'POST',
    body: formData,
    credentials: 'include',
  };
  
  // 添加 sessionId 到请求头
  if (sessionId) {
    fetchOptions.headers = {
      'X-Session-Id': sessionId,
    };
  }
  
  try {
    const response = await fetch(url, fetchOptions);
    return response;
  } catch (error) {
    console.error(`[Backend] FormData 请求失败: ${url}`, error);
    throw error;
  }
}

/**
 * 转换后端响应为统一格式
 * 安全处理空响应和解析错误
 */
export async function handleBackendResponse(response: Response): Promise<{ success: boolean; data?: unknown; error?: string; message?: string }> {
  try {
    // 先检查响应状态
    if (!response.ok) {
      // 尝试读取错误消息
      const text = await response.text();
      if (text) {
        try {
          const errorData = JSON.parse(text);
          const errorMsg = errorData.message || errorData.error || errorData.msg || `请求失败 (${response.status})`;
          return { success: false, error: errorMsg };
        } catch {
          return { success: false, error: `请求失败 (${response.status})` };
        }
      }
      return { success: false, error: `请求失败 (${response.status})` };
    }
    
    // 安全读取响应体
    const text = await response.text();
    
    // 空响应视为成功（HTTP 200 即可）
    if (!text) {
      return { success: true, data: undefined };
    }
    
    // 解析 JSON
    const result = JSON.parse(text);
    
    // 后端返回格式：{ code, message, data } 或 { success, message, data }
    // 兼容多种响应格式
    // 如果 HTTP 状态码是 200 且没有明确的错误标志，则视为成功
    const isSuccess = 
      result.code === 200 || 
      result.code === undefined || 
      result.success === true ||
      (result.code !== 400 && result.code !== 401 && result.code !== 403 && result.code !== 500 && result.success !== false);
    
    if (isSuccess) {
      return { 
        success: true, 
        data: result.data ?? result,
        message: result.message 
      };
    } else {
      return { success: false, error: result.message || result.error || '请求失败' };
    }
  } catch {
    return { success: false, error: '解析响应失败' };
  }
}

/**
 * 构建查询字符串
 */
export function buildQueryString(params: Record<string, string | number | boolean | undefined>): string {
  const searchParams = new URLSearchParams();
  
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') {
      searchParams.append(key, String(value));
    }
  }
  
  const queryString = searchParams.toString();
  return queryString ? `?${queryString}` : '';
}

// ==========================================
// 认证相关 API
// ==========================================

export const authApi = {
  /**
   * 用户登录
   */
  async login(username: string, password: string, rememberMe?: boolean): Promise<Response> {
    return backendFetch('/auth/login', {
      method: 'POST',
      body: { username, password, rememberMe },
    });
  },
  
  /**
   * 用户登出
   */
  async logout(): Promise<Response> {
    return backendFetch('/auth/logout', { method: 'POST' });
  },
  
  /**
   * 验证会话
   */
  async validateSession(): Promise<Response> {
    return backendFetch('/auth/session');
  },
};

// ==========================================
// 图片相关 API
// ==========================================

export const imageApi = {
  /**
   * 获取图片列表
   */
  async list(params: {
    page?: number;
    size?: number;
    pageSize?: number;
    albumId?: string;
    favorite?: boolean;
    deleted?: boolean;
    sortBy?: string;
    sortOrder?: string;
    search?: string;
    keyword?: string;
    tag?: string;
    onlyMainImage?: boolean;
    onlyMine?: boolean;
    includeDeleted?: boolean;
    requestHeaders?: Record<string, string | null>;
  } = {}): Promise<Response> {
    const { requestHeaders, ...restParams } = params;
    const queryString = buildQueryString({
      page: restParams.page || 1,
      pageSize: restParams.pageSize || restParams.size || 40,
      albumId: restParams.albumId,
      favorite: restParams.favorite,
      deleted: restParams.deleted,
      sortBy: restParams.sortBy,
      sortOrder: restParams.sortOrder,
      keyword: restParams.search || restParams.keyword,
      tag: restParams.tag,
      onlyMainImage: restParams.onlyMainImage,
      onlyMine: restParams.onlyMine,
      includeDeleted: restParams.includeDeleted,
    });

    return backendFetch(`/images${queryString}`, { requestHeaders });
  },
  
  /**
   * 获取图片详情
   */
  async get(id: string): Promise<Response> {
    return backendFetch(`/images/${id}`);
  },
  
  /**
   * 上传图片
   */
  async upload(formData: FormData): Promise<Response> {
    return backendFetchFormData('/images/upload', formData);
  },
  
  /**
   * 批量上传图片
   */
  async uploadBatch(formData: FormData): Promise<Response> {
    return backendFetchFormData('/images/upload/batch', formData);
  },
  
  /**
   * 更新图片
   */
  async update(id: string, data: { title?: string; albumId?: string; tags?: string[]; description?: string }): Promise<Response> {
    return backendFetch(`/images/${id}`, {
      method: 'PUT',
      body: data,
    });
  },
  
  /**
   * 删除图片（移至回收站）
   */
  async delete(id: string): Promise<Response> {
    return backendFetch(`/images/${id}`, { method: 'DELETE' });
  },
  
  /**
   * 永久删除图片
   */
  async permanentDelete(id: string): Promise<Response> {
    return backendFetch(`/images/${id}/permanent`, { method: 'DELETE' });
  },
  
  /**
   * 恢复图片
   */
  async restore(id: string): Promise<Response> {
    return backendFetch(`/images/${id}/restore`, { method: 'POST' });
  },
  
  /**
   * 切换收藏状态
   */
  async toggleFavorite(id: string): Promise<Response> {
    return backendFetch(`/images/${id}/favorite`, { method: 'POST' });
  },
  
  /**
   * 批量操作
   */
  async batchOperation(operation: 'delete' | 'favorite' | 'move', imageIds: string[], targetAlbumId?: string): Promise<Response> {
    return backendFetch('/images/batch', {
      method: 'POST',
      body: { operation, imageIds, targetAlbumId },
    });
  },
  
  /**
   * 批量移动图片
   */
  async moveImages(imageIds: string[], targetAlbumId: string): Promise<Response> {
    return backendFetch('/images/move', {
      method: 'POST',
      body: { imageIds, targetAlbumId },
    });
  },
  
  /**
   * 批量删除图片
   */
  async deleteImages(imageIds: string[], permanent?: boolean): Promise<Response> {
    return backendFetch('/images/delete', {
      method: 'POST',
      body: { imageIds, permanent },
    });
  },
  
  /**
   * 获取收藏图片
   */
  async getFavorites(page: number = 1, pageSize: number = 20): Promise<Response> {
    return backendFetch(`/images/favorites?page=${page}&pageSize=${pageSize}`);
  },
  
  /**
   * 获取回收站图片
   */
  async getTrash(page: number = 1, pageSize: number = 20): Promise<Response> {
    return backendFetch(`/images/trash?page=${page}&pageSize=${pageSize}`);
  },
  
  /**
   * 清空回收站
   */
  async clearTrash(): Promise<Response> {
    return backendFetch('/images/trash', { method: 'DELETE' });
  },
  
  /**
   * 获取回收站主图数量
   */
  async getTrashCount(): Promise<Response> {
    return backendFetch('/images/trash/count');
  },
  
  /**
   * 恢复回收站图片
   */
  async restoreFromTrash(imageIds: string[]): Promise<Response> {
    return backendFetch('/images/trash/restore', {
      method: 'POST',
      body: { imageIds },  // 修复：传递正确的请求体格式 { imageIds: [...] }
    });
  },
  
  /**
   * 获取所有标签
   */
  async getTags(requestHeaders?: Record<string, string | null>): Promise<Response> {
    return backendFetch('/images/tags', { requestHeaders });
  },
  
  /**
   * 筛选图片
   */
  async filter(params: {
    tag?: string;
    albumId?: string;
    favorite?: boolean;
    keyword?: string;
    sortBy?: string;
    sortOrder?: string;
    page?: number;
    pageSize?: number;
    requestHeaders?: Record<string, string | null>;
  } = {}): Promise<Response> {
    const { requestHeaders, ...restParams } = params;
    const queryString = buildQueryString({
      ...restParams,
      favorite: restParams.favorite,
    });
    return backendFetch(`/images/filter${queryString}`, { requestHeaders });
  },
  
  /**
   * 分类图片
   */
  async classify(imageIds: string[], targetCategory: string, useAI?: boolean, requestHeaders?: Record<string, string | null>): Promise<Response> {
    return backendFetch('/images/classify', {
      method: 'POST',
      body: { imageIds, targetCategory, useAI },
      requestHeaders,
    });
  },
  
  /**
   * 获取图片统计
   */
  async getStats(requestHeaders?: Record<string, string | null>): Promise<Response> {
    return backendFetch('/images/stats', { requestHeaders });
  },
};

// ==========================================
// 相册相关 API
// ==========================================

export const albumApi = {
  /**
   * 获取所有相册
   */
  async list(requestHeaders?: Record<string, string | null>): Promise<Response> {
    return backendFetch('/albums', { requestHeaders });
  },
  
  /**
   * 获取相册详情
   */
  async get(id: string, requestHeaders?: Record<string, string | null>): Promise<Response> {
    return backendFetch(`/albums/${id}`, { requestHeaders });
  },
  
  /**
   * 创建相册
   */
  async create(name: string, description?: string, requestHeaders?: Record<string, string | null>): Promise<Response> {
    return backendFetch('/albums', {
      method: 'POST',
      body: { name, description },
      requestHeaders,
    });
  },
  
  /**
   * 更新相册
   */
  async update(id: string, name: string, description?: string, requestHeaders?: Record<string, string | null>): Promise<Response> {
    return backendFetch(`/albums/${id}`, {
      method: 'PUT',
      body: { name, description },
      requestHeaders,
    });
  },
  
  /**
   * 删除相册
   */
  async delete(id: string, requestHeaders?: Record<string, string | null>): Promise<Response> {
    return backendFetch(`/albums/${id}`, { 
      method: 'DELETE',
      requestHeaders,
    });
  },
};

// ==========================================
// 分类相关 API
// ==========================================

export const categoryApi = {
  /**
   * 获取所有分类
   */
  async list(requestHeaders?: Record<string, string | null>): Promise<Response> {
    return backendFetch('/categories', { requestHeaders });
  },
  
  /**
   * 获取分类详情
   */
  async get(id: string, requestHeaders?: Record<string, string | null>): Promise<Response> {
    return backendFetch(`/categories/${id}`, { requestHeaders });
  },
  
  /**
   * 获取分类下的图片
   */
  async getImages(id: string, page: number = 1, pageSize: number = 40, requestHeaders?: Record<string, string | null>): Promise<Response> {
    return backendFetch(`/categories/${id}/images?page=${page}&pageSize=${pageSize}`, { requestHeaders });
  },
  
  /**
   * 获取所有标签
   */
  async getTags(requestHeaders?: Record<string, string | null>): Promise<Response> {
    return backendFetch('/categories/tags', { requestHeaders });
  },
};

// ==========================================
// 用户相关 API
// ==========================================

export const userApi = {
  /**
   * 获取当前用户信息
   */
  async getCurrentUser(requestHeaders?: Record<string, string | null>): Promise<Response> {
    return backendFetch('/user', { requestHeaders });
  },
  
  /**
   * 获取用户统计信息
   */
  async getStats(requestHeaders?: Record<string, string | null>): Promise<Response> {
    return backendFetch('/user/stats', { requestHeaders });
  },
  
  /**
   * 更新用户资料
   */
  async updateProfile(data: {
    username?: string;
    nickname?: string;
    email?: string;
    avatar?: string;
    bio?: string;
    phone?: string;
  }, requestHeaders?: Record<string, string | null>): Promise<Response> {
    return backendFetch('/user/profile', {
      method: 'PUT',
      body: data,
      requestHeaders,
    });
  },
  
  /**
   * 修改密码
   */
  async changePassword(currentPassword: string, newPassword: string, confirmPassword: string, requestHeaders?: Record<string, string | null>): Promise<Response> {
    return backendFetch('/user/password', {
      method: 'PUT',
      body: { currentPassword, newPassword, confirmPassword },
      requestHeaders,
    });
  },
  
  /**
   * 获取用户设置
   */
  async getSettings(requestHeaders?: Record<string, string | null>): Promise<Response> {
    return backendFetch('/user/settings', { requestHeaders });
  },
  
  /**
   * 更新用户设置
   */
  async updateSettings(settings: unknown, requestHeaders?: Record<string, string | null>): Promise<Response> {
    return backendFetch('/user/settings', {
      method: 'PUT',
      body: settings,
      requestHeaders,
    });
  },
  
  /**
   * 获取通知列表
   */
  async getNotifications(requestHeaders?: Record<string, string | null>): Promise<Response> {
    return backendFetch('/user/notifications', { requestHeaders });
  },
  
  /**
   * 创建通知
   */
  async createNotification(notification: {
    type: string;
    title: string;
    content: string;
    resourceId?: string;
    data?: string;
  }, requestHeaders?: Record<string, string | null>): Promise<Response> {
    return backendFetch('/user/notifications', {
      method: 'POST',
      body: notification,
      requestHeaders,
    });
  },
  
  /**
   * 删除通知
   */
  async deleteNotification(id: string, requestHeaders?: Record<string, string | null>): Promise<Response> {
    return backendFetch(`/user/notifications/${id}`, { method: 'DELETE', requestHeaders });
  },
  
  /**
   * 获取未读通知数量
   */
  async getUnreadCount(requestHeaders?: Record<string, string | null>): Promise<Response> {
    return backendFetch('/user/notifications/unread-count', { requestHeaders });
  },
  
  /**
   * 标记通知为已读
   */
  async markNotificationRead(id: string, requestHeaders?: Record<string, string | null>): Promise<Response> {
    return backendFetch(`/user/notifications/${id}/read`, { method: 'POST', requestHeaders });
  },
  
  /**
   * 标记所有通知为已读
   */
  async markAllNotificationsRead(requestHeaders?: Record<string, string | null>): Promise<Response> {
    return backendFetch('/user/notifications/read-all', { method: 'POST', requestHeaders });
  },
};

// ==========================================
// AI 相关 API
// ==========================================

export const aiApi = {
  /**
   * 识别图片
   */
  async recognize(imageUrls: string[], useKeywordMatch?: boolean, useVisionRecognition?: boolean, requestHeaders?: Record<string, string | null>): Promise<Response> {
    return backendFetch('/ai/recognize', {
      method: 'POST',
      body: { imageUrls, useKeywordMatch, useVisionRecognition },
      requestHeaders,
    });
  },
  
  /**
   * 识别单张图片
   */
  async recognizeImage(imageId: string, requestHeaders?: Record<string, string | null>): Promise<Response> {
    return backendFetch(`/ai/recognize/${imageId}`, { 
      method: 'POST',
      requestHeaders,
    });
  },
};

// ==========================================
// 管理员相关 API
// ==========================================

export const adminApi = {
  /**
   * 获取所有用户列表
   */
  async getUsers(requestHeaders?: Record<string, string | null>): Promise<Response> {
    return backendFetch('/admin/users', { requestHeaders });
  },
  
  /**
   * 获取用户详情
   */
  async getUser(id: string, requestHeaders?: Record<string, string | null>): Promise<Response> {
    return backendFetch(`/admin/users/${id}`, { requestHeaders });
  },
  
  /**
   * 获取系统统计
   */
  async getStats(requestHeaders?: Record<string, string | null>): Promise<Response> {
    return backendFetch('/admin/stats', { requestHeaders });
  },
};
