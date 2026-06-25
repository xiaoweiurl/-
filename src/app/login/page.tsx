'use client';

import React from 'react';
import { useRouter } from 'next/navigation';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import { User, Lock, Eye, EyeOff, Loader2, Palette, Factory, ArrowLeft, Megaphone, Scissors, Cloud, ChevronRight, Sparkles, Building2, CheckCircle2, Zap, Shield, Globe, Cpu, TrendingUp, Layers } from 'lucide-react';
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
  const [step, setStep] = React.useState<Step>(() => {
    // 如果URL参数指定 step=portal，且用户已登录，直接进入角色选择
    if (typeof window !== 'undefined') {
      const params = new URLSearchParams(window.location.search);
      if (params.get('step') === 'portal' && localStorage.getItem('session_id')) {
        return 'portal';
      }
    }
    return 'login';
  });
  const [selectedBrand, setSelectedBrand] = React.useState<BrandKey>(() => {
    if (typeof window !== 'undefined') {
      const saved = localStorage.getItem('selected_brand');
      if (saved === 'bonasi' || saved === 'yingyun') return saved;
    }
    return 'yingyun';
  });
  const [portal, setPortal] = React.useState<PortalType>(null);
  const [username, setUsername] = React.useState('');
  const [password, setPassword] = React.useState('');
  const [showPassword, setShowPassword] = React.useState(false);
  const [rememberMe, setRememberMe] = React.useState(false);
  const [isLoading, setIsLoading] = React.useState(false);
  const [loggedInUser, setLoggedInUser] = React.useState<LoginResponse['data'] | null>(null);
  const [focusedField, setFocusedField] = React.useState<string | null>(null);

  const brand = BRANDS[selectedBrand];

  // 当从子页面返回(step=portal)时，恢复用户信息
  React.useEffect(() => {
    if (step === 'portal' && !loggedInUser) {
      const username = localStorage.getItem('user_id') || '';
      const company = localStorage.getItem('user_company') || '';
      // 构造最小用户信息以支持 portal 页面显示
      setLoggedInUser({
        success: true,
        data: {
          sessionId: localStorage.getItem('session_id') || '',
          user: {
            id: username,
            username: localStorage.getItem('username') || '用户',
            role: 'user' as const,
            company,
          },
        },
      } as LoginResponse);
    }
  }, []);

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
        if (result.data.user?.username) {
          localStorage.setItem('username', result.data.user.username);
        }
        setLoggedInUser(result.data);
        const userCompany = result.data.user?.company;
        if (userCompany && userCompany.trim() !== '') {
          const brandKey = userCompany === '宝娜斯' ? 'bonasi' : 'yingyun';
          setSelectedBrand(brandKey);
          localStorage.setItem('selected_brand', brandKey);
          localStorage.setItem('user_company', userCompany);
          setStep('portal');
          toast.success('登录成功', { description: `欢迎回来，${result.data.user?.username || '用户'}！` });
        } else {
          setStep('company');
          toast.success('验证通过', { description: '请选择您所属的公司' });
        }
      } else {
        toast.error('登录失败', { description: result.error || '用户名或密码错误' });
      }
    } catch (error) {
      console.error('登录失败:', error);
      toast.error('登录失败', { description: '网络错误，请重试' });
    } finally {
      setIsLoading(false);
    }
  };

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
    } catch { /* 降级 */ }
    localStorage.setItem('selected_brand', companyKey);
    localStorage.setItem('user_company', companyName);
    setStep('portal');
  };

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

  // ========== Step 1: 登录 ==========
  if (step === 'login') {
    return (
      <div className="min-h-screen flex">
        <Toaster position="top-center" richColors closeButton />

        {/* 左侧品牌区 */}
        <div className="hidden lg:flex lg:w-[52%] relative overflow-hidden bg-gradient-to-br from-[#1a1035] via-[#2d1b69] to-[#1a1035]">
          {/* 深层光晕 */}
          <div className="absolute inset-0">
            <div className="absolute top-[-10%] right-[-5%] w-[600px] h-[600px] bg-violet-600/20 rounded-full blur-[120px]" />
            <div className="absolute bottom-[-15%] left-[-10%] w-[500px] h-[500px] bg-fuchsia-500/15 rounded-full blur-[100px]" />
            <div className="absolute top-[40%] left-[30%] w-[300px] h-[300px] bg-indigo-400/10 rounded-full blur-[80px]" />
            {/* 细网格 */}
            <div className="absolute inset-0 opacity-[0.03]" style={{
              backgroundImage: 'linear-gradient(rgba(255,255,255,1) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,1) 1px, transparent 1px)',
              backgroundSize: '80px 80px'
            }} />
            {/* 浮动粒子 */}
            <div className="absolute top-[15%] left-[20%] w-2 h-2 bg-violet-400/40 rounded-full animate-pulse" />
            <div className="absolute top-[60%] right-[25%] w-1.5 h-1.5 bg-fuchsia-400/30 rounded-full animate-pulse" style={{ animationDelay: '2s' }} />
            <div className="absolute top-[80%] left-[40%] w-1 h-1 bg-indigo-300/40 rounded-full animate-pulse" style={{ animationDelay: '1s' }} />
          </div>

          {/* 品牌内容 */}
          <div className="relative z-10 flex flex-col justify-between px-14 xl:px-20 py-12 w-full">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-lg bg-gradient-to-br from-violet-500 to-fuchsia-500 flex items-center justify-center shadow-lg shadow-violet-500/30">
                <Sparkles className="w-5 h-5 text-white" />
              </div>
              <span className="text-white/60 text-sm font-medium tracking-wider uppercase">Smart Platform</span>
            </div>

            <div className="max-w-lg">
              <h1 className="text-[3.2rem] xl:text-[3.8rem] font-extrabold text-white leading-[1.1] mb-5 tracking-tight">
                企业数智
                <br />
                <span className="bg-gradient-to-r from-violet-300 via-fuchsia-300 to-amber-200 bg-clip-text text-transparent">中台系统</span>
              </h1>
              <p className="text-white/50 text-base leading-relaxed mb-10 max-w-md">
                融合AI智能与供应链管理，赋能无缝针织行业数字化升级。从设计到生产，从报价到营销，一站式智能解决方案。
              </p>

              {/* 数据指标 */}
              <div className="flex gap-8 mb-10">
                {[
                  { value: '99.9%', label: '系统可用性' },
                  { value: '< 200ms', label: '响应延迟' },
                  { value: '50+', label: '企业客户' },
                ].map((item, i) => (
                  <div key={i}>
                    <div className="text-xl font-bold text-white/90">{item.value}</div>
                    <div className="text-xs text-white/35 mt-0.5">{item.label}</div>
                  </div>
                ))}
              </div>

              {/* 能力卡片 */}
              <div className="space-y-3">
                {[
                  { icon: Cpu, title: 'AI 智能识别', desc: '自动分类 · 标签提取 · 语义搜索' },
                  { icon: TrendingUp, title: '供应链管理', desc: '智能报价 · 供应商对比 · 成本分析' },
                  { icon: Layers, title: '多品牌协同', desc: '宝娜斯 & 盈云 · 数据隔离 · 统一管理' },
                ].map((item, i) => (
                  <div key={i} className="flex items-center gap-3.5 group cursor-default">
                    <div className="w-9 h-9 rounded-lg bg-white/[0.07] backdrop-blur-sm flex items-center justify-center border border-white/[0.06] group-hover:bg-white/[0.12] group-hover:border-white/[0.1] transition-all duration-300">
                      <item.icon className="w-4 h-4 text-white/70" />
                    </div>
                    <div className="flex-1">
                      <div className="flex items-baseline gap-2">
                        <span className="text-white/85 text-sm font-semibold">{item.title}</span>
                        <span className="text-white/30 text-xs">{item.desc}</span>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </div>

            <div className="text-white/20 text-xs">
              © 2024 企业数智中台系统 · v2.0
            </div>
          </div>
        </div>

        {/* 右侧登录区 */}
        <div className="flex-1 flex items-center justify-center p-8 bg-[#fafafe] relative">
          {/* 移动端 Logo */}
          <div className="lg:hidden absolute top-8 left-1/2 -translate-x-1/2">
            <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-violet-500 to-purple-600 flex items-center justify-center shadow-lg shadow-violet-500/25">
              <Sparkles className="w-6 h-6 text-white" />
            </div>
          </div>

          <div className="w-full max-w-[380px]">
            {/* 标题 */}
            <div className="mb-10">
              <h2 className="text-[1.65rem] font-bold text-slate-800 tracking-tight">欢迎回来</h2>
              <p className="text-slate-400 mt-1.5 text-sm">登录以访问您的工作空间</p>
            </div>

            <form onSubmit={handleLogin} className="space-y-5">
              {/* 用户名 */}
              <div>
                <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wider mb-2">用户名</label>
                <div className={cn(
                  'relative rounded-xl border transition-all duration-200',
                  focusedField === 'username'
                    ? 'border-violet-400 ring-[3px] ring-violet-500/10 bg-white shadow-sm'
                    : 'border-slate-200 bg-white hover:border-slate-300'
                )}>
                  <div className={cn(
                    'absolute left-3.5 top-1/2 -translate-y-1/2 transition-colors duration-200',
                    focusedField === 'username' ? 'text-violet-500' : 'text-slate-400'
                  )}>
                    <User className="w-[17px] h-[17px]" />
                  </div>
                  <input
                    type="text"
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                    onFocus={() => setFocusedField('username')}
                    onBlur={() => setFocusedField(null)}
                    placeholder="请输入用户名"
                    className="w-full pl-10 pr-4 py-2.5 rounded-xl bg-transparent focus:outline-none placeholder:text-slate-300 text-slate-700 text-sm"
                  />
                </div>
              </div>

              {/* 密码 */}
              <div>
                <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wider mb-2">密码</label>
                <div className={cn(
                  'relative rounded-xl border transition-all duration-200',
                  focusedField === 'password'
                    ? 'border-violet-400 ring-[3px] ring-violet-500/10 bg-white shadow-sm'
                    : 'border-slate-200 bg-white hover:border-slate-300'
                )}>
                  <div className={cn(
                    'absolute left-3.5 top-1/2 -translate-y-1/2 transition-colors duration-200',
                    focusedField === 'password' ? 'text-violet-500' : 'text-slate-400'
                  )}>
                    <Lock className="w-[17px] h-[17px]" />
                  </div>
                  <input
                    type={showPassword ? 'text' : 'password'}
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    onFocus={() => setFocusedField('password')}
                    onBlur={() => setFocusedField(null)}
                    placeholder="请输入密码"
                    className="w-full pl-10 pr-10 py-2.5 rounded-xl bg-transparent focus:outline-none placeholder:text-slate-300 text-slate-700 text-sm"
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword(!showPassword)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 transition-colors p-0.5"
                  >
                    {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                  </button>
                </div>
              </div>

              {/* 记住我 */}
              <div className="flex items-center justify-between pt-1">
                <label className="flex items-center cursor-pointer group">
                  <div className={cn(
                    'w-4 h-4 rounded border flex items-center justify-center transition-all duration-200',
                    rememberMe
                      ? 'bg-violet-500 border-violet-500'
                      : 'border-slate-300 group-hover:border-slate-400'
                  )}>
                    {rememberMe && (
                      <svg className="w-2.5 h-2.5 text-white" viewBox="0 0 12 12" fill="none">
                        <path d="M2 6L5 9L10 3" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                      </svg>
                    )}
                  </div>
                  <input type="checkbox" checked={rememberMe} onChange={(e) => setRememberMe(e.target.checked)} className="sr-only" />
                  <span className="ml-2 text-sm text-slate-500 group-hover:text-slate-700 transition-colors">记住我</span>
                </label>
                <span className="text-xs text-slate-400">7天免登录</span>
              </div>

              {/* 登录按钮 */}
              <Button
                type="submit"
                disabled={isLoading}
                className={cn(
                  'w-full py-2.5 h-auto text-white font-semibold text-sm rounded-xl',
                  'bg-gradient-to-r from-violet-600 to-fuchsia-600',
                  'shadow-[0_4px_14px_-2px_rgba(124,58,237,0.4)]',
                  'hover:shadow-[0_6px_20px_-2px_rgba(124,58,237,0.5)]',
                  'hover:from-violet-500 hover:to-fuchsia-500',
                  'active:scale-[0.98]',
                  'transition-all duration-200',
                  'disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:shadow-[0_4px_14px_-2px_rgba(124,58,237,0.4)] disabled:active:scale-100'
                )}
              >
                {isLoading ? (
                  <>
                    <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                    验证中...
                  </>
                ) : (
                  '登 录'
                )}
              </Button>
            </form>

            <div className="mt-6 text-center">
              <button
                type="button"
                onClick={() => router.push('/register')}
                className="text-sm text-slate-400 hover:text-violet-600 transition-colors"
              >
                没有账号？<span className="font-semibold">立即注册</span>
              </button>
            </div>

            {/* 安全提示 */}
            <div className="mt-8 pt-6 border-t border-slate-100 flex items-center justify-center gap-4 text-[11px] text-slate-300">
              <span className="flex items-center gap-1"><Shield className="w-3 h-3" /> 加密传输</span>
              <span className="flex items-center gap-1"><Globe className="w-3 h-3" /> 安全连接</span>
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

        {/* 左侧 */}
        <div className="hidden lg:flex lg:w-[45%] relative overflow-hidden bg-gradient-to-br from-[#1a1035] via-[#2d1b69] to-[#1a1035]">
          <div className="absolute inset-0">
            <div className="absolute top-[-5%] right-[10%] w-[500px] h-[500px] bg-amber-500/10 rounded-full blur-[120px]" />
            <div className="absolute bottom-[-10%] left-[-5%] w-[400px] h-[400px] bg-orange-500/10 rounded-full blur-[100px]" />
            <div className="absolute inset-0 opacity-[0.03]" style={{
              backgroundImage: 'linear-gradient(rgba(255,255,255,1) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,1) 1px, transparent 1px)',
              backgroundSize: '80px 80px'
            }} />
          </div>

          <div className="relative z-10 flex flex-col justify-center px-14 xl:px-20">
            <div className="max-w-md">
              <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-amber-500 to-orange-600 flex items-center justify-center shadow-lg shadow-amber-500/30 mb-10">
                <Building2 className="w-6 h-6 text-white" />
              </div>
              <h2 className="text-[2.8rem] font-extrabold text-white leading-[1.1] mb-4 tracking-tight">
                选择您的
                <br />
                <span className="bg-gradient-to-r from-amber-300 to-orange-200 bg-clip-text text-transparent">所属公司</span>
              </h2>
              <p className="text-white/45 text-base leading-relaxed mb-8">
                此选择将永久绑定到您的账号，绑定后不可更改。
              </p>
              {loggedInUser?.user?.username && (
                <div className="inline-flex items-center gap-2 px-4 py-2.5 bg-white/[0.07] backdrop-blur-sm rounded-xl border border-white/[0.08]">
                  <User className="w-4 h-4 text-white/60" />
                  <span className="text-white/70 text-sm font-medium">当前账号：{loggedInUser.user.username}</span>
                </div>
              )}
            </div>
          </div>
        </div>

        {/* 右侧 */}
        <div className="flex-1 flex items-center justify-center p-8 bg-[#fafafe]">
          <div className="w-full max-w-xl">
            <div className="lg:hidden text-center mb-8">
              <div className="w-12 h-12 mx-auto rounded-xl bg-gradient-to-br from-amber-500 to-orange-600 flex items-center justify-center shadow-lg mb-4">
                <Building2 className="w-6 h-6 text-white" />
              </div>
              <h1 className="text-xl font-bold text-slate-800">选择您所属的公司</h1>
              <p className="text-slate-400 mt-1 text-sm">此选择将绑定到您的账号，不可更改</p>
            </div>

            <div className="space-y-3">
              {COMPANY_OPTIONS.map((company) => {
                const isBonasi = company.key === 'bonasi';
                const Icon = isBonasi ? Scissors : Cloud;
                const gradientFrom = isBonasi ? 'from-rose-500' : 'from-violet-500';
                const gradientTo = isBonasi ? 'to-pink-600' : 'to-fuchsia-600';
                const shadowColor = isBonasi ? 'shadow-rose-500/25' : 'shadow-violet-500/25';
                const hoverBorder = isBonasi ? 'hover:border-rose-200' : 'hover:border-violet-200';
                const accentColor = isBonasi ? 'text-rose-500' : 'text-violet-500';

                return (
                  <button
                    key={company.key}
                    onClick={() => handleSelectCompany(company.key)}
                    className={cn(
                      'group w-full bg-white rounded-2xl border border-slate-150 p-5',
                      'hover:shadow-lg hover:-translate-y-0.5',
                      hoverBorder,
                      'transition-all duration-300 text-left flex items-center gap-4'
                    )}
                  >
                    <div className={cn(
                      'w-12 h-12 rounded-xl bg-gradient-to-br flex items-center justify-center shadow-lg flex-shrink-0',
                      gradientFrom, gradientTo, shadowColor
                    )}>
                      <Icon className="w-6 h-6 text-white" />
                    </div>
                    <div className="flex-1 min-w-0">
                      <h2 className="text-base font-bold text-slate-800">{company.fullName}</h2>
                      <p className="text-xs text-slate-400 mt-0.5 truncate">{company.description}</p>
                    </div>
                    <div className="flex flex-col items-end gap-1.5 flex-shrink-0">
                      <ChevronRight className={cn('w-4 h-4 text-slate-300 group-hover:translate-x-0.5 transition-all duration-300', accentColor)} />
                      <span className="text-[10px] text-amber-600/80 bg-amber-50 px-1.5 py-0.5 rounded-md font-medium">
                        不可更改
                      </span>
                    </div>
                  </button>
                );
              })}
            </div>

            <button
              type="button"
              onClick={() => setStep('login')}
              className="mt-6 flex items-center gap-1.5 text-sm text-slate-400 hover:text-slate-600 transition-colors mx-auto"
            >
              <ArrowLeft className="w-3.5 h-3.5" />
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
  const brandGradientFrom = selectedBrand === 'bonasi' ? 'from-rose-600' : 'from-violet-600';
  const brandGradientVia = selectedBrand === 'bonasi' ? 'via-pink-700' : 'via-purple-700';
  const brandGradientTo = selectedBrand === 'bonasi' ? 'to-rose-800' : 'to-indigo-800';

  const portalCards = [
    {
      key: 'designer' as PortalType,
      icon: Palette,
      title: '设计师入口',
      desc: '知识库管理 · 图片上传 · AI识别 · 文档中心',
      gradient: 'from-violet-500 to-fuchsia-600',
      shadow: 'shadow-violet-500/25',
      hoverBorder: 'hover:border-violet-200',
      accent: 'text-violet-500',
    },
    {
      key: 'factory' as PortalType,
      icon: Factory,
      title: '工厂 / 供应链入口',
      desc: '产品报价 · 原料管理 · 生产计划 · 辅料采购',
      gradient: 'from-amber-500 to-orange-600',
      shadow: 'shadow-amber-500/25',
      hoverBorder: 'hover:border-amber-200',
      accent: 'text-amber-500',
    },
    {
      key: 'marketing' as PortalType,
      icon: Megaphone,
      title: '市场营销 AI 入口',
      desc: '营销策略 · 市场分析 · 文案生成 · 行业洞察',
      gradient: 'from-emerald-500 to-teal-600',
      shadow: 'shadow-emerald-500/25',
      hoverBorder: 'hover:border-emerald-200',
      accent: 'text-emerald-500',
    },
  ];

  return (
    <div className="min-h-screen flex">
      <Toaster position="top-center" richColors closeButton />

      {/* 左侧 */}
      <div className={cn('hidden lg:flex lg:w-[45%] relative overflow-hidden bg-gradient-to-br', brandGradientFrom, brandGradientVia, brandGradientTo)}>
        <div className="absolute inset-0">
          <div className="absolute top-[-10%] right-[5%] w-[500px] h-[500px] bg-white/[0.06] rounded-full blur-[120px]" />
          <div className="absolute bottom-[-10%] left-[5%] w-[400px] h-[400px] bg-white/[0.04] rounded-full blur-[100px]" />
          <div className="absolute inset-0 opacity-[0.03]" style={{
            backgroundImage: 'linear-gradient(rgba(255,255,255,1) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,1) 1px, transparent 1px)',
            backgroundSize: '80px 80px'
          }} />
        </div>

        <div className="relative z-10 flex flex-col justify-center px-14 xl:px-20">
          <div className="max-w-md">
            <div className="w-12 h-12 rounded-xl bg-white/15 backdrop-blur-sm flex items-center justify-center mb-10 border border-white/10">
              <Icon className="w-6 h-6 text-white" />
            </div>
            <h2 className="text-[2.8rem] font-extrabold text-white leading-[1.1] mb-4 tracking-tight">
              {companyName}
              <br />
              <span className="text-white/70">企业数智中台系统</span>
            </h2>
            <p className="text-white/40 text-base leading-relaxed mb-8">{brand.slogan}</p>

            <div className="inline-flex items-center gap-2 px-4 py-2.5 bg-white/[0.07] backdrop-blur-sm rounded-xl border border-white/[0.08]">
              <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />
              <span className="text-white/60 text-sm font-medium">已绑定：{companyName}</span>
            </div>
          </div>
        </div>
      </div>

      {/* 右侧 */}
      <div className="flex-1 flex items-center justify-center p-8 bg-[#fafafe]">
        <div className="w-full max-w-lg">
          <div className="lg:hidden text-center mb-8">
            <div className={cn('w-12 h-12 mx-auto rounded-xl bg-gradient-to-br flex items-center justify-center shadow-lg mb-4', brandGradientFrom, brandGradientTo)}>
              <Icon className="w-6 h-6 text-white" />
            </div>
            <h1 className="text-xl font-bold text-slate-800">{companyName}企业数智中台系统</h1>
            <div className="inline-flex items-center gap-1.5 mt-2 px-3 py-1 bg-emerald-50 border border-emerald-100 rounded-lg">
              <CheckCircle2 className="w-3 h-3 text-emerald-500" />
              <span className="text-[11px] text-emerald-700 font-medium">已绑定：{companyName}</span>
            </div>
          </div>

          <div className="space-y-3">
            {portalCards.map((card) => {
              const CardIcon = card.icon;
              return (
                <button
                  key={card.key}
                  onClick={() => handleSelectPortal(card.key)}
                  className={cn(
                    'group w-full bg-white rounded-2xl border border-slate-150 p-5',
                    'hover:shadow-lg hover:-translate-y-0.5',
                    card.hoverBorder,
                    'transition-all duration-300 text-left flex items-center gap-4'
                  )}
                >
                  <div className={cn(
                    'w-12 h-12 rounded-xl bg-gradient-to-br flex items-center justify-center shadow-lg flex-shrink-0',
                    card.gradient, card.shadow
                  )}>
                    <CardIcon className="w-6 h-6 text-white" />
                  </div>
                  <div className="flex-1 min-w-0">
                    <h2 className="text-base font-bold text-slate-800">{card.title}</h2>
                    <p className="text-xs text-slate-400 mt-0.5 truncate">{card.desc}</p>
                  </div>
                  <ChevronRight className={cn('w-4 h-4 text-slate-300 group-hover:translate-x-0.5 transition-all duration-300', card.accent)} />
                </button>
              );
            })}
          </div>

          <button
            type="button"
            onClick={() => {
              setStep(loggedInUser?.user?.company ? 'login' : 'company');
            }}
            className="mt-6 flex items-center gap-1.5 text-sm text-slate-400 hover:text-slate-600 transition-colors mx-auto"
          >
            <ArrowLeft className="w-3.5 h-3.5" />
            返回上一步
          </button>
        </div>
      </div>
    </div>
  );
}
