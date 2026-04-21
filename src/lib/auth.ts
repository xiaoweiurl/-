// 用户设置模型
export interface UserSettings {
  theme: 'light' | 'dark' | 'system';
  language: 'zh-CN' | 'en-US';
  emailNotifications: boolean;
  pushNotifications: boolean;
  autoPlayVideos: boolean;
  highQualityPreviews: boolean;
  compactMode: boolean;
  showFileInfo: boolean;
  defaultView: 'grid' | 'masonry' | 'list';
  pageSize: 20 | 40 | 60 | 100;
}

// 用户数据模型
export interface User {
  id: string;
  username: string;
  email: string;
  role: 'admin' | 'user';
  avatar?: string;
  nickname?: string;
  bio?: string;
  phone?: string;
  createdAt: string;
  lastLoginAt?: string;
  settings: UserSettings;
}

// 用户角色类型
export type UserRole = 'admin' | 'user';

// 用户权限配置
export const PERMISSIONS = {
  admin: {
    canUpload: true,
    canDelete: true,
    canMove: true,
    canEditTags: true,
    canManageUsers: true,
    canViewAllImages: true,
    canManageAlbums: true,
  },
  user: {
    canUpload: true,
    canDelete: false, // 只能删除自己上传的
    canMove: true,
    canEditTags: false,
    canManageUsers: false,
    canViewAllImages: true,
    canManageAlbums: false,
  },
} as const;

// 默认用户设置
export const defaultUserSettings: UserSettings = {
  theme: 'light',
  language: 'zh-CN',
  emailNotifications: true,
  pushNotifications: true,
  autoPlayVideos: true,
  highQualityPreviews: true,
  compactMode: false,
  showFileInfo: true,
  defaultView: 'masonry',
  pageSize: 40,
};

// 模拟用户数据存储（生产环境应使用数据库）
export const usersStore: User[] = [
  {
    id: 'admin-1',
    username: 'admin',
    email: 'admin@example.com',
    role: 'admin',
    avatar: 'https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?w=100&h=100&fit=crop',
    nickname: '管理员',
    bio: '图片管理系统管理员',
    phone: '13800138000',
    createdAt: '2024-01-01',
    lastLoginAt: '2024-01-15',
    settings: { ...defaultUserSettings },
  },
  {
    id: 'user-1',
    username: 'user',
    email: 'user@example.com',
    role: 'user',
    avatar: 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=100&h=100&fit=crop',
    nickname: '普通用户',
    bio: '图片管理系统用户',
    phone: '13900139000',
    createdAt: '2024-01-10',
    lastLoginAt: '2024-01-15',
    settings: { ...defaultUserSettings },
  },
];

// Session存储（生产环境应使用Redis等）
export interface SessionData {
  userId: string;
  expiresAt: number;
  createdAt: number;
  ipAddress?: string;
  userAgent?: string;
}
export const sessionsStore: Map<string, SessionData> = new Map();

// 安全的Session ID生成（使用UUID）
export function generateSessionId(): string {
  // 使用Web Crypto API生成安全的随机数
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID();
  }
  // 降级方案：使用时间戳 + 随机数 + 哈希
  const timestamp = Date.now().toString(36);
  const randomPart = Array.from({ length: 32 }, () => 
    Math.random().toString(36).charAt(2)
  ).join('');
  const data = timestamp + randomPart;
  return hashString(data);
}

// 简单的字符串哈希函数（用于降级方案）
function hashString(str: string): string {
  let hash = 5381;
  for (let i = 0; i < str.length; i++) {
    const char = str.charCodeAt(i);
    hash = ((hash << 5) + hash) + char;
    hash = hash & hash; // Convert to 32bit integer
  }
  return Math.abs(hash).toString(36) + str.substring(0, 16);
}

// Session配置
const SESSION_CONFIG = {
  expiresInMs: 24 * 60 * 60 * 1000, // 24小时
  maxSessionsPerUser: 5, // 每个用户最大session数
};

