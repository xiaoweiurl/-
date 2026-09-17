'use client';

import React from 'react';
import { useRouter } from 'next/navigation';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import { User, Lock, Eye, EyeOff, Loader2, Palette, Factory, ArrowLeft, Megaphone, Scissors, Cloud, ChevronRight, Sparkles, Building2, CheckCircle2, Shield, Globe, Cpu, TrendingUp, Layers } from 'lucide-react';
import { toast } from 'sonner';
import { Toaster } from '@/components/ui/sonner';
import { BRANDS, COMPANY_OPTIONS, type BrandKey } from '@/lib/brand';
import { readReturnPath, takeReturnPath } from '@/lib/auth-redirect';

interface LoginResponse {
  success: boolean;
  message?: string;
  error?: string;
  data?: {
    sessionId?: string;
    expiresIn?: number;
    alreadyLoggedIn?: boolean;
    user: {
      id: string;
      username: string;
      email?: string;
      avatar?: string;
      role: string;
      membership?: string;
      company?: string;
      mustChangePassword?: boolean;
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
  const [showDuplicateLoginDialog, setShowDuplicateLoginDialog] = React.useState(false);
  const [loggedInUser, setLoggedInUser] = React.useState<LoginResponse['data'] | null>(null);
  const [focusedField, setFocusedField] = React.useState<string | null>(null);

  const brand = BRANDS[selectedBrand];

  // 客户端初始化：从 localStorage 恢复状态（避免 SSR hydration mismatch）
  React.useEffect(() => {
    // 钉钉深链：把 ?returnUrl=/sampler/{id} 写入 sessionStorage，登录多步不丢。
    // 有回跳地址时不要恢复门户选择，否则会丢掉商品 id。
    const returnPath = readReturnPath();
    const backToPortal = localStorage.getItem('back_to_portal');
    if (!returnPath && backToPortal === 'true' && localStorage.getItem('session_id')) {
      localStorage.removeItem('back_to_portal');
      setStep('portal');
    }

    // 恢复 selectedBrand 状态
    const saved = localStorage.getItem('selected_brand');
    if (saved === 'bonasi' || saved === 'yingyun') {
      setSelectedBrand(saved);
    }
  }, []);

  // 当从子页面返回(step=portal)时，恢复用户信息
  React.useEffect(() => {
    if (step === 'portal' && !loggedInUser) {
      const username = localStorage.getItem('user_id') || '';
      const company = localStorage.getItem('user_company') || '';
      // 构造最小用户信息以支持 portal 页面显示
      setLoggedInUser({
        sessionId: localStorage.getItem('session_id') || '',
        user: {
          id: username,
          username: localStorage.getItem('username') || '用户',
          role: 'user' as const,
          company,
        },
      });
    }
  }, []);

  // 清除旧会话数据（登录前调用）
  const clearOldSession = async () => {
    const oldSessionId = localStorage.getItem('session_id');
    if (oldSessionId) {
      try {
        // 调专用接口删除 Redis 中的旧 session
        await fetch('/api/auth/session', {
          method: 'DELETE',
          credentials: 'include',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ sessionId: oldSessionId }),
        });
      } catch {
        // 忽略失败，继续登录
      }
    }
    // 清除所有本地存储的用户数据
    localStorage.removeItem('session_id');
    localStorage.removeItem('session_expires');
    localStorage.removeItem('user_id');
    localStorage.removeItem('username');
    localStorage.removeItem('user_role');
    localStorage.removeItem('user_company');
    // 清除 cookie
    document.cookie = 'session_id=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT';
    document.cookie = 'user_role=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT';
  };

  // 跨标签页会话同步：当其他标签页登录/登出时，当前标签页自动刷新
  React.useEffect(() => {
    const handleStorageChange = (e: StorageEvent) => {
      if (e.key === 'session_id') {
        if (e.newValue === null) {
          // 其他标签页登出了，当前标签页也登出
          localStorage.removeItem('session_id');
          localStorage.removeItem('session_expires');
          localStorage.removeItem('user_id');
          localStorage.removeItem('username');
          localStorage.removeItem('user_role');
          localStorage.removeItem('user_company');
          setLoggedInUser(null);
          setStep('login');
          toast.info('已在其他窗口登出');
        } else if (e.newValue !== e.oldValue && e.newValue !== localStorage.getItem('session_id')) {
          // 其他标签页登录了不同账号，当前标签页刷新以同步
          window.location.reload();
        }
      }
    };
    window.addEventListener('storage', handleStorageChange);
    return () => window.removeEventListener('storage', handleStorageChange);
  }, []);

