'use client';

import { useState, useEffect, useCallback } from 'react';
import {
  Activity, AlertTriangle, AlertCircle, CheckCircle2, Clock,
  Cpu, HardDrive, MemoryStick, Network, RefreshCw, Search,
  Shield, TrendingDown, TrendingUp, Users, Zap, Server,
  Database, ArrowUpDown, ChevronDown, ChevronUp, Eye,
  Download, Trash2, RotateCcw, XCircle, Bug, FileText,
  BarChart3, Globe, Lock, UserCheck, ArrowRight,
} from 'lucide-react';

// ============ 类型定义 ============
interface ApiMetrics {
  summary: {
    totalRequests: number; successRate: number; avgResponseTime: number;
    errorCount: number; uptime: string; activeUsers: number; requestsPerMinute: number;
  };
  hourlyRequests: { hour: string; total: number; success: number; error: number; avgResponseTime: number }[];
  endpoints: { path: string; method: string; calls: number; avgMs: number; errorRate: number; p99Ms: number }[];
  systemResources: {
    cpu: { current: number; peak: number; cores: number };
    memory: { usedMb: number; totalMb: number; peakMb: number; percentage: number };
    disk: { usedGb: number; totalGb: number; percentage: number };
    network: { inboundKbps: number; outboundKbps: number; totalRequests: number };
  };
  lastUpdated: string;
}

interface ErrorItem {
  id: string; type: string; message: string; stack: string;
  endpoint: string; method: string; statusCode: number;
  occurrences: number; firstSeen: string; lastSeen: string;
  severity: string; status: string;
}

interface PerformanceData {
  responseTimeline: { minute: string; p50: number; p90: number; p99: number }[];
  services: {
    name: string; status: string; uptime: string;
    responseTime: { p50: number; p90: number; p99: number };
    throughput: number; errorRate: number; connections: number; maxConnections: number;
  }[];
  slowQueries: { endpoint: string; avgMs: number; maxMs: number; calls: number; dbMs: number; aiMs: number }[];
  runtime: {
    jvm: { heapUsedMb: number; heapMaxMb: number; gcPauseMs: number; threadCount: number; peakThreadCount: number };
    node: { rssMb: number; heapUsedMb: number; heapTotalMb: number; externalMb: number; arrayBuffersMb: number };
    database: { activeConnections: number; maxConnections: number; waitingConnections: number; avgQueryMs: number; slowQueryCount: number };
  };
  lastUpdated: string;
}

interface AuditLog {
  id: string; action: string; resourceType: string; resourceId: string;
  userId: string; username: string; details: string;
  ipAddress: string; userAgent: string; createdAt: string;
}

interface BackupItem {
  id: string; name: string; type: string; size: number; status: string; createdAt: string;
}

type TabKey = 'monitor' | 'errors' | 'performance' | 'audit' | 'backup' | 'users';

// ============ 工具函数 ============
const fmt = (n: number | undefined | null) => n != null ? n.toLocaleString() : '-';
const fmtMs = (ms: number | undefined | null) => ms == null ? '-' : ms >= 1000 ? `${(ms / 1000).toFixed(1)}s` : `${ms}ms`;
const fmtSize = (bytes: number | undefined | null) => {
  if (bytes == null) return '-';
  if (bytes >= 1073741824) return `${(bytes / 1073741824).toFixed(1)}GB`;
  if (bytes >= 1048576) return `${(bytes / 1048576).toFixed(1)}MB`;
  return `${(bytes / 1024).toFixed(1)}KB`;
};
const timeAgo = (iso: string) => {
  const diff = Date.now() - new Date(iso).getTime();
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`;
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`;
  return `${Math.floor(diff / 86400000)}天前`;
};
const severityColor = (s: string) => {
  if (s === 'critical') return 'text-red-400 bg-red-500/10 border-red-500/20';
  if (s === 'high') return 'text-orange-400 bg-orange-500/10 border-orange-500/20';
  if (s === 'medium') return 'text-yellow-400 bg-yellow-500/10 border-yellow-500/20';
  return 'text-blue-400 bg-blue-500/10 border-blue-500/20';
};
const statusIcon = (s: string) => {
  if (s === 'healthy') return <CheckCircle2 className="w-3.5 h-3.5 text-green-400" />;
  if (s === 'degraded') return <AlertTriangle className="w-3.5 h-3.5 text-yellow-400" />;
  return <AlertCircle className="w-3.5 h-3.5 text-red-400" />;
};

