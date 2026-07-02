'use client';

import React, { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { Eye, EyeOff, ArrowRight, Loader2 } from 'lucide-react';
import { BrandSidebar, inputClass, primaryBtnClass, Divider } from '@/components/AuthVisuals';

export default function LoginPage() {
  const router = useRouter();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPwd, setShowPwd] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  /* 检查已登录 → 直接跳转首页 */
  useEffect(() => {
    (async () => {
      try {
        const r = await fetch('/api/auth/login');
        const d = await r.json();
        if (d?.success || d?.loggedIn) {
          router.replace('/');
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
      if (!r.ok || !d.success) { setError(d.error || d.message || '登录失败'); return; }
      /* 登录成功 → 直接跳转首页 */
      router.replace('/');
    } catch { setError('网络错误，请重试'); }
    finally { setLoading(false); }
  };

  return (
    <div className="min-h-screen flex bg-slate-50">
      <BrandSidebar />

      {/* 右侧表单区 */}
      <div className="flex-1 flex items-center justify-center p-6 sm:p-10">
        <div className="w-full max-w-md">
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
                  className={inputClass + ' pr-10'}
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
      </div>
    </div>
  );
}
