import { NextRequest, NextResponse } from 'next/server';
import { getSessionId } from '@/lib/backend-proxy';

// 获取后端 API 地址
const BACKEND_API_URL = process.env.NEXT_PUBLIC_BACKEND_API_URL || 'http://localhost:8080/api';

/**
 * POST /api/batch-download/tasks - 提交异步批量下载任务
 */
export async function POST(request: NextRequest) {
  try {
    const body = await request.json();
    
    // 获取 sessionId（从 cookie 中提取）
    const sessionId = getSessionId({
      'cookie': request.headers.get('cookie') || '',
      'x-session-id': request.headers.get('x-session-id') || '',
    });
    
    console.log('[batch-download/tasks] SessionId from request:', sessionId ? sessionId.substring(0, 8) + '...' : 'null');
    console.log('[batch-download/tasks] Cookie:', request.headers.get('cookie')?.substring(0, 50));
    
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
    };
    
    // 添加 sessionId 到请求头
    if (sessionId) {
      headers['X-Session-Id'] = sessionId;
    }
    
    // TODO: 暂时直接调用同步接口，绕过异步任务逻辑
    // 后续如果异步任务工作正常，可以改回调用 /batch-download/tasks
    const response = await fetch(`${BACKEND_API_URL}/images/batch-download`, {
      method: 'POST',
      headers,
      body: JSON.stringify(body),
    });
    
    const data = await response.json();
    console.log('[batch-download/tasks] Response:', JSON.stringify(data).substring(0, 200));
    return NextResponse.json(data, { status: response.status });
  } catch (error) {
    console.error('提交异步任务失败:', error);
    return NextResponse.json({ 
      success: false, 
      message: '提交任务失败' 
    }, { status: 500 });
  }
}
