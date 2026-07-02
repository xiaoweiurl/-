'use client';

import React, { useEffect, useRef } from 'react';
import { useRouter } from 'next/navigation';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import {
  User, Lock, Eye, EyeOff, Loader2, Palette, Factory,
  ArrowLeft, Megaphone, Scissors, Cloud, ChevronRight,
  Sparkles, Building2, CheckCircle2, Shield, Globe, Cpu,
  TrendingUp, Layers, Hexagon, Activity, Radar, Orbit,
  Fingerprint, ArrowRight
} from 'lucide-react';
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

/* ============ 粒子背景 Canvas ============ */
function ParticleBackground() {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    let animId: number;
    let w = 0, h = 0;

    const resize = () => {
      w = canvas.width = window.innerWidth;
      h = canvas.height = window.innerHeight;
    };
    resize();
    window.addEventListener('resize', resize);

    const particles: { x: number; y: number; vx: number; vy: number; size: number; alpha: number }[] = [];
    for (let i = 0; i < 80; i++) {
      particles.push({
        x: Math.random() * w,
        y: Math.random() * h,
        vx: (Math.random() - 0.5) * 0.3,
        vy: (Math.random() - 0.5) * 0.3,
        size: Math.random() * 1.5 + 0.5,
        alpha: Math.random() * 0.5 + 0.2,
      });
    }

    const draw = () => {
      ctx.clearRect(0, 0, w, h);

      // draw connections
      for (let i = 0; i < particles.length; i++) {
        for (let j = i + 1; j < particles.length; j++) {
          const dx = particles[i].x - particles[j].x;
          const dy = particles[i].y - particles[j].y;
          const dist = Math.sqrt(dx * dx + dy * dy);
          if (dist < 150) {
            const alpha = (1 - dist / 150) * 0.15;
            ctx.beginPath();
            ctx.strokeStyle = `rgba(59, 130, 246, ${alpha})`;
            ctx.lineWidth = 0.5;
            ctx.moveTo(particles[i].x, particles[i].y);
            ctx.lineTo(particles[j].x, particles[j].y);
            ctx.stroke();
          }
        }
      }

      // draw particles
      particles.forEach(p => {
        p.x += p.vx;
        p.y += p.vy;
        if (p.x < 0 || p.x > w) p.vx *= -1;
        if (p.y < 0 || p.y > h) p.vy *= -1;

        ctx.beginPath();
        ctx.arc(p.x, p.y, p.size, 0, Math.PI * 2);
        ctx.fillStyle = `rgba(59, 130, 246, ${p.alpha})`;
        ctx.fill();
      });

      animId = requestAnimationFrame(draw);
    };
    draw();

    return () => {
      cancelAnimationFrame(animId);
      window.removeEventListener('resize', resize);
    };
  }, []);

  return (
    <canvas
      ref={canvasRef}
      className="absolute inset-0 w-full h-full"
      style={{ opacity: 0.6 }}
    />
  );
}

/* ============ 扫描线装饰 ============ */
function ScanLine() {
  return (
    <div className="absolute top-0 left-0 right-0 h-px bg-gradient-to-r from-transparent via-blue-500/40 to-transparent" />
  );
}

/* ============ 浮动角标装饰 ============ */
function CornerDecoration() {
  return (
    <>
      <div className="absolute top-0 left-0 w-16 h-px bg-gradient-to-r from-blue-500/60 to-transparent" />
      <div className="absolute top-0 left-0 w-px h-16 bg-gradient-to-b from-blue-500/60 to-transparent" />
      <div className="absolute top-0 right-0 w-16 h-px bg-gradient-to-l from-blue-500/60 to-transparent" />
      <div className="absolute top-0 right-0 w-px h-16 bg-gradient-to-b from-blue-500/60 to-transparent" />
      <div className="absolute bottom-0 left-0 w-16 h-px bg-gradient-to-r from-blue-500/60 to-transparent" />
      <div className="absolute bottom-0 left-0 w-px h-16 bg-gradient-to-t from-blue-500/60 to-transparent" />
      <div className="absolute bottom-0 right-0 w-16 h-px bg-gradient-to-l from-blue-500/60 to-transparent" />
      <div className="absolute bottom-0 right-0 w-px h-16 bg-gradient-to-t from-blue-500/60 to-transparent" />
    </>
  );
}

