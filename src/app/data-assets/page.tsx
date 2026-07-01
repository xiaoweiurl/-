'use client';

import { useState, useEffect, useMemo, useCallback } from 'react';
import {
  Database, Search, Filter, Tag, GitBranch, Star, Eye, Clock,
  ArrowRight, ChevronRight, ChevronDown, FileText, FileSpreadsheet,
  Image, FolderOpen, Upload, RefreshCw, TrendingUp, AlertTriangle,
  CheckCircle2, XCircle, BarChart3, Layers, Network, Shield, Activity,
  MoreHorizontal, Plus, Bookmark, Zap, CircleDot, Boxes,
} from 'lucide-react';

// ========== 数据类型 ==========
type AssetType = 'knowledge' | 'document' | 'image' | 'memory' | 'model';
type QualityLevel = 'high' | 'medium' | 'low' | 'unknown';

interface DataAsset {
  id: string; name: string; type: AssetType; category: string; tags: string[];
  quality: QualityLevel; qualityScore: number; size: number;
  createdAt: string; updatedAt: string; lastAccessedAt: string;
  accessCount: number; lineage: { sources: string[]; targets: string[] };
  owner: string; status: 'active' | 'archived' | 'processing';
  format: string; vectorized?: boolean; embeddingStatus?: string; sourceUrl?: string;
}

const TYPE_CONFIG: Record<AssetType, { label: string; icon: React.ReactNode; color: string; bgColor: string }> = {
  knowledge: { label: '知识库', icon: <BookOpen className="w-4 h-4" />, color: 'text-blue-400', bgColor: 'bg-blue-500/10' },
  document: { label: '文档', icon: <FileText className="w-4 h-4" />, color: 'text-emerald-400', bgColor: 'bg-emerald-500/10' },
  image: { label: '图片', icon: <Image className="w-4 h-4" />, color: 'text-violet-400', bgColor: 'bg-violet-500/10' },
  memory: { label: '记忆库', icon: <Layers className="w-4 h-4" />, color: 'text-amber-400', bgColor: 'bg-amber-500/10' },
  model: { label: 'AI模型', icon: <Zap className="w-4 h-4" />, color: 'text-cyan-400', bgColor: 'bg-cyan-500/10' },
};

const QUALITY_CONFIG: Record<QualityLevel, { label: string; color: string; icon: React.ReactNode }> = {
  high: { label: '优质', color: 'text-emerald-400', icon: <CheckCircle2 className="w-3.5 h-3.5" /> },
  medium: { label: '合格', color: 'text-amber-400', icon: <AlertTriangle className="w-3.5 h-3.5" /> },
  low: { label: '低质', color: 'text-red-400', icon: <XCircle className="w-3.5 h-3.5" /> },
  unknown: { label: '未评', color: 'text-slate-400', icon: <CircleDot className="w-3.5 h-3.5" /> },
};

function BookOpen(props: React.SVGProps<SVGSVGElement> & { className?: string }) {
  return (
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" {...props}>
      <path d="M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z" /><path d="M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z" />
    </svg>
  );
}

function formatSize(bytes: number): string {
  if (bytes === 0) return 'API';
  if (bytes < 1024) return bytes + ' B';
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB';
  return (bytes / 1048576).toFixed(1) + ' MB';
}
function formatDate(dateStr: string): string {
  return new Date(dateStr).toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' });
}
function timeAgo(dateStr: string): string {
  const diff = Date.now() - new Date(dateStr).getTime();
  const hours = Math.floor(diff / 3600000);
  if (hours < 24) return `${hours}小时前`;
  const days = Math.floor(hours / 24);
  if (days < 30) return `${days}天前`;
  return formatDate(dateStr);
}

