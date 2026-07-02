'use client';

import React, { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { Eye, EyeOff, ArrowRight, Loader2, Building2, ChevronRight, Palette, Factory, TrendingUp } from 'lucide-react';
import { BrandSidebar, inputClass, primaryBtnClass, Divider } from '@/components/AuthVisuals';
import { COMPANY_OPTIONS } from '@/lib/brand';
type CompanyOption = typeof COMPANY_OPTIONS[number];

const PORTALS = [
  { key: 'designer', label: '设计师平台', icon: Palette, color: 'from-violet-500 to-purple-600', bgLight: 'bg-violet-50', textLight: 'text-violet-600', desc: '款式设计·AI识别·素材管理' },
  { key: 'factory', label: '工厂平台', icon: Factory, color: 'from-amber-500 to-orange-600', bgLight: 'bg-amber-50', textLight: 'text-amber-600', desc: '生产计划·原料采购·质量控制' },
  { key: 'marketing', label: '市场营销平台', icon: TrendingUp, color: 'from-emerald-500 to-teal-600', bgLight: 'bg-emerald-50', textLight: 'text-emerald-600', desc: '商品管理·渠道分析·智能报价' },
];

export default function LoginPage() {
  const router = useRouter();
  const [step, setStep] = useState<'login' | 'company' | 'portal'>('login');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPwd, setShowPwd] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [selectedCompany, setSelectedCompany] = useState<CompanyOption | null>(null);

  /* 检查已登录 */
  useEffect(() => {
    (async () => {
      try {
        const r = await fetch('/api/auth/login');
        const d = await r.json();
        if (d?.loggedIn) {
          if (d.company && d.portal) router.replace('/');
          else if (d.company) setStep('portal');
          else setStep('company');
        }
      } catch {}
    })();
  }, [router]);

  /* 登录 */
  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!username.trim() || !password.trim()) { setError('请输入用户名和密码'); return; }
    setLoading(true); setError('');
    try {
      const r = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: username.trim(), password }),
      });
      const d = await r.json();
      if (!r.ok) { setError(d.error || '登录失败'); return; }
      if (d.company && d.portal) { router.replace('/'); return; }
      setStep('company');
    } catch { setError('网络错误，请重试'); }
    finally { setLoading(false); }
  };

  /* 选择公司 */
  const handleSelectCompany = (company: CompanyOption) => {
    setSelectedCompany(company);
    document.cookie = `company=${company.key}; path=/; max-age=${30 * 86400}; SameSite=Lax`;
    setStep('portal');
  };

  /* 选择门户 */
  const handleSelectPortal = (portal: string) => {
    document.cookie = `portal=${portal}; path=/; max-age=${30 * 86400}; SameSite=Lax`;
    router.replace('/');
  };

  /* ============ 渲染 ============ */
  return (
    <div className="min-h-screen flex bg-slate-50">
      <BrandSidebar />

      {/* 右侧表单区 */}
      <div className="flex-1 flex items-center justify-center p-6 sm:p-10">
        <div className="w-full max-w-md">
          {step === 'login' && renderLogin()}
          {step === 'company' && renderCompany()}
          {step === 'portal' && renderPortal()}
        </div>
      </div>
    </div>
  );

  /* ---------- 登录表单 ---------- */
  function renderLogin() {
    return (
      <div>
        {/* 移动端 Logo */}
        <div className="lg:hidden flex items-center gap-2.5 mb-10">
          <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-blue-600 to-indigo-600 flex items-center justify-center shadow-lg shadow-blue-500/20">
            <span className="text-lg font-bold text-white">盈</span>
          </div>
          <span className="text-lg font-bold text-slate-800">盈云中台</span>
        </div>

        <h2 className="text-2xl font-bold text-slate-900 mb-1.5">欢迎回来</h2>
        <p className="text-sm text-slate-500 mb-8">登录您的账户以继续</p>

        <form onSubmit={handleLogin} className="space-y-4">
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1.5">用户名</label>
            <input
              type="text"
              value={username}
              onChange={e => setUsername(e.target.value)}
              placeholder="请输入用户名"
              className={inputClass}
              autoComplete="username"
            />
          </div>

          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1.5">密码</label>
            <div className="relative">
              <input
                type={showPwd ? 'text' : 'password'}
                value={password}
                onChange={e => setPassword(e.target.value)}
                placeholder="请输入密码"
                className={cn(inputClass, 'pr-10')}
                autoComplete="current-password"
              />
              <button
                type="button"
                onClick={() => setShowPwd(!showPwd)}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
              >
                {showPwd ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
              </button>
            </div>
          </div>

          {error && (
            <div className="flex items-center gap-2 p-3 rounded-lg bg-red-50 border border-red-100 text-sm text-red-600">
              <span className="w-1.5 h-1.5 rounded-full bg-red-400 shrink-0" />
              {error}
            </div>
          )}

          <button type="submit" disabled={loading} className={primaryBtnClass + ' flex items-center justify-center gap-2'}>
            {loading ? <Loader2 className="w-4 h-4 animate-spin" /> : <>登录 <ArrowRight className="w-4 h-4" /></>}
          </button>
        </form>

        <Divider text="或" />

        <p className="text-center text-sm text-slate-500">
          还没有账户？{' '}
          <a href="/register" className="text-blue-600 hover:text-blue-700 font-medium">立即注册</a>
        </p>

        {/* 底部安全标签 */}
        <div className="flex items-center justify-center gap-5 mt-10 text-[10px] text-slate-400">
          <span className="flex items-center gap-1"><span className="w-1 h-1 rounded-full bg-green-400" />加密传输</span>
          <span className="flex items-center gap-1"><span className="w-1 h-1 rounded-full bg-blue-400" />安全连接</span>
          <span className="flex items-center gap-1"><span className="w-1 h-1 rounded-full bg-indigo-400" />实时监控</span>
        </div>
      </div>
    );
  }

  /* ---------- 公司选择 ---------- */
  function renderCompany() {
    return (
      <div>
        <h2 className="text-2xl font-bold text-slate-900 mb-1.5">选择公司</h2>
        <p className="text-sm text-slate-500 mb-8">选择您要登录的公司组织</p>

        <div className="space-y-3">
          {COMPANY_OPTIONS.map(company => (
            <button
              key={company.key}
              onClick={() => handleSelectCompany(company)}
              className="w-full flex items-center gap-4 p-4 rounded-xl bg-white border border-slate-200 hover:border-blue-300 hover:shadow-md hover:shadow-blue-500/5 transition-all duration-200 group text-left"
            >
              <div className={cn(
                'w-12 h-12 rounded-xl flex items-center justify-center shrink-0',
                company.key === 'bonasi' ? 'bg-rose-50 text-rose-500' : 'bg-blue-50 text-blue-500'
              )}>
                <Building2 className="w-5 h-5" />
              </div>
              <div className="flex-1 min-w-0">
                <h3 className="text-sm font-semibold text-slate-800 group-hover:text-blue-600 transition-colors">{company.name}</h3>
                <p className="text-xs text-slate-400 mt-0.5 truncate">{company.description}</p>
              </div>
              <ChevronRight className="w-4 h-4 text-slate-300 group-hover:text-blue-400 group-hover:translate-x-0.5 transition-all" />
            </button>
          ))}
        </div>

        <button onClick={() => setStep('login')} className="mt-8 text-sm text-slate-500 hover:text-slate-700 transition-colors">
          ← 返回登录
        </button>
      </div>
    );
  }

  /* ---------- 门户选择 ---------- */
  function renderPortal() {
    return (
      <div>
        <h2 className="text-2xl font-bold text-slate-900 mb-1.5">选择工作台</h2>
        <p className="text-sm text-slate-500 mb-8">
          {selectedCompany ? `${selectedCompany.name} · ` : ''}选择您的工作入口
        </p>

        <div className="space-y-3">
          {PORTALS.map(portal => {
            const Icon = portal.icon;
            return (
              <button
                key={portal.key}
                onClick={() => handleSelectPortal(portal.key)}
                className="w-full flex items-center gap-4 p-4 rounded-xl bg-white border border-slate-200 hover:border-slate-300 hover:shadow-md transition-all duration-200 group text-left"
              >
                <div className={cn('w-12 h-12 rounded-xl flex items-center justify-center shrink-0', portal.bgLight, portal.textLight)}>
                  <Icon className="w-5 h-5" />
                </div>
                <div className="flex-1 min-w-0">
                  <h3 className="text-sm font-semibold text-slate-800 group-hover:text-blue-600 transition-colors">{portal.label}</h3>
                  <p className="text-xs text-slate-400 mt-0.5">{portal.desc}</p>
                </div>
                <ChevronRight className="w-4 h-4 text-slate-300 group-hover:text-blue-400 group-hover:translate-x-0.5 transition-all" />
              </button>
            );
          })}
        </div>

        <button onClick={() => setStep('company')} className="mt-8 text-sm text-slate-500 hover:text-slate-700 transition-colors">
          ← 返回选择公司
        </button>
      </div>
    );
  }
}

function cn(...args: (string | undefined | false)[]) {
  return args.filter(Boolean).join(' ');
}
