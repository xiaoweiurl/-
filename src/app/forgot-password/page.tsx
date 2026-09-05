'use client';

import React from 'react';
import { useRouter } from 'next/navigation';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import {
  User,
  Lock,
  Eye,
  EyeOff,
  Loader2,
  Sparkles,
  Shield,
  Mail,
  Phone,
  ArrowLeft,
  ArrowRight,
  CheckCircle2,
  AlertCircle,
} from 'lucide-react';
import { toast } from 'sonner';
import { Toaster } from '@/components/ui/sonner';

type Step = 'username' | 'verify' | 'reset' | 'success';

interface UserInfo {
  username: string;
  maskedEmail: string;
  maskedPhone: string;
  hasEmail: boolean;
  hasPhone: boolean;
}

export default function ForgotPasswordPage() {
  const router = useRouter();
  const [step, setStep] = React.useState<Step>('username');
  const [focusedField, setFocusedField] = React.useState<string | null>(null);

  // 表单数据
  const [username, setUsername] = React.useState('');
  const [userInfo, setUserInfo] = React.useState<UserInfo | null>(null);
  const [verifyType, setVerifyType] = React.useState<'email' | 'phone'>('email');
  const [verifyValue, setVerifyValue] = React.useState('');
  const [newPassword, setNewPassword] = React.useState('');
  const [confirmPassword, setConfirmPassword] = React.useState('');
  const [showNewPassword, setShowNewPassword] = React.useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = React.useState(false);

  const [isLoading, setIsLoading] = React.useState(false);

  // 密码强度
  const passwordStrength = React.useMemo(() => {
    if (!newPassword) return { level: 0, label: '', color: '' };
    let score = 0;
    if (newPassword.length >= 6) score++;
    if (newPassword.length >= 10) score++;
    if (/[A-Z]/.test(newPassword)) score++;
    if (/[0-9]/.test(newPassword)) score++;
    if (/[^A-Za-z0-9]/.test(newPassword)) score++;
    const levels = [
      { level: 1, label: '弱', color: 'bg-[#ff3b30]' },
      { level: 2, label: '一般', color: 'bg-[#ff9500]' },
      { level: 3, label: '中等', color: 'bg-[#ff9500]' },
      { level: 4, label: '良好', color: 'bg-[#007aff]' },
      { level: 5, label: '强', color: 'bg-[#34c759]' },
    ];
    return levels[Math.min(score, 5) - 1] || { level: 0, label: '', color: '' };
  }, [newPassword]);

  // Step 1: 提交用户名，获取验证信息
  const handleUsernameSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!username.trim()) {
      toast.error('请输入用户名');
      return;
    }
    setIsLoading(true);
    try {
      const response = await fetch(`/api/auth/forgot-password?username=${encodeURIComponent(username.trim())}`, {
        method: 'GET',
      });
      const result = await response.json();
      if (result.success && result.data) {
        setUserInfo(result.data);
        // 自动选择有值的验证方式
        if (result.data.hasEmail) {
          setVerifyType('email');
        } else if (result.data.hasPhone) {
          setVerifyType('phone');
        }
        setStep('verify');
        toast.success('用户验证', { description: '请完成身份验证' });
      } else {
        toast.error('用户不存在', { description: result.error || '请检查用户名是否正确' });
      }
    } catch {
      toast.error('查询失败', { description: '网络错误，请重试' });
    } finally {
      setIsLoading(false);
    }
  };

  // Step 2: 验证身份
  const handleVerifySubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!verifyValue.trim()) {
      toast.error(`请输入${verifyType === 'email' ? '邮箱' : '手机号'}`);
      return;
    }
    setStep('reset');
  };

  // Step 3: 重置密码
  const handleResetSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (newPassword.length < 6) {
      toast.error('密码长度至少6位');
      return;
    }
    if (newPassword !== confirmPassword) {
      toast.error('两次输入的密码不一致');
      return;
    }
    setIsLoading(true);
    try {
      const response = await fetch('/api/auth/forgot-password', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          username,
          verifyValue,
          verifyType,
          newPassword,
          confirmPassword,
        }),
      });
      const result = await response.json();
      if (result.success) {
        setStep('success');
        toast.success('密码重置成功');
      } else {
        toast.error('重置失败', { description: result.error || '请重试' });
      }
    } catch {
      toast.error('重置失败', { description: '网络错误，请重试' });
    } finally {
      setIsLoading(false);
    }
  };

  const stepIndex = ['username', 'verify', 'reset', 'success'].indexOf(step);

  return (
    <div className="min-h-screen flex">
      <Toaster position="top-center" richColors closeButton />

      {/* 左侧品牌区 */}
      <div className="hidden lg:flex lg:w-[52%] relative overflow-hidden bg-gradient-to-br from-[#007AFF] via-[#0055D4] to-[#007AFF]">
        <div className="absolute inset-0">
          <div className="absolute top-[-10%] right-[-5%] w-[600px] h-[600px] bg-[rgba(0,122,255,0.2)] rounded-full blur-[120px]" />
          <div className="absolute bottom-[-15%] left-[-10%] w-[500px] h-[500px] bg-[rgba(175,82,222,0.15)] rounded-full blur-[100px]" />
          <div className="absolute top-[40%] left-[30%] w-[300px] h-[300px] bg-[rgba(0,122,255,0.1)] rounded-full blur-[80px]" />
          <div className="absolute inset-0 opacity-[0.03]" style={{
            backgroundImage: 'linear-gradient(rgba(255,255,255,1) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,1) 1px, transparent 1px)',
            backgroundSize: '80px 80px'
          }} />
        </div>

        <div className="relative z-10 flex flex-col justify-between px-14 xl:px-20 py-12 w-full">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-lg bg-[rgba(118,118,128,0.16)] backdrop-blur-sm border border-white/30 flex items-center justify-center shadow-lg">
              <Shield className="w-5 h-5 text-white" />
            </div>
            <span className="text-white/70 text-sm font-medium tracking-wider uppercase">Password Recovery</span>
          </div>

          <div className="max-w-lg">
            <h1 className="text-[3.2rem] xl:text-[3.8rem] font-extrabold text-white leading-[1.1] mb-5 tracking-tight">
              找回
              <br />
              <span className="text-white/80">您的密码</span>
            </h1>
            <p className="text-white/60 text-base leading-relaxed mb-10 max-w-md">
              通过身份验证重置您的登录密码。请按照步骤完成操作，保障您的账户安全。
            </p>

            {/* 步骤指示器 */}
            <div className="space-y-4">
              {[
                { num: 1, label: '输入用户名', desc: '确认您的账号' },
                { num: 2, label: '身份验证', desc: '验证邮箱或手机号' },
                { num: 3, label: '设置新密码', desc: '创建新的登录密码' },
              ].map((item) => {
                const isActive = stepIndex >= item.num - 1;
                const isCurrent = stepIndex === item.num - 1;
                return (
                  <div key={item.num} className="flex items-center gap-3.5 group">
                    <div className={cn(
                      'w-9 h-9 rounded-lg flex items-center justify-center border transition-all duration-300',
                      isActive
                        ? 'bg-white/[0.12] border-white/[0.15] backdrop-blur-sm'
                        : 'bg-white/[0.03] border-white/[0.05]'
                    )}>
                      {stepIndex > item.num - 1 ? (
                        <CheckCircle2 className="w-4 h-4 text-[#34c759]" />
                      ) : (
                        <span className={cn(
                          'text-sm font-bold',
                          isCurrent ? 'text-white' : 'text-white/30'
                        )}>
                          {item.num}
                        </span>
                      )}
                    </div>
                    <div className="flex-1">
                      <div className={cn(
                        'text-sm font-semibold transition-colors',
                        isActive ? 'text-white/90' : 'text-white/30'
                      )}>
                        {item.label}
                      </div>
                      <div className={cn(
                        'text-xs transition-colors',
                        isActive ? 'text-white/40' : 'text-white/20'
                      )}>
                        {item.desc}
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>

          <div className="text-white/30 text-xs">
            © 2024 企业数智中台系统 · v2.0
          </div>
        </div>
      </div>

      {/* 右侧操作区 */}
      <div className="flex-1 flex items-center justify-center p-8 bg-[#fafafe] relative">
        {/* 移动端 Logo */}
        <div className="lg:hidden absolute top-8 left-1/2 -translate-x-1/2">
          <div className="w-12 h-12 rounded-xl bg-[#007AFF] flex items-center justify-center shadow-lg">
            <Shield className="w-6 h-6 text-white" />
          </div>
        </div>

        <div className="w-full max-w-[380px]">
          {/* 返回登录 */}
          <button
            type="button"
            onClick={() => router.push('/login')}
            className="flex items-center gap-1.5 text-sm text-[#8e8e93] hover:text-[#007aff] transition-colors mb-8"
          >
            <ArrowLeft className="w-4 h-4" />
            返回登录
          </button>

          {/* Step 1: 输入用户名 */}
          {step === 'username' && (
            <>
              <div className="mb-10">
                <div className="flex items-center gap-2 mb-2">
                  <span className="w-7 h-7 rounded-full bg-[rgba(0,122,255,0.1)] flex items-center justify-center text-[#007aff] text-xs font-bold">1</span>
                  <h2 className="text-[1.65rem] font-bold text-[#8e8e93] tracking-tight">找回密码</h2>
                </div>
                <p className="text-[#8e8e93] mt-1.5 text-sm">请输入您的用户名，我们将查找您的账号</p>
              </div>

              <form onSubmit={handleUsernameSubmit} className="space-y-5">
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

                <Button
                  type="submit"
                  disabled={isLoading}
                  className={cn(
                    'w-full py-2.5 h-auto text-white font-semibold text-sm rounded-xl',
                    'bg-[#007AFF] hover:opacity-90',
                    'active:scale-[0.98]',
                    'transition-all duration-200',
                    'disabled:opacity-50 disabled:cursor-not-allowed'
                  )}
                >
                  {isLoading ? (
                    <>
                      <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                      查询中...
                    </>
                  ) : (
                    <>
                      下一步
                      <ArrowRight className="w-4 h-4 ml-2" />
                    </>
                  )}
                </Button>
              </form>
            </>
          )}

          {/* Step 2: 身份验证 */}
          {step === 'verify' && userInfo && (
            <>
              <div className="mb-10">
                <div className="flex items-center gap-2 mb-2">
                  <span className="w-7 h-7 rounded-full bg-[rgba(0,122,255,0.1)] flex items-center justify-center text-[#007aff] text-xs font-bold">2</span>
                  <h2 className="text-[1.65rem] font-bold text-[#8e8e93] tracking-tight">身份验证</h2>
                </div>
                <p className="text-[#8e8e93] mt-1.5 text-sm">请验证您的身份信息</p>
              </div>

              {/* 验证方式选择 */}
              <div className="mb-5">
                <label className="block text-xs font-semibold text-[#8e8e93] uppercase tracking-wider mb-2.5">选择验证方式</label>
                <div className="grid grid-cols-2 gap-2.5">
                  {userInfo.hasEmail && (
                    <button
                      type="button"
                      onClick={() => { setVerifyType('email'); setVerifyValue(''); }}
                      className={cn(
                        'flex items-center gap-2 px-3.5 py-2.5 rounded-xl border transition-all duration-200 text-sm font-medium',
                        verifyType === 'email'
                          ? 'border-[#007aff] bg-[rgba(0,122,255,0.1)] text-[#007aff] ring-[3px] ring-[rgba(0,122,255,0.1)]'
                          : 'border-[#e5e5ea] bg-white text-[#8e8e93] hover:border-[#e5e5ea]'
                      )}
                    >
                      <Mail className="w-4 h-4" />
                      邮箱验证
                    </button>
                  )}
                  {userInfo.hasPhone && (
                    <button
                      type="button"
                      onClick={() => { setVerifyType('phone'); setVerifyValue(''); }}
                      className={cn(
                        'flex items-center gap-2 px-3.5 py-2.5 rounded-xl border transition-all duration-200 text-sm font-medium',
                        verifyType === 'phone'
                          ? 'border-[#007aff] bg-[rgba(0,122,255,0.1)] text-[#007aff] ring-[3px] ring-[rgba(0,122,255,0.1)]'
                          : 'border-[#e5e5ea] bg-white text-[#8e8e93] hover:border-[#e5e5ea]'
                      )}
                    >
                      <Phone className="w-4 h-4" />
                      手机验证
                    </button>
                  )}
                </div>
              </div>

              <form onSubmit={handleVerifySubmit} className="space-y-5">
                {/* 提示信息 */}
                <div className="flex items-start gap-2.5 p-3.5 rounded-xl bg-[rgba(0,122,255,0.1)] border border-[#007aff]">
                  <AlertCircle className="w-4 h-4 text-[#007aff] mt-0.5 flex-shrink-0" />
                  <div className="text-xs text-[#007aff] leading-relaxed">
                    请输入您账号绑定的{verifyType === 'email' ? '邮箱' : '手机号'}完成验证。
                    <br />
                    <span className="text-[#007aff]">
                      {verifyType === 'email' ? '邮箱' : '手机号'}：{verifyType === 'email' ? userInfo.maskedEmail : userInfo.maskedPhone}
                    </span>
                  </div>
                </div>

                <div>
                  <label className="block text-xs font-semibold text-[#8e8e93] uppercase tracking-wider mb-2">
                    {verifyType === 'email' ? '邮箱地址' : '手机号码'}
                  </label>
                  <div className={cn(
                    'relative rounded-xl border transition-all duration-200',
                    focusedField === 'verify'
                      ? 'border-[#007aff] ring-[3px] ring-[rgba(0,122,255,0.1)] bg-white shadow-sm'
                      : 'border-[#e5e5ea] bg-white hover:border-[#e5e5ea]'
                  )}>
                    <div className={cn(
                      'absolute left-3.5 top-1/2 -translate-y-1/2 transition-colors duration-200',
                      focusedField === 'verify' ? 'text-[#007aff]' : 'text-[#8e8e93]'
                    )}>
                      {verifyType === 'email' ? <Mail className="w-[17px] h-[17px]" /> : <Phone className="w-[17px] h-[17px]" />}
                    </div>
                    <input
                      type="text"
                      value={verifyValue}
                      onChange={(e) => setVerifyValue(e.target.value)}
                      onFocus={() => setFocusedField('verify')}
                      onBlur={() => setFocusedField(null)}
                      placeholder={verifyType === 'email' ? '请输入完整邮箱地址' : '请输入完整手机号码'}
                      className="w-full pl-10 pr-4 py-2.5 rounded-xl bg-transparent focus:outline-none placeholder:text-[#3a3a3c] text-[#8e8e93] text-sm"
                    />
                  </div>
                </div>

                <div className="flex gap-2.5">
                  <Button
                    type="button"
                    onClick={() => setStep('username')}
                    className={cn(
                      'flex-1 py-2.5 h-auto font-semibold text-sm rounded-xl',
                      'border border-[#e5e5ea] bg-white text-[#8e8e93]',
                      'hover:bg-[#f2f2f7] hover:text-[#8e8e93]',
                      'transition-all duration-200'
                    )}
                  >
                    上一步
                  </Button>
                  <Button
                    type="submit"
                    className={cn(
                      'flex-1 py-2.5 h-auto text-white font-semibold text-sm rounded-xl',
                      'bg-[#007AFF] hover:opacity-90',
                      'active:scale-[0.98]',
                      'transition-all duration-200'
                    )}
                  >
                    验证
                    <ArrowRight className="w-4 h-4 ml-2" />
                  </Button>
                </div>
              </form>
            </>
          )}

          {/* Step 3: 设置新密码 */}
          {step === 'reset' && (
            <>
              <div className="mb-10">
                <div className="flex items-center gap-2 mb-2">
                  <span className="w-7 h-7 rounded-full bg-[rgba(0,122,255,0.1)] flex items-center justify-center text-[#007aff] text-xs font-bold">3</span>
                  <h2 className="text-[1.65rem] font-bold text-[#8e8e93] tracking-tight">设置新密码</h2>
                </div>
                <p className="text-[#8e8e93] mt-1.5 text-sm">请为您账号 {username} 设置新的登录密码</p>
              </div>

              <form onSubmit={handleResetSubmit} className="space-y-5">
                {/* 新密码 */}
                <div>
                  <label className="block text-xs font-semibold text-[#8e8e93] uppercase tracking-wider mb-2">新密码</label>
                  <div className={cn(
                    'relative rounded-xl border transition-all duration-200',
                    focusedField === 'newPassword'
                      ? 'border-[#007aff] ring-[3px] ring-[rgba(0,122,255,0.1)] bg-white shadow-sm'
                      : 'border-[#e5e5ea] bg-white hover:border-[#e5e5ea]'
                  )}>
                    <div className={cn(
                      'absolute left-3.5 top-1/2 -translate-y-1/2 transition-colors duration-200',
                      focusedField === 'newPassword' ? 'text-[#007aff]' : 'text-[#8e8e93]'
                    )}>
                      <Lock className="w-[17px] h-[17px]" />
                    </div>
                    <input
                      type={showNewPassword ? 'text' : 'password'}
                      value={newPassword}
                      onChange={(e) => setNewPassword(e.target.value)}
                      onFocus={() => setFocusedField('newPassword')}
                      onBlur={() => setFocusedField(null)}
                      placeholder="请输入新密码（至少6位）"
                      className="w-full pl-10 pr-10 py-2.5 rounded-xl bg-transparent focus:outline-none placeholder:text-[#3a3a3c] text-[#8e8e93] text-sm"
                    />
                    <button
                      type="button"
                      onClick={() => setShowNewPassword(!showNewPassword)}
                      className="absolute right-3 top-1/2 -translate-y-1/2 text-[#8e8e93] hover:text-[#8e8e93] transition-colors p-0.5"
                    >
                      {showNewPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                    </button>
                  </div>

                  {/* 密码强度指示器 */}
                  {newPassword && (
                    <div className="mt-2 flex items-center gap-2">
                      <div className="flex-1 flex gap-1">
                        {[1, 2, 3, 4, 5].map((i) => (
                          <div
                            key={i}
                            className={cn(
                              'h-1 flex-1 rounded-full transition-all duration-200',
                              passwordStrength.level >= i ? passwordStrength.color : 'bg-[rgba(118,118,128,0.08)]'
                            )}
                          />
                        ))}
                      </div>
                      <span className={cn(
                        'text-xs font-medium',
                        passwordStrength.level >= 4 ? 'text-[#34c759]' :
                        passwordStrength.level >= 3 ? 'text-[#007aff]' :
                        passwordStrength.level >= 2 ? 'text-[#ff9500]' : 'text-[#ff3b30]'
                      )}>
                        {passwordStrength.label}
                      </span>
                    </div>
                  )}
                </div>

                {/* 确认密码 */}
                <div>
                  <label className="block text-xs font-semibold text-[#8e8e93] uppercase tracking-wider mb-2">确认新密码</label>
                  <div className={cn(
                    'relative rounded-xl border transition-all duration-200',
                    focusedField === 'confirmPassword'
                      ? 'border-[#007aff] ring-[3px] ring-[rgba(0,122,255,0.1)] bg-white shadow-sm'
                      : confirmPassword && confirmPassword === newPassword
                        ? 'border-[#34c759] bg-[rgba(52,199,89,0.03)]'
                        : 'border-[#e5e5ea] bg-white hover:border-[#e5e5ea]'
                  )}>
                    <div className={cn(
                      'absolute left-3.5 top-1/2 -translate-y-1/2 transition-colors duration-200',
                      focusedField === 'confirmPassword' ? 'text-[#007aff]' : 'text-[#8e8e93]'
                    )}>
                      <Lock className="w-[17px] h-[17px]" />
                    </div>
                    <input
                      type={showConfirmPassword ? 'text' : 'password'}
                      value={confirmPassword}
                      onChange={(e) => setConfirmPassword(e.target.value)}
                      onFocus={() => setFocusedField('confirmPassword')}
                      onBlur={() => setFocusedField(null)}
                      placeholder="请再次输入新密码"
                      className="w-full pl-10 pr-10 py-2.5 rounded-xl bg-transparent focus:outline-none placeholder:text-[#3a3a3c] text-[#8e8e93] text-sm"
                    />
                    <button
                      type="button"
                      onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                      className="absolute right-3 top-1/2 -translate-y-1/2 text-[#8e8e93] hover:text-[#8e8e93] transition-colors p-0.5"
                    >
                      {showConfirmPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                    </button>
                  </div>
                  {confirmPassword && confirmPassword !== newPassword && (
                    <p className="mt-1.5 text-xs text-[#ff3b30] flex items-center gap-1">
                      <AlertCircle className="w-3 h-3" />
                      两次输入的密码不一致
                    </p>
                  )}
                  {confirmPassword && confirmPassword === newPassword && (
                    <p className="mt-1.5 text-xs text-[#34c759] flex items-center gap-1">
                      <CheckCircle2 className="w-3 h-3" />
                      密码一致
                    </p>
                  )}
                </div>

                <div className="flex gap-2.5">
                  <Button
                    type="button"
                    onClick={() => setStep('verify')}
                    className={cn(
                      'flex-1 py-2.5 h-auto font-semibold text-sm rounded-xl',
                      'border border-[#e5e5ea] bg-white text-[#8e8e93]',
                      'hover:bg-[#f2f2f7] hover:text-[#8e8e93]',
                      'transition-all duration-200'
                    )}
                  >
                    上一步
                  </Button>
                  <Button
                    type="submit"
                    disabled={isLoading || !newPassword || !confirmPassword || newPassword !== confirmPassword}
                    className={cn(
                      'flex-1 py-2.5 h-auto text-white font-semibold text-sm rounded-xl',
                      'bg-[#007AFF] hover:opacity-90',
                      'active:scale-[0.98]',
                      'transition-all duration-200',
                      'disabled:opacity-50 disabled:cursor-not-allowed'
                    )}
                  >
                    {isLoading ? (
                      <>
                        <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                        重置中...
                      </>
                    ) : (
                      '确认重置'
                    )}
                  </Button>
                </div>
              </form>
            </>
          )}

          {/* Step 4: 成功 */}
          {step === 'success' && (
            <div className="text-center py-8">
              <div className="w-20 h-20 mx-auto rounded-full bg-[rgba(52,199,89,0.1)] flex items-center justify-center mb-6">
                <CheckCircle2 className="w-10 h-10 text-[#34c759]" />
              </div>
              <h2 className="text-2xl font-bold text-[#8e8e93] mb-2">密码重置成功</h2>
              <p className="text-[#8e8e93] text-sm mb-8 max-w-xs mx-auto leading-relaxed">
                您的密码已成功重置，请使用新密码登录系统。
              </p>
              <Button
                type="button"
                onClick={() => router.push('/login')}
                className={cn(
                  'w-full py-2.5 h-auto text-white font-semibold text-sm rounded-xl',
                  'bg-[#007AFF] hover:opacity-90',
                  'active:scale-[0.98]',
                  'transition-all duration-200'
                )}
              >
                前往登录
                <ArrowRight className="w-4 h-4 ml-2" />
              </Button>
            </div>
          )}

          {/* 安全提示 */}
          {step !== 'success' && (
            <div className="mt-8 pt-6 border-t border-[#e5e5ea] flex items-center justify-center gap-4 text-[11px] text-[#3a3a3c]">
              <span className="flex items-center gap-1"><Shield className="w-3 h-3" /> 加密传输</span>
              <span className="flex items-center gap-1"><Lock className="w-3 h-3" /> 安全验证</span>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
