/**
 * API 数据验证 Schema
 * 使用 Zod 进行类型安全的请求验证
 */

import { z, ZodError } from 'zod';
import { NextResponse } from 'next/server';

// ==========================================
// 用户相关 Schema
// ==========================================

/**
 * 登录请求
 */
export const loginSchema = z.object({
  username: z.string().min(1, '用户名不能为空').max(50, '用户名过长'),
  password: z.string().min(1, '密码不能为空'),
  rememberMe: z.boolean().optional().default(false),
  company: z.string().optional(),
});

export type LoginInput = z.infer<typeof loginSchema>;

/**
 * 用户注册
 */
export const registerSchema = z.object({
  username: z.string()
    .min(3, '用户名至少3个字符')
    .max(20, '用户名最多20个字符')
    .regex(/^[a-zA-Z0-9_]+$/, '用户名只能包含字母、数字和下划线'),
  password: z.string()
    .min(8, '密码至少8个字符')
    .regex(/[A-Z]/, '密码必须包含大写字母')
    .regex(/[a-z]/, '密码必须包含小写字母')
    .regex(/[0-9]/, '密码必须包含数字'),
  email: z.string().email('请输入有效的邮箱地址'),
  nickname: z.string().max(50, '昵称最多50个字符').optional(),
});

export type RegisterInput = z.infer<typeof registerSchema>;

/**
 * 密码修改
 */
export const passwordChangeSchema = z.object({
  currentPassword: z.string().min(1, '请输入当前密码'),
  newPassword: z.string()
    .min(8, '密码至少8个字符')
    .regex(/[A-Z]/, '密码必须包含大写字母')
    .regex(/[a-z]/, '密码必须包含小写字母')
    .regex(/[0-9]/, '密码必须包含数字')
    .regex(/[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>\/?]/, '密码必须包含特殊字符'),
  confirmPassword: z.string().min(1, '请确认新密码'),
}).refine(data => data.newPassword === data.confirmPassword, {
  message: '两次输入的密码不一致',
  path: ['confirmPassword'],
});

export type PasswordChangeInput = z.infer<typeof passwordChangeSchema>;

/**
 * 用户资料更新
 */
export const profileUpdateSchema = z.object({
  email: z.string().email().optional(),
  nickname: z.string().max(50).optional(),
  phone: z.string().regex(/^1[3-9]\d{9}$/, '请输入有效的手机号').optional().or(z.literal('')),
  bio: z.string().max(500, '简介最多500个字符').optional(),
});

export type ProfileUpdateInput = z.infer<typeof profileUpdateSchema>;

// ==========================================
// 图片相关 Schema
// ==========================================

/**
 * 图片查询参数
 */
export const imageQuerySchema = z.object({
  albumId: z.string().optional(),
  favorites: z.enum(['true', 'false']).transform(v => v === 'true').optional(),
  includeDeleted: z.enum(['true', 'false']).transform(v => v === 'true').optional(),
  page: z.coerce.number().int().positive().default(1),
  pageSize: z.coerce.number().int().positive().max(100).default(40),
  sortBy: z.enum(['createdAt', 'name', 'size', 'date']).default('createdAt'),
  sortOrder: z.enum(['asc', 'desc']).default('desc'),
  keyword: z.string().max(100).optional(),
  tags: z.string().optional(), // 逗号分隔的标签
});

export type ImageQueryInput = z.infer<typeof imageQuerySchema>;

/**
 * 批量操作
 */
export const batchOperationSchema = z.object({
  operation: z.enum(['delete', 'favorite', 'unfavorite', 'move', 'classify']),
  imageIds: z.array(z.number().int().positive()).min(1, '请选择至少一张图片').max(1000, '最多支持1000张图片'),
  targetAlbumId: z.number().int().positive().optional(),
  targetCategory: z.string().optional(),
});

export type BatchOperationInput = z.infer<typeof batchOperationSchema>;

/**
 * 图片分类
 */
export const classifySchema = z.object({
  imageIds: z.array(z.number().int().positive()).min(1),
  targetCategory: z.string().min(1, '请选择目标分类'),
  useAI: z.boolean().default(false),
});

export type ClassifyInput = z.infer<typeof classifySchema>;

/**
 * 回收站恢复
 */
export const restoreSchema = z.object({
  imageIds: z.array(z.number().int().positive()).min(1),
});

export type RestoreInput = z.infer<typeof restoreSchema>;

// ==========================================
// 相册相关 Schema
// ==========================================

/**
 * 创建相册
 */
export const createAlbumSchema = z.object({
  name: z.string().min(1, '相册名称不能为空').max(100, '相册名称最多100个字符'),
  description: z.string().max(500, '描述最多500个字符').optional(),
  matchingMode: z.enum(['contains', 'exact', 'startsWith', 'endsWith', 'regex', 'fuzzy']).optional(),
  matchingKeywords: z.array(z.string()).optional(),
});

export type CreateAlbumInput = z.infer<typeof createAlbumSchema>;

/**
 * 更新相册
 */
export const updateAlbumSchema = z.object({
  name: z.string().min(1).max(100).optional(),
  description: z.string().max(500).optional(),
  matchingMode: z.enum(['contains', 'exact', 'startsWith', 'endsWith', 'regex', 'fuzzy']).optional(),
  matchingConfig: z.string().optional(), // JSON字符串
});

export type UpdateAlbumInput = z.infer<typeof updateAlbumSchema>;

/**
 * 批量更新匹配模式
 */
