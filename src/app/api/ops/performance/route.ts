import { NextRequest, NextResponse } from 'next/server';
import { isBackendAvailable, backendFetch } from '@/lib/backend-proxy';

// 生成模拟性能指标数据
function generateMockPerformance() {
  const now = Date.now();
  const minuteMs = 60000;

  // 最近60分钟响应时间趋势
  const responseTimeline = Array.from({ length: 60 }, (_, i) => ({
    minute: `${String(i).padStart(2, '0')}m`,
    p50: Math.floor(Math.random() * 100 + 40),
    p90: Math.floor(Math.random() * 200 + 150),
    p99: Math.floor(Math.random() * 500 + 400),
  }));

  // 各服务性能
  const services = [
    {
      name: 'API Gateway',
      status: 'healthy',
      uptime: '15d 8h 32m',
      responseTime: { p50: 12, p90: 28, p99: 95 },
      throughput: 650,
      errorRate: 0.1,
      connections: 45,
      maxConnections: 200,
    },
    {
      name: 'Auth Service',
      status: 'healthy',
      uptime: '15d 8h 32m',
      responseTime: { p50: 35, p90: 85, p99: 320 },
      throughput: 120,
      errorRate: 0.8,
      connections: 8,
      maxConnections: 50,
    },
    {
      name: 'Image Service',
      status: 'healthy',
      uptime: '15d 8h 32m',
      responseTime: { p50: 85, p90: 250, p99: 1200 },
      throughput: 340,
      errorRate: 0.3,
      connections: 12,
      maxConnections: 100,
    },
    {
      name: 'AI Chat Service',
      status: 'degraded',
      uptime: '3d 12h 5m',
      responseTime: { p50: 2500, p90: 8000, p99: 15000 },
      throughput: 85,
      errorRate: 2.5,
      connections: 18,
      maxConnections: 30,
    },
    {
      name: 'Knowledge Service',
      status: 'healthy',
      uptime: '15d 8h 32m',
      responseTime: { p50: 65, p90: 180, p99: 450 },
      throughput: 200,
      errorRate: 0.5,
      connections: 6,
      maxConnections: 50,
    },
    {
      name: 'Storage Service',
      status: 'healthy',
      uptime: '15d 8h 32m',
      responseTime: { p50: 150, p90: 400, p99: 2000 },
      throughput: 180,
      errorRate: 1.2,
      connections: 15,
      maxConnections: 80,
    },
  ];

  // 慢查询 Top 10
  const slowQueries = [
    { endpoint: '/api/chat', avgMs: 3500, maxMs: 12000, calls: 6200, dbMs: 0, aiMs: 3400 },
    { endpoint: '/api/ai-image', avgMs: 8500, maxMs: 25000, calls: 1500, dbMs: 0, aiMs: 8400 },
    { endpoint: '/api/images/upload', avgMs: 1800, maxMs: 5200, calls: 3200, dbMs: 120, aiMs: 0 },
    { endpoint: '/api/memory/search', avgMs: 220, maxMs: 890, calls: 2800, dbMs: 180, aiMs: 0 },
    { endpoint: '/api/documents/upload', avgMs: 2200, maxMs: 6800, calls: 2100, dbMs: 95, aiMs: 0 },
    { endpoint: '/api/images', avgMs: 85, maxMs: 320, calls: 8540, dbMs: 72, aiMs: 0 },
    { endpoint: '/api/knowledge/upload', avgMs: 1500, maxMs: 4500, calls: 800, dbMs: 200, aiMs: 0 },
    { endpoint: '/api/supply-chain', avgMs: 95, maxMs: 280, calls: 3800, dbMs: 80, aiMs: 0 },
    { endpoint: '/api/albums', avgMs: 45, maxMs: 120, calls: 5600, dbMs: 38, aiMs: 0 },
    { endpoint: '/api/auth/login', avgMs: 120, maxMs: 450, calls: 1280, dbMs: 95, aiMs: 0 },
  ];

  // JVM/运行时指标
  const runtime = {
    jvm: {
      heapUsedMb: 1024,
      heapMaxMb: 2048,
      gcPauseMs: 12,
      threadCount: 48,
      peakThreadCount: 65,
    },
    node: {
      rssMb: 256,
      heapUsedMb: 180,
      heapTotalMb: 256,
      externalMb: 12,
      arrayBuffersMb: 8,
    },
    database: {
      activeConnections: 12,
      maxConnections: 20,
      waitingConnections: 0,
      avgQueryMs: 45,
      slowQueryCount: 3,
    },
  };

  return {
    responseTimeline,
    services,
    slowQueries,
    runtime,
    lastUpdated: new Date(now).toISOString(),
  };
}

// GET /api/ops/performance
export async function GET(request: NextRequest) {
  try {
    const backendAvailable = await isBackendAvailable();

    if (!backendAvailable) {
      const data = generateMockPerformance();
      return NextResponse.json({ success: true, data });
    }

    const cookieHeader = request.headers.get('cookie');
    const response = await backendFetch('/ops/performance', {
      headers: cookieHeader ? { cookie: cookieHeader } : {},
    });

    if (!response.ok) throw new Error(`Backend responded with ${response.status}`);

    const data = await response.json();
    return NextResponse.json(data);
  } catch (error) {
    const data = generateMockPerformance();
    return NextResponse.json({ success: true, data });
  }
}
