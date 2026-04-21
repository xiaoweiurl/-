'use client';

import React from 'react';
import { useRouter } from 'next/navigation';
import { motion } from 'framer-motion';
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  LineChart,
  Line,
  PieChart,
  Pie,
  Cell,
  AreaChart,
  Area,
} from 'recharts';
import {
  LayoutDashboard,
  Image,
  FolderOpen,
  Tag,
  Heart,
  Trash2,
  TrendingUp,
  Calendar,
  HardDrive,
  ChevronLeft,
  Loader2,
  Eye,
  Download,
  Star,
  Flame,
  Activity,
  Upload,
} from 'lucide-react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { toast } from 'sonner';
import { cn } from '@/lib/utils';

// 统计数据类型
interface TrendData {
  date: string;
  count: number;
  size: number;
}

interface DashboardStats {
  overview: {
    totalImages: number;
    totalSize: number;
    totalAlbums: number;
    totalTags: number;
    favoritesCount: number;
    trashCount: number;
    recentUploads7d: number;
    recentUploads30d: number;
  };
  uploadTrend: TrendData[];
  storageTrend: TrendData[];
  albumDistribution: Array<{
    name: string;
    count: number;
    percentage: number;
  }>;
  topTags: Array<{
    name: string;
    count: number;
  }>;
  fileTypeStats: Array<{
    type: string;
    count: number;
    size: number;
  }>;
}

// 热门资源类型
interface HotResource {
  id: string;
  title: string;
  thumbnailUrl: string;
  viewCount: number;
  downloadCount: number;
  favoriteCount: number;
  albumName: string;
}

// 热门相册类型
interface HotAlbum {
  id: string;
  name: string;
  imageCount: number;
  totalSize: number;
  coverUrl: string;
}

// 活跃度统计类型
interface ActivityStats {
  todayUploads: number;
  todayViews: number;
  todayDownloads: number;
  todayFavorites: number;
  growthRate: number;
}

// 格式化文件大小
function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
}

// 格式化数字
function formatNumber(num: number): string {
  if (num >= 1000000) {
    return (num / 1000000).toFixed(1) + 'M';
  }
  if (num >= 1000) {
    return (num / 1000).toFixed(1) + 'K';
  }
  return num.toString();
}

// 格式化日期
function formatDate(dateStr: string): string {
  const date = new Date(dateStr);
  return `${date.getMonth() + 1}/${date.getDate()}`;
}

// 饼图颜色
const PIE_COLORS = [
  '#8b5cf6', '#ec4899', '#3b82f6', '#10b981', '#f59e0b',
  '#ef4444', '#06b6d4', '#84cc16', '#f97316', '#6366f1',
];

// 统计卡片组件
function StatCard({
  title,
  value,
  description,
  icon: Icon,
  trend,
  trendUp,
  color,
}: {
  title: string;
  value: string | number;
  description?: string;
  icon: React.ElementType;
  trend?: string;
  trendUp?: boolean;
  color: string;
}) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.3 }}
    >
      <Card className="relative overflow-hidden">
        <div className={cn('absolute top-0 right-0 w-24 h-24 opacity-10 rounded-full -mr-8 -mt-8', color)} />
        <CardHeader className="flex flex-row items-center justify-between pb-2">
          <CardTitle className="text-sm font-medium text-slate-600">{title}</CardTitle>
          <div className={cn('p-2 rounded-lg', color.replace('bg-', 'bg-opacity-20 bg-'))}>
            <Icon className={cn('w-4 h-4', color.replace('bg-', 'text-'))} />
          </div>
        </CardHeader>
        <CardContent>
          <div className="text-2xl font-bold text-slate-900">{value}</div>
          {description && (
            <p className="text-xs text-slate-500 mt-1">{description}</p>
          )}
          {trend && (
            <div className="flex items-center gap-1 mt-2">
              <TrendingUp className={cn(
                'w-3 h-3',
                trendUp ? 'text-green-500' : 'text-red-500'
              )} />
              <span className={cn(
                'text-xs',
                trendUp ? 'text-green-500' : 'text-red-500'
              )}>
                {trend}
              </span>
            </div>
          )}
        </CardContent>
      </Card>
    </motion.div>
  );
}

