'use client';

import React from 'react';
import { useRouter } from 'next/navigation';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import { User, Lock, Eye, EyeOff, Loader2, Palette, Factory, ArrowLeft, Megaphone, Scissors, Cloud, ChevronRight, Sparkles, Building2, CheckCircle2 } from 'lucide-react';
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

type Step = 'login' | 'company' | 'portal';
type PortalType = 'designer' | 'factory' | 'marketing' | null;

export default function LoginPage() {
  const router = useRouter();
  const [step, setStep] = React.useState<Step>('login');
  const [selectedBrand, setSelectedBrand] = React.useState<BrandKey>('yingyun');
  const [portal, setPortal] = React.useState<PortalType>(null);
  const [username, setUsername] = React.useState('');
  const [password, setPassword] = React.useState('');
  const [showPassword, setShowPassword] = React.useState(false);
  const [rememberMe, setRememberMe] = React.useState(false);
  const [isLoading, setIsLoading] = React.useState(false);
  const [loggedInUser, setLoggedInUser] = React.useState<LoginResponse['data'] | null>(null);

  const brand = BRANDS[selectedBrand];

  // 登录验证（第一步：输入账号密码）
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
        const sessionId = result.data.sessionId;

        if (sessionId) {
          const maxAge = rememberMe ? 7 * 24 * 60 * 60 : 24 * 60 * 60;
          localStorage.setItem('session_id', sessionId);
          localStorage.setItem('session_expires', String(Date.now() + maxAge * 1000));
          const cookieExpiry = new Date(Date.now() + maxAge * 1000).toUTCString();
          document.cookie = `session_id=${sessionId}; path=/; expires=${cookieExpiry}; SameSite=Lax`;
        }

        if (result.data.user?.id) {
          localStorage.setItem('user_id', result.data.user.id);
        }

        setLoggedInUser(result.data);

        // 判断用户是否已绑定公司
        const userCompany = result.data.user?.company;
        if (userCompany && userCompany.trim() !== '') {
          // 已绑定公司 → 自动设置品牌，跳到选择入口
          const brandKey = userCompany === '宝娜斯' ? 'bonasi' : 'yingyun';
          setSelectedBrand(brandKey);
          localStorage.setItem('selected_brand', brandKey);
          localStorage.setItem('user_company', userCompany);
          setStep('portal');
          toast.success('登录成功', {
            description: `欢迎回来，${result.data.user?.username || '用户'}！`,
          });
        } else {
          // 未绑定公司 → 需要选择公司
          setStep('company');
          toast.success('验证通过', {
            description: '请选择您所属的公司',
          });
        }
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

  // 选择公司后绑定公司（首次选择）
  const handleSelectCompany = async (companyKey: BrandKey) => {
    setSelectedBrand(companyKey);
    const companyName = companyKey === 'bonasi' ? '宝娜斯' : '盈云';

    // 调用后端绑定公司
    try {
      const userId = loggedInUser?.user?.id;
      if (userId) {
        await fetch('/api/auth/bind-company', {
          method: 'POST',
          credentials: 'include',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ userId, company: companyName }),
        });
      }
    } catch {
      // 绑定失败不影响流程，降级使用 localStorage
    }

    localStorage.setItem('selected_brand', companyKey);
    localStorage.setItem('user_company', companyName);
    setStep('portal');
  };

  // 选择入口后跳转
  const handleSelectPortal = (portalType: PortalType) => {
    setPortal(portalType);
    localStorage.setItem('portal_type', portalType || 'designer');

    toast.success('欢迎进入', {
      description: `正在跳转...`,
    });

    if (portalType === 'factory') {
      router.replace('/supply-chain');
    } else if (portalType === 'marketing') {
      router.replace('/marketing');
    } else {
      router.replace('/');
    }
    router.refresh();
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

  // ========== Step 1: 登录表单（输入账号密码） ==========
  if (step === 'login') {
    return (
      <div className="min-h-screen bg-gradient-to-br from-slate-50 via-white to-slate-100 flex items-center justify-center p-4">
        <Toaster position="top-center" richColors closeButton />

        <div className="w-full max-w-md">
          <div className="text-center mb-8">
            <div className="w-20 h-20 mx-auto rounded-2xl bg-gradient-to-br from-slate-600 to-slate-800 flex items-center justify-center shadow-xl shadow-slate-500/30 mb-4">
              <Sparkles className="w-10 h-10 text-white" />
            </div>
            <h1 className="text-3xl font-bold text-slate-800">
              产品智能中台
            </h1>
            <p className="text-slate-500 mt-2">请登录您的账号</p>
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
                      'focus:outline-none focus:ring-2 focus:ring-slate-500/20 focus:border-slate-500',
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
                      'focus:outline-none focus:ring-2 focus:ring-slate-500/20 focus:border-slate-500',
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
                    className="w-4 h-4 text-slate-600 border-slate-300 rounded focus:ring-slate-500"
                  />
                  <span className="ml-2 text-sm text-slate-600">记住我（7天有效期）</span>
                </label>
              </div>

              <Button
                type="submit"
                disabled={isLoading}
                className={cn(
                  'w-full py-3 text-white font-medium rounded-xl',
                  'bg-gradient-to-r from-slate-600 to-slate-800',
                  'shadow-lg shadow-slate-500/25',
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
                  className="flex-1 py-2 px-4 rounded-lg text-sm font-medium border bg-slate-50 text-slate-600 border-slate-200 hover:bg-slate-100 transition-colors"
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
          </div>

          <p className="text-center text-xs text-slate-400 mt-6">
            © 2024 产品智能中台. All rights reserved.
          </p>
        </div>
      </div>
    );
  }

  // ========== Step 2: 选择公司（仅首次未绑定时显示） ==========
  if (step === 'company') {
    return (
      <div className="min-h-screen bg-gradient-to-br from-slate-50 via-white to-slate-100 flex items-center justify-center p-4">
        <Toaster position="top-center" richColors closeButton />

        <div className="w-full max-w-2xl">
          <div className="text-center mb-10">
            <div className="w-20 h-20 mx-auto rounded-2xl bg-gradient-to-br from-slate-600 to-slate-800 flex items-center justify-center shadow-xl shadow-slate-500/30 mb-4">
              <Building2 className="w-10 h-10 text-white" />
            </div>
            <h1 className="text-3xl font-bold text-slate-800">
              选择您所属的公司
            </h1>
            <p className="text-slate-500 mt-2">此选择将绑定到您的账号，后续不可更改</p>
            {loggedInUser?.user?.username && (
              <p className="text-sm text-slate-400 mt-1">
                当前账号：<span className="font-medium text-slate-600">{loggedInUser.user.username}</span>
              </p>
            )}
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
                  onClick={() => handleSelectCompany(company.key)}
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
                  <div className="flex items-center justify-between">
                    <div className={cn('flex items-center text-sm font-medium group-hover:translate-x-1 transition-transform', textColor)}>
                      选择 {company.name}
                      <ChevronRight className="w-4 h-4 ml-1" />
                    </div>
                    <span className="text-xs text-amber-500 bg-amber-50 px-2 py-1 rounded-full">
                      绑定后不可更改
                    </span>
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

  // ========== Step 3: 选择入口 ==========
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

          {/* 显示已绑定公司 */}
          <div className="inline-flex items-center gap-1.5 mt-3 px-3 py-1.5 bg-green-50 border border-green-200 rounded-full">
            <CheckCircle2 className="w-4 h-4 text-green-500" />
            <span className="text-sm text-green-700 font-medium">已绑定：{companyName}</span>
          </div>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
          {portalCards.map((card) => {
            const CardIcon = card.icon;
            return (
              <button
                key={card.key}
                onClick={() => handleSelectPortal(card.key)}
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

        <p className="text-center text-xs text-slate-400 mt-8">
          © 2024 {companyName}产品智能中台. All rights reserved.
        </p>
      </div>
    </div>
  );
}
