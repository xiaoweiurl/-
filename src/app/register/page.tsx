'use client';

import React from 'react';
import { useRouter } from 'next/navigation';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import { User, Loader2, ArrowLeft, Building2, Check } from 'lucide-react';
import { toast } from 'sonner';
import { Toaster } from '@/components/ui/sonner';

interface Candidate {
  dingtalkUserid: string;
  name: string;
  jobTitle?: string;
  deptName?: string;
  deptPath?: string;
  alreadyRegistered?: boolean;
}

interface RegisterResponse {
  success: boolean;
  message?: string;
  error?: string;
  candidates?: Candidate[];
  data?: {
    sessionId?: string;
    user?: {
      id: string;
      username: string;
      email?: string;
      role: string;
      company?: string;
      mustChangePassword?: boolean;
    };
  };
}

const DEFAULT_COMPANY = '宝娜斯集团';

export default function RegisterPage() {
  const router = useRouter();
  const [name, setName] = React.useState('');
  const [candidates, setCandidates] = React.useState<Candidate[]>([]);
  const [selectedUserId, setSelectedUserId] = React.useState<string | null>(null);
  const [isLoading, setIsLoading] = React.useState(false);

  const submit = async (dingtalkUserid?: string) => {
    const trimmed = name.trim();
    if (!trimmed) {
      toast.error('请输入姓名');
      return;
    }

    setIsLoading(true);
    try {
      const response = await fetch('/api/auth/register', {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          name: trimmed,
          company: DEFAULT_COMPANY,
          dingtalkUserid: dingtalkUserid || undefined,
        }),
      });

      const result: RegisterResponse = await response.json();

      if (response.status === 409 && (result.candidates?.length || 0) > 0) {
        setCandidates(result.candidates || []);
        setSelectedUserId(null);
        toast.message('找到多名同名员工', { description: '请选择您的身份后继续' });
        return;
      }

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
          description: `账号 ${result.data.user?.username || trimmed} 已创建。初始密码为 123456，首次登录需修改。`,
        });

        if (result.data.user?.mustChangePassword) {
          window.location.href = '/settings?tab=security&forceChange=1';
          return;
        }
        router.replace('/');
        router.refresh();
      } else {
        toast.error('注册失败', {
          description: result.error || result.message || '未找到匹配的钉钉通讯录成员',
        });
      }
    } catch (error) {
      console.error('注册失败:', error);
      toast.error('注册失败', { description: '网络错误，请重试' });
    } finally {
      setIsLoading(false);
    }
  };

  const handleRegister = async (e: React.FormEvent) => {
    e.preventDefault();
    await submit(selectedUserId || undefined);
  };

  return (
    <div className="min-h-screen bg-[#F2F2F7] flex items-center justify-center p-4">
      <Toaster position="top-center" richColors closeButton />

      <div className="w-full max-w-md">
        <div className="text-center mb-8">
          <div className="w-20 h-20 mx-auto rounded-2xl bg-[#007AFF] flex items-center justify-center shadow-sm mb-4">
            <span className="text-3xl font-bold text-white">宝</span>
          </div>
          <h1 className="text-3xl font-bold text-[#1C1C1E]">创建账号</h1>
          <p className="text-[#8E8E93] mt-2">使用钉钉通讯录姓名注册（办公/管理端）</p>
        </div>

        <div className="bg-white rounded-2xl shadow-sm border border-[#E5E5EA] p-8">
          <form onSubmit={handleRegister} className="space-y-5">
            <div className="flex items-center gap-2 p-3 rounded-xl bg-[#F2F2F7] border border-[#E5E5EA]">
              <Building2 className="w-5 h-5 text-[#8E8E93]" />
              <span className="text-sm text-[#8E8E93]">所属公司：</span>
              <span className="text-sm font-medium text-[#007AFF]">{DEFAULT_COMPANY}</span>
            </div>

            <div>
              <label className="block text-sm font-medium text-[#3A3A3C] mb-2">姓名</label>
              <div className="relative">
                <div className="absolute left-3 top-1/2 -translate-y-1/2 text-[#8E8E93]">
                  <User className="w-5 h-5" />
                </div>
                <input
                  type="text"
                  value={name}
                  onChange={(e) => {
                    setName(e.target.value);
                    setCandidates([]);
                    setSelectedUserId(null);
                  }}
                  placeholder="请输入钉钉通讯录中的姓名"
                  className={cn(
                    'w-full pl-11 pr-4 py-3 rounded-xl border border-[#E5E5EA] bg-white',
                    'focus:outline-none focus:ring-2 focus:ring-[rgba(0,122,255,0.2)] focus:border-[#007AFF]',
                    'placeholder:text-[#8E8E93] text-[#1C1C1E]',
                    'transition-all duration-200'
                  )}
                />
              </div>
              <p className="mt-2 text-xs text-[#8E8E93]">
                初始密码为 123456，登录后需按系统提示修改。无需填写邮箱。
              </p>
            </div>

            {candidates.length > 0 && (
              <div className="space-y-2">
                <p className="text-sm font-medium text-[#3A3A3C]">请选择您的身份</p>
                {candidates.map((c) => {
                  const disabled = Boolean(c.alreadyRegistered);
                  const selected = selectedUserId === c.dingtalkUserid;
                  return (
                    <button
                      key={c.dingtalkUserid}
                      type="button"
                      disabled={disabled}
                      onClick={() => setSelectedUserId(c.dingtalkUserid)}
                      className={cn(
                        'w-full text-left rounded-xl border p-3 transition-all duration-200',
                        disabled
                          ? 'opacity-50 cursor-not-allowed border-[#E5E5EA] bg-[#F2F2F7]'
                          : selected
                            ? 'border-[#007AFF] bg-[#007AFF]/5'
                            : 'border-[#E5E5EA] bg-white hover:-translate-y-[1px] hover:shadow-sm'
                      )}
                    >
                      <div className="flex items-start justify-between gap-2">
                        <div>
                          <div className="text-sm font-medium text-[#1C1C1E]">{c.name}</div>
                          <div className="text-xs text-[#8E8E93] mt-0.5">
                            {c.jobTitle || '未填写职位'}
                            {c.deptPath ? ` · ${c.deptPath}` : c.deptName ? ` · ${c.deptName}` : ''}
                          </div>
                          {disabled && (
                            <div className="text-xs text-[#FF9500] mt-1">该成员已注册，请直接登录</div>
                          )}
                        </div>
                        {selected && !disabled && <Check className="w-4 h-4 text-[#007AFF] mt-0.5" />}
                      </div>
                    </button>
                  );
                })}
              </div>
            )}

            <Button
              type="submit"
              disabled={isLoading || (candidates.length > 0 && !selectedUserId)}
              className={cn(
                'w-full py-3 text-white font-medium rounded-xl',
                'bg-[#007AFF] hover:opacity-90',
                'transition-all duration-200',
                'disabled:opacity-50 disabled:cursor-not-allowed'
              )}
            >
              {isLoading ? (
                <>
                  <Loader2 className="w-5 h-5 mr-2 animate-spin" />
                  匹配中...
                </>
              ) : candidates.length > 0 ? (
                '确认并注册'
              ) : (
                '注 册'
              )}
            </Button>
          </form>
        </div>

        <div className="mt-4 text-center">
          <button
            type="button"
            onClick={() => router.push('/login')}
            className="text-sm text-[#8E8E93] hover:text-[#3A3A3C] transition-colors inline-flex items-center gap-1"
          >
            <ArrowLeft className="w-4 h-4" />
            已有账号？返回登录
          </button>
        </div>
      </div>
    </div>
  );
}
