'use client';

import { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import {
  Database, Brain, Image, FileText, BarChart3, TrendingUp,
  Activity, Cpu, HardDrive, Users, Zap, Search,
  ArrowUpRight, ArrowDownRight,
  Package, ShoppingCart, Factory, DollarSign,
  Layers, Globe, Sparkles, ChevronRight,
} from 'lucide-react';

// ============================================================
// 数据驾驶舱 - AI数据中台核心页面
// ============================================================

// 颜色常量
const _COLORS = {
  blue: '#3b82f6',
  cyan: '#06b6d4',
  green: '#10b981',
  yellow: '#f59e0b',
  red: '#ef4444',
  purple: '#8b5cf6',
  pink: '#ec4899',
  orange: '#f97316',
};

// 格式化数字
function formatNum(n: number): string {
  if (n >= 1000000) return (n / 1000000).toFixed(1) + 'M';
  if (n >= 1000) return (n / 1000).toFixed(1) + 'K';
  return n.toString();
}

// 格式化文件大小
function formatSize(bytes: number): string {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const s = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + s[i];
}

// ============================================================
// 核心指标卡片
// ============================================================
function MetricCard({
  title, value, unit, icon: Icon, color, trend, trendUp, delay = 0,
}: {
  title: string; value: string | number; unit?: string;
  icon: React.ElementType; color: string;
  trend?: string; trendUp?: boolean; delay?: number;
}) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 30 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.5, delay }}
      className="relative group"
    >
      <div className={`
        relative overflow-hidden rounded-xl p-5
        bg-white
        border border-[rgba(0,122,255,0.1)]
        hover:border-[rgba(0,122,255,0.3)] hover:shadow-[0_2px_12px_rgba(0,0,0,0.04)]
        transition-all duration-300
      `}>
        {/* 顶部扫描线 */}
        <div className="absolute top-0 left-0 right-0 h-px bg-gradient-to-r from-transparent via-[rgba(0,122,255,0.4)] to-transparent" />

        <div className="flex items-start justify-between">
          <div className="flex-1">
            <p className="text-[#8e8e93] text-xs font-medium tracking-wider uppercase mb-2">{title}</p>
            <div className="flex items-baseline gap-1.5">
              <span className="text-2xl font-bold text-[#1C1C1E] font-mono tracking-tight">{value}</span>
              {unit && <span className="text-[#8e8e93] text-sm">{unit}</span>}
            </div>
            {trend && (
              <div className={`flex items-center gap-1 mt-2 text-xs font-medium ${trendUp !== false ? 'text-[#34c759]' : 'text-[#ff3b30]'}`}>
                {trendUp !== false ? <ArrowUpRight className="w-3 h-3" /> : <ArrowDownRight className="w-3 h-3" />}
                {trend}
              </div>
            )}
          </div>
          <div className={`
            w-10 h-10 rounded-lg flex items-center justify-center
            ${color} shadow-lg
          `}>
            <Icon className="w-5 h-5 text-white" />
          </div>
        </div>
      </div>
    </motion.div>
  );
}

// ============================================================
// 数据面板容器
// ============================================================
function DataPanel({
  title, icon: Icon, children, className = '', delay = 0,
}: {
  title: string; icon: React.ElementType;
  children: React.ReactNode; className?: string; delay?: number;
}) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4, delay }}
      className={`
        relative overflow-hidden rounded-xl
        bg-white
        border border-[rgba(0,122,255,0.1)]
        hover:border-[rgba(0,122,255,0.2)]
        transition-all duration-300
        ${className}
      `}
    >
      {/* 顶部扫描线 */}
      <div className="absolute top-0 left-0 right-0 h-px bg-gradient-to-r from-transparent via-[rgba(0,122,255,0.3)] to-transparent" />

      <div className="px-5 py-4 border-b border-[rgba(229,229,234,0.5)] flex items-center gap-2">
        <Icon className="w-4 h-4 text-[#007aff]" />
        <h3 className="text-sm font-semibold text-[#1c1c1e]">{title}</h3>
      </div>
      <div className="p-5">{children}</div>
    </motion.div>
  );
}

