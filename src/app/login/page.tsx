'use client';

import React from 'react';
import { useRouter } from 'next/navigation';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import { User, Lock, Eye, EyeOff, Loader2, BookOpen } from 'lucide-react';
import { toast } from 'sonner';
import { Toaster } from '@/components/ui/sonner';

interface LoginResponse {
  success: boolean;
  message?: string;
  error?: string;
  data?: {
    sessionId?: string;
    expiresIn?: number;
    user: {
      id: string;
      username: string;
      email?: string;
      avatar?: string;
      role: string;
      membership?: string;
    };
  };
}

export default function LoginPage() {
  const router = useRouter();
  const [username, setUsername] = React.useState('');
  const [password, setPassword] = React.useState('');
  const [showPassword, setShowPassword] = React.useState(false);
  const [rememberMe, setRememberMe] = React.useState(false);
  const [isLoading, setIsLoading] = React.useState(false);

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!username.trim() || !password.trim()) {
      toast.error('请输入用户名和密码');
      return;
    }

    setIsLoading(true);

    try {
      const response = await fetch('/api/auth/login', {
        method: 'POST',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ username, password, rememberMe }),
      });

      // 调试：检查响应
      console.log('[Login] 响应状态:', response.status);

      const result: LoginResponse = await response.json();
      console.log('[Login] 登录结果:', result);

      if (result.success && result.data) {
        // 从响应数据获取 sessionId
        let sessionId = result.data.sessionId;
        
        if (sessionId) {
          const maxAge = rememberMe ? 7 * 24 * 60 * 60 : 24 * 60 * 60;
          
          // 同时存储到 localStorage（用于前端逻辑）和 Cookie（用于 API 请求）
          localStorage.setItem('session_id', sessionId);
          localStorage.setItem('session_expires', String(Date.now() + maxAge * 1000));
          
          // 设置 Cookie（用于 API 请求自动携带）
          const isHttps = window.location.protocol === 'https:';
          const cookieExpiry = new Date(Date.now() + maxAge * 1000).toUTCString();
          // HTTPS 时需要 Secure 属性
          const secureAttr = isHttps ? 'Secure;' : '';
          document.cookie = `session_id=${sessionId}; path=/; expires=${cookieExpiry}; SameSite=Lax; ${secureAttr}`;
          
          console.log('[Login] 存储 sessionId:', sessionId.substring(0, 8) + '...');
        } else {
          console.error('[Login] 无法获取 sessionId！');
        }
        
        toast.success('登录成功', {
          description: `欢迎回来，${result.data.user?.username || '用户'}！`,
        });
        
        // 跳转到首页（使用多种方式确保跳转成功）
        setTimeout(() => {
          // 方式1: router.push
          router.push('/');
          router.refresh();
          
          // 方式2: 如果 500ms 后还在登录页，使用 window.location 强制跳转
          setTimeout(() => {
            if (window.location.pathname === '/login') {
              console.log('[Login] 使用 window.location 强制跳转');
              window.location.href = '/';
            }
          }, 500);
        }, 100);
      } else {
        toast.error('登录失败', {
          description: result.error || '用户名或密码错误',
        });
      }
    } catch (error) {
      console.error('登录失败:', error);
      toast.error('登录失败', {
        description: '网络错误，请重试',
      });
    } finally {
      setIsLoading(false);
    }
  };

  // 快速登录按钮（使用后端默认密码）
  const quickLogin = (type: 'admin' | 'user') => {
    if (type === 'admin') {
      setUsername('admin');
      setPassword('Admin@123');
    } else {
      setUsername('user');
      setPassword('User@123');
    }
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-emerald-50 via-white to-teal-50 flex items-center justify-center p-4">
      <Toaster position="top-center" richColors closeButton />
      
      <div className="w-full max-w-md">
        {/* Logo区域 */}
        <div className="text-center mb-8">
          <div className="w-20 h-20 mx-auto rounded-2xl bg-gradient-to-br from-emerald-500 to-teal-600 flex items-center justify-center shadow-xl shadow-emerald-500/30 mb-4">
            <BookOpen className="w-10 h-10 text-white" />
          </div>
          <h1 className="text-3xl font-bold bg-gradient-to-r from-emerald-600 to-teal-600 bg-clip-text text-transparent">
            数字知识库
          </h1>
          <p className="text-slate-500 mt-2">Digital Knowledge Base</p>
        </div>

        {/* 登录卡片 */}
        <div className="bg-white/80 backdrop-blur-xl rounded-2xl shadow-xl border border-slate-200/60 p-8">
          <form onSubmit={handleLogin} className="space-y-5">
            {/* 用户名输入 */}
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-2">
                用户名
              </label>
              <div className="relative">
                <div className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400">
                  <User className="w-5 h-5" />
                </div>
                <input
                  type="text"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  placeholder="请输入用户名"
                  className={cn(
                    'w-full pl-11 pr-4 py-3 rounded-xl border border-slate-200 bg-white/50',
                    'focus:outline-none focus:ring-2 focus:ring-violet-500/20 focus:border-violet-500',
                    'placeholder:text-slate-400 text-slate-700',
                    'transition-all duration-200'
                  )}
                />
              </div>
            </div>

            {/* 密码输入 */}
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-2">
                密码
              </label>
              <div className="relative">
                <div className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400">
                  <Lock className="w-5 h-5" />
                </div>
                <input
                  type={showPassword ? 'text' : 'password'}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="请输入密码"
                  className={cn(
                    'w-full pl-11 pr-11 py-3 rounded-xl border border-slate-200 bg-white/50',
                    'focus:outline-none focus:ring-2 focus:ring-violet-500/20 focus:border-violet-500',
                    'placeholder:text-slate-400 text-slate-700',
                    'transition-all duration-200'
                  )}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
                >
                  {showPassword ? (
                    <EyeOff className="w-5 h-5" />
                  ) : (
                    <Eye className="w-5 h-5" />
                  )}
                </button>
              </div>
            </div>

            {/* 记住我 */}
            <div className="flex items-center">
              <label className="flex items-center cursor-pointer">
                <input
                  type="checkbox"
                  checked={rememberMe}
                  onChange={(e) => setRememberMe(e.target.checked)}
                  className="w-4 h-4 text-violet-600 border-slate-300 rounded focus:ring-violet-500"
                />
                <span className="ml-2 text-sm text-slate-600">记住我（7天有效期）</span>
              </label>
            </div>

            {/* 登录按钮 */}
            <Button
              type="submit"
              disabled={isLoading}
              className={cn(
                'w-full py-3 text-white font-medium rounded-xl',
                'bg-gradient-to-r from-violet-500 to-purple-600',
                'hover:from-violet-600 hover:to-purple-700',
                'shadow-lg shadow-violet-500/25',
                'transition-all duration-200',
                'disabled:opacity-50 disabled:cursor-not-allowed'
              )}
            >
              {isLoading ? (
                <>
                  <Loader2 className="w-5 h-5 mr-2 animate-spin" />
                  登录中...
                </>
              ) : (
                '登 录'
              )}
            </Button>
          </form>

          {/* 快速登录 */}
          <div className="mt-6 pt-6 border-t border-slate-200">
            <p className="text-sm text-slate-500 text-center mb-3">快速登录</p>
            <div className="flex gap-3">
              <button
                type="button"
                onClick={() => quickLogin('admin')}
                className={cn(
                  'flex-1 py-2 px-4 rounded-lg text-sm font-medium',
                  'bg-violet-50 text-violet-600 border border-violet-200',
                  'hover:bg-violet-100 transition-colors'
                )}
              >
                管理员登录
              </button>
              <button
                type="button"
                onClick={() => quickLogin('user')}
                className={cn(
                  'flex-1 py-2 px-4 rounded-lg text-sm font-medium',
                  'bg-slate-50 text-slate-600 border border-slate-200',
                  'hover:bg-slate-100 transition-colors'
                )}
              >
                普通用户登录
              </button>
            </div>
          </div>

          {/* 测试账号说明 */}
          <div className="mt-6 p-4 bg-slate-50 rounded-xl">
            <p className="text-xs text-slate-500 text-center mb-2">测试账号（请使用后端默认密码）</p>
            <div className="grid grid-cols-2 gap-4 text-xs">
              <div className="text-center">
                <p className="text-slate-700 font-medium">管理员</p>
                <p className="text-slate-400">admin / Admin@123</p>
              </div>
              <div className="text-center">
                <p className="text-slate-700 font-medium">普通用户</p>
                <p className="text-slate-400">user / User@123</p>
              </div>
            </div>
          </div>
        </div>

        {/* 版权信息 */}
        <p className="text-center text-xs text-slate-400 mt-6">
          © 2024 图片管理系统. All rights reserved.
        </p>
      </div>
    </div>
  );
}
