import { NextRequest, NextResponse } from 'next/server';
import { isBackendAvailable, backendFetch } from '@/lib/backend-proxy';

// 测试 S3/OSS 存储连接状态（管理员权限）
export async function GET(request: NextRequest) {
  try {
    const backendAvailable = await isBackendAvailable();

    if (!backendAvailable) {
      return NextResponse.json({
        success: true,
        storageType: 'local',
        message: '后端不可用，当前使用降级模式（本地模拟存储）',
      });
    }

    const sessionId = request.cookies.get('session_id')?.value;

    const response = await backendFetch('/storage/s3-test', {
      headers: {
        'X-Session-Id': sessionId || '',
      },
    });

    const data = await response.json();
    return NextResponse.json(data, { status: response.status });
  } catch (error) {
    console.error('S3 test error:', error);
    return NextResponse.json({ error: 'S3 连接测试失败' }, { status: 500 });
  }
}
