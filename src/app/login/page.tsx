'use client';

import React from 'react';
import { useRouter } from 'next/navigation';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import { User, Lock, Eye, EyeOff, Loader2, Palette, Factory, ArrowLeft } from 'lucide-react';
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
      company?: string;
    };
  };
}

type PortalType = 'designer' | 'factory' | null;

export default function LoginPage() {
  const router = useRouter();
  const [portal, setPortal] = React.useState<PortalType>(null);
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

      const result: LoginResponse = await response.json();

      if (result.success && result.data) {
        let sessionId = result.data.sessionId;

        if (sessionId) {
          const maxAge = rememberMe ? 7 * 24 * 60 * 60 : 24 * 60 * 60;
          localStorage.setItem('session_id', sessionId);
          localStorage.setItem('session_expires', String(Date.now() + maxAge * 1000));
          const cookieExpiry = new Date(Date.now() + maxAge * 1000).toUTCString();
          document.cookie = `session_id=${sessionId}; path=/; expires=${cookieExpiry}; SameSite=Lax`;
        }

        // 根据入口类型存储并跳转
        localStorage.setItem('portal_type', portal || 'designer');

        // 存储公司标签
        if (result.data.user?.company) {
          localStorage.setItem('user_company', result.data.user.company);
        }

        toast.success('登录成功', {
          description: `欢迎回来，${result.data.user?.username || '用户'}！`,
        });

        if (portal === 'factory') {
          router.replace('/supply-chain');
        } else {
          router.replace('/');
        }
        router.refresh();
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

  const quickLogin = (type: 'admin' | 'user') => {
    if (type === 'admin') {
      setUsername('admin');
      setPassword('Admin@123');
    } else {
      setUsername('user');
      setPassword('User@123');
    }
  };

  // 入口选择页面
  if (!portal) {
    return (
      <div className="min-h-screen bg-gradient-to-br from-slate-50 via-white to-violet-50 flex items-center justify-center p-4">
        <Toaster position="top-center" richColors closeButton />

        <div className="w-full max-w-3xl">
          {/* Logo区域 */}
          <div className="text-center mb-10">
            <div className="w-20 h-20 mx-auto rounded-2xl bg-gradient-to-br from-violet-500 to-purple-600 flex items-center justify-center shadow-xl shadow-violet-500/30 mb-4">
              <Palette className="w-10 h-10 text-white" />
            </div>
            <h1 className="text-3xl font-bold bg-gradient-to-r from-violet-600 to-purple-600 bg-clip-text text-transparent">
              盈云产品智能中台
            </h1>
            <p className="text-slate-500 mt-2">请选择登录入口</p>
          </div>

          {/* 两个入口卡片 */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            {/* 设计师入口 */}
            <button
              onClick={() => setPortal('designer')}
              className={cn(
                'group relative bg-white/80 backdrop-blur-xl rounded-2xl shadow-lg border border-slate-200/60 p-8',
                'hover:shadow-xl hover:border-violet-300 hover:-translate-y-1',
                'transition-all duration-300 text-left'
              )}
            >
              <div className="w-16 h-16 rounded-2xl bg-gradient-to-br from-violet-500 to-purple-600 flex items-center justify-center shadow-lg shadow-violet-500/25 mb-5">
                <Palette className="w-8 h-8 text-white" />
              </div>
              <h2 className="text-xl font-bold text-slate-800 mb-2">设计师入口</h2>
              <p className="text-sm text-slate-500 mb-4">
                知识库管理、图片上传、AI识别、文档中心
              </p>
              <div className="flex items-center text-violet-600 text-sm font-medium group-hover:translate-x-1 transition-transform">
                进入设计师工作台
                <svg className="w-4 h-4 ml-1" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                </svg>
              </div>
            </button>

            {/* 工厂/供应链入口 */}
            <button
              onClick={() => setPortal('factory')}
              className={cn(
                'group relative bg-white/80 backdrop-blur-xl rounded-2xl shadow-lg border border-slate-200/60 p-8',
                'hover:shadow-xl hover:border-amber-300 hover:-translate-y-1',
                'transition-all duration-300 text-left'
              )}
            >
              <div className="w-16 h-16 rounded-2xl bg-gradient-to-br from-amber-500 to-orange-600 flex items-center justify-center shadow-lg shadow-amber-500/25 mb-5">
                <Factory className="w-8 h-8 text-white" />
              </div>
              <h2 className="text-xl font-bold text-slate-800 mb-2">工厂/供应链入口</h2>
              <p className="text-sm text-slate-500 mb-4">
                产品报价、原料管理、生产计划、辅料采购
              </p>
              <div className="flex items-center text-amber-600 text-sm font-medium group-hover:translate-x-1 transition-transform">
                进入供应链管理
                <svg className="w-4 h-4 ml-1" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                </svg>
              </div>
            </button>
          </div>

          {/* 版权信息 */}
          <p className="text-center text-xs text-slate-400 mt-8">
            © 2024 盈云产品智能中台. All rights reserved.
          </p>
        </div>
      </div>
    );
  }

  // 登录表单页面
  const isFactory = portal === 'factory';
  const gradientFrom = isFactory ? 'from-amber-500' : 'from-violet-500';
  const gradientTo = isFactory ? 'to-orange-600' : 'to-purple-600';
  const ringColor = isFactory ? 'focus:ring-amber-500/20 focus:border-amber-500' : 'focus:ring-violet-500/20 focus:border-violet-500';
  const btnFrom = isFactory ? 'from-amber-500' : 'from-violet-500';
  const btnTo = isFactory ? 'to-orange-600' : 'to-purple-600';
  const btnHoverFrom = isFactory ? 'from-amber-600' : 'from-violet-600';
  const btnHoverTo = isFactory ? 'to-orange-700' : 'to-purple-700';
  const shadowColor = isFactory ? 'shadow-amber-500/25' : 'shadow-violet-500/25';
  const bgGradient = isFactory
    ? 'bg-gradient-to-br from-amber-50 via-white to-orange-50'
    : 'bg-gradient-to-br from-emerald-50 via-white to-teal-50';
  const iconBg = isFactory
    ? 'bg-gradient-to-br from-amber-500 to-orange-600 shadow-amber-500/30'
    : 'bg-gradient-to-br from-emerald-500 to-teal-600 shadow-emerald-500/30';
  const Icon = isFactory ? Factory : Palette;
  const title = isFactory ? '供应链管理' : '盈云产品智能中台';
  const subtitle = isFactory ? 'Supply Chain Management' : 'Digital Knowledge Base';
  const quickBtnBg = isFactory ? 'bg-amber-50 text-amber-600 border-amber-200 hover:bg-amber-100' : 'bg-violet-50 text-violet-600 border-violet-200 hover:bg-violet-100';
  const quickBtnBgAlt = isFactory ? 'bg-slate-50 text-slate-600 border-slate-200 hover:bg-slate-100' : 'bg-slate-50 text-slate-600 border-slate-200 hover:bg-slate-100';

  return (
    <div className={cn('min-h-screen flex items-center justify-center p-4', bgGradient)}>
      <Toaster position="top-center" richColors closeButton />

      <div className="w-full max-w-md">
        {/* Logo区域 */}
        <div className="text-center mb-8">
          <div className={cn('w-20 h-20 mx-auto rounded-2xl flex items-center justify-center shadow-xl mb-4', iconBg)}>
            <Icon className="w-10 h-10 text-white" />
          </div>
          <h1 className={cn('text-3xl font-bold bg-gradient-to-r bg-clip-text text-transparent', gradientFrom, gradientTo)}>
            {title}
          </h1>
          <p className="text-slate-500 mt-2">{subtitle}</p>
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
                    'focus:outline-none focus:ring-2',
                    ringColor,
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
                    'focus:outline-none focus:ring-2',
                    ringColor,
                    'placeholder:text-slate-400 text-slate-700',
                    'transition-all duration-200'
                  )}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
                >
                  {showPassword ? <EyeOff className="w-5 h-5" /> : <Eye className="w-5 h-5" />}
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
                'bg-gradient-to-r',
                btnFrom, btnTo,
                'hover:' + btnHoverFrom.replace('from-', 'from-') + ' hover:' + btnHoverTo.replace('to-', 'to-'),
                'shadow-lg',
                shadowColor,
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
                  'flex-1 py-2 px-4 rounded-lg text-sm font-medium border transition-colors',
                  quickBtnBg
                )}
              >
                管理员登录
              </button>
              <button
                type="button"
                onClick={() => quickLogin('user')}
                className={cn(
                  'flex-1 py-2 px-4 rounded-lg text-sm font-medium border transition-colors',
                  quickBtnBgAlt
                )}
              >
                普通用户登录
              </button>
            </div>
          </div>

          {/* 注册链接 */}
          <div className="mt-3 text-center">
            <button
              type="button"
              onClick={() => router.push('/register')}
              className="text-sm text-slate-500 hover:text-indigo-600 transition-colors"
            >
              没有账号？立即注册
            </button>
          </div>

          {/* 返回入口选择 */}
          <div className="mt-4">
            <button
              type="button"
              onClick={() => setPortal(null)}
              className="w-full py-2 text-sm text-slate-400 hover:text-slate-600 flex items-center justify-center gap-1 transition-colors"
            >
              <ArrowLeft className="w-4 h-4" />
              返回选择入口
            </button>
          </div>
        </div>

        {/* 版权信息 */}
        <p className="text-center text-xs text-slate-400 mt-6">
          © 2024 盈云产品智能中台. All rights reserved.
        </p>
      </div>
    </div>
  );
}
