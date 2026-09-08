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
  role: 'admin' | 'user' | 'superadmin';
  avatar?: string;
  nickname?: string;
  bio?: string;
  phone?: string;
  createdAt: string;
  lastLoginAt?: string;
  settings: UserSettings;
}

// 用户角色类型（三级权限：普通用户 / 管理员 / 超级管理员）
export type UserRole = 'user' | 'admin' | 'superadmin';

// 用户权限配置
export const PERMISSIONS = {
  superadmin: {
    canUpload: true,
    canDelete: true,
    canMove: true,
    canEditTags: true,
    canManageUsers: true,
    canViewAllImages: true,
    canManageAlbums: true,
    canErpSync: true,
  },
  admin: {
    canUpload: true,
    canDelete: true,
    canMove: true,
    canEditTags: true,
    canManageUsers: true,
    canViewAllImages: true,
    canManageAlbums: true,
    canErpSync: true,
  },
  user: {
    canUpload: true,
    canDelete: false, // 只能删除自己上传的
    canMove: true,
    canEditTags: false,
    canManageUsers: false,
    canViewAllImages: true,
    canManageAlbums: false,
    canErpSync: false,
  },
} as const;

// ==================== 三级权限辅助函数 ====================

/** 是否管理员及以上（admin / superadmin）——同步页等管理功能可见性判断 */
export function isAdminOrAbove(role?: string | null): boolean {
  return role === 'admin' || role === 'superadmin';
}

/** 是否超级管理员 */
export function isSuperAdmin(role?: string | null): boolean {
  return role === 'superadmin';
}

/** 角色显示名（三级） */
export function roleDisplayName(role?: string | null): string {
  if (role === 'superadmin') return '超级管理员';
  if (role === 'admin') return '管理员';
  return '普通用户';
}

/**
 * 操作者是否有权重置目标用户密码
 * 规则：超级管理员不受限；管理员不能修改其他管理员/超级管理员的密码（本人除外）
 */
export function canResetPasswordOf(
  operatorRole: string | null | undefined,
  operatorId: string,
  targetRole: string | null | undefined,
  targetId: string,
): boolean {
  if (operatorRole === 'superadmin') return true;
  if (operatorRole === 'admin') {
    if (operatorId === targetId) return true;
    const targetIsAdminOrAbove = targetRole === 'admin' || targetRole === 'superadmin';
    return !targetIsAdminOrAbove;
  }
  return false;
}

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
