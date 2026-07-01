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
  callsToday: number;
  callsTotal: number;
  avgLatency: number;
  successRate: number;
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
  cost: number;
}

// ===== AI 能力数据 =====
const AI_CAPABILITIES: AICapability[] = [
  {
    id: 'smart-chat',
    name: 'AI 智能对话',
    description: '基于 DeepSeek V4 Pro 的多模态智能对话，支持思考模式、联网搜索、知识库检索和记忆库上下文',
    icon: <MessageSquare className="w-6 h-6" />,
    color: 'text-blue-400',
    bgColor: 'bg-blue-500/10',
    borderColor: 'border-blue-500/20',
    status: 'online',
    category: '对话能力',
    callsToday: 128,
    callsTotal: 3847,
    avgLatency: 2300,
    successRate: 98.5,
    route: '/chat',
    features: ['DeepSeek V4 Pro', '思考模式', '联网搜索', '知识库检索', '记忆库上下文', '多轮对话'],
  },
  {
    id: 'ai-image',
    name: 'AI 智能生图',
    description: '支持文字生图和图生图，多种模型可选，高分辨率输出，支持批量生成和风格控制',
    icon: <Image className="w-6 h-6" />,
    color: 'text-purple-400',
    bgColor: 'bg-purple-500/10',
    borderColor: 'border-purple-500/20',
    status: 'online',
    category: '生成能力',
    callsToday: 56,
    callsTotal: 1823,
    avgLatency: 8500,
    successRate: 96.2,
    route: '/ai-image',
    features: ['文生图', '图生图', '多模型选择', '高分辨率', '批量生成', '风格控制'],
  },
  {
    id: 'ai-recognize',
    name: 'AI 智能识别',
    description: '基于豆包 Vision 模型的图片内容识别，自动分类、标签提取、场景理解',
    icon: <Eye className="w-6 h-6" />,
    color: 'text-cyan-400',
    bgColor: 'bg-cyan-500/10',
    borderColor: 'border-cyan-500/20',
    status: 'online',
    category: '识别能力',
    callsToday: 89,
    callsTotal: 4521,
    avgLatency: 1800,
    successRate: 97.8,
    route: '/',
    features: ['图片分类', '标签提取', '场景理解', '自动归类', '批量识别'],
  },
  {
    id: 'factory-chat',
    name: '工厂供应链助手',
    description: '专注供应链与工厂业务的 AI 助手，支持智能报价、成本计算、供应商对比分析',
    icon: <Cpu className="w-6 h-6" />,
    color: 'text-orange-400',
    bgColor: 'bg-orange-500/10',
    borderColor: 'border-orange-500/20',
    status: 'online',
    category: '对话能力',
    callsToday: 34,
    callsTotal: 956,
    avgLatency: 2600,
    successRate: 97.1,
    route: '/supply-chain',
    features: ['成本计算', '智能报价', '供应商分析', '联网搜索', '十步成本法'],
  },
  {
    id: 'knowledge-search',
    name: '知识库语义检索',
    description: '基于向量嵌入的语义搜索引擎，支持文档自动切片、向量化存储和精准语义匹配',
    icon: <BookOpen className="w-6 h-6" />,
    color: 'text-emerald-400',
    bgColor: 'bg-emerald-500/10',
    borderColor: 'border-emerald-500/20',
    status: 'online',
    category: '检索能力',
    callsToday: 67,
    callsTotal: 2341,
    avgLatency: 450,
    successRate: 99.2,
    route: '/knowledge',
    features: ['语义检索', '文档切片', '向量化存储', '多格式支持', 'RAG增强'],
  },
  {
    id: 'memory-rag',
    name: '记忆库 RAG',
    description: 'AI 对话上下文记忆与检索增强生成，支持知识域管理、卡片式知识存储和对话历史',
    icon: <Brain className="w-6 h-6" />,
    color: 'text-violet-400',
    bgColor: 'bg-violet-500/10',
    borderColor: 'border-violet-500/20',
    status: 'online',
    category: '检索能力',
    callsToday: 45,
    callsTotal: 1567,
    avgLatency: 380,
    successRate: 99.5,
    route: '/memory',
    features: ['知识域管理', '知识卡片', '文档上传', '语义搜索', 'RAG对话'],
  },
  {
    id: 'web-search',
    name: '联网搜索引擎',
    description: '实时互联网搜索能力，自动判断何时需要联网，整合搜索结果为 AI 提供最新信息',
    icon: <Globe className="w-6 h-6" />,
    color: 'text-sky-400',
    bgColor: 'bg-sky-500/10',
    borderColor: 'border-sky-500/20',
    status: 'online',
    category: '检索能力',
    callsToday: 92,
    callsTotal: 3256,
    avgLatency: 2200,
    successRate: 94.3,
    route: '/chat',
    features: ['实时搜索', '智能判断', '结果整合', '多源聚合', '时效性保障'],
  },
  {
    id: 'smart-quote',
    name: '智能报价引擎',
    description: '基于原料用量×采购最低价的自动成本计算引擎，十步法精确计算产品成本和建议报价',
    icon: <BarChart3 className="w-6 h-6" />,
    color: 'text-amber-400',
    bgColor: 'bg-amber-500/10',
    borderColor: 'border-amber-500/20',
    status: 'online',
    category: '业务能力',
    callsToday: 23,
    callsTotal: 678,
    avgLatency: 800,
    successRate: 99.1,
    route: '/supply-chain',
    features: ['十步成本法', '自动计算', '供应商对比', '利润分析', '批量报价'],
  },
];

