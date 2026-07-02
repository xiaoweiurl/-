'use client';

import React, { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { Eye, EyeOff, ArrowRight, Loader2, Building2, ChevronRight, Check } from 'lucide-react';
import { BrandSidebar, inputClass, primaryBtnClass, Divider, PasswordStrength } from '@/components/AuthVisuals';
import { COMPANY_OPTIONS } from '@/lib/brand';
type CompanyOption = typeof COMPANY_OPTIONS[number];

export default function RegisterPage() {
  const router = useRouter();
  const [step, setStep] = useState<'company' | 'form'>('company');
  const [selectedCompany, setSelectedCompany] = useState<CompanyOption | null>(null);
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPwd, setConfirmPwd] = useState('');
  const [showPwd, setShowPwd] = useState(false);
  const [showConfirmPwd, setShowConfirmPwd] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);

  /* 选择公司后进入表单 */
  const handleSelectCompany = (company: CompanyOption) => {
    setSelectedCompany(company);
    setStep('form');
  };

  /* 注册 */
  const handleRegister = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (!username.trim()) { setError('请输入用户名'); return; }
    if (username.trim().length < 3) { setError('用户名至少 3 个字符'); return; }
    if (!password) { setError('请输入密码'); return; }
    if (password.length < 6) { setError('密码至少 6 个字符'); return; }
    if (password !== confirmPwd) { setError('两次密码不一致'); return; }

    setLoading(true);
    try {
      const r = await fetch('/api/auth/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          username: username.trim(),
          password,
          company: selectedCompany?.key,
        }),
      });
      const d = await r.json();
      if (!r.ok) { setError(d.error || '注册失败'); return; }
      setSuccess(true);
      setTimeout(() => router.replace('/login'), 2000);
    } catch { setError('网络错误，请重试'); }
    finally { setLoading(false); }
  };

  /* ============ 渲染 ============ */
  return (
    <div className="min-h-screen flex bg-slate-50">
      <BrandSidebar />

      {/* 右侧表单区 */}
      <div className="flex-1 flex items-center justify-center p-6 sm:p-10">
        <div className="w-full max-w-md">
          {step === 'company' && renderCompany()}
          {step === 'form' && renderForm()}
        </div>
      </div>
    </div>
  );

  /* ---------- 公司选择 ---------- */
  function renderCompany() {
    return (
      <div>
        {/* 移动端 Logo */}
        <div className="lg:hidden flex items-center gap-2.5 mb-10">
          <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-blue-600 to-indigo-600 flex items-center justify-center shadow-lg shadow-blue-500/20">
            <span className="text-lg font-bold text-white">盈</span>
          </div>
          <span className="text-lg font-bold text-slate-800">盈云中台</span>
        </div>

        <h2 className="text-2xl font-bold text-slate-900 mb-1.5">创建账户</h2>
        <p className="text-sm text-slate-500 mb-8">首先选择您所属的公司</p>

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

        <p className="mt-8 text-sm text-slate-500 text-center">
          已有账户？{' '}
          <a href="/login" className="text-blue-600 hover:text-blue-700 font-medium">立即登录</a>
        </p>
      </div>
    );
  }

  /* ---------- 注册表单 ---------- */
  function renderForm() {
    if (success) {
      return (
        <div className="text-center py-10">
          <div className="w-16 h-16 mx-auto mb-4 rounded-full bg-green-50 flex items-center justify-center">
            <Check className="w-8 h-8 text-green-500" />
          </div>
          <h2 className="text-xl font-bold text-slate-900 mb-2">注册成功</h2>
          <p className="text-sm text-slate-500">即将跳转到登录页面…</p>
        </div>
      );
    }

    return (
      <div>
        {/* 移动端 Logo */}
        <div className="lg:hidden flex items-center gap-2.5 mb-10">
          <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-blue-600 to-indigo-600 flex items-center justify-center shadow-lg shadow-blue-500/20">
            <span className="text-lg font-bold text-white">盈</span>
          </div>
          <span className="text-lg font-bold text-slate-800">盈云中台</span>
        </div>

        <button
          onClick={() => { setStep('company'); setError(''); }}
          className="flex items-center gap-1 text-sm text-slate-500 hover:text-slate-700 mb-6 transition-colors"
        >
          ← 返回选择公司
        </button>

        <h2 className="text-2xl font-bold text-slate-900 mb-1.5">注册账户</h2>
        <p className="text-sm text-slate-500 mb-1">
          {selectedCompany?.name && <span className="inline-flex items-center gap-1.5 px-2 py-0.5 rounded-md bg-blue-50 text-blue-600 text-xs font-medium mr-1">{selectedCompany.name}</span>}
          填写以下信息完成注册
        </p>
        <div className="mb-6" />

        <form onSubmit={handleRegister} className="space-y-4">
          {/* 用户名 */}
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1.5">用户名</label>
            <input
              type="text"
              value={username}
              onChange={e => setUsername(e.target.value)}
              placeholder="至少 3 个字符"
              className={inputClass}
              autoComplete="username"
            />
          </div>

          {/* 密码 */}
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1.5">密码</label>
            <div className="relative">
              <input
                type={showPwd ? 'text' : 'password'}
                value={password}
                onChange={e => setPassword(e.target.value)}
                placeholder="至少 6 个字符"
                className={cn(inputClass, 'pr-10')}
                autoComplete="new-password"
              />
              <button
                type="button"
                onClick={() => setShowPwd(!showPwd)}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
              >
                {showPwd ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
              </button>
            </div>
            <PasswordStrength password={password} />
          </div>

          {/* 确认密码 */}
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1.5">确认密码</label>
            <div className="relative">
              <input
                type={showConfirmPwd ? 'text' : 'password'}
                value={confirmPwd}
                onChange={e => setConfirmPwd(e.target.value)}
                placeholder="再次输入密码"
                className={cn(inputClass, 'pr-10')}
                autoComplete="new-password"
              />
              <button
                type="button"
                onClick={() => setShowConfirmPwd(!showConfirmPwd)}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
              >
                {showConfirmPwd ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
              </button>
            </div>
            {confirmPwd && password !== confirmPwd && (
              <p className="text-xs text-red-500 mt-1">两次密码不一致</p>
            )}
          </div>

          {error && (
            <div className="flex items-center gap-2 p-3 rounded-lg bg-red-50 border border-red-100 text-sm text-red-600">
              <span className="w-1.5 h-1.5 rounded-full bg-red-400 shrink-0" />
              {error}
            </div>
          )}

          <button type="submit" disabled={loading} className={primaryBtnClass + ' flex items-center justify-center gap-2'}>
            {loading ? <Loader2 className="w-4 h-4 animate-spin" /> : <>注册 <ArrowRight className="w-4 h-4" /></>}
          </button>
        </form>

        <Divider text="或" />

        <p className="text-center text-sm text-slate-500">
          已有账户？{' '}
          <a href="/login" className="text-blue-600 hover:text-blue-700 font-medium">立即登录</a>
        </p>
      </div>
    );
  }
}

function cn(...args: (string | undefined | false)[]) {
  return args.filter(Boolean).join(' ');
}
