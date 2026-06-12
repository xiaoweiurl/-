/**
 * 后端 API 代理工具
 * 
 * 两套 URL 策略：
 * 1. 客户端（浏览器）: 走 /api/proxy 同源代理，避免 CORS 和 Private Network Access
 * 2. 服务端（Next.js API Route）: 直连 http://localhost:8080/api，无 CORS 限制
 */

// 后端 API 基础 URL（服务端直连用）
const BACKEND_INTERNAL_URL = process.env.BACKEND_API_URL || process.env.NEXT_PUBLIC_BACKEND_API_URL || 'http://localhost:8080/api';

// 数据库中存储的静态资源前缀
const LOCAL_STATIC_PREFIX = 'http://localhost:8080';

/**
 * 判断当前是否在服务端执行
 */
function isServerSide(): boolean {
  return typeof window === 'undefined';
}

/**
 * 获取后端 API URL
 * - 客户端: 返回 /api/proxy（同源代理路径）
 * - 服务端: 返回 http://localhost:8080/api（直连 Java 后端）
 */
const getBackendApiUrl = () => {
  if (isServerSide()) {
    return BACKEND_INTERNAL_URL;
  }
  return '/api/proxy';
};

/**
 * 获取后端内部直连地址（仅服务端 API route 使用）
 */
export function getBackendInternalUrl(): string {
  return BACKEND_INTERNAL_URL;
}

/**
 * 重写静态资源 URL（图片、文件等）
 * 
 * 数据库中存储的是 http://localhost:8080/api/uploads/... 
 * 本地访问时可以直接用，但映射访问时需要替换为映射域名
 * 
 * 逻辑：
 * - localhost 访问 → 不替换，直接用 localhost:8080
 * - 映射域名访问 → 替换为映射域名（Java后端80端口对应映射的8080）
 */
export function rewriteStaticUrl(url: string): string {
  if (!url || typeof window === 'undefined') return url;
  
  // 只处理包含 localhost:8080 的 URL（用 includes 而不是 startsWith，兼容各种路径格式）
  if (!url.includes('localhost:8080')) return url;
  
  const currentHost = window.location.hostname;
  const currentPort = window.location.port;
  
  // 本地访问不需要替换
  if (currentHost === 'localhost' || currentHost === '127.0.0.1') return url;
  
  // 映射域名访问：替换 localhost:8080 为当前域名
  // Java后端映射在外网默认80端口（不需要端口号）
  const protocol = window.location.protocol;
  const newPrefix = `${protocol}//${currentHost}`;
  
  const result = url.replace('http://localhost:8080', newPrefix);
  console.log(`[rewriteStaticUrl] ${url} → ${result}`);
  return result;
}

/**
 * 批量重写对象中的静态资源 URL
 * 递归查找所有包含 localhost:8080 的字符串字段并重写
 */
export function rewriteStaticUrls<T>(obj: T): T {
  if (typeof window === 'undefined' || !obj) return obj;
  
  if (typeof obj === 'string') {
    return rewriteStaticUrl(obj) as T;
  }
  
  if (Array.isArray(obj)) {
    return obj.map(item => rewriteStaticUrls(item)) as T;
  }
  
  if (typeof obj === 'object' && obj !== null) {
    const result = { ...obj } as Record<string, unknown>;
    for (const key of Object.keys(result)) {
      result[key] = rewriteStaticUrls(result[key]);
    }
    return result as T;
  }
  
  return obj;
}

/**
 * 根据当前访问域名动态替换图片URL
 * 本地访问(localhost/127.0.0.1): 保持 http://localhost:8080/... 不变
 * 映射访问(外网域名): 替换为 http://当前域名/... (映射的80端口,即Java后端)
 * 
 * 映射对应关系:
 *   内部 8080 → 外部 域名:80 (默认80端口省略)
 *   内部 5000 → 外部 域名:8000 (Next.js前端)
 */
export function proxyImageUrl(url: string | undefined): string {
  if (!url) return '/placeholder.svg';
  
  // 已经是相对路径，直接返回
  if (url.startsWith('/')) return url;
  
  // 完整URL: http://localhost:8080/api/uploads/xxx → /api/uploads/xxx
  // 不依赖 window 对象，SSR/CSR 通用
  const match = url.match(/^https?:\/\/[^/]+(\/.*)$/);
  if (match) return match[1];
  
  return url;
}

// 全局调试函数，可在控制台执行 window.__debugProxyImageUrl('http://localhost:8080/api/uploads/test.jpg')
if (typeof window !== 'undefined') {
  (window as unknown as Record<string, unknown>).__debugProxyImageUrl = proxyImageUrl;
}

