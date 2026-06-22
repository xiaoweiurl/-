'use client';

import React from 'react';
import { useRouter } from 'next/navigation';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import { User, Lock, Eye, EyeOff, Loader2, Palette, Factory, ArrowLeft, Megaphone, Scissors, Cloud, ChevronRight, Sparkles } from 'lucide-react';
import { toast } from 'sonner';
import { Toaster } from '@/components/ui/sonner';
import { BRANDS, COMPANY_OPTIONS, type BrandKey } from '@/lib/brand';

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

type Step = 'company' | 'portal' | 'login';
type PortalType = 'designer' | 'factory' | 'marketing' | null;

export default function LoginPage() {
  const router = useRouter();
  const [step, setStep] = React.useState<Step>('company');
  const [selectedBrand, setSelectedBrand] = React.useState<BrandKey>('yingyun');
  const [portal, setPortal] = React.useState<PortalType>(null);
  const [username, setUsername] = React.useState('');
  const [password, setPassword] = React.useState('');
  const [showPassword, setShowPassword] = React.useState(false);
  const [rememberMe, setRememberMe] = React.useState(false);
  const [isLoading, setIsLoading] = React.useState(false);

  const brand = BRANDS[selectedBrand];

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!username.trim() || !password.trim()) {
      toast.error('请输入用户名和密码');
      return;
    }

    setIsLoading(true);

    try {
      // 将选择的公司名作为 company 参数传给后端
      const companyName = brand.name === '宝娜斯' ? '宝娜斯' : '盈云';

      const response = await fetch('/api/auth/login', {
        method: 'POST',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ username, password, rememberMe, company: companyName }),
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

        // 存储公司标签和用户ID — 使用登录时选择的公司
        localStorage.setItem('user_company', companyName);
        localStorage.setItem('selected_brand', selectedBrand);
        if (result.data.user?.id) {
          localStorage.setItem('user_id', result.data.user.id);
        }

        toast.success('登录成功', {
          description: `欢迎回来，${result.data.user?.username || '用户'}！`,
        });

        if (portal === 'factory') {
          router.replace('/supply-chain');
        } else if (portal === 'marketing') {
          router.replace('/marketing');
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

  // ========== Step 1: 选择公司 ==========
  if (step === 'company') {
    return (
      <div className="min-h-screen bg-gradient-to-br from-slate-50 via-white to-slate-100 flex items-center justify-center p-4">
        <Toaster position="top-center" richColors closeButton />

        <div className="w-full max-w-2xl">
          <div className="text-center mb-10">
            <div className="w-20 h-20 mx-auto rounded-2xl bg-gradient-to-br from-slate-600 to-slate-800 flex items-center justify-center shadow-xl shadow-slate-500/30 mb-4">
              <Sparkles className="w-10 h-10 text-white" />
            </div>
            <h1 className="text-3xl font-bold text-slate-800">
              产品智能中台
            </h1>
            <p className="text-slate-500 mt-2">请选择您所属的公司</p>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            {COMPANY_OPTIONS.map((company) => {
              const isBonasi = company.key === 'bonasi';
              const Icon = isBonasi ? Scissors : Cloud;
              const gradientFrom = isBonasi ? 'from-rose-500' : 'from-violet-500';
              const gradientTo = isBonasi ? 'to-pink-600' : 'to-purple-600';
              const shadowColor = isBonasi ? 'shadow-rose-500/25' : 'shadow-violet-500/25';
              const hoverBorder = isBonasi ? 'hover:border-rose-300' : 'hover:border-violet-300';
              const textColor = isBonasi ? 'text-rose-600' : 'text-violet-600';

              return (
                <button
                  key={company.key}
                  onClick={() => {
                    setSelectedBrand(company.key);
                    setStep('portal');
                  }}
                  className={cn(
                    'group relative bg-white/80 backdrop-blur-xl rounded-2xl shadow-lg border border-slate-200/60 p-8',
                    'hover:shadow-xl hover:-translate-y-1',
                    hoverBorder,
                    'transition-all duration-300 text-left'
                  )}
                >
                  <div className={cn(
                    'w-16 h-16 rounded-2xl bg-gradient-to-br flex items-center justify-center shadow-lg mb-5',
                    gradientFrom, gradientTo, shadowColor
                  )}>
                    <Icon className="w-8 h-8 text-white" />
                  </div>
                  <h2 className="text-xl font-bold text-slate-800 mb-1">{company.fullName}</h2>
                  <p className="text-sm text-slate-500 mb-4">{company.description}</p>
                  <div className={cn('flex items-center text-sm font-medium group-hover:translate-x-1 transition-transform', textColor)}>
                    进入 {company.name}
                    <ChevronRight className="w-4 h-4 ml-1" />
                  </div>
                </button>
              );
            })}
          </div>

          <p className="text-center text-xs text-slate-400 mt-8">
            © 2024 产品智能中台. All rights reserved.
          </p>
        </div>
      </div>
    );
  }

  // ========== Step 2: 选择入口 ==========
  if (step === 'portal') {
    const Icon = selectedBrand === 'bonasi' ? Scissors : Cloud;
    const companyName = brand.name;

    const portalCards = [
      {
        key: 'designer' as PortalType,
        icon: Palette,
        title: '设计师入口',
        desc: '知识库管理、图片上传、AI识别、文档中心',
        gradientFrom: brand.primaryFrom,
        gradientTo: brand.primaryTo,
        hoverBorder: brand.loginCardHoverBorder,
        textColor: brand.primarySolid,
      },
      {
        key: 'factory' as PortalType,
        icon: Factory,
        title: '工厂/供应链入口',
        desc: '产品报价、原料管理、生产计划、辅料采购',
        gradientFrom: 'from-amber-500',
        gradientTo: 'to-orange-600',
        hoverBorder: 'hover:border-amber-300',
        textColor: 'text-amber-600',
      },
      {
        key: 'marketing' as PortalType,
        icon: Megaphone,
        title: '市场营销AI入口',
        desc: '无缝针织行业营销策略、市场分析、文案生成',
        gradientFrom: 'from-emerald-500',
        gradientTo: 'to-teal-600',
        hoverBorder: 'hover:border-emerald-300',
        textColor: 'text-emerald-600',
      },
    ];

    return (
      <div className={cn('min-h-screen flex items-center justify-center p-4', brand.loginBg)}>
        <Toaster position="top-center" richColors closeButton />

        <div className="w-full max-w-3xl">
          <div className="text-center mb-8">
            <div className={cn(
              'w-20 h-20 mx-auto rounded-2xl flex items-center justify-center shadow-xl mb-4',
              'bg-gradient-to-br', brand.primaryFrom, brand.primaryTo, brand.buttonShadow
            )}>
              <Icon className="w-10 h-10 text-white" />
            </div>
            <h1 className={cn('text-3xl font-bold bg-gradient-to-r bg-clip-text text-transparent', brand.primaryFrom, brand.primaryTo)}>
              {companyName}产品智能中台
            </h1>
            <p className="text-slate-500 mt-2">请选择登录入口</p>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
            {portalCards.map((card) => {
              const CardIcon = card.icon;
              return (
                <button
                  key={card.key}
                  onClick={() => {
                    setPortal(card.key);
                    setStep('login');
                  }}
                  className={cn(
                    'group relative bg-white/80 backdrop-blur-xl rounded-2xl shadow-lg border border-slate-200/60 p-6',
                    'hover:shadow-xl hover:-translate-y-1',
                    card.hoverBorder,
                    'transition-all duration-300 text-left'
                  )}
                >
                  <div className={cn(
                    'w-14 h-14 rounded-2xl bg-gradient-to-br flex items-center justify-center shadow-lg mb-4',
                    card.gradientFrom, card.gradientTo
                  )}>
                    <CardIcon className="w-7 h-7 text-white" />
                  </div>
                  <h2 className="text-lg font-bold text-slate-800 mb-1">{card.title}</h2>
                  <p className="text-sm text-slate-500 mb-3">{card.desc}</p>
                  <div className={cn('flex items-center text-sm font-medium group-hover:translate-x-1 transition-transform', card.textColor)}>
                    进入
                    <ChevronRight className="w-4 h-4 ml-1" />
                  </div>
                </button>
              );
            })}
          </div>

          <div className="flex items-center justify-center mt-8">
            <button
              onClick={() => setStep('company')}
              className="flex items-center gap-1 text-sm text-slate-400 hover:text-slate-600 transition-colors"
            >
              <ArrowLeft className="w-4 h-4" />
              返回选择公司
            </button>
          </div>

          <p className="text-center text-xs text-slate-400 mt-4">
            © 2024 {companyName}产品智能中台. All rights reserved.
          </p>
        </div>
      </div>
    );
  }

  // ========== Step 3: 登录表单 ==========
  const isFactory = portal === 'factory';
  const isMarketing = portal === 'marketing';
  const Icon = selectedBrand === 'bonasi' ? Scissors : Cloud;

  // 供应链/营销入口保留原色，设计师入口用品牌色
  const gradientFrom = isFactory ? 'from-amber-500' : isMarketing ? 'from-emerald-500' : brand.primaryFrom;
  const gradientTo = isFactory ? 'to-orange-600' : isMarketing ? 'to-teal-600' : brand.primaryTo;
  const ringColor = isFactory ? 'focus:ring-amber-500/20 focus:border-amber-500' : isMarketing ? 'focus:ring-emerald-500/20 focus:border-emerald-500' : `focus:ring-purple-500/20 focus:border-purple-500`;
  const btnFrom = isFactory ? 'from-amber-500' : isMarketing ? 'from-emerald-500' : brand.primaryFrom;
  const btnTo = isFactory ? 'to-orange-600' : isMarketing ? 'to-teal-600' : brand.primaryTo;
  const shadowColor = isFactory ? 'shadow-amber-500/25' : isMarketing ? 'shadow-emerald-500/25' : brand.buttonShadow;
  const bgGradient = isFactory
    ? 'bg-gradient-to-br from-amber-50 via-white to-orange-50'
    : isMarketing
    ? 'bg-gradient-to-br from-emerald-50 via-white to-teal-50'
    : brand.loginBg;

  const title = isFactory ? '供应链管理' : isMarketing ? '市场营销助手' : `${brand.name}产品智能中台`;
  const subtitle = isFactory ? 'Supply Chain Management' : isMarketing ? 'Marketing AI Assistant' : brand.slogan;

  const quickBtnBg = isFactory
    ? 'bg-amber-50 text-amber-600 border-amber-200 hover:bg-amber-100'
    : isMarketing
    ? 'bg-emerald-50 text-emerald-600 border-emerald-200 hover:bg-emerald-100'
    : selectedBrand === 'bonasi'
    ? 'bg-rose-50 text-rose-600 border-rose-200 hover:bg-rose-100'
    : 'bg-violet-50 text-violet-600 border-violet-200 hover:bg-violet-100';

  return (
    <div className={cn('min-h-screen flex items-center justify-center p-4', bgGradient)}>
      <Toaster position="top-center" richColors closeButton />

      <div className="w-full max-w-md">
        <div className="text-center mb-8">
          <div className={cn('w-20 h-20 mx-auto rounded-2xl flex items-center justify-center shadow-xl mb-4', 'bg-gradient-to-br', brand.primaryFrom, brand.primaryTo, brand.buttonShadow)}>
            <Icon className="w-10 h-10 text-white" />
          </div>
          <h1 className={cn('text-3xl font-bold bg-gradient-to-r bg-clip-text text-transparent', gradientFrom, gradientTo)}>
            {title}
          </h1>
          <p className="text-slate-500 mt-2">{subtitle}</p>
        </div>

        <div className="bg-white/80 backdrop-blur-xl rounded-2xl shadow-xl border border-slate-200/60 p-8">
          <form onSubmit={handleLogin} className="space-y-5">
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-2">用户名</label>
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

            <div>
              <label className="block text-sm font-medium text-slate-700 mb-2">密码</label>
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
                  登录中...
                </>
              ) : (
                '登 录'
              )}
            </Button>
          </form>

          <div className="mt-6 pt-6 border-t border-slate-200">
            <p className="text-sm text-slate-500 text-center mb-3">快速登录</p>
            <div className="flex gap-3">
              <button
                type="button"
                onClick={() => quickLogin('admin')}
                className={cn('flex-1 py-2 px-4 rounded-lg text-sm font-medium border transition-colors', quickBtnBg)}
              >
                管理员登录
              </button>
              <button
                type="button"
                onClick={() => quickLogin('user')}
                className="flex-1 py-2 px-4 rounded-lg text-sm font-medium border bg-slate-50 text-slate-600 border-slate-200 hover:bg-slate-100 transition-colors"
              >
                普通用户登录
              </button>
            </div>
          </div>

          <div className="mt-3 text-center">
            <button
              type="button"
              onClick={() => router.push('/register')}
              className="text-sm text-slate-500 hover:text-indigo-600 transition-colors"
            >
              没有账号？立即注册
            </button>
          </div>

          <div className="mt-4">
            <button
              type="button"
              onClick={() => setStep('portal')}
              className="w-full py-2 text-sm text-slate-400 hover:text-slate-600 flex items-center justify-center gap-1 transition-colors"
            >
              <ArrowLeft className="w-4 h-4" />
              返回选择入口
            </button>
          </div>
        </div>

        <p className="text-center text-xs text-slate-400 mt-6">
          © 2024 {brand.name}产品智能中台. All rights reserved.
        </p>
      </div>
    </div>
  );
}
