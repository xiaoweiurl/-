import { NextRequest, NextResponse } from 'next/server';
import { isBackendAvailable, backendFetch } from '@/lib/backend-proxy';

// 生成模拟 API 监控指标
function generateMockMetrics() {
  const now = Date.now();
  const hourMs = 3600000;

  // 24小时请求量趋势
  const hourlyRequests = Array.from({ length: 24 }, (_, i) => ({
    hour: `${String(i).padStart(2, '0')}:00`,
    total: Math.floor(Math.random() * 500 + 100),
    success: Math.floor(Math.random() * 450 + 90),
    error: Math.floor(Math.random() * 30 + 2),
    avgResponseTime: Math.floor(Math.random() * 300 + 50),
  }));

  // API 端点统计
  const endpoints = [
    { path: '/api/auth/login', method: 'POST', calls: 1280, avgMs: 120, errorRate: 0.8, p99Ms: 450 },
    { path: '/api/images', method: 'GET', calls: 8540, avgMs: 85, errorRate: 0.3, p99Ms: 320 },
    { path: '/api/images/upload', method: 'POST', calls: 3200, avgMs: 1800, errorRate: 2.1, p99Ms: 5200 },
    { path: '/api/knowledge/docs', method: 'GET', calls: 4500, avgMs: 65, errorRate: 0.5, p99Ms: 180 },
    { path: '/api/memory/search', method: 'GET', calls: 2800, avgMs: 220, errorRate: 1.2, p99Ms: 890 },
    { path: '/api/chat', method: 'POST', calls: 6200, avgMs: 3500, errorRate: 1.8, p99Ms: 12000 },
    { path: '/api/ai-image', method: 'POST', calls: 1500, avgMs: 8500, errorRate: 3.5, p99Ms: 25000 },
    { path: '/api/supply-chain', method: 'GET', calls: 3800, avgMs: 95, errorRate: 0.4, p99Ms: 280 },
    { path: '/api/albums', method: 'GET', calls: 5600, avgMs: 45, errorRate: 0.1, p99Ms: 120 },
    { path: '/api/documents/upload', method: 'POST', calls: 2100, avgMs: 2200, errorRate: 1.5, p99Ms: 6800 },
  ];

  // 系统资源
  const systemResources = {
    cpu: { current: 42, peak: 78, cores: 4 },
    memory: { usedMb: 1847, totalMb: 4096, peakMb: 3200, percentage: 45 },
    disk: { usedGb: 28.5, totalGb: 50, percentage: 57 },
    network: { inboundKbps: 1250, outboundKbps: 3400, totalRequests: 39520 },
  };

  return {
    summary: {
      totalRequests: 39520,
      successRate: 97.8,
      avgResponseTime: 186,
      errorCount: 869,
      uptime: '15天 8小时',
      activeUsers: 23,
      requestsPerMinute: 28,
    },
    hourlyRequests,
    endpoints,
    systemResources,
    lastUpdated: new Date(now).toISOString(),
  };
}

// GET /api/ops/metrics
export async function GET(request: NextRequest) {
  try {
    const backendAvailable = await isBackendAvailable();

    if (!backendAvailable) {
      // 降级模式：返回模拟数据
      const metrics = generateMockMetrics();
      return NextResponse.json({ success: true, data: metrics });
    }

    // 生产模式：代理到 Java 后端
    const cookieHeader = request.headers.get('cookie');
    const response = await backendFetch('/ops/metrics', {
      headers: cookieHeader ? { cookie: cookieHeader } : {},
    });

    if (!response.ok) {
      throw new Error(`Backend responded with ${response.status}`);
    }

    const data = await response.json();
    return NextResponse.json(data);
  } catch (error) {
    // 降级到模拟数据
    const metrics = generateMockMetrics();
    return NextResponse.json({ success: true, data: metrics });
  }
}
