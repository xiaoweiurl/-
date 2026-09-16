'use client';

import { useCallback, useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import {
  ArrowLeft,
  Building2,
  ChevronRight,
  Loader2,
  RefreshCcw,
  Search,
  ShieldAlert,
} from 'lucide-react';

interface SyncState {
  company?: string;
  status?: string;
  message?: string;
  deptCount?: number;
  userCount?: number;
  durationMs?: number;
  lastSyncAt?: string | null;
}

interface DeptNode {
  id: string;
  dingDeptId: number;
  parentDingDeptId?: number | null;
  name: string;
  path?: string;
  userCount?: number;
  children?: DeptNode[];
}

interface Contact {
  dingtalkUserid: string;
  name: string;
  jobTitle?: string;
  deptName?: string;
  deptPath?: string;
  alreadyRegistered?: boolean;
}

function statusText(status?: string) {
  switch (status) {
    case 'success':
      return { text: '成功', cls: 'bg-[#34C759]/10 text-[#34C759]' };
    case 'failed':
      return { text: '失败', cls: 'bg-[#FF3B30]/10 text-[#FF3B30]' };
    default:
      return { text: '未同步', cls: 'bg-[rgba(118,118,128,0.12)] text-[#8E8E93]' };
  }
}

function DeptTree({ nodes, depth = 0 }: { nodes: DeptNode[]; depth?: number }) {
  if (!nodes?.length) return null;
  return (
    <ul className={depth === 0 ? 'space-y-1' : 'mt-1 ml-4 space-y-1 border-l border-[#E5E5EA] pl-3'}>
      {nodes.map((node) => (
        <li key={node.id || String(node.dingDeptId)}>
          <div className="flex items-center gap-2 py-1.5 text-sm">
            <ChevronRight className="w-3.5 h-3.5 text-[#8E8E93]" />
            <span className="text-[#1C1C1E] font-medium">{node.name}</span>
            <span className="text-xs text-[#8E8E93]">{node.userCount ?? 0} 人</span>
          </div>
          {node.children && node.children.length > 0 && (
            <DeptTree nodes={node.children} depth={depth + 1} />
          )}
        </li>
      ))}
    </ul>
  );
}

export default function OrgPage() {
  const router = useRouter();
  const [roleChecked, setRoleChecked] = useState(false);
  const [isAdmin, setIsAdmin] = useState(false);
  const [configured, setConfigured] = useState(false);
  const [sync, setSync] = useState<SyncState | null>(null);
  const [tree, setTree] = useState<DeptNode[]>([]);
  const [contacts, setContacts] = useState<Contact[]>([]);
  const [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(true);
  const [syncing, setSyncing] = useState(false);
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

  const loadAll = useCallback(async () => {
    setLoading(true);
    try {
      const [statusRes, deptRes] = await Promise.all([
        fetch('/api/org/status', { headers: sessionHeaders(), credentials: 'include' }),
        fetch('/api/org/departments', { headers: sessionHeaders(), credentials: 'include' }),
      ]);
      const statusJson = await statusRes.json();
      const deptJson = await deptRes.json();
      if (statusJson.success) {
        setConfigured(Boolean(statusJson.data?.configured));
        setSync(statusJson.data?.sync || null);
      }
      if (deptJson.success) {
        setTree(deptJson.data || []);
      }
    } catch {
      showToast('error', '加载组织数据失败');
    } finally {
      setLoading(false);
    }
  }, [sessionHeaders]);

  useEffect(() => {
    fetch('/api/auth/login', { credentials: 'include' })
      .then((res) => (res.ok ? res.json() : null))
      .then((data) => {
        const role = data?.success ? String(data.data?.role || '').toLowerCase() : '';
        setIsAdmin(role === 'admin' || role === 'superadmin');
        setRoleChecked(true);
      })
      .catch(() => setRoleChecked(true));
  }, []);

  useEffect(() => {
    if (isAdmin) loadAll();
  }, [isAdmin, loadAll]);

  const handleSync = async () => {
    setSyncing(true);
    try {
      const res = await fetch('/api/org/sync', {
        method: 'POST',
        headers: sessionHeaders(),
        credentials: 'include',
      });
      const json = await res.json();
      if (json.success) {
        showToast('success', `同步完成：${json.data?.deptCount ?? 0} 个部门，${json.data?.userCount ?? 0} 人`);
        await loadAll();
      } else {
        showToast('error', json.message || json.error || '同步失败');
      }
    } catch {
      showToast('error', '同步请求失败');
    } finally {
      setSyncing(false);
    }
  };

  const handleSearch = async () => {
    try {
      const q = keyword.trim() ? `?name=${encodeURIComponent(keyword.trim())}` : '';
      const res = await fetch(`/api/org/contacts${q}`, {
        headers: sessionHeaders(),
        credentials: 'include',
      });
      const json = await res.json();
      if (json.success) setContacts(json.data || []);
      else showToast('error', json.message || '搜索失败');
    } catch {
      showToast('error', '搜索失败');
    }
  };

  if (!roleChecked) {
    return (
      <div className="min-h-screen bg-[#F2F2F7] flex items-center justify-center">
        <Loader2 className="w-6 h-6 animate-spin text-[#8E8E93]" />
      </div>
    );
  }

  if (!isAdmin) {
    return (
      <div className="min-h-screen bg-[#F2F2F7] flex items-center justify-center p-4">
        <div className="bg-white rounded-2xl border border-[#E5E5EA] p-8 max-w-md text-center">
          <ShieldAlert className="w-10 h-10 text-[#FF9500] mx-auto mb-3" />
          <h1 className="text-lg font-semibold text-[#1C1C1E]">无权限</h1>
          <p className="text-sm text-[#8E8E93] mt-2">仅管理员可同步钉钉组织（办公/管理端）。</p>
          <button
            onClick={() => router.push('/')}
            className="mt-6 text-sm text-[#007AFF]"
          >
            返回首页
          </button>
        </div>
      </div>
    );
  }

  const badge = statusText(sync?.status);

  return (
    <div className="min-h-screen bg-[#F2F2F7]">
      {toast && (
        <div className={`fixed top-4 right-4 z-50 rounded-xl px-4 py-3 text-sm shadow-lg ${
          toast.type === 'success' ? 'bg-white text-[#34C759] border border-[#34C759]/30' : 'bg-white text-[#FF3B30] border border-[#FF3B30]/30'
        }`}>
          {toast.text}
        </div>
      )}

      <header className="h-14 bg-white border-b border-[#E5E5EA] flex items-center px-4 gap-3">
        <button onClick={() => router.push('/')} className="flex items-center gap-1.5 text-sm text-[#8E8E93] hover:text-[#1C1C1E]">
          <ArrowLeft className="w-4 h-4" />
          返回
        </button>
        <Building2 className="w-5 h-5 text-[#007AFF]" />
        <h1 className="text-[15px] font-semibold text-[#1C1C1E]">钉钉组织</h1>
        <span className="text-xs text-[#8E8E93]">办公/管理端</span>
      </header>

      <main className="max-w-5xl mx-auto p-4 space-y-4">
        <section className="bg-white rounded-2xl border border-[#E5E5EA] p-5">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <h2 className="text-base font-semibold text-[#1C1C1E]">同步钉钉组织</h2>
              <p className="text-xs text-[#8E8E93] mt-1">
                {configured ? '已配置企业内部应用凭证' : '未配置 DINGTALK_APP_KEY / DINGTALK_APP_SECRET，同步将失败'}
              </p>
            </div>
            <button
              onClick={handleSync}
              disabled={syncing}
              className="inline-flex items-center gap-2 px-4 py-2 rounded-xl bg-[#007AFF] text-white text-sm disabled:opacity-50"
            >
              {syncing ? <Loader2 className="w-4 h-4 animate-spin" /> : <RefreshCcw className="w-4 h-4" />}
              {syncing ? '同步中...' : '同步钉钉组织'}
            </button>
          </div>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-3 mt-4">
            <div className="rounded-xl bg-[#F2F2F7] p-3">
              <div className="text-xs text-[#8E8E93]">状态</div>
              <span className={`inline-block mt-1 text-xs px-2 py-0.5 rounded-md ${badge.cls}`}>{badge.text}</span>
            </div>
            <div className="rounded-xl bg-[#F2F2F7] p-3">
              <div className="text-xs text-[#8E8E93]">部门</div>
              <div className="text-lg font-semibold text-[#1C1C1E] mt-1">{sync?.deptCount ?? 0}</div>
            </div>
            <div className="rounded-xl bg-[#F2F2F7] p-3">
              <div className="text-xs text-[#8E8E93]">通讯录</div>
              <div className="text-lg font-semibold text-[#1C1C1E] mt-1">{sync?.userCount ?? 0}</div>
            </div>
            <div className="rounded-xl bg-[#F2F2F7] p-3">
              <div className="text-xs text-[#8E8E93]">上次同步</div>
              <div className="text-sm text-[#3A3A3C] mt-1">{sync?.lastSyncAt || '—'}</div>
            </div>
          </div>
          {sync?.message && (
            <p className="text-xs text-[#8E8E93] mt-3">{sync.message}</p>
          )}
        </section>

        <section className="bg-white rounded-2xl border border-[#E5E5EA] p-5">
          <h2 className="text-base font-semibold text-[#1C1C1E] mb-3">部门树</h2>
          {loading ? (
            <Loader2 className="w-5 h-5 animate-spin text-[#8E8E93]" />
          ) : tree.length === 0 ? (
            <p className="text-sm text-[#8E8E93]">暂无部门数据，请先同步。</p>
          ) : (
            <DeptTree nodes={tree} />
          )}
        </section>

        <section className="bg-white rounded-2xl border border-[#E5E5EA] p-5">
          <h2 className="text-base font-semibold text-[#1C1C1E] mb-3">通讯录搜索</h2>
          <div className="flex gap-2">
            <div className="relative flex-1">
              <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-[#8E8E93]" />
              <input
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
                placeholder="按姓名 / 职位 / 部门搜索"
                className="w-full pl-9 pr-3 py-2 rounded-xl border border-[#E5E5EA] text-sm"
              />
            </div>
            <button
              onClick={handleSearch}
              className="px-4 py-2 rounded-xl bg-black/5 text-sm text-[#1C1C1E]"
            >
              搜索
            </button>
          </div>
          <div className="mt-3 space-y-2">
            {contacts.map((c) => (
              <div key={c.dingtalkUserid} className="rounded-xl border border-[#E5E5EA] px-3 py-2">
                <div className="text-sm font-medium text-[#1C1C1E]">{c.name}</div>
                <div className="text-xs text-[#8E8E93]">
                  {c.jobTitle || '未填写职位'}
                  {c.deptPath ? ` · ${c.deptPath}` : ''}
                  {c.alreadyRegistered ? ' · 已注册' : ''}
                </div>
              </div>
            ))}
          </div>
        </section>
      </main>
    </div>
  );
}
