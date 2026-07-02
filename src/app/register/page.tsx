'use client';

import React from 'react';
import { useRouter } from 'next/navigation';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import {
  User, Lock, Eye, EyeOff, Loader2, ArrowLeft, Mail, Building2,
  ChevronRight, Shield, Globe, Cpu
} from 'lucide-react';
import { toast } from 'sonner';
import { Toaster } from '@/components/ui/sonner';
import {
  AuthShell, GlassCard, HexagonLogo, SecurityBadges, DataFlowDecoration
} from '@/components/AuthVisuals';

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

type CompanyType = '宝娜斯' | '盈云' | null;

/* ============ 公司选择卡片 ============ */
function CompanyCard({
  company,
  label,
  desc,
  icon,
  gradient,
  glowColor,
  onClick,
}: {
  company: string;
  label: string;
  desc: string;
  icon: React.ReactNode;
  gradient: string;
  glowColor: string;
  onClick: () => void;
}) {
  return (
    <button
      onClick={onClick}
      className={cn(
        'group relative w-full text-left',
        'rounded-2xl border border-blue-500/10',
        'bg-slate-900/40 backdrop-blur-xl',
        'p-8 transition-all duration-500',
        'hover:border-blue-500/30 hover:-translate-y-1',
        `hover:shadow-[0_0_30px_-8px_${glowColor}]`
      )}
    >
      {/* 顶部渐变条 */}
      <div className={cn('absolute top-0 left-0 right-0 h-0.5 bg-gradient-to-r from-transparent via-blue-500/40 to-transparent opacity-0 group-hover:opacity-100 transition-opacity')} />

      <div className="flex items-start gap-5">
        <div className={cn(
          'w-16 h-16 rounded-xl flex items-center justify-center shrink-0',
          'bg-gradient-to-br border',
          gradient
        )}>
          {icon}
        </div>
        <div className="flex-1 min-w-0">
          <h3 className="text-lg font-bold text-slate-100 mb-1">{company}</h3>
          <p className="text-sm text-slate-400 leading-relaxed mb-4">{desc}</p>
          <div className="flex items-center text-blue-400 text-sm font-medium group-hover:translate-x-1 transition-transform">
            选择{label}
            <ChevronRight className="w-4 h-4 ml-1" />
          </div>
        </div>
      </div>
    </button>
  );
}

/* ============ 输入框组件 ============ */
function AuthInput({
  icon: Icon,
  type = 'text',
  value,
  onChange,
  placeholder,
  label,
  rightElement,
}: {
  icon: React.ComponentType<{ className?: string }>;
  type?: string;
  value: string;
  onChange: (val: string) => void;
  placeholder: string;
  label: string;
  rightElement?: React.ReactNode;
}) {
  return (
    <div>
      <label className="block text-xs font-medium text-slate-400 mb-2 tracking-wide uppercase">
        {label}
      </label>
      <div className="relative">
        <div className="absolute left-3.5 top-1/2 -translate-y-1/2 text-blue-500/40">
          <Icon className="w-5 h-5" />
        </div>
        <input
          type={type}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          placeholder={placeholder}
          className={cn(
            'w-full pl-11 pr-4 py-3 rounded-xl',
            'bg-slate-900/50 border border-blue-500/10',
            'text-slate-200 placeholder:text-slate-600',
            'focus:outline-none focus:border-blue-500/30 focus:shadow-[0_0_15px_-3px_rgba(59,130,246,0.2)]',
            'transition-all duration-200'
          )}
        />
        {rightElement && (
          <div className="absolute right-3 top-1/2 -translate-y-1/2">
            {rightElement}
          </div>
        )}
      </div>
    </div>
  );
}

