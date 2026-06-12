/**
 * 后端 API 代理工具
 * 所有请求通过 Next.js API 代理转发到 Java 后端，避免外网访问时的 CORS 和 Private Network Access 问题
 */

// 后端 API 基础 URL（服务端代理内部使用）
const BACKEND_INTERNAL_URL = process.env.NEXT_PUBLIC_BACKEND_API_URL || 'http://localhost:8080/api';

/**
 * 获取后端 API 代理路径
 * 所有前端请求统一走同源 /api/proxy/... 路径，由 Next.js 服务端代理转发
 * 彻底避免跨域和 Private Network Access 限制
 */
const getBackendApiUrl = () => '/api/proxy';

/**
 * 获取后端内部直连地址（仅服务端 API route 使用）
 */
export function getBackendInternalUrl(): string {
  return BACKEND_INTERNAL_URL;
}

/**
 * 获取 sessionId（从 localStorage 或请求头）
 * @param requestHeaders 可选的请求头对象（用于服务端 API route）
 */
export function getSessionId(requestHeaders?: Record<string, string | null>): string | null {
  // 服务端调用：从请求头获取
  if (requestHeaders) {
    const cookie = requestHeaders['cookie'];
    if (cookie && typeof cookie === 'string') {
      const match = cookie.match(/session_id=([^;]+)/);
      if (match) return match[1];
    }
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
  
  // 如果在缓存有效期内，直接返回缓存结果
  if (backendAvailableCache !== null && (now - lastCheckTime) < CACHE_TTL) {
    return backendAvailableCache;
  }
  
  try {
    // 使用 GET 请求检测后端可用性（不用 OPTIONS，Spring Boot 可能不支持）
    const response = await fetch(`${getBackendApiUrl()}/albums`, {
      method: 'GET',
      signal: AbortSignal.timeout(5000),
    });
    // 只要后端有响应（包括 401/403/404），都说明后端服务在运行
    backendAvailableCache = response.status < 500;
    lastCheckTime = now;
    console.log(`[Backend] 后端服务${backendAvailableCache ? '可用' : '不可用'} (status: ${response.status})`);
    return backendAvailableCache;
  } catch (error) {
    backendAvailableCache = false;
    lastCheckTime = now;
    console.log(`[Backend] 后端服务不可用`, error instanceof Error ? error.message : error);
    return false;
  }
}

/**
 * 获取后端 API URL（兼容旧代码）
 */
export function getBackendUrl(): string {
  return getBackendApiUrl();
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
  requestHeaders?: Record<string, string | null>;
}

/**
 * 发送请求到后端（通过 Next.js 代理）
 */
export async function backendFetch(
  endpoint: string,
  options: BackendRequestOptions = {}
): Promise<Response> {
  const url = `${getBackendApiUrl()}${endpoint}`;

  const fetchOptions: RequestInit = {
    method: options.method || 'GET',
    signal: AbortSignal.timeout(options.timeout || 15000),
  };

  const inputHeaders = options.headers || {};
  const headers: Record<string, string> = {};
  
  for (const [key, value] of Object.entries(inputHeaders)) {
    if (value !== undefined) {
      headers[key] = value;
    }
  }

  if (options.body && options.method !== 'GET' && !headers['Content-Type']) {
    headers['Content-Type'] = 'application/json';
  }

  let sessionId: string | null | undefined = headers['X-Session-Id'];
  if (!sessionId) {
    sessionId = getSessionId(options.requestHeaders);
  }

  if (sessionId) {
    headers['X-Session-Id'] = sessionId;
  }

  fetchOptions.headers = headers;

  if (options.body && options.method !== 'GET') {
    fetchOptions.body = typeof options.body === 'string' ? options.body : JSON.stringify(options.body);
  }

  console.log(`[Backend] ${options.method || 'GET'} ${url}`);
  if (sessionId) {
    console.log(`[Backend] X-Session-Id: ${sessionId.substring(0, 8)}...`);
  }

  const maxRetries = 2;
  let lastError: Error | null = null;
  
  for (let attempt = 0; attempt <= maxRetries; attempt++) {
    try {
      const response = await fetch(url, fetchOptions);
      return response;
    } catch (error) {
      lastError = error instanceof Error ? error : new Error(String(error));
      if (attempt < maxRetries && (lastError.message.includes('Failed to fetch') || lastError.message.includes('ECONNREFUSED') || lastError.message.includes('Timeout'))) {
        await new Promise(resolve => setTimeout(resolve, 1000 * (attempt + 1)));
        continue;
      }
      console.warn(`[Backend] 请求失败 (${attempt + 1}次): ${url}`, lastError.message);
      throw lastError;
    }
  }
  throw lastError;
}

/**
 * 发送 FormData 请求到后端（通过 Next.js 代理）
 */
export async function backendFetchFormData(
  endpoint: string,
  formData: FormData,
  requestHeaders?: Record<string, string | null>
): Promise<Response> {
  const url = `${getBackendApiUrl()}${endpoint}`;
  
  const sessionId = getSessionId(requestHeaders);
  
  console.log(`[Backend] POST (FormData) ${url}`);
  if (sessionId) {
    console.log(`[Backend] X-Session-Id: ${sessionId.substring(0, 8)}...`);
  }
  
  const fetchOptions: RequestInit = {
    method: 'POST',
    body: formData,
  };
  
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
 */
export async function handleBackendResponse(response: Response): Promise<{ success: boolean; data?: unknown; error?: string; message?: string }> {
  try {
    if (!response.ok) {
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
    
    const text = await response.text();
    
    if (!text) {
      return { success: true, data: undefined };
    }
    
    const result = JSON.parse(text);
    
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
  async login(username: string, password: string, rememberMe?: boolean): Promise<Response> {
    return backendFetch('/auth/login', {
      method: 'POST',
      body: { username, password, rememberMe },
    });
  },
  
  async logout(): Promise<Response> {
    return backendFetch('/auth/logout', { method: 'POST' });
  },
  
  async validateSession(): Promise<Response> {
    return backendFetch('/auth/session');
  },
};

// ==========================================
// 图片相关 API
// ==========================================

export const imageApi = {
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
    otherUsers?: boolean;
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
      otherUsers: restParams.otherUsers,
      includeDeleted: restParams.includeDeleted,
    });

    return backendFetch(`/images${queryString}`, { requestHeaders });
  },
  
  async get(id: string): Promise<Response> {
    return backendFetch(`/images/${id}`);
  },
  
  async upload(formData: FormData): Promise<Response> {
    return backendFetchFormData('/images/upload', formData);
  },
  
  async uploadBatch(formData: FormData): Promise<Response> {
    return backendFetchFormData('/images/upload/batch', formData);
  },
  
  async update(id: string, data: { title?: string; albumId?: string; tags?: string[]; description?: string }): Promise<Response> {
    return backendFetch(`/images/${id}`, {
      method: 'PUT',
      body: data,
    });
  },
  
  async delete(id: string): Promise<Response> {
    return backendFetch(`/images/${id}`, { method: 'DELETE' });
  },
  
  async permanentDelete(id: string): Promise<Response> {
    return backendFetch(`/images/${id}/permanent`, { method: 'DELETE' });
  },
  
  async restore(id: string): Promise<Response> {
    return backendFetch(`/images/${id}/restore`, { method: 'POST' });
  },
  
  async toggleFavorite(id: string): Promise<Response> {
    return backendFetch(`/images/${id}/favorite`, { method: 'POST' });
  },
  
  async batchOperation(operation: 'delete' | 'favorite' | 'move', imageIds: string[], targetAlbumId?: string): Promise<Response> {
    return backendFetch('/images/batch', {
      method: 'POST',
      body: { operation, imageIds, targetAlbumId },
    });
  },
  
  async moveImages(imageIds: string[], targetAlbumId: string): Promise<Response> {
    return backendFetch('/images/move', {
      method: 'POST',
      body: { imageIds, targetAlbumId },
    });
  },
  
  async deleteImages(imageIds: string[], permanent?: boolean): Promise<Response> {
    return backendFetch('/images/delete', {
      method: 'POST',
      body: { imageIds, permanent },
    });
  },
  
  async getFavorites(page: number = 1, pageSize: number = 20): Promise<Response> {
    return backendFetch(`/images/favorites?page=${page}&pageSize=${pageSize}`);
  },
  
  async getTrash(page: number = 1, pageSize: number = 20): Promise<Response> {
    return backendFetch(`/images/trash?page=${page}&pageSize=${pageSize}`);
  },
  
  async clearTrash(): Promise<Response> {
    return backendFetch('/images/trash', { method: 'DELETE' });
  },
  
  async getTrashCount(): Promise<Response> {
    return backendFetch('/images/trash/count');
  },
  
  async restoreFromTrash(imageIds: string[]): Promise<Response> {
    return backendFetch('/images/trash/restore', {
      method: 'POST',
      body: { imageIds },
    });
  },
  
  async getTags(requestHeaders?: Record<string, string | null>): Promise<Response> {
    return backendFetch('/images/tags', { requestHeaders });
  },
  
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
  
  async classify(imageIds: string[], targetCategory: string, useAI?: boolean, requestHeaders?: Record<string, string | null>): Promise<Response> {
    return backendFetch('/images/classify', {
      method: 'POST',
      body: { imageIds, targetCategory, useAI },
      requestHeaders,
    });
  },
  
  async getStats(requestHeaders?: Record<string, string | null>): Promise<Response> {
    return backendFetch('/images/stats', { requestHeaders });
  },
};

// ==========================================
// 相册相关 API
// ==========================================

export const albumApi = {
  async list(requestHeaders?: Record<string, string | null>): Promise<Response> {
    return backendFetch('/albums', { requestHeaders });
  },
  
  async get(id: string, requestHeaders?: Record<string, string | null>): Promise<Response> {
    return backendFetch(`/albums/${id}`, { requestHeaders });
  },
  
  async create(name: string, description?: string, requestHeaders?: Record<string, string | null>): Promise<Response> {
    return backendFetch('/albums', {
      method: 'POST',
      body: { name, description },
      requestHeaders,
    });
  },
  
  async update(id: string, name: string, description?: string, requestHeaders?: Record<string, string | null>): Promise<Response> {
    return backendFetch(`/albums/${id}`, {
      method: 'PUT',
      body: { name, description },
      requestHeaders,
    });
  },
  
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
  async list(requestHeaders?: Record<string, string | null>): Promise<Response> {
    return backendFetch('/categories', { requestHeaders });
  },
  
  async get(id: string, requestHeaders?: Record<string, string | null>): Promise<Response> {
    return backendFetch(`/categories/${id}`, { requestHeaders });
  },
  
  async getImages(id: string, page: number = 1, pageSize: number = 40, requestHeaders?: Record<string, string | null>): Promise<Response> {
    return backendFetch(`/categories/${id}/images?page=${page}&pageSize=${pageSize}`, { requestHeaders });
  },
  
  async getTags(requestHeaders?: Record<string, string | null>): Promise<Response> {
    return backendFetch('/categories/tags', { requestHeaders });
  },
};

// ==========================================
// 用户相关 API
// ==========================================

export const userApi = {
  async getCurrentUser(requestHeaders?: Record<string, string | null>): Promise<Response> {
    return backendFetch('/user', { requestHeaders });
  },
  
  async getStats(requestHeaders?: Record<string, string | null>): Promise<Response> {
    return backendFetch('/user/stats', { requestHeaders });
  },
  
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
  
  async changePassword(currentPassword: string, newPassword: string, confirmPassword: string, requestHeaders?: Record<string, string | null>): Promise<Response> {
    return backendFetch('/user/password', {
      method: 'PUT',
      body: { currentPassword, newPassword, confirmPassword },
      requestHeaders,
    });
  },
  
  async getSettings(requestHeaders?: Record<string, string | null>): Promise<Response> {
    return backendFetch('/user/settings', { requestHeaders });
  },
  
  async updateSettings(settings: unknown, requestHeaders?: Record<string, string | null>): Promise<Response> {
    return backendFetch('/user/settings', {
      method: 'PUT',
      body: settings,
      requestHeaders,
    });
  },
  
  async getNotifications(requestHeaders?: Record<string, string | null>): Promise<Response> {
    return backendFetch('/user/notifications', { requestHeaders });
  },
  
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
  
  async deleteNotification(id: string, requestHeaders?: Record<string, string | null>): Promise<Response> {
    return backendFetch(`/user/notifications/${id}`, { method: 'DELETE', requestHeaders });
  },
  
  async getUnreadCount(requestHeaders?: Record<string, string | null>): Promise<Response> {
    return backendFetch('/user/notifications/unread-count', { requestHeaders });
  },
  
  async markNotificationRead(id: string, requestHeaders?: Record<string, string | null>): Promise<Response> {
    return backendFetch(`/user/notifications/${id}/read`, { method: 'POST', requestHeaders });
  },
  
  async markAllNotificationsRead(requestHeaders?: Record<string, string | null>): Promise<Response> {
    return backendFetch('/user/notifications/read-all', { method: 'POST', requestHeaders });
  },
};

// ==========================================
// AI 相关 API
// ==========================================

export const aiApi = {
  async recognize(imageUrls: string[], useKeywordMatch?: boolean, useVisionRecognition?: boolean, requestHeaders?: Record<string, string | null>): Promise<Response> {
    return backendFetch('/ai/recognize', {
      method: 'POST',
      body: { imageUrls, useKeywordMatch, useVisionRecognition },
      requestHeaders,
    });
  },
  
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
  async getUsers(requestHeaders?: Record<string, string | null>): Promise<Response> {
    return backendFetch('/admin/users', { requestHeaders });
  },
  
  async getUser(id: string, requestHeaders?: Record<string, string | null>): Promise<Response> {
    return backendFetch(`/admin/users/${id}`, { requestHeaders });
  },
  
  async getStats(requestHeaders?: Record<string, string | null>): Promise<Response> {
    return backendFetch('/admin/stats', { requestHeaders });
  },
};
