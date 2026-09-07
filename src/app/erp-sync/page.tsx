'use client';

import React, { useState, useEffect, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import {
  RefreshCcw, ArrowLeft, LogOut, LogIn, ShieldAlert, Database, Clock,
  CheckCircle2, XCircle, AlertCircle, Trash2, Server, ChevronRight,
  Layers, Activity, KeyRound, User, Globe, Loader2, History
} from 'lucide-react';

// ==================== 类型定义 ====================

interface ErpAuthState {
  loggedIn: boolean;
  uid?: string | null;
  loginTime?: string | null;
  demo?: boolean;
  demoEnabled?: boolean;
  baseUrl?: string;
  customId?: string;
}

interface ModuleState {
  moduleKey: string;
  moduleName: string;
  endpoint: string;
  supportsTimeFilter: boolean;
  lastSyncTime?: string | null;
  totalRecords?: number | null;
  lastAdded?: number | null;
  lastStatus?: string | null;
  lastMessage?: string | null;
  lastDuration?: number | null;
}

interface SyncSummary {
  moduleCount: number;
  syncedModules: number;
  totalRecords: number;
  todaySyncCount: number;
  failedCount: number;
  latestSyncTime?: string | null;
}

interface SyncLog {
  id: number;
  moduleKey: string;
  moduleName: string;
  syncType: string;
  rangeStart?: string | null;
  rangeEnd?: string | null;
  added?: number | null;
  failed?: number | null;
  status: string;
  duration?: number | null;
  message?: string | null;
  source?: string | null;
  time?: string | null;
}

// ==================== 工具函数 ====================

function statusBadge(status?: string | null) {
  switch (status) {
    case 'success':
      return { text: '成功', cls: 'bg-[#34C759]/10 text-[#34C759]' };
    case 'failed':
      return { text: '失败', cls: 'bg-[#FF3B30]/10 text-[#FF3B30]' };
    case 'partial':
      return { text: '部分成功', cls: 'bg-[#FF9500]/10 text-[#FF9500]' };
    case 'never':
    default:
      return { text: '未同步', cls: 'bg-[rgba(118,118,128,0.12)] text-[#8E8E93]' };
  }
}

function fmtNum(n?: number | null): string {
  if (n == null) return '0';
  return n.toLocaleString();
}

// ==================== 主组件 ====================

export default function ErpSyncPage() {
  const router = useRouter();

  // 系统用户角色（权限守卫）
  const [roleChecked, setRoleChecked] = useState(false);
  const [isAdmin, setIsAdmin] = useState(false);

  // ERP 登录态
  const [authState, setAuthState] = useState<ErpAuthState | null>(null);
  const [loginForm, setLoginForm] = useState({ uid: '', password: '', customId: '' });
  const [loginLoading, setLoginLoading] = useState(false);
  const [loginError, setLoginError] = useState('');

  // 同步状态
  const [modules, setModules] = useState<ModuleState[]>([]);
  const [summary, setSummary] = useState<SyncSummary | null>(null);
  const [logs, setLogs] = useState<SyncLog[]>([]);
  const [pageLoading, setPageLoading] = useState(true);

  // 同步执行状态
  const [syncingModule, setSyncingModule] = useState<string | null>(null);
  const [syncingAll, setSyncingAll] = useState(false);
  const [syncAllProgress, setSyncAllProgress] = useState<{ done: number; total: number; current?: string } | null>(null);
  const [toast, setToast] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  const sessionHeaders = useCallback((): Record<string, string> => {
    const sessionId = typeof window !== 'undefined' ? localStorage.getItem('session_id') : null;
    const h: Record<string, string> = { 'Content-Type': 'application/json' };
    if (sessionId) h['X-Session-Id'] = sessionId;
    return h;
  }, []);

  const showToast = (type: 'success' | 'error', text: string) => {
    setToast({ type, text });
    setTimeout(() => setToast(null), 4000);
  };

  // ==================== 数据加载 ====================

  const loadAuthState = useCallback(async () => {
    try {
      const res = await fetch('/api/erp-sync/auth-state', { headers: sessionHeaders() });
      const json = await res.json();
      if (json.success) {
        setAuthState(json.data);
        if (json.data?.customId) setLoginForm(f => ({ ...f, customId: f.customId || json.data.customId }));
      }
    } catch { /* ignore */ }
  }, [sessionHeaders]);

  const loadStatus = useCallback(async () => {
    try {
      const res = await fetch('/api/erp-sync/status', { headers: sessionHeaders() });
      const json = await res.json();
      if (json.success) {
        setModules(json.data.modules || []);
        setSummary(json.data.summary || null);
      }
    } catch { /* ignore */ }
  }, [sessionHeaders]);

  const loadLogs = useCallback(async () => {
    try {
      const res = await fetch('/api/erp-sync/logs?limit=50', { headers: sessionHeaders() });
      const json = await res.json();
      if (json.success) setLogs(json.data || []);
    } catch { /* ignore */ }
  }, [sessionHeaders]);

  const loadAll = useCallback(async () => {
    setPageLoading(true);
    await Promise.all([loadAuthState(), loadStatus(), loadLogs()]);
    setPageLoading(false);
  }, [loadAuthState, loadStatus, loadLogs]);

  // ==================== 权限守卫 ====================

  useEffect(() => {
    fetch('/api/auth/login', { credentials: 'include' })
      .then(res => (res.ok ? res.json() : null))
      .then(data => {
        const role = data?.success ? String(data.data?.role || '').toLowerCase() : '';
        setIsAdmin(role === 'admin' || role === 'superadmin');
        setRoleChecked(true);
      })
      .catch(() => setRoleChecked(true));
  }, []);

  useEffect(() => {
    if (isAdmin) loadAll();
  }, [isAdmin, loadAll]);

  // ==================== ERP 登录 / 登出 ====================

  const handleLogin = async () => {
    if (!loginForm.uid.trim() || !loginForm.password) {
      setLoginError('请输入 ERP 账号和密码');
      return;
    }
    setLoginLoading(true);
    setLoginError('');
    try {
      const res = await fetch('/api/erp-sync/login', {
        method: 'POST',
        headers: sessionHeaders(),
        body: JSON.stringify({
          uid: loginForm.uid.trim(),
          password: loginForm.password,
          customId: loginForm.customId.trim(),
        }),
      });
      const json = await res.json();
      if (json.success) {
        setAuthState(s => ({ ...s, ...json.data, loggedIn: true }));
        showToast('success', json.data?.demo ? '演示模式登录成功' : 'ERP 登录成功');
        setLoginForm(f => ({ ...f, password: '' }));
      } else {
        setLoginError(json.message || '登录失败');
      }
    } catch {
      setLoginError('网络异常，请稍后重试');
    } finally {
      setLoginLoading(false);
    }
  };

  const handleLogout = async () => {
    try {
      await fetch('/api/erp-sync/logout', { method: 'POST', headers: sessionHeaders() });
      setAuthState(s => ({ ...s, loggedIn: false, uid: null, loginTime: null }));
      showToast('success', '已退出 ERP 登录');
    } catch { /* ignore */ }
  };

  // ==================== 同步操作 ====================

  const handleSyncModule = async (moduleKey: string) => {
    setSyncingModule(moduleKey);
    try {
      const res = await fetch(`/api/erp-sync/sync/${moduleKey}`, { method: 'POST', headers: sessionHeaders() });
      const json = await res.json();
      if (res.status === 401 || json.code === 401) {
        setAuthState(s => ({ ...s, loggedIn: false }));
        showToast('error', 'ERP 登录已失效，请重新登录');
        return;
      }
      if (json.success) {
        const d = json.data;
        showToast('success', `${d.moduleName} 同步完成，新增 ${d.added} 条`);
      } else {
        showToast('error', json.message || '同步失败');
      }
    } catch {
      showToast('error', '同步请求失败');
    } finally {
      setSyncingModule(null);
      await Promise.all([loadStatus(), loadLogs()]);
    }
  };

  const handleSyncAll = async () => {
    setSyncingAll(true);
    setSyncAllProgress({ done: 0, total: modules.length || 7 });
    try {
      const res = await fetch('/api/erp-sync/sync-all', { method: 'POST', headers: sessionHeaders() });
      const json = await res.json();
      if (res.status === 401 || json.code === 401) {
        setAuthState(s => ({ ...s, loggedIn: false }));
        showToast('error', 'ERP 登录已失效，请重新登录');
        return;
      }
      if (json.success) {
        const results: Array<{ moduleName: string; added: number; status: string }> = json.data || [];
        const totalAdded = results.reduce((s, r) => s + (r.added || 0), 0);
        const failed = results.filter(r => r.status === 'failed').length;
        showToast(failed > 0 ? 'error' : 'success',
          failed > 0 ? `同步完成，共 ${totalAdded} 条，${failed} 个模块失败` : `全部同步完成，共新增 ${totalAdded} 条`);
      } else {
        showToast('error', json.message || '同步失败');
      }
    } catch {
      showToast('error', '同步请求失败');
    } finally {
      setSyncingAll(false);
      setSyncAllProgress(null);
      await Promise.all([loadStatus(), loadLogs()]);
    }
  };

  const handleClearLogs = async () => {
    if (!confirm('确定清空所有同步日志？（不影响同步状态）')) return;
    try {
      const res = await fetch('/api/erp-sync/logs', { method: 'DELETE', headers: sessionHeaders() });
      const json = await res.json();
      if (json.success) {
        setLogs([]);
        showToast('success', '日志已清空');
      }
    } catch { /* ignore */ }
  };

  // ==================== 渲染 ====================

  if (!roleChecked) {
    return (
      <div className="min-h-screen bg-[#F2F2F7] flex items-center justify-center">
        <Loader2 className="w-6 h-6 text-[#007AFF] animate-spin" />
      </div>
    );
  }

  if (!isAdmin) {
    return (
      <div className="min-h-screen bg-[#F2F2F7] flex items-center justify-center p-6">
        <div className="bg-white rounded-2xl shadow-[0_2px_12px_rgba(0,0,0,0.04)] p-10 max-w-md w-full text-center">
          <div className="w-16 h-16 rounded-2xl bg-[#FF3B30]/10 flex items-center justify-center mx-auto mb-4">
            <ShieldAlert className="w-8 h-8 text-[#FF3B30]" />
          </div>
          <h1 className="text-xl font-bold text-[#1C1C1E] mb-2">无访问权限</h1>
          <p className="text-sm text-[#8E8E93] mb-6">ERP 数据同步功能仅对管理员及以上角色开放</p>
          <button onClick={() => router.push('/supply-chain')}
            className="h-10 px-6 rounded-xl bg-[#007AFF] text-white text-sm font-medium hover:bg-[#0066D6] active:scale-[0.98] transition-all">
            返回供应链
          </button>
        </div>
      </div>
    );
  }

  const loggedIn = !!authState?.loggedIn;

  return (
    <div className="min-h-screen bg-[#F2F2F7]">
      {/* 顶部导航（毛玻璃） */}
      <header className="sticky top-0 z-20 backdrop-blur-[20px] bg-[rgba(255,255,255,0.72)] border-b border-[#E5E5EA]">
        <div className="max-w-[1200px] mx-auto px-6 h-14 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <button onClick={() => router.push('/supply-chain')}
              className="w-8 h-8 rounded-lg flex items-center justify-center text-[#8E8E93] hover:bg-[rgba(118,118,128,0.12)] hover:text-[#1C1C1E] transition-all">
              <ArrowLeft className="w-4.5 h-4.5" />
            </button>
            <div className="w-8 h-8 rounded-xl bg-[#007AFF] flex items-center justify-center">
              <RefreshCcw className="w-4 h-4 text-white" />
            </div>
            <div>
              <h1 className="text-[17px] font-bold text-[#1C1C1E] leading-tight">ERP 数据同步</h1>
              <p className="text-[11px] text-[#8E8E93] leading-tight">增量同步 · 按数据库最新时间过滤</p>
            </div>
          </div>
          {loggedIn && (
            <div className="flex items-center gap-2">
              <span className={`text-[11px] px-2 py-0.5 rounded-full ${authState?.demo ? 'bg-[#FF9500]/10 text-[#FF9500]' : 'bg-[#34C759]/10 text-[#34C759]'}`}>
                {authState?.demo ? '演示模式' : '已连接 ERP'}
              </span>
              <span className="text-[12px] text-[#8E8E93]">{authState?.uid}</span>
              <button onClick={handleLogout}
                className="h-8 px-3 rounded-lg text-[12px] font-medium text-[#FF3B30] hover:bg-[#FF3B30]/10 transition-all flex items-center gap-1">
                <LogOut className="w-3.5 h-3.5" />登出
              </button>
            </div>
          )}
        </div>
      </header>

      {/* Toast */}
      {toast && (
        <div className={`fixed top-16 left-1/2 -translate-x-1/2 z-30 px-4 py-2.5 rounded-xl text-[13px] font-medium shadow-[0_6px_24px_rgba(0,0,0,0.12)] flex items-center gap-2 ${
          toast.type === 'success' ? 'bg-[#34C759] text-white' : 'bg-[#FF3B30] text-white'
        }`}>
          {toast.type === 'success' ? <CheckCircle2 className="w-4 h-4" /> : <XCircle className="w-4 h-4" />}
          {toast.text}
        </div>
      )}

      <main className="max-w-[1200px] mx-auto px-6 py-6 space-y-6">

        {/* ========== ERP 登录卡 ========== */}
        {!loggedIn ? (
          <div className="bg-white rounded-2xl shadow-[0_2px_12px_rgba(0,0,0,0.04)] p-6">
            <div className="flex items-center gap-3 mb-5">
              <div className="w-10 h-10 rounded-xl bg-[#007AFF]/10 flex items-center justify-center">
                <KeyRound className="w-5 h-5 text-[#007AFF]" />
              </div>
              <div>
                <h2 className="text-[16px] font-bold text-[#1C1C1E]">ERP 账号登录</h2>
                <p className="text-[12px] text-[#8E8E93]">登录后获取 token，服务端自动携带于所有业务接口请求头</p>
              </div>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
              <div className="relative">
                <User className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-[#8E8E93]" />
                <input
                  value={loginForm.uid}
                  onChange={e => setLoginForm(f => ({ ...f, uid: e.target.value }))}
                  placeholder="ERP 账号"
                  className="w-full h-10 pl-9 pr-3 rounded-xl bg-[rgba(118,118,128,0.12)] text-[14px] text-[#1C1C1E] placeholder:text-[#8E8E93] outline-none focus:bg-white focus:ring-2 focus:ring-[#007AFF]/30 transition-all"
                />
              </div>
              <div className="relative">
                <KeyRound className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-[#8E8E93]" />
                <input
                  type="password"
                  value={loginForm.password}
                  onChange={e => setLoginForm(f => ({ ...f, password: e.target.value }))}
                  onKeyDown={e => e.key === 'Enter' && handleLogin()}
                  placeholder="密码"
                  className="w-full h-10 pl-9 pr-3 rounded-xl bg-[rgba(118,118,128,0.12)] text-[14px] text-[#1C1C1E] placeholder:text-[#8E8E93] outline-none focus:bg-white focus:ring-2 focus:ring-[#007AFF]/30 transition-all"
                />
              </div>
              <div className="relative">
                <Globe className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-[#8E8E93]" />
                <input
                  value={loginForm.customId}
                  onChange={e => setLoginForm(f => ({ ...f, customId: e.target.value }))}
                  placeholder="账套码（可选）"
                  className="w-full h-10 pl-9 pr-3 rounded-xl bg-[rgba(118,118,128,0.12)] text-[14px] text-[#1C1C1E] placeholder:text-[#8E8E93] outline-none focus:bg-white focus:ring-2 focus:ring-[#007AFF]/30 transition-all"
                />
              </div>
            </div>
            {loginError && (
              <div className="mt-3 flex items-center gap-1.5 text-[12px] text-[#FF3B30]">
                <AlertCircle className="w-3.5 h-3.5" />{loginError}
              </div>
            )}
            <div className="mt-4 flex items-center justify-between">
              <p className="text-[11px] text-[#8E8E93]">
                登录接口独立地址；业务接口统一前缀 <span className="font-mono">{authState?.baseUrl || '—'}</span>
              </p>
              <button onClick={handleLogin} disabled={loginLoading}
                className="h-10 px-6 rounded-xl bg-[#007AFF] text-white text-[14px] font-medium hover:bg-[#0066D6] active:scale-[0.98] disabled:opacity-50 transition-all flex items-center gap-2">
                {loginLoading ? <Loader2 className="w-4 h-4 animate-spin" /> : <LogIn className="w-4 h-4" />}
                登录
              </button>
            </div>
          </div>
        ) : (
          <>
            {/* ========== 同步状态概览 ========== */}
            <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
              {[
                { label: '同步模块', value: `${summary?.syncedModules ?? 0}/${summary?.moduleCount ?? 7}`, icon: <Layers className="w-5 h-5" />, iconBg: 'bg-[#007AFF]/10', iconColor: 'text-[#007AFF]' },
                { label: '累计记录', value: fmtNum(summary?.totalRecords), icon: <Database className="w-5 h-5" />, iconBg: 'bg-[#AF52DE]/10', iconColor: 'text-[#AF52DE]' },
                { label: '今日同步', value: fmtNum(summary?.todaySyncCount), icon: <Activity className="w-5 h-5" />, iconBg: 'bg-[#34C759]/10', iconColor: 'text-[#34C759]', suffix: ' 次' },
                { label: '累计失败', value: fmtNum(summary?.failedCount), icon: <XCircle className="w-5 h-5" />, iconBg: 'bg-[#FF3B30]/10', iconColor: 'text-[#FF3B30]', suffix: ' 条' },
              ].map((card, i) => (
                <div key={i} className="bg-white rounded-2xl p-5 shadow-[0_2px_12px_rgba(0,0,0,0.04)] hover:shadow-[0_6px_24px_rgba(0,0,0,0.08)] hover:-translate-y-[3px] transition-all duration-300">
                  <div className={`w-10 h-10 rounded-xl ${card.iconBg} ${card.iconColor} flex items-center justify-center mb-3`}>
                    {card.icon}
                  </div>
                  <div className="text-2xl font-bold text-[#1C1C1E] tabular-nums">
                    {card.value}<span className="text-[13px] font-normal text-[#8E8E93]">{card.suffix || ''}</span>
                  </div>
                  <div className="text-[12px] text-[#8E8E93] mt-0.5">{card.label}</div>
                </div>
              ))}
            </div>

            {/* ========== 模块同步 ========== */}
            <div className="bg-white rounded-2xl shadow-[0_2px_12px_rgba(0,0,0,0.04)] overflow-hidden">
              <div className="px-6 py-4 flex items-center justify-between border-b border-[#E5E5EA]">
                <div>
                  <h2 className="text-[16px] font-bold text-[#1C1C1E]">数据模块</h2>
                  <p className="text-[12px] text-[#8E8E93] mt-0.5">
                    最近同步：{summary?.latestSyncTime || '暂无'} · 首次同步默认回看 90 天
                  </p>
                </div>
                <button onClick={handleSyncAll} disabled={syncingAll || syncingModule !== null}
                  className="h-10 px-5 rounded-xl bg-[#007AFF] text-white text-[14px] font-medium hover:bg-[#0066D6] active:scale-[0.98] disabled:opacity-50 transition-all flex items-center gap-2">
                  {syncingAll ? <Loader2 className="w-4 h-4 animate-spin" /> : <RefreshCcw className="w-4 h-4" />}
                  {syncingAll ? '同步中...' : '立即同步全部'}
                </button>
              </div>

              {syncingAll && syncAllProgress && (
                <div className="px-6 py-3 bg-[#007AFF]/[0.04] border-b border-[#E5E5EA]">
                  <div className="flex items-center gap-3">
                    <Loader2 className="w-4 h-4 text-[#007AFF] animate-spin flex-shrink-0" />
                    <span className="text-[13px] text-[#3A3A3C]">正在串行同步 {syncAllProgress.total} 个模块，请稍候...</span>
                  </div>
                  <div className="mt-2 h-1.5 rounded-full bg-[rgba(118,118,128,0.12)] overflow-hidden">
                    <div className="h-full rounded-full bg-[#007AFF] transition-all duration-500 animate-pulse" style={{ width: '100%' }} />
                  </div>
                </div>
              )}

              <div className="divide-y divide-[#E5E5EA]">
                {pageLoading ? (
                  <div className="py-12 flex items-center justify-center">
                    <Loader2 className="w-5 h-5 text-[#007AFF] animate-spin" />
                  </div>
                ) : modules.map(m => {
                  const badge = statusBadge(m.lastStatus);
                  const syncing = syncingModule === m.moduleKey;
                  return (
                    <div key={m.moduleKey} className="px-6 py-4 flex items-center gap-4 hover:bg-[#F9F9FB] transition-colors">
                      <div className="w-10 h-10 rounded-xl bg-[#007AFF]/10 flex items-center justify-center flex-shrink-0">
                        <Server className="w-5 h-5 text-[#007AFF]" />
                      </div>
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2">
                          <span className="text-[14px] font-semibold text-[#1C1C1E]">{m.moduleName}</span>
                          <span className={`text-[11px] px-2 py-0.5 rounded-full ${badge.cls}`}>{badge.text}</span>
                          {m.supportsTimeFilter && (
                            <span className="text-[10px] px-1.5 py-0.5 rounded bg-[rgba(118,118,128,0.12)] text-[#8E8E93]">时间过滤</span>
                          )}
                        </div>
                        <div className="text-[11px] text-[#8E8E93] mt-0.5 font-mono truncate">{m.endpoint}</div>
                      </div>
                      <div className="hidden md:flex items-center gap-6 text-right flex-shrink-0">
                        <div>
                          <div className="text-[13px] font-semibold text-[#1C1C1E] tabular-nums">{fmtNum(m.totalRecords)}</div>
                          <div className="text-[10px] text-[#8E8E93]">累计记录</div>
                        </div>
                        <div>
                          <div className="text-[13px] font-semibold text-[#34C759] tabular-nums">+{fmtNum(m.lastAdded)}</div>
                          <div className="text-[10px] text-[#8E8E93]">最近增量</div>
                        </div>
                        <div className="w-36">
                          <div className="text-[12px] text-[#3A3A3C] flex items-center gap-1 justify-end">
                            <Clock className="w-3 h-3 text-[#8E8E93]" />
                            <span className="truncate">{m.lastSyncTime || '从未同步'}</span>
                          </div>
                          <div className="text-[10px] text-[#8E8E93] text-right">最后同步</div>
                        </div>
                      </div>
                      <button onClick={() => handleSyncModule(m.moduleKey)} disabled={syncing || syncingAll}
                        className="h-9 px-4 rounded-xl text-[13px] font-medium text-[#007AFF] bg-[#007AFF]/10 hover:bg-[#007AFF]/20 active:scale-[0.98] disabled:opacity-50 transition-all flex items-center gap-1.5 flex-shrink-0">
                        {syncing ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <RefreshCcw className="w-3.5 h-3.5" />}
                        同步
                      </button>
                    </div>
                  );
                })}
              </div>
            </div>

            {/* ========== 同步日志 ========== */}
            <div className="bg-white rounded-2xl shadow-[0_2px_12px_rgba(0,0,0,0.04)] overflow-hidden">
              <div className="px-6 py-4 flex items-center justify-between border-b border-[#E5E5EA]">
                <div className="flex items-center gap-2">
                  <History className="w-4.5 h-4.5 text-[#8E8E93]" />
                  <h2 className="text-[16px] font-bold text-[#1C1C1E]">同步日志</h2>
                  <span className="text-[12px] text-[#8E8E93]">最近 {logs.length} 条</span>
                </div>
                <button onClick={handleClearLogs} disabled={logs.length === 0}
                  className="h-8 px-3 rounded-lg text-[12px] font-medium text-[#FF3B30] hover:bg-[#FF3B30]/10 disabled:opacity-40 transition-all flex items-center gap-1">
                  <Trash2 className="w-3.5 h-3.5" />清空日志
                </button>
              </div>
              {logs.length === 0 ? (
                <div className="py-14 text-center">
                  <div className="w-14 h-14 rounded-2xl bg-[rgba(118,118,128,0.12)] flex items-center justify-center mx-auto mb-3">
                    <History className="w-7 h-7 text-[#8E8E93]" />
                  </div>
                  <p className="text-[13px] text-[#8E8E93]">暂无同步日志</p>
                </div>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-[13px]">
                    <thead>
                      <tr className="border-b border-[#E5E5EA] text-left">
                        <th className="px-6 py-3 text-[11px] font-semibold text-[#8E8E93]">时间</th>
                        <th className="px-3 py-3 text-[11px] font-semibold text-[#8E8E93]">模块</th>
                        <th className="px-3 py-3 text-[11px] font-semibold text-[#8E8E93]">类型</th>
                        <th className="px-3 py-3 text-[11px] font-semibold text-[#8E8E93]">增量范围</th>
                        <th className="px-3 py-3 text-[11px] font-semibold text-[#8E8E93] text-right">记录数</th>
                        <th className="px-3 py-3 text-[11px] font-semibold text-[#8E8E93]">状态</th>
                        <th className="px-3 py-3 text-[11px] font-semibold text-[#8E8E93] text-right">耗时</th>
                        <th className="px-6 py-3 text-[11px] font-semibold text-[#8E8E93]">信息</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-[#E5E5EA]">
                      {logs.map(log => {
                        const badge = statusBadge(log.status);
                        return (
                          <tr key={log.id} className="hover:bg-[#F9F9FB] transition-colors">
                            <td className="px-6 py-3 text-[#3A3A3C] whitespace-nowrap tabular-nums">{log.time || '-'}</td>
                            <td className="px-3 py-3 text-[#1C1C1E] font-medium whitespace-nowrap">{log.moduleName}</td>
                            <td className="px-3 py-3">
                              <span className="text-[11px] px-2 py-0.5 rounded-full bg-[rgba(118,118,128,0.12)] text-[#3A3A3C]">
                                {log.syncType === 'full' ? '全量' : '增量'}
                              </span>
                            </td>
                            <td className="px-3 py-3 text-[#8E8E93] text-[12px] whitespace-nowrap tabular-nums">
                              {log.rangeStart ? `${log.rangeStart} → ${log.rangeEnd || ''}` : '—'}
                            </td>
                            <td className="px-3 py-3 text-right tabular-nums">
                              <span className="text-[#34C759] font-semibold">+{fmtNum(log.added)}</span>
                              {(log.failed ?? 0) > 0 && <span className="text-[#FF3B30] ml-1">/ -{log.failed}</span>}
                            </td>
                            <td className="px-3 py-3">
                              <span className={`text-[11px] px-2 py-0.5 rounded-full ${badge.cls}`}>{badge.text}</span>
                            </td>
                            <td className="px-3 py-3 text-right text-[#8E8E93] tabular-nums whitespace-nowrap">
                              {log.duration != null ? `${(log.duration / 1000).toFixed(1)}s` : '-'}
                            </td>
                            <td className="px-6 py-3 text-[#8E8E93] text-[12px] max-w-[220px] truncate" title={log.message || ''}>
                              {log.message || '-'}
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </>
        )}
      </main>
    </div>
  );
}
