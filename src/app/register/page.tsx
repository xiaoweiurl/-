'use client';

import React from 'react';
import { useRouter } from 'next/navigation';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import { User, Lock, Eye, EyeOff, Loader2, Palette, ArrowLeft, Mail, Building2 } from 'lucide-react';
import { toast } from 'sonner';
import { Toaster } from '@/components/ui/sonner';

interface RegisterResponse {
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

type CompanyType = '宝娜斯' | '盈云' | null;

export default function RegisterPage() {
  const router = useRouter();
  const [company, setCompany] = React.useState<CompanyType>(null);
  const [username, setUsername] = React.useState('');
  const [email, setEmail] = React.useState('');
  const [password, setPassword] = React.useState('');
  const [confirmPassword, setConfirmPassword] = React.useState('');
  const [showPassword, setShowPassword] = React.useState(false);
  const [isLoading, setIsLoading] = React.useState(false);

  const handleRegister = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!company) {
      toast.error('请选择所属公司');
      return;
    }
    if (!username.trim()) {
      toast.error('请输入用户名');
      return;
    }
    if (!email.trim()) {
      toast.error('请输入邮箱');
      return;
    }
    if (password.length < 6) {
      toast.error('密码长度不能少于6位');
      return;
    }
    if (password !== confirmPassword) {
      toast.error('两次输入的密码不一致');
      return;
    }

    setIsLoading(true);

