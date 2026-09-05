'use client';

import { useState, useEffect } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { Badge } from '@/components/ui/badge';
import {
  FileText,
  RefreshCw,
  Loader2,
  ChevronLeft,
  ChevronRight,
} from 'lucide-react';

interface AuditLog {
  id: string;
  userId: string;
  username: string;
  action: string;
  resourceType: string;
  resourceId: string | null;
  details: string;
  ipAddress: string;
  userAgent: string;
  createdAt: string;
}

interface AuditLogsResponse {
  logs: AuditLog[];
  total: number;
  page: number;
  pageSize: number;
  totalPages: number;
}

const ACTION_LABELS: Record<string, string> = {
  login: '登录',
  logout: '登出',
  upload: '上传',
  delete: '删除',
  update: '更新',
  create: '创建',
  download: '下载',
  share: '分享',
  restore: '恢复',
  move: '移动',
  backup: '备份',
};

const ACTION_COLORS: Record<string, string> = {
  login: 'bg-[rgba(0,122,255,0.1)] text-[#007aff]',
  logout: 'bg-[rgba(118,118,128,0.08)] text-[#8e8e93]',
  upload: 'bg-[rgba(52,199,89,0.1)] text-[#34c759]',
  delete: 'bg-[rgba(255,59,48,0.1)] text-[#ff3b30]',
  update: 'bg-[rgba(255,149,0,0.1)] text-[#ff9500]',
  create: 'bg-[rgba(0,122,255,0.1)] text-[#007aff]',
  download: 'bg-[rgba(0,122,255,0.1)] text-[#007aff]',
  share: 'bg-[rgba(175,82,222,0.1)] text-[#af52de]',
  restore: 'bg-[rgba(255,149,0,0.1)] text-[#ff9500]',
  move: 'bg-[rgba(0,122,255,0.1)] text-[#007aff]',
  backup: 'bg-[rgba(52,199,89,0.1)] text-[#34c759]',
};

export default function AuditLogs() {
  const [logs, setLogs] = useState<AuditLog[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [actionFilter, setActionFilter] = useState('all');

  useEffect(() => {
    loadLogs();
  }, [page, actionFilter]);

  const loadLogs = async () => {
    setLoading(true);
    try {
      const params = new URLSearchParams();
      params.append('page', page.toString());
      params.append('pageSize', '20');
      if (actionFilter !== 'all') {
        params.append('action', actionFilter);
      }

      const response = await fetch(`/api/audit?${params.toString()}`);
      const data: AuditLogsResponse = await response.json();

      if (data.logs) {
        setLogs(data.logs);
        setTotalPages(data.totalPages || 1);
      }
    } catch (error) {
      console.error('Load audit logs failed:', error);
    } finally {
      setLoading(false);
    }
  };

  const formatDate = (dateStr: string) => {
    return new Date(dateStr).toLocaleString('zh-CN');
  };

  const getActionLabel = (action: string) => {
    return ACTION_LABELS[action] || action;
  };

  const getActionColor = (action: string) => {
    return ACTION_COLORS[action] || 'bg-[rgba(118,118,128,0.08)] text-[#8e8e93]';
  };

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between">
        <CardTitle className="flex items-center gap-2">
          <FileText className="w-5 h-5" />
          操作日志
        </CardTitle>
        <div className="flex items-center gap-2">
          <Select value={actionFilter} onValueChange={setActionFilter}>
            <SelectTrigger className="w-32">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">全部操作</SelectItem>
              <SelectItem value="login">登录</SelectItem>
              <SelectItem value="upload">上传</SelectItem>
              <SelectItem value="delete">删除</SelectItem>
              <SelectItem value="download">下载</SelectItem>
              <SelectItem value="share">分享</SelectItem>
            </SelectContent>
          </Select>
          <Button variant="ghost" size="sm" onClick={loadLogs}>
            <RefreshCw className="w-4 h-4" />
          </Button>
        </div>
      </CardHeader>
      <CardContent>
        {loading ? (
          <div className="flex items-center justify-center py-8">
            <Loader2 className="w-6 h-6 animate-spin text-[#8e8e93]" />
          </div>
        ) : logs.length === 0 ? (
          <div className="text-center py-8 text-[#8e8e93]">暂无操作日志</div>
        ) : (
          <>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>时间</TableHead>
                  <TableHead>用户</TableHead>
                  <TableHead>操作</TableHead>
                  <TableHead>详情</TableHead>
                  <TableHead>IP 地址</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {logs.map((log) => (
                  <TableRow key={log.id}>
                    <TableCell className="text-sm text-[#8e8e93]">
                      {formatDate(log.createdAt)}
                    </TableCell>
                    <TableCell>{log.username}</TableCell>
                    <TableCell>
                      <Badge className={getActionColor(log.action)}>
                        {getActionLabel(log.action)}
                      </Badge>
                    </TableCell>
                    <TableCell className="max-w-xs truncate">
                      {log.details}
                    </TableCell>
                    <TableCell className="text-sm text-[#8e8e93]">
                      {log.ipAddress}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>

            {/* 分页 */}
            {totalPages > 1 && (
              <div className="flex items-center justify-center gap-2 mt-4">
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page <= 1}
                  onClick={() => setPage(page - 1)}
                >
                  <ChevronLeft className="w-4 h-4" />
                </Button>
                <span className="text-sm text-[#8e8e93]">
                  {page} / {totalPages}
                </span>
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page >= totalPages}
                  onClick={() => setPage(page + 1)}
                >
                  <ChevronRight className="w-4 h-4" />
                </Button>
              </div>
            )}
          </>
        )}
      </CardContent>
    </Card>
  );
}