// ============ 主组件 ============
export default function OpsCenterPage() {
  const [activeTab, setActiveTab] = useState<TabKey>('monitor');
  const [metrics, setMetrics] = useState<ApiMetrics | null>(null);
  const [errors, setErrors] = useState<ErrorItem[]>([]);
  const [perf, setPerf] = useState<PerformanceData | null>(null);
  const [auditLogs, setAuditLogs] = useState<AuditLog[]>([]);
  const [backups, setBackups] = useState<BackupItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [expandedError, setExpandedError] = useState<string | null>(null);

  const [apiError, setApiError] = useState<string | null>(null);

  const fetchData = useCallback(async () => {
    setLoading(true);
    setApiError(null);
    try {
      const [mRes, eRes, pRes, aRes, bRes] = await Promise.all([
        fetch('/api/ops/metrics'),
        fetch('/api/ops/errors'),
        fetch('/api/ops/performance'),
        fetch('/api/audit?pageSize=30'),
        fetch('/api/backup?pageSize=10'),
      ]);

      // 逐个安全解析，HTTP错误时跳过
      const safeJson = async (res: Response) => {
        if (!res.ok) return null;
        try { return await res.json(); } catch { return null; }
      };

      const [mData, eData, pData, aData, bData] = await Promise.all([
        safeJson(mRes), safeJson(eRes), safeJson(pRes), safeJson(aRes), safeJson(bRes),
      ]);

      const failedApis: string[] = [];
      if (mData?.success) setMetrics(mData.data); else failedApis.push('API监控');
      if (eData?.success) setErrors(eData.data.errors); else failedApis.push('错误追踪');
      if (pData?.success) setPerf(pData.data); else failedApis.push('性能指标');
      if (aData?.success) setAuditLogs(aData.logs || aData.data?.logs || []); else failedApis.push('操作审计');
      if (bData?.success) setBackups(bData.backups || bData.data?.backups || []); else failedApis.push('备份管理');

      if (failedApis.length === 5) {
        setApiError('无法连接后端服务（默认 http://localhost:8080/api），请检查Java后端是否启动及端口配置');
      } else if (failedApis.length > 0) {
        setApiError(`${failedApis.join('、')}数据加载失败，请检查对应后端接口`);
      }
    } catch (e) {
      console.error('Failed to fetch ops data:', e);
      setApiError('网络请求失败，请检查服务连接');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchData(); }, [fetchData]);

  const tabs: { key: TabKey; label: string; icon: React.ReactNode }[] = [
    { key: 'monitor', label: 'API 监控', icon: <Activity className="w-4 h-4" /> },
    { key: 'errors', label: '错误追踪', icon: <Bug className="w-4 h-4" /> },
    { key: 'performance', label: '性能指标', icon: <Zap className="w-4 h-4" /> },
    { key: 'audit', label: '操作审计', icon: <Shield className="w-4 h-4" /> },
    { key: 'backup', label: '备份管理', icon: <Database className="w-4 h-4" /> },
    { key: 'users', label: '用户管理', icon: <Users className="w-4 h-4" /> },
  ];

  return (
    <div className="h-screen flex flex-col bg-[#0a0e1a] text-slate-200 overflow-hidden">
      {/* 顶部栏 */}
      <div className="flex items-center justify-between px-6 py-4 border-b border-slate-700/50 bg-slate-800/30">
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-lg bg-gradient-to-br from-blue-600 to-cyan-500 flex items-center justify-center">
            <Server className="w-5 h-5 text-white" />
          </div>
          <div>
            <h1 className="text-lg font-semibold text-slate-100">系统运维中心</h1>
            <p className="text-xs text-slate-400">API监控 · 错误追踪 · 性能指标 · 操作审计 · 备份管理</p>
          </div>
        </div>
        <div className="flex items-center gap-3">
          <span className="text-xs text-slate-500">
            {metrics?.lastUpdated ? `更新于 ${timeAgo(metrics.lastUpdated)}` : '加载中...'}
          </span>
          <button onClick={fetchData} className="p-2 rounded-lg hover:bg-slate-700/50 text-slate-400 hover:text-slate-200 transition-colors">
            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
          </button>
        </div>
      </div>

      {/* Tab 导航 */}
      <div className="flex items-center gap-1 px-6 py-2 border-b border-slate-700/30 bg-slate-900/50 overflow-x-auto">
        {tabs.map(tab => (
          <button
            key={tab.key}
            onClick={() => setActiveTab(tab.key)}
            className={`flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-all whitespace-nowrap
              ${activeTab === tab.key
                ? 'bg-blue-500/15 text-blue-400 border border-blue-500/20'
                : 'text-slate-400 hover:text-slate-200 hover:bg-slate-700/30 border border-transparent'
              }`}
          >
            {tab.icon}
            {tab.label}
          </button>
        ))}
      </div>

      {/* 服务连接错误提示 */}
      {apiError && (
        <div className="px-6 py-3 bg-orange-500/10 border-b border-orange-500/20 flex items-center gap-2">
          <AlertTriangle className="w-4 h-4 text-orange-400 shrink-0" />
          <span className="text-sm text-orange-300">{apiError}</span>
          <button onClick={fetchData} className="ml-auto text-xs text-orange-400 hover:text-orange-300 underline">重试</button>
        </div>
      )}

      {/* 内容区 */}
      <div className="flex-1 overflow-y-auto p-6">
        {loading && !metrics ? (
          <div className="flex items-center justify-center h-64">
            <RefreshCw className="w-8 h-8 text-blue-400 animate-spin" />
          </div>
        ) : (
          <>
            {activeTab === 'monitor' && <MonitorTab metrics={metrics} />}
            {activeTab === 'errors' && <ErrorsTab errors={errors || []} expandedError={expandedError} setExpandedError={setExpandedError} />}
            {activeTab === 'performance' && <PerformanceTab perf={perf} />}
            {activeTab === 'audit' && <AuditTab logs={auditLogs || []} />}
            {activeTab === 'backup' && <BackupTab backups={backups || []} onRefresh={fetchData} />}
            {activeTab === 'users' && <UsersTab />}
          </>
        )}
      </div>
    </div>
  );
}

// ============ API 监控 Tab ============
function MonitorTab({ metrics }: { metrics: ApiMetrics | null }) {
  if (!metrics) return (
    <div className="flex flex-col items-center justify-center py-20 text-slate-400">
      <Server className="w-10 h-10 mb-3 text-slate-600" />
      <p className="text-sm">后端服务未连接，API监控数据暂不可用</p>
    </div>
  );
  const { summary: rawSummary = { totalRequests: 0, errorRate: 0, avgResponseTime: 0, successRate: 0, requestsPerMinute: 0, errorCount: 0, activeUsers: 0, uptime: 0 }, hourlyRequests = [], endpoints = [], systemResources: sys = { cpu: { current: 0, peak: 0, cores: 0 }, memory: { usedMb: 0, totalMb: 0, peakMb: 0, percentage: 0 }, disk: { usedGb: 0, totalGb: 0, percentage: 0 }, network: { inboundKbps: 0, outboundKbps: 0, totalRequests: 0 } } } = metrics || {};
  const summary = Object.assign({ totalRequests: 0, errorRate: 0, avgResponseTime: 0, successRate: 0, requestsPerMinute: 0, errorCount: 0, activeUsers: 0, uptime: 0 }, rawSummary);
  const maxRequests = hourlyRequests.length > 0 ? Math.max(...hourlyRequests.map((h: any) => h.total)) : 1;

  return (
    <div className="space-y-6">
      {/* KPI 指标 */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        {[
          { label: '总请求数', value: fmt(summary.totalRequests ?? 0), icon: <Globe className="w-5 h-5" />, color: 'blue', sub: `${summary.requestsPerMinute ?? 0} 次/分` },
          { label: '成功率', value: `${summary.successRate ?? 0}%`, icon: <CheckCircle2 className="w-5 h-5" />, color: 'green', sub: `${fmt(summary.errorCount ?? 0)} 错误` },
          { label: '平均响应', value: fmtMs(summary.avgResponseTime ?? 0), icon: <Clock className="w-5 h-5" />, color: 'cyan', sub: '全端点平均' },
          { label: '在线用户', value: fmt(summary.activeUsers), icon: <Users className="w-5 h-5" />, color: 'purple', sub: `运行 ${summary.uptime ?? '-'}` },
        ].map((kpi, i) => (
          <div key={i} className="bg-slate-800/50 rounded-xl p-4 border border-slate-700/30 hover:border-blue-500/20 transition-all">
            <div className="flex items-center justify-between mb-2">
              <span className="text-sm text-slate-400">{kpi.label}</span>
              <div className={`text-${kpi.color}-400`}>{kpi.icon}</div>
            </div>
            <div className="text-2xl font-bold text-slate-100 font-mono">{kpi.value}</div>
            <div className="text-xs text-slate-500 mt-1">{kpi.sub}</div>
          </div>
        ))}
      </div>

      {/* 请求量趋势 + 系统资源 */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        {/* 24小时请求趋势 */}
        <div className="lg:col-span-2 bg-slate-800/50 rounded-xl p-5 border border-slate-700/30">
          <h3 className="text-sm font-medium text-slate-300 mb-4">24小时请求趋势</h3>
          <div className="flex items-end gap-[3px] h-48">
            {hourlyRequests.map((h, i) => (
              <div key={i} className="flex-1 flex flex-col items-center gap-0.5 group relative">
                <div className="w-full relative flex-1 flex items-end">
                  <div
                    className="w-full bg-blue-500/20 rounded-t-sm group-hover:bg-blue-500/30 transition-colors"
                    style={{ height: `${(h.success / maxRequests) * 100}%` }}
                  />
                  <div
                    className="w-full bg-red-500/40 rounded-t-sm absolute bottom-0"
                    style={{ height: `${(h.error / maxRequests) * 100}%` }}
                  />
                </div>
                {/* Hover tooltip */}
                <div className="absolute -top-16 left-1/2 -translate-x-1/2 hidden group-hover:block bg-slate-700 rounded-lg px-2 py-1 text-xs whitespace-nowrap z-10 shadow-lg">
                  <div className="text-slate-200">{h.hour}</div>
                  <div className="text-blue-400">成功: {h.success}</div>
                  <div className="text-red-400">错误: {h.error}</div>
                  <div className="text-slate-400">均值: {h.avgResponseTime}ms</div>
                </div>
                {i % 4 === 0 && <span className="text-[9px] text-slate-600 mt-1">{h.hour}</span>}
              </div>
            ))}
          </div>
          <div className="flex items-center gap-4 mt-3 text-xs text-slate-500">
            <span className="flex items-center gap-1"><span className="w-3 h-2 bg-blue-500/30 rounded-sm" /> 成功</span>
            <span className="flex items-center gap-1"><span className="w-3 h-2 bg-red-500/40 rounded-sm" /> 错误</span>
          </div>
        </div>

        {/* 系统资源 */}
        <div className="bg-slate-800/50 rounded-xl p-5 border border-slate-700/30">
          <h3 className="text-sm font-medium text-slate-300 mb-4">系统资源</h3>
          <div className="space-y-4">
            {/* CPU */}
            <div>
              <div className="flex items-center justify-between text-xs mb-1.5">
                <span className="text-slate-400 flex items-center gap-1.5"><Cpu className="w-3.5 h-3.5" /> CPU</span>
                <span className="text-slate-300 font-mono">{(sys.cpu?.current ?? 0)}% <span className="text-slate-500">/ 峰值 {(sys.cpu?.peak ?? 0)}%</span></span>
              </div>
              <div className="h-2 bg-slate-700/50 rounded-full overflow-hidden">
                <div className={`h-full rounded-full transition-all ${(sys.cpu?.current ?? 0) > 70 ? 'bg-red-500' : (sys.cpu?.current ?? 0) > 50 ? 'bg-yellow-500' : 'bg-blue-500'}`} style={{ width: `${(sys.cpu?.current ?? 0)}%` }} />
              </div>
            </div>
            {/* Memory */}
            <div>
              <div className="flex items-center justify-between text-xs mb-1.5">
                <span className="text-slate-400 flex items-center gap-1.5"><MemoryStick className="w-3.5 h-3.5" /> 内存</span>
                <span className="text-slate-300 font-mono">{(sys.memory?.usedMb ?? 0)}MB / {(sys.memory?.totalMb ?? 1)}MB</span>
              </div>
              <div className="h-2 bg-slate-700/50 rounded-full overflow-hidden">
                <div className={`h-full rounded-full transition-all ${((sys.memory?.usedMb ?? 0)/(sys.memory?.totalMb ?? 1)*100) > 70 ? 'bg-red-500' : ((sys.memory?.usedMb ?? 0)/(sys.memory?.totalMb ?? 1)*100) > 50 ? 'bg-yellow-500' : 'bg-cyan-500'}`} style={{ width: `${Math.round((sys.memory?.usedMb ?? 0)/(sys.memory?.totalMb ?? 1)*100)}%` }} />
              </div>
            </div>
            {/* Disk */}
            <div>
              <div className="flex items-center justify-between text-xs mb-1.5">
                <span className="text-slate-400 flex items-center gap-1.5"><HardDrive className="w-3.5 h-3.5" /> 磁盘</span>
                <span className="text-slate-300 font-mono">{(sys.disk?.usedGb ?? 0)}GB / {(sys.disk?.totalGb ?? 1)}GB</span>
              </div>
              <div className="h-2 bg-slate-700/50 rounded-full overflow-hidden">
                <div className={`h-full rounded-full transition-all ${(sys.disk?.percentage ?? 0) > 80 ? 'bg-red-500' : (sys.disk?.percentage ?? 0) > 60 ? 'bg-yellow-500' : 'bg-green-500'}`} style={{ width: `${(sys.disk?.percentage ?? 0)}%` }} />
              </div>
            </div>
            {/* Network */}
            <div>
              <div className="flex items-center justify-between text-xs mb-1.5">
                <span className="text-slate-400 flex items-center gap-1.5"><Network className="w-3.5 h-3.5" /> 网络</span>
                <span className="text-slate-300 font-mono">↑{(sys.network?.outboundKbps ?? 0)}K ↓{(sys.network?.inboundKbps ?? 0)}K</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* API 端点排行 */}
      <div className="bg-slate-800/50 rounded-xl p-5 border border-slate-700/30">
        <h3 className="text-sm font-medium text-slate-300 mb-4">API 端点排行</h3>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-slate-400 text-xs border-b border-slate-700/30">
                <th className="text-left py-2 px-3">端点</th>
                <th className="text-right py-2 px-3">调用次数</th>
                <th className="text-right py-2 px-3">平均耗时</th>
                <th className="text-right py-2 px-3">P99</th>
                <th className="text-right py-2 px-3">错误率</th>
              </tr>
            </thead>
            <tbody>
              {endpoints.sort((a, b) => b.calls - a.calls).map((ep, i) => (
                <tr key={i} className="border-b border-slate-700/10 hover:bg-slate-700/20 transition-colors">
                  <td className="py-2.5 px-3">
                    <span className={`inline-block px-1.5 py-0.5 rounded text-[10px] font-mono mr-2
                      ${ep.method === 'GET' ? 'bg-green-500/10 text-green-400' : 'bg-blue-500/10 text-blue-400'}`}>
                      {ep.method}
                    </span>
                    <span className="text-slate-300 font-mono text-xs">{ep.path}</span>
                  </td>
                  <td className="text-right py-2.5 px-3 text-slate-300 font-mono">{fmt(ep.calls)}</td>
                  <td className="text-right py-2.5 px-3 text-slate-300 font-mono">{fmtMs(ep.avgMs)}</td>
                  <td className="text-right py-2.5 px-3 font-mono">
                    <span className={(ep.p99Ms ?? 0) > 5000 ? 'text-red-400' : (ep.p99Ms ?? 0) > 1000 ? 'text-yellow-400' : 'text-slate-300'}>
                      {fmtMs(ep.p99Ms)}
                    </span>
                  </td>
                  <td className="text-right py-2.5 px-3 font-mono">
                    <span className={(ep.errorRate ?? 0) > 2 ? 'text-red-400' : (ep.errorRate ?? 0) > 1 ? 'text-yellow-400' : 'text-green-400'}>
                      {(ep.errorRate ?? 0)}%
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

// ============ 错误追踪 Tab ============
function ErrorsTab({ errors, expandedError, setExpandedError }: { errors: ErrorItem[]; expandedError: string | null; setExpandedError: (id: string | null) => void }) {
  const unresolved = errors.filter(e => e.status === 'unresolved');
  const critical = errors.filter(e => e.severity === 'critical' || e.severity === 'high');

  return (
    <div className="space-y-6">
      {/* 错误统计 */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="bg-slate-800/50 rounded-xl p-4 border border-red-500/20">
          <div className="text-sm text-slate-400 mb-1">未解决</div>
          <div className="text-2xl font-bold text-red-400 font-mono">{unresolved.length}</div>
        </div>
        <div className="bg-slate-800/50 rounded-xl p-4 border border-orange-500/20">
          <div className="text-sm text-slate-400 mb-1">严重/高危</div>
          <div className="text-2xl font-bold text-orange-400 font-mono">{critical.length}</div>
        </div>
        <div className="bg-slate-800/50 rounded-xl p-4 border border-slate-700/30">
          <div className="text-sm text-slate-400 mb-1">总错误类型</div>
          <div className="text-2xl font-bold text-slate-100 font-mono">{errors.length}</div>
        </div>
        <div className="bg-slate-800/50 rounded-xl p-4 border border-slate-700/30">
          <div className="text-sm text-slate-400 mb-1">总发生次数</div>
          <div className="text-2xl font-bold text-slate-100 font-mono">{fmt(errors.reduce((s, e) => s + (e.occurrences ?? 1), 0))}</div>
        </div>
      </div>

      {/* 错误列表 */}
      <div className="bg-slate-800/50 rounded-xl border border-slate-700/30 overflow-hidden">
        <div className="px-5 py-3 border-b border-slate-700/30">
          <h3 className="text-sm font-medium text-slate-300">错误详情</h3>
        </div>
        <div className="divide-y divide-slate-700/20">
          {errors.map(err => (
            <div key={err.id}>
              <button
                onClick={() => setExpandedError(expandedError === err.id ? null : err.id)}
                className="w-full px-5 py-3.5 flex items-start gap-3 hover:bg-slate-700/20 transition-colors text-left"
              >
                <div className={`mt-0.5 px-2 py-0.5 rounded text-[10px] font-semibold border ${severityColor(err.severity)}`}>
                  {(err.severity || 'low').toUpperCase()}
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 mb-1">
                    <span className="text-sm text-slate-200 font-medium truncate">{err.message}</span>
                    {err.status === 'resolved' && (
                      <span className="px-1.5 py-0.5 rounded text-[10px] bg-green-500/10 text-green-400 border border-green-500/20">已解决</span>
                    )}
                  </div>
                  <div className="flex items-center gap-3 text-xs text-slate-500">
                    <span className="font-mono">{err.endpoint}</span>
                    <span>·</span>
                    <span>{err.type}</span>
                    <span>·</span>
                    <span>{fmt(err.occurrences)} 次</span>
                    <span>·</span>
                    <span>最近 {timeAgo(err.lastSeen)}</span>
                  </div>
                </div>
                {expandedError === err.id ? <ChevronUp className="w-4 h-4 text-slate-500" /> : <ChevronDown className="w-4 h-4 text-slate-500" />}
              </button>
              {expandedError === err.id && (
                <div className="px-5 pb-4 ml-9 space-y-3">
                  <div className="bg-slate-900/80 rounded-lg p-3 border border-slate-700/30">
                    <div className="text-xs text-slate-400 mb-1">堆栈追踪</div>
                    <pre className="text-xs text-red-300/80 font-mono whitespace-pre-wrap">{err.stack || '暂无堆栈信息'}</pre>
                  </div>
                  <div className="grid grid-cols-3 gap-3 text-xs">
                    <div className="bg-slate-900/50 rounded-lg p-2.5">
                      <div className="text-slate-500 mb-0.5">HTTP 状态码</div>
                      <div className="text-slate-200 font-mono">{err.statusCode ?? '-'}</div>
                    </div>
                    <div className="bg-slate-900/50 rounded-lg p-2.5">
                      <div className="text-slate-500 mb-0.5">首次出现</div>
                      <div className="text-slate-200">{timeAgo(err.firstSeen)}</div>
                    </div>
                    <div className="bg-slate-900/50 rounded-lg p-2.5">
                      <div className="text-slate-500 mb-0.5">最近出现</div>
                      <div className="text-slate-200">{timeAgo(err.lastSeen)}</div>
                    </div>
                  </div>
                  {err.status === 'unresolved' && (
                    <button
                      onClick={async () => {
                        await fetch('/api/ops/errors', { method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ errorId: err.id, status: 'resolved' }) });
                      }}
                      className="text-xs px-3 py-1.5 rounded-lg bg-green-500/10 text-green-400 border border-green-500/20 hover:bg-green-500/20 transition-colors"
                    >
                      标记为已解决
                    </button>
                  )}
                </div>
              )}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

// ============ 性能指标 Tab ============
function PerformanceTab({ perf }: { perf: PerformanceData | null }) {
  if (!perf) return (
    <div className="flex flex-col items-center justify-center py-20 text-slate-400">
      <Zap className="w-10 h-10 mb-3 text-slate-600" />
      <p className="text-sm">后端服务未连接，性能指标数据暂不可用</p>
    </div>
  );
  const rawPerf = perf || {};
  const services = (rawPerf.services || []).map((s: any) => ({ responseTime: { p50: 0, p99: 0 }, errorRate: 0, throughput: 0, activeConnections: 0, ...s }));
  const slowQueries = (rawPerf.slowQueries || []).map((q: any) => ({ duration: 0, ...q }));
  const runtime = rawPerf.runtime || { jvm: { heapUsedMb: 0, heapMaxMb: 0, gcPauseMs: 0, threadCount: 0, peakThreadCount: 0 }, node: { rssMb: 0, heapUsedMb: 0, heapTotalMb: 0, externalMb: 0, arrayBuffersMb: 0 }, database: { activeConnections: 0, maxConnections: 0, waitingConnections: 0, avgQueryMs: 0, slowQueryCount: 0 } };
  const lastUpdated = rawPerf.lastUpdated || '';

  return (
    <div className="space-y-6">
      {/* 服务状态 */}
      <div className="bg-slate-800/50 rounded-xl p-5 border border-slate-700/30">
        <h3 className="text-sm font-medium text-slate-300 mb-4">服务状态</h3>
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
          {services.map((svc, i) => (
            <div key={i} className="bg-slate-900/50 rounded-lg p-4 border border-slate-700/20">
              <div className="flex items-center justify-between mb-3">
                <div className="flex items-center gap-2">
                  {statusIcon(svc.status)}
                  <span className="text-sm font-medium text-slate-200">{svc.name}</span>
                </div>
                <span className={`text-[10px] px-1.5 py-0.5 rounded border
                  ${(svc.status ?? 'warning') === 'healthy' ? 'text-green-400 bg-green-500/10 border-green-500/20' : 'text-yellow-400 bg-yellow-500/10 border-yellow-500/20'}`}>
                  {(svc.status ?? 'unknown').toUpperCase()}
                </span>
              </div>
              <div className="grid grid-cols-2 gap-2 text-xs">
                <div><span className="text-slate-500">P50</span> <span className="text-slate-300 font-mono ml-1">{fmtMs(svc.responseTime?.p50 ?? 0)}</span></div>
                <div><span className="text-slate-500">P99</span> <span className="text-slate-300 font-mono ml-1">{fmtMs(svc.responseTime?.p99 ?? 0)}</span></div>
                <div><span className="text-slate-500">吞吐</span> <span className="text-slate-300 font-mono ml-1">{svc.throughput ?? 0}/s</span></div>
                <div><span className="text-slate-500">错误率</span> <span className={`font-mono ml-1 ${(svc.errorRate ?? 0) > 2 ? 'text-red-400' : (svc.errorRate ?? 0) > 1 ? 'text-yellow-400' : 'text-green-400'}`}>{svc.errorRate ?? 0}%</span></div>
                <div className="col-span-2">
                  <span className="text-slate-500">连接</span>
                  <span className="text-slate-300 font-mono ml-1">{svc.connections ?? 0}/{svc.maxConnections ?? 0}</span>
                  <div className="h-1 bg-slate-700/50 rounded-full mt-1 overflow-hidden">
                    <div className={`h-full rounded-full ${((svc.connections ?? 0) / Math.max((svc.maxConnections ?? 1), 1)) > 0.8 ? 'bg-red-500' : 'bg-blue-500'}`} style={{ width: `${Math.min(((svc.connections ?? 0) / Math.max((svc.maxConnections ?? 1), 1)) * 100, 100)}%` }} />
                  </div>
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* 慢查询 + 运行时 */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        {/* 慢查询 Top10 */}
        <div className="bg-slate-800/50 rounded-xl p-5 border border-slate-700/30">
          <h3 className="text-sm font-medium text-slate-300 mb-4">慢查询 Top 10</h3>
          <div className="space-y-2">
            {slowQueries.map((q, i) => (
              <div key={i} className="flex items-center gap-3 text-xs group">
                <span className="text-slate-600 w-4 text-right">{i + 1}</span>
                <span className="text-slate-300 font-mono flex-1 truncate group-hover:text-blue-400 transition-colors">{q.endpoint}</span>
                <span className={`font-mono ${q.avgMs > 3000 ? 'text-red-400' : q.avgMs > 500 ? 'text-yellow-400' : 'text-slate-400'}`}>{fmtMs(q.avgMs)}</span>
                {q.aiMs > 0 && <span className="text-purple-400/60 text-[10px]">AI:{fmtMs(q.aiMs)}</span>}
                {q.dbMs > 0 && <span className="text-green-400/60 text-[10px]">DB:{fmtMs(q.dbMs)}</span>}
              </div>
            ))}
          </div>
        </div>

        {/* 运行时指标 */}
        <div className="bg-slate-800/50 rounded-xl p-5 border border-slate-700/30">
          <h3 className="text-sm font-medium text-slate-300 mb-4">运行时指标</h3>
          <div className="space-y-4">
            {/* JVM */}
            <div>
              <div className="text-xs text-blue-400 font-medium mb-2">Java JVM</div>
              <div className="grid grid-cols-2 gap-2 text-xs">
                <div className="bg-slate-900/50 rounded-lg p-2.5">
                  <div className="text-slate-500">堆内存</div>
                  <div className="text-slate-200 font-mono">{(runtime.jvm?.heapUsedMb ?? 0)}MB / {(runtime.jvm?.heapMaxMb ?? 0)}MB</div>
                  <div className="h-1.5 bg-slate-700/50 rounded-full mt-1.5 overflow-hidden">
                    <div className="h-full bg-blue-500 rounded-full" style={{ width: `${((runtime.jvm?.heapUsedMb ?? 0) / Math.max((runtime.jvm?.heapMaxMb ?? 1), 1)) * 100}%` }} />
                  </div>
                </div>
                <div className="bg-slate-900/50 rounded-lg p-2.5">
                  <div className="text-slate-500">GC 暂停</div>
                  <div className="text-slate-200 font-mono">{(runtime.jvm?.gcPauseMs ?? 0)}ms</div>
                </div>
                <div className="bg-slate-900/50 rounded-lg p-2.5">
                  <div className="text-slate-500">线程数</div>
                  <div className="text-slate-200 font-mono">{(runtime.jvm?.threadCount ?? 0)} <span className="text-slate-500">/ 峰值 {(runtime.jvm?.peakThreadCount ?? 0)}</span></div>
                </div>
              </div>
            </div>
            {/* Node.js */}
            <div>
              <div className="text-xs text-green-400 font-medium mb-2">Node.js</div>
              <div className="grid grid-cols-2 gap-2 text-xs">
                <div className="bg-slate-900/50 rounded-lg p-2.5">
                  <div className="text-slate-500">RSS</div>
                  <div className="text-slate-200 font-mono">{(runtime.node?.rssMb ?? 0)}MB</div>
                </div>
                <div className="bg-slate-900/50 rounded-lg p-2.5">
                  <div className="text-slate-500">堆内存</div>
                  <div className="text-slate-200 font-mono">{(runtime.node?.heapUsedMb ?? 0)}MB / {(runtime.node?.heapTotalMb ?? 0)}MB</div>
                </div>
              </div>
            </div>
            {/* Database */}
            <div>
              <div className="text-xs text-cyan-400 font-medium mb-2">数据库</div>
              <div className="grid grid-cols-3 gap-2 text-xs">
                <div className="bg-slate-900/50 rounded-lg p-2.5">
                  <div className="text-slate-500">活跃连接</div>
                  <div className="text-slate-200 font-mono">{(runtime.database?.activeConnections ?? 0)}/{(runtime.database?.maxConnections ?? 0)}</div>
                </div>
                <div className="bg-slate-900/50 rounded-lg p-2.5">
                  <div className="text-slate-500">平均查询</div>
                  <div className="text-slate-200 font-mono">{(runtime.database?.avgQueryMs ?? 0)}ms</div>
                </div>
                <div className="bg-slate-900/50 rounded-lg p-2.5">
                  <div className="text-slate-500">慢查询</div>
                  <div className={`font-mono ${(runtime.database?.slowQueryCount ?? 0) > 0 ? 'text-yellow-400' : 'text-slate-200'}`}>{(runtime.database?.slowQueryCount ?? 0)}</div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

// ============ 操作审计 Tab ============
function AuditTab({ logs }: { logs: AuditLog[] }) {
  const actionIcons: Record<string, React.ReactNode> = {
    login: <Lock className="w-3.5 h-3.5" />,
    logout: <XCircle className="w-3.5 h-3.5" />,
    upload: <ArrowUpDown className="w-3.5 h-3.5" />,
    delete: <Trash2 className="w-3.5 h-3.5" />,
    download: <Download className="w-3.5 h-3.5" />,
    share: <Globe className="w-3.5 h-3.5" />,
    create_album: <FileText className="w-3.5 h-3.5" />,
    delete_album: <Trash2 className="w-3.5 h-3.5" />,
  };

  const actionColor: Record<string, string> = {
    login: 'text-blue-400 bg-blue-500/10',
    logout: 'text-slate-400 bg-slate-500/10',
    upload: 'text-green-400 bg-green-500/10',
    delete: 'text-red-400 bg-red-500/10',
    download: 'text-cyan-400 bg-cyan-500/10',
    share: 'text-purple-400 bg-purple-500/10',
    create_album: 'text-blue-400 bg-blue-500/10',
    delete_album: 'text-red-400 bg-red-500/10',
  };

  return (
    <div className="space-y-4">
      <div className="bg-slate-800/50 rounded-xl border border-slate-700/30 overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-slate-400 text-xs border-b border-slate-700/30">
                <th className="text-left py-3 px-4">操作</th>
                <th className="text-left py-3 px-4">用户</th>
                <th className="text-left py-3 px-4">详情</th>
                <th className="text-left py-3 px-4">资源</th>
                <th className="text-left py-3 px-4">IP</th>
                <th className="text-left py-3 px-4">时间</th>
              </tr>
            </thead>
            <tbody>
              {logs.map((log, i) => (
                <tr key={i} className="border-b border-slate-700/10 hover:bg-slate-700/20 transition-colors">
                  <td className="py-2.5 px-4">
                    <span className={`inline-flex items-center gap-1.5 px-2 py-1 rounded text-xs ${actionColor[log.action || ''] || 'text-slate-400 bg-slate-500/10'}`}>
                      {actionIcons[log.action || ''] || <Activity className="w-3.5 h-3.5" />}
                      {log.action || '-'}
                    </span>
                  </td>
                  <td className="py-2.5 px-4 text-slate-300 text-xs">{log.username || '-'}</td>
                  <td className="py-2.5 px-4 text-slate-400 text-xs max-w-[300px] truncate">{log.details || '-'}</td>
                  <td className="py-2.5 px-4 text-slate-400 text-xs font-mono">{(log.resourceType || '')}#{(log.resourceId || '')}</td>
                  <td className="py-2.5 px-4 text-slate-500 text-xs font-mono">{log.ipAddress || '-'}</td>
                  <td className="py-2.5 px-4 text-slate-500 text-xs">{timeAgo(log.createdAt || '')}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

// ============ 备份管理 Tab ============
function BackupTab({ backups, onRefresh }: { backups: BackupItem[]; onRefresh: () => void }) {
  const [creating, setCreating] = useState(false);

  const typeLabel: Record<string, { label: string; color: string }> = {
    full: { label: '全量备份', color: 'text-blue-400 bg-blue-500/10' },
    images: { label: '图片备份', color: 'text-green-400 bg-green-500/10' },
    database: { label: '数据库', color: 'text-cyan-400 bg-cyan-500/10' },
    settings: { label: '配置备份', color: 'text-purple-400 bg-purple-500/10' },
  };

  return (
    <div className="space-y-6">
      {/* 操作栏 */}
      <div className="flex items-center justify-between">
        <div className="text-sm text-slate-400">共 {backups.length} 个备份记录</div>
        <button
          onClick={async () => {
            setCreating(true);
            // 创建备份
            const res = await fetch('/api/backup', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ type: 'full' }) });
            if (!res.ok) console.error('Backup creation failed');
            setCreating(false);
            onRefresh();
          }}
          disabled={creating}
          className="flex items-center gap-2 px-4 py-2 rounded-lg bg-gradient-to-r from-blue-600 to-cyan-500 text-white text-sm font-medium hover:shadow-lg hover:shadow-blue-500/20 transition-all disabled:opacity-50"
        >
          {creating ? <RefreshCw className="w-4 h-4 animate-spin" /> : <Download className="w-4 h-4" />}
          {creating ? '创建中...' : '创建备份'}
        </button>
      </div>

      {/* 备份列表 */}
      <div className="bg-slate-800/50 rounded-xl border border-slate-700/30 overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="text-slate-400 text-xs border-b border-slate-700/30">
              <th className="text-left py-3 px-5">备份名称</th>
              <th className="text-left py-3 px-5">类型</th>
              <th className="text-right py-3 px-5">大小</th>
              <th className="text-left py-3 px-5">状态</th>
              <th className="text-left py-3 px-5">创建时间</th>
              <th className="text-right py-3 px-5">操作</th>
            </tr>
          </thead>
          <tbody>
            {backups.map((bk, i) => {
              const tl = typeLabel[bk.type || ''] || { label: bk.type || 'unknown', color: 'text-slate-400 bg-slate-500/10' };
              return (
                <tr key={i} className="border-b border-slate-700/10 hover:bg-slate-700/20 transition-colors">
                  <td className="py-2.5 px-5 text-slate-200">{bk.name || '未命名'}</td>
                  <td className="py-2.5 px-5">
                    <span className={`px-2 py-0.5 rounded text-xs ${tl.color}`}>{tl.label}</span>
                  </td>
                  <td className="py-2.5 px-5 text-right text-slate-300 font-mono">{fmtSize(bk.size)}</td>
                  <td className="py-2.5 px-5">
                    {bk.status === 'completed' ? (
                      <span className="flex items-center gap-1 text-green-400 text-xs"><CheckCircle2 className="w-3.5 h-3.5" /> 完成</span>
                    ) : (
                      <span className="flex items-center gap-1 text-yellow-400 text-xs"><RefreshCw className="w-3.5 h-3.5 animate-spin" /> 进行中</span>
                    )}
                  </td>
                  <td className="py-2.5 px-5 text-slate-400 text-xs">{timeAgo(bk.createdAt)}</td>
                  <td className="py-2.5 px-5 text-right">
                    <div className="flex items-center justify-end gap-1">
                      <button className="p-1.5 rounded hover:bg-slate-700/50 text-slate-500 hover:text-blue-400 transition-colors" title="恢复">
                        <RotateCcw className="w-3.5 h-3.5" />
                      </button>
                      <button className="p-1.5 rounded hover:bg-slate-700/50 text-slate-500 hover:text-green-400 transition-colors" title="下载">
                        <Download className="w-3.5 h-3.5" />
                      </button>
                      <button className="p-1.5 rounded hover:bg-slate-700/50 text-slate-500 hover:text-red-400 transition-colors" title="删除">
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    </div>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}

// ============ 用户管理 Tab ============
function UsersTab() {
  const [users, setUsers] = useState<Array<{
    id: string; username: string; role: string; email: string;
    status: string; lastLogin: string; createdAt: string;
  }>>([]);

  useEffect(() => {
    // 加载真实用户数据
    fetch('/api/users')
      .then(res => res.json())
      .then(data => {
        if (data.success && data.users) {
          setUsers(data.users);
        }
      })
      .catch(err => console.error('Failed to load users:', err));
  }, []);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div className="text-sm text-slate-400">共 {users.length} 个用户</div>
        <button className="flex items-center gap-2 px-4 py-2 rounded-lg bg-gradient-to-r from-blue-600 to-cyan-500 text-white text-sm font-medium hover:shadow-lg hover:shadow-blue-500/20 transition-all">
          <UserCheck className="w-4 h-4" />
          添加用户
        </button>
      </div>

      <div className="bg-slate-800/50 rounded-xl border border-slate-700/30 overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="text-slate-400 text-xs border-b border-slate-700/30">
              <th className="text-left py-3 px-5">用户名</th>
              <th className="text-left py-3 px-5">角色</th>
              <th className="text-left py-3 px-5">邮箱</th>
              <th className="text-left py-3 px-5">状态</th>
              <th className="text-left py-3 px-5">最近登录</th>
              <th className="text-right py-3 px-5">操作</th>
            </tr>
          </thead>
          <tbody>
            {users.map(user => (
              <tr key={user.id} className="border-b border-slate-700/10 hover:bg-slate-700/20 transition-colors">
                <td className="py-2.5 px-5">
                  <div className="flex items-center gap-2.5">
                    <div className={`w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold
                      ${user.role === 'admin' ? 'bg-gradient-to-br from-blue-600 to-cyan-500 text-white' : 'bg-slate-700 text-slate-300'}`}>
                      {user.username?.[0]?.toUpperCase() ?? '?'}
                    </div>
                    <span className="text-slate-200">{user.username}</span>
                  </div>
                </td>
                <td className="py-2.5 px-5">
                  <span className={`px-2 py-0.5 rounded text-xs
                    ${user.role === 'admin' ? 'text-blue-400 bg-blue-500/10' : 'text-slate-400 bg-slate-500/10'}`}>
                    {user.role === 'admin' ? '管理员' : '普通用户'}
                  </span>
                </td>
                <td className="py-2.5 px-5 text-slate-400 text-xs">{user.email}</td>
                <td className="py-2.5 px-5">
                  <span className={`flex items-center gap-1 text-xs ${user.status === 'active' ? 'text-green-400' : 'text-slate-500'}`}>
                    <span className={`w-1.5 h-1.5 rounded-full ${user.status === 'active' ? 'bg-green-400' : 'bg-slate-600'}`} />
                    {user.status === 'active' ? '活跃' : '停用'}
                  </span>
                </td>
                <td className="py-2.5 px-5 text-slate-400 text-xs">{timeAgo(user.lastLogin)}</td>
                <td className="py-2.5 px-5 text-right">
                  <div className="flex items-center justify-end gap-1">
                    <button className="p-1.5 rounded hover:bg-slate-700/50 text-slate-500 hover:text-blue-400 transition-colors" title="编辑">
                      <Eye className="w-3.5 h-3.5" />
                    </button>
                    <button className="p-1.5 rounded hover:bg-slate-700/50 text-slate-500 hover:text-yellow-400 transition-colors" title="重置密码">
                      <Lock className="w-3.5 h-3.5" />
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
