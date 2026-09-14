'use client';

import { useState, useEffect, useCallback } from 'react';
import {
  Activity, AlertTriangle, AlertCircle, CheckCircle2, Clock,
  Cpu, HardDrive, MemoryStick, Network, RefreshCw,
  Shield, Users, Zap, Server,
  Database, ArrowUpDown, ChevronDown, ChevronUp, Eye,
  Download, Trash2, RotateCcw, XCircle, Bug, FileText,
  Globe, Lock, UserCheck,
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

type TabKey = 'monitor' | 'errors' | 'performance' | 'audit' | 'backup' | 'users' | 'storage';

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
  if (s === 'critical') return 'text-[#ff3b30] bg-[rgba(255,59,48,0.1)] border-[rgba(255,59,48,0.2)]';
  if (s === 'high') return 'text-[#ff9500] bg-[rgba(255,149,0,0.1)] border-[rgba(255,149,0,0.2)]';
  if (s === 'medium') return 'text-[#ff9500] bg-[rgba(255,149,0,0.1)] border-[rgba(255,149,0,0.2)]';
  return 'text-[#007aff] bg-[rgba(0,122,255,0.1)] border-[rgba(0,122,255,0.2)]';
};
const statusIcon = (s: string) => {
  if (s === 'healthy') return <CheckCircle2 className="w-3.5 h-3.5 text-[#34c759]" />;
  if (s === 'degraded') return <AlertTriangle className="w-3.5 h-3.5 text-[#ff9500]" />;
  return <AlertCircle className="w-3.5 h-3.5 text-[#ff3b30]" />;
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
      if (aData?.success) setAuditLogs(Array.isArray(aData.data) ? aData.data : (aData.logs || [])); else failedApis.push('操作审计');
      if (bData?.success) setBackups(Array.isArray(bData.data) ? bData.data : (bData.backups || bData.data?.backups || [])); else failedApis.push('备份管理');

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
    { key: 'storage', label: '存储测试', icon: <HardDrive className="w-4 h-4" /> },
    { key: 'users', label: '用户管理', icon: <Users className="w-4 h-4" /> },
  ];

  return (
    <div className="h-screen flex flex-col bg-[#F2F2F7] text-[#1c1c1e] overflow-hidden">
      {/* 顶部栏 */}
      <div className="flex items-center justify-between px-6 py-4 border-b border-[rgba(229,229,234,0.5)] bg-white">
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-lg bg-[#007AFF] flex items-center justify-center">
            <Server className="w-5 h-5 text-[#1C1C1E]" />
          </div>
          <div>
            <h1 className="text-lg font-semibold text-[#1c1c1e]">系统运维中心</h1>
            <p className="text-xs text-[#8e8e93]">API监控 · 错误追踪 · 性能指标 · 操作审计 · 备份管理</p>
          </div>
        </div>
        <div className="flex items-center gap-3">
          <span className="text-xs text-[#8e8e93]">
            {metrics?.lastUpdated ? `更新于 ${timeAgo(metrics.lastUpdated)}` : '加载中...'}
          </span>
          <button onClick={fetchData} className="p-2 rounded-lg hover:bg-[rgba(118,118,128,0.12)] text-[#8e8e93] hover:text-[#1c1c1e] transition-colors">
            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
          </button>
        </div>
      </div>

      {/* Tab 导航 */}
      <div className="flex items-center gap-1 px-6 py-2 border-b border-[rgba(229,229,234,0.3)] bg-[rgba(242,242,247,0.5)] overflow-x-auto">
        {tabs.map(tab => (
          <button
            key={tab.key}
            onClick={() => setActiveTab(tab.key)}
            className={`flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-all whitespace-nowrap
              ${activeTab === tab.key
                ? 'bg-[rgba(0,122,255,0.15)] text-[#007aff] border border-[rgba(0,122,255,0.2)]'
                : 'text-[#8e8e93] hover:text-[#1c1c1e] hover:bg-[rgba(0,0,0,0.015)] border border-transparent'
              }`}
          >
            {tab.icon}
            {tab.label}
          </button>
        ))}
      </div>

      {/* 服务连接错误提示 */}
      {apiError && (
        <div className="px-6 py-3 bg-[rgba(255,149,0,0.1)] border-b border-[rgba(255,149,0,0.2)] flex items-center gap-2">
          <AlertTriangle className="w-4 h-4 text-[#ff9500] shrink-0" />
          <span className="text-sm text-[#ff9500]">{apiError}</span>
          <button onClick={fetchData} className="ml-auto text-xs text-[#ff9500] hover:text-[#ff9500] underline">重试</button>
        </div>
      )}

      {/* 内容区 */}
      <div className="flex-1 overflow-y-auto p-6">
        {loading && !metrics ? (
          <div className="flex items-center justify-center h-64">
            <RefreshCw className="w-8 h-8 text-[#007aff] animate-spin" />
          </div>
        ) : (
          <>
            {activeTab === 'monitor' && <MonitorTab metrics={metrics} />}
            {activeTab === 'errors' && <ErrorsTab errors={errors || []} expandedError={expandedError} setExpandedError={setExpandedError} />}
            {activeTab === 'performance' && <PerformanceTab perf={perf} />}
            {activeTab === 'audit' && <AuditTab logs={auditLogs || []} />}
            {activeTab === 'backup' && <BackupTab backups={backups || []} onRefresh={fetchData} />}
            {activeTab === 'storage' && <StorageTestTab />}
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
    <div className="flex flex-col items-center justify-center py-20 text-[#8e8e93]">
      <Server className="w-10 h-10 mb-3 text-[#8e8e93]" />
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
          <div key={i} className="bg-white rounded-xl p-4 border border-[rgba(229,229,234,0.3)] hover:border-[rgba(0,122,255,0.2)] transition-all">
            <div className="flex items-center justify-between mb-2">
              <span className="text-sm text-[#8e8e93]">{kpi.label}</span>
              <div className={`text-${kpi.color}-400`}>{kpi.icon}</div>
            </div>
            <div className="text-2xl font-bold text-[#1c1c1e] font-mono">{kpi.value}</div>
            <div className="text-xs text-[#8e8e93] mt-1">{kpi.sub}</div>
          </div>
        ))}
      </div>

      {/* 请求量趋势 + 系统资源 */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        {/* 24小时请求趋势 */}
        <div className="lg:col-span-2 bg-white rounded-xl p-5 border border-[rgba(229,229,234,0.3)]">
          <h3 className="text-sm font-medium text-[#3a3a3c] mb-4">24小时请求趋势</h3>
          <div className="flex items-end gap-[3px] h-48">
            {hourlyRequests.map((h, i) => (
              <div key={i} className="flex-1 flex flex-col items-center gap-0.5 group relative">
                <div className="w-full relative flex-1 flex items-end">
                  <div
                    className="w-full bg-[rgba(0,122,255,0.2)] rounded-t-sm group-hover:bg-[rgba(0,122,255,0.3)] transition-colors"
                    style={{ height: `${(h.success / maxRequests) * 100}%` }}
                  />
                  <div
                    className="w-full bg-[rgba(255,59,48,0.4)] rounded-t-sm absolute bottom-0"
                    style={{ height: `${(h.error / maxRequests) * 100}%` }}
                  />
                </div>
                {/* Hover tooltip */}
                <div className="absolute -top-16 left-1/2 -translate-x-1/2 hidden group-hover:block bg-[rgba(118,118,128,0.12)] rounded-lg px-2 py-1 text-xs whitespace-nowrap z-10 shadow-lg">
                  <div className="text-[#1c1c1e]">{h.hour}</div>
                  <div className="text-[#007aff]">成功: {h.success}</div>
                  <div className="text-[#ff3b30]">错误: {h.error}</div>
                  <div className="text-[#8e8e93]">均值: {h.avgResponseTime}ms</div>
                </div>
                {i % 4 === 0 && <span className="text-[9px] text-[#8e8e93] mt-1">{h.hour}</span>}
              </div>
            ))}
          </div>
          <div className="flex items-center gap-4 mt-3 text-xs text-[#8e8e93]">
            <span className="flex items-center gap-1"><span className="w-3 h-2 bg-[rgba(0,122,255,0.3)] rounded-sm" /> 成功</span>
            <span className="flex items-center gap-1"><span className="w-3 h-2 bg-[rgba(255,59,48,0.4)] rounded-sm" /> 错误</span>
          </div>
        </div>

        {/* 系统资源 */}
        <div className="bg-white rounded-xl p-5 border border-[rgba(229,229,234,0.3)]">
          <h3 className="text-sm font-medium text-[#3a3a3c] mb-4">系统资源</h3>
          <div className="space-y-4">
            {/* CPU */}
            <div>
              <div className="flex items-center justify-between text-xs mb-1.5">
                <span className="text-[#8e8e93] flex items-center gap-1.5"><Cpu className="w-3.5 h-3.5" /> CPU</span>
                <span className="text-[#3a3a3c] font-mono">{(sys.cpu?.current ?? 0)}% <span className="text-[#8e8e93]">/ 峰值 {(sys.cpu?.peak ?? 0)}%</span></span>
              </div>
              <div className="h-2 bg-[rgba(118,118,128,0.12)] rounded-full overflow-hidden">
                <div className={`h-full rounded-full transition-all ${(sys.cpu?.current ?? 0) > 70 ? 'bg-[#ff3b30]' : (sys.cpu?.current ?? 0) > 50 ? 'bg-[#ff9500]' : 'bg-[#007aff]'}`} style={{ width: `${(sys.cpu?.current ?? 0)}%` }} />
              </div>
            </div>
            {/* Memory */}
            <div>
              <div className="flex items-center justify-between text-xs mb-1.5">
                <span className="text-[#8e8e93] flex items-center gap-1.5"><MemoryStick className="w-3.5 h-3.5" /> 内存</span>
                <span className="text-[#3a3a3c] font-mono">{(sys.memory?.usedMb ?? 0)}MB / {(sys.memory?.totalMb ?? 1)}MB</span>
              </div>
              <div className="h-2 bg-[rgba(118,118,128,0.12)] rounded-full overflow-hidden">
                <div className={`h-full rounded-full transition-all ${((sys.memory?.usedMb ?? 0)/(sys.memory?.totalMb ?? 1)*100) > 70 ? 'bg-[#ff3b30]' : ((sys.memory?.usedMb ?? 0)/(sys.memory?.totalMb ?? 1)*100) > 50 ? 'bg-[#ff9500]' : 'bg-[#007aff]'}`} style={{ width: `${Math.round((sys.memory?.usedMb ?? 0)/(sys.memory?.totalMb ?? 1)*100)}%` }} />
              </div>
            </div>
            {/* Disk */}
            <div>
              <div className="flex items-center justify-between text-xs mb-1.5">
                <span className="text-[#8e8e93] flex items-center gap-1.5"><HardDrive className="w-3.5 h-3.5" /> 磁盘</span>
                <span className="text-[#3a3a3c] font-mono">{(sys.disk?.usedGb ?? 0)}GB / {(sys.disk?.totalGb ?? 1)}GB</span>
              </div>
              <div className="h-2 bg-[rgba(118,118,128,0.12)] rounded-full overflow-hidden">
                <div className={`h-full rounded-full transition-all ${(sys.disk?.percentage ?? 0) > 80 ? 'bg-[#ff3b30]' : (sys.disk?.percentage ?? 0) > 60 ? 'bg-[#ff9500]' : 'bg-[#34c759]'}`} style={{ width: `${(sys.disk?.percentage ?? 0)}%` }} />
              </div>
            </div>
            {/* Network */}
            <div>
              <div className="flex items-center justify-between text-xs mb-1.5">
                <span className="text-[#8e8e93] flex items-center gap-1.5"><Network className="w-3.5 h-3.5" /> 网络</span>
                <span className="text-[#3a3a3c] font-mono">↑{(sys.network?.outboundKbps ?? 0)}K ↓{(sys.network?.inboundKbps ?? 0)}K</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* API 端点排行 */}
      <div className="bg-white rounded-xl p-5 border border-[rgba(229,229,234,0.3)]">
        <h3 className="text-sm font-medium text-[#3a3a3c] mb-4">API 端点排行</h3>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-[#8e8e93] text-xs border-b border-[rgba(229,229,234,0.3)]">
                <th className="text-left py-2 px-3">端点</th>
                <th className="text-right py-2 px-3">调用次数</th>
                <th className="text-right py-2 px-3">平均耗时</th>
                <th className="text-right py-2 px-3">P99</th>
                <th className="text-right py-2 px-3">错误率</th>
              </tr>
            </thead>
            <tbody>
              {endpoints.sort((a, b) => b.calls - a.calls).map((ep, i) => (
                <tr key={i} className="border-b border-[rgba(229,229,234,0.1)] hover:bg-[rgba(0,0,0,0.01)] transition-colors">
                  <td className="py-2.5 px-3">
                    <span className={`inline-block px-1.5 py-0.5 rounded text-[10px] font-mono mr-2
                      ${ep.method === 'GET' ? 'bg-[rgba(52,199,89,0.1)] text-[#34c759]' : 'bg-[rgba(0,122,255,0.1)] text-[#007aff]'}`}>
                      {ep.method}
                    </span>
                    <span className="text-[#3a3a3c] font-mono text-xs">{ep.path}</span>
                  </td>
                  <td className="text-right py-2.5 px-3 text-[#3a3a3c] font-mono">{fmt(ep.calls)}</td>
                  <td className="text-right py-2.5 px-3 text-[#3a3a3c] font-mono">{fmtMs(ep.avgMs)}</td>
                  <td className="text-right py-2.5 px-3 font-mono">
                    <span className={(ep.p99Ms ?? 0) > 5000 ? 'text-[#ff3b30]' : (ep.p99Ms ?? 0) > 1000 ? 'text-[#ff9500]' : 'text-[#3a3a3c]'}>
                      {fmtMs(ep.p99Ms)}
                    </span>
                  </td>
                  <td className="text-right py-2.5 px-3 font-mono">
                    <span className={(ep.errorRate ?? 0) > 2 ? 'text-[#ff3b30]' : (ep.errorRate ?? 0) > 1 ? 'text-[#ff9500]' : 'text-[#34c759]'}>
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
        <div className="bg-white rounded-xl p-4 border border-[rgba(255,59,48,0.2)]">
          <div className="text-sm text-[#8e8e93] mb-1">未解决</div>
          <div className="text-2xl font-bold text-[#ff3b30] font-mono">{unresolved.length}</div>
        </div>
        <div className="bg-white rounded-xl p-4 border border-[rgba(255,149,0,0.2)]">
          <div className="text-sm text-[#8e8e93] mb-1">严重/高危</div>
          <div className="text-2xl font-bold text-[#ff9500] font-mono">{critical.length}</div>
        </div>
        <div className="bg-white rounded-xl p-4 border border-[rgba(229,229,234,0.3)]">
          <div className="text-sm text-[#8e8e93] mb-1">总错误类型</div>
          <div className="text-2xl font-bold text-[#1c1c1e] font-mono">{errors.length}</div>
        </div>
        <div className="bg-white rounded-xl p-4 border border-[rgba(229,229,234,0.3)]">
          <div className="text-sm text-[#8e8e93] mb-1">总发生次数</div>
          <div className="text-2xl font-bold text-[#1c1c1e] font-mono">{fmt(errors.reduce((s, e) => s + (e.occurrences ?? 1), 0))}</div>
        </div>
      </div>

      {/* 错误列表 */}
      <div className="bg-white rounded-xl border border-[rgba(229,229,234,0.3)] overflow-hidden">
        <div className="px-5 py-3 border-b border-[rgba(229,229,234,0.3)]">
          <h3 className="text-sm font-medium text-[#3a3a3c]">错误详情</h3>
        </div>
        <div className="divide-y divide-[rgba(229,229,234,0.2)]">
          {errors.map(err => (
            <div key={err.id}>
              <button
                onClick={() => setExpandedError(expandedError === err.id ? null : err.id)}
                className="w-full px-5 py-3.5 flex items-start gap-3 hover:bg-[rgba(0,0,0,0.01)] transition-colors text-left"
              >
                <div className={`mt-0.5 px-2 py-0.5 rounded text-[10px] font-semibold border ${severityColor(err.severity)}`}>
                  {(err.severity || 'low').toUpperCase()}
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 mb-1">
                    <span className="text-sm text-[#1c1c1e] font-medium truncate">{err.message}</span>
                    {err.status === 'resolved' && (
                      <span className="px-1.5 py-0.5 rounded text-[10px] bg-[rgba(52,199,89,0.1)] text-[#34c759] border border-[rgba(52,199,89,0.2)]">已解决</span>
                    )}
                  </div>
                  <div className="flex items-center gap-3 text-xs text-[#8e8e93]">
                    <span className="font-mono">{err.endpoint}</span>
                    <span>·</span>
                    <span>{err.type}</span>
                    <span>·</span>
                    <span>{fmt(err.occurrences)} 次</span>
                    <span>·</span>
                    <span>最近 {timeAgo(err.lastSeen)}</span>
                  </div>
                </div>
                {expandedError === err.id ? <ChevronUp className="w-4 h-4 text-[#8e8e93]" /> : <ChevronDown className="w-4 h-4 text-[#8e8e93]" />}
              </button>
              {expandedError === err.id && (
                <div className="px-5 pb-4 ml-9 space-y-3">
                  <div className="bg-[rgba(242,242,247,0.8)] rounded-lg p-3 border border-[rgba(229,229,234,0.3)]">
                    <div className="text-xs text-[#8e8e93] mb-1">堆栈追踪</div>
                    <pre className="text-xs text-[rgba(255,59,48,0.8)] font-mono whitespace-pre-wrap">{err.stack || '暂无堆栈信息'}</pre>
                  </div>
                  <div className="grid grid-cols-3 gap-3 text-xs">
                    <div className="bg-[rgba(242,242,247,0.5)] rounded-lg p-2.5">
                      <div className="text-[#8e8e93] mb-0.5">HTTP 状态码</div>
                      <div className="text-[#1c1c1e] font-mono">{err.statusCode ?? '-'}</div>
                    </div>
                    <div className="bg-[rgba(242,242,247,0.5)] rounded-lg p-2.5">
                      <div className="text-[#8e8e93] mb-0.5">首次出现</div>
                      <div className="text-[#1c1c1e]">{timeAgo(err.firstSeen)}</div>
                    </div>
                    <div className="bg-[rgba(242,242,247,0.5)] rounded-lg p-2.5">
                      <div className="text-[#8e8e93] mb-0.5">最近出现</div>
                      <div className="text-[#1c1c1e]">{timeAgo(err.lastSeen)}</div>
                    </div>
                  </div>
                  {err.status === 'unresolved' && (
                    <button
                      onClick={async () => {
                        await fetch(`/api/ops/errors/${err.id}/resolve`, { method: 'PATCH' });
                      }}
                      className="text-xs px-3 py-1.5 rounded-lg bg-[rgba(52,199,89,0.1)] text-[#34c759] border border-[rgba(52,199,89,0.2)] hover:bg-[rgba(52,199,89,0.2)] transition-colors"
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
  const [nodeMetrics, setNodeMetrics] = useState<{ rssMb: number; heapUsedMb: number; heapTotalMb: number; externalMb: number; arrayBuffersMb: number } | null>(null);

  useEffect(() => {
    fetch('/api/ops/node-metrics')
      .then(r => r.json())
      .then(d => { if (d.success && d.data?.node) setNodeMetrics(d.data.node); })
      .catch(() => {});
  }, []);

  if (!perf) return (
    <div className="flex flex-col items-center justify-center py-20 text-[#8e8e93]">
      <Zap className="w-10 h-10 mb-3 text-[#8e8e93]" />
      <p className="text-sm">后端服务未连接，性能指标数据暂不可用</p>
    </div>
  );
  const rawPerf = perf || {};
  const services = (rawPerf.services || []).map((s: any) => ({ responseTime: { p50: 0, p99: 0 }, errorRate: 0, throughput: 0, activeConnections: 0, ...s }));
  const slowQueries = (rawPerf.slowQueries || []).map((q: any) => ({ duration: 0, ...q }));
  const defaultRuntime = { jvm: { heapUsedMb: 0, heapMaxMb: 0, gcPauseMs: 0, threadCount: 0, peakThreadCount: 0 }, node: { rssMb: 0, heapUsedMb: 0, heapTotalMb: 0, externalMb: 0, arrayBuffersMb: 0 }, database: { activeConnections: 0, maxConnections: 0, waitingConnections: 0, avgQueryMs: 0, slowQueryCount: 0 } };
  const runtime = { ...defaultRuntime, ...rawPerf.runtime, node: nodeMetrics || rawPerf.runtime?.node || defaultRuntime.node };
  const lastUpdated = rawPerf.lastUpdated || '';

  return (
    <div className="space-y-6">
      {/* 服务状态 */}
      <div className="bg-white rounded-xl p-5 border border-[rgba(229,229,234,0.3)]">
        <h3 className="text-sm font-medium text-[#3a3a3c] mb-4">服务状态</h3>
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
          {services.map((svc, i) => (
            <div key={i} className="bg-[rgba(242,242,247,0.5)] rounded-lg p-4 border border-[rgba(229,229,234,0.2)]">
              <div className="flex items-center justify-between mb-3">
                <div className="flex items-center gap-2">
                  {statusIcon(svc.status)}
                  <span className="text-sm font-medium text-[#1c1c1e]">{svc.name}</span>
                </div>
                <span className={`text-[10px] px-1.5 py-0.5 rounded border
                  ${(svc.status ?? 'warning') === 'healthy' ? 'text-[#34c759] bg-[rgba(52,199,89,0.1)] border-[rgba(52,199,89,0.2)]' : 'text-[#ff9500] bg-[rgba(255,149,0,0.1)] border-[rgba(255,149,0,0.2)]'}`}>
                  {(svc.status ?? 'unknown').toUpperCase()}
                </span>
              </div>
              <div className="grid grid-cols-2 gap-2 text-xs">
                <div><span className="text-[#8e8e93]">P50</span> <span className="text-[#3a3a3c] font-mono ml-1">{fmtMs(svc.responseTime?.p50 ?? 0)}</span></div>
                <div><span className="text-[#8e8e93]">P99</span> <span className="text-[#3a3a3c] font-mono ml-1">{fmtMs(svc.responseTime?.p99 ?? 0)}</span></div>
                <div><span className="text-[#8e8e93]">吞吐</span> <span className="text-[#3a3a3c] font-mono ml-1">{svc.throughput ?? 0}/s</span></div>
                <div><span className="text-[#8e8e93]">错误率</span> <span className={`font-mono ml-1 ${(svc.errorRate ?? 0) > 2 ? 'text-[#ff3b30]' : (svc.errorRate ?? 0) > 1 ? 'text-[#ff9500]' : 'text-[#34c759]'}`}>{svc.errorRate ?? 0}%</span></div>
                <div className="col-span-2">
                  <span className="text-[#8e8e93]">连接</span>
                  <span className="text-[#3a3a3c] font-mono ml-1">{svc.connections ?? 0}/{svc.maxConnections ?? 0}</span>
                  <div className="h-1 bg-[rgba(118,118,128,0.12)] rounded-full mt-1 overflow-hidden">
                    <div className={`h-full rounded-full ${((svc.connections ?? 0) / Math.max((svc.maxConnections ?? 1), 1)) > 0.8 ? 'bg-[#ff3b30]' : 'bg-[#007aff]'}`} style={{ width: `${Math.min(((svc.connections ?? 0) / Math.max((svc.maxConnections ?? 1), 1)) * 100, 100)}%` }} />
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
        <div className="bg-white rounded-xl p-5 border border-[rgba(229,229,234,0.3)]">
          <h3 className="text-sm font-medium text-[#3a3a3c] mb-4">慢查询 Top 10</h3>
          <div className="space-y-2">
            {slowQueries.map((q, i) => (
              <div key={i} className="flex items-center gap-3 text-xs group">
                <span className="text-[#8e8e93] w-4 text-right">{i + 1}</span>
                <span className="text-[#3a3a3c] font-mono flex-1 truncate group-hover:text-[#007aff] transition-colors">{q.endpoint}</span>
                <span className={`font-mono ${q.avgMs > 3000 ? 'text-[#ff3b30]' : q.avgMs > 500 ? 'text-[#ff9500]' : 'text-[#8e8e93]'}`}>{fmtMs(q.avgMs)}</span>
                {q.aiMs > 0 && <span className="text-[rgba(0,122,255,0.6)] text-[10px]">AI:{fmtMs(q.aiMs)}</span>}
                {q.dbMs > 0 && <span className="text-[rgba(52,199,89,0.6)] text-[10px]">DB:{fmtMs(q.dbMs)}</span>}
              </div>
            ))}
          </div>
        </div>

        {/* 运行时指标 */}
        <div className="bg-white rounded-xl p-5 border border-[rgba(229,229,234,0.3)]">
          <h3 className="text-sm font-medium text-[#3a3a3c] mb-4">运行时指标</h3>
          <div className="space-y-4">
            {/* JVM */}
            <div>
              <div className="text-xs text-[#007aff] font-medium mb-2">Java JVM</div>
              <div className="grid grid-cols-2 gap-2 text-xs">
                <div className="bg-[rgba(242,242,247,0.5)] rounded-lg p-2.5">
                  <div className="text-[#8e8e93]">堆内存</div>
                  <div className="text-[#1c1c1e] font-mono">{(runtime.jvm?.heapUsedMb ?? 0)}MB / {(runtime.jvm?.heapMaxMb ?? 0)}MB</div>
                  <div className="h-1.5 bg-[rgba(118,118,128,0.12)] rounded-full mt-1.5 overflow-hidden">
                    <div className="h-full bg-[#007aff] rounded-full" style={{ width: `${((runtime.jvm?.heapUsedMb ?? 0) / Math.max((runtime.jvm?.heapMaxMb ?? 1), 1)) * 100}%` }} />
                  </div>
                </div>
                <div className="bg-[rgba(242,242,247,0.5)] rounded-lg p-2.5">
                  <div className="text-[#8e8e93]">GC 暂停</div>
                  <div className="text-[#1c1c1e] font-mono">{(runtime.jvm?.gcPauseMs ?? 0)}ms</div>
                </div>
                <div className="bg-[rgba(242,242,247,0.5)] rounded-lg p-2.5">
                  <div className="text-[#8e8e93]">线程数</div>
                  <div className="text-[#1c1c1e] font-mono">{(runtime.jvm?.threadCount ?? 0)} <span className="text-[#8e8e93]">/ 峰值 {(runtime.jvm?.peakThreadCount ?? 0)}</span></div>
                </div>
              </div>
            </div>
            {/* Node.js */}
            <div>
              <div className="text-xs text-[#34c759] font-medium mb-2">Node.js</div>
              <div className="grid grid-cols-2 gap-2 text-xs">
                <div className="bg-[rgba(242,242,247,0.5)] rounded-lg p-2.5">
                  <div className="text-[#8e8e93]">RSS</div>
                  <div className="text-[#1c1c1e] font-mono">{(runtime.node?.rssMb ?? 0)}MB</div>
                </div>
                <div className="bg-[rgba(242,242,247,0.5)] rounded-lg p-2.5">
                  <div className="text-[#8e8e93]">堆内存</div>
                  <div className="text-[#1c1c1e] font-mono">{(runtime.node?.heapUsedMb ?? 0)}MB / {(runtime.node?.heapTotalMb ?? 0)}MB</div>
                </div>
              </div>
            </div>
            {/* Database */}
            <div>
              <div className="text-xs text-[#007aff] font-medium mb-2">数据库</div>
              <div className="grid grid-cols-3 gap-2 text-xs">
                <div className="bg-[rgba(242,242,247,0.5)] rounded-lg p-2.5">
                  <div className="text-[#8e8e93]">活跃连接</div>
                  <div className="text-[#1c1c1e] font-mono">{(runtime.database?.activeConnections ?? 0)}/{(runtime.database?.maxConnections ?? 0)}</div>
                </div>
                <div className="bg-[rgba(242,242,247,0.5)] rounded-lg p-2.5">
                  <div className="text-[#8e8e93]">平均查询</div>
                  <div className="text-[#1c1c1e] font-mono">{(runtime.database?.avgQueryMs ?? 0)}ms</div>
                </div>
                <div className="bg-[rgba(242,242,247,0.5)] rounded-lg p-2.5">
                  <div className="text-[#8e8e93]">慢查询</div>
                  <div className={`font-mono ${(runtime.database?.slowQueryCount ?? 0) > 0 ? 'text-[#ff9500]' : 'text-[#1c1c1e]'}`}>{(runtime.database?.slowQueryCount ?? 0)}</div>
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
    login: 'text-[#007aff] bg-[rgba(0,122,255,0.1)]',
    logout: 'text-[#8e8e93] bg-[rgba(0,0,0,0.01)]',
    upload: 'text-[#34c759] bg-[rgba(52,199,89,0.1)]',
    delete: 'text-[#ff3b30] bg-[rgba(255,59,48,0.1)]',
    download: 'text-[#007aff] bg-[rgba(0,122,255,0.1)]',
    share: 'text-[#007aff] bg-[rgba(0,122,255,0.1)]',
    create_album: 'text-[#007aff] bg-[rgba(0,122,255,0.1)]',
    delete_album: 'text-[#ff3b30] bg-[rgba(255,59,48,0.1)]',
  };

  return (
    <div className="space-y-4">
      <div className="bg-white rounded-xl border border-[rgba(229,229,234,0.3)] overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-[#8e8e93] text-xs border-b border-[rgba(229,229,234,0.3)]">
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
                <tr key={i} className="border-b border-[rgba(229,229,234,0.1)] hover:bg-[rgba(0,0,0,0.01)] transition-colors">
                  <td className="py-2.5 px-4">
                    <span className={`inline-flex items-center gap-1.5 px-2 py-1 rounded text-xs ${actionColor[log.action || ''] || 'text-[#8e8e93] bg-[rgba(0,0,0,0.01)]'}`}>
                      {actionIcons[log.action || ''] || <Activity className="w-3.5 h-3.5" />}
                      {log.action || '-'}
                    </span>
                  </td>
                  <td className="py-2.5 px-4 text-[#3a3a3c] text-xs">{log.username || '-'}</td>
                  <td className="py-2.5 px-4 text-[#8e8e93] text-xs max-w-[300px] truncate">{log.details || '-'}</td>
                  <td className="py-2.5 px-4 text-[#8e8e93] text-xs font-mono">{(log.resourceType || '')}#{(log.resourceId || '')}</td>
                  <td className="py-2.5 px-4 text-[#8e8e93] text-xs font-mono">{log.ipAddress || '-'}</td>
                  <td className="py-2.5 px-4 text-[#8e8e93] text-xs">{timeAgo(log.createdAt || '')}</td>
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
    full: { label: '全量备份', color: 'text-[#007aff] bg-[rgba(0,122,255,0.1)]' },
    images: { label: '图片备份', color: 'text-[#34c759] bg-[rgba(52,199,89,0.1)]' },
    database: { label: '数据库', color: 'text-[#007aff] bg-[rgba(0,122,255,0.1)]' },
    settings: { label: '配置备份', color: 'text-[#007aff] bg-[rgba(0,122,255,0.1)]' },
  };

  return (
    <div className="space-y-6">
      {/* 操作栏 */}
      <div className="flex items-center justify-between">
        <div className="text-sm text-[#8e8e93]">共 {backups.length} 个备份记录</div>
        <button
          onClick={async () => {
            setCreating(true);
            // 创建备份
            const res = await fetch('/api/backup/create?backupType=full', { method: 'POST' });
            if (!res.ok) console.error('Backup creation failed');
            setCreating(false);
            onRefresh();
          }}
          disabled={creating}
          className="flex items-center gap-2 px-4 py-2 rounded-lg bg-[#007AFF] text-white text-sm font-medium hover:shadow-lg hover:shadow-[0_2px_12px_rgba(0,0,0,0.04)] transition-all disabled:opacity-50"
        >
          {creating ? <RefreshCw className="w-4 h-4 animate-spin" /> : <Download className="w-4 h-4" />}
          {creating ? '创建中...' : '创建备份'}
        </button>
      </div>

      {/* 备份列表 */}
      <div className="bg-white rounded-xl border border-[rgba(229,229,234,0.3)] overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="text-[#8e8e93] text-xs border-b border-[rgba(229,229,234,0.3)]">
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
              const tl = typeLabel[bk.type || ''] || { label: bk.type || 'unknown', color: 'text-[#8e8e93] bg-[rgba(0,0,0,0.01)]' };
              return (
                <tr key={i} className="border-b border-[rgba(229,229,234,0.1)] hover:bg-[rgba(0,0,0,0.01)] transition-colors">
                  <td className="py-2.5 px-5 text-[#1c1c1e]">{bk.name || '未命名'}</td>
                  <td className="py-2.5 px-5">
                    <span className={`px-2 py-0.5 rounded text-xs ${tl.color}`}>{tl.label}</span>
                  </td>
                  <td className="py-2.5 px-5 text-right text-[#3a3a3c] font-mono">{fmtSize(bk.size)}</td>
                  <td className="py-2.5 px-5">
                    {bk.status === 'completed' ? (
                      <span className="flex items-center gap-1 text-[#34c759] text-xs"><CheckCircle2 className="w-3.5 h-3.5" /> 完成</span>
                    ) : (
                      <span className="flex items-center gap-1 text-[#ff9500] text-xs"><RefreshCw className="w-3.5 h-3.5 animate-spin" /> 进行中</span>
                    )}
                  </td>
                  <td className="py-2.5 px-5 text-[#8e8e93] text-xs">{timeAgo(bk.createdAt)}</td>
                  <td className="py-2.5 px-5 text-right">
                    <div className="flex items-center justify-end gap-1">
                      <button className="p-1.5 rounded hover:bg-[rgba(118,118,128,0.12)] text-[#8e8e93] hover:text-[#007aff] transition-colors" title="恢复">
                        <RotateCcw className="w-3.5 h-3.5" />
                      </button>
                      <button className="p-1.5 rounded hover:bg-[rgba(118,118,128,0.12)] text-[#8e8e93] hover:text-[#34c759] transition-colors" title="下载">
                        <Download className="w-3.5 h-3.5" />
                      </button>
                      <button className="p-1.5 rounded hover:bg-[rgba(118,118,128,0.12)] text-[#8e8e93] hover:text-[#ff3b30] transition-colors" title="删除">
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
    // 加载真实用户数据（管理员接口，由 Java 后端鉴权）
    fetch('/api/admin/users')
      .then(res => res.json())
      .then(data => {
        if (data.success) {
          // handleBackendResponse 返回 { success, data } ，data 即用户数组
          const userList = Array.isArray(data.data) ? data.data : (data.users || []);
          setUsers(userList);
        }
      })
      .catch(err => console.error('Failed to load users:', err));
  }, []);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div className="text-sm text-[#8e8e93]">共 {users.length} 个用户</div>
        <button className="flex items-center gap-2 px-4 py-2 rounded-lg bg-[#007AFF] text-white text-sm font-medium hover:shadow-lg hover:shadow-[0_2px_12px_rgba(0,0,0,0.04)] transition-all">
          <UserCheck className="w-4 h-4" />
          添加用户
        </button>
      </div>

      <div className="bg-white rounded-xl border border-[rgba(229,229,234,0.3)] overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="text-[#8e8e93] text-xs border-b border-[rgba(229,229,234,0.3)]">
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
              <tr key={user.id} className="border-b border-[rgba(229,229,234,0.1)] hover:bg-[rgba(0,0,0,0.01)] transition-colors">
                <td className="py-2.5 px-5">
                  <div className="flex items-center gap-2.5">
                    <div className={`w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold
                      ${user.role === 'admin' ? 'bg-[#007AFF] text-white' : 'bg-[rgba(118,118,128,0.12)] text-[#3a3a3c]'}`}>
                      {user.username?.[0]?.toUpperCase() ?? '?'}
                    </div>
                    <span className="text-[#1c1c1e]">{user.username}</span>
                  </div>
                </td>
                <td className="py-2.5 px-5">
                  <span className={`px-2 py-0.5 rounded text-xs
                    ${user.role === 'admin' ? 'text-[#007aff] bg-[rgba(0,122,255,0.1)]' : 'text-[#8e8e93] bg-[rgba(0,0,0,0.01)]'}`}>
                    {user.role === 'admin' ? '管理员' : '普通用户'}
                  </span>
                </td>
                <td className="py-2.5 px-5 text-[#8e8e93] text-xs">{user.email}</td>
                <td className="py-2.5 px-5">
                  <span className={`flex items-center gap-1 text-xs ${user.status === 'active' ? 'text-[#34c759]' : 'text-[#8e8e93]'}`}>
                    <span className={`w-1.5 h-1.5 rounded-full ${user.status === 'active' ? 'bg-[#34c759]' : 'bg-[rgba(0,0,0,0.08)]'}`} />
                    {user.status === 'active' ? '活跃' : '停用'}
                  </span>
                </td>
                <td className="py-2.5 px-5 text-[#8e8e93] text-xs">{timeAgo(user.lastLogin)}</td>
                <td className="py-2.5 px-5 text-right">
                  <div className="flex items-center justify-end gap-1">
                    <button className="p-1.5 rounded hover:bg-[rgba(118,118,128,0.12)] text-[#8e8e93] hover:text-[#007aff] transition-colors" title="编辑">
                      <Eye className="w-3.5 h-3.5" />
                    </button>
                    <button className="p-1.5 rounded hover:bg-[rgba(118,118,128,0.12)] text-[#8e8e93] hover:text-[#ff9500] transition-colors" title="重置密码">
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

// ============ 存储测试 Tab ============
function StorageTestTab() {
  const [result, setResult] = useState<Record<string, any> | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const testS3 = async () => {
    setLoading(true);
    setError(null);
    setResult(null);
    try {
      const res = await fetch('/api/storage/s3-test');
      const data = await res.json();
      setResult(data);
    } catch (e) {
      setError(e instanceof Error ? e.message : '请求失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-medium text-[#3a3a3c]">S3/OSS 存储连接测试</h3>
        <button
          onClick={testS3}
          disabled={loading}
          className="flex items-center gap-2 px-4 py-2 rounded-lg bg-[#007AFF] text-white text-sm font-medium transition-all disabled:opacity-50"
        >
          <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
          {loading ? '测试中...' : '开始测试'}
        </button>
      </div>

      {error && (
        <div className="p-4 rounded-lg bg-[rgba(255,59,48,0.1)] border border-[rgba(255,59,48,0.2)]">
          <p className="text-[#ff3b30] text-sm">{error}</p>
        </div>
      )}

      {result && (
        <div className="space-y-4">
          {/* 连接状态 */}
          <div className={`p-4 rounded-lg border ${
            result.success
              ? 'bg-[rgba(52,199,89,0.1)] border-[rgba(52,199,89,0.2)]'
              : 'bg-[rgba(255,59,48,0.1)] border-[rgba(255,59,48,0.2)]'
          }`}>
            <div className="flex items-center gap-2">
              {result.success
                ? <CheckCircle2 className="w-5 h-5 text-[#34c759]" />
                : <AlertCircle className="w-5 h-5 text-[#ff3b30]" />
              }
              <span className={`text-sm font-medium ${
                result.success ? 'text-[#34c759]' : 'text-[#ff3b30]'
              }`}>
                {(result.message as string) || (result.success ? '连接成功' : '连接失败')}
              </span>
            </div>
          </div>

          {/* 详细信息 */}
          <div className="p-4 rounded-lg bg-white border border-[rgba(229,229,234,0.5)]">
            <h4 className="text-xs font-medium text-[#8e8e93] mb-3">连接详情</h4>
            <div className="space-y-2">
              {Object.entries(result).filter(([k]) => k !== 'success' && k !== 'message').map(([key, value]) => (
                <div key={key} className="flex items-center justify-between py-1 border-b border-[rgba(229,229,234,0.3)] last:border-0">
                  <span className="text-xs text-[#8e8e93]">{key}</span>
                  <span className="text-xs text-[#3a3a3c] font-mono max-w-md truncate">
                    {typeof value === 'boolean'
                      ? (value ? '✓' : '✗')
                      : typeof value === 'string'
                        ? value
                        : JSON.stringify(value)
                    }
                  </span>
                </div>
              ))}
            </div>
          </div>

          {/* 预签名URL访问测试 */}
          {result.presignedUrl && (
            <div className="p-4 rounded-lg bg-white border border-[rgba(229,229,234,0.5)]">
              <h4 className="text-xs font-medium text-[#8e8e93] mb-2">预签名 URL</h4>
              <div className="p-2 rounded bg-[rgba(242,242,247,0.5)] text-xs text-[#007aff] font-mono break-all">
                {String(result.presignedUrl)}
              </div>
              <a
                href={String(result.presignedUrl)}
                target="_blank"
                rel="noopener noreferrer"
                className="mt-2 inline-flex items-center gap-1 text-xs text-[#007aff] hover:text-[#007aff]"
              >
                <Globe className="w-3 h-3" />
                在浏览器中打开验证
              </a>
            </div>
          )}
        </div>
      )}

      {!result && !error && !loading && (
        <div className="p-8 rounded-lg bg-white border border-[rgba(229,229,234,0.3)] text-center">
          <HardDrive className="w-8 h-8 text-[#8e8e93] mx-auto mb-3" />
          <p className="text-sm text-[#8e8e93]">点击"开始测试"按钮验证 S3/OSS 存储连接</p>
          <p className="text-xs text-[#8e8e93] mt-1">测试流程：上传 → 获取URL → 预签名 → 删除</p>
        </div>
      )}
    </div>
  );
}
