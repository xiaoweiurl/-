'use client';

import { useState, useEffect } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Progress } from '@/components/ui/progress';
import { Button } from '@/components/ui/button';
import {
  HardDrive,
  Image as ImageIcon,
  FolderOpen,
  Trash2,
  TrendingUp,
  Database,
  Loader2,
} from 'lucide-react';

interface StorageStats {
  totalImages: number;
  totalAlbums: number;
  totalSize: number;
  usedQuota: number;
  quotaLimit: number;
  trashCount: number;
  trashSize: number;
  usagePercentage: number;
}

export default function StorageStats() {
  const [stats, setStats] = useState<StorageStats | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadStats();
  }, []);

  const loadStats = async () => {
    setLoading(true);
    try {
      const response = await fetch('/api/storage/stats');
      const data = await response.json();
      if (data.stats) {
        setStats(data.stats);
      }
    } catch (error) {
      console.error('Load storage stats failed:', error);
    } finally {
      setLoading(false);
    }
  };

  const formatSize = (bytes: number) => {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  };

  const getUsageColor = (percentage: number) => {
    if (percentage < 50) return 'bg-green-500';
    if (percentage < 80) return 'bg-yellow-500';
    return 'bg-red-500';
  };

  if (loading) {
    return (
      <Card>
        <CardContent className="flex items-center justify-center py-8">
          <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
        </CardContent>
      </Card>
    );
  }

  if (!stats) {
    return (
      <Card>
        <CardContent className="text-center py-8 text-gray-500">
          无法加载存储统计
        </CardContent>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between">
        <CardTitle className="flex items-center gap-2">
          <HardDrive className="w-5 h-5" />
          存储空间
        </CardTitle>
        <Button variant="ghost" size="sm" onClick={loadStats}>
          刷新
        </Button>
      </CardHeader>
      <CardContent className="space-y-6">
        {/* 使用量进度条 */}
        <div className="space-y-2">
          <div className="flex justify-between text-sm">
            <span>已使用 {formatSize(stats.usedQuota)}</span>
            <span>
              {stats.quotaLimit > 0
                ? `共 ${formatSize(stats.quotaLimit)}`
                : '无限制'}
            </span>
          </div>
          <div className="relative h-3 rounded-full bg-gray-100 overflow-hidden">
            <div
              className={`absolute left-0 top-0 h-full rounded-full transition-all ${getUsageColor(
                stats.usagePercentage
              )}`}
              style={{ width: `${Math.min(stats.usagePercentage, 100)}%` }}
            />
          </div>
          {stats.quotaLimit > 0 && (
            <p className="text-xs text-gray-500 text-right">
              {stats.usagePercentage.toFixed(1)}% 已使用
            </p>
          )}
        </div>

        {/* 统计卡片 */}
        <div className="grid grid-cols-2 gap-4">
          <div className="p-4 rounded-lg bg-blue-50 space-y-1">
            <div className="flex items-center gap-2 text-blue-600">
              <ImageIcon className="w-4 h-4" />
              <span className="text-sm font-medium">图片数量</span>
            </div>
            <p className="text-2xl font-bold text-blue-700">
              {stats.totalImages.toLocaleString()}
            </p>
          </div>

          <div className="p-4 rounded-lg bg-purple-50 space-y-1">
            <div className="flex items-center gap-2 text-purple-600">
              <FolderOpen className="w-4 h-4" />
              <span className="text-sm font-medium">相册数量</span>
            </div>
            <p className="text-2xl font-bold text-purple-700">
              {stats.totalAlbums.toLocaleString()}
            </p>
          </div>

          <div className="p-4 rounded-lg bg-green-50 space-y-1">
            <div className="flex items-center gap-2 text-green-600">
              <Database className="w-4 h-4" />
              <span className="text-sm font-medium">总大小</span>
            </div>
            <p className="text-2xl font-bold text-green-700">
              {formatSize(stats.totalSize)}
            </p>
          </div>

          <div className="p-4 rounded-lg bg-orange-50 space-y-1">
            <div className="flex items-center gap-2 text-orange-600">
              <Trash2 className="w-4 h-4" />
              <span className="text-sm font-medium">回收站</span>
            </div>
            <p className="text-2xl font-bold text-orange-700">
              {stats.trashCount.toLocaleString()}
            </p>
            <p className="text-xs text-orange-500">
              {formatSize(stats.trashSize)}
            </p>
          </div>
        </div>

        {/* 使用趋势提示 */}
        {stats.usagePercentage > 80 && stats.quotaLimit > 0 && (
          <div className="flex items-center gap-2 p-3 bg-yellow-50 rounded-lg text-yellow-700 text-sm">
            <TrendingUp className="w-4 h-4 flex-shrink-0" />
            <span>
              存储空间即将用尽，建议清理回收站或升级存储配额
            </span>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