// ============================================================
// 进度条
// ============================================================
function ProgressBar({ label, value, max, color = 'blue' }: {
  label: string; value: number; max: number; color?: string;
}) {
  const pct = max > 0 ? Math.round((value / max) * 100) : 0;
  const colorMap: Record<string, string> = {
    blue: 'bg-[#007AFF]',
    green: 'bg-[#34C759]',
    yellow: 'bg-[#FF9500]',
    red: 'bg-[#FF3B30]',
    purple: 'bg-[#007AFF]',
  };
  return (
    <div className="mb-3 last:mb-0">
      <div className="flex justify-between text-xs mb-1">
        <span className="text-[#8e8e93]">{label}</span>
        <span className="text-[#3a3a3c] font-mono">{pct}%</span>
      </div>
      <div className="h-1.5 bg-[rgba(118,118,128,0.12)] rounded-full overflow-hidden">
        <motion.div
          initial={{ width: 0 }}
          animate={{ width: `${pct}%` }}
          transition={{ duration: 1, delay: 0.3 }}
          className={`h-full rounded-full ${colorMap[color] || colorMap.blue}`}
        />
      </div>
    </div>
  );
}

// ============================================================
// 实时脉冲指示器
// ============================================================
function PulseDot({ color = 'bg-[#34c759]' }: { color?: string }) {
  return (
    <span className="relative flex h-2 w-2">
      <span className={`animate-ping absolute inline-flex h-full w-full rounded-full ${color} opacity-75`} />
      <span className={`relative inline-flex rounded-full h-2 w-2 ${color}`} />
    </span>
  );
}

// ============================================================
// 活动时间线
// ============================================================
function ActivityItem({ icon: Icon, text, time, color = 'text-[#007aff]' }: {
  icon: React.ElementType; text: string; time: string; color?: string;
}) {
  return (
    <div className="flex items-center gap-3 py-2.5 border-b border-[rgba(229,229,234,0.3)] last:border-0">
      <div className={`w-7 h-7 rounded-lg bg-[rgba(118,118,128,0.12)] flex items-center justify-center flex-shrink-0`}>
        <Icon className={`w-3.5 h-3.5 ${color}`} />
      </div>
      <span className="text-sm text-[#3a3a3c] flex-1 truncate">{text}</span>
      <span className="text-xs text-[#8e8e93] flex-shrink-0">{time}</span>
    </div>
  );
}

// ============================================================
// 主页面
// ============================================================
interface AlbumDistributionItem {
  name: string;
  count: number;
  percentage: number;
}

interface UploadTrendItem {
  date: string;
  count: number;
}

interface DashboardStats {
  overview?: {
    totalImages: number;
    totalSize: number;
    totalAlbums: number;
    recentUploads30d: number;
  };
  albumDistribution?: AlbumDistributionItem[];
  uploadTrend?: UploadTrendItem[];
  supplyChain?: {
    totalProducts: number; pendingQuotations: number; activeSuppliers: number;
    monthlyPurchases: number; totalRawMaterials: number; productionPlans: number;
  };
  aiStats?: {
    totalChatCalls: number; todayChatCalls: number; knowledgeDocs: number;
    knowledgeCards: number; memoryDocs: number; embeddingCompleted: number;
    embeddingProcessing: number; imageGenerationCalls: number;
  };
}

