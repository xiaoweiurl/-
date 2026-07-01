'use client';

import { useEffect, useState, useRef } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Database, Brain, Image, FileText, BarChart3, TrendingUp,
  Activity, Cpu, HardDrive, Users, Zap, Search,
  ArrowUpRight, ArrowDownRight, Clock, RefreshCw,
  Package, ShoppingCart, Factory, DollarSign,
  Layers, Globe, Sparkles, ChevronRight,
} from 'lucide-react';

// ============================================================
// 数据驾驶舱 - AI数据中台核心页面
// ============================================================

// 颜色常量
const COLORS = {
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
        bg-gradient-to-br from-slate-800/80 to-slate-900/80
        border border-blue-500/10
        hover:border-blue-500/30 hover:shadow-[0_0_20px_rgba(59,130,246,0.1)]
        transition-all duration-300
      `}>
        {/* 顶部扫描线 */}
        <div className="absolute top-0 left-0 right-0 h-px bg-gradient-to-r from-transparent via-blue-500/40 to-transparent" />

        <div className="flex items-start justify-between">
          <div className="flex-1">
            <p className="text-slate-400 text-xs font-medium tracking-wider uppercase mb-2">{title}</p>
            <div className="flex items-baseline gap-1.5">
              <span className="text-2xl font-bold text-white font-mono tracking-tight">{value}</span>
              {unit && <span className="text-slate-400 text-sm">{unit}</span>}
            </div>
            {trend && (
              <div className={`flex items-center gap-1 mt-2 text-xs font-medium ${trendUp !== false ? 'text-emerald-400' : 'text-red-400'}`}>
                {trendUp !== false ? <ArrowUpRight className="w-3 h-3" /> : <ArrowDownRight className="w-3 h-3" />}
                {trend}
              </div>
            )}
          </div>
          <div className={`
            w-10 h-10 rounded-lg flex items-center justify-center
            bg-gradient-to-br ${color} shadow-lg
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
        bg-gradient-to-br from-slate-800/80 to-slate-900/80
        border border-blue-500/10
        hover:border-blue-500/20
        transition-all duration-300
        ${className}
      `}
    >
      {/* 顶部扫描线 */}
      <div className="absolute top-0 left-0 right-0 h-px bg-gradient-to-r from-transparent via-blue-500/30 to-transparent" />

      <div className="px-5 py-4 border-b border-slate-700/50 flex items-center gap-2">
        <Icon className="w-4 h-4 text-blue-400" />
        <h3 className="text-sm font-semibold text-slate-200">{title}</h3>
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
    blue: 'from-blue-500 to-cyan-400',
    green: 'from-emerald-500 to-green-400',
    yellow: 'from-yellow-500 to-amber-400',
    red: 'from-red-500 to-rose-400',
    purple: 'from-purple-500 to-violet-400',
  };
  return (
    <div className="mb-3 last:mb-0">
      <div className="flex justify-between text-xs mb-1">
        <span className="text-slate-400">{label}</span>
        <span className="text-slate-300 font-mono">{pct}%</span>
      </div>
      <div className="h-1.5 bg-slate-700/50 rounded-full overflow-hidden">
        <motion.div
          initial={{ width: 0 }}
          animate={{ width: `${pct}%` }}
          transition={{ duration: 1, delay: 0.3 }}
          className={`h-full rounded-full bg-gradient-to-r ${colorMap[color] || colorMap.blue}`}
        />
      </div>
    </div>
  );
}

// ============================================================
// 实时脉冲指示器
// ============================================================
function PulseDot({ color = 'bg-emerald-400' }: { color?: string }) {
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
function ActivityItem({ icon: Icon, text, time, color = 'text-blue-400' }: {
  icon: React.ElementType; text: string; time: string; color?: string;
}) {
  return (
    <div className="flex items-center gap-3 py-2.5 border-b border-slate-700/30 last:border-0">
      <div className={`w-7 h-7 rounded-lg bg-slate-700/50 flex items-center justify-center flex-shrink-0`}>
        <Icon className={`w-3.5 h-3.5 ${color}`} />
      </div>
      <span className="text-sm text-slate-300 flex-1 truncate">{text}</span>
      <span className="text-xs text-slate-500 flex-shrink-0">{time}</span>
    </div>
  );
}

// ============================================================
// 主页面
// ============================================================
export default function DashboardPage() {
  const [stats, setStats] = useState<any>(null);
  const [currentTime, setCurrentTime] = useState(new Date());
  const [supplyChainStats, setSupplyChainStats] = useState({
    totalProducts: 156,
    pendingQuotes: 23,
    activeSuppliers: 48,
    monthlyOrders: 89,
  });
  const [aiStats, setAiStats] = useState({
    totalCalls: 12847,
    todayCalls: 342,
    successRate: 98.7,
    avgResponseTime: 1.2,
  });

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
          setStats(data);
        }
      } catch {
        // 使用模拟数据
        setStats({
          overview: {
            totalImages: 2847,
            totalSize: 15600000000,
            totalAlbums: 34,
            totalTags: 128,
            favoritesCount: 456,
            trashCount: 23,
            recentUploads7d: 87,
            recentUploads30d: 342,
          },
          uploadTrend: Array.from({ length: 30 }, (_, i) => ({
            date: new Date(Date.now() - (29 - i) * 86400000).toISOString().slice(0, 10),
            count: Math.floor(Math.random() * 20) + 5,
          })),
          albumDistribution: [
            { name: '产品图片', count: 1200, percentage: 42 },
            { name: '设计素材', count: 650, percentage: 23 },
            { name: '营销素材', count: 480, percentage: 17 },
            { name: '工厂资料', count: 320, percentage: 11 },
            { name: '其他', count: 197, percentage: 7 },
          ],
        });
      }
    };
    fetchStats();
  }, []);

  const timeStr = currentTime.toLocaleTimeString('zh-CN', { hour12: false });
  const dateStr = currentTime.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' });

  return (
    <div className="min-h-screen bg-[#0a0e1a] text-slate-200">
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
            <h1 className="text-2xl font-bold text-white flex items-center gap-3">
              <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-blue-500 to-cyan-400 flex items-center justify-center">
                <BarChart3 className="w-4.5 h-4.5 text-white" />
              </div>
              数据驾驶舱
              <span className="text-xs font-normal text-slate-500 bg-slate-800 px-2 py-0.5 rounded-full border border-slate-700">
                LIVE
              </span>
            </h1>
            <p className="text-slate-500 text-sm mt-1">盈云产品智能中台 · 实时数据监控</p>
          </div>
          <div className="flex items-center gap-6">
            <div className="text-right">
              <div className="text-2xl font-mono text-blue-400 tracking-widest">{timeStr}</div>
              <div className="text-xs text-slate-500">{dateStr}</div>
            </div>
            <div className="flex items-center gap-2 text-xs text-emerald-400">
              <PulseDot />
              <span>系统运行正常</span>
            </div>
          </div>
        </motion.div>

        {/* ========== 核心指标行 ========== */}
        <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-4">
          <MetricCard title="知识总量" value={stats ? formatNum(stats.overview.totalImages) : '2.8K'} unit="条"
            icon={Database} color="from-blue-600 to-blue-400" trend="+12% 本周" trendUp delay={0} />
          <MetricCard title="存储空间" value={stats ? formatSize(stats.overview.totalSize) : '14.5'} unit="GB"
            icon={HardDrive} color="from-cyan-600 to-cyan-400" trend="使用率 68%" delay={0.05} />
          <MetricCard title="分类目录" value={stats ? stats.overview.totalAlbums : 34} unit="个"
            icon={Layers} color="from-emerald-600 to-emerald-400" trend="+3 本月" trendUp delay={0.1} />
          <MetricCard title="AI调用" value={formatNum(aiStats.totalCalls)} unit="次"
            icon={Cpu} color="from-purple-600 to-purple-400" trend={`今日 ${aiStats.todayCalls}`} trendUp delay={0.15} />
          <MetricCard title="产品数量" value={supplyChainStats.totalProducts} unit="款"
            icon={Package} color="from-yellow-600 to-yellow-400" trend="活跃供应商 48" delay={0.2} />
          <MetricCard title="待处理报价" value={supplyChainStats.pendingQuotes} unit="条"
            icon={DollarSign} color="from-orange-600 to-orange-400" trend="需及时处理" trendUp={false} delay={0.25} />
        </div>

        {/* ========== 第二行：AI能力 + 供应链 + 存储分析 ========== */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">

          {/* AI能力监控 */}
          <DataPanel title="AI能力监控" icon={Brain} delay={0.2}>
            <div className="space-y-4">
              <div className="grid grid-cols-2 gap-3">
                <div className="bg-slate-700/30 rounded-lg p-3 text-center">
                  <div className="text-lg font-bold text-blue-400 font-mono">{aiStats.successRate}%</div>
                  <div className="text-xs text-slate-500 mt-1">调用成功率</div>
                </div>
                <div className="bg-slate-700/30 rounded-lg p-3 text-center">
                  <div className="text-lg font-bold text-cyan-400 font-mono">{aiStats.avgResponseTime}s</div>
                  <div className="text-xs text-slate-500 mt-1">平均响应</div>
                </div>
              </div>
              <div className="space-y-2">
                <ProgressBar label="DeepSeek 对话" value={8420} max={10000} color="blue" />
                <ProgressBar label="AI 图片识别" value={2340} max={10000} color="purple" />
                <ProgressBar label="AI 图片生成" value={1280} max={5000} color="cyan" />
                <ProgressBar label="向量化处理" value={807} max={5000} color="green" />
              </div>
              <div className="flex items-center gap-2 text-xs text-slate-500 pt-1">
                <Activity className="w-3 h-3" />
                <span>最近1小时调用 <span className="text-blue-400 font-mono">47</span> 次</span>
              </div>
            </div>
          </DataPanel>

          {/* 供应链概览 */}
          <DataPanel title="供应链概览" icon={Factory} delay={0.25}>
            <div className="space-y-4">
              <div className="grid grid-cols-2 gap-3">
                <div className="bg-slate-700/30 rounded-lg p-3 text-center">
                  <div className="text-lg font-bold text-yellow-400 font-mono">{supplyChainStats.activeSuppliers}</div>
                  <div className="text-xs text-slate-500 mt-1">活跃供应商</div>
                </div>
                <div className="bg-slate-700/30 rounded-lg p-3 text-center">
                  <div className="text-lg font-bold text-emerald-400 font-mono">{supplyChainStats.monthlyOrders}</div>
                  <div className="text-xs text-slate-500 mt-1">本月订单</div>
                </div>
              </div>
              <div className="space-y-2">
                <ProgressBar label="原料采购完成率" value={78} max={100} color="green" />
                <ProgressBar label="生产计划执行率" value={65} max={100} color="yellow" />
                <ProgressBar label="辅料采购完成率" value={92} max={100} color="blue" />
                <ProgressBar label="成本核算覆盖率" value={45} max={100} color="purple" />
              </div>
              <div className="flex items-center gap-2 text-xs text-slate-500 pt-1">
                <ShoppingCart className="w-3 h-3" />
                <span>待处理采购单 <span className="text-yellow-400 font-mono">7</span> 条</span>
              </div>
            </div>
          </DataPanel>

          {/* 存储与资源 */}
          <DataPanel title="存储与资源" icon={HardDrive} delay={0.3}>
            <div className="space-y-4">
              <div className="bg-slate-700/30 rounded-lg p-4">
                <div className="flex justify-between text-xs mb-2">
                  <span className="text-slate-400">存储使用</span>
                  <span className="text-blue-400 font-mono">14.5 GB / 20 GB</span>
                </div>
                <div className="h-2 bg-slate-700/50 rounded-full overflow-hidden">
                  <motion.div
                    initial={{ width: 0 }}
                    animate={{ width: '72.5%' }}
                    transition={{ duration: 1.5, delay: 0.5 }}
                    className="h-full rounded-full bg-gradient-to-r from-blue-500 to-cyan-400"
                  />
                </div>
              </div>
              <div className="space-y-2">
                {stats?.albumDistribution?.slice(0, 4).map((item: any, i: number) => (
                  <div key={i} className="flex items-center gap-3">
                    <div className={`w-2 h-2 rounded-full bg-gradient-to-r ${
                      ['from-blue-500 to-blue-400', 'from-emerald-500 to-emerald-400',
                       'from-yellow-500 to-yellow-400', 'from-purple-500 to-purple-400'][i]
                    }`} />
                    <span className="text-xs text-slate-400 flex-1">{item.name}</span>
                    <span className="text-xs text-slate-300 font-mono">{item.count}</span>
                    <span className="text-xs text-slate-500 font-mono w-10 text-right">{item.percentage}%</span>
                  </div>
                ))}
              </div>
              <div className="flex items-center gap-2 text-xs text-slate-500 pt-1">
                <Database className="w-3 h-3" />
                <span>分类目录 <span className="text-emerald-400 font-mono">34</span> 个</span>
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
                {stats?.uploadTrend?.map((item: any, i: number) => {
                  const maxCount = Math.max(...(stats?.uploadTrend?.map((d: any) => d.count) || [1]));
                  const height = maxCount > 0 ? (item.count / maxCount) * 100 : 0;
                  return (
                    <motion.div
                      key={i}
                      initial={{ height: 0 }}
                      animate={{ height: `${height}%` }}
                      transition={{ duration: 0.5, delay: i * 0.02 }}
                      className="flex-1 bg-gradient-to-t from-blue-500/60 to-cyan-400/40 rounded-t-sm hover:from-blue-400 hover:to-cyan-300 transition-colors group relative cursor-pointer min-w-0"
                    >
                      <div className="absolute -top-8 left-1/2 -translate-x-1/2 bg-slate-700 text-xs text-white px-2 py-1 rounded opacity-0 group-hover:opacity-100 transition-opacity whitespace-nowrap pointer-events-none z-10">
                        {item.count}条 · {item.date.slice(5)}
                      </div>
                    </motion.div>
                  );
                }) || Array.from({ length: 30 }, (_, i) => (
                  <motion.div
                    key={i}
                    initial={{ height: 0 }}
                    animate={{ height: `${Math.random() * 80 + 20}%` }}
                    transition={{ duration: 0.5, delay: i * 0.02 }}
                    className="flex-1 bg-gradient-to-t from-blue-500/60 to-cyan-400/40 rounded-t-sm min-w-0"
                  />
                ))}
              </div>
              <div className="flex justify-between text-xs text-slate-500 pt-1">
                <span>30天前</span>
                <span>今天</span>
              </div>
              <div className="flex items-center gap-4 text-xs">
                <div className="flex items-center gap-1.5">
                  <div className="w-2 h-2 rounded-full bg-blue-500" />
                  <span className="text-slate-400">日均录入 <span className="text-blue-400 font-mono">{stats ? Math.round(stats.overview.recentUploads30d / 30) : 11}</span> 条</span>
                </div>
                <div className="flex items-center gap-1.5">
                  <ArrowUpRight className="w-3 h-3 text-emerald-400" />
                  <span className="text-slate-400">环比增长 <span className="text-emerald-400 font-mono">+12%</span></span>
                </div>
              </div>
            </div>
          </DataPanel>

          {/* 系统活动日志 */}
          <DataPanel title="系统活动" icon={Activity} delay={0.35}>
            <div className="space-y-1">
              <ActivityItem icon={Zap} text="AI对话完成 - 产品报价查询" time="2分钟前" color="text-blue-400" />
              <ActivityItem icon={Image} text="批量上传 12 张产品图片" time="15分钟前" color="text-emerald-400" />
              <ActivityItem icon={Brain} text="知识库向量化处理完成" time="28分钟前" color="text-purple-400" />
              <ActivityItem icon={Search} text="记忆库语义搜索 - 面料知识" time="45分钟前" color="text-cyan-400" />
              <ActivityItem icon={Package} text="新增供应商报价 - 涤纶DTY" time="1小时前" color="text-yellow-400" />
              <ActivityItem icon={Users} text="用户 admin 更新了系统设置" time="2小时前" color="text-slate-400" />
              <ActivityItem icon={FileText} text="知识库文档分类整理" time="3小时前" color="text-orange-400" />
              <ActivityItem icon={Globe} text="联网搜索 - 2025春夏季面料趋势" time="4小时前" color="text-pink-400" />
            </div>
            <div className="mt-3 flex items-center gap-1 text-xs text-blue-400 cursor-pointer hover:text-blue-300 transition-colors">
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
            { label: 'AI对话', icon: Brain, href: '/chat', color: 'from-blue-500/20 to-blue-600/20 hover:from-blue-500/30 hover:to-blue-600/30' },
            { label: '知识库', icon: Database, href: '/knowledge', color: 'from-emerald-500/20 to-emerald-600/20 hover:from-emerald-500/30 hover:to-emerald-600/30' },
            { label: 'AI生图', icon: Sparkles, href: '/ai-image', color: 'from-purple-500/20 to-purple-600/20 hover:from-purple-500/30 hover:to-purple-600/30' },
            { label: '记忆库', icon: Brain, href: '/memory', color: 'from-cyan-500/20 to-cyan-600/20 hover:from-cyan-500/30 hover:to-cyan-600/30' },
            { label: '供应链', icon: Factory, href: '/supply-chain', color: 'from-yellow-500/20 to-yellow-600/20 hover:from-yellow-500/30 hover:to-yellow-600/30' },
            { label: '文档中心', icon: FileText, href: '/documents', color: 'from-orange-500/20 to-orange-600/20 hover:from-orange-500/30 hover:to-orange-600/30' },
          ].map((item) => (
            <a
              key={item.label}
              href={item.href}
              className={`
                flex flex-col items-center gap-2 p-4 rounded-xl
                bg-gradient-to-br ${item.color}
                border border-slate-700/30
                hover:border-blue-500/20
                transition-all duration-200
                group cursor-pointer
              `}
            >
              <item.icon className="w-6 h-6 text-slate-300 group-hover:text-white transition-colors" />
              <span className="text-xs text-slate-400 group-hover:text-slate-200 transition-colors">{item.label}</span>
            </a>
          ))}
        </motion.div>
      </div>
    </div>
  );
}