export const batchMatchingModeSchema = z.object({
  mode: z.enum(['contains', 'exact', 'startsWith', 'endsWith', 'regex', 'fuzzy']),
});

export type BatchMatchingModeInput = z.infer<typeof batchMatchingModeSchema>;

// ==========================================
// 用户管理 Schema (管理员)
// ==========================================

/**
 * 创建用户 (管理员)
 */
export const adminCreateUserSchema = z.object({
  username: z.string().min(3).max(20).regex(/^[a-zA-Z0-9_]+$/),
  password: z.string().min(8),
  email: z.string().email(),
  role: z.enum(['admin', 'user']).default('user'),
  nickname: z.string().max(50).optional(),
});

export type AdminCreateUserInput = z.infer<typeof adminCreateUserSchema>;

/**
 * 更新用户 (管理员)
 */
export const adminUpdateUserSchema = z.object({
  username: z.string().min(3).max(20).optional(),
  email: z.string().email().optional(),
  nickname: z.string().max(50).optional(),
  phone: z.string().optional(),
  bio: z.string().max(500).optional(),
  role: z.enum(['admin', 'user']).optional(),
  membership: z.enum(['free', 'premium']).optional(),
});

export type AdminUpdateUserInput = z.infer<typeof adminUpdateUserSchema>;

/**
 * 重置密码 (管理员)
 */
export const adminResetPasswordSchema = z.object({
  newPassword: z.string().min(8),
});

export type AdminResetPasswordInput = z.infer<typeof adminResetPasswordSchema>;

// ==========================================
// 通知 Schema
// ==========================================

/**
 * 通知操作
 */
export const notificationActionSchema = z.object({
  action: z.enum(['markRead', 'markAllRead', 'clearRead']),
  notificationId: z.string().optional(), // markRead时必填
});

export type NotificationActionInput = z.infer<typeof notificationActionSchema>;

// ==========================================
// 设置 Schema
// ==========================================

/**
 * 更新设置
 */
export const updateSettingsSchema = z.object({
  theme: z.enum(['light', 'dark', 'system']).optional(),
  language: z.enum(['zh-CN', 'en-US']).optional(),
  pageSize: z.number().int().positive().max(100).optional(),
  defaultSort: z.string().optional(),
  aiRecognitionEnabled: z.boolean().optional(),
  emailNotifications: z.boolean().optional(),
  systemNotifications: z.boolean().optional(),
  uploadNotifications: z.boolean().optional(),
  autoPlayVideos: z.boolean().optional(),
  highQualityPreviews: z.boolean().optional(),
  compactMode: z.boolean().optional(),
  showFileInfo: z.boolean().optional(),
  defaultView: z.enum(['grid', 'list']).optional(),
});

export type UpdateSettingsInput = z.infer<typeof updateSettingsSchema>;

// ==========================================
// 商品 Schema
// ==========================================

/**
 * 商品查询
 */
export const productQuerySchema = z.object({
  page: z.coerce.number().int().positive().default(1),
  pageSize: z.coerce.number().int().positive().max(100).default(20),
  category: z.string().optional(),
  keyword: z.string().max(100).optional(),
});

export type ProductQueryInput = z.infer<typeof productQuerySchema>;

/**
 * 批量下载图片
 */
export const batchDownloadSchema = z.object({
  images: z.array(z.object({
    productName: z.string().min(1, '商品名称不能为空'),
    mainImageUrl: z.string().url('请输入有效的URL'),
    detailImageUrls: z.array(z.string().url()).optional(),
    category: z.string().optional(),
    description: z.string().optional(),
  })).min(1, '请提供至少一个商品').max(100, '最多支持100个商品'),
});

export type BatchDownloadInput = z.infer<typeof batchDownloadSchema>;

// ==========================================
// 验证辅助函数
// ==========================================

/**
 * 验证并返回数据或错误响应
 */
export async function validateRequest<T>(
  schema: z.ZodSchema<T>,
  request: Request
): Promise<{ success: true; data: T } | { success: false; response: NextResponse }> {
  try {
    let json: unknown;
    
    // 支持 FormData 和 JSON
    const contentType = request.headers.get('content-type') || '';
    
    if (contentType.includes('application/json')) {
      json = await request.json();
    } else if (contentType.includes('multipart/form-data')) {
      const formData = await request.formData();
      json = Object.fromEntries(formData.entries());
    } else {
      return {
        success: false,
        response: NextResponse.json(
          { success: false, error: '不支持的内容类型' },
          { status: 415 }
        ),
      };
    }
    
    const result = schema.safeParse(json);
    
    if (!result.success) {
      const errors = result.error.issues.map(issue => issue.message).join('; ');
      return {
        success: false,
        response: NextResponse.json(
          { success: false, error: errors },
          { status: 400 }
        ),
      };
    }
    
    return { success: true, data: result.data };
  } catch (error) {
    return {
      success: false,
      response: NextResponse.json(
        { success: false, error: '请求格式错误' },
        { status: 400 }
      ),
    };
  }
}

/**
 * 验证查询参数
 */
export function validateQuery<T>(
  schema: z.ZodSchema<T>,
  searchParams: URLSearchParams
): { success: true; data: T } | { success: false; error: string } {
  const params: Record<string, string> = {};
  searchParams.forEach((value, key) => {
    params[key] = value;
  });
  
  const result = schema.safeParse(params);
  
  if (!result.success) {
    const errors = result.error.issues.map(issue => issue.message).join('; ');
    return { success: false, error: errors };
  }
  
  return { success: true, data: result.data };
}