    try {
      const response = await fetch('/api/auth/register', {
        method: 'POST',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ username, email, password, company }),
      });

      const result: RegisterResponse = await response.json();

      if (result.success && result.data) {
        const sessionId = result.data.sessionId;

        if (sessionId) {
          const maxAge = 7 * 24 * 60 * 60;
          localStorage.setItem('session_id', sessionId);
          localStorage.setItem('session_expires', String(Date.now() + maxAge * 1000));
          const cookieExpiry = new Date(Date.now() + maxAge * 1000).toUTCString();
          document.cookie = `session_id=${sessionId}; path=/; expires=${cookieExpiry}; SameSite=Lax`;
        }

        if (result.data.user?.company) {
          localStorage.setItem('user_company', result.data.user.company);
        }

        toast.success('注册成功', {
          description: `欢迎，${result.data.user?.username || '用户'}！`,
        });

        router.replace('/');
        router.refresh();
      } else {
        toast.error('注册失败', {
          description: result.error || '注册失败，请重试',
        });
      }
    } catch (error) {
      console.error('注册失败:', error);
      toast.error('注册失败', {
        description: '网络错误，请重试',
      });
    } finally {
      setIsLoading(false);
    }
  };

  // 公司选择页面
  if (!company) {
    return (
      <div className="min-h-screen bg-gradient-to-br from-slate-50 via-white to-indigo-50 flex items-center justify-center p-4">
        <Toaster position="top-center" richColors closeButton />

        <div className="w-full max-w-3xl">
          {/* Logo区域 */}
          <div className="text-center mb-10">
            <div className="w-20 h-20 mx-auto rounded-2xl bg-gradient-to-br from-indigo-500 to-blue-600 flex items-center justify-center shadow-xl shadow-indigo-500/30 mb-4">
              <Building2 className="w-10 h-10 text-white" />
            </div>
            <h1 className="text-3xl font-bold bg-gradient-to-r from-indigo-600 to-blue-600 bg-clip-text text-transparent">
              创建新账号
            </h1>
            <p className="text-slate-500 mt-2">请选择您所属的公司</p>
          </div>

          {/* 两个公司卡片 */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            {/* 宝娜斯 */}
            <button
              onClick={() => setCompany('宝娜斯')}
              className={cn(
                'group relative bg-white/80 backdrop-blur-xl rounded-2xl shadow-lg border border-slate-200/60 p-8',
                'hover:shadow-xl hover:border-rose-300 hover:-translate-y-1',
                'transition-all duration-300 text-left'
              )}
            >
              <div className="w-16 h-16 rounded-2xl bg-gradient-to-br from-rose-500 to-pink-600 flex items-center justify-center shadow-lg shadow-rose-500/25 mb-5">
                <span className="text-2xl font-bold text-white">宝</span>
              </div>
              <h2 className="text-xl font-bold text-slate-800 mb-2">宝娜斯</h2>
              <p className="text-sm text-slate-500 mb-4">
                宝娜斯品牌用户，管理专属知识库与文档
              </p>
              <div className="flex items-center text-rose-600 text-sm font-medium group-hover:translate-x-1 transition-transform">
                选择宝娜斯
                <svg className="w-4 h-4 ml-1" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                </svg>
              </div>
            </button>

            {/* 盈云 */}
            <button
              onClick={() => setCompany('盈云')}
              className={cn(
                'group relative bg-white/80 backdrop-blur-xl rounded-2xl shadow-lg border border-slate-200/60 p-8',
                'hover:shadow-xl hover:border-indigo-300 hover:-translate-y-1',
                'transition-all duration-300 text-left'
              )}
            >
              <div className="w-16 h-16 rounded-2xl bg-gradient-to-br from-indigo-500 to-blue-600 flex items-center justify-center shadow-lg shadow-indigo-500/25 mb-5">
                <span className="text-2xl font-bold text-white">盈</span>
              </div>
              <h2 className="text-xl font-bold text-slate-800 mb-2">盈云</h2>
              <p className="text-sm text-slate-500 mb-4">
                盈云品牌用户，管理专属知识库与文档
              </p>
              <div className="flex items-center text-indigo-600 text-sm font-medium group-hover:translate-x-1 transition-transform">
                选择盈云
                <svg className="w-4 h-4 ml-1" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                </svg>
              </div>
            </button>
          </div>

          {/* 返回登录 */}
          <div className="mt-8 text-center">
            <button
              type="button"
              onClick={() => router.push('/login')}
              className="text-sm text-slate-400 hover:text-slate-600 flex items-center justify-center gap-1 transition-colors mx-auto"
            >
              <ArrowLeft className="w-4 h-4" />
              已有账号？返回登录
            </button>
          </div>
        </div>
      </div>
    );
  }

  // 注册表单页面
  const isBonasi = company === '宝娜斯';
  const gradientFrom = isBonasi ? 'from-rose-500' : 'from-indigo-500';
  const gradientTo = isBonasi ? 'to-pink-600' : 'to-blue-600';
  const ringColor = isBonasi ? 'focus:ring-rose-500/20 focus:border-rose-500' : 'focus:ring-indigo-500/20 focus:border-indigo-500';
  const btnFrom = isBonasi ? 'from-rose-500' : 'from-indigo-500';
  const btnTo = isBonasi ? 'to-pink-600' : 'to-blue-600';
  const shadowColor = isBonasi ? 'shadow-rose-500/25' : 'shadow-indigo-500/25';
  const bgGradient = isBonasi
    ? 'bg-gradient-to-br from-rose-50 via-white to-pink-50'
    : 'bg-gradient-to-br from-indigo-50 via-white to-blue-50';
  const iconBg = isBonasi
    ? 'bg-gradient-to-br from-rose-500 to-pink-600 shadow-rose-500/30'
    : 'bg-gradient-to-br from-indigo-500 to-blue-600 shadow-indigo-500/30';

  return (
    <div className={cn('min-h-screen flex items-center justify-center p-4', bgGradient)}>
      <Toaster position="top-center" richColors closeButton />

      <div className="w-full max-w-md">
        {/* Logo区域 */}
        <div className="text-center mb-8">
          <div className={cn('w-20 h-20 mx-auto rounded-2xl flex items-center justify-center shadow-xl mb-4', iconBg)}>
            <span className="text-3xl font-bold text-white">{isBonasi ? '宝' : '盈'}</span>
          </div>
          <h1 className={cn('text-3xl font-bold bg-gradient-to-r bg-clip-text text-transparent', gradientFrom, gradientTo)}>
            {company}
          </h1>
          <p className="text-slate-500 mt-2">创建{company}专属账号</p>
        </div>

        {/* 注册卡片 */}
        <div className="bg-white/80 backdrop-blur-xl rounded-2xl shadow-xl border border-slate-200/60 p-8">
          <form onSubmit={handleRegister} className="space-y-5">
            {/* 所属公司展示 */}
            <div className="flex items-center gap-2 p-3 rounded-xl bg-slate-50 border border-slate-200">
              <Building2 className="w-5 h-5 text-slate-400" />
              <span className="text-sm text-slate-600">所属公司：</span>
              <span className={cn('text-sm font-medium', isBonasi ? 'text-rose-600' : 'text-indigo-600')}>
                {company}
              </span>
            </div>

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

            {/* 邮箱输入 */}
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-2">
                邮箱
              </label>
              <div className="relative">
                <div className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400">
                  <Mail className="w-5 h-5" />
                </div>
                <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="请输入邮箱"
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
                  placeholder="请输入密码（至少6位）"
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

            {/* 确认密码 */}
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-2">
                确认密码
              </label>
              <div className="relative">
                <div className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400">
                  <Lock className="w-5 h-5" />
                </div>
                <input
                  type={showPassword ? 'text' : 'password'}
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  placeholder="请再次输入密码"
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

            {/* 注册按钮 */}
            <Button
              type="submit"
              disabled={isLoading}
              className={cn(
                'w-full py-3 text-white font-medium rounded-xl',
                'bg-gradient-to-r',
                btnFrom, btnTo,
                'shadow-lg',
                shadowColor,
                'transition-all duration-200',
                'disabled:opacity-50 disabled:cursor-not-allowed'
              )}
            >
              {isLoading ? (
                <>
                  <Loader2 className="w-5 h-5 mr-2 animate-spin" />
                  注册中...
                </>
              ) : (
                '注 册'
              )}
            </Button>
          </form>

          {/* 返回公司选择 */}
          <div className="mt-4">
            <button
              type="button"
              onClick={() => setCompany(null)}
              className="w-full py-2 text-sm text-slate-400 hover:text-slate-600 flex items-center justify-center gap-1 transition-colors"
            >
              <ArrowLeft className="w-4 h-4" />
              重新选择公司
            </button>
          </div>
        </div>

        {/* 返回登录 */}
        <div className="mt-4 text-center">
          <button
            type="button"
            onClick={() => router.push('/login')}
            className="text-sm text-slate-400 hover:text-slate-600 transition-colors"
          >
            已有账号？返回登录
          </button>
        </div>
      </div>
    </div>
  );
}
