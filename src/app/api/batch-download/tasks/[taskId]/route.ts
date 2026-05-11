import { NextRequest, NextResponse } from 'next/server';
import { getSessionId } from '@/lib/backend-proxy';

// 获取后端 API 地址
const BACKEND_API_URL = process.env.NEXT_PUBLIC_BACKEND_API_URL || 'http://localhost:8080/api';

/**
 * GET /api/batch-download/tasks/[taskId] - 获取任务进度
 */
export async function GET(
  request: NextRequest,
  { params }: { params: Promise<{ taskId: string }> }
) {
  try {
    const { taskId } = await params;
    
    // 获取 sessionId（从 cookie 中提取）
    const sessionId = getSessionId({
      'cookie': request.headers.get('cookie') || '',
      'x-session-id': request.headers.get('x-session-id') || '',
    });
    
    const headers: Record<string, string> = {};
    
    // 添加 sessionId 到请求头
    if (sessionId) {
      headers['X-Session-Id'] = sessionId;
    }
    
    const response = await fetch(`${BACKEND_API_URL}/batch-download/tasks/${taskId}`, {
      method: 'GET',
      headers,
    });
    
    const data = await response.json();
    return NextResponse.json(data, { status: response.status });
  } catch (error) {
    console.error('获取任务进度失败:', error);
    return NextResponse.json({ 
      success: false, 
      message: '获取任务进度失败' 
    }, { status: 500 });
  }
}