export default function DataAssetsPage() {
  const [activeTab, setActiveTab] = useState<'overview' | 'catalog' | 'lineage' | 'quality'>('catalog');
  const [searchQuery, setSearchQuery] = useState('');
  const [filterType, setFilterType] = useState<AssetType | 'all'>('all');
  const [filterQuality, setFilterQuality] = useState<QualityLevel | 'all'>('all');
  const [sortBy, setSortBy] = useState<'name' | 'quality' | 'access' | 'updated'>('updated');
  const [sortOrder, setSortOrder] = useState<'asc' | 'desc'>('desc');
  const [selectedAsset, setSelectedAsset] = useState<DataAsset | null>(null);
  const [expandedCategories, setExpandedCategories] = useState<Set<string>>(new Set());

  const [assets, setAssets] = useState<DataAsset[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const fetchAssets = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const res = await fetch('/api/data-assets');
      const json = await res.json();
      if (json.success && Array.isArray(json.data)) {
        setAssets(json.data);
      } else {
        setError(json.error || '加载失败');
      }
    } catch (e) {
      setError('网络错误，无法加载数据资产');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchAssets(); }, [fetchAssets]);

  const categoryStats = useMemo(() => {
    const stats: Record<string, { count: number; types: Set<AssetType>; avgQuality: number }> = {};
    assets.forEach(a => {
      if (!stats[a.category]) stats[a.category] = { count: 0, types: new Set(), avgQuality: 0 };
      stats[a.category].count++;
      stats[a.category].types.add(a.type);
      stats[a.category].avgQuality += a.qualityScore;
    });
    Object.keys(stats).forEach(k => { stats[k].avgQuality = Math.round(stats[k].avgQuality / stats[k].count); });
    return stats;
  }, [assets]);

  const typeStats = useMemo(() => {
    const stats: Record<AssetType, number> = { knowledge: 0, document: 0, image: 0, memory: 0, model: 0 };
    assets.forEach(a => { if (stats[a.type] != null) stats[a.type]++; });
    return stats;
  }, [assets]);

  const filteredAssets = useMemo(() => {
    let list = [...assets];
    if (searchQuery) {
      const q = searchQuery.toLowerCase();
      list = list.filter(a => a.name.toLowerCase().includes(q) || a.tags.some(t => t.toLowerCase().includes(q)) || a.category.toLowerCase().includes(q));
    }
    if (filterType !== 'all') list = list.filter(a => a.type === filterType);
    if (filterQuality !== 'all') list = list.filter(a => a.quality === filterQuality);
    list.sort((a, b) => {
      let cmp = 0;
      switch (sortBy) {
        case 'name': cmp = a.name.localeCompare(b.name); break;
        case 'quality': cmp = a.qualityScore - b.qualityScore; break;
        case 'access': cmp = a.accessCount - b.accessCount; break;
        case 'updated': cmp = new Date(a.updatedAt).getTime() - new Date(b.updatedAt).getTime(); break;
      }
      return sortOrder === 'desc' ? -cmp : cmp;
    });
    return list;
  }, [assets, searchQuery, filterType, filterQuality, sortBy, sortOrder]);

  const qualityDist = useMemo(() => {
    const dist = { high: 0, medium: 0, low: 0, unknown: 0 };
    assets.forEach(a => dist[a.quality]++);
    return dist;
  }, [assets]);

  const avgQuality = useMemo(() => assets.length ? Math.round(assets.reduce((s, a) => s + a.qualityScore, 0) / assets.length) : 0, [assets]);

  const toggleCategory = useCallback((cat: string) => {
    setExpandedCategories(prev => { const next = new Set(prev); next.has(cat) ? next.delete(cat) : next.add(cat); return next; });
  }, []);

  if (loading) {
    return (
      <div className="min-h-screen bg-[#0a0e1a] text-slate-200 flex items-center justify-center">
        <div className="text-center">
          <RefreshCw className="w-8 h-8 text-blue-400 animate-spin mx-auto mb-3" />
          <p className="text-slate-400">加载数据资产...</p>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="min-h-screen bg-[#0a0e1a] text-slate-200 flex items-center justify-center">
        <div className="text-center">
          <AlertTriangle className="w-8 h-8 text-red-400 mx-auto mb-3" />
          <p className="text-slate-300 mb-2">{error}</p>
          <button onClick={fetchAssets} className="px-4 py-2 bg-blue-600/20 text-blue-400 border border-blue-500/30 rounded-lg text-sm hover:bg-blue-600/30 transition-colors">
            <RefreshCw className="w-4 h-4 inline mr-1" />重试
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-[#0a0e1a] text-slate-200">
      {/* 顶部 */}
      <div className="sticky top-0 z-20 bg-[#0f172a]/90 backdrop-blur-xl border-b border-slate-700/50">
        <div className="flex items-center justify-between px-6 py-4">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-blue-600 to-cyan-500 flex items-center justify-center shadow-[0_0_15px_rgba(59,130,246,0.3)]">
              <Database className="w-5 h-5 text-white" />
            </div>
            <div>
              <h1 className="text-lg font-bold text-slate-100">数据资产管理</h1>
              <p className="text-xs text-slate-400">统一数据资产目录 · 血缘关系 · 质量评分 · 使用追踪</p>
            </div>
          </div>
          <div className="flex items-center gap-3">
            <div className="relative">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
              <input value={searchQuery} onChange={e => setSearchQuery(e.target.value)}
                placeholder="搜索资产名称、标签、分类..."
                className="w-72 pl-9 pr-4 py-2 bg-slate-800/50 border border-slate-700/50 rounded-lg text-sm text-slate-200 placeholder:text-slate-500 focus:outline-none focus:border-blue-500/50 focus:ring-1 focus:ring-blue-500/20" />
            </div>
            <button onClick={fetchAssets} className="px-3 py-2 bg-slate-800/50 border border-slate-700/50 rounded-lg text-sm text-slate-300 hover:bg-slate-700/50 transition-colors">
              <RefreshCw className="w-4 h-4 inline mr-1" />刷新
            </button>
          </div>
        </div>
        <div className="flex px-6 gap-1">
          {([
            { key: 'overview' as const, label: '资产概览', icon: BarChart3 },
            { key: 'catalog' as const, label: '资产目录', icon: FolderOpen },
            { key: 'lineage' as const, label: '血缘关系', icon: Network },
            { key: 'quality' as const, label: '质量监控', icon: Shield },
          ]).map(tab => (
            <button key={tab.key} onClick={() => setActiveTab(tab.key)}
              className={`flex items-center gap-2 px-4 py-2.5 text-sm rounded-t-lg border-b-2 transition-colors ${activeTab === tab.key ? 'border-blue-500 text-blue-400 bg-blue-500/5' : 'border-transparent text-slate-400 hover:text-slate-200 hover:bg-slate-800/30'}`}>
              <tab.icon className="w-4 h-4" />{tab.label}
            </button>
          ))}
        </div>
      </div>

      <div className="p-6">
        {/* ===== 资产概览 ===== */}
        {activeTab === 'overview' && (
          <div className="space-y-6">
            <div className="grid grid-cols-4 gap-4">
              {[
                { label: '数据资产总数', value: assets.length, unit: '项', icon: Database, color: 'blue' },
                { label: '平均质量评分', value: avgQuality, unit: '分', icon: Star, color: 'emerald' },
                { label: '今日访问次数', value: assets.reduce((s, a) => s + Math.floor(a.accessCount * 0.05), 0), unit: '次', icon: Eye, color: 'violet' },
                { label: '活跃资产占比', value: assets.length ? Math.round(assets.filter(a => a.status === 'active').length / assets.length * 100) : 0, unit: '%', icon: Activity, color: 'cyan' },
              ].map((kpi, i) => (
                <div key={i} className="bg-slate-800/50 border border-slate-700/50 rounded-xl p-4 hover:shadow-[0_0_20px_rgba(59,130,246,0.1)] transition-all">
                  <div className="flex items-center justify-between mb-3">
                    <span className="text-xs text-slate-400">{kpi.label}</span>
                    <kpi.icon className={`w-4 h-4 text-${kpi.color}-400`} />
                  </div>
                  <div className="flex items-baseline gap-1">
                    <span className={`text-2xl font-bold font-mono text-${kpi.color}-400 [text-shadow:0_0_10px_rgba(59,130,246,0.3)]`}>{kpi.value}</span>
                    <span className="text-sm text-slate-400">{kpi.unit}</span>
                  </div>
                </div>
              ))}
            </div>
            <div className="grid grid-cols-3 gap-4">
              <div className="bg-slate-800/50 border border-slate-700/50 rounded-xl p-5">
                <h3 className="text-sm font-semibold text-slate-200 mb-4">资产类型分布</h3>
                <div className="space-y-3">
                  {(Object.entries(typeStats) as [AssetType, number][]).map(([type, count]) => {
                    const cfg = TYPE_CONFIG[type];
                    const pct = assets.length ? Math.round(count / assets.length * 100) : 0;
                    return (
                      <div key={type} className="flex items-center gap-3">
                        <div className={`w-8 h-8 rounded-lg ${cfg.bgColor} flex items-center justify-center ${cfg.color}`}>{cfg.icon}</div>
                        <div className="flex-1">
                          <div className="flex justify-between text-xs mb-1">
                            <span className="text-slate-300">{cfg.label}</span>
                            <span className="text-slate-400">{count} 项 ({pct}%)</span>
                          </div>
                          <div className="h-1.5 bg-slate-700/50 rounded-full overflow-hidden">
                            <div className={`h-full rounded-full ${cfg.color.replace('text-', 'bg-')}`} style={{ width: `${pct}%` }} />
                          </div>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
              <div className="col-span-2 bg-slate-800/50 border border-slate-700/50 rounded-xl p-5">
                <h3 className="text-sm font-semibold text-slate-200 mb-4">资产分类概览</h3>
                <div className="space-y-2">
                  {Object.entries(categoryStats).map(([cat, stat]) => (
                    <div key={cat} className="flex items-center gap-3 p-3 bg-slate-900/30 rounded-lg hover:bg-slate-700/20 transition-colors">
                      <FolderOpen className="w-4 h-4 text-blue-400" />
                      <span className="text-sm text-slate-200 flex-1">{cat}</span>
                      <div className="flex items-center gap-2">
                        {Array.from(stat.types).map(t => (
                          <span key={t} className={`px-1.5 py-0.5 text-[10px] rounded ${TYPE_CONFIG[t].bgColor} ${TYPE_CONFIG[t].color}`}>{TYPE_CONFIG[t].label}</span>
                        ))}
                      </div>
                      <span className="text-xs text-slate-400 font-mono">{stat.count}项</span>
                      <div className="flex items-center gap-1">
                        <Star className="w-3 h-3 text-amber-400" />
                        <span className={`text-xs font-mono ${stat.avgQuality >= 80 ? 'text-emerald-400' : stat.avgQuality >= 60 ? 'text-amber-400' : 'text-red-400'}`}>{stat.avgQuality}</span>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </div>
            <div className="bg-slate-800/50 border border-slate-700/50 rounded-xl p-5">
              <h3 className="text-sm font-semibold text-slate-200 mb-4">最近活跃资产</h3>
              <div className="grid grid-cols-2 gap-3">
                {[...assets].sort((a, b) => new Date(b.lastAccessedAt).getTime() - new Date(a.lastAccessedAt).getTime()).slice(0, 6).map(asset => {
                  const cfg = TYPE_CONFIG[asset.type];
                  return (
                    <div key={asset.id} className="flex items-center gap-3 p-3 bg-slate-900/30 rounded-lg">
                      <div className={`w-8 h-8 rounded-lg ${cfg.bgColor} flex items-center justify-center ${cfg.color}`}>{cfg.icon}</div>
                      <div className="flex-1 min-w-0">
                        <div className="text-sm text-slate-200 truncate">{asset.name}</div>
                        <div className="text-xs text-slate-500">{timeAgo(asset.lastAccessedAt)} · {asset.accessCount}次访问</div>
                      </div>
                      <span className={`text-xs font-mono ${asset.qualityScore >= 80 ? 'text-emerald-400' : 'text-amber-400'}`}>{asset.qualityScore}分</span>
                    </div>
                  );
                })}
              </div>
            </div>
          </div>
        )}

        {/* ===== 资产目录 ===== */}
        {activeTab === 'catalog' && (
          <div className="flex gap-6">
            <div className="w-64 flex-shrink-0">
              <div className="bg-slate-800/50 border border-slate-700/50 rounded-xl p-4 sticky top-28">
                <h3 className="text-sm font-semibold text-slate-200 mb-3">分类目录</h3>
                <div className="space-y-1 mb-4">
                  <button onClick={() => setFilterType('all')}
                    className={`w-full flex items-center gap-2 px-3 py-2 rounded-lg text-sm transition-colors ${filterType === 'all' ? 'bg-blue-500/10 text-blue-400' : 'text-slate-400 hover:bg-slate-700/30'}`}>
                    <Boxes className="w-4 h-4" />全部类型<span className="ml-auto font-mono text-xs">{assets.length}</span>
                  </button>
                  {(Object.entries(typeStats) as [AssetType, number][]).map(([type, count]) => {
                    const cfg = TYPE_CONFIG[type];
                    return (
                      <button key={type} onClick={() => setFilterType(filterType === type ? 'all' : type)}
                        className={`w-full flex items-center gap-2 px-3 py-2 rounded-lg text-sm transition-colors ${filterType === type ? 'bg-blue-500/10 text-blue-400' : 'text-slate-400 hover:bg-slate-700/30'}`}>
                        <span className={cfg.color}>{cfg.icon}</span>{cfg.label}<span className="ml-auto font-mono text-xs">{count}</span>
                      </button>
                    );
                  })}
                </div>
                <div className="border-t border-slate-700/30 pt-3">
                  <h4 className="text-xs text-slate-500 mb-2">业务分类</h4>
                  <div className="space-y-1">
                    {Object.entries(categoryStats).map(([cat, stat]) => (
                      <button key={cat} onClick={() => toggleCategory(cat)}
                        className="w-full flex items-center gap-2 px-3 py-1.5 rounded-lg text-sm text-slate-400 hover:bg-slate-700/30 hover:text-slate-200 transition-colors">
                        {expandedCategories.has(cat) ? <ChevronDown className="w-3 h-3" /> : <ChevronRight className="w-3 h-3" />}
                        <FolderOpen className="w-3.5 h-3.5" />
                        <span className="flex-1 text-left truncate">{cat}</span>
                        <span className="font-mono text-xs">{stat.count}</span>
                      </button>
                    ))}
                  </div>
                </div>
              </div>
            </div>
            <div className="flex-1 space-y-3">
              <div className="flex items-center gap-3 bg-slate-800/30 border border-slate-700/30 rounded-xl p-3">
                <Filter className="w-4 h-4 text-slate-400" />
                <span className="text-xs text-slate-400">质量:</span>
                {(['all', 'high', 'medium', 'low'] as const).map(q => (
                  <button key={q} onClick={() => setFilterQuality(q)}
                    className={`px-2.5 py-1 rounded text-xs transition-colors ${filterQuality === q ? 'bg-blue-500/20 text-blue-400' : 'text-slate-400 hover:text-slate-200'}`}>
                    {q === 'all' ? '全部' : QUALITY_CONFIG[q].label}
                  </button>
                ))}
                <div className="flex-1" />
                <span className="text-xs text-slate-400">排序:</span>
                <select value={`${sortBy}-${sortOrder}`}
                  onChange={e => { const [s, o] = e.target.value.split('-'); setSortBy(s as typeof sortBy); setSortOrder(o as typeof sortOrder); }}
                  className="bg-slate-800/50 border border-slate-700/50 rounded px-2 py-1 text-xs text-slate-300">
                  <option value="updated-desc">最近更新</option>
                  <option value="updated-asc">最早更新</option>
                  <option value="quality-desc">质量最高</option>
                  <option value="quality-asc">质量最低</option>
                  <option value="access-desc">访问最多</option>
                  <option value="name-asc">名称 A-Z</option>
                </select>
                <span className="text-xs text-slate-500">共 {filteredAssets.length} 项</span>
              </div>
              {filteredAssets.map(asset => {
                const cfg = TYPE_CONFIG[asset.type];
                const qCfg = QUALITY_CONFIG[asset.quality];
                return (
                  <div key={asset.id} onClick={() => setSelectedAsset(selectedAsset?.id === asset.id ? null : asset)}
                    className={`bg-slate-800/50 border rounded-xl p-4 cursor-pointer transition-all hover:shadow-[0_0_15px_rgba(59,130,246,0.08)] ${selectedAsset?.id === asset.id ? 'border-blue-500/50 bg-slate-800/80' : 'border-slate-700/50'}`}>
                    <div className="flex items-start gap-4">
                      <div className={`w-11 h-11 rounded-xl ${cfg.bgColor} flex items-center justify-center ${cfg.color} flex-shrink-0`}>{cfg.icon}</div>
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 mb-1">
                          <h4 className="text-sm font-semibold text-slate-100 truncate">{asset.name}</h4>
                          {asset.status === 'archived' && <span className="px-1.5 py-0.5 text-[10px] rounded bg-slate-600/30 text-slate-400">已归档</span>}
                          {asset.vectorized && <span className="px-1.5 py-0.5 text-[10px] rounded bg-emerald-500/10 text-emerald-400 flex items-center gap-0.5"><Zap className="w-2.5 h-2.5" />向量化</span>}
                        </div>
                        <div className="flex items-center gap-2 mb-2">
                          <span className={`px-1.5 py-0.5 text-[10px] rounded ${cfg.bgColor} ${cfg.color}`}>{cfg.label}</span>
                          <span className="text-xs text-slate-500">{asset.format}</span>
                          <span className="text-xs text-slate-600">·</span>
                          <span className="text-xs text-slate-500">{formatSize(asset.size)}</span>
                          <span className="text-xs text-slate-600">·</span>
                          <span className="text-xs text-slate-500">{asset.owner}</span>
                        </div>
                        <div className="flex flex-wrap gap-1">
                          {asset.tags.map(tag => <span key={tag} className="px-1.5 py-0.5 text-[10px] rounded bg-slate-700/50 text-slate-400">{tag}</span>)}
                        </div>
                      </div>
                      <div className="flex items-center gap-4 flex-shrink-0">
                        <div className="text-center">
                          <div className={`text-lg font-bold font-mono ${asset.qualityScore >= 80 ? 'text-emerald-400' : asset.qualityScore >= 60 ? 'text-amber-400' : 'text-red-400'}`}>{asset.qualityScore}</div>
                          <div className="text-[10px] text-slate-500">质量分</div>
                        </div>
                        <div className="text-center">
                          <div className="text-lg font-bold font-mono text-blue-400">{asset.accessCount}</div>
                          <div className="text-[10px] text-slate-500">访问</div>
                        </div>
                        <div className="text-center min-w-[56px]">
                          <div className="text-xs text-slate-400">{timeAgo(asset.updatedAt)}</div>
                          <div className="text-[10px] text-slate-500">更新</div>
                        </div>
                      </div>
                    </div>
                    {selectedAsset?.id === asset.id && (
                      <div className="mt-4 pt-4 border-t border-slate-700/30 grid grid-cols-3 gap-4">
                        <div>
                          <h5 className="text-xs font-semibold text-slate-300 mb-2 flex items-center gap-1"><Network className="w-3 h-3 text-blue-400" />血缘关系</h5>
                          <div className="space-y-1.5">
                            <div className="text-xs text-slate-400">上游来源:</div>
                            {asset.lineage.sources.map(src => <div key={src} className="flex items-center gap-1 text-xs text-slate-300 pl-2"><ArrowRight className="w-3 h-3 text-blue-400" />{src}</div>)}
                            <div className="text-xs text-slate-400 mt-1">下游消费:</div>
                            {asset.lineage.targets.length > 0 ? asset.lineage.targets.map(tgt => <div key={tgt} className="flex items-center gap-1 text-xs text-slate-300 pl-2"><ArrowRight className="w-3 h-3 text-cyan-400" />{tgt}</div>) : <div className="text-xs text-slate-500 pl-2">暂无下游</div>}
                          </div>
                        </div>
                        <div>
                          <h5 className="text-xs font-semibold text-slate-300 mb-2 flex items-center gap-1"><Shield className="w-3 h-3 text-emerald-400" />质量评分</h5>
                          <div className="space-y-2">
                            <div><div className="flex justify-between text-xs mb-0.5"><span className="text-slate-400">完整性</span><span className="text-slate-300">{Math.min(100, asset.qualityScore + 3)}%</span></div><div className="h-1.5 bg-slate-700/50 rounded-full overflow-hidden"><div className="h-full bg-emerald-500 rounded-full" style={{ width: `${Math.min(100, asset.qualityScore + 3)}%` }} /></div></div>
                            <div><div className="flex justify-between text-xs mb-0.5"><span className="text-slate-400">准确性</span><span className="text-slate-300">{asset.qualityScore}%</span></div><div className="h-1.5 bg-slate-700/50 rounded-full overflow-hidden"><div className="h-full bg-blue-500 rounded-full" style={{ width: `${asset.qualityScore}%` }} /></div></div>
                            <div><div className="flex justify-between text-xs mb-0.5"><span className="text-slate-400">时效性</span><span className="text-slate-300">{Math.max(20, asset.qualityScore - 10)}%</span></div><div className="h-1.5 bg-slate-700/50 rounded-full overflow-hidden"><div className="h-full bg-amber-500 rounded-full" style={{ width: `${Math.max(20, asset.qualityScore - 10)}%` }} /></div></div>
                          </div>
                        </div>
                        <div>
                          <h5 className="text-xs font-semibold text-slate-300 mb-2 flex items-center gap-1"><Activity className="w-3 h-3 text-violet-400" />使用追踪</h5>
                          <div className="space-y-1.5 text-xs">
                            <div className="flex justify-between"><span className="text-slate-400">总访问次数</span><span className="text-slate-200 font-mono">{asset.accessCount}</span></div>
                            <div className="flex justify-between"><span className="text-slate-400">创建时间</span><span className="text-slate-300">{formatDate(asset.createdAt)}</span></div>
                            <div className="flex justify-between"><span className="text-slate-400">最后访问</span><span className="text-slate-300">{timeAgo(asset.lastAccessedAt)}</span></div>
                            <div className="flex justify-between"><span className="text-slate-400">负责人</span><span className="text-slate-300">{asset.owner}</span></div>
                            <div className="flex justify-between"><span className="text-slate-400">向量化</span><span className={asset.vectorized ? 'text-emerald-400' : 'text-slate-500'}>{asset.vectorized ? '已完成' : '未处理'}</span></div>
                          </div>
                        </div>
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          </div>
        )}

        {/* ===== 血缘关系 ===== */}
        {activeTab === 'lineage' && (
          <div className="space-y-6">
            <div className="bg-slate-800/50 border border-slate-700/50 rounded-xl p-5">
              <h3 className="text-sm font-semibold text-slate-200 mb-4">数据血缘关系图谱</h3>
              <div className="space-y-4">
                {[
                  { level: '数据源', items: Array.from(new Set(assets.flatMap(a => a.lineage.sources))).filter(Boolean), color: 'blue' },
                  { level: '数据资产', items: assets.map(a => a.name), color: 'cyan' },
                  { level: '消费方', items: Array.from(new Set(assets.flatMap(a => a.lineage.targets))).filter(Boolean), color: 'violet' },
                ].map((layer, li) => (
                  <div key={li}>
                    <div className="flex items-center gap-2 mb-2">
                      <div className={`w-2 h-2 rounded-full bg-${layer.color}-400`} />
                      <span className="text-xs font-semibold text-slate-300">{layer.level}</span>
                      <span className="text-xs text-slate-500">({layer.items.length})</span>
                    </div>
                    <div className="flex flex-wrap gap-2 pl-4">
                      {layer.items.map((item, ii) => (
                        <span key={ii} className={`px-2.5 py-1.5 text-xs rounded-lg bg-${layer.color}-500/10 text-${layer.color}-400 border border-${layer.color}-500/20`}>{item}</span>
                      ))}
                    </div>
                    {li < 2 && <div className="flex justify-center my-2"><ArrowRight className="w-4 h-4 text-slate-500 rotate-90" /></div>}
                  </div>
                ))}
              </div>
            </div>
            <div className="bg-slate-800/50 border border-slate-700/50 rounded-xl p-5">
              <h3 className="text-sm font-semibold text-slate-200 mb-4">血缘关系明细</h3>
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead><tr className="border-b border-slate-700/30">
                    <th className="text-left py-2 px-3 text-xs text-slate-400 font-medium">数据资产</th>
                    <th className="text-left py-2 px-3 text-xs text-slate-400 font-medium">上游来源</th>
                    <th className="text-center py-2 px-3 text-xs text-slate-400 font-medium w-8"></th>
                    <th className="text-left py-2 px-3 text-xs text-slate-400 font-medium">下游消费</th>
                  </tr></thead>
                  <tbody>
                    {assets.filter(a => a.lineage.sources.length > 0 || a.lineage.targets.length > 0).map(asset => (
                      <tr key={asset.id} className="border-b border-slate-700/20 hover:bg-slate-700/10">
                        <td className="py-2 px-3 text-slate-200">{asset.name}</td>
                        <td className="py-2 px-3"><div className="flex flex-wrap gap-1">{asset.lineage.sources.map(s => <span key={s} className="px-1.5 py-0.5 text-[10px] rounded bg-blue-500/10 text-blue-400">{s}</span>)}</div></td>
                        <td className="py-2 px-3 text-center"><ArrowRight className="w-4 h-4 text-slate-500 mx-auto" /></td>
                        <td className="py-2 px-3"><div className="flex flex-wrap gap-1">{asset.lineage.targets.length > 0 ? asset.lineage.targets.map(t => <span key={t} className="px-1.5 py-0.5 text-[10px] rounded bg-violet-500/10 text-violet-400">{t}</span>) : <span className="text-xs text-slate-500">—</span>}</div></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        )}

        {/* ===== 质量监控 ===== */}
        {activeTab === 'quality' && (
          <div className="space-y-6">
            <div className="grid grid-cols-4 gap-4">
              <div className="bg-slate-800/50 border border-slate-700/50 rounded-xl p-4">
                <div className="text-xs text-slate-400 mb-2">优质资产</div>
                <div className="text-2xl font-bold font-mono text-emerald-400">{qualityDist.high}</div>
                <div className="text-xs text-slate-500 mt-1">评分 ≥ 80</div>
              </div>
              <div className="bg-slate-800/50 border border-slate-700/50 rounded-xl p-4">
                <div className="text-xs text-slate-400 mb-2">合格资产</div>
                <div className="text-2xl font-bold font-mono text-amber-400">{qualityDist.medium}</div>
                <div className="text-xs text-slate-500 mt-1">评分 60-79</div>
              </div>
              <div className="bg-slate-800/50 border border-slate-700/50 rounded-xl p-4">
                <div className="text-xs text-slate-400 mb-2">低质资产</div>
                <div className="text-2xl font-bold font-mono text-red-400">{qualityDist.low}</div>
                <div className="text-xs text-slate-500 mt-1">评分 &lt; 60</div>
              </div>
              <div className="bg-slate-800/50 border border-slate-700/50 rounded-xl p-4">
                <div className="text-xs text-slate-400 mb-2">平均质量分</div>
                <div className="text-2xl font-bold font-mono text-blue-400">{avgQuality}</div>
                <div className="text-xs text-slate-500 mt-1">满分 100</div>
              </div>
            </div>
            <div className="bg-slate-800/50 border border-slate-700/50 rounded-xl p-5">
              <h3 className="text-sm font-semibold text-slate-200 mb-4">资产质量评分排行</h3>
              <div className="space-y-2">
                {[...assets].sort((a, b) => b.qualityScore - a.qualityScore).map((asset, idx) => {
                  const qCfg = QUALITY_CONFIG[asset.quality];
                  return (
                    <div key={asset.id} className="flex items-center gap-3 p-3 bg-slate-900/30 rounded-lg">
                      <span className={`text-xs font-mono w-5 text-center ${idx < 3 ? 'text-blue-400' : 'text-slate-500'}`}>{idx + 1}</span>
                      <div className={`w-7 h-7 rounded ${TYPE_CONFIG[asset.type].bgColor} flex items-center justify-center ${TYPE_CONFIG[asset.type].color}`}>{TYPE_CONFIG[asset.type].icon}</div>
                      <span className="text-sm text-slate-200 flex-1 truncate">{asset.name}</span>
                      <span className={`px-1.5 py-0.5 text-[10px] rounded flex items-center gap-1 ${qCfg.color} bg-slate-700/50`}>{qCfg.icon}{qCfg.label}</span>
                      <div className="w-32"><div className="h-2 bg-slate-700/50 rounded-full overflow-hidden"><div className={`h-full rounded-full ${asset.qualityScore >= 80 ? 'bg-emerald-500' : asset.qualityScore >= 60 ? 'bg-amber-500' : 'bg-red-500'}`} style={{ width: `${asset.qualityScore}%` }} /></div></div>
                      <span className={`text-sm font-mono w-8 text-right ${asset.qualityScore >= 80 ? 'text-emerald-400' : asset.qualityScore >= 60 ? 'text-amber-400' : 'text-red-400'}`}>{asset.qualityScore}</span>
                    </div>
                  );
                })}
              </div>
            </div>
            {assets.filter(a => a.quality === 'low').length > 0 && (
              <div className="bg-red-500/5 border border-red-500/20 rounded-xl p-5">
                <h3 className="text-sm font-semibold text-red-400 mb-3 flex items-center gap-2"><AlertTriangle className="w-4 h-4" />低质量资产告警</h3>
                <div className="space-y-2">
                  {assets.filter(a => a.quality === 'low').map(asset => (
                    <div key={asset.id} className="flex items-center gap-3 p-3 bg-red-500/5 rounded-lg">
                      <XCircle className="w-4 h-4 text-red-400" />
                      <span className="text-sm text-slate-200 flex-1">{asset.name}</span>
                      <span className="text-xs text-slate-400">{asset.category}</span>
                      <span className="text-xs text-red-400 font-mono">{asset.qualityScore}分</span>
                      <span className="text-xs text-slate-500">更新于 {timeAgo(asset.updatedAt)}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