/**
 * 获取 sessionId
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

// 后端可用性缓存（仅服务端使用）
let backendAvailableCache: boolean | null = null;
let lastCheckTime = 0;
const CACHE_TTL = 30000; // 30秒缓存

/**
 * 检查后端服务是否可用
 * - 服务端: 直接请求 Java 后端
 * - 客户端: 通过 /api/proxy 代理检测
 */
export async function isBackendAvailable(): Promise<boolean> {
  const now = Date.now();
  
  // 如果在缓存有效期内，直接返回缓存结果
  if (backendAvailableCache !== null && (now - lastCheckTime) < CACHE_TTL) {
    return backendAvailableCache;
  }
  
  try {
    if (isServerSide()) {
      // 服务端：直接请求 Java 后端
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 5000);
      const response = await fetch(`${BACKEND_INTERNAL_URL}/albums`, {
        method: 'GET',
        signal: controller.signal,
      });
      clearTimeout(timeoutId);
      
      backendAvailableCache = response.status < 500;
      lastCheckTime = now;
      console.log(`[Backend] 服务端检测后端可用性: ${response.status} -> ${backendAvailableCache}`);
      return backendAvailableCache;
    } else {
      // 客户端：通过 /api/proxy 代理检测
      const response = await fetch('/api/proxy/albums', {
        method: 'GET',
        signal: AbortSignal.timeout(20000),
      });
      
      // 502 = 代理连不上后端
      if (response.status === 502) {
        backendAvailableCache = false;
        lastCheckTime = now;
        console.log('[Backend] 后端服务不可用 (502)');
        return false;
      }
      
      // 其他任何状态码都说明后端在运行
      backendAvailableCache = true;
      lastCheckTime = now;
      console.log(`[Backend] 后端服务可用 (status: ${response.status})`);
      return true;
    }
  } catch (error) {
    backendAvailableCache = false;
    lastCheckTime = now;
    console.log('[Backend] 后端服务不可用', error instanceof Error ? error.message : error);
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
 * 发送请求到后端
 * - 客户端: 走 /api/proxy 代理
 * - 服务端: 直连 http://localhost:8080/api
 */
export async function backendFetch(
  endpoint: string,
  options: BackendRequestOptions = {}
): Promise<Response> {
  const baseUrl = getBackendApiUrl();
  const url = `${baseUrl}${endpoint}`;

  const fetchOptions: RequestInit = {
    method: options.method || 'GET',
    signal: AbortSignal.timeout(options.timeout || 30000),
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

  // 服务端从请求头获取 sessionId
  let sessionId: string | null | undefined = headers['X-Session-Id'];
  if (!sessionId) {
    sessionId = getSessionId(options.requestHeaders);
  }

  if (sessionId) {
    headers['X-Session-Id'] = sessionId;
  }

  // 服务端转发 Cookie（用于 Java 后端的 Session 认证）
  if (isServerSide() && options.requestHeaders?.cookie) {
    headers['Cookie'] = options.requestHeaders.cookie;
  }

  fetchOptions.headers = headers;

  if (options.body && options.method !== 'GET') {
    fetchOptions.body = typeof options.body === 'string' ? options.body : JSON.stringify(options.body);
  }

  console.log(`[Backend] ${isServerSide() ? 'SSR' : 'CSR'} ${options.method || 'GET'} ${url}`);

  try {
    const response = await fetch(url, fetchOptions);
    return response;
  } catch (error) {
    const err = error instanceof Error ? error : new Error(String(error));
    console.warn(`[Backend] 请求失败: ${url}`, err.message);
    throw err;
  }
}

/**
 * 发送 FormData 请求到后端
 * - 客户端: 走 /api/proxy 代理
 * - 服务端: 直连 http://localhost:8080/api
 */
export async function backendFetchFormData(
  endpoint: string,
  formData: FormData,
  requestHeaders?: Record<string, string | null>
): Promise<Response> {
  const baseUrl = getBackendApiUrl();
  const url = `${baseUrl}${endpoint}`;
  
  const sessionId = getSessionId(requestHeaders);
  
  console.log(`[Backend] ${isServerSide() ? 'SSR' : 'CSR'} POST (FormData) ${url}`);

  const fetchOptions: RequestInit = {
    method: 'POST',
    body: formData,
  };
  
  const headers: Record<string, string> = {};
  
  if (sessionId) {
    headers['X-Session-Id'] = sessionId;
  }

  // 服务端转发 Cookie
  if (isServerSide() && requestHeaders?.cookie) {
    headers['Cookie'] = requestHeaders.cookie;
  }

  fetchOptions.headers = headers;
  
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