// 创建Session
export function createSession(userId: string, metadata?: { ipAddress?: string; userAgent?: string }): string {
  // 清理过期session
  cleanupExpiredSessions();
  
  // 检查用户session数量限制
  const userSessions = Array.from(sessionsStore.entries())
    .filter(([_, session]) => session.userId === userId);
  
  if (userSessions.length >= SESSION_CONFIG.maxSessionsPerUser) {
    // 删除最老的session
    const oldest = userSessions.sort((a, b) => a[1].createdAt - b[1].createdAt)[0];
    sessionsStore.delete(oldest[0]);
  }
  
  const sessionId = generateSessionId();
  const expiresAt = Date.now() + SESSION_CONFIG.expiresInMs;
  sessionsStore.set(sessionId, { 
    userId, 
    expiresAt, 
    createdAt: Date.now(),
    ipAddress: metadata?.ipAddress,
    userAgent: metadata?.userAgent,
  });
  return sessionId;
}

// 验证Session
export function validateSession(sessionId: string): User | null {
  const session = sessionsStore.get(sessionId);
  if (!session) return null;
  
  if (Date.now() > session.expiresAt) {
    sessionsStore.delete(sessionId);
    return null;
  }
  
  const user = usersStore.find(u => u.id === session.userId);
  return user || null;
}

// 刷新Session过期时间
export function refreshSession(sessionId: string): boolean {
  const session = sessionsStore.get(sessionId);
  if (!session) return false;
  
  if (Date.now() > session.expiresAt) {
    sessionsStore.delete(sessionId);
    return false;
  }
  
  // 续期
  session.expiresAt = Date.now() + SESSION_CONFIG.expiresInMs;
  return true;
}

// 删除Session
export function deleteSession(sessionId: string): void {
  sessionsStore.delete(sessionId);
}

// 清理过期session
function cleanupExpiredSessions(): void {
  const now = Date.now();
  for (const [sessionId, session] of sessionsStore.entries()) {
    if (now > session.expiresAt) {
      sessionsStore.delete(sessionId);
    }
  }
}

// 根据用户名查找用户
export function findUserByUsername(username: string): User | undefined {
  return usersStore.find(u => u.username === username);
}

// 根据ID查找用户
export function findUserById(id: string): User | undefined {
  return usersStore.find(u => u.id === id);
}

// 检查权限
export function hasPermission(user: User, permission: keyof typeof PERMISSIONS.admin): boolean {
  return PERMISSIONS[user.role][permission];
}

// 密码强度验证
export function validatePasswordStrength(password: string): { valid: boolean; errors: string[] } {
  const errors: string[] = [];
  
  if (password.length < 8) {
    errors.push('密码长度至少8位');
  }
  if (!/[A-Z]/.test(password)) {
    errors.push('密码必须包含大写字母');
  }
  if (!/[a-z]/.test(password)) {
    errors.push('密码必须包含小写字母');
  }
  if (!/[0-9]/.test(password)) {
    errors.push('密码必须包含数字');
  }
  if (!/[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>\/?]/.test(password)) {
    errors.push('密码必须包含特殊字符');
  }
  
  return { valid: errors.length === 0, errors };
}

// 更新用户资料
export function updateUserProfile(userId: string, updates: Partial<Pick<User, 'nickname' | 'bio' | 'phone' | 'avatar'>>): User | null {
  const userIndex = usersStore.findIndex(u => u.id === userId);
  if (userIndex === -1) return null;
  
  Object.assign(usersStore[userIndex], updates);
  return usersStore[userIndex];
}

// 更新用户设置
export function updateUserSettings(userId: string, settings: Partial<UserSettings>): User | null {
  const userIndex = usersStore.findIndex(u => u.id === userId);
  if (userIndex === -1) return null;
  
  usersStore[userIndex].settings = { ...usersStore[userIndex].settings, ...settings };
  return usersStore[userIndex];
}

// 注意：密码修改功能应该在后端实现，前端仅调用API
// 这里的前端模拟不处理密码，因为密码哈希应该在后端完成

// 检查邮箱是否已被其他用户使用
export function isEmailTaken(email: string, excludeUserId?: string): boolean {
  return usersStore.some(u => u.email === email && u.id !== excludeUserId);
}

// 更新用户邮箱
export function updateUserEmail(userId: string, email: string): { success: boolean; error?: string } {
  if (isEmailTaken(email, userId)) {
    return { success: false, error: '该邮箱已被使用' };
  }
  
  const userIndex = usersStore.findIndex(u => u.id === userId);
  if (userIndex === -1) {
    return { success: false, error: '用户不存在' };
  }
  
  usersStore[userIndex].email = email;
  return { success: true };
}
