import { NextRequest, NextResponse } from 'next/server';
import { backendFetch, isBackendAvailable } from '@/lib/backend-proxy';

export async function POST(request: NextRequest) {
  try {
    const backendAvailable = await isBackendAvailable();
    
    if (!backendAvailable) {
      return NextResponse.json(
        { success: false, error: '后端服务不可用' },
        { status: 503 }
      );
    }
    
    const body = await request.json();
    
    const response = await backendFetch('/auth/register', {
      method: 'POST',
      body: JSON.stringify(body),
    });
    
    const data = await response.json();
    
    if (!response.ok) {
      return NextResponse.json(
        { success: false, error: data.error || data.message || '注册失败' },
        { status: response.status }
      );
    }
    
    return NextResponse.json({ success: true, data });
  } catch (error) {
    console.error('注册代理错误:', error);
    return NextResponse.json(
      { success: false, error: '注册服务暂时不可用' },
      { status: 500 }
    );
  }
}
