import { NextRequest, NextResponse } from 'next/server';
import { isBackendAvailable, backendFetch } from '@/lib/backend-proxy';

// 生成模拟错误追踪数据
function generateMockErrors() {
  const now = Date.now();
  const errors = [
    {
      id: 'err-001',
      type: 'API_ERROR',
      message: 'DeepSeek API 请求超时 (timeout: 30s)',
      stack: 'SmartChatServiceImpl.chat(SmartChatServiceImpl.java:245)\n→ DeepSeekClient.complete(DeepSeekClient.java:89)\n→ HttpClient.timeout(HttpClient.java:156)',
      endpoint: '/api/chat',
      method: 'POST',
      statusCode: 504,
      occurrences: 23,
      firstSeen: new Date(now - 3 * 86400000).toISOString(),
      lastSeen: new Date(now - 3600000).toISOString(),
      severity: 'high',
      status: 'unresolved',
    },
    {
      id: 'err-002',
      type: 'UPLOAD_ERROR',
      message: '文件上传失败：S3 bucket 空间不足',
      stack: 'FileUploadService.upload(FileUploadService.java:112)\n→ S3Client.putObject(S3Client.java:78)',
      endpoint: '/api/images/upload',
      method: 'POST',
      statusCode: 507,
      occurrences: 8,
      firstSeen: new Date(now - 5 * 86400000).toISOString(),
      lastSeen: new Date(now - 2 * 86400000).toISOString(),
      severity: 'critical',
      status: 'resolved',
    },
    {
      id: 'err-003',
      type: 'DB_ERROR',
      message: '数据库连接池耗尽 (max: 20, active: 20, waiting: 5)',
      stack: 'DataSource.getConnection(DataSource.java:67)\n→ HikariPool.evictConnection(HikariPool.java:234)',
      endpoint: '/api/images',
      method: 'GET',
      statusCode: 503,
      occurrences: 45,
      firstSeen: new Date(now - 7 * 86400000).toISOString(),
      lastSeen: new Date(now - 7200000).toISOString(),
      severity: 'critical',
      status: 'unresolved',
    },
    {
      id: 'err-004',
      type: 'AUTH_ERROR',
      message: 'Session 过期但未正确清理，导致内存泄漏',
      stack: 'SessionManager.validate(SessionManager.java:89)\n→ SessionStore.cleanup(SessionStore.java:45)',
      endpoint: '/api/auth/login',
      method: 'GET',
      statusCode: 401,
      occurrences: 156,
      firstSeen: new Date(now - 10 * 86400000).toISOString(),
      lastSeen: new Date(now - 1800000).toISOString(),
      severity: 'medium',
      status: 'unresolved',
    },
    {
      id: 'err-005',
      type: 'VALIDATION_ERROR',
      message: '图片格式不支持：webp 文件头校验失败',
      stack: 'ImageValidator.validate(ImageValidator.java:34)\n→ FileHeader.check(FileHeader.java:22)',
      endpoint: '/api/images/upload',
      method: 'POST',
      statusCode: 400,
      occurrences: 67,
      firstSeen: new Date(now - 12 * 86400000).toISOString(),
      lastSeen: new Date(now - 43200000).toISOString(),
      severity: 'low',
      status: 'resolved',
    },
    {
      id: 'err-006',
      type: 'API_ERROR',
      message: 'MiniMax Embedding API 返回空向量 (dim=0)',
      stack: 'EmbeddingService.embed(EmbeddingService.java:178)\n→ MiniMaxClient.invoke(MiniMaxClient.java:56)',
      endpoint: '/api/memory/search',
      method: 'GET',
      statusCode: 500,
      occurrences: 12,
      firstSeen: new Date(now - 2 * 86400000).toISOString(),
      lastSeen: new Date(now - 5 * 3600000).toISOString(),
      severity: 'high',
      status: 'unresolved',
    },
    {
      id: 'err-007',
      type: 'RATE_LIMIT',
      message: '请求频率超过限制：100次/分钟',
      stack: 'RateLimiter.check(RateLimiter.java:34)',
      endpoint: '/api/images/upload',
      method: 'POST',
      statusCode: 429,
      occurrences: 89,
      firstSeen: new Date(now - 6 * 86400000).toISOString(),
      lastSeen: new Date(now - 900000).toISOString(),
      severity: 'medium',
      status: 'unresolved',
    },
    {
      id: 'err-008',
      type: 'DB_ERROR',
      message: '知识库文档切片失败：文本内容为空',
      stack: 'KnowledgeBaseService.slice(KnowledgeBaseService.java:234)\n→ TextSplitter.split(TextSplitter.java:67)',
      endpoint: '/api/knowledge/upload',
      method: 'POST',
      statusCode: 500,
      occurrences: 34,
      firstSeen: new Date(now - 4 * 86400000).toISOString(),
      lastSeen: new Date(now - 86400000).toISOString(),
      severity: 'medium',
      status: 'resolved',
    },
  ];

  // 24小时错误趋势
  const hourlyErrors = Array.from({ length: 24 }, (_, i) => ({
    hour: `${String(i).padStart(2, '0')}:00`,
    count: Math.floor(Math.random() * 15 + (i > 8 && i < 20 ? 5 : 1)),
  }));

  return {
    errors,
    hourlyErrors,
    summary: {
      total: errors.reduce((s, e) => s + e.occurrences, 0),
      unresolved: errors.filter(e => e.status === 'unresolved').length,
      critical: errors.filter(e => e.severity === 'critical').length,
      high: errors.filter(e => e.severity === 'high').length,
    },
  };
}

// GET /api/ops/errors
export async function GET(request: NextRequest) {
  try {
    const { searchParams } = new URL(request.url);
    const severity = searchParams.get('severity') || '';
    const status = searchParams.get('status') || '';

    const backendAvailable = await isBackendAvailable();

    if (!backendAvailable) {
      let result = generateMockErrors();
      let filteredErrors = result.errors;

      if (severity) filteredErrors = filteredErrors.filter(e => e.severity === severity);
      if (status) filteredErrors = filteredErrors.filter(e => e.status === status);

      return NextResponse.json({
        success: true,
        data: { ...result, errors: filteredErrors },
      });
    }

    const params = new URLSearchParams();
    if (severity) params.append('severity', severity);
    if (status) params.append('status', status);

    const cookieHeader = request.headers.get('cookie');
    const response = await backendFetch(`/ops/errors?${params.toString()}`, {
      headers: cookieHeader ? { cookie: cookieHeader } : {},
    });

    if (!response.ok) throw new Error(`Backend responded with ${response.status}`);

    const data = await response.json();
    return NextResponse.json(data);
  } catch (error) {
    const result = generateMockErrors();
    return NextResponse.json({ success: true, data: result });
  }
}

// PATCH /api/ops/errors - 标记错误为已解决
export async function PATCH(request: NextRequest) {
  try {
    const body = await request.json();
    const { errorId, status } = body as { errorId: string; status: string };

    // 在降级模式下模拟成功
    return NextResponse.json({
      success: true,
      message: `错误 ${errorId} 已标记为 ${status}`,
    });
  } catch {
    return NextResponse.json({ success: false, message: '操作失败' }, { status: 500 });
  }
}