// ===== 模拟趋势数据 =====
const generateTrendData = (): CallTrend[] => {
  const data: CallTrend[] = [];
  const now = new Date();
  for (let i = 13; i >= 0; i--) {
    const d = new Date(now);
    d.setDate(d.getDate() - i);
    const total = Math.floor(200 + Math.random() * 300);
    const success = Math.floor(total * (0.94 + Math.random() * 0.06));
    data.push({
      date: `${d.getMonth() + 1}/${d.getDate()}`,
      calls: total,
      success,
      fail: total - success,
    });
  }
  return data;
};

const MODEL_USAGE: ModelUsage[] = [
  { model: 'DeepSeek V4 Pro', calls: 2847, tokens: 4823000, cost: 38.56 },
  { model: '豆包 Vision', calls: 1521, tokens: 892000, cost: 12.34 },
  { model: 'MiniMax Embedding', calls: 4521, tokens: 2341000, cost: 5.67 },
  { model: 'nano-banana', calls: 823, tokens: 0, cost: 16.42 },
  { model: 'gpt-image-2', calls: 412, tokens: 0, cost: 24.60 },
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

  useEffect(() => {
    const trend = generateTrendData();
    setTrendData(trend);

    const totalToday = AI_CAPABILITIES.reduce((s, c) => s + c.callsToday, 0);
    const totalAll = AI_CAPABILITIES.reduce((s, c) => s + c.callsTotal, 0);
    const avgRate = AI_CAPABILITIES.reduce((s, c) => s + c.successRate, 0) / AI_CAPABILITIES.length;
    const avgLat = AI_CAPABILITIES.reduce((s, c) => s + c.avgLatency, 0) / AI_CAPABILITIES.length;
    const onlineCount = AI_CAPABILITIES.filter(c => c.status === 'online').length;

    setStats({
      totalCallsToday: totalToday,
      totalCallsAll: totalAll,
      avgSuccessRate: avgRate,
      avgLatency: Math.round(avgLat),
      onlineCount,
      totalCount: AI_CAPABILITIES.length,
    });
  }, []);

  const formatNumber = (n: number) => n >= 10000 ? `${(n / 10000).toFixed(1)}万` : n.toLocaleString();
  const formatLatency = (ms: number) => ms >= 1000 ? `${(ms / 1000).toFixed(1)}s` : `${ms}ms`;

  // ===== 渲染：概览 =====
  const renderOverview = () => (
    <div className="space-y-6">
      {/* 核心指标 */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        {[
          { label: '今日调用', value: formatNumber(stats.totalCallsToday), sub: `累计 ${formatNumber(stats.totalCallsAll)}`, icon: <Zap className="w-5 h-5" />, color: 'from-blue-500 to-cyan-500', glow: 'shadow-blue-500/20' },
          { label: '服务可用率', value: `${stats.avgSuccessRate.toFixed(1)}%`, sub: `${stats.onlineCount}/${stats.totalCount} 能力在线`, icon: <CheckCircle className="w-5 h-5" />, color: 'from-green-500 to-emerald-500', glow: 'shadow-green-500/20' },
          { label: '平均响应', value: formatLatency(stats.avgLatency), sub: 'P99 < 5s', icon: <Clock className="w-5 h-5" />, color: 'from-amber-500 to-orange-500', glow: 'shadow-amber-500/20' },
          { label: 'AI 能力数', value: stats.totalCount.toString(), sub: '持续扩展中', icon: <Sparkles className="w-5 h-5" />, color: 'from-purple-500 to-violet-500', glow: 'shadow-purple-500/20' },
        ].map((card, i) => (
          <div key={i} className={`bg-slate-800/50 rounded-xl border border-slate-700/50 p-5 hover:shadow-lg ${card.glow} transition-all duration-300 group`}>
            <div className="flex items-center justify-between mb-3">
              <span className="text-sm text-slate-400">{card.label}</span>
              <div className={`w-9 h-9 rounded-lg bg-gradient-to-r ${card.color} flex items-center justify-center text-white shadow-sm`}>
                {card.icon}
              </div>
            </div>
            <div className="text-2xl font-bold text-slate-100 font-mono">{card.value}</div>
            <div className="text-xs text-slate-500 mt-1">{card.sub}</div>
          </div>
        ))}
      </div>

      {/* 调用趋势图 */}
      <div className="bg-slate-800/50 rounded-xl border border-slate-700/50 p-5">
        <div className="flex items-center justify-between mb-5">
          <h3 className="text-base font-semibold text-slate-100 flex items-center gap-2">
            <Activity className="w-5 h-5 text-blue-400" />
            近14天调用趋势
          </h3>
          <div className="flex items-center gap-4 text-xs">
            <span className="flex items-center gap-1.5"><span className="w-2.5 h-2.5 rounded-full bg-blue-500" />成功</span>
            <span className="flex items-center gap-1.5"><span className="w-2.5 h-2.5 rounded-full bg-red-500/60" />失败</span>
          </div>
        </div>
        <div className="h-52 flex items-end gap-1.5">
          {trendData.map((d, i) => {
            const maxCalls = Math.max(...trendData.map(t => t.calls));
            const successH = (d.success / maxCalls) * 100;
            const failH = (d.fail / maxCalls) * 100;
            return (
              <div key={i} className="flex-1 flex flex-col items-center gap-0.5 group/bar relative">
                <div className="absolute -top-8 left-1/2 -translate-x-1/2 px-2 py-1 rounded-md bg-slate-700 text-[10px] text-slate-300 opacity-0 group-hover/bar:opacity-100 transition-opacity whitespace-nowrap z-10">
                  {d.success} 成功 / {d.fail} 失败
                </div>
                <div className="w-full flex flex-col gap-px" style={{ height: '180px' }}>
                  <div
                    className="w-full bg-gradient-to-t from-blue-600 to-blue-400 rounded-t-sm transition-all duration-300 group-hover/bar:from-blue-500 group-hover/bar:to-blue-300"
                    style={{ height: `${successH}%`, marginTop: 'auto' }}
                  />
                  {d.fail > 0 && (
                    <div
                      className="w-full bg-red-500/40 rounded-b-sm"
                      style={{ height: `${Math.max(failH, 2)}%` }}
                    />
                  )}
                </div>
                <span className="text-[10px] text-slate-500 mt-1.5">{d.date}</span>
              </div>
            );
          })}
        </div>
      </div>

      {/* 快捷入口：能力分类 */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        {[
          { title: '对话能力', count: AI_CAPABILITIES.filter(c => c.category === '对话能力').length, icon: <MessageSquare className="w-5 h-5" />, color: 'text-blue-400', bg: 'bg-blue-500/10', border: 'border-blue-500/20', desc: '智能对话与问答' },
          { title: '生成能力', count: AI_CAPABILITIES.filter(c => c.category === '生成能力').length, icon: <Wand2 className="w-5 h-5" />, color: 'text-purple-400', bg: 'bg-purple-500/10', border: 'border-purple-500/20', desc: '内容创作与生成' },
          { title: '检索能力', count: AI_CAPABILITIES.filter(c => c.category === '检索能力').length, icon: <Search className="w-5 h-5" />, color: 'text-emerald-400', bg: 'bg-emerald-500/10', border: 'border-emerald-500/20', desc: '知识检索与搜索' },
          { title: '业务能力', count: AI_CAPABILITIES.filter(c => c.category === '业务能力').length, icon: <BarChart3 className="w-5 h-5" />, color: 'text-amber-400', bg: 'bg-amber-500/10', border: 'border-amber-500/20', desc: '行业专用能力' },
        ].map((cat, i) => (
          <button key={i} onClick={() => setActiveTab('capabilities')}
            className={`${cat.bg} border ${cat.border} rounded-xl p-4 text-left hover:shadow-lg transition-all duration-300 group`}>
            <div className={`${cat.color} mb-3`}>{cat.icon}</div>
            <h4 className="text-sm font-semibold text-slate-200 mb-1">{cat.title}</h4>
            <p className="text-xs text-slate-400 mb-2">{cat.desc}</p>
            <div className="flex items-center justify-between">
              <span className="text-xs text-slate-500">{cat.count} 项能力</span>
              <ChevronRight className="w-4 h-4 text-slate-500 group-hover:text-slate-300 transition-colors" />
            </div>
          </button>
        ))}
      </div>

      {/* 模型用量排行 */}
      <div className="bg-slate-800/50 rounded-xl border border-slate-700/50 p-5">
        <h3 className="text-base font-semibold text-slate-100 flex items-center gap-2 mb-4">
          <Database className="w-5 h-5 text-cyan-400" />
          模型调用排行
        </h3>
        <div className="space-y-3">
          {MODEL_USAGE.map((m, i) => {
            const maxCalls = Math.max(...MODEL_USAGE.map(x => x.calls));
            const pct = (m.calls / maxCalls) * 100;
            return (
              <div key={i} className="group">
                <div className="flex items-center justify-between mb-1.5">
                  <div className="flex items-center gap-2">
                    <span className="text-xs font-mono text-slate-500 w-5">{i + 1}</span>
                    <span className="text-sm text-slate-200 font-medium">{m.model}</span>
                  </div>
                  <div className="flex items-center gap-4 text-xs">
                    <span className="text-slate-400">{formatNumber(m.calls)} 次调用</span>
                    {m.tokens > 0 && <span className="text-slate-500">{formatNumber(m.tokens)} tokens</span>}
                    <span className="text-amber-400 font-medium">¥{m.cost.toFixed(2)}</span>
                  </div>
                </div>
                <div className="h-2 bg-slate-700/50 rounded-full overflow-hidden">
                  <div
                    className="h-full rounded-full bg-gradient-to-r from-blue-600 to-cyan-500 transition-all duration-500"
                    style={{ width: `${pct}%` }}
                  />
                </div>
              </div>
            );
          })}
        </div>
        <div className="mt-4 pt-3 border-t border-slate-700/50 flex items-center justify-between">
          <span className="text-xs text-slate-500">总成本估算</span>
          <span className="text-sm font-bold text-amber-400">¥{MODEL_USAGE.reduce((s, m) => s + m.cost, 0).toFixed(2)}</span>
        </div>
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
              <h3 className="text-sm font-semibold text-slate-300 mb-4 flex items-center gap-2">
                <Layers className="w-4 h-4 text-blue-400" />
                {cat}
                <span className="text-xs text-slate-500 font-normal ml-1">{items.length} 项能力</span>
              </h3>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {items.map(cap => (
                  <div
                    key={cap.id}
                    className={`bg-slate-800/50 rounded-xl border ${cap.borderColor} p-5 hover:shadow-lg hover:shadow-blue-500/5 transition-all duration-300 group cursor-pointer`}
                    onClick={() => router.push(cap.route)}
                  >
                    {/* 头部 */}
                    <div className="flex items-start justify-between mb-3">
                      <div className="flex items-center gap-3">
                        <div className={`w-11 h-11 rounded-xl ${cap.bgColor} ${cap.color} flex items-center justify-center`}>
                          {cap.icon}
                        </div>
                        <div>
                          <h4 className="text-sm font-semibold text-slate-100 group-hover:text-white transition-colors">{cap.name}</h4>
                          <div className="flex items-center gap-2 mt-0.5">
                            <span className={`inline-flex items-center gap-1 px-1.5 py-0.5 rounded-full text-[10px] font-medium ${
                              cap.status === 'online' ? 'bg-green-500/15 text-green-400' :
                              cap.status === 'beta' ? 'bg-amber-500/15 text-amber-400' :
                              'bg-red-500/15 text-red-400'
                            }`}>
                              <span className={`w-1.5 h-1.5 rounded-full ${
                                cap.status === 'online' ? 'bg-green-400' :
                                cap.status === 'beta' ? 'bg-amber-400' :
                                'bg-red-400'
                              } ${cap.status === 'online' ? 'animate-pulse' : ''}`} />
                              {cap.status === 'online' ? '在线' : cap.status === 'beta' ? '测试中' : '离线'}
                            </span>
                          </div>
                        </div>
                      </div>
                      <ArrowRight className="w-4 h-4 text-slate-600 group-hover:text-slate-300 transition-colors mt-1" />
                    </div>

                    {/* 描述 */}
                    <p className="text-xs text-slate-400 leading-relaxed mb-3">{cap.description}</p>

                    {/* 能力标签 */}
                    <div className="flex flex-wrap gap-1.5 mb-4">
                      {cap.features.map(f => (
                        <span key={f} className="px-2 py-0.5 rounded-md text-[10px] font-medium bg-slate-700/50 text-slate-300 border border-slate-600/30">
                          {f}
                        </span>
                      ))}
                    </div>

                    {/* 指标 */}
                    <div className="grid grid-cols-3 gap-3 pt-3 border-t border-slate-700/30">
                      <div className="text-center">
                        <div className="text-xs text-slate-500 mb-0.5">今日调用</div>
                        <div className="text-sm font-bold text-slate-200 font-mono">{cap.callsToday}</div>
                      </div>
                      <div className="text-center">
                        <div className="text-xs text-slate-500 mb-0.5">成功率</div>
                        <div className={`text-sm font-bold font-mono ${cap.successRate >= 98 ? 'text-green-400' : cap.successRate >= 95 ? 'text-amber-400' : 'text-red-400'}`}>
                          {cap.successRate}%
                        </div>
                      </div>
                      <div className="text-center">
                        <div className="text-xs text-slate-500 mb-0.5">平均耗时</div>
                        <div className="text-sm font-bold text-slate-200 font-mono">{formatLatency(cap.avgLatency)}</div>
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
  const renderMonitor = () => (
    <div className="space-y-6">
      {/* 实时状态 */}
      <div className="bg-slate-800/50 rounded-xl border border-slate-700/50 p-5">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-base font-semibold text-slate-100 flex items-center gap-2">
            <Server className="w-5 h-5 text-green-400" />
            服务健康状态
          </h3>
          <span className="flex items-center gap-1.5 text-xs text-green-400">
            <span className="w-2 h-2 rounded-full bg-green-400 animate-pulse" />
            全部正常
          </span>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-3">
          {[
            { name: 'DeepSeek API', status: 'normal', latency: '1.2s', uptime: '99.9%' },
            { name: '豆包 Vision', status: 'normal', latency: '0.8s', uptime: '99.7%' },
            { name: 'MiniMax Embedding', status: 'normal', latency: '0.3s', uptime: '99.95%' },
            { name: 'Web Search', status: 'normal', latency: '1.8s', uptime: '98.5%' },
          ].map((s, i) => (
            <div key={i} className="bg-slate-900/50 rounded-lg border border-slate-700/30 p-3 flex items-center gap-3">
              <div className={`w-2.5 h-2.5 rounded-full ${s.status === 'normal' ? 'bg-green-400 animate-pulse' : 'bg-red-400'}`} />
              <div className="flex-1 min-w-0">
                <div className="text-xs font-medium text-slate-200 truncate">{s.name}</div>
                <div className="text-[10px] text-slate-500">{s.latency} · {s.uptime}</div>
              </div>
              <CheckCircle className="w-4 h-4 text-green-500/50" />
            </div>
          ))}
        </div>
      </div>

      {/* 模型用量明细 */}
      <div className="bg-slate-800/50 rounded-xl border border-slate-700/50 p-5">
        <h3 className="text-base font-semibold text-slate-100 flex items-center gap-2 mb-4">
          <PieChart className="w-5 h-5 text-violet-400" />
          模型用量明细
        </h3>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-700/50">
                <th className="text-left py-3 px-3 text-slate-400 font-medium">模型</th>
                <th className="text-right py-3 px-3 text-slate-400 font-medium">调用次数</th>
                <th className="text-right py-3 px-3 text-slate-400 font-medium">Token 消耗</th>
                <th className="text-right py-3 px-3 text-slate-400 font-medium">费用</th>
                <th className="text-right py-3 px-3 text-slate-400 font-medium">占比</th>
              </tr>
            </thead>
            <tbody>
              {MODEL_USAGE.map((m, i) => {
                const totalCalls = MODEL_USAGE.reduce((s, x) => s + x.calls, 0);
                const pct = ((m.calls / totalCalls) * 100).toFixed(1);
                return (
                  <tr key={i} className="border-b border-slate-700/30 hover:bg-slate-700/20 transition-colors">
                    <td className="py-3 px-3 text-slate-200 font-medium">{m.model}</td>
                    <td className="py-3 px-3 text-right font-mono text-slate-300">{formatNumber(m.calls)}</td>
                    <td className="py-3 px-3 text-right font-mono text-slate-400">{m.tokens > 0 ? formatNumber(m.tokens) : '-'}</td>
                    <td className="py-3 px-3 text-right font-mono text-amber-400">¥{m.cost.toFixed(2)}</td>
                    <td className="py-3 px-3 text-right">
                      <div className="flex items-center justify-end gap-2">
                        <div className="w-16 h-1.5 bg-slate-700/50 rounded-full overflow-hidden">
                          <div className="h-full rounded-full bg-blue-500" style={{ width: `${pct}%` }} />
                        </div>
                        <span className="text-xs text-slate-400 w-10 text-right">{pct}%</span>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>

      {/* 最近调用日志 */}
      <div className="bg-slate-800/50 rounded-xl border border-slate-700/50 p-5">
        <h3 className="text-base font-semibold text-slate-100 flex items-center gap-2 mb-4">
          <Clock className="w-5 h-5 text-sky-400" />
          最近调用记录
        </h3>
        <div className="space-y-2">
          {[
            { time: '16:42:31', capability: 'AI 智能对话', model: 'DeepSeek V4 Pro', status: 'success', latency: '2.3s', tokens: 1847 },
            { time: '16:41:58', capability: '知识库语义检索', model: 'MiniMax Embedding', status: 'success', latency: '0.3s', tokens: 423 },
            { time: '16:41:22', capability: 'AI 智能生图', model: 'nano-banana', status: 'success', latency: '8.1s', tokens: 0 },
            { time: '16:40:45', capability: '联网搜索引擎', model: 'Web Search', status: 'success', latency: '1.9s', tokens: 0 },
            { time: '16:40:12', capability: 'AI 智能对话', model: 'DeepSeek V4 Pro', status: 'success', latency: '3.1s', tokens: 2341 },
            { time: '16:39:55', capability: '工厂供应链助手', model: 'DeepSeek V4 Pro', status: 'success', latency: '2.7s', tokens: 1567 },
            { time: '16:39:21', capability: 'AI 智能识别', model: '豆包 Vision', status: 'fail', latency: '1.2s', tokens: 0 },
            { time: '16:38:47', capability: '记忆库 RAG', model: 'MiniMax Embedding', status: 'success', latency: '0.4s', tokens: 312 },
          ].map((log, i) => (
            <div key={i} className="flex items-center gap-3 py-2 px-3 rounded-lg hover:bg-slate-700/20 transition-colors text-xs">
              <span className="text-slate-500 font-mono w-16 shrink-0">{log.time}</span>
              <span className="text-slate-200 w-32 truncate">{log.capability}</span>
              <span className="text-slate-400 w-32 truncate">{log.model}</span>
              <span className={`shrink-0 ${log.status === 'success' ? 'text-green-400' : 'text-red-400'}`}>
                {log.status === 'success' ? <CheckCircle className="w-3.5 h-3.5" /> : <XCircle className="w-3.5 h-3.5" />}
              </span>
              <span className="text-slate-400 font-mono w-12 text-right">{log.latency}</span>
              <span className="text-slate-500 font-mono w-16 text-right">{log.tokens > 0 ? `${log.tokens} tok` : '-'}</span>
            </div>
          ))}
        </div>
      </div>

      {/* 配额与限制 */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div className="bg-slate-800/50 rounded-xl border border-slate-700/50 p-5">
          <h3 className="text-sm font-semibold text-slate-200 flex items-center gap-2 mb-3">
            <Shield className="w-4 h-4 text-blue-400" />
            API 配额
          </h3>
          <div className="space-y-3">
            {[
              { name: 'DeepSeek V4 Pro', used: 2847, total: 10000 },
              { name: '豆包 Vision', used: 1521, total: 5000 },
              { name: 'MiniMax Embedding', used: 4521, total: 20000 },
              { name: '图片生成', used: 1235, total: 3000 },
            ].map((q, i) => (
              <div key={i}>
                <div className="flex items-center justify-between text-xs mb-1">
                  <span className="text-slate-300">{q.name}</span>
                  <span className="text-slate-400">{formatNumber(q.used)} / {formatNumber(q.total)}</span>
                </div>
                <div className="h-1.5 bg-slate-700/50 rounded-full overflow-hidden">
                  <div
                    className={`h-full rounded-full transition-all duration-500 ${
                      (q.used / q.total) > 0.9 ? 'bg-red-500' :
                      (q.used / q.total) > 0.7 ? 'bg-amber-500' :
                      'bg-blue-500'
                    }`}
                    style={{ width: `${(q.used / q.total) * 100}%` }}
                  />
                </div>
              </div>
            ))}
          </div>
        </div>
        <div className="bg-slate-800/50 rounded-xl border border-slate-700/50 p-5">
          <h3 className="text-sm font-semibold text-slate-200 flex items-center gap-2 mb-3">
            <AlertTriangle className="w-4 h-4 text-amber-400" />
            速率限制
          </h3>
          <div className="space-y-2.5">
            {[
              { name: '对话接口', limit: '100次/分钟', current: '23次/分钟' },
              { name: '图片生成', limit: '5次/分钟', current: '1次/分钟' },
              { name: '图片识别', limit: '20次/分钟', current: '8次/分钟' },
              { name: '向量检索', limit: '50次/分钟', current: '12次/分钟' },
              { name: '联网搜索', limit: '30次/分钟', current: '5次/分钟' },
            ].map((r, i) => (
              <div key={i} className="flex items-center justify-between text-xs py-1.5 px-2 rounded-lg bg-slate-900/30">
                <span className="text-slate-300">{r.name}</span>
                <div className="flex items-center gap-3">
                  <span className="text-slate-400">{r.current}</span>
                  <span className="text-slate-500">/ {r.limit}</span>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );

  return (
    <div className="min-h-screen bg-[#0a0e1a]">
      {/* 顶部导航 */}
      <header className="bg-[#0f172a]/90 backdrop-blur-xl border-b border-slate-700/50 sticky top-0 z-50">
        <div className="max-w-[1400px] mx-auto px-5 h-14 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <button onClick={() => router.push('/')} className="flex items-center gap-1 text-sm text-slate-400 hover:text-slate-200 transition-colors shrink-0">
              <ChevronRight className="w-4 h-4 rotate-180" />
              <span>返回</span>
            </button>
            <span className="text-slate-700">|</span>
            <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-blue-600 to-cyan-500 flex items-center justify-center text-white shadow-sm">
              <Sparkles className="w-4 h-4" />
            </div>
            <div>
              <h1 className="text-[15px] font-bold text-slate-100 leading-tight">AI 能力中心</h1>
              <p className="text-[11px] text-slate-500">统一管理 · 用量监控 · 能力编排</p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <span className="flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-green-500/10 text-green-400 text-xs border border-green-500/20">
              <span className="w-1.5 h-1.5 rounded-full bg-green-400 animate-pulse" />
              {stats.onlineCount}/{stats.totalCount} 在线
            </span>
          </div>
        </div>
      </header>

      <div className="max-w-[1400px] mx-auto px-5 py-5">
        {/* Tab 导航 */}
        <div className="flex gap-1 mb-6 bg-slate-800/50 rounded-xl p-1 border border-slate-700/50 w-fit">
          {[
            { key: 'overview' as const, label: '能力概览', icon: <BarChart3 className="w-4 h-4" /> },
            { key: 'capabilities' as const, label: '全部能力', icon: <Sparkles className="w-4 h-4" /> },
            { key: 'monitor' as const, label: '用量监控', icon: <Activity className="w-4 h-4" /> },
          ].map(tab => (
            <button key={tab.key} onClick={() => setActiveTab(tab.key)}
              className={`flex items-center gap-1.5 px-4 py-2 rounded-lg text-sm font-medium transition-all ${
                activeTab === tab.key
                  ? 'bg-gradient-to-r from-blue-600 to-cyan-500 text-white shadow-sm'
                  : 'text-slate-400 hover:bg-slate-700/50 hover:text-slate-200'
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