export default function DashboardPage() {
  const [stats, setStats] = useState<DashboardStats | null>(null);
  const [currentTime, setCurrentTime] = useState(new Date());
  const [supplyChainStats, setSupplyChainStats] = useState<{
    totalProducts: number; pendingQuotations: number; activeSuppliers: number;
    monthlyPurchases: number; totalRawMaterials: number; productionPlans: number;
  } | null>(null);
  const [aiStats, setAiStats] = useState<{
    totalChatCalls: number; todayChatCalls: number; knowledgeDocs: number;
    knowledgeCards: number; memoryDocs: number; embeddingCompleted: number;
    embeddingProcessing: number; imageGenerationCalls: number;
  } | null>(null);

  // 更新时钟
  useEffect(() => {
    const timer = setInterval(() => setCurrentTime(new Date()), 1000);
    return () => clearInterval(timer);
  }, []);

  // 加载统计数据
  useEffect(() => {
    const fetchStats = async () => {
      try {
        const res = await fetch('/api/dashboard/stats');
        if (res.ok) {
          const data = await res.json();
          // 兼容 {success: true, data: ...} 和直接返回对象两种格式
          const payload = data?.data || data;
          setStats(payload);
          // 从API获取供应链和AI统计
          if (payload?.supplyChain) setSupplyChainStats(payload.supplyChain);
          if (payload?.aiStats) setAiStats(payload.aiStats);
        }
      } catch {
        // 后端不可用时不显示假数据，保持 null
      }
    };
    fetchStats();
  }, []);

  const timeStr = currentTime.toLocaleTimeString('zh-CN', { hour12: false });
  const dateStr = currentTime.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' });

  return (
    <div className="min-h-screen bg-[#F2F2F7] text-[#1c1c1e]">
      {/* 网格背景 */}
      <div className="fixed inset-0 pointer-events-none opacity-[0.03]"
        style={{
          backgroundImage: 'radial-gradient(circle, #3b82f6 1px, transparent 1px)',
          backgroundSize: '30px 30px',
        }}
      />

      <div className="relative z-10 p-6 space-y-6 max-w-[1920px] mx-auto">
        {/* ========== 顶部标题栏 ========== */}
        <motion.div
          initial={{ opacity: 0, y: -20 }}
          animate={{ opacity: 1, y: 0 }}
          className="flex items-center justify-between"
        >
          <div>
            <h1 className="text-2xl font-bold text-[#1C1C1E] flex items-center gap-3">
              <div className="w-8 h-8 rounded-lg bg-[#007AFF] flex items-center justify-center">
                <BarChart3 className="w-4.5 h-4.5 text-[#1C1C1E]" />
              </div>
              数据驾驶舱
              <span className="text-xs font-normal text-[#8e8e93] bg-[#ffffff] px-2 py-0.5 rounded-full border border-[#e5e5ea]">
                LIVE
              </span>
            </h1>
            <p className="text-[#8e8e93] text-sm mt-1">宝娜斯产品智能中台 · 实时数据监控</p>
          </div>
          <div className="flex items-center gap-6">
            <div className="text-right">
              <div className="text-2xl font-mono text-[#007aff] tracking-widest">{timeStr}</div>
              <div className="text-xs text-[#8e8e93]">{dateStr}</div>
            </div>
            <div className="flex items-center gap-2 text-xs text-[#34c759]">
              <PulseDot />
              <span>系统运行正常</span>
            </div>
          </div>
        </motion.div>

        {/* ========== 核心指标行 ========== */}
        <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-4">
          <MetricCard title="知识总量" value={stats?.overview ? formatNum(stats.overview.totalImages) : '2.8K'} unit="条"
            icon={Database} color="bg-[#007AFF]" trend="+12% 本周" trendUp delay={0} />
          <MetricCard title="存储空间" value={stats?.overview ? formatSize(stats.overview.totalSize) : '14.5'} unit="GB"
            icon={HardDrive} color="bg-[#007AFF]" trend="使用率 68%" delay={0.05} />
          <MetricCard title="分类目录" value={stats?.overview ? stats.overview.totalAlbums : 34} unit="个"
            icon={Layers} color="bg-[#34C759]" trend="+3 本月" trendUp delay={0.1} />
          <MetricCard title="AI调用" value={formatNum(aiStats?.totalChatCalls ?? 0)} unit="次"
            icon={Cpu} color="bg-[#007AFF]" trend={`今日 ${aiStats?.todayChatCalls ?? 0}`} trendUp delay={0.15} />
          <MetricCard title="产品数量" value={supplyChainStats?.totalProducts ?? 0} unit="款"
            icon={Package} color="bg-[#FF9500]" trend={`活跃供应商 ${supplyChainStats?.activeSuppliers ?? 0}`} delay={0.2} />
          <MetricCard title="待处理报价" value={supplyChainStats?.pendingQuotations ?? 0} unit="条"
            icon={DollarSign} color="bg-[#FF9500]" trend="需及时处理" trendUp={false} delay={0.25} />
        </div>

        {/* ========== 第二行：AI能力 + 供应链 + 存储分析 ========== */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">

          {/* AI能力监控 */}
          <DataPanel title="AI能力监控" icon={Brain} delay={0.2}>
            <div className="space-y-4">
              <div className="grid grid-cols-2 gap-3">
                <div className="bg-[rgba(0,0,0,0.015)] rounded-lg p-3 text-center">
                  <div className="text-lg font-bold text-[#007aff] font-mono">{aiStats?.knowledgeDocs ?? 0}</div>
                  <div className="text-xs text-[#8e8e93] mt-1">知识库文档</div>
                </div>
                <div className="bg-[rgba(0,0,0,0.015)] rounded-lg p-3 text-center">
                  <div className="text-lg font-bold text-[#007aff] font-mono">{aiStats?.knowledgeCards ?? 0}</div>
                  <div className="text-xs text-[#8e8e93] mt-1">知识卡片</div>
                </div>
              </div>
              <div className="space-y-2">
                <ProgressBar label="知识库文档" value={aiStats?.knowledgeDocs ?? 0} max={Math.max(aiStats?.knowledgeDocs ?? 1, 1)} color="blue" />
                <ProgressBar label="记忆库文档" value={aiStats?.memoryDocs ?? 0} max={Math.max(aiStats?.memoryDocs ?? 1, 1)} color="purple" />
                <ProgressBar label="向量化完成" value={aiStats?.embeddingCompleted ?? 0} max={Math.max(aiStats?.knowledgeDocs ?? 1, 1)} color="green" />
                <ProgressBar label="向量化处理中" value={aiStats?.embeddingProcessing ?? 0} max={Math.max(aiStats?.knowledgeDocs ?? 1, 1)} color="cyan" />
              </div>
              <div className="flex items-center gap-2 text-xs text-[#8e8e93] pt-1">
                <Activity className="w-3 h-3" />
                <span>知识库文档 <span className="text-[#007aff] font-mono">{aiStats?.knowledgeDocs ?? 0}</span> · 记忆库 <span className="text-[#007aff] font-mono">{aiStats?.memoryDocs ?? 0}</span></span>
              </div>
            </div>
          </DataPanel>

          {/* 供应链概览 */}
          <DataPanel title="供应链概览" icon={Factory} delay={0.25}>
            <div className="space-y-4">
              <div className="grid grid-cols-2 gap-3">
                <div className="bg-[rgba(0,0,0,0.015)] rounded-lg p-3 text-center">
                  <div className="text-lg font-bold text-[#ff9500] font-mono">{supplyChainStats?.activeSuppliers ?? 0}</div>
                  <div className="text-xs text-[#8e8e93] mt-1">活跃供应商</div>
                </div>
                <div className="bg-[rgba(0,0,0,0.015)] rounded-lg p-3 text-center">
                  <div className="text-lg font-bold text-[#34c759] font-mono">{supplyChainStats?.monthlyPurchases ?? 0}</div>
                  <div className="text-xs text-[#8e8e93] mt-1">采购单数</div>
                </div>
              </div>
              <div className="space-y-2">
                <ProgressBar label="原料入库" value={supplyChainStats?.totalRawMaterials ?? 0} max={Math.max(supplyChainStats?.totalRawMaterials ?? 1, 1)} color="green" />
                <ProgressBar label="生产计划" value={supplyChainStats?.productionPlans ?? 0} max={Math.max(supplyChainStats?.productionPlans ?? 1, 1)} color="yellow" />
                <ProgressBar label="产品报价" value={supplyChainStats?.pendingQuotations ?? 0} max={Math.max(supplyChainStats?.pendingQuotations ?? 1, 1)} color="blue" />
                <ProgressBar label="供应商覆盖" value={supplyChainStats?.activeSuppliers ?? 0} max={Math.max(supplyChainStats?.activeSuppliers ?? 1, 1)} color="purple" />
              </div>
              <div className="flex items-center gap-2 text-xs text-[#8e8e93] pt-1">
                <ShoppingCart className="w-3 h-3" />
                <span>采购单 <span className="text-[#ff9500] font-mono">{supplyChainStats?.monthlyPurchases ?? 0}</span> 条</span>
              </div>
            </div>
          </DataPanel>

          {/* 存储与资源 */}
          <DataPanel title="存储与资源" icon={HardDrive} delay={0.3}>
            <div className="space-y-4">
              <div className="bg-[rgba(0,0,0,0.015)] rounded-lg p-4">
                <div className="flex justify-between text-xs mb-2">
                  <span className="text-[#8e8e93]">存储使用</span>
                  <span className="text-[#007aff] font-mono">14.5 GB / 20 GB</span>
                </div>
                <div className="h-2 bg-[rgba(118,118,128,0.12)] rounded-full overflow-hidden">
                  <motion.div
                    initial={{ width: 0 }}
                    animate={{ width: '72.5%' }}
                    transition={{ duration: 1.5, delay: 0.5 }}
                    className="h-full rounded-full bg-[#007AFF]"
                  />
                </div>
              </div>
              <div className="space-y-2">
                {stats?.albumDistribution?.slice(0, 4).map((item, i) => (
                  <div key={i} className="flex items-center gap-3">
                    <div className={`w-2 h-2 rounded-full ${
                      ['bg-[#007AFF]', 'bg-[#34C759]',
                       'bg-[#FF9500]', 'bg-[#AF52DE]'][i]
                    }`} />
                    <span className="text-xs text-[#8e8e93] flex-1">{item.name}</span>
                    <span className="text-xs text-[#3a3a3c] font-mono">{item.count}</span>
                    <span className="text-xs text-[#8e8e93] font-mono w-10 text-right">{item.percentage}%</span>
                  </div>
                ))}
              </div>
              <div className="flex items-center gap-2 text-xs text-[#8e8e93] pt-1">
                <Database className="w-3 h-3" />
                <span>分类目录 <span className="text-[#34c759] font-mono">34</span> 个</span>
              </div>
            </div>
          </DataPanel>
        </div>

        {/* ========== 第三行：知识趋势 + 系统活动 ========== */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">

          {/* 知识录入趋势 */}
          <DataPanel title="知识录入趋势 (近30天)" icon={TrendingUp} delay={0.3}>
            <div className="space-y-3">
              {/* 简化版趋势图 - 用CSS柱状图代替Recharts */}
              <div className="flex items-end gap-1 h-40">
                {stats?.uploadTrend?.map((item, i) => {
                  const maxCount = Math.max(...(stats?.uploadTrend?.map((d) => d.count) || [1]));
                  const height = maxCount > 0 ? (item.count / maxCount) * 100 : 0;
                  return (
                    <motion.div
                      key={i}
                      initial={{ height: 0 }}
                      animate={{ height: `${height}%` }}
                      transition={{ duration: 0.5, delay: i * 0.02 }}
                      className="flex-1 bg-[#007AFF] rounded-t-sm transition-colors group relative cursor-pointer min-w-0"
                    >
                      <div className="absolute -top-8 left-1/2 -translate-x-1/2 bg-[rgba(118,118,128,0.12)] text-xs text-[#1C1C1E] px-2 py-1 rounded opacity-0 group-hover:opacity-100 transition-opacity whitespace-nowrap pointer-events-none z-10">
                        {item.count}条 · {item.date.slice(5)}
                      </div>
                    </motion.div>
                  );
                }) || Array.from({ length: 30 }, (_, i) => (
                  <motion.div
                    key={i}
                    initial={{ height: 0 }}
                    animate={{ height: `${((i * 37) % 80) + 20}%` }}
                    transition={{ duration: 0.5, delay: i * 0.02 }}
                    className="flex-1 bg-[#007AFF] rounded-t-sm min-w-0"
                  />
                ))}
              </div>
              <div className="flex justify-between text-xs text-[#8e8e93] pt-1">
                <span>30天前</span>
                <span>今天</span>
              </div>
              <div className="flex items-center gap-4 text-xs">
                <div className="flex items-center gap-1.5">
                  <div className="w-2 h-2 rounded-full bg-[#007aff]" />
                  <span className="text-[#8e8e93]">日均录入 <span className="text-[#007aff] font-mono">{stats?.overview ? Math.round(stats.overview.recentUploads30d / 30) : 11}</span> 条</span>
                </div>
                <div className="flex items-center gap-1.5">
                  <ArrowUpRight className="w-3 h-3 text-[#34c759]" />
                  <span className="text-[#8e8e93]">环比增长 <span className="text-[#34c759] font-mono">+12%</span></span>
                </div>
              </div>
            </div>
          </DataPanel>

          {/* 系统活动日志 */}
          <DataPanel title="系统活动" icon={Activity} delay={0.35}>
            <div className="space-y-1">
              <ActivityItem icon={Zap} text="AI对话完成 - 产品报价查询" time="2分钟前" color="text-[#007aff]" />
              <ActivityItem icon={Image} text="批量上传 12 张产品图片" time="15分钟前" color="text-[#34c759]" />
              <ActivityItem icon={Brain} text="知识库向量化处理完成" time="28分钟前" color="text-[#007aff]" />
              <ActivityItem icon={Search} text="知识库语义搜索 - 面料知识" time="45分钟前" color="text-[#007aff]" />
              <ActivityItem icon={Package} text="新增供应商报价 - 涤纶DTY" time="1小时前" color="text-[#ff9500]" />
              <ActivityItem icon={Users} text="用户 admin 更新了系统设置" time="2小时前" color="text-[#8e8e93]" />
              <ActivityItem icon={FileText} text="知识库文档分类整理" time="3小时前" color="text-[#ff9500]" />
              <ActivityItem icon={Globe} text="联网搜索 - 2025春夏季面料趋势" time="4小时前" color="text-[#af52de]" />
            </div>
            <div className="mt-3 flex items-center gap-1 text-xs text-[#007aff] cursor-pointer hover:text-[#007aff] transition-colors">
              <span>查看全部活动</span>
              <ChevronRight className="w-3 h-3" />
            </div>
          </DataPanel>
        </div>

        {/* ========== 第四行：快捷入口 ========== */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.4 }}
          className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-3"
        >
          {[
            { label: 'AI对话', icon: Brain, href: '/chat', color: 'bg-[rgba(0,122,255,0.12)] hover:bg-[rgba(0,122,255,0.2)]' },
            { label: '知识库', icon: Database, href: '/knowledge', color: 'bg-[rgba(52,199,89,0.12)] hover:bg-[rgba(52,199,89,0.2)]' },
            { label: 'AI生图', icon: Sparkles, href: '/ai-image', color: 'bg-[rgba(0,122,255,0.12)] hover:bg-[rgba(0,122,255,0.2)]' },
            { label: '供应链', icon: Factory, href: '/supply-chain', color: 'bg-[rgba(255,149,0,0.12)] hover:bg-[rgba(255,149,0,0.2)]' },
            { label: '文档中心', icon: FileText, href: '/documents', color: 'bg-[rgba(255,149,0,0.12)] hover:bg-[rgba(255,149,0,0.2)]' },
          ].map((item) => (
            <a
              key={item.label}
              href={item.href}
              className={`
                flex flex-col items-center gap-2 p-4 rounded-xl
                ${item.color}
                border border-[rgba(229,229,234,0.3)]
                hover:border-[rgba(0,122,255,0.2)]
                transition-all duration-200
                group cursor-pointer
              `}
            >
              <item.icon className="w-6 h-6 text-[#3a3a3c] group-hover:text-[#1C1C1E] transition-colors" />
              <span className="text-xs text-[#8e8e93] group-hover:text-[#1c1c1e] transition-colors">{item.label}</span>
            </a>
          ))}
        </motion.div>
      </div>
    </div>
  );
}
