'use client';

import React from 'react';
import {
  FileText, ShoppingCart, Factory, BadgeCheck, RefreshCw,
  TrendingUp, TrendingDown, PieChart as PieChartIcon, LineChart as LineChartIcon,
  ClipboardList, DollarSign, Hash, User
} from 'lucide-react';
import {
  ResponsiveContainer, LineChart, Line, XAxis, YAxis, CartesianGrid,
  Tooltip, Legend, PieChart, Pie, Cell
} from 'recharts';

// ============ 类型定义（对接 Java DocumentStats API） ============
interface OverviewData {
  quotationAmount: number;
  quotationCount: number;
  salesAmount: number;
  salesOrderCount: number;
  gongyidanCount: number;
  passRate: number;
  quotationMonthTrend: number | null;
  salesMonthTrend: number | null;
}

interface TrendPoint { day: string; amount: number }
interface TrendData { days: number; quotation: TrendPoint[]; sales: TrendPoint[] }
interface StatusSlice { name: string; value: number }

interface RecentQuotation {
  dh: string; date: string; customer: string; huohao: string;
  spname: string; saleprice: number; cost: number; passRate: number;
}

interface RecentGongyidan {
  bh: string; hhtype: string; huohao: string; spname: string;
  designer: string; unit: string; rsjgh: string;
  bomCount: number; machineCount: number; processCount: number; priceCount: number;
}

// 环形图配色（iOS 功能色系，避免高饱和荧光）
const PIE_COLORS = ['#007AFF', '#34C759', '#FF9500', '#AF52DE', '#5AC8FA', '#FF3B30', '#8E8E93'];