/* ============ 主页面 ============ */
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

  // ============ 公司选择页面 ============
  if (!company) {
    return (
      <AuthShell>
        <Toaster position="top-center" richColors closeButton />

        <div className="relative z-10 w-full max-w-2xl">
          {/* Logo */}
          <div className="text-center mb-12">
            <HexagonLogo text="创" className="mb-6" />
            <h1 className="text-3xl font-bold text-slate-100 tracking-tight">
              创建新账号
            </h1>
            <p className="text-sm text-slate-500 mt-2">
              请选择您所属的公司以继续注册
            </p>
          </div>

          {/* 公司卡片 */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
            <CompanyCard
              company="宝娜斯"
              label="宝娜斯"
              desc="宝娜斯品牌用户，管理专属知识库与文档资源"
              icon={<span className="text-2xl font-bold text-rose-300">宝</span>}
              gradient="from-rose-500/10 to-pink-500/10 border-rose-500/20"
              glowColor="rgba(244,63,94,0.3)"
              onClick={() => setCompany('宝娜斯')}
            />
            <CompanyCard
              company="盈云"
              label="盈云"
              desc="盈云品牌用户，管理专属知识库与文档资源"
              icon={<span className="text-2xl font-bold text-blue-300">盈</span>}
              gradient="from-blue-500/10 to-cyan-500/10 border-blue-500/20"
              glowColor="rgba(59,130,246,0.3)"
              onClick={() => setCompany('盈云')}
            />
          </div>

          {/* 返回登录 */}
          <div className="mt-10 text-center">
            <button
              type="button"
              onClick={() => router.push('/login')}
              className="text-sm text-slate-500 hover:text-slate-300 flex items-center justify-center gap-1.5 transition-colors mx-auto"
            >
              <ArrowLeft className="w-4 h-4" />
              已有账号？返回登录
            </button>
          </div>

          <div className="mt-8">
            <SecurityBadges />
          </div>
        </div>
      </AuthShell>
    );
  }

  // ============ 注册表单页面 ============
  const isBonasi = company === '宝娜斯';
  const accentColor = isBonasi ? 'rose' : 'blue';
  const accentClass = isBonasi ? 'text-rose-400' : 'text-blue-400';

  return (
    <AuthShell>
      <Toaster position="top-center" richColors closeButton />

      <div className="relative z-10 w-full max-w-md">
        {/* Logo */}
        <div className="text-center mb-8">
          <HexagonLogo text={isBonasi ? '宝' : '盈'} className="mb-5" />
          <h1 className="text-2xl font-bold text-slate-100 tracking-tight">
            {company}
          </h1>
          <p className="text-sm text-slate-500 mt-1">
            创建{company}专属账号
          </p>
        </div>

        {/* 注册卡片 */}
        <GlassCard className="p-8">
          <form onSubmit={handleRegister} className="space-y-5">
            {/* 所属公司展示 */}
            <div className="flex items-center gap-3 p-3 rounded-xl bg-slate-800/50 border border-blue-500/10">
              <Building2 className="w-5 h-5 text-blue-500/40" />
              <span className="text-sm text-slate-400">所属公司</span>
              <span className={cn('text-sm font-medium ml-auto', accentClass)}>
                {company}
              </span>
            </div>

            {/* 用户名 */}
            <AuthInput
              icon={User}
              value={username}
              onChange={setUsername}
              placeholder="请输入用户名"
              label="用户名"
            />

            {/* 邮箱 */}
            <AuthInput
              icon={Mail}
              type="email"
              value={email}
              onChange={setEmail}
              placeholder="请输入邮箱"
              label="邮箱"
            />

            {/* 密码 */}
            <AuthInput
              icon={Lock}
              type={showPassword ? 'text' : 'password'}
              value={password}
              onChange={setPassword}
              placeholder="请输入密码（至少6位）"
              label="密码"
              rightElement={
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="text-slate-500 hover:text-slate-300 transition-colors"
                >
                  {showPassword ? <EyeOff className="w-5 h-5" /> : <Eye className="w-5 h-5" />}
                </button>
              }
            />

            {/* 确认密码 */}
            <AuthInput
              icon={Lock}
              type={showPassword ? 'text' : 'password'}
              value={confirmPassword}
              onChange={setConfirmPassword}
              placeholder="请再次输入密码"
              label="确认密码"
            />

            {/* 注册按钮 */}
            <Button
              type="submit"
              disabled={isLoading}
              className={cn(
                'w-full py-3 text-white font-medium rounded-xl',
                'bg-gradient-to-r from-blue-600 to-cyan-600',
                'shadow-lg shadow-blue-500/20',
                'hover:shadow-blue-500/30 hover:scale-[1.02]',
                'transition-all duration-200',
                'disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:scale-100'
              )}
            >
              {isLoading ? (
                <>
                  <Loader2 className="w-5 h-5 mr-2 animate-spin" />
                  注册中...
                </>
              ) : (
                <>
                  创建账号
                  <ChevronRight className="w-4 h-4 ml-1" />
                </>
              )}
            </Button>
          </form>

          {/* 返回公司选择 */}
          <div className="mt-5 pt-5 border-t border-blue-500/10">
            <button
              type="button"
              onClick={() => setCompany(null)}
              className="w-full py-2 text-sm text-slate-500 hover:text-slate-300 flex items-center justify-center gap-1.5 transition-colors"
            >
              <ArrowLeft className="w-4 h-4" />
              重新选择公司
            </button>
          </div>
        </GlassCard>

        {/* 返回登录 */}
        <div className="mt-6 text-center">
          <button
            type="button"
            onClick={() => router.push('/login')}
            className="text-sm text-slate-500 hover:text-slate-300 transition-colors"
          >
            已有账号？<span className="text-blue-400 hover:text-blue-300">立即登录</span>
          </button>
        </div>

        <div className="mt-8">
          <SecurityBadges />
        </div>

        <div className="mt-6">
          <DataFlowDecoration />
        </div>
      </div>
    </AuthShell>
  );
}