export default function DashboardPage() {
  const router = useRouter();
  const [stats, setStats] = React.useState<DashboardStats | null>(null);
  const [hotResources, setHotResources] = React.useState<HotResource[]>([]);
  const [hotAlbums, setHotAlbums] = React.useState<HotAlbum[]>([]);
  const [activityStats, setActivityStats] = React.useState<ActivityStats | null>(null);
  const [isLoading, setIsLoading] = React.useState(true);
  const [period, setPeriod] = React.useState<'week' | 'month' | 'year'>('month');

  // 获取统计数据
  const fetchStats = React.useCallback(async () => {
    try {
      setIsLoading(true);
      
      // 并行获取所有统计数据
      const [statsRes, hotRes, albumsRes, activityRes] = await Promise.all([
        fetch(`/api/dashboard/stats?period=${period}`, { credentials: 'include' }),
        fetch('/api/dashboard/hot-resources?limit=10', { credentials: 'include' }),
        fetch('/api/dashboard/hot-albums?limit=5', { credentials: 'include' }),
        fetch('/api/dashboard/activity', { credentials: 'include' }),
      ]);

      const [statsData, hotData, albumsData, activityData] = await Promise.all([
        statsRes.json(),
        hotRes.json(),
        albumsRes.json(),
        activityRes.json(),
      ]);

      if (statsData.success) {
        setStats(statsData.data);
      }
      
      if (hotData.success) {
        setHotResources(hotData.data || []);
      }
      
      if (albumsData.success) {
        setHotAlbums(albumsData.data || []);
      }
      
      if (activityData.success) {
        setActivityStats(activityData.data);
      }
    } catch (error) {
      console.error('Failed to fetch stats:', error);
      toast.error('获取统计数据失败');
    } finally {
      setIsLoading(false);
    }
  }, [period]);

  React.useEffect(() => {
    fetchStats();
  }, [fetchStats]);

  // 加载中状态
  if (isLoading) {
    return (
      <div className="min-h-screen bg-slate-50 flex items-center justify-center">
        <div className="flex flex-col items-center gap-4">
          <Loader2 className="w-8 h-8 animate-spin text-violet-600" />
          <p className="text-slate-500">加载统计数据...</p>
        </div>
      </div>
    );
  }

  if (!stats) {
    return (
      <div className="min-h-screen bg-slate-50 flex items-center justify-center">
        <div className="text-center">
          <p className="text-slate-500 mb-4">无法加载统计数据</p>
          <Button onClick={fetchStats} variant="outline">
            重试
          </Button>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-50">
      {/* 顶部导航 */}
      <header className="bg-white border-b border-slate-200 sticky top-0 z-10">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex items-center justify-between h-16">
            <div className="flex items-center gap-4">
              <Button
                variant="ghost"
                size="sm"
                onClick={() => router.push('/')}
                className="text-slate-600 hover:text-slate-900"
              >
                <ChevronLeft className="w-4 h-4 mr-1" />
                返回
              </Button>
              <div className="flex items-center gap-2">
                <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-violet-500 to-purple-600 flex items-center justify-center">
                  <LayoutDashboard className="w-4 h-4 text-white" />
                </div>
                <h1 className="text-lg font-semibold text-slate-900">数据仪表盘</h1>
              </div>
            </div>
            <div className="flex items-center gap-3">
              <Tabs value={period} onValueChange={(v) => setPeriod(v as typeof period)}>
                <TabsList className="bg-slate-100">
                  <TabsTrigger value="week">近7天</TabsTrigger>
                  <TabsTrigger value="month">近30天</TabsTrigger>
                  <TabsTrigger value="year">近一年</TabsTrigger>
                </TabsList>
              </Tabs>
              <Button onClick={fetchStats} variant="outline" size="sm">
                刷新
              </Button>
            </div>
          </div>
        </div>
      </header>

      {/* 主内容 */}
      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {/* 概览统计卡片 */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
          <StatCard
            title="总图片数"
            value={stats.overview.totalImages.toLocaleString()}
            description={`${stats.overview.recentUploads7d} 张本周新增`}
            icon={Image}
            trend={`+${stats.overview.recentUploads7d} 本周`}
            trendUp={true}
            color="bg-violet-500"
          />
          <StatCard
            title="存储空间"
            value={formatFileSize(stats.overview.totalSize)}
            description="已使用存储"
            icon={HardDrive}
            color="bg-blue-500"
          />
          <StatCard
            title="相册数量"
            value={stats.overview.totalAlbums}
            description={`${stats.albumDistribution.length} 个活跃相册`}
            icon={FolderOpen}
            color="bg-green-500"
          />
          <StatCard
            title="标签总数"
            value={stats.overview.totalTags}
            description={`${stats.topTags.length} 个热门标签`}
            icon={Tag}
            color="bg-pink-500"
          />
        </div>

        {/* 第二行统计 */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-8">
          <StatCard
            title="收藏图片"
            value={stats.overview.favoritesCount}
            description={`${stats.overview.totalImages > 0 ? Math.round((stats.overview.favoritesCount / stats.overview.totalImages) * 100) : 0}% 的图片已收藏`}
            icon={Heart}
            color="bg-red-500"
          />
          <StatCard
            title="回收站"
            value={stats.overview.trashCount}
            description="待清理图片"
            icon={Trash2}
            color="bg-orange-500"
          />
          <StatCard
            title="本月上传"
            value={stats.overview.recentUploads30d}
            description="近30天新增"
            icon={Calendar}
            trend={`日均 ${Math.round(stats.overview.recentUploads30d / 30)} 张`}
            trendUp={true}
            color="bg-cyan-500"
          />
        </div>

        {/* 图表区域 */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {/* 上传趋势图 */}
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.3, delay: 0.1 }}
          >
            <Card className="h-[400px]">
              <CardHeader>
                <CardTitle className="text-base font-semibold flex items-center gap-2">
                  <TrendingUp className="w-4 h-4 text-violet-500" />
                  上传趋势
                </CardTitle>
                <CardDescription>每日图片上传数量统计</CardDescription>
              </CardHeader>
              <CardContent className="h-[300px]">
                <ResponsiveContainer width="100%" height="100%">
                  <AreaChart data={stats.uploadTrend}>
                    <defs>
                      <linearGradient id="colorCount" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="5%" stopColor="#8b5cf6" stopOpacity={0.3}/>
                        <stop offset="95%" stopColor="#8b5cf6" stopOpacity={0}/>
                      </linearGradient>
                    </defs>
                    <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                    <XAxis
                      dataKey="date"
                      tickFormatter={formatDate}
                      stroke="#64748b"
                      fontSize={12}
                      tickLine={false}
                    />
                    <YAxis stroke="#64748b" fontSize={12} tickLine={false} />
                    <Tooltip
                      contentStyle={{
                        backgroundColor: 'white',
                        border: '1px solid #e2e8f0',
                        borderRadius: '8px',
                        boxShadow: '0 4px 6px -1px rgb(0 0 0 / 0.1)',
                      }}
                      labelFormatter={(label) => formatDate(label as string)}
                    />
                    <Area
                      type="monotone"
                      dataKey="count"
                      stroke="#8b5cf6"
                      strokeWidth={2}
                      fillOpacity={1}
                      fill="url(#colorCount)"
                    />
                  </AreaChart>
                </ResponsiveContainer>
              </CardContent>
            </Card>
          </motion.div>

          {/* 相册分布饼图 */}
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.3, delay: 0.2 }}
          >
            <Card className="h-[400px]">
              <CardHeader>
                <CardTitle className="text-base font-semibold flex items-center gap-2">
                  <FolderOpen className="w-4 h-4 text-violet-500" />
                  相册分布
                </CardTitle>
                <CardDescription>各相册图片数量占比</CardDescription>
              </CardHeader>
              <CardContent className="h-[300px]">
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <Pie
                      data={stats.albumDistribution.slice(0, 8)}
                      cx="50%"
                      cy="50%"
                      innerRadius={60}
                      outerRadius={100}
                      paddingAngle={2}
                      dataKey="count"
                      nameKey="name"
                    >
                      {stats.albumDistribution.slice(0, 8).map((entry, index) => (
                        <Cell key={`cell-${index}`} fill={PIE_COLORS[index % PIE_COLORS.length]} />
                      ))}
                    </Pie>
                    <Tooltip
                      contentStyle={{
                        backgroundColor: 'white',
                        border: '1px solid #e2e8f0',
                        borderRadius: '8px',
                        boxShadow: '0 4px 6px -1px rgb(0 0 0 / 0.1)',
                      }}
                      formatter={(value: number, name: string, props: any) => [
                        `${value} 张 (${props.payload.percentage}%)`,
                        name,
                      ]}
                    />
                  </PieChart>
                </ResponsiveContainer>
              </CardContent>
            </Card>
          </motion.div>

          {/* 文件类型分布 */}
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.3, delay: 0.3 }}
          >
            <Card className="h-[400px]">
              <CardHeader>
                <CardTitle className="text-base font-semibold flex items-center gap-2">
                  <Image className="w-4 h-4 text-violet-500" />
                  文件类型分布
                </CardTitle>
                <CardDescription>各类型文件数量和大小</CardDescription>
              </CardHeader>
              <CardContent className="h-[300px]">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={stats.fileTypeStats.slice(0, 6)} layout="vertical">
                    <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" horizontal={false} />
                    <XAxis type="number" stroke="#64748b" fontSize={12} />
                    <YAxis
                      dataKey="type"
                      type="category"
                      stroke="#64748b"
                      fontSize={12}
                      width={60}
                    />
                    <Tooltip
                      contentStyle={{
                        backgroundColor: 'white',
                        border: '1px solid #e2e8f0',
                        borderRadius: '8px',
                        boxShadow: '0 4px 6px -1px rgb(0 0 0 / 0.1)',
                      }}
                      formatter={(value: number, name: string, props: any) => [
                        `${value} 张 (${formatFileSize(props.payload.size)})`,
                        props.payload.type,
                      ]}
                    />
                    <Bar
                      dataKey="count"
                      fill="#8b5cf6"
                      radius={[0, 4, 4, 0]}
                    />
                  </BarChart>
                </ResponsiveContainer>
              </CardContent>
            </Card>
          </motion.div>

          {/* 热门标签云 */}
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.3, delay: 0.4 }}
          >
            <Card className="h-[400px]">
              <CardHeader>
                <CardTitle className="text-base font-semibold flex items-center gap-2">
                  <Tag className="w-4 h-4 text-violet-500" />
                  热门标签
                </CardTitle>
                <CardDescription>使用频率最高的标签</CardDescription>
              </CardHeader>
              <CardContent>
                <div className="flex flex-wrap gap-2 max-h-[300px] overflow-y-auto">
                  {stats.topTags.map((tag, index) => {
                    const size = index < 3 ? 'lg' : index < 8 ? 'default' : 'sm';
                    const colors = [
                      'bg-violet-100 text-violet-700 border-violet-200',
                      'bg-pink-100 text-pink-700 border-pink-200',
                      'bg-blue-100 text-blue-700 border-blue-200',
                      'bg-green-100 text-green-700 border-green-200',
                      'bg-orange-100 text-orange-700 border-orange-200',
                    ];
                    const colorClass = colors[index % colors.length];

                    return (
                      <motion.div
                        key={tag.name}
                        initial={{ opacity: 0, scale: 0.8 }}
                        animate={{ opacity: 1, scale: 1 }}
                        transition={{ duration: 0.2, delay: index * 0.05 }}
                      >
                        <Badge
                          variant="outline"
                          className={cn(
                            'cursor-pointer hover:shadow-md transition-shadow',
                            colorClass,
                            size === 'lg' && 'text-base px-3 py-1',
                            size === 'sm' && 'text-xs',
                          )}
                        >
                          {tag.name}
                          <span className="ml-1 opacity-60">({tag.count})</span>
                        </Badge>
                      </motion.div>
                    );
                  })}
                </div>
              </CardContent>
            </Card>
          </motion.div>
        </div>

        {/* 存储空间趋势 */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.3, delay: 0.5 }}
          className="mt-6"
        >
          <Card>
            <CardHeader>
              <CardTitle className="text-base font-semibold flex items-center gap-2">
                <HardDrive className="w-4 h-4 text-violet-500" />
                存储空间趋势
              </CardTitle>
              <CardDescription>每日新增存储空间统计</CardDescription>
            </CardHeader>
            <CardContent className="h-[300px]">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={stats.storageTrend}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                  <XAxis
                    dataKey="date"
                    tickFormatter={formatDate}
                    stroke="#64748b"
                    fontSize={12}
                  />
                  <YAxis
                    stroke="#64748b"
                    fontSize={12}
                    tickFormatter={(value) => formatFileSize(value as number)}
                  />
                  <Tooltip
                    contentStyle={{
                      backgroundColor: 'white',
                      border: '1px solid #e2e8f0',
                      borderRadius: '8px',
                      boxShadow: '0 4px 6px -1px rgb(0 0 0 / 0.1)',
                    }}
                    labelFormatter={(label) => formatDate(label as string)}
                    formatter={(value: number) => [formatFileSize(value), '新增存储']}
                  />
                  <Bar
                    dataKey="size"
                    fill="#3b82f6"
                    radius={[4, 4, 0, 0]}
                  />
                </BarChart>
              </ResponsiveContainer>
            </CardContent>
          </Card>
        </motion.div>

        {/* 活跃度统计 */}
        {activityStats && (
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.3, delay: 0.6 }}
            className="mt-6"
          >
            <Card className="bg-gradient-to-r from-violet-500 to-purple-600 border-0">
              <CardContent className="p-6">
                <div className="flex items-center gap-2 mb-4">
                  <Activity className="w-5 h-5 text-white" />
                  <h3 className="text-lg font-semibold text-white">今日活跃度</h3>
                  {activityStats.growthRate !== 0 && (
                    <Badge className={cn(
                      "ml-2",
                      activityStats.growthRate > 0 
                        ? "bg-green-400 text-green-900" 
                        : "bg-red-400 text-red-900"
                    )}>
                      {activityStats.growthRate > 0 ? '+' : ''}{activityStats.growthRate.toFixed(1)}%
                    </Badge>
                  )}
                </div>
                <div className="grid grid-cols-2 md:grid-cols-4 gap-6">
                  <div className="text-center">
                    <div className="flex items-center justify-center gap-2 mb-2">
                      <Upload className="w-5 h-5 text-violet-200" />
                      <span className="text-3xl font-bold text-white">{activityStats.todayUploads}</span>
                    </div>
                    <p className="text-sm text-violet-200">今日上传</p>
                  </div>
                  <div className="text-center">
                    <div className="flex items-center justify-center gap-2 mb-2">
                      <Eye className="w-5 h-5 text-violet-200" />
                      <span className="text-3xl font-bold text-white">{formatNumber(activityStats.todayViews)}</span>
                    </div>
                    <p className="text-sm text-violet-200">今日浏览</p>
                  </div>
                  <div className="text-center">
                    <div className="flex items-center justify-center gap-2 mb-2">
                      <Download className="w-5 h-5 text-violet-200" />
                      <span className="text-3xl font-bold text-white">{formatNumber(activityStats.todayDownloads)}</span>
                    </div>
                    <p className="text-sm text-violet-200">今日下载</p>
                  </div>
                  <div className="text-center">
                    <div className="flex items-center justify-center gap-2 mb-2">
                      <Star className="w-5 h-5 text-violet-200" />
                      <span className="text-3xl font-bold text-white">{activityStats.todayFavorites}</span>
                    </div>
                    <p className="text-sm text-violet-200">今日收藏</p>
                  </div>
                </div>
              </CardContent>
            </Card>
          </motion.div>
        )}

        {/* 热门资源和热门相册 */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mt-6">
          {/* 热门资源 */}
          {hotResources.length > 0 && (
            <motion.div
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.3, delay: 0.7 }}
            >
              <Card className="h-[400px]">
                <CardHeader>
                  <CardTitle className="text-base font-semibold flex items-center gap-2">
                    <Flame className="w-4 h-4 text-orange-500" />
                    热门资源
                  </CardTitle>
                  <CardDescription>浏览、下载、收藏综合排名</CardDescription>
                </CardHeader>
                <CardContent className="overflow-y-auto">
                  <div className="space-y-3">
                    {hotResources.slice(0, 8).map((resource, index) => (
                      <motion.div
                        key={resource.id}
                        initial={{ opacity: 0, x: -20 }}
                        animate={{ opacity: 1, x: 0 }}
                        transition={{ duration: 0.2, delay: index * 0.05 }}
                        className="flex items-center gap-3 p-2 rounded-lg hover:bg-slate-50 transition-colors"
                      >
                        <div className="flex-shrink-0 w-8 h-8 rounded-full bg-gradient-to-br from-orange-400 to-red-500 flex items-center justify-center text-white font-bold text-sm">
                          {index + 1}
                        </div>
                        <div className="flex-shrink-0 w-12 h-12 rounded-lg bg-slate-100 overflow-hidden">
                          {resource.thumbnailUrl ? (
                            <img
                              src={resource.thumbnailUrl}
                              alt={resource.title}
                              className="w-full h-full object-cover"
                              onError={(e) => {
                                (e.target as HTMLImageElement).style.display = 'none';
                              }}
                            />
                          ) : (
                            <div className="w-full h-full flex items-center justify-center">
                              <Image className="w-6 h-6 text-slate-400" />
                            </div>
                          )}
                        </div>
                        <div className="flex-1 min-w-0">
                          <p className="text-sm font-medium text-slate-800 truncate">
                            {resource.title || '未命名'}
                          </p>
                          <p className="text-xs text-slate-500 truncate">
                            {resource.albumName}
                          </p>
                        </div>
                        <div className="flex items-center gap-3 text-xs text-slate-500">
                          <span className="flex items-center gap-1">
                            <Eye className="w-3 h-3" />
                            {formatNumber(resource.viewCount)}
                          </span>
                          <span className="flex items-center gap-1">
                            <Download className="w-3 h-3" />
                            {formatNumber(resource.downloadCount)}
                          </span>
                          <span className="flex items-center gap-1 text-red-500">
                            <Heart className="w-3 h-3" />
                            {resource.favoriteCount}
                          </span>
                        </div>
                      </motion.div>
                    ))}
                  </div>
                </CardContent>
              </Card>
            </motion.div>
          )}

          {/* 热门相册 */}
          {hotAlbums.length > 0 && (
            <motion.div
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.3, delay: 0.8 }}
            >
              <Card className="h-[400px]">
                <CardHeader>
                  <CardTitle className="text-base font-semibold flex items-center gap-2">
                    <FolderOpen className="w-4 h-4 text-green-500" />
                    热门相册
                  </CardTitle>
                  <CardDescription>资源数量最多的相册</CardDescription>
                </CardHeader>
                <CardContent className="overflow-y-auto">
                  <div className="space-y-3">
                    {hotAlbums.map((album, index) => (
                      <motion.div
                        key={album.id}
                        initial={{ opacity: 0, x: -20 }}
                        animate={{ opacity: 1, x: 0 }}
                        transition={{ duration: 0.2, delay: index * 0.05 }}
                        className="flex items-center gap-3 p-2 rounded-lg hover:bg-slate-50 transition-colors cursor-pointer"
                        onClick={() => router.push(`/?album=${album.id}`)}
                      >
                        <div className="flex-shrink-0 w-10 h-10 rounded-lg bg-gradient-to-br from-green-400 to-emerald-500 flex items-center justify-center text-white font-bold">
                          {index + 1}
                        </div>
                        <div className="flex-shrink-0 w-12 h-12 rounded-lg bg-slate-100 overflow-hidden">
                          {album.coverUrl ? (
                            <img
                              src={album.coverUrl}
                              alt={album.name}
                              className="w-full h-full object-cover"
                              onError={(e) => {
                                (e.target as HTMLImageElement).style.display = 'none';
                              }}
                            />
                          ) : (
                            <div className="w-full h-full flex items-center justify-center">
                              <FolderOpen className="w-6 h-6 text-slate-400" />
                            </div>
                          )}
                        </div>
                        <div className="flex-1 min-w-0">
                          <p className="text-sm font-medium text-slate-800 truncate">
                            {album.name}
                          </p>
                          <p className="text-xs text-slate-500">
                            {formatFileSize(album.totalSize)}
                          </p>
                        </div>
                        <div className="text-right">
                          <span className="text-lg font-bold text-slate-800">
                            {album.imageCount}
                          </span>
                          <p className="text-xs text-slate-500">张图片</p>
                        </div>
                      </motion.div>
                    ))}
                  </div>
                </CardContent>
              </Card>
            </motion.div>
          )}
        </div>
      </main>
    </div>
  );
}
