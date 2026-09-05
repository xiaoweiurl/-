'use client';

import React, { useState, useEffect, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import {
  Sparkles, Brain, MessageSquare, Image, Globe, Search,
  BarChart3, TrendingUp, Zap, Clock, ArrowRight, Activity,
  Bot, BookOpen, Eye, Cpu, ChevronRight, RefreshCw,
  CheckCircle, XCircle, AlertTriangle, Server,
  Mic, FileText, Wand2, Lightbulb, Layers, Shield,
  Play, ExternalLink, Database, PieChart
} from 'lucide-react';

// ===== 类型定义 =====
interface AICapability {
  id: string;
  name: string;
  description: string;
  icon: React.ReactNode;
  color: string;
  bgColor: string;
  borderColor: string;
  status: 'online' | 'offline' | 'beta';
  category: string;
  route: string;
  features: string[];
}

interface CallTrend {
  date: string;
  calls: number;
  success: number;
  fail: number;
}

interface ModelUsage {
  model: string;
  calls: number;
  tokens: number;
  capability?: string;
  todayCalls?: number;
  avgLatency?: number;
  successRate?: number;
}

// ===== 能力标识 → 中文名映射（与后端 AiCallLogService 的能力常量一致） =====
const CAPABILITY_NAMES: Record<string, string> = {
  'smart-chat': 'AI 智能对话',
  'factory-chat': '工厂供应链助手',
  'marketing-chat': '市场营销对话',
  'web-search': '联网搜索引擎',
  'embedding': '向量 Embedding',
  'ai-recognize': 'AI 智能识别',
  'ai-image': 'AI 智能生图',
  'quotation': '智能报价引擎',
};
const capName = (cap: string) => CAPABILITY_NAMES[cap] || cap;

// ===== AI 能力数据 =====
const AI_CAPABILITIES: AICapability[] = [
  {
    id: 'smart-chat',
    name: 'AI 智能对话',
    description: '基于 qwen3.6 本地大模型的多轮智能对话，支持思考模式、联网搜索、知识库检索',
    icon: <MessageSquare className="w-6 h-6" />,
    color: 'text-[#007aff]',
    bgColor: 'bg-[rgba(0,122,255,0.1)]',
    borderColor: 'border-[rgba(0,122,255,0.2)]',
    status: 'online',
    category: '对话能力',
    route: '/chat',
    features: ['qwen3.6 本地模型', '思考模式', '联网搜索', '知识库检索', '多轮对话'],
  },
  {
    id: 'ai-image',
    name: 'AI 智能生图',
    description: '支持文字生图和图生图，多种模型可选，高分辨率输出，支持批量生成和风格控制',
    icon: <Image className="w-6 h-6" />,
    color: 'text-[#007aff]',
    bgColor: 'bg-[rgba(0,122,255,0.1)]',
    borderColor: 'border-[rgba(0,122,255,0.2)]',
    status: 'online',
    category: '生成能力',
    route: '/ai-image',
    features: ['文生图', '图生图', '多模型选择', '高分辨率', '批量生成', '风格控制'],
  },
  {
    id: 'ai-recognize',
    name: 'AI 智能识别',
    description: '基于 qwen3.6:35b 多模态模型的图片内容识别，自动分类、标签提取、场景理解',
    icon: <Eye className="w-6 h-6" />,
    color: 'text-[#007aff]',
    bgColor: 'bg-[rgba(0,122,255,0.1)]',
    borderColor: 'border-[rgba(0,122,255,0.2)]',
    status: 'online',
    category: '识别能力',
    route: '/',
    features: ['图片分类', '标签提取', '场景理解', '自动归类', '批量识别'],
  },
  {
    id: 'factory-chat',
    name: '工厂供应链助手',
    description: '专注供应链与工厂业务的 AI 助手，支持智能报价、成本计算、供应商对比分析',
    icon: <Cpu className="w-6 h-6" />,
    color: 'text-[#ff9500]',
    bgColor: 'bg-[rgba(255,149,0,0.1)]',
    borderColor: 'border-[rgba(255,149,0,0.2)]',
    status: 'online',
    category: '对话能力',
    route: '/supply-chain',
    features: ['成本计算', '智能报价', '供应商分析', '联网搜索', '十步成本法'],
  },
  {
    id: 'knowledge-search',
    name: '知识库语义检索',
    description: '基于 bge-m3 向量模型的语义搜索引擎，支持文档自动切片、向量化存储和精准语义匹配',
    icon: <BookOpen className="w-6 h-6" />,
    color: 'text-[#34c759]',
    bgColor: 'bg-[rgba(52,199,89,0.1)]',
    borderColor: 'border-[rgba(52,199,89,0.2)]',
    status: 'online',
    category: '检索能力',
    route: '/knowledge',
    features: ['语义检索', '文档切片', '向量化存储', '多格式支持', 'RAG增强'],
  },
  {
    id: 'web-search',
    name: '联网搜索引擎',
    description: '基于 MiniMax-M3 的实时联网搜索，自动判断何时需要联网，整合搜索结果为 AI 提供最新信息',
    icon: <Globe className="w-6 h-6" />,
    color: 'text-[#007aff]',
    bgColor: 'bg-[rgba(0,122,255,0.1)]',
    borderColor: 'border-[rgba(0,122,255,0.2)]',
    status: 'online',
    category: '检索能力',
    route: '/chat',
    features: ['实时搜索', '智能判断', '结果整合', '多源聚合', '时效性保障'],
  },
  {
    id: 'smart-quote',
    name: '智能报价引擎',
    description: '基于原料用量×采购最低价的自动成本计算引擎，十步法精确计算产品成本和建议报价',
    icon: <BarChart3 className="w-6 h-6" />,
    color: 'text-[#ff9500]',
    bgColor: 'bg-[rgba(255,149,0,0.1)]',
    borderColor: 'border-[rgba(255,149,0,0.2)]',
    status: 'online',
    category: '业务能力',
    route: '/supply-chain',
    features: ['十步成本法', '自动计算', '供应商对比', '利润分析', '批量报价'],
  },
];

// ===== 组件 =====
export default function AICenterPage() {
  const router = useRouter();
  const [activeTab, setActiveTab] = useState<'overview' | 'capabilities' | 'monitor'>('overview');
  const [trendData, setTrendData] = useState<CallTrend[]>([]);
  const [stats, setStats] = useState({
    totalCallsToday: 0,
    totalCallsAll: 0,
    avgSuccessRate: 0,
    avgLatency: 0,
    onlineCount: 0,
    totalCount: 0,
  });
  const [dashboardData, setDashboardData] = useState<any>(null);
  const [usageData, setUsageData] = useState<any>(null);
  const [loading, setLoading] = useState(true);

  // 前端能力ID → 后端调用日志 capability 标识的映射
  const CAP_ID_TO_LOG_KEY: Record<string, string> = {
    'smart-chat': 'smart-chat',
    'factory-chat': 'factory-chat',
    'web-search': 'web-search',
    'ai-recognize': 'ai-recognize',
    'knowledge-search': 'embedding',
    'smart-quote': 'quotation',
    'ai-image': 'ai-image',
  };

  // 根据能力ID获取真实统计数据（优先 ai_call_log 真实调用记录，兜底 dashboard 统计）
  const getCapStats = (capId: string) => {
    const logKey = CAP_ID_TO_LOG_KEY[capId];
    const row = (usageData?.todayUsage ?? []).find((r: any) => r.capability === logKey);
    if (row) {
      return { callsToday: Number(row.today ?? 0), callsTotal: Number(row.total ?? 0) };
    }
    if (usageData) {
      // 用量接口可用但该能力暂无调用记录 → 真实为 0
      return { callsToday: 0, callsTotal: 0 };
    }
    // 用量接口不可用，兜底 dashboard 统计
    const ai = dashboardData?.aiStats;
    if (!ai) return null;
    switch (capId) {
      case 'smart-chat':
        return { callsToday: ai.todayChatCalls ?? 0, callsTotal: ai.totalChatCalls ?? 0 };
      case 'knowledge-search':
        return { callsToday: 0, callsTotal: ai.knowledgeDocs ?? 0 };
      case 'ai-recognize':
        return { callsToday: 0, callsTotal: ai.embeddingCompleted ?? 0 };
      default:
        return null;
    }
  };

  // 模型用量数据（全部来自后端 ai_call_log 真实调用记录）
  const modelUsage: ModelUsage[] = usageData?.modelUsage ? usageData.modelUsage.map((m: any) => ({
    model: m.model,
    calls: Number(m.calls ?? 0),
    tokens: Number(m.tokens ?? 0),
    capability: m.capability,
    todayCalls: Number(m.today_calls ?? 0),
    avgLatency: Number(m.avg_latency ?? 0),
    successRate: Number(m.success_rate ?? 0),
  })) : [];

  useEffect(() => {
    const fetchData = async () => {
      setLoading(true);
      try {
        // 并行拉取：仪表盘统计 + AI 用量监控（真实调用记录）
        const [dashRes, usageRes] = await Promise.all([
          fetch('/api/dashboard/stats').catch(() => null),
          fetch('/api/ai-usage/overview').catch(() => null),
        ]);

        let payload: any = null;
        if (dashRes && dashRes.ok) {
          const data = await dashRes.json();
          payload = data?.data || data;
          setDashboardData(payload);
        }

        let usage: any = null;
        if (usageRes && usageRes.ok) {
          const u = await usageRes.json();
          usage = u?.data || null;
          setUsageData(usage);
        }

        const onlineCount = AI_CAPABILITIES.filter(c => c.status === 'online').length;

        // 核心指标全部用真实调用记录计算
        const todayUsage: any[] = usage?.todayUsage ?? [];
        const todayTotal = todayUsage.reduce((s: number, r: any) => s + Number(r.today ?? 0), 0);
        const allTotal = todayUsage.reduce((s: number, r: any) => s + Number(r.total ?? 0), 0);
        const health: any[] = usage?.health ?? [];
        const healthCalls = health.reduce((s: number, h: any) => s + Number(h.calls ?? 0), 0);
        const avgRate = health.length > 0
          ? health.reduce((s: number, h: any) => s + Number(h.success_rate ?? 0), 0) / health.length : 0;
        const avgLat = healthCalls > 0
          ? Math.round(health.reduce((s: number, h: any) => s + Number(h.avg_latency ?? 0) * Number(h.calls ?? 0), 0) / healthCalls) : 0;

        setStats({
          totalCallsToday: todayTotal,
          totalCallsAll: allTotal,
          avgSuccessRate: avgRate,
          avgLatency: avgLat,
          onlineCount,
          totalCount: AI_CAPABILITIES.length,
        });

        // 7日真实调用趋势
        if (usage?.trend) {
          setTrendData(usage.trend.map((t: any) => ({
            date: t.date,
            calls: Number(t.calls ?? 0),
            success: Number(t.success ?? 0),
            fail: Number(t.fail ?? 0),
          })));
        }
      } catch {
        // 后端不可用，保留默认值
        const onlineCount = AI_CAPABILITIES.filter(c => c.status === 'online').length;
        setStats(prev => ({ ...prev, onlineCount, totalCount: AI_CAPABILITIES.length }));
      } finally {
        setLoading(false);
      }
    };
    fetchData();
  }, []);

  const formatNumber = (n: number) => n >= 10000 ? `${(n / 10000).toFixed(1)}万` : n.toLocaleString();
  const formatLatency = (ms: number) => ms >= 1000 ? `${(ms / 1000).toFixed(1)}s` : `${ms}ms`;

  // ===== 渲染：概览 =====
  const renderOverview = () => (
    <div className="space-y-6">
      {/* 核心指标 */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        {[
          { label: '今日调用', value: formatNumber(stats.totalCallsToday), sub: `累计 ${formatNumber(stats.totalCallsAll)}`, icon: <Zap className="w-5 h-5" />, color: 'bg-[#007AFF]', glow: 'shadow-[0_2px_12px_rgba(0,0,0,0.04)]' },
          { label: '服务可用率', value: `${stats.avgSuccessRate.toFixed(1)}%`, sub: `${stats.onlineCount}/${stats.totalCount} 能力在线`, icon: <CheckCircle className="w-5 h-5" />, color: 'bg-[#34C759]', glow: 'shadow-[0_2px_12px_rgba(0,0,0,0.04)]' },
          { label: '平均响应', value: formatLatency(stats.avgLatency), sub: '近24小时真实均值', icon: <Clock className="w-5 h-5" />, color: 'bg-[#FF9500]', glow: 'shadow-[0_2px_12px_rgba(0,0,0,0.04)]' },
          { label: 'AI 能力数', value: stats.totalCount.toString(), sub: '持续扩展中', icon: <Sparkles className="w-5 h-5" />, color: 'bg-[#007AFF]', glow: 'shadow-[0_2px_12px_rgba(0,0,0,0.04)]' },
        ].map((card, i) => (
          <div key={i} className={`bg-white rounded-xl border border-[rgba(229,229,234,0.5)] p-5 hover:shadow-lg ${card.glow} transition-all duration-300 group`}>
            <div className="flex items-center justify-between mb-3">
              <span className="text-sm text-[#8e8e93]">{card.label}</span>
              <div className={`w-9 h-9 rounded-lg ${card.color} flex items-center justify-center text-white shadow-sm`}>
                {card.icon}
              </div>
            </div>
            <div className="text-2xl font-bold text-[#1c1c1e] font-mono">{card.value}</div>
            <div className="text-xs text-[#8e8e93] mt-1">{card.sub}</div>
          </div>
        ))}
      </div>

      {/* 调用趋势图 */}
      <div className="bg-white rounded-xl border border-[rgba(229,229,234,0.5)] p-5">
        <div className="flex items-center justify-between mb-5">
          <h3 className="text-base font-semibold text-[#1c1c1e] flex items-center gap-2">
            <Activity className="w-5 h-5 text-[#007aff]" />
            近7天调用趋势
          </h3>
          <div className="flex items-center gap-4 text-xs">
            <span className="flex items-center gap-1.5"><span className="w-2.5 h-2.5 rounded-full bg-[#007aff]" />成功</span>
            <span className="flex items-center gap-1.5"><span className="w-2.5 h-2.5 rounded-full bg-[rgba(255,59,48,0.6)]" />失败</span>
          </div>
        </div>
        {trendData.length === 0 ? (
          <div className="h-52 flex items-center justify-center text-xs text-[#8e8e93]">
            暂无调用记录 —— 产生 AI 调用后将自动展示近7天趋势
          </div>
        ) : (
        <div className="h-52 flex items-end gap-1.5">
          {trendData.map((d, i) => {
            const maxCalls = Math.max(...trendData.map(t => t.calls), 1);
            const successH = (d.success / maxCalls) * 100;
            const failH = (d.fail / maxCalls) * 100;
            return (
              <div key={i} className="flex-1 flex flex-col items-center gap-0.5 group/bar relative">
                <div className="absolute -top-8 left-1/2 -translate-x-1/2 px-2 py-1 rounded-md bg-[rgba(118,118,128,0.12)] text-[10px] text-[#3a3a3c] opacity-0 group-hover/bar:opacity-100 transition-opacity whitespace-nowrap z-10">
                  {d.success} 成功 / {d.fail} 失败
                </div>
                <div className="w-full flex flex-col gap-px" style={{ height: '180px' }}>
                  <div
                    className="w-full bg-[#007AFF] rounded-t-sm transition-all duration-300 group-hover/bar:from-[#007aff] group-hover/bar:to-[#007aff]"
                    style={{ height: `${successH}%`, marginTop: 'auto' }}
                  />
                  {d.fail > 0 && (
                    <div
                      className="w-full bg-[rgba(255,59,48,0.4)] rounded-b-sm"
                      style={{ height: `${Math.max(failH, 2)}%` }}
                    />
                  )}
                </div>
                <span className="text-[10px] text-[#8e8e93] mt-1.5">{d.date}</span>
              </div>
            );
          })}
        </div>
        )}
      </div>

      {/* 快捷入口：能力分类 */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        {[
          { title: '对话能力', count: AI_CAPABILITIES.filter(c => c.category === '对话能力').length, icon: <MessageSquare className="w-5 h-5" />, color: 'text-[#007aff]', bg: 'bg-[rgba(0,122,255,0.1)]', border: 'border-[rgba(0,122,255,0.2)]', desc: '智能对话与问答' },
          { title: '生成能力', count: AI_CAPABILITIES.filter(c => c.category === '生成能力').length, icon: <Wand2 className="w-5 h-5" />, color: 'text-[#007aff]', bg: 'bg-[rgba(0,122,255,0.1)]', border: 'border-[rgba(0,122,255,0.2)]', desc: '内容创作与生成' },
          { title: '检索能力', count: AI_CAPABILITIES.filter(c => c.category === '检索能力').length, icon: <Search className="w-5 h-5" />, color: 'text-[#34c759]', bg: 'bg-[rgba(52,199,89,0.1)]', border: 'border-[rgba(52,199,89,0.2)]', desc: '知识检索与搜索' },
          { title: '业务能力', count: AI_CAPABILITIES.filter(c => c.category === '业务能力').length, icon: <BarChart3 className="w-5 h-5" />, color: 'text-[#ff9500]', bg: 'bg-[rgba(255,149,0,0.1)]', border: 'border-[rgba(255,149,0,0.2)]', desc: '行业专用能力' },
        ].map((cat, i) => (
          <button key={i} onClick={() => setActiveTab('capabilities')}
            className={`${cat.bg} border ${cat.border} rounded-xl p-4 text-left hover:shadow-lg transition-all duration-300 group`}>
            <div className={`${cat.color} mb-3`}>{cat.icon}</div>
            <h4 className="text-sm font-semibold text-[#1c1c1e] mb-1">{cat.title}</h4>
            <p className="text-xs text-[#8e8e93] mb-2">{cat.desc}</p>
            <div className="flex items-center justify-between">
              <span className="text-xs text-[#8e8e93]">{cat.count} 项能力</span>
              <ChevronRight className="w-4 h-4 text-[#8e8e93] group-hover:text-[#3a3a3c] transition-colors" />
            </div>
          </button>
        ))}
      </div>

      {/* 模型用量排行（真实调用记录） */}
      <div className="bg-white rounded-xl border border-[rgba(229,229,234,0.5)] p-5">
        <h3 className="text-base font-semibold text-[#1c1c1e] flex items-center gap-2 mb-4">
          <Database className="w-5 h-5 text-[#007aff]" />
          模型调用排行
        </h3>
        {modelUsage.length === 0 ? (
          <div className="py-8 text-center text-xs text-[#8e8e93]">
            暂无调用记录 —— 数据来自系统真实调用日志，产生 AI 调用后将自动展示
          </div>
        ) : (
          <div className="space-y-3">
            {modelUsage.map((m, i) => {
              const maxCalls = Math.max(...modelUsage.map(x => x.calls), 1);
              const pct = (m.calls / maxCalls) * 100;
              return (
                <div key={i} className="group">
                  <div className="flex items-center justify-between mb-1.5">
                    <div className="flex items-center gap-2">
                      <span className="text-xs font-mono text-[#8e8e93] w-5">{i + 1}</span>
                      <span className="text-sm text-[#1c1c1e] font-medium">{m.model}</span>
                      <span className="text-[10px] text-[#8e8e93]">{capName(m.capability ?? '')}</span>
                    </div>
                    <div className="flex items-center gap-4 text-xs">
                      <span className="text-[#8e8e93]">{formatNumber(m.calls)} 次调用</span>
                      <span className="text-[#8e8e93]">{formatLatency(m.avgLatency ?? 0)}</span>
                      <span className={`font-medium ${(m.successRate ?? 0) >= 95 ? 'text-[#34c759]' : (m.successRate ?? 0) >= 80 ? 'text-[#ff9500]' : 'text-[#ff3b30]'}`}>
                        {(m.successRate ?? 0).toFixed(1)}%
                      </span>
                    </div>
                  </div>
                  <div className="h-2 bg-[rgba(118,118,128,0.12)] rounded-full overflow-hidden">
                    <div
                      className="h-full rounded-full bg-[#007AFF] transition-all duration-500"
                      style={{ width: `${pct}%` }}
                    />
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );

  // ===== 渲染：能力卡片 =====
  const renderCapabilities = () => {
    const categories = [...new Set(AI_CAPABILITIES.map(c => c.category))];
    return (
      <div className="space-y-8">
        {categories.map(cat => {
          const items = AI_CAPABILITIES.filter(c => c.category === cat);
          return (
            <div key={cat}>
              <h3 className="text-sm font-semibold text-[#3a3a3c] mb-4 flex items-center gap-2">
                <Layers className="w-4 h-4 text-[#007aff]" />
                {cat}
                <span className="text-xs text-[#8e8e93] font-normal ml-1">{items.length} 项能力</span>
              </h3>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {items.map(cap => (
                  <div
                    key={cap.id}
                    className={`bg-white rounded-xl border ${cap.borderColor} p-5 hover:shadow-lg hover:shadow-[0_2px_12px_rgba(0,0,0,0.04)] transition-all duration-300 group cursor-pointer`}
                    onClick={() => router.push(cap.route)}
                  >
                    {/* 头部 */}
                    <div className="flex items-start justify-between mb-3">
                      <div className="flex items-center gap-3">
                        <div className={`w-11 h-11 rounded-xl ${cap.bgColor} ${cap.color} flex items-center justify-center`}>
                          {cap.icon}
                        </div>
                        <div>
                          <h4 className="text-sm font-semibold text-[#1c1c1e] group-hover:text-[#1C1C1E] transition-colors">{cap.name}</h4>
                          <div className="flex items-center gap-2 mt-0.5">
                            <span className={`inline-flex items-center gap-1 px-1.5 py-0.5 rounded-full text-[10px] font-medium ${
                              cap.status === 'online' ? 'bg-[rgba(52,199,89,0.15)] text-[#34c759]' :
                              cap.status === 'beta' ? 'bg-[rgba(255,149,0,0.15)] text-[#ff9500]' :
                              'bg-[rgba(255,59,48,0.15)] text-[#ff3b30]'
                            }`}>
                              <span className={`w-1.5 h-1.5 rounded-full ${
                                cap.status === 'online' ? 'bg-[#34c759]' :
                                cap.status === 'beta' ? 'bg-[#ff9500]' :
                                'bg-[#ff3b30]'
                              } ${cap.status === 'online' ? 'animate-pulse' : ''}`} />
                              {cap.status === 'online' ? '在线' : cap.status === 'beta' ? '测试中' : '离线'}
                            </span>
                          </div>
                        </div>
                      </div>
                      <ArrowRight className="w-4 h-4 text-[#8e8e93] group-hover:text-[#3a3a3c] transition-colors mt-1" />
                    </div>

                    {/* 描述 */}
                    <p className="text-xs text-[#8e8e93] leading-relaxed mb-3">{cap.description}</p>

                    {/* 能力标签 */}
                    <div className="flex flex-wrap gap-1.5 mb-4">
                      {cap.features.map(f => (
                        <span key={f} className="px-2 py-0.5 rounded-md text-[10px] font-medium bg-[rgba(118,118,128,0.12)] text-[#3a3a3c] border border-[rgba(229,229,234,0.3)]">
                          {f}
                        </span>
                      ))}
                    </div>

                    {/* 指标 */}
                    <div className="grid grid-cols-3 gap-3 pt-3 border-t border-[rgba(229,229,234,0.3)]">
                      <div className="text-center">
                        <div className="text-xs text-[#8e8e93] mb-0.5">今日调用</div>
                        <div className="text-sm font-bold text-[#1c1c1e] font-mono">
                          {getCapStats(cap.id)?.callsToday ?? '--'}
                        </div>
                      </div>
                      <div className="text-center">
                        <div className="text-xs text-[#8e8e93] mb-0.5">累计调用</div>
                        <div className="text-sm font-bold text-[#1c1c1e] font-mono">
                          {getCapStats(cap.id)?.callsTotal ?? '--'}
                        </div>
                      </div>
                      <div className="text-center">
                        <div className="text-xs text-[#8e8e93] mb-0.5">状态</div>
                        <div className={`text-sm font-bold font-mono ${cap.status === 'online' ? 'text-[#34c759]' : cap.status === 'beta' ? 'text-[#ff9500]' : 'text-[#ff3b30]'}`}>
                          {cap.status === 'online' ? '正常' : cap.status === 'beta' ? '测试' : '离线'}
                        </div>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          );
        })}
      </div>
    );
  };

  // ===== 渲染：用量监控 =====
  const renderMonitor = () => {
    const health: any[] = usageData?.health ?? [];
    const recent: any[] = usageData?.recent ?? [];
    const todayUsage: any[] = usageData?.todayUsage ?? [];
    const rateLimits: any[] = usageData?.rateLimits ?? [];
    const allNormal = health.length > 0 && health.every((h: any) => h.level === 'normal');
    const hasError = health.some((h: any) => h.level === 'error');
    const todayTotal = todayUsage.reduce((s: number, r: any) => s + Number(r.today ?? 0), 0);
    const emptyHint = (
      <div className="py-8 text-center text-xs text-[#8e8e93]">
        暂无调用记录 —— 数据来自系统真实调用日志，产生 AI 调用后将自动展示
      </div>
    );
    return (
    <div className="space-y-6">
      {/* 实时状态（近24h真实成功率/平均延迟，按实际调用的模型分组） */}
      <div className="bg-white rounded-xl border border-[rgba(229,229,234,0.5)] p-5">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-base font-semibold text-[#1c1c1e] flex items-center gap-2">
            <Server className="w-5 h-5 text-[#34c759]" />
            服务健康状态
            <span className="text-[10px] font-normal text-[#8e8e93]">近24小时真实调用</span>
          </h3>
          {health.length > 0 && (
            <span className={`flex items-center gap-1.5 text-xs ${hasError ? 'text-[#ff3b30]' : allNormal ? 'text-[#34c759]' : 'text-[#ff9500]'}`}>
              <span className={`w-2 h-2 rounded-full ${hasError ? 'bg-[#ff3b30]' : allNormal ? 'bg-[#34c759]' : 'bg-[#ff9500]'} animate-pulse`} />
              {hasError ? '存在异常' : allNormal ? '全部正常' : '部分降级'}
            </span>
          )}
        </div>
        {health.length === 0 ? emptyHint : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-3">
            {health.map((s: any, i: number) => {
              const rate = Number(s.success_rate ?? 0);
              const level = s.level ?? 'normal';
              return (
                <div key={i} className="bg-[rgba(242,242,247,0.5)] rounded-lg border border-[rgba(229,229,234,0.3)] p-3 flex items-center gap-3">
                  <div className={`w-2.5 h-2.5 rounded-full ${level === 'normal' ? 'bg-[#34c759] animate-pulse' : level === 'warning' ? 'bg-[#ff9500]' : 'bg-[#ff3b30]'}`} />
                  <div className="flex-1 min-w-0">
                    <div className="text-xs font-medium text-[#1c1c1e] truncate">{s.model}</div>
                    <div className="text-[10px] text-[#8e8e93]">
                      {formatLatency(Number(s.avg_latency ?? 0))} · 成功率 {rate}% · {formatNumber(Number(s.calls ?? 0))}次
                    </div>
                  </div>
                  {level === 'normal'
                    ? <CheckCircle className="w-4 h-4 text-[rgba(52,199,89,0.5)]" />
                    : level === 'warning'
                      ? <AlertTriangle className="w-4 h-4 text-[rgba(255,149,0,0.6)]" />
                      : <XCircle className="w-4 h-4 text-[rgba(255,59,48,0.6)]" />}
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* 模型用量明细（真实分组统计） */}
      <div className="bg-white rounded-xl border border-[rgba(229,229,234,0.5)] p-5">
        <h3 className="text-base font-semibold text-[#1c1c1e] flex items-center gap-2 mb-4">
          <PieChart className="w-5 h-5 text-[#007aff]" />
          模型用量明细
        </h3>
        {modelUsage.length === 0 ? emptyHint : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-[rgba(229,229,234,0.5)]">
                  <th className="text-left py-3 px-3 text-[#8e8e93] font-medium">模型</th>
                  <th className="text-left py-3 px-3 text-[#8e8e93] font-medium">能力</th>
                  <th className="text-right py-3 px-3 text-[#8e8e93] font-medium">调用次数</th>
                  <th className="text-right py-3 px-3 text-[#8e8e93] font-medium">今日</th>
                  <th className="text-right py-3 px-3 text-[#8e8e93] font-medium">平均延迟</th>
                  <th className="text-right py-3 px-3 text-[#8e8e93] font-medium">成功率</th>
                  <th className="text-right py-3 px-3 text-[#8e8e93] font-medium">占比</th>
                </tr>
              </thead>
              <tbody>
                {modelUsage.map((m, i) => {
                  const totalCalls = modelUsage.reduce((s, x) => s + x.calls, 0);
                  const pct = totalCalls > 0 ? ((m.calls / totalCalls) * 100).toFixed(1) : '0.0';
                  return (
                    <tr key={i} className="border-b border-[rgba(229,229,234,0.3)] hover:bg-[rgba(0,0,0,0.01)] transition-colors">
                      <td className="py-3 px-3 text-[#1c1c1e] font-medium">{m.model}</td>
                      <td className="py-3 px-3 text-[#8e8e93] text-xs">{capName(m.capability ?? '')}</td>
                      <td className="py-3 px-3 text-right font-mono text-[#3a3a3c]">{formatNumber(m.calls)}</td>
                      <td className="py-3 px-3 text-right font-mono text-[#8e8e93]">{formatNumber(m.todayCalls ?? 0)}</td>
                      <td className="py-3 px-3 text-right font-mono text-[#8e8e93]">{formatLatency(m.avgLatency ?? 0)}</td>
                      <td className={`py-3 px-3 text-right font-mono ${(m.successRate ?? 0) >= 95 ? 'text-[#34c759]' : (m.successRate ?? 0) >= 80 ? 'text-[#ff9500]' : 'text-[#ff3b30]'}`}>
                        {(m.successRate ?? 0).toFixed(1)}%
                      </td>
                      <td className="py-3 px-3 text-right">
                        <div className="flex items-center justify-end gap-2">
                          <div className="w-16 h-1.5 bg-[rgba(118,118,128,0.12)] rounded-full overflow-hidden">
                            <div className="h-full rounded-full bg-[#007aff]" style={{ width: `${pct}%` }} />
                          </div>
                          <span className="text-xs text-[#8e8e93] w-10 text-right">{pct}%</span>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* 最近调用日志（真实记录） */}
      <div className="bg-white rounded-xl border border-[rgba(229,229,234,0.5)] p-5">
        <h3 className="text-base font-semibold text-[#1c1c1e] flex items-center gap-2 mb-4">
          <Clock className="w-5 h-5 text-[#007aff]" />
          最近调用记录
        </h3>
        {recent.length === 0 ? emptyHint : (
          <div className="space-y-2">
            {recent.map((log: any, i: number) => (
              <div key={i} className="flex items-center gap-3 py-2 px-3 rounded-lg hover:bg-[rgba(0,0,0,0.01)] transition-colors text-xs">
                <span className="text-[#8e8e93] font-mono w-16 shrink-0">{log.time}</span>
                <span className="text-[#1c1c1e] w-32 truncate">{capName(log.capability ?? '')}</span>
                <span className="text-[#8e8e93] w-32 truncate">{log.model}</span>
                <span className={`shrink-0 ${log.status === 'success' ? 'text-[#34c759]' : 'text-[#ff3b30]'}`}>
                  {log.status === 'success' ? <CheckCircle className="w-3.5 h-3.5" /> : <XCircle className="w-3.5 h-3.5" />}
                </span>
                <span className="text-[#8e8e93] font-mono w-14 text-right">{formatLatency(Number(log.latency_ms ?? 0))}</span>
                <span className="text-[#8e8e93] font-mono w-16 text-right">{Number(log.tokens ?? 0) > 0 ? `${log.tokens} tok` : '-'}</span>
                <span className="text-[#8e8e93] flex-1 truncate text-right">{log.detail ?? ''}</span>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* 今日用量与系统限流（全部真实） */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div className="bg-white rounded-xl border border-[rgba(229,229,234,0.5)] p-5">
          <h3 className="text-sm font-semibold text-[#1c1c1e] flex items-center gap-2 mb-3">
            <Shield className="w-4 h-4 text-[#007aff]" />
            今日用量
            <span className="text-[10px] font-normal text-[#8e8e93]">今日 {formatNumber(todayTotal)} 次</span>
          </h3>
          {todayUsage.length === 0 ? emptyHint : (
            <div className="space-y-3">
              {todayUsage.map((q: any, i: number) => {
                const maxToday = Math.max(...todayUsage.map((x: any) => Number(x.today ?? 0)), 1);
                return (
                  <div key={i}>
                    <div className="flex items-center justify-between text-xs mb-1">
                      <span className="text-[#3a3a3c]">{capName(q.capability ?? '')}</span>
                      <span className="text-[#8e8e93]">今日 {formatNumber(Number(q.today ?? 0))} · 累计 {formatNumber(Number(q.total ?? 0))}</span>
                    </div>
                    <div className="h-1.5 bg-[rgba(118,118,128,0.12)] rounded-full overflow-hidden">
                      <div
                        className="h-full rounded-full bg-[#007aff] transition-all duration-500"
                        style={{ width: `${(Number(q.today ?? 0) / maxToday) * 100}%` }}
                      />
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
        <div className="bg-white rounded-xl border border-[rgba(229,229,234,0.5)] p-5">
          <h3 className="text-sm font-semibold text-[#1c1c1e] flex items-center gap-2 mb-3">
            <AlertTriangle className="w-4 h-4 text-[#ff9500]" />
            速率限制
            <span className="text-[10px] font-normal text-[#8e8e93]">系统真实限流配置</span>
          </h3>
          <div className="space-y-2.5">
            {rateLimits.map((r: any, i: number) => (
              <div key={i} className="flex items-center justify-between text-xs py-1.5 px-2 rounded-lg bg-[rgba(242,242,247,0.3)]">
                <span className="text-[#3a3a3c]">{r.name}</span>
                <div className="flex items-center gap-3">
                  <span className="text-[#8e8e93]">当前窗口 {r.current ?? 0} 次</span>
                  {Number(r.rejected ?? 0) > 0 && <span className="text-[#ff3b30]">拒绝 {r.rejected} 次</span>}
                  <span className="text-[#8e8e93]">/ {r.limit}</span>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
    );
  };

  return (
    <div className="min-h-screen bg-[#F2F2F7]">
      {/* 顶部导航 */}
      <header className="bg-white/80 backdrop-blur-xl border-b border-[rgba(229,229,234,0.5)] sticky top-0 z-50">
        <div className="max-w-[1400px] mx-auto px-5 h-14 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <button onClick={() => router.push('/')} className="flex items-center gap-1 text-sm text-[#8e8e93] hover:text-[#1c1c1e] transition-colors shrink-0">
              <ChevronRight className="w-4 h-4 rotate-180" />
              <span>返回</span>
            </button>
            <span className="text-[#8e8e93]">|</span>
            <div className="w-8 h-8 rounded-lg bg-[#007AFF] flex items-center justify-center text-white shadow-sm">
              <Sparkles className="w-4 h-4" />
            </div>
            <div>
              <h1 className="text-[15px] font-bold text-[#1c1c1e] leading-tight">AI 能力中心</h1>
              <p className="text-[11px] text-[#8e8e93]">统一管理 · 用量监控 · 能力编排</p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <span className="flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-[rgba(52,199,89,0.1)] text-[#34c759] text-xs border border-[rgba(52,199,89,0.2)]">
              <span className="w-1.5 h-1.5 rounded-full bg-[#34c759] animate-pulse" />
              {stats.onlineCount}/{stats.totalCount} 在线
            </span>
          </div>
        </div>
      </header>

      <div className="max-w-[1400px] mx-auto px-5 py-5">
        {/* Tab 导航 */}
        <div className="flex gap-1 mb-6 bg-white rounded-xl p-1 border border-[rgba(229,229,234,0.5)] w-fit">
          {[
            { key: 'overview' as const, label: '能力概览', icon: <BarChart3 className="w-4 h-4" /> },
            { key: 'capabilities' as const, label: '全部能力', icon: <Sparkles className="w-4 h-4" /> },
            { key: 'monitor' as const, label: '用量监控', icon: <Activity className="w-4 h-4" /> },
          ].map(tab => (
            <button key={tab.key} onClick={() => setActiveTab(tab.key)}
              className={`flex items-center gap-1.5 px-4 py-2 rounded-lg text-sm font-medium transition-all ${
                activeTab === tab.key
                  ? 'bg-[#007AFF] text-white shadow-sm'
                  : 'text-[#8e8e93] hover:bg-[rgba(118,118,128,0.12)] hover:text-[#1c1c1e]'
              }`}>
              {tab.icon}{tab.label}
            </button>
          ))}
        </div>

        {/* 内容区 */}
        {activeTab === 'overview' && renderOverview()}
        {activeTab === 'capabilities' && renderCapabilities()}
        {activeTab === 'monitor' && renderMonitor()}
      </div>
    </div>
  );
}