/* ============ 玻璃态卡片 ============ */
function GlassCard({ children, className, style, onClick }: { children: React.ReactNode; className?: string; style?: React.CSSProperties; onClick?: () => void }) {
  return (
    <div
      onClick={onClick}
      style={style}
      className={cn(
        'relative overflow-hidden rounded-2xl',
        'backdrop-blur-xl',
        'bg-slate-900/40',
        'border border-blue-500/10',
        'shadow-[0_0_40px_-12px_rgba(59,130,246,0.15)]',
        className
      )}
    >
      <CornerDecoration />
      <div className="absolute inset-0 bg-gradient-to-b from-blue-500/[0.02] to-transparent pointer-events-none" />
      {children}
    </div>
  );
}

/* ============ 主页面 ============ */
export default function LoginPage() {
  const router = useRouter();
  const [step, setStep] = React.useState<Step>(() => {
    if (typeof window !== 'undefined') {
      const backToPortal = localStorage.getItem('back_to_portal');
      if (backToPortal === 'true' && localStorage.getItem('session_id')) {
        localStorage.removeItem('back_to_portal');
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

  React.useEffect(() => {
    if (step === 'portal' && !loggedInUser) {
      const username = localStorage.getItem('user_id') || '';
      const company = localStorage.getItem('user_company') || '';
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
        if (result.data.user?.id) localStorage.setItem('user_id', result.data.user.id);
        if (result.data.user?.username) localStorage.setItem('username', result.data.user.username);
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

  // ========== Shared background ==========
  const Background = () => (
    <div className="fixed inset-0 bg-[#060b14] overflow-hidden">
      {/* 深层渐变 */}
      <div className="absolute inset-0 bg-gradient-to-br from-[#060b14] via-[#0a1628] to-[#060b14]" />
      {/* 光晕 */}
      <div className="absolute top-[-20%] right-[-10%] w-[700px] h-[700px] bg-blue-600/[0.07] rounded-full blur-[150px]" />
      <div className="absolute bottom-[-20%] left-[-10%] w-[600px] h-[600px] bg-cyan-600/[0.05] rounded-full blur-[150px]" />
      <div className="absolute top-[40%] left-[50%] w-[400px] h-[400px] bg-indigo-600/[0.04] rounded-full blur-[120px]" />
      {/* 网格 */}
      <div className="absolute inset-0 opacity-[0.03]" style={{
        backgroundImage: 'linear-gradient(rgba(59,130,246,0.5) 1px, transparent 1px), linear-gradient(90deg, rgba(59,130,246,0.5) 1px, transparent 1px)',
        backgroundSize: '60px 60px'
      }} />
      {/* 粒子 */}
      <ParticleBackground />
      {/* 扫描线 */}
      <div className="absolute top-0 left-0 right-0 h-px bg-gradient-to-r from-transparent via-blue-500/30 to-transparent" />
      <div className="absolute bottom-0 left-0 right-0 h-px bg-gradient-to-r from-transparent via-blue-500/20 to-transparent" />
    </div>
  );

  // ========== Step 1: 登录 ==========
  if (step === 'login') {
    return (
      <div className="relative min-h-screen flex items-center justify-center p-4">
        <Background />
        <Toaster position="top-center" richColors closeButton />

        <div className="relative z-10 w-full max-w-[420px]">
          {/* Logo 区 */}
          <div className="flex flex-col items-center mb-10">
            <div className="relative mb-5">
              <div className="w-16 h-16 rounded-2xl bg-gradient-to-br from-blue-500 to-cyan-500 flex items-center justify-center shadow-[0_0_30px_-5px_rgba(59,130,246,0.5)]">
                <Hexagon className="w-8 h-8 text-white" strokeWidth={1.5} />
              </div>
              <div className="absolute -inset-1 rounded-2xl bg-gradient-to-r from-blue-500 to-cyan-500 opacity-20 blur-md animate-pulse" />
            </div>
            <h1 className="text-2xl font-bold text-white tracking-tight">盈云产品智能中台</h1>
            <p className="text-blue-400/50 text-xs mt-1.5 tracking-widest uppercase font-medium">AI Data Intelligence Platform</p>
          </div>

          {/* 登录卡片 */}
          <GlassCard className="p-8">
            <ScanLine />

            <div className="mb-8">
              <h2 className="text-lg font-semibold text-white flex items-center gap-2">
                <Fingerprint className="w-4 h-4 text-blue-400" />
                身份验证
              </h2>
              <p className="text-slate-500 text-xs mt-1">请输入您的账号信息以继续</p>
            </div>

            <form onSubmit={handleLogin} className="space-y-5">
              {/* 用户名 */}
              <div>
                <label className="block text-[11px] font-medium text-blue-400/70 uppercase tracking-wider mb-2">用户名</label>
                <div className={cn(
                  'relative rounded-xl border transition-all duration-300',
                  focusedField === 'username'
                    ? 'border-blue-500/50 bg-slate-800/60 shadow-[0_0_15px_-3px_rgba(59,130,246,0.2)]'
                    : 'border-blue-500/10 bg-slate-800/40 hover:border-blue-500/25'
                )}>
                  <div className={cn(
                    'absolute left-3.5 top-1/2 -translate-y-1/2 transition-colors duration-300',
                    focusedField === 'username' ? 'text-blue-400' : 'text-slate-600'
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
                    className="w-full pl-10 pr-4 py-3 rounded-xl bg-transparent focus:outline-none placeholder:text-slate-600 text-slate-200 text-sm"
                  />
                </div>
              </div>

              {/* 密码 */}
              <div>
                <label className="block text-[11px] font-medium text-blue-400/70 uppercase tracking-wider mb-2">密码</label>
                <div className={cn(
                  'relative rounded-xl border transition-all duration-300',
                  focusedField === 'password'
                    ? 'border-blue-500/50 bg-slate-800/60 shadow-[0_0_15px_-3px_rgba(59,130,246,0.2)]'
                    : 'border-blue-500/10 bg-slate-800/40 hover:border-blue-500/25'
                )}>
                  <div className={cn(
                    'absolute left-3.5 top-1/2 -translate-y-1/2 transition-colors duration-300',
                    focusedField === 'password' ? 'text-blue-400' : 'text-slate-600'
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
                    className="w-full pl-10 pr-10 py-3 rounded-xl bg-transparent focus:outline-none placeholder:text-slate-600 text-slate-200 text-sm"
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword(!showPassword)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-600 hover:text-blue-400 transition-colors p-0.5"
                  >
                    {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                  </button>
                </div>
              </div>

              {/* 记住我 */}
              <div className="flex items-center justify-between">
                <label className="flex items-center cursor-pointer group">
                  <div className={cn(
                    'w-4 h-4 rounded border flex items-center justify-center transition-all duration-300',
                    rememberMe
                      ? 'bg-blue-500 border-blue-500 shadow-[0_0_8px_rgba(59,130,246,0.4)]'
                      : 'border-slate-600 group-hover:border-blue-500/50'
                  )}>
                    {rememberMe && (
                      <svg className="w-2.5 h-2.5 text-white" viewBox="0 0 12 12" fill="none">
                        <path d="M2 6L5 9L10 3" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                      </svg>
                    )}
                  </div>
                  <input type="checkbox" checked={rememberMe} onChange={(e) => setRememberMe(e.target.checked)} className="sr-only" />
                  <span className="ml-2 text-xs text-slate-500 group-hover:text-slate-300 transition-colors">记住我</span>
                </label>
                <span className="text-[10px] text-slate-600">7天免登录</span>
              </div>

              {/* 登录按钮 */}
              <Button
                type="submit"
                disabled={isLoading}
                className={cn(
                  'w-full py-3 h-auto text-white font-semibold text-sm rounded-xl',
                  'bg-gradient-to-r from-blue-600 to-cyan-600',
                  'shadow-[0_0_20px_-5px_rgba(59,130,246,0.4)]',
                  'hover:shadow-[0_0_30px_-3px_rgba(59,130,246,0.6)]',
                  'hover:from-blue-500 hover:to-cyan-500',
                  'active:scale-[0.98]',
                  'transition-all duration-300',
                  'disabled:opacity-50 disabled:cursor-not-allowed'
                )}
              >
                {isLoading ? (
                  <>
                    <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                    验证中...
                  </>
                ) : (
                  <span className="flex items-center gap-2">
                    登 录
                    <ArrowRight className="w-4 h-4" />
                  </span>
                )}
              </Button>
            </form>

            {/* 注册链接 */}
            <div className="mt-6 text-center">
              <button
                type="button"
                onClick={() => router.push('/register')}
                className="text-xs text-slate-600 hover:text-blue-400 transition-colors"
              >
                没有账号？<span className="font-semibold">立即注册</span>
              </button>
            </div>

            {/* 安全提示 */}
            <div className="mt-6 pt-5 border-t border-blue-500/10 flex items-center justify-center gap-6 text-[10px] text-slate-700">
              <span className="flex items-center gap-1.5"><Shield className="w-3 h-3 text-blue-500/50" /> 加密传输</span>
              <span className="flex items-center gap-1.5"><Globe className="w-3 h-3 text-blue-500/50" /> 安全连接</span>
              <span className="flex items-center gap-1.5"><Activity className="w-3 h-3 text-blue-500/50" /> 实时监控</span>
            </div>
          </GlassCard>

          {/* 底部信息 */}
          <div className="mt-8 text-center">
            <div className="flex items-center justify-center gap-6 text-[10px] text-slate-700">
              <span className="flex items-center gap-1"><Cpu className="w-3 h-3 text-blue-500/40" /> AI 驱动</span>
              <span className="flex items-center gap-1"><TrendingUp className="w-3 h-3 text-blue-500/40" /> 供应链</span>
              <span className="flex items-center gap-1"><Layers className="w-3 h-3 text-blue-500/40" /> 多品牌</span>
            </div>
            <p className="text-[10px] text-slate-800 mt-3"> 2024 盈云产品智能中台 · v2.0</p>
          </div>
        </div>
      </div>
    );
  }

  // ========== Step 2: 选择公司 ==========
  if (step === 'company') {
    return (
      <div className="relative min-h-screen flex items-center justify-center p-4">
        <Background />
        <Toaster position="top-center" richColors closeButton />

        <div className="relative z-10 w-full max-w-[560px]">
          {/* 标题区 */}
          <div className="text-center mb-10">
            <div className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full bg-blue-500/10 border border-blue-500/20 mb-5">
              <Building2 className="w-3.5 h-3.5 text-blue-400" />
              <span className="text-xs text-blue-400 font-medium">公司绑定</span>
            </div>
            <h1 className="text-2xl font-bold text-white tracking-tight">选择您的所属公司</h1>
            <p className="text-slate-500 text-xs mt-2">此选择将永久绑定到您的账号，绑定后不可更改</p>
          </div>

          {/* 公司卡片 */}
          <div className="space-y-4">
            {COMPANY_OPTIONS.map((company) => {
              const isBonasi = company.key === 'bonasi';
              const Icon = isBonasi ? Scissors : Cloud;
              const accentFrom = isBonasi ? 'from-rose-500' : 'from-blue-500';
              const accentTo = isBonasi ? 'to-pink-600' : 'to-cyan-500';
              const glowColor = isBonasi ? 'rgba(244,63,94,0.3)' : 'rgba(59,130,246,0.3)';
              const borderHover = isBonasi ? 'hover:border-rose-500/30' : 'hover:border-blue-500/30';

              return (
                <GlassCard
                  key={company.key}
                  className={cn(
                    'p-6 cursor-pointer group transition-all duration-500',
                    'hover:shadow-[0_0_40px_-10px_var(--glow)]',
                    borderHover
                  )}
                  style={{ '--glow': glowColor } as React.CSSProperties}
                  onClick={() => handleSelectCompany(company.key)}
                >
                  <ScanLine />
                  <div className="flex items-center gap-5">
                    <div className={cn(
                      'w-14 h-14 rounded-xl bg-gradient-to-br flex items-center justify-center shadow-lg flex-shrink-0',
                      accentFrom, accentTo
                    )}>
                      <Icon className="w-7 h-7 text-white" />
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2">
                        <h2 className="text-base font-bold text-white">{company.fullName}</h2>
                        <span className="text-[10px] text-amber-400/80 bg-amber-400/10 px-1.5 py-0.5 rounded border border-amber-400/20">
                          不可更改
                        </span>
                      </div>
                      <p className="text-xs text-slate-500 mt-1">{company.description}</p>
                      <div className="flex gap-2 mt-2.5">
                        <span className="text-[10px] text-slate-600 bg-slate-800/60 px-2 py-0.5 rounded border border-slate-700/50">{isBonasi ? '无缝针织' : 'AI智能'}</span>
                        <span className="text-[10px] text-slate-600 bg-slate-800/60 px-2 py-0.5 rounded border border-slate-700/50">{isBonasi ? '品质制造' : '数字化转型'}</span>
                      </div>
                    </div>
                    <div className="flex-shrink-0">
                      <div className={cn(
                        'w-10 h-10 rounded-full border border-blue-500/20 flex items-center justify-center',
                        'group-hover:border-blue-500/50 group-hover:bg-blue-500/10 transition-all duration-300'
                      )}>
                        <ChevronRight className="w-4 h-4 text-slate-500 group-hover:text-blue-400 group-hover:translate-x-0.5 transition-all duration-300" />
                      </div>
                    </div>
                  </div>
                </GlassCard>
              );
            })}
          </div>

          {/* 返回 */}
          <button
            type="button"
            onClick={() => setStep('login')}
            className="mt-8 flex items-center gap-2 text-xs text-slate-600 hover:text-blue-400 transition-colors mx-auto"
          >
            <ArrowLeft className="w-3.5 h-3.5" />
            返回登录
          </button>
        </div>
      </div>
    );
  }

  // ========== Step 3: 选择入口 ==========
  const Icon = selectedBrand === 'bonasi' ? Scissors : Cloud;
  const companyName = brand.name;
  const accentFrom = selectedBrand === 'bonasi' ? 'from-rose-500' : 'from-blue-500';
  const accentTo = selectedBrand === 'bonasi' ? 'to-pink-600' : 'to-cyan-500';

  const portalCards = [
    {
      key: 'designer' as PortalType,
      icon: Palette,
      title: '设计师入口',
      desc: '知识库管理 · 图片上传 · AI识别 · 文档中心',
      gradient: 'from-violet-500 to-fuchsia-600',
      glow: 'rgba(139,92,246,0.3)',
      borderHover: 'hover:border-violet-500/30',
      iconBg: 'bg-violet-500/10',
      iconColor: 'text-violet-400',
    },
    {
      key: 'factory' as PortalType,
      icon: Factory,
      title: '工厂 / 供应链入口',
      desc: '产品报价 · 原料管理 · 生产计划 · 辅料采购',
      gradient: 'from-amber-500 to-orange-600',
      glow: 'rgba(245,158,11,0.3)',
      borderHover: 'hover:border-amber-500/30',
      iconBg: 'bg-amber-500/10',
      iconColor: 'text-amber-400',
    },
    {
      key: 'marketing' as PortalType,
      icon: Megaphone,
      title: '市场营销 AI 入口',
      desc: '营销策略 · 市场分析 · 文案生成 · 行业洞察',
      gradient: 'from-emerald-500 to-teal-600',
      glow: 'rgba(16,185,129,0.3)',
      borderHover: 'hover:border-emerald-500/30',
      iconBg: 'bg-emerald-500/10',
      iconColor: 'text-emerald-400',
    },
  ];

  return (
    <div className="relative min-h-screen flex items-center justify-center p-4">
      <Background />
      <Toaster position="top-center" richColors closeButton />

      <div className="relative z-10 w-full max-w-[680px]">
        {/* 用户信息 */}
        <div className="text-center mb-10">
          <div className={cn(
            'w-14 h-14 mx-auto rounded-2xl bg-gradient-to-br flex items-center justify-center shadow-lg mb-4',
            accentFrom, accentTo
          )}>
            <Icon className="w-7 h-7 text-white" />
          </div>
          <h1 className="text-2xl font-bold text-white tracking-tight">{companyName}</h1>
          <p className="text-slate-500 text-xs mt-1">企业数智中台系统</p>
          {loggedInUser?.user?.username && (
            <div className="inline-flex items-center gap-2 mt-3 px-3 py-1.5 rounded-full bg-emerald-500/10 border border-emerald-500/20">
              <CheckCircle2 className="w-3 h-3 text-emerald-400" />
              <span className="text-xs text-emerald-400 font-medium">{loggedInUser.user.username} 已登录</span>
            </div>
          )}
        </div>

        {/* 入口卡片 - 2列布局 */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          {portalCards.map((card) => {
            const CardIcon = card.icon;
            return (
              <GlassCard
                key={card.key}
                className={cn(
                  'p-5 cursor-pointer group transition-all duration-500',
                  'hover:shadow-[0_0_40px_-10px_var(--glow)]',
                  'hover:-translate-y-1',
                  card.borderHover
                )}
                style={{ '--glow': card.glow } as React.CSSProperties}
                onClick={() => handleSelectPortal(card.key)}
              >
                <ScanLine />
                <div className="flex flex-col items-center text-center">
                  <div className={cn(
                    'w-12 h-12 rounded-xl bg-gradient-to-br flex items-center justify-center shadow-lg mb-4',
                    card.gradient
                  )}>
                    <CardIcon className="w-6 h-6 text-white" />
                  </div>
                  <h2 className="text-sm font-bold text-white mb-1.5">{card.title}</h2>
                  <p className="text-[11px] text-slate-500 leading-relaxed">{card.desc}</p>
                  <div className="mt-4 flex items-center gap-1 text-[11px] text-blue-400/70 group-hover:text-blue-400 transition-colors">
                    <span>进入</span>
                    <ArrowRight className="w-3 h-3 group-hover:translate-x-0.5 transition-transform duration-300" />
                  </div>
                </div>
              </GlassCard>
            );
          })}
        </div>

        {/* 返回 */}
        <button
          type="button"
          onClick={() => setStep(loggedInUser?.user?.company ? 'login' : 'company')}
          className="mt-8 flex items-center gap-2 text-xs text-slate-600 hover:text-blue-400 transition-colors mx-auto"
        >
          <ArrowLeft className="w-3.5 h-3.5" />
          返回上一步
        </button>
      </div>
    </div>
  );
}