  // 实际执行登录逻辑
  const doLogin = async (forceLogin: boolean = false) => {
    setIsLoading(true);
    try {
      // SSO：只在确认强制登录时才清除旧会话
      // 首次登录（forceLogin=false）不清除，让后端 Redis 判断是否已有活跃会话
      if (forceLogin) {
        await clearOldSession();
      }

      const response = await fetch('/api/auth/login', {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password, rememberMe, forceLogin }),
      });

      // SSO: HTTP 409 = 用户已在其他地方登录，需要确认
      // 用 HTTP 状态码判断，不依赖 JSON 字段解析，最可靠
      if (response.status === 409 && !forceLogin) {
        setShowDuplicateLoginDialog(true);
        setIsLoading(false);
        return;
      }

      const result: LoginResponse = await response.json();

      if (result.success && result.data) {
        // 安全检查：正常登录必须有 user 数据，否则是异常响应
        if (!result.data.user) {
          console.error('[Login] 响应成功但缺少 user 数据:', JSON.stringify(result.data));
          toast.error('登录异常', { description: '服务器返回数据不完整，请重试' });
          return;
        }
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
        if (result.data.user?.role) {
          localStorage.setItem('user_role', result.data.user.role);
          // 同时存入 cookie，确保降级模式也能获取
          const roleExpiry = new Date(Date.now() + 7 * 24 * 3600 * 1000).toUTCString();
          document.cookie = `user_role=${result.data.user.role}; path=/; expires=${roleExpiry}; SameSite=Lax`;
        }
        if (result.data.user?.company) {
          localStorage.setItem('user_company', result.data.user.company);
        }
        setLoggedInUser(result.data);
        // 安全：种子账号/被管理员重置密码后，首次登录强制改密
        if (result.data.user?.mustChangePassword) {
          toast.warning('请先修改密码', { description: '首次登录需修改初始密码后才能继续使用' });
          window.location.href = '/settings?tab=security&forceChange=1';
          return;
        }
        const userCompany = result.data.user?.company;
        if (userCompany && userCompany.trim() !== '') {
          const brandKey = userCompany.includes('宝娜斯') ? 'bonasi' : 'yingyun';
          setSelectedBrand(brandKey);
          localStorage.setItem('selected_brand', brandKey);
          localStorage.setItem('user_company', userCompany);
          toast.success('登录成功', { description: `欢迎回来，${result.data.user?.username || '用户'}！` });
          const pendingReturn = takeReturnPath();
          if (pendingReturn) {
            window.location.href = pendingReturn;
            return;
          }
          setStep('portal');
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

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!username.trim() || !password.trim()) {
      toast.error('请输入用户名和密码');
      return;
    }

    // 直接调用登录接口，由后端检查是否已登录
    await doLogin(false);
  };

  // 确认重复登录（强制登录，踢掉旧会话）
  const handleConfirmDuplicateLogin = async () => {
    setShowDuplicateLoginDialog(false);
    await doLogin(true);
  };

  const handleSelectCompany = async (companyKey: BrandKey) => {
    setSelectedBrand(companyKey);
    const companyName = '宝娜斯集团';
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
    const pendingReturn = takeReturnPath();
    if (pendingReturn) {
      window.location.href = pendingReturn;
      return;
    }
    setStep('portal');
  };

  const handleSelectPortal = (portalType: PortalType) => {
    setPortal(portalType);
    localStorage.setItem('portal_type', portalType || 'designer');
    toast.success('欢迎进入', { description: '正在跳转...' });
    setTimeout(() => {
      if (portalType === 'factory') {
        window.location.href = '/supply-chain';
      } else if (portalType === 'marketing') {
        window.location.href = '/marketing';
      } else {
        window.location.href = '/';
      }
    }, 300);
  };

