'use client';

import { useState, useEffect, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import { toast } from 'sonner';
import { Toaster } from '@/components/ui/sonner';
import {
  ArrowLeft, Search, RefreshCw, Loader2, ClipboardList, Users, Package,
  CalendarDays, CheckCircle2, Eye, X, ChevronLeft, ChevronRight,
  TrendingUp, Factory, Hash, User, Building2, FileText, Boxes,
} from 'lucide-react';

// ============ 类型定义 ============
interface HistoryOrder {
  dh: string;
  zhdate: string | number | null;
  jhDate: string | number | null;
  state: string;
  stateText: string;
  zxtate: string;
  zxtateText: string;
  businessDh: string | null;
  ddtype: string | null;
  khname: string | null;
  detailhuohaocp: string | null;
  detailhuohao: string | null;
  spname: string | null;
  slSum: number | null;
  remark: string | null;
  ywyname: string | null;
  ywynameText: string;
  sfplan: string | null;
  sfplanText: string;
  zhuser: string | null;
  checkuser: string | null;
  ckeckdate: string | number | null;
}

interface OrderStats {
  totalOrders: number;
  totalQuantity: number;
  customerCount: number;
  plannedCount: number;
  monthNewCount: number;
}

const PAGE_SIZES = [10, 20, 50];

// ============ 工具函数 ============
/** Java Timestamp 序列化兼容（ISO 字符串或 epoch 毫秒） */
function formatDate(v: string | number | null | undefined, withTime = false): string {
  if (v === null || v === undefined || v === '') return '-';
  const d = typeof v === 'number' ? new Date(v) : new Date(String(v));
  if (isNaN(d.getTime())) return String(v);
  const pad = (n: number) => String(n).padStart(2, '0');
  const date = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
  return withTime ? `${date} ${pad(d.getHours())}:${pad(d.getMinutes())}` : date;
}

function formatQty(v: number | null): string {
  if (v === null || v === undefined) return '-';
  return v.toLocaleString('zh-CN', { maximumFractionDigits: 2 });
}

/** 执行状态徽章样式 */
function zxtateBadge(text: string): string {
  if (text === '已复审') return 'bg-emerald-500/10 text-emerald-400 border-emerald-500/25';
  if (text === '已经终审') return 'bg-blue-500/10 text-blue-400 border-blue-500/25';
  return 'bg-slate-500/10 text-slate-400 border-slate-500/25';
}

// ============ 主组件 ============
export default function HistoryOrdersPage() {
  const router = useRouter();

  const [orders, setOrders] = useState<HistoryOrder[]>([]);
  const [stats, setStats] = useState<OrderStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);

  // 筛选条件（输入态 + 生效态分离，点击查询才生效）
  const [keywordInput, setKeywordInput] = useState('');
  const [keyword, setKeyword] = useState('');
  const [zxtate, setZxtate] = useState('');
  const [sfplan, setSfplan] = useState('');
  const [dateFrom, setDateFrom] = useState('');
  const [dateTo, setDateTo] = useState('');

  const [detail, setDetail] = useState<HistoryOrder | null>(null);

  const loadStats = useCallback(async () => {
    try {
      const res = await fetch('/api/history-orders/stats');
      const json = await res.json();
      if (json.success) setStats(json.data);
    } catch { /* 统计失败不阻塞列表 */ }
  }, []);

  const loadOrders = useCallback(async () => {
    setLoading(true);
    try {
      const params = new URLSearchParams({ page: String(page), size: String(size) });
      if (keyword) params.set('keyword', keyword);
      if (zxtate) params.set('zxtate', zxtate);
      if (sfplan) params.set('sfplan', sfplan);
      if (dateFrom) params.set('dateFrom', dateFrom);
      if (dateTo) params.set('dateTo', dateTo);
      const res = await fetch(`/api/history-orders?${params}`);
      if (res.status === 401 || res.status === 403) {
        router.push('/login');
        return;
      }
      const json = await res.json();
      if (json.success) {
        setOrders(json.data.list || []);
        setTotal(json.data.total || 0);
      } else {
        toast.error(json.message || '加载失败');
      }
    } catch {
      toast.error('后端服务暂不可用');
    } finally {
      setLoading(false);
    }
  }, [page, size, keyword, zxtate, sfplan, dateFrom, dateTo, router]);

  useEffect(() => { loadStats(); }, [loadStats]);
  useEffect(() => { loadOrders(); }, [loadOrders]);

  const handleSearch = () => {
    setPage(1);
    setKeyword(keywordInput.trim());
  };

  const handleReset = () => {
    setKeywordInput('');
    setKeyword('');
    setZxtate('');
    setSfplan('');
    setDateFrom('');
    setDateTo('');
    setPage(1);
  };

  const totalPages = Math.max(1, Math.ceil(total / size));

  const STAT_CARDS = [
    { label: '已审核订单', value: stats?.totalOrders, icon: ClipboardList, color: 'text-blue-400', bg: 'bg-blue-500/10 border-blue-500/20' },
    { label: '数量合计', value: stats?.totalQuantity, icon: Package, color: 'text-emerald-400', bg: 'bg-emerald-500/10 border-emerald-500/20', isQty: true },
    { label: '客户数', value: stats?.customerCount, icon: Users, color: 'text-violet-400', bg: 'bg-violet-500/10 border-violet-500/20' },
    { label: '已下计划', value: stats?.plannedCount, icon: CheckCircle2, color: 'text-amber-400', bg: 'bg-amber-500/10 border-amber-500/20' },
    { label: '本月新增', value: stats?.monthNewCount, icon: TrendingUp, color: 'text-cyan-400', bg: 'bg-cyan-500/10 border-cyan-500/20' },
  ];

  return (
    <div className="min-h-screen bg-slate-900 text-slate-200">
      <Toaster position="top-center" richColors />

      {/* Header */}
      <header className="sticky top-0 z-20 bg-slate-900/90 backdrop-blur-xl border-b border-slate-700/50">
        <div className="max-w-[1600px] mx-auto px-4 py-3 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <button
              onClick={() => router.push('/supply-chain')}
              className="flex items-center gap-1 text-sm text-slate-400 hover:text-slate-200 transition-colors shrink-0"
            >
              <ArrowLeft className="w-4 h-4" />
              <span>返回</span>
            </button>
            <span className="text-slate-600">|</span>
            <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-amber-500 to-orange-500 flex items-center justify-center text-white shadow-sm">
              <Factory className="w-4 h-4" />
            </div>
            <div>
              <h1 className="text-[15px] font-bold text-slate-100 leading-tight">历史订单</h1>
              <p className="text-[11px] text-slate-400">已审核投入生产的销售订单</p>
            </div>
          </div>
        </div>
      </header>

      <div className="max-w-[1600px] mx-auto px-4 py-4 space-y-4">
        {/* 统计卡片 */}
        <div className="grid grid-cols-2 md:grid-cols-3 xl:grid-cols-5 gap-3">
          {STAT_CARDS.map(card => (
            <div key={card.label}
              className={cn_card(card.bg)}>
              <div className="flex items-center justify-between">
                <span className="text-[12px] text-slate-400">{card.label}</span>
                <card.icon className={cn_icon(card.color)} />
              </div>
              <div className="mt-2 text-2xl font-bold text-slate-100 font-mono">
                {stats === null ? <Loader2 className="w-5 h-5 animate-spin text-slate-500" />
                  : card.isQty ? formatQty(Number(card.value ?? 0)) : String(card.value ?? 0)}
              </div>
            </div>
          ))}
        </div>

        {/* 筛选栏 */}
        <div className="bg-slate-800/50 rounded-xl border border-slate-700/50 p-3 flex flex-wrap items-center gap-2">
          <div className="relative flex-1 min-w-[220px]">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-500" />
            <input
              value={keywordInput}
              onChange={e => setKeywordInput(e.target.value)}
              onKeyDown={e => e.key === 'Enter' && handleSearch()}
              placeholder="搜索单号 / 业务单号 / 客户 / 货号 / 业务员"
              className="w-full pl-9 pr-3 py-2 rounded-lg bg-slate-900/60 border border-slate-700/60 text-[13px] text-slate-200 placeholder:text-slate-500 focus:outline-none focus:border-amber-500/50"
            />
          </div>
          <select value={zxtate} onChange={e => { setZxtate(e.target.value); setPage(1); }}
            className="px-3 py-2 rounded-lg bg-slate-900/60 border border-slate-700/60 text-[13px] text-slate-300 focus:outline-none focus:border-amber-500/50">
            <option value="">执行状态（全部）</option>
            <option value="1">已复审</option>
            <option value="2">已经终审</option>
            <option value="0">未审核</option>
          </select>
          <select value={sfplan} onChange={e => { setSfplan(e.target.value); setPage(1); }}
            className="px-3 py-2 rounded-lg bg-slate-900/60 border border-slate-700/60 text-[13px] text-slate-300 focus:outline-none focus:border-amber-500/50">
            <option value="">是否下计划（全部）</option>
            <option value="是">已下计划</option>
            <option value="否">未下计划</option>
          </select>
          <div className="flex items-center gap-1.5 text-[13px] text-slate-400">
            <CalendarDays className="w-4 h-4" />
            <input type="date" value={dateFrom} onChange={e => { setDateFrom(e.target.value); setPage(1); }}
              className="px-2 py-2 rounded-lg bg-slate-900/60 border border-slate-700/60 text-slate-300 focus:outline-none focus:border-amber-500/50 [color-scheme:dark]" />
            <span>至</span>
            <input type="date" value={dateTo} onChange={e => { setDateTo(e.target.value); setPage(1); }}
              className="px-2 py-2 rounded-lg bg-slate-900/60 border border-slate-700/60 text-slate-300 focus:outline-none focus:border-amber-500/50 [color-scheme:dark]" />
          </div>
          <button onClick={handleSearch}
            className="px-4 py-2 rounded-lg bg-gradient-to-r from-amber-500 to-orange-500 text-white text-[13px] font-medium hover:opacity-90 transition-opacity">
            查询
          </button>
          <button onClick={handleReset}
            className="px-3 py-2 rounded-lg bg-slate-700/50 text-slate-300 text-[13px] hover:bg-slate-700 transition-colors">
            重置
          </button>
          <button onClick={() => { loadOrders(); loadStats(); }}
            className="p-2 rounded-lg bg-slate-700/50 text-slate-300 hover:bg-slate-700 transition-colors" title="刷新">
            <RefreshCw className="w-4 h-4" />
          </button>
        </div>

        {/* 数据表格 */}
        <div className="bg-slate-800/50 rounded-xl border border-slate-700/50 overflow-hidden">
          {loading ? (
            <div className="flex items-center justify-center py-20">
              <Loader2 className="w-5 h-5 text-amber-500 animate-spin mr-2" />
              <span className="text-slate-500 text-sm">加载中...</span>
            </div>
          ) : orders.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-20 text-slate-500">
              <Boxes className="w-10 h-10 mb-3 text-slate-600" />
              <p className="text-sm">暂无已审核的历史订单</p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-[13px]">
                <thead>
                  <tr className="bg-slate-900/50 text-slate-400 text-left">
                    <th className="px-4 py-3 font-medium whitespace-nowrap">订单号</th>
                    <th className="px-4 py-3 font-medium whitespace-nowrap">品名</th>
                    <th className="px-4 py-3 font-medium whitespace-nowrap">客户</th>
                    <th className="px-4 py-3 font-medium whitespace-nowrap">生产货号</th>
                    <th className="px-4 py-3 font-medium whitespace-nowrap text-right">数量</th>
                    <th className="px-4 py-3 font-medium whitespace-nowrap">下单日期</th>
                    <th className="px-4 py-3 font-medium whitespace-nowrap">交货日期</th>
                    <th className="px-4 py-3 font-medium whitespace-nowrap">执行状态</th>
                    <th className="px-4 py-3 font-medium whitespace-nowrap">下计划</th>
                    <th className="px-4 py-3 font-medium whitespace-nowrap">业务员</th>
                    <th className="px-4 py-3 font-medium whitespace-nowrap text-center">操作</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-700/40">
                  {orders.map(o => (
                    <tr key={o.dh} className="hover:bg-slate-700/20 transition-colors">
                      <td className="px-4 py-3 font-mono text-amber-400/90 whitespace-nowrap">{o.dh}</td>
                      <td className="px-4 py-3 text-slate-200 whitespace-nowrap">{o.spname || '-'}</td>
                      <td className="px-4 py-3 text-slate-300 whitespace-nowrap">{o.khname || '-'}</td>
                      <td className="px-4 py-3 font-mono text-slate-300 whitespace-nowrap">{o.detailhuohao || '-'}</td>
                      <td className="px-4 py-3 text-right font-mono text-slate-200 whitespace-nowrap">{formatQty(o.slSum)}</td>
                      <td className="px-4 py-3 text-slate-400 whitespace-nowrap">{formatDate(o.zhdate)}</td>
                      <td className="px-4 py-3 text-slate-400 whitespace-nowrap">{formatDate(o.jhDate)}</td>
                      <td className="px-4 py-3 whitespace-nowrap">
                        <span className={`px-2 py-0.5 rounded-full text-[11px] border ${zxtateBadge(o.zxtateText)}`}>
                          {o.zxtateText}
                        </span>
                      </td>
                      <td className="px-4 py-3 whitespace-nowrap">
                        <span className={`px-2 py-0.5 rounded-full text-[11px] border ${
                          o.sfplanText === '已下计划'
                            ? 'bg-amber-500/10 text-amber-400 border-amber-500/25'
                            : 'bg-slate-500/10 text-slate-500 border-slate-500/25'
                        }`}>
                          {o.sfplanText}
                        </span>
                      </td>
                      <td className="px-4 py-3 whitespace-nowrap">
                        {o.ywyname
                          ? <span className="text-slate-200">{o.ywyname}</span>
                          : <span className="text-slate-500 text-[12px]">{o.ywynameText}</span>}
                      </td>
                      <td className="px-4 py-3 text-center whitespace-nowrap">
                        <button onClick={() => setDetail(o)}
                          className="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg bg-slate-700/50 text-slate-300 text-[12px] hover:bg-slate-700 hover:text-slate-100 transition-colors">
                          <Eye className="w-3.5 h-3.5" />详情
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {/* 分页 */}
          {!loading && total > 0 && (
            <div className="flex items-center justify-between px-4 py-3 border-t border-slate-700/50 text-[13px] text-slate-400">
              <span>共 {total} 条</span>
              <div className="flex items-center gap-2">
                <select value={size} onChange={e => { setSize(Number(e.target.value)); setPage(1); }}
                  className="px-2 py-1.5 rounded-lg bg-slate-900/60 border border-slate-700/60 text-slate-300 focus:outline-none">
                  {PAGE_SIZES.map(s => <option key={s} value={s}>{s} 条/页</option>)}
                </select>
                <button disabled={page <= 1} onClick={() => setPage(p => p - 1)}
                  className="p-1.5 rounded-lg bg-slate-700/50 disabled:opacity-40 hover:bg-slate-700 transition-colors">
                  <ChevronLeft className="w-4 h-4" />
                </button>
                <span className="font-mono">{page} / {totalPages}</span>
                <button disabled={page >= totalPages} onClick={() => setPage(p => p + 1)}
                  className="p-1.5 rounded-lg bg-slate-700/50 disabled:opacity-40 hover:bg-slate-700 transition-colors">
                  <ChevronRight className="w-4 h-4" />
                </button>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* 详情弹窗 */}
      {detail && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4"
          onClick={() => setDetail(null)}>
          <div className="w-full max-w-2xl max-h-[85vh] overflow-y-auto bg-slate-800 rounded-2xl border border-slate-700/60 shadow-2xl"
            onClick={e => e.stopPropagation()}>
            <div className="sticky top-0 flex items-center justify-between px-5 py-4 bg-slate-800/95 backdrop-blur border-b border-slate-700/50">
              <div>
                <h2 className="text-[15px] font-bold text-slate-100">订单详情</h2>
                <p className="text-[12px] text-slate-400 font-mono">{detail.dh}</p>
              </div>
              <button onClick={() => setDetail(null)}
                className="p-1.5 rounded-lg text-slate-400 hover:bg-slate-700 hover:text-slate-200 transition-colors">
                <X className="w-4 h-4" />
              </button>
            </div>
            <div className="p-5 grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-3 text-[13px]">
              <DetailRow icon={Hash} label="订单号" value={detail.dh} mono />
              <DetailRow icon={FileText} label="业务单号" value={detail.businessDh} mono />
              <DetailRow icon={Package} label="品名" value={detail.spname} />
              <DetailRow icon={Hash} label="生产货号" value={detail.detailhuohao} mono />
              <DetailRow icon={Hash} label="成品货号" value={detail.detailhuohaocp} mono />
              <DetailRow icon={Building2} label="客户名称" value={detail.khname} />
              <DetailRow icon={User} label="业务员" value={detail.ywynameText} highlight={!detail.ywyname} />
              <DetailRow icon={FileText} label="销售类型" value={detail.ddtype} />
              <DetailRow icon={Package} label="数量合计" value={formatQty(detail.slSum)} mono />
              <DetailRow icon={CalendarDays} label="下单日期" value={formatDate(detail.zhdate, true)} mono />
              <DetailRow icon={CalendarDays} label="交货日期" value={formatDate(detail.jhDate, true)} mono />
              <DetailRow icon={CheckCircle2} label="订单状态" value={detail.stateText} />
              <DetailRow icon={CheckCircle2} label="执行状态" value={detail.zxtateText} />
              <DetailRow icon={CheckCircle2} label="是否下计划" value={detail.sfplanText} />
              <DetailRow icon={User} label="制单人" value={detail.zhuser} />
              <DetailRow icon={User} label="审核人" value={detail.checkuser} />
              <DetailRow icon={CalendarDays} label="审核日期" value={formatDate(detail.ckeckdate, true)} mono />
              {detail.remark && (
                <div className="sm:col-span-2">
                  <DetailRow icon={FileText} label="备注" value={detail.remark} />
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

// ============ 样式辅助（避免重复类名串） ============
function cn_card(bg: string): string {
  return `rounded-xl border p-4 bg-slate-800/50 backdrop-blur ${bg}`;
}
function cn_icon(color: string): string {
  return `w-4 h-4 ${color}`;
}

// ============ 详情行组件 ============
function DetailRow({ icon: Icon, label, value, mono, highlight }: {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  value: string | null;
  mono?: boolean;
  highlight?: boolean;
}) {
  return (
    <div className="flex items-start gap-2.5 py-1">
      <Icon className="w-4 h-4 mt-0.5 text-slate-500 shrink-0" />
      <div className="min-w-0">
        <div className="text-[11px] text-slate-500">{label}</div>
        <div className={`text-slate-200 break-all ${mono ? 'font-mono' : ''} ${highlight ? 'text-slate-500' : ''}`}>
          {value || '-'}
        </div>
      </div>
    </div>
  );
}
