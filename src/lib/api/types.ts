/**
 * API 配置
 * 
 * 后端 API 基础配置
 */

// 后端 API 地址
export const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';

// API 响应类型
export interface ApiResponse<T = unknown> {
  code: number;
  message: string;
  data: T;
  timestamp: number;
}

// 分页响应类型
export interface PageResponse<T> {
  list: T[];
  total: number;
  page: number;
  pageSize: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

// 图片类型
export interface ImageItem {
  id: string;
  title: string;
  url: string;
  thumbnailUrl: string;
  size: number;
  sizeFormatted: string;
  width: number;
  height: number;
  resolution: string;
  fileType: string;
  albumId: string;
  albumName: string;
  favorite: boolean;
  tags: string[];
  description?: string;
  createdAt: string;
  updatedAt: string;
  userId: string;
  deleted: boolean;
  deletedAt?: string;
}

// 匹配配置类型（与原有分类逻辑一致）
export type MatchMode = 'contains' | 'exact' | 'startsWith' | 'endsWith' | 'regex' | 'fuzzy';

export interface MatchingConfig {
  mode: MatchMode;
  caseSensitive?: boolean;
  synonyms?: {
    keywords: string[];
    targetKeyword: string;
  }[];
}

// 智能相册类型
export interface SmartAlbum {
  id: string;
  name: string;
  description?: string;
  matchingConfig: MatchingConfig; // 使用原有的匹配配置格式
  isSystem?: boolean;
  createdAt?: string;
  updatedAt?: string;
}

// 预置智能相册配置（基于 matchingConfig）
export const PRESET_SMART_ALBUMS: SmartAlbum[] = [
  {
    id: 'smart-recent',
    name: '最近添加',
    description: '最近30天内添加的图片',
    isSystem: true,
    matchingConfig: {
      mode: 'contains',
      // 最近添加的逻辑由后端根据时间判断，这里用特殊标记
    }
  },
  {
    id: 'smart-favorites',
    name: '我的收藏',
    description: '所有收藏的图片',
    isSystem: true,
    matchingConfig: {
      mode: 'contains',
      // 收藏逻辑由后端根据 favorite 字段判断
    }
  }
];

// 用户类型
export interface User {
  id: string;
  username: string;
  email: string;
  avatarUrl?: string;
  role: string;
  membership: string;
  storageUsed: number;
  storageLimit: number;
  createdAt: string;
  lastLoginAt: string;
}

// 通知类型
export interface Notification {
  id: string;
  content: string;
  type: string;
  read: boolean;
  createdAt: string;
  resourceId?: string;
  userId: string;
}

// 相册类型
export interface Album {
  id: string;
  name: string;
  description?: string;
  coverUrl?: string;
  imageCount: number;
  matchingConfig?: MatchingConfig;
  createdAt: string;
  updatedAt: string;
  userId: string;
}

// 图片查询参数
export interface ImageQueryParams {
  keyword?: string;
  albumId?: string;
  fileType?: string;
  favorite?: boolean;
  deleted?: boolean;
  startDate?: string;
  endDate?: string;
  tags?: string[];
  sortBy?: 'date' | 'name' | 'size';
  sortOrder?: 'asc' | 'desc';
  page?: number;
  pageSize?: number;
  [key: string]: string | number | boolean | undefined | string[];
}

// 批量操作参数
export interface BatchOperationParams {
  imageIds: string[];
  targetAlbumId?: string;
  operation: 'move' | 'favorite' | 'delete' | 'restore';
}