  // SSO 重复登录确认弹窗（独立变量，需在每个 step 的 return 中引用）
  const ssoDialog = showDuplicateLoginDialog && (
    <div
      className="fixed inset-0 z-[9999] flex items-center justify-center"
      onClick={() => setShowDuplicateLoginDialog(false)}
    >
      {/* 遮罩层 */}
      <div className="absolute inset-0 bg-black/10 backdrop-blur-sm" />
      {/* 弹窗主体 */}
      <div
        className="relative bg-[rgba(242,242,247,0.95)] border border-[rgba(0,122,255,0.3)] backdrop-blur-xl rounded-xl p-6 max-w-md w-full mx-4 shadow-[0_2px_12px_rgba(0,0,0,0.04)]"
        onClick={(e) => e.stopPropagation()}
      >
        {/* 标题 */}
        <div className="flex items-center gap-2 mb-4">
          <Shield className="w-5 h-5 text-[#007aff]" />
          <h3 className="text-lg font-semibold text-[#1c1c1e]">账户已登录</h3>
        </div>
        {/* 内容 */}
        <p className="text-[#8e8e93] text-sm mb-6">
          账户 <span className="text-[#007aff] font-medium">{username}</span> 已在其他地方登录。
          <br />
          确认登录将使之前的登录失效，是否继续？
        </p>
        {/* 按钮 */}
        <div className="flex justify-end gap-3">
          <button
            onClick={() => setShowDuplicateLoginDialog(false)}
            className="px-4 py-2 rounded-lg bg-[#ffffff] border border-[#e5e5ea] text-[#3a3a3c] hover:bg-[rgba(118,118,128,0.12)] hover:text-[#1c1c1e] transition-colors text-sm"
          >
            取消
          </button>
          <button
            onClick={handleConfirmDuplicateLogin}
            className="px-4 py-2 rounded-lg bg-[#007aff] hover:bg-[#007aff] text-white transition-colors text-sm font-medium"
          >
            确认登录
          </button>
        </div>
      </div>
    </div>
  );

