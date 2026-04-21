/**
 * API 路由中间件
 * 提供统一的认证、权限、错误处理等装饰器
 */

import { NextRequest, NextResponse } from 'next/server';
import { validateSession, hasPermission, type User } from './auth';
import { APIError, handleAPIError, checkRateLimit, validateId } from './api-utils';

// ==========================================
// 中间件类型定义
// ==========================================

type NextHandler = (
  request: NextRequest,
  context?: { params?: Record<string, string> }
) => Promise<NextResponse>;

/**
 * 中间件选项
 */
interface MiddlewareOptions {
  /** 是否需要认证 */
  requireAuth?: boolean;
  /** 需要的权限 */
  requiredPermissions?: (keyof typeof import('./auth').PERMISSIONS.admin)[];
  /** 速率限制类型 */
  rateLimitType?: 'default' | 'upload' | 'login' | 'password';
  /** 是否允许公开访问 */
  public?: boolean;
}

// ==========================================
// 用户上下文
// ==========================================

export interface UserContext {
  user: User | null;
  sessionId: string | null;
}

const USER_CONTEXT_KEY = 'x-user-context';

/**
 * 从请求中提取Session（支持 cookie 和 x-session-id header）
 */
export function extractSession(request: NextRequest): { sessionId: string | null; user: User | null } {
  // 优先从 header 获取 x-session-id
  const sessionIdFromHeader = request.headers.get('x-session-id');
  if (sessionIdFromHeader) {
    const user = validateSession(sessionIdFromHeader);
    if (user) {
      return { sessionId: sessionIdFromHeader, user };
    }
  }
  
  // 从 cookie 中获取 session_id
  const cookieHeader = request.headers.get('cookie') || '';
  const sessionMatch = cookieHeader.match(/session_id=([^;]+)/);
  const sessionId = sessionMatch ? sessionMatch[1] : null;
  
  if (!sessionId) {
    return { sessionId: null, user: null };
  }
  
  const user = validateSession(sessionId);
  return { sessionId, user };
}

/**
 * 验证用户权限
 */
export function verifyPermissions(user: User, required: string[]): boolean {
  if (!user) return false;
  return required.every(perm => hasPermission(user, perm as keyof typeof import('./auth').PERMISSIONS.admin));
}

// ==========================================
// API 处理器工厂
// ==========================================

/**
 * 创建受保护的API处理器
 */
export function withAuth(
  handler: NextHandler,
  options: MiddlewareOptions = {}
): NextHandler {
  return async (request: NextRequest, context?: { params?: Record<string, string> }) => {
    try {
      // 速率限制检查
      if (options.rateLimitType) {
        const clientIP = getClientIP(request);
        const rateLimit = checkRateLimit(clientIP, options.rateLimitType);
        
        if (!rateLimit.allowed) {
          return NextResponse.json(
            { success: false, error: '请求过于频繁，请稍后再试' },
            { 
              status: 429,
              headers: {
                'X-RateLimit-Reset': String(Math.ceil(rateLimit.resetAt / 1000)),
                'X-RateLimit-Remaining': '0',
              }
            }
          );
        }
      }
      
      // 公开接口无需认证
      if (options.public) {
        return handler(request, context);
      }
      
      // 获取用户信息
      const { sessionId, user } = extractSession(request);
      
      // 检查是否需要认证
      if (options.requireAuth !== false && !user) {
        return NextResponse.json(
          { success: false, error: '请先登录' },
          { status: 401 }
        );
      }
      
      // 检查权限
      if (options.requiredPermissions && user) {
        const hasPerms = verifyPermissions(user, options.requiredPermissions);
        if (!hasPerms) {
          return NextResponse.json(
            { success: false, error: '您没有权限执行此操作' },
            { status: 403 }
          );
        }
      }
      
      // 创建用户上下文
      const userContext: UserContext = { user, sessionId };
      
      // 调用处理器（通过header传递用户上下文，不适合大项目，生产应使用JWT或Session存储）
      const response = await handler(request, context);
      
      // 添加用户信息到响应头（用于调试，生产可移除）
      if (user && process.env.NODE_ENV !== 'production') {
        response.headers.set('X-User-Id', user.id);
        response.headers.set('X-User-Role', user.role);
      }
      
      return response;
    } catch (error) {
      return handleAPIError(error);
    }
  };
}

/**
 * 只允许管理员访问
 */
export function adminOnly(handler: NextHandler): NextHandler {
  return withAuth(handler, {
    requireAuth: true,
    requiredPermissions: ['canManageUsers'],
  });
}

/**
 * 只允许已登录用户访问
 */
export function authOnly(handler: NextHandler): NextHandler {
  return withAuth(handler, {
    requireAuth: true,
  });
}

/**
 * 允许公开访问（但仍会记录用户信息）
 */
export function publicEndpoint(handler: NextHandler): NextHandler {
  return withAuth(handler, {
    public: true,
  });
}

/**
 * 限制请求速率
 */
export function withRateLimit(handler: NextHandler, type: 'default' | 'upload' | 'login' | 'password' = 'default'): NextHandler {
  return withAuth(handler, {
    rateLimitType: type,
    public: true,
  });
}

// ==========================================
// 路由参数验证
// ==========================================

/**
 * 验证动态路由参数ID
 */
export function validateRouteId(params: Record<string, string> | Promise<Record<string, string>>): Promise<{ id: string }> {
  return Promise.resolve(params).then(async (p) => {
    const id = p.id || p.slug || p.category;
    
    const validation = validateId(id);
    if (!validation.valid) {
      throw new APIError(validation.error || '无效的ID', 400);
    }
    
    return { id };
  });
}

// ==========================================
// 工具函数
// ==========================================

/**
 * 获取客户端IP
 */
export function getClientIP(request: NextRequest): string {
  const forwarded = request.headers.get('x-forwarded-for');
  const realIP = request.headers.get('x-real-ip');
  
  if (forwarded) {
    return forwarded.split(',')[0].trim();
  }
  
  if (realIP) {
    return realIP;
  }
  
  return 'unknown';
}

/**
 * 创建标准成功响应
 */
export function successResponse<T>(data: T, message?: string): NextResponse {
  return NextResponse.json({
    success: true,
    data,
    ...(message && { message }),
  });
}

/**
 * 创建标准错误响应
 */
export function errorResponse(message: string, status: number = 400): NextResponse {
  return NextResponse.json(
    { success: false, error: message },
    { status }
  );
}

/**
 * 创建分页响应
 */
export function paginatedResponse<T>(
  list: T[],
  total: number,
  page: number,
  pageSize: number
): NextResponse {
  return NextResponse.json({
    success: true,
    data: {
      list,
      total,
      page,
      pageSize,
      totalPages: Math.ceil(total / pageSize),
      hasMore: page * pageSize < total,
    },
  });
}
