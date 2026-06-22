'use client';

import React from 'react';
import { useRouter } from 'next/navigation';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import { User, Lock, Eye, EyeOff, Loader2, Palette, Factory, ArrowLeft, Megaphone, Scissors, Cloud, ChevronRight, Sparkles, Building2, CheckCircle2, Zap, Shield, Globe } from 'lucide-react';
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

  // 登录验证
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
        headers: { 'Content-Type': 'application/json' },
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

        const userCompany = result.data.user?.company;
        if (userCompany && userCompany.trim() !== '') {
          const brandKey = userCompany === '宝娜斯' ? 'bonasi' : 'yingyun';
          setSelectedBrand(brandKey);
          localStorage.setItem('selected_brand', brandKey);
          localStorage.setItem('user_company', userCompany);
          setStep('portal');
          toast.success('登录成功', {
            description: `欢迎回来，${result.data.user?.username || '用户'}！`,
          });
        } else {
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
      toast.error('登录失败', { description: '网络错误，请重试' });
    } finally {
      setIsLoading(false);
    }
  };

  // 选择公司后绑定
  const handleSelectCompany = async (companyKey: BrandKey) => {
    setSelectedBrand(companyKey);
    const companyName = companyKey === 'bonasi' ? '宝娜斯' : '盈云';

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
      // 降级
    }

    localStorage.setItem('selected_brand', companyKey);
    localStorage.setItem('user_company', companyName);
    setStep('portal');
  };

  // 选择入口后跳转
  const handleSelectPortal = (portalType: PortalType) => {
    setPortal(portalType);
    localStorage.setItem('portal_type', portalType || 'designer');
    toast.success('欢迎进入', { description: '正在跳转...' });

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

  // ========== Step 1: 登录表单 ==========
  if (step === 'login') {
    return (
      <div className="min-h-screen flex">
        <Toaster position="top-center" richColors closeButton />

        {/* 左侧品牌展示区 */}
        <div className="hidden lg:flex lg:w-[55%] relative overflow-hidden bg-gradient-to-br from-violet-600 via-purple-600 to-indigo-700">
          {/* 动态装饰元素 */}
          <div className="absolute inset-0">
            <div className="absolute top-20 left-20 w-72 h-72 bg-white/10 rounded-full blur-3xl animate-pulse" />
            <div className="absolute bottom-20 right-20 w-96 h-96 bg-pink-400/15 rounded-full blur-3xl animate-pulse" style={{ animationDelay: '1s' }} />
            <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[500px] h-[500px] bg-indigo-400/10 rounded-full blur-3xl" />
            {/* 网格线 */}
            <div className="absolute inset-0 opacity-[0.04]" style={{
              backgroundImage: 'linear-gradient(rgba(255,255,255,.5) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,.5) 1px, transparent 1px)',
              backgroundSize: '60px 60px'
            }} />
          </div>

          {/* 左侧内容 */}
          <div className="relative z-10 flex flex-col justify-center px-16 xl:px-24">
            <div className="max-w-lg">
              <div className="flex items-center gap-3 mb-8">
                <div className="w-12 h-12 rounded-xl bg-white/20 backdrop-blur-sm flex items-center justify-center">
                  <Sparkles className="w-6 h-6 text-white" />
                </div>
                <span className="text-white/80 text-lg font-medium">Smart Platform</span>
              </div>

              <h1 className="text-5xl xl:text-6xl font-bold text-white leading-tight mb-6">
                产品智能
                <br />
                <span className="bg-gradient-to-r from-pink-300 to-amber-200 bg-clip-text text-transparent">中台系统</span>
              </h1>

              <p className="text-white/70 text-lg leading-relaxed mb-12">
                融合AI智能与供应链管理，赋能无缝针织行业的数字化升级。从设计到生产，从报价到营销，一站式智能解决方案。
              </p>

              {/* 特性列表 */}
              <div className="space-y-4">
                {[
                  { icon: Zap, title: 'AI 智能识别', desc: '自动分类与标签提取' },
                  { icon: Shield, title: '供应链管理', desc: '智能报价与供应商对比' },
                  { icon: Globe, title: '多品牌协同', desc: '宝娜斯 & 盈云双品牌支持' },
                ].map((item, i) => (
                  <div key={i} className="flex items-center gap-4 group">
                    <div className="w-10 h-10 rounded-lg bg-white/10 backdrop-blur-sm flex items-center justify-center group-hover:bg-white/20 transition-colors">
                      <item.icon className="w-5 h-5 text-white/90" />
                    </div>
                    <div>
                      <p className="text-white font-medium text-sm">{item.title}</p>
                      <p className="text-white/50 text-xs">{item.desc}</p>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>

          {/* 底部装饰 */}
          <div className="absolute bottom-8 left-16 xl:left-24 text-white/30 text-xs">
            © 2024 盈云产品智能中台
          </div>
        </div>

        {/* 右侧登录表单区 */}
        <div className="flex-1 flex items-center justify-center p-8 bg-gradient-to-br from-slate-50 to-white relative">
          {/* 移动端 Logo */}
          <div className="lg:hidden absolute top-6 left-1/2 -translate-x-1/2">
            <div className="w-14 h-14 rounded-2xl bg-gradient-to-br from-violet-500 to-purple-600 flex items-center justify-center shadow-lg shadow-violet-500/30">
              <Sparkles className="w-7 h-7 text-white" />
            </div>
          </div>

          <div className="w-full max-w-[400px]">
            <div className="mb-8">
              <h2 className="text-2xl font-bold text-slate-800">欢迎回来</h2>
              <p className="text-slate-500 mt-1.5">请登录您的账号以继续</p>
            </div>

            <form onSubmit={handleLogin} className="space-y-5">
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1.5">用户名</label>
                <div className="relative group">
                  <div className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400 group-focus-within:text-violet-500 transition-colors">
                    <User className="w-[18px] h-[18px]" />
                  </div>
                  <input
                    type="text"
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                    placeholder="请输入用户名"
                    className={cn(
                      'w-full pl-11 pr-4 py-3 rounded-xl border border-slate-200 bg-white',
                      'focus:outline-none focus:ring-2 focus:ring-violet-500/20 focus:border-violet-400',
                      'placeholder:text-slate-400 text-slate-700',
                      'transition-all duration-200 hover:border-slate-300'
                    )}
                  />
                </div>
              </div>

              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1.5">密码</label>
                <div className="relative group">
                  <div className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400 group-focus-within:text-violet-500 transition-colors">
                    <Lock className="w-[18px] h-[18px]" />
                  </div>
                  <input
                    type={showPassword ? 'text' : 'password'}
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder="请输入密码"
                    className={cn(
                      'w-full pl-11 pr-11 py-3 rounded-xl border border-slate-200 bg-white',
                      'focus:outline-none focus:ring-2 focus:ring-violet-500/20 focus:border-violet-400',
                      'placeholder:text-slate-400 text-slate-700',
                      'transition-all duration-200 hover:border-slate-300'
                    )}
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword(!showPassword)}
                    className="absolute right-3.5 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 transition-colors"
                  >
                    {showPassword ? <EyeOff className="w-[18px] h-[18px]" /> : <Eye className="w-[18px] h-[18px]" />}
                  </button>
                </div>
              </div>

              <div className="flex items-center justify-between">
                <label className="flex items-center cursor-pointer group">
                  <input
                    type="checkbox"
                    checked={rememberMe}
                    onChange={(e) => setRememberMe(e.target.checked)}
                    className="w-4 h-4 text-violet-600 border-slate-300 rounded focus:ring-violet-500"
                  />
                  <span className="ml-2 text-sm text-slate-600 group-hover:text-slate-800 transition-colors">记住我</span>
                </label>
                <span className="text-xs text-slate-400">7天有效期</span>
              </div>

              <Button
                type="submit"
                disabled={isLoading}
                className={cn(
                  'w-full py-3 h-auto text-white font-semibold rounded-xl',
                  'bg-gradient-to-r from-violet-600 to-purple-600',
                  'shadow-lg shadow-violet-500/25 hover:shadow-xl hover:shadow-violet-500/30',
                  'hover:from-violet-500 hover:to-purple-500',
                  'transition-all duration-300',
                  'disabled:opacity-60 disabled:cursor-not-allowed disabled:hover:shadow-lg'
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
            <div className="mt-8 pt-6 border-t border-slate-100">
              <div className="flex items-center gap-3 mb-4">
                <div className="flex-1 h-px bg-slate-100" />
                <span className="text-xs text-slate-400 font-medium">快速体验</span>
                <div className="flex-1 h-px bg-slate-100" />
              </div>
              <div className="grid grid-cols-2 gap-3">
                <button
                  type="button"
                  onClick={() => quickLogin('admin')}
                  className={cn(
                    'py-2.5 px-4 rounded-xl text-sm font-medium',
                    'border border-slate-200 bg-white text-slate-600',
                    'hover:bg-violet-50 hover:border-violet-200 hover:text-violet-700',
                    'transition-all duration-200'
                  )}
                >
                  管理员登录
                </button>
                <button
                  type="button"
                  onClick={() => quickLogin('user')}
                  className={cn(
                    'py-2.5 px-4 rounded-xl text-sm font-medium',
                    'border border-slate-200 bg-white text-slate-600',
                    'hover:bg-violet-50 hover:border-violet-200 hover:text-violet-700',
                    'transition-all duration-200'
                  )}
                >
                  普通用户登录
                </button>
              </div>
            </div>

            <div className="mt-4 text-center">
              <button
                type="button"
                onClick={() => router.push('/register')}
                className="text-sm text-slate-500 hover:text-violet-600 transition-colors"
              >
                没有账号？<span className="font-medium">立即注册</span>
              </button>
            </div>
          </div>
        </div>
      </div>
    );
  }

  // ========== Step 2: 选择公司 ==========
  if (step === 'company') {
    return (
      <div className="min-h-screen flex">
        <Toaster position="top-center" richColors closeButton />

        {/* 左侧装饰 */}
        <div className="hidden lg:flex lg:w-[45%] relative overflow-hidden bg-gradient-to-br from-amber-500 via-orange-500 to-rose-500">
          <div className="absolute inset-0">
            <div className="absolute top-32 right-20 w-80 h-80 bg-white/10 rounded-full blur-3xl animate-pulse" />
            <div className="absolute bottom-20 left-10 w-64 h-64 bg-yellow-300/15 rounded-full blur-3xl animate-pulse" style={{ animationDelay: '1.5s' }} />
            <div className="absolute inset-0 opacity-[0.04]" style={{
              backgroundImage: 'linear-gradient(rgba(255,255,255,.5) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,.5) 1px, transparent 1px)',
              backgroundSize: '60px 60px'
            }} />
          </div>

          <div className="relative z-10 flex flex-col justify-center px-16 xl:px-20">
            <div className="max-w-md">
              <div className="w-14 h-14 rounded-2xl bg-white/20 backdrop-blur-sm flex items-center justify-center mb-8">
                <Building2 className="w-7 h-7 text-white" />
              </div>
              <h2 className="text-4xl font-bold text-white leading-tight mb-4">
                选择您的
                <br />
                所属公司
              </h2>
              <p className="text-white/70 text-lg leading-relaxed mb-8">
                此选择将永久绑定到您的账号，绑定后不可更改，请谨慎选择。
              </p>
              {loggedInUser?.user?.username && (
                <div className="inline-flex items-center gap-2 px-4 py-2 bg-white/15 backdrop-blur-sm rounded-xl border border-white/20">
                  <User className="w-4 h-4 text-white/80" />
                  <span className="text-white/90 text-sm font-medium">当前账号：{loggedInUser.user.username}</span>
                </div>
              )}
            </div>
          </div>
        </div>

        {/* 右侧选择区 */}
        <div className="flex-1 flex items-center justify-center p-8 bg-gradient-to-br from-slate-50 to-white">
          <div className="w-full max-w-xl">
            {/* 移动端标题 */}
            <div className="lg:hidden text-center mb-8">
              <div className="w-14 h-14 mx-auto rounded-2xl bg-gradient-to-br from-amber-500 to-rose-500 flex items-center justify-center shadow-lg mb-4">
                <Building2 className="w-7 h-7 text-white" />
              </div>
              <h1 className="text-2xl font-bold text-slate-800">选择您所属的公司</h1>
              <p className="text-slate-500 mt-1 text-sm">此选择将绑定到您的账号，不可更改</p>
            </div>

            <div className="space-y-4">
              {COMPANY_OPTIONS.map((company) => {
                const isBonasi = company.key === 'bonasi';
                const Icon = isBonasi ? Scissors : Cloud;
                const gradientFrom = isBonasi ? 'from-rose-500' : 'from-violet-500';
                const gradientTo = isBonasi ? 'to-pink-600' : 'to-purple-600';
                const hoverBg = isBonasi ? 'hover:bg-rose-50' : 'hover:bg-violet-50';
                const hoverBorder = isBonasi ? 'hover:border-rose-200' : 'hover:border-violet-200';
                const accentColor = isBonasi ? 'text-rose-600' : 'text-violet-600';

                return (
                  <button
                    key={company.key}
                    onClick={() => handleSelectCompany(company.key)}
                    className={cn(
                      'group w-full bg-white rounded-2xl border border-slate-200 p-6',
                      'hover:shadow-lg hover:-translate-y-0.5',
                      hoverBg, hoverBorder,
                      'transition-all duration-300 text-left flex items-center gap-5'
                    )}
                  >
                    <div className={cn(
                      'w-14 h-14 rounded-xl bg-gradient-to-br flex items-center justify-center shadow-lg flex-shrink-0',
                      gradientFrom, gradientTo
                    )}>
                      <Icon className="w-7 h-7 text-white" />
                    </div>
                    <div className="flex-1 min-w-0">
                      <h2 className="text-lg font-bold text-slate-800 mb-0.5">{company.fullName}</h2>
                      <p className="text-sm text-slate-500 truncate">{company.description}</p>
                    </div>
                    <div className="flex flex-col items-end gap-2 flex-shrink-0">
                      <ChevronRight className={cn('w-5 h-5 text-slate-300 group-hover:translate-x-1 transition-transform', accentColor)} />
                      <span className="text-[10px] text-amber-600 bg-amber-50 px-2 py-0.5 rounded-full border border-amber-100">
                        绑定后不可更改
                      </span>
                    </div>
                  </button>
                );
              })}
            </div>

            <button
              type="button"
              onClick={() => setStep('login')}
              className="mt-6 flex items-center gap-1.5 text-sm text-slate-500 hover:text-slate-700 transition-colors mx-auto"
            >
              <ArrowLeft className="w-4 h-4" />
              返回登录
            </button>
          </div>
        </div>
      </div>
    );
  }

  // ========== Step 3: 选择入口 ==========
  const Icon = selectedBrand === 'bonasi' ? Scissors : Cloud;
  const companyName = brand.name;
  const brandGradientFrom = selectedBrand === 'bonasi' ? 'from-rose-500' : 'from-violet-600';
  const brandGradientVia = selectedBrand === 'bonasi' ? 'via-pink-600' : 'via-purple-600';
  const brandGradientTo = selectedBrand === 'bonasi' ? 'to-rose-700' : 'to-indigo-700';

  const portalCards = [
    {
      key: 'designer' as PortalType,
      icon: Palette,
      title: '设计师入口',
      desc: '知识库管理、图片上传、AI识别、文档中心',
      gradient: 'from-violet-500 to-purple-600',
      hoverBorder: 'hover:border-violet-200',
      hoverBg: 'hover:bg-violet-50/50',
      accent: 'text-violet-600',
    },
    {
      key: 'factory' as PortalType,
      icon: Factory,
      title: '工厂/供应链入口',
      desc: '产品报价、原料管理、生产计划、辅料采购',
      gradient: 'from-amber-500 to-orange-600',
      hoverBorder: 'hover:border-amber-200',
      hoverBg: 'hover:bg-amber-50/50',
      accent: 'text-amber-600',
    },
    {
      key: 'marketing' as PortalType,
      icon: Megaphone,
      title: '市场营销AI入口',
      desc: '无缝针织行业营销策略、市场分析、文案生成',
      gradient: 'from-emerald-500 to-teal-600',
      hoverBorder: 'hover:border-emerald-200',
      hoverBg: 'hover:bg-emerald-50/50',
      accent: 'text-emerald-600',
    },
  ];

  return (
    <div className="min-h-screen flex">
      <Toaster position="top-center" richColors closeButton />

      {/* 左侧品牌装饰 */}
      <div className={cn('hidden lg:flex lg:w-[45%] relative overflow-hidden bg-gradient-to-br', brandGradientFrom, brandGradientVia, brandGradientTo)}>
        <div className="absolute inset-0">
          <div className="absolute top-20 right-10 w-72 h-72 bg-white/10 rounded-full blur-3xl animate-pulse" />
          <div className="absolute bottom-20 left-10 w-80 h-80 bg-white/5 rounded-full blur-3xl animate-pulse" style={{ animationDelay: '1s' }} />
          <div className="absolute inset-0 opacity-[0.04]" style={{
            backgroundImage: 'linear-gradient(rgba(255,255,255,.5) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,.5) 1px, transparent 1px)',
            backgroundSize: '60px 60px'
          }} />
        </div>

        <div className="relative z-10 flex flex-col justify-center px-16 xl:px-20">
          <div className="max-w-md">
            <div className={cn('w-14 h-14 rounded-2xl bg-white/20 backdrop-blur-sm flex items-center justify-center mb-8')}>
              <Icon className="w-7 h-7 text-white" />
            </div>
            <h2 className="text-4xl font-bold text-white leading-tight mb-4">
              {companyName}
              <br />
              产品智能中台
            </h2>
            <p className="text-white/70 text-lg leading-relaxed mb-8">{brand.slogan}</p>

            <div className="inline-flex items-center gap-2 px-4 py-2 bg-white/15 backdrop-blur-sm rounded-xl border border-white/20">
              <CheckCircle2 className="w-4 h-4 text-emerald-300" />
              <span className="text-white/90 text-sm font-medium">已绑定：{companyName}</span>
            </div>
          </div>
        </div>
      </div>

      {/* 右侧入口选择区 */}
      <div className="flex-1 flex items-center justify-center p-8 bg-gradient-to-br from-slate-50 to-white">
        <div className="w-full max-w-lg">
          {/* 移动端标题 */}
          <div className="lg:hidden text-center mb-8">
            <div className={cn('w-14 h-14 mx-auto rounded-2xl bg-gradient-to-br flex items-center justify-center shadow-lg mb-4', brandGradientFrom, brandGradientTo)}>
              <Icon className="w-7 h-7 text-white" />
            </div>
            <h1 className="text-2xl font-bold text-slate-800">{companyName}产品智能中台</h1>
            <div className="inline-flex items-center gap-1.5 mt-2 px-3 py-1 bg-green-50 border border-green-200 rounded-full">
              <CheckCircle2 className="w-3.5 h-3.5 text-green-500" />
              <span className="text-xs text-green-700 font-medium">已绑定：{companyName}</span>
            </div>
          </div>

          <div className="space-y-4">
            {portalCards.map((card) => {
              const CardIcon = card.icon;
              return (
                <button
                  key={card.key}
                  onClick={() => handleSelectPortal(card.key)}
                  className={cn(
                    'group w-full bg-white rounded-2xl border border-slate-200 p-6',
                    'hover:shadow-lg hover:-translate-y-0.5',
                    card.hoverBorder, card.hoverBg,
                    'transition-all duration-300 text-left flex items-center gap-5'
                  )}
                >
                  <div className={cn(
                    'w-14 h-14 rounded-xl bg-gradient-to-br flex items-center justify-center shadow-lg flex-shrink-0',
                    card.gradient
                  )}>
                    <CardIcon className="w-7 h-7 text-white" />
                  </div>
                  <div className="flex-1 min-w-0">
                    <h2 className="text-lg font-bold text-slate-800 mb-0.5">{card.title}</h2>
                    <p className="text-sm text-slate-500 truncate">{card.desc}</p>
                  </div>
                  <ChevronRight className={cn('w-5 h-5 text-slate-300 group-hover:translate-x-1 transition-transform', card.accent)} />
                </button>
              );
            })}
          </div>

          <button
            type="button"
            onClick={() => {
              setStep(loggedInUser?.user?.company ? 'login' : 'company');
            }}
            className="mt-6 flex items-center gap-1.5 text-sm text-slate-500 hover:text-slate-700 transition-colors mx-auto"
          >
            <ArrowLeft className="w-4 h-4" />
            返回上一步
          </button>
        </div>
      </div>
    </div>
  );
}