  // ========== Step 1: 登录 ==========
  if (step === 'login') {
    return (
      <React.Fragment>
      <div className="min-h-screen flex">
        <Toaster position="top-center" richColors closeButton />

        {/* 左侧品牌区 */}
        <div className="hidden lg:flex lg:w-[52%] relative overflow-hidden bg-gradient-to-br from-[#007AFF] via-[#0055D4] to-[#007AFF]">
          {/* 深层光晕 */}
          <div className="absolute inset-0">
            <div className="absolute top-[-10%] right-[-5%] w-[600px] h-[600px] bg-[rgba(0,122,255,0.2)] rounded-full blur-[120px]" />
            <div className="absolute bottom-[-15%] left-[-10%] w-[500px] h-[500px] bg-[rgba(175,82,222,0.15)] rounded-full blur-[100px]" />
            <div className="absolute top-[40%] left-[30%] w-[300px] h-[300px] bg-[rgba(0,122,255,0.1)] rounded-full blur-[80px]" />
            {/* 细网格 */}
            <div className="absolute inset-0 opacity-[0.03]" style={{
              backgroundImage: 'linear-gradient(rgba(255,255,255,1) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,1) 1px, transparent 1px)',
              backgroundSize: '80px 80px'
            }} />
            {/* 浮动粒子 */}
            <div className="absolute top-[15%] left-[20%] w-2 h-2 bg-[rgba(0,122,255,0.4)] rounded-full animate-pulse" />
            <div className="absolute top-[60%] right-[25%] w-1.5 h-1.5 bg-[rgba(175,82,222,0.3)] rounded-full animate-pulse" style={{ animationDelay: '2s' }} />
            <div className="absolute top-[80%] left-[40%] w-1 h-1 bg-[rgba(0,122,255,0.4)] rounded-full animate-pulse" style={{ animationDelay: '1s' }} />
          </div>

          {/* 品牌内容 */}
          <div className="relative z-10 flex flex-col justify-between px-14 xl:px-20 py-12 w-full">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-lg bg-[rgba(118,118,128,0.16)] backdrop-blur-sm border border-white/30 flex items-center justify-center shadow-lg">
                <Sparkles className="w-5 h-5 text-white" />
              </div>
              <span className="text-white/70 text-sm font-medium tracking-wider uppercase">Smart Platform</span>
            </div>

            <div className="max-w-lg">
              <h1 className="text-[3.2rem] xl:text-[3.8rem] font-extrabold text-white leading-[1.1] mb-5 tracking-tight">
                企业数智
                <br />
                <span className="text-white/80">中台系统</span>
              </h1>
              <p className="text-white/60 text-base leading-relaxed mb-10 max-w-md">
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
                    <div className="text-xl font-bold text-white">{item.value}</div>
                    <div className="text-xs text-white/50 mt-0.5">{item.label}</div>
                  </div>
                ))}
              </div>

              {/* 能力卡片 */}
              <div className="space-y-3">
                {[
                  { icon: Cpu, title: 'AI 智能识别', desc: '自动分类 · 标签提取 · 语义搜索' },
                  { icon: TrendingUp, title: '供应链管理', desc: '智能报价 · 供应商对比 · 成本分析' },
                  { icon: Layers, title: '集团一体化', desc: '宝娜斯集团 · 全链路数据 · 统一管理' },
                ].map((item, i) => (
                  <div key={i} className="flex items-center gap-3.5 group cursor-default">
                    <div className="w-9 h-9 rounded-lg bg-white/15 backdrop-blur-sm flex items-center justify-center border border-white/20 group-hover:bg-white/25 transition-all duration-300">
                      <item.icon className="w-4 h-4 text-white/80" />
                    </div>
                    <div className="flex-1">
                      <div className="flex items-baseline gap-2">
                        <span className="text-white/90 text-sm font-semibold">{item.title}</span>
                        <span className="text-white/40 text-xs">{item.desc}</span>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </div>

            <div className="text-white/30 text-xs">
              © 2024 企业数智中台系统 · v2.0
            </div>
          </div>
        </div>

        {/* 右侧登录区 */}
        <div className="flex-1 flex items-center justify-center p-8 bg-[#fafafe] relative">
          {/* 移动端 Logo */}
          <div className="lg:hidden absolute top-8 left-1/2 -translate-x-1/2">
            <div className="w-12 h-12 rounded-xl bg-[#007AFF] flex items-center justify-center shadow-lg">
              <Sparkles className="w-6 h-6 text-white" />
            </div>
          </div>

          <div className="w-full max-w-[380px]">
            {/* 标题 */}
            <div className="mb-10">
              <h2 className="text-[1.65rem] font-bold text-[#8e8e93] tracking-tight">欢迎回来</h2>
              <p className="text-[#8e8e93] mt-1.5 text-sm">登录以访问您的工作空间</p>
            </div>

            <form onSubmit={handleLogin} className="space-y-5">
              {/* 用户名 */}
              <div>
                <label className="block text-xs font-semibold text-[#8e8e93] uppercase tracking-wider mb-2">用户名</label>
                <div className={cn(
                  'relative rounded-xl border transition-all duration-200',
                  focusedField === 'username'
                    ? 'border-[#007aff] ring-[3px] ring-[rgba(0,122,255,0.1)] bg-white shadow-sm'
                    : 'border-[#e5e5ea] bg-white hover:border-[#e5e5ea]'
                )}>
                  <div className={cn(
                    'absolute left-3.5 top-1/2 -translate-y-1/2 transition-colors duration-200',
                    focusedField === 'username' ? 'text-[#007aff]' : 'text-[#8e8e93]'
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
                    className="w-full pl-10 pr-4 py-2.5 rounded-xl bg-transparent focus:outline-none placeholder:text-[#3a3a3c] text-[#8e8e93] text-sm"
                  />
                </div>
              </div>

              {/* 密码 */}
              <div>
                <label className="block text-xs font-semibold text-[#8e8e93] uppercase tracking-wider mb-2">密码</label>
                <div className={cn(
                  'relative rounded-xl border transition-all duration-200',
                  focusedField === 'password'
                    ? 'border-[#007aff] ring-[3px] ring-[rgba(0,122,255,0.1)] bg-white shadow-sm'
                    : 'border-[#e5e5ea] bg-white hover:border-[#e5e5ea]'
                )}>
                  <div className={cn(
                    'absolute left-3.5 top-1/2 -translate-y-1/2 transition-colors duration-200',
                    focusedField === 'password' ? 'text-[#007aff]' : 'text-[#8e8e93]'
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
                    className="w-full pl-10 pr-10 py-2.5 rounded-xl bg-transparent focus:outline-none placeholder:text-[#3a3a3c] text-[#8e8e93] text-sm"
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword(!showPassword)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-[#8e8e93] hover:text-[#8e8e93] transition-colors p-0.5"
                  >
                    {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                  </button>
                </div>
              </div>

              {/* 记住我 + 忘记密码 */}
              <div className="flex items-center justify-between pt-1">
                <label className="flex items-center cursor-pointer group">
                  <div className={cn(
                    'w-4 h-4 rounded border flex items-center justify-center transition-all duration-200',
                    rememberMe
                      ? 'bg-[#007aff] border-[#007aff]'
                      : 'border-[#e5e5ea] group-hover:border-[#e5e5ea]'
                  )}>
                    {rememberMe && (
                      <svg className="w-2.5 h-2.5 text-[#1C1C1E]" viewBox="0 0 12 12" fill="none">
                        <path d="M2 6L5 9L10 3" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                      </svg>
                    )}
                  </div>
                  <input type="checkbox" checked={rememberMe} onChange={(e) => setRememberMe(e.target.checked)} className="sr-only" />
                  <span className="ml-2 text-sm text-[#8e8e93] group-hover:text-[#8e8e93] transition-colors">记住我</span>
                </label>
                <button
                  type="button"
                  onClick={() => router.push('/forgot-password')}
                  className="text-xs text-[#007aff] hover:text-[#007aff] font-medium transition-colors"
                >
                  忘记密码？
                </button>
              </div>

              {/* 登录按钮 */}
              <Button
                type="submit"
                disabled={isLoading}
                className={cn(
                  'w-full py-2.5 h-auto text-white font-semibold text-sm rounded-xl',
                  'bg-[#007AFF] hover:opacity-90',
                  'active:scale-[0.98]',
                  'transition-all duration-200',
                  'disabled:opacity-50 disabled:cursor-not-allowed disabled:active:scale-100'
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
                className="text-sm text-[#8e8e93] hover:text-[#007aff] transition-colors"
              >
                没有账号？<span className="font-semibold">立即注册</span>
              </button>
            </div>

            {/* 安全提示 */}
            <div className="mt-8 pt-6 border-t border-[#e5e5ea] flex items-center justify-center gap-4 text-[11px] text-[#3a3a3c]">
              <span className="flex items-center gap-1"><Shield className="w-3 h-3" /> 加密传输</span>
              <span className="flex items-center gap-1"><Globe className="w-3 h-3" /> 安全连接</span>
            </div>
          </div>
        </div>
      </div>
      {ssoDialog}
    </React.Fragment>
  );
}

  // ========== Step 2: 选择公司 ==========
  if (step === 'company') {
    return (
      <React.Fragment>
      <div className="min-h-screen flex">
        <Toaster position="top-center" richColors closeButton />

        {/* 左侧 */}
        <div className="hidden lg:flex lg:w-[45%] relative overflow-hidden bg-gradient-to-br from-[#007AFF] via-[#0055D4] to-[#007AFF]">
          <div className="absolute inset-0">
            <div className="absolute top-[-5%] right-[10%] w-[500px] h-[500px] bg-[rgba(255,149,0,0.1)] rounded-full blur-[120px]" />
            <div className="absolute bottom-[-10%] left-[-5%] w-[400px] h-[400px] bg-[rgba(255,149,0,0.1)] rounded-full blur-[100px]" />
            <div className="absolute inset-0 opacity-[0.03]" style={{
              backgroundImage: 'linear-gradient(rgba(255,255,255,1) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,1) 1px, transparent 1px)',
              backgroundSize: '80px 80px'
            }} />
          </div>

          <div className="relative z-10 flex flex-col justify-center px-14 xl:px-20">
            <div className="max-w-md">
              <div className="w-12 h-12 rounded-xl bg-[rgba(118,118,128,0.16)] backdrop-blur-sm border border-white/30 flex items-center justify-center shadow-lg mb-10">
                <Building2 className="w-6 h-6 text-white" />
              </div>
              <h2 className="text-[2.8rem] font-extrabold text-white leading-[1.1] mb-4 tracking-tight">
                选择您的
                <br />
                <span className="text-white/80">所属公司</span>
              </h2>
              <p className="text-white/60 text-base leading-relaxed mb-8">
                此选择将永久绑定到您的账号，绑定后不可更改。
              </p>
              {loggedInUser?.user?.username && (
                <div className="inline-flex items-center gap-2 px-4 py-2.5 bg-[rgba(118,118,128,0.16)] backdrop-blur-sm rounded-xl border border-white/30">
                  <User className="w-4 h-4 text-white/80" />
                  <span className="text-white/90 text-sm font-medium">当前账号：{loggedInUser.user.username}</span>
                </div>
              )}
            </div>
          </div>
        </div>

        {/* 右侧 */}
        <div className="flex-1 flex items-center justify-center p-8 bg-[#fafafe]">
          <div className="w-full max-w-xl">
            <div className="lg:hidden text-center mb-8">
              <div className="w-12 h-12 mx-auto rounded-xl bg-[#007AFF] flex items-center justify-center shadow-lg mb-4">
                <Building2 className="w-6 h-6 text-white" />
              </div>
              <h1 className="text-xl font-bold text-[#1C1C1E]">选择您所属的公司</h1>
              <p className="text-[#8E8E93] mt-1 text-sm">此选择将绑定到您的账号，不可更改</p>
            </div>

            <div className="space-y-3">
              {COMPANY_OPTIONS.map((company) => {
                const isBonasi = company.key === 'bonasi';
                const Icon = isBonasi ? Scissors : Cloud;
                const iconBg = isBonasi ? 'bg-[#007AFF]' : 'bg-[#AF52DE]';
                const hoverBorder = isBonasi ? 'hover:border-[#007aff]' : 'hover:border-[#007aff]';
                const accentColor = isBonasi ? 'text-[#007aff]' : 'text-[#007aff]';

                return (
                  <button
                    key={company.key}
                    onClick={() => handleSelectCompany(company.key)}
                    className={cn(
                      'group w-full bg-white rounded-2xl border border-[#e5e5ea] p-5',
                      'hover:shadow-lg hover:-translate-y-0.5',
                      hoverBorder,
                      'transition-all duration-300 text-left flex items-center gap-4'
                    )}
                  >
                    <div className={cn(
                      'w-12 h-12 rounded-xl flex items-center justify-center shadow-lg flex-shrink-0',
                      iconBg
                    )}>
                      <Icon className="w-6 h-6 text-white" />
                    </div>
                    <div className="flex-1 min-w-0">
                      <h2 className="text-base font-bold text-[#1C1C1E]">{company.fullName}</h2>
                      <p className="text-xs text-[#8e8e93] mt-0.5 truncate">{company.description}</p>
                    </div>
                    <div className="flex flex-col items-end gap-1.5 flex-shrink-0">
                      <ChevronRight className={cn('w-4 h-4 text-[#3a3a3c] group-hover:translate-x-0.5 transition-all duration-300', accentColor)} />
                      <span className="text-[10px] text-[rgba(255,149,0,0.8)] bg-[rgba(255,149,0,0.1)] px-1.5 py-0.5 rounded-md font-medium">
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
              className="mt-6 flex items-center gap-1.5 text-sm text-[#8e8e93] hover:text-[#8e8e93] transition-colors mx-auto"
            >
              <ArrowLeft className="w-3.5 h-3.5" />
              返回登录
            </button>
          </div>
        </div>
      </div>
      {ssoDialog}
      </React.Fragment>
    );
  }

  // ========== Step 3: 选择入口 ==========
  const Icon = selectedBrand === 'bonasi' ? Scissors : Cloud;
  const companyName = brand.name;
  const brandGradientFrom = selectedBrand === 'bonasi' ? 'from-[#007aff]' : 'from-[#007aff]';
  const brandGradientVia = selectedBrand === 'bonasi' ? 'via-[#af52de]' : 'via-[#007aff]';
  const brandGradientTo = selectedBrand === 'bonasi' ? 'to-[#007aff]' : 'to-[#007aff]';

  const portalCards = [
    {
      key: 'designer' as PortalType,
      icon: Palette,
      title: '设计师入口',
      desc: '知识库管理 · 图片上传 · AI识别 · 文档中心',
      gradient: 'bg-[#007AFF]',
      shadow: 'shadow-[0_2px_12px_rgba(0,0,0,0.04)]',
      hoverBorder: 'hover:border-[#007aff]',
      accent: 'text-[#007aff]',
    },
    {
      key: 'factory' as PortalType,
      icon: Factory,
      title: '工厂 / 供应链入口',
      desc: '产品报价 · 原料管理 · 生产计划 · 辅料采购',
      gradient: 'bg-[#FF9500]',
      shadow: 'shadow-[0_2px_12px_rgba(0,0,0,0.04)]',
      hoverBorder: 'hover:border-[#ff9500]',
      accent: 'text-[#ff9500]',
    },
    {
      key: 'marketing' as PortalType,
      icon: Megaphone,
      title: '市场营销 AI 入口',
      desc: '营销策略 · 市场分析 · 文案生成 · 行业洞察',
      gradient: 'bg-[#34C759]',
      shadow: 'shadow-[0_2px_12px_rgba(0,0,0,0.04)]',
      hoverBorder: 'hover:border-[#34c759]',
      accent: 'text-[#34c759]',
    },
  ];

  return (
    <React.Fragment>
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
            <div className="w-12 h-12 rounded-xl bg-[rgba(118,118,128,0.16)] backdrop-blur-sm flex items-center justify-center mb-10 border border-white/30">
              <Icon className="w-6 h-6 text-white" />
            </div>
            <h2 className="text-[2.8rem] font-extrabold text-white leading-[1.1] mb-4 tracking-tight">
              {companyName}
              <br />
              <span className="text-white/80">企业数智中台系统</span>
            </h2>
            <p className="text-white/60 text-base leading-relaxed mb-8">{brand.slogan}</p>

            <div className="inline-flex items-center gap-2 px-4 py-2.5 bg-[rgba(118,118,128,0.16)] backdrop-blur-sm rounded-xl border border-white/30">
              <CheckCircle2 className="w-3.5 h-3.5 text-[#34c759]" />
              <span className="text-white/80 text-sm font-medium">已绑定：{companyName}</span>
            </div>
          </div>
        </div>
      </div>

      {/* 右侧 */}
      <div className="flex-1 flex items-center justify-center p-8 bg-[#fafafe]">
        <div className="w-full max-w-lg">
          <div className="lg:hidden text-center mb-8">
            <div className="w-12 h-12 mx-auto rounded-xl bg-[#007AFF] flex items-center justify-center shadow-lg mb-4">
              <Icon className="w-6 h-6 text-white" />
            </div>
            <h1 className="text-xl font-bold text-[#1C1C1E]">{companyName}企业数智中台系统</h1>
            <div className="inline-flex items-center gap-1.5 mt-2 px-3 py-1 bg-[rgba(52,199,89,0.1)] border border-[#34c759] rounded-lg">
              <CheckCircle2 className="w-3 h-3 text-[#34c759]" />
              <span className="text-[11px] text-[#34c759] font-medium">已绑定：{companyName}</span>
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
                    'group w-full bg-white rounded-2xl border border-[#e5e5ea] p-5',
                    'hover:shadow-lg hover:-translate-y-0.5',
                    card.hoverBorder,
                    'transition-all duration-300 text-left flex items-center gap-4'
                  )}
                >
                  <div className={cn(
                    'w-12 h-12 rounded-xl bg-gradient-to-br flex items-center justify-center shadow-lg flex-shrink-0',
                    card.gradient, card.shadow
                  )}>
                    <CardIcon className="w-6 h-6 text-[#1C1C1E]" />
                  </div>
                  <div className="flex-1 min-w-0">
                    <h2 className="text-base font-bold text-[#8e8e93]">{card.title}</h2>
                    <p className="text-xs text-[#8e8e93] mt-0.5 truncate">{card.desc}</p>
                  </div>
                  <ChevronRight className={cn('w-4 h-4 text-[#3a3a3c] group-hover:translate-x-0.5 transition-all duration-300', card.accent)} />
                </button>
              );
            })}
          </div>

          <button
            type="button"
            onClick={() => {
              setStep(loggedInUser?.user?.company ? 'login' : 'company');
            }}
            className="mt-6 flex items-center gap-1.5 text-sm text-[#8e8e93] hover:text-[#8e8e93] transition-colors mx-auto"
          >
            <ArrowLeft className="w-3.5 h-3.5" />
            返回上一步
          </button>
        </div>
      </div>
      </div>
      {ssoDialog}
    </React.Fragment>
  );
}
