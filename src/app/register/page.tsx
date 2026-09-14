'use client';

import React from 'react';
import { useRouter } from 'next/navigation';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import { User, Lock, Eye, EyeOff, Loader2, ArrowLeft, Mail, Building2 } from 'lucide-react';
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

type CompanyType = '宝娜斯集团' | null;

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
      <div className="min-h-screen bg-white flex items-center justify-center p-4">
        <Toaster position="top-center" richColors closeButton />

        <div className="w-full max-w-3xl">
          {/* Logo区域 */}
          <div className="text-center mb-10">
            <div className="w-20 h-20 mx-auto rounded-2xl bg-[#007AFF] flex items-center justify-center shadow-xl shadow-[0_2px_12px_rgba(0,0,0,0.04)] mb-4">
              <Building2 className="w-10 h-10 text-[#1C1C1E]" />
            </div>
            <h1 className="text-3xl font-bold bg-[#007AFF] bg-clip-text text-transparent">
              创建新账号
            </h1>
            <p className="text-[#8e8e93] mt-2">请选择您所属的公司</p>
          </div>

          {/* 公司卡片（统一宝娜斯集团） */}
          <div className="max-w-md mx-auto">
            <button
              onClick={() => setCompany('宝娜斯集团')}
              className={cn(
                'group relative w-full bg-white/80 backdrop-blur-xl rounded-2xl shadow-lg border border-[rgba(229,229,234,0.6)] p-8',
                'hover:shadow-xl hover:border-[#007aff] hover:-translate-y-1',
                'transition-all duration-300 text-left'
              )}
            >
              <div className="w-16 h-16 rounded-2xl bg-[#007AFF] flex items-center justify-center shadow-lg shadow-[0_2px_12px_rgba(0,0,0,0.04)] mb-5">
                <span className="text-2xl font-bold text-[#1C1C1E]">宝</span>
              </div>
              <h2 className="text-xl font-bold text-[#8e8e93] mb-2">宝娜斯集团</h2>
              <p className="text-sm text-[#8e8e93] mb-4">
                宝娜斯集团用户，管理专属知识库与文档
              </p>
              <div className="flex items-center text-[#007aff] text-sm font-medium group-hover:translate-x-1 transition-transform">
                选择宝娜斯集团
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
              className="text-sm text-[#8e8e93] hover:text-[#8e8e93] flex items-center justify-center gap-1 transition-colors mx-auto"
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
  const isBonasi = company === '宝娜斯集团';
  const ringColor = 'focus:ring-[rgba(0,122,255,0.2)] focus:border-[#007aff]';
  const bgGradient = 'bg-[#F2F2F7]';
  const iconBg = 'bg-[#007AFF] shadow-[0_2px_12px_rgba(0,0,0,0.04)]';

  return (
    <div className={cn('min-h-screen flex items-center justify-center p-4', bgGradient)}>
      <Toaster position="top-center" richColors closeButton />

      <div className="w-full max-w-md">
        {/* Logo区域 */}
        <div className="text-center mb-8">
          <div className={cn('w-20 h-20 mx-auto rounded-2xl flex items-center justify-center shadow-xl mb-4', iconBg)}>
            <span className="text-3xl font-bold text-white">{isBonasi ? '宝' : '盈'}</span>
          </div>
          <h1 className="text-3xl font-bold text-[#1C1C1E]">
            {company}
          </h1>
          <p className="text-[#8e8e93] mt-2">创建{company}专属账号</p>
        </div>

        {/* 注册卡片 */}
        <div className="bg-white/80 backdrop-blur-xl rounded-2xl shadow-xl border border-[rgba(229,229,234,0.6)] p-8">
          <form onSubmit={handleRegister} className="space-y-5">
            {/* 所属公司展示 */}
            <div className="flex items-center gap-2 p-3 rounded-xl bg-[#f2f2f7] border border-[#e5e5ea]">
              <Building2 className="w-5 h-5 text-[#8e8e93]" />
              <span className="text-sm text-[#8e8e93]">所属公司：</span>
              <span className={cn('text-sm font-medium', isBonasi ? 'text-[#007aff]' : 'text-[#007aff]')}>
                {company}
              </span>
            </div>

            {/* 用户名输入 */}
            <div>
              <label className="block text-sm font-medium text-[#8e8e93] mb-2">
                用户名
              </label>
              <div className="relative">
                <div className="absolute left-3 top-1/2 -translate-y-1/2 text-[#8e8e93]">
                  <User className="w-5 h-5" />
                </div>
                <input
                  type="text"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  placeholder="请输入用户名"
                  className={cn(
                    'w-full pl-11 pr-4 py-3 rounded-xl border border-[#e5e5ea] bg-[rgba(118,118,128,0.28)]',
                    'focus:outline-none focus:ring-2',
                    ringColor,
                    'placeholder:text-[#8e8e93] text-[#8e8e93]',
                    'transition-all duration-200'
                  )}
                />
              </div>
            </div>

            {/* 邮箱输入 */}
            <div>
              <label className="block text-sm font-medium text-[#8e8e93] mb-2">
                邮箱
              </label>
              <div className="relative">
                <div className="absolute left-3 top-1/2 -translate-y-1/2 text-[#8e8e93]">
                  <Mail className="w-5 h-5" />
                </div>
                <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="请输入邮箱"
                  className={cn(
                    'w-full pl-11 pr-4 py-3 rounded-xl border border-[#e5e5ea] bg-[rgba(118,118,128,0.28)]',
                    'focus:outline-none focus:ring-2',
                    ringColor,
                    'placeholder:text-[#8e8e93] text-[#8e8e93]',
                    'transition-all duration-200'
                  )}
                />
              </div>
            </div>

            {/* 密码输入 */}
            <div>
              <label className="block text-sm font-medium text-[#8e8e93] mb-2">
                密码
              </label>
              <div className="relative">
                <div className="absolute left-3 top-1/2 -translate-y-1/2 text-[#8e8e93]">
                  <Lock className="w-5 h-5" />
                </div>
                <input
                  type={showPassword ? 'text' : 'password'}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="请输入密码（至少6位）"
                  className={cn(
                    'w-full pl-11 pr-11 py-3 rounded-xl border border-[#e5e5ea] bg-[rgba(118,118,128,0.28)]',
                    'focus:outline-none focus:ring-2',
                    ringColor,
                    'placeholder:text-[#8e8e93] text-[#8e8e93]',
                    'transition-all duration-200'
                  )}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-[#8e8e93] hover:text-[#8e8e93]"
                >
                  {showPassword ? <EyeOff className="w-5 h-5" /> : <Eye className="w-5 h-5" />}
                </button>
              </div>
            </div>

            {/* 确认密码 */}
            <div>
              <label className="block text-sm font-medium text-[#8e8e93] mb-2">
                确认密码
              </label>
              <div className="relative">
                <div className="absolute left-3 top-1/2 -translate-y-1/2 text-[#8e8e93]">
                  <Lock className="w-5 h-5" />
                </div>
                <input
                  type={showPassword ? 'text' : 'password'}
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  placeholder="请再次输入密码"
                  className={cn(
                    'w-full pl-11 pr-4 py-3 rounded-xl border border-[#e5e5ea] bg-[rgba(118,118,128,0.28)]',
                    'focus:outline-none focus:ring-2',
                    ringColor,
                    'placeholder:text-[#8e8e93] text-[#8e8e93]',
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
                'bg-[#007AFF] hover:opacity-90',
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
              className="w-full py-2 text-sm text-[#8e8e93] hover:text-[#8e8e93] flex items-center justify-center gap-1 transition-colors"
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
            className="text-sm text-[#8e8e93] hover:text-[#8e8e93] transition-colors"
          >
            已有账号？返回登录
          </button>
        </div>
      </div>
    </div>
  );
}