function fmtMoney(v: number | null | undefined): string {
  if (v == null || isNaN(v)) return '-';
  if (Math.abs(v) >= 10000) return `${(v / 10000).toFixed(2)}万`;
  return v.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function TrendBadge({ value }: { value: number | null }) {
  if (value == null) return null;
  const up = value >= 0;
  return (
    <span className={`inline-flex items-center gap-0.5 px-2 py-0.5 rounded-full text-xs font-medium ${
      up ? 'bg-[#34C759]/10 text-[#34C759]' : 'bg-[#FF3B30]/10 text-[#FF3B30]'
    }`}>
      {up ? <TrendingUp className="w-3 h-3" /> : <TrendingDown className="w-3 h-3" />}
      {up ? '+' : ''}{value.toFixed(1)}%
    </span>
  );
}

// ============ 主组件：三单据数据概览 ============
export default function DocumentStatsDashboard() {
  const [loading, setLoading] = React.useState(true);
  const [overview, setOverview] = React.useState<OverviewData | null>(null);
  const [trend, setTrend] = React.useState<TrendData | null>(null);
  const [statusDist, setStatusDist] = React.useState<StatusSlice[]>([]);
  const [recentQuotations, setRecentQuotations] = React.useState<RecentQuotation[]>([]);
  const [recentGongyidan, setRecentGongyidan] = React.useState<RecentGongyidan[]>([]);
  const [error, setError] = React.useState<string | null>(null);

  const loadData = React.useCallback(async () => {
    setLoading(true);
    setError(null);
    const sessionId = localStorage.getItem('session_id');
    const headers: Record<string, string> = {};
    if (sessionId) headers['X-Session-Id'] = sessionId;

    const safeGet = async (url: string) => {
      try {
        const res = await fetch(url, { headers });
        if (!res.ok) return null;
        const json = await res.json();
        // Java ApiResponse: {success, data} 或直接数据
        return json?.data ?? json;
      } catch {
        return null;
      }
    };

    const [ov, tr, st, rq, rg] = await Promise.all([
      safeGet('/api/document-stats/overview'),
      safeGet('/api/document-stats/trend?days=30'),
      safeGet('/api/document-stats/gongyidan-status'),
      safeGet('/api/document-stats/recent-quotations?limit=8'),
      safeGet('/api/document-stats/recent-gongyidan?limit=8'),
    ]);

    if (!ov && !tr && !st) {
      setError('统计数据暂不可用（后端服务未连接），请稍后重试');
    }
    if (ov) setOverview(ov);
    if (tr) setTrend(tr);
    if (Array.isArray(st)) setStatusDist(st);
    if (Array.isArray(rq)) setRecentQuotations(rq);
    if (Array.isArray(rg)) setRecentGongyidan(rg);
    setLoading(false);
  }, []);

  React.useEffect(() => { loadData(); }, [loadData]);

  // 合并报价/销售趋势为同一日期轴
  const mergedTrend = React.useMemo(() => {
    if (!trend) return [];
    const map = new Map<string, { day: string; quotation: number; sales: number }>();
    (trend.quotation || []).forEach(p => {
      map.set(p.day, { day: p.day, quotation: Number(p.amount) || 0, sales: 0 });
    });
    (trend.sales || []).forEach(p => {
      const existing = map.get(p.day);
      if (existing) existing.sales = Number(p.amount) || 0;
      else map.set(p.day, { day: p.day, quotation: 0, sales: Number(p.amount) || 0 });
    });
    return Array.from(map.values());
  }, [trend]);

  if (loading) {
    return (
      <div className="flex items-center justify-center py-20">
        <RefreshCw className="w-6 h-6 text-[#007AFF] animate-spin mr-2" />
        <span className="text-[#8e8e93]">加载单据统计中...</span>
      </div>
    );
  }

  if (error) {
    return (
      <div className="bg-white rounded-2xl p-10 shadow-[0_2px_12px_rgba(0,0,0,0.04)] text-center">
        <Factory className="w-10 h-10 text-[#8E8E93] mx-auto mb-3" />
        <p className="text-[#3A3A3C] font-medium">{error}</p>
        <button
          onClick={loadData}
          className="mt-4 px-4 py-2 bg-[#007AFF] text-white text-sm font-medium rounded-xl hover:opacity-90 active:scale-[0.97] transition-all"
        >
          重新加载
        </button>
      </div>
    );
  }

  const cards = [
    {
      label: '报价金额', value: `¥${fmtMoney(overview?.quotationAmount)}`,
      icon: <DollarSign className="w-5 h-5" />, iconBg: 'bg-[#007AFF]/10', iconColor: 'text-[#007AFF]',
      trend: overview?.quotationMonthTrend ?? null,
    },
    {
      label: '报价笔数', value: overview?.quotationCount ?? 0,
      icon: <FileText className="w-5 h-5" />, iconBg: 'bg-[#5AC8FA]/10', iconColor: 'text-[#5AC8FA]',
      suffix: '笔',
    },
    {
      label: '销售金额', value: `¥${fmtMoney(overview?.salesAmount)}`,
      icon: <ShoppingCart className="w-5 h-5" />, iconBg: 'bg-[#34C759]/10', iconColor: 'text-[#34C759]',
      trend: overview?.salesMonthTrend ?? null,
    },
    {
      label: '订单数', value: overview?.salesOrderCount ?? 0,
      icon: <Hash className="w-5 h-5" />, iconBg: 'bg-[#FF9500]/10', iconColor: 'text-[#FF9500]',
      suffix: '单',
    },
    {
      label: '在制工艺数', value: overview?.gongyidanCount ?? 0,
      icon: <Factory className="w-5 h-5" />, iconBg: 'bg-[#AF52DE]/10', iconColor: 'text-[#AF52DE]',
      suffix: '张',
    },
    {
      label: '合格率', value: `${overview?.passRate ?? 0}`,
      icon: <BadgeCheck className="w-5 h-5" />, iconBg: 'bg-[#34C759]/10', iconColor: 'text-[#34C759]',
      suffix: '%',
    },
  ];

  return (
    <div className="space-y-6">
      {/* 6 个数据概览卡片 */}
      <div className="grid grid-cols-2 lg:grid-cols-3 gap-4">
        {cards.map((card, i) => (
          <div key={i} className="bg-white rounded-2xl p-5 shadow-[0_2px_12px_rgba(0,0,0,0.04)] hover:shadow-[0_6px_24px_rgba(0,0,0,0.08)] hover:-translate-y-[3px] transition-all duration-300">
            <div className="flex items-start justify-between mb-4">
              <div className={`w-10 h-10 rounded-xl ${card.iconBg} flex items-center justify-center ${card.iconColor}`}>
                {card.icon}
              </div>
              {card.trend !== undefined && <TrendBadge value={card.trend ?? null} />}
            </div>
            <h3 className="text-2xl font-bold tabular-nums text-[#1C1C1E]">
              {card.value}
              {card.suffix && <span className="text-sm font-normal text-[#8E8E93] ml-1">{card.suffix}</span>}
            </h3>
            <p className="text-sm text-[#8E8E93] mt-1">{card.label}</p>
          </div>
        ))}
      </div>
      {overview && (
        <p className="text-xs text-[#8E8E93] -mt-3 px-1">销售金额为估算口径（订单数量 × 货号最新报价），环比为本月与上月笔数对比</p>
      )}

      {/* 图表行：趋势折线图 + 状态环形图 */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        {/* 报价 & 销售金额趋势 */}
        <div className="lg:col-span-2 bg-white rounded-2xl p-5 shadow-[0_2px_12px_rgba(0,0,0,0.04)]">
          <h3 className="text-lg font-semibold text-[#1c1c1e] mb-1 flex items-center gap-2">
            <LineChartIcon className="w-5 h-5 text-[#007AFF]" />报价 & 销售金额趋势
          </h3>
          <p className="text-xs text-[#8E8E93] mb-4">近 {trend?.days ?? 30} 天按制单日期聚合</p>
          {mergedTrend.length === 0 ? (
            <div className="h-64 flex items-center justify-center text-[#8E8E93] text-sm">暂无趋势数据</div>
          ) : (
            <div className="h-64">
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={mergedTrend} margin={{ top: 8, right: 12, left: 0, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#E5E5EA" vertical={false} />
                  <XAxis
                    dataKey="day" tick={{ fontSize: 11, fill: '#8E8E93' }}
                    axisLine={{ stroke: '#E5E5EA' }} tickLine={false}
                  />
                  <YAxis
                    tick={{ fontSize: 11, fill: '#8E8E93' }}
                    axisLine={false} tickLine={false}
                    tickFormatter={(v: number) => (v >= 10000 ? `${(v / 10000).toFixed(1)}万` : String(v))}
                  />
                  <Tooltip
                    formatter={(value: number | string, name: string) => [
                      `¥${Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 2 })}`,
                      name === 'quotation' ? '报价金额' : '销售金额',
                    ]}
                    contentStyle={{
                      borderRadius: 12, border: '1px solid #E5E5EA',
                      boxShadow: '0 6px 24px rgba(0,0,0,0.08)', fontSize: 12,
                    }}
                  />
                  <Legend
                    formatter={(v: string) => (v === 'quotation' ? '报价金额' : '销售金额')}
                    wrapperStyle={{ fontSize: 12 }}
                  />
                  <Line type="monotone" dataKey="quotation" stroke="#007AFF" strokeWidth={2.5}
                    dot={false} activeDot={{ r: 4 }} />
                  <Line type="monotone" dataKey="sales" stroke="#34C759" strokeWidth={2.5}
                    dot={false} activeDot={{ r: 4 }} />
                </LineChart>
              </ResponsiveContainer>
            </div>
          )}
        </div>

        {/* 工艺单状态分布 */}
        <div className="bg-white rounded-2xl p-5 shadow-[0_2px_12px_rgba(0,0,0,0.04)]">
          <h3 className="text-lg font-semibold text-[#1c1c1e] mb-1 flex items-center gap-2">
            <PieChartIcon className="w-5 h-5 text-[#AF52DE]" />工艺单状态分布
          </h3>
          <p className="text-xs text-[#8E8E93] mb-4">按货号类型统计</p>
          {statusDist.length === 0 ? (
            <div className="h-64 flex items-center justify-center text-[#8E8E93] text-sm">暂无分布数据</div>
          ) : (
            <div className="h-64">
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie
                    data={statusDist} dataKey="value" nameKey="name"
                    innerRadius="55%" outerRadius="80%" paddingAngle={2}
                    stroke="none"
                  >
                    {statusDist.map((_, i) => (
                      <Cell key={i} fill={PIE_COLORS[i % PIE_COLORS.length]} />
                    ))}
                  </Pie>
                  <Tooltip
                    formatter={(value: number | string, name: string) => [`${value} 张`, name]}
                    contentStyle={{
                      borderRadius: 12, border: '1px solid #E5E5EA',
                      boxShadow: '0 6px 24px rgba(0,0,0,0.08)', fontSize: 12,
                    }}
                  />
                  <Legend wrapperStyle={{ fontSize: 12 }} />
                </PieChart>
              </ResponsiveContainer>
            </div>
          )}
        </div>
      </div>

      {/* 列表行：最近报价单 + 最近工艺单 */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        {/* 最近报价单 */}
        <div className="bg-white rounded-2xl shadow-[0_2px_12px_rgba(0,0,0,0.04)] overflow-hidden">
          <div className="px-5 py-4 border-b border-[#E5E5EA] flex items-center justify-between">
            <h3 className="text-lg font-semibold text-[#1c1c1e] flex items-center gap-2">
              <FileText className="w-5 h-5 text-[#007AFF]" />最近报价单
            </h3>
            <span className="text-xs text-[#8E8E93]">{recentQuotations.length} 条</span>
          </div>
          <div className="divide-y divide-[#E5E5EA]">
            {recentQuotations.length === 0 ? (
              <div className="py-12 text-center text-[#8E8E93] text-sm">暂无报价单数据</div>
            ) : recentQuotations.map((q, i) => (
              <div key={i} className="px-5 py-3 hover:bg-[#F9F9FB] transition-colors">
                <div className="flex items-center justify-between mb-1">
                  <span className="font-mono text-sm font-medium text-[#007AFF]">{q.dh || '-'}</span>
                  <span className="text-xs text-[#8E8E93] tabular-nums">{q.date || '-'}</span>
                </div>
                <div className="flex items-center justify-between">
                  <div className="text-sm text-[#3A3A3C] truncate mr-3">
                    {q.customer || '-'}
                    {q.huohao && <span className="text-[#8E8E93] ml-2">货号 {q.huohao}</span>}
                    {q.spname && <span className="text-[#8E8E93] ml-2">{q.spname}</span>}
                  </div>
                  <div className="shrink-0 text-right">
                    <span className="text-sm font-semibold text-[#1C1C1E] tabular-nums">¥{fmtMoney(q.saleprice)}</span>
                    {q.passRate != null && q.passRate > 0 && (
                      <span className="ml-2 text-xs bg-[#34C759]/10 text-[#34C759] px-1.5 py-0.5 rounded-full">
                        合格率 {q.passRate > 1 ? q.passRate.toFixed(0) : (q.passRate * 100).toFixed(0)}%
                      </span>
                    )}
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* 最近工艺单（带关联数据计数） */}
        <div className="bg-white rounded-2xl shadow-[0_2px_12px_rgba(0,0,0,0.04)] overflow-hidden">
          <div className="px-5 py-4 border-b border-[#E5E5EA] flex items-center justify-between">
            <h3 className="text-lg font-semibold text-[#1c1c1e] flex items-center gap-2">
              <ClipboardList className="w-5 h-5 text-[#AF52DE]" />最近工艺单
            </h3>
            <span className="text-xs text-[#8E8E93]">{recentGongyidan.length} 条</span>
          </div>
          <div className="divide-y divide-[#E5E5EA]">
            {recentGongyidan.length === 0 ? (
              <div className="py-12 text-center text-[#8E8E93] text-sm">暂无工艺单数据</div>
            ) : recentGongyidan.map((g, i) => (
              <div key={i} className="px-5 py-3 hover:bg-[#F9F9FB] transition-colors">
                <div className="flex items-center justify-between mb-1">
                  <span className="font-mono text-sm font-medium text-[#AF52DE]">{g.bh || '-'}</span>
                  {g.hhtype && (
                    <span className="text-xs bg-[#AF52DE]/10 text-[#AF52DE] px-2 py-0.5 rounded-full">{g.hhtype}</span>
                  )}
                </div>
                <div className="flex items-center justify-between mb-2">
                  <div className="text-sm text-[#3A3A3C] truncate mr-3">
                    {g.huohao && <span className="mr-2">货号 {g.huohao}</span>}
                    {g.spname && <span className="text-[#8E8E93] mr-2">{g.spname}</span>}
                    {g.designer && (
                      <span className="text-[#8E8E93] inline-flex items-center gap-0.5">
                        <User className="w-3 h-3" />{g.designer}
                      </span>
                    )}
                  </div>
                </div>
                {/* 关联数据计数：BOM / 机台 / 工序 / 工价 */}
                <div className="flex gap-2 flex-wrap">
                  <span className="text-[11px] bg-[rgba(118,118,128,0.12)] text-[#3A3A3C] px-2 py-0.5 rounded-md">BOM {g.bomCount}</span>
                  <span className="text-[11px] bg-[rgba(118,118,128,0.12)] text-[#3A3A3C] px-2 py-0.5 rounded-md">机台 {g.machineCount}</span>
                  <span className="text-[11px] bg-[rgba(118,118,128,0.12)] text-[#3A3A3C] px-2 py-0.5 rounded-md">工序 {g.processCount}</span>
                  <span className="text-[11px] bg-[rgba(118,118,128,0.12)] text-[#3A3A3C] px-2 py-0.5 rounded-md">工价 {g.priceCount}</span>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
