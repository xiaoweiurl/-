import { NextRequest, NextResponse } from 'next/server';
import { backendFetch, isBackendAvailable } from '@/lib/backend-proxy';

// Mock 分享数据（与 route.ts 共享）
declare global {
  // eslint-disable-next-line no-var
  var mockShareLinks: Map<string, unknown[]> | undefined;
}

// 生成模拟的图片数据
function generateMockImages(count: number, resourceName: string) {
  const images = [];
  const widths = [800, 1024, 1280, 1920];
  const heights = [600, 768, 1024, 1080];
  
  for (let i = 0; i < count; i++) {
    const width = widths[i % widths.length];
    const height = heights[i % heights.length];
    images.push({
      id: `img-${Date.now()}-${i}`,
      title: `${resourceName} - 图片 ${i + 1}`,
      url: `https://picsum.photos/seed/${Date.now() + i}/${width}/${height}`,
      thumbnailUrl: `https://picsum.photos/seed/${Date.now() + i}/400/300`,
      width,
      height,
      size: Math.floor(Math.random() * 5000000) + 100000,
      createdAt: new Date(Date.now() - Math.random() * 30 * 24 * 60 * 60 * 1000).toISOString(),
    });
  }
  return images;
}

// 访问分享链接（公开访问，无需认证）
export async function GET(
  request: NextRequest,
  { params }: { params: Promise<{ shareCode: string }> }
) {
  try {
    const { shareCode } = await params;
    const { searchParams } = new URL(request.url);
    const password = searchParams.get('password') || '';
    
    // 检查后端是否可用
    const backendAvailable = await isBackendAvailable();
    
    if (!backendAvailable) {
      // 降级模式：从全局 mock 数据查找
      // 由于无法直接访问另一个路由的变量，这里重新生成一些示例分享
      const mockShare = {
        id: `share-${shareCode}`,
        shareCode,
        resourceType: 'album',
        resourceId: 'mock-album-id',
        resourceName: '示例相册',
        password: null,
        expiresAt: null,
        accessCount: Math.floor(Math.random() * 100),
        createdAt: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString(),
      };
      
      // 生成模拟图片
      const images = generateMockImages(12, mockShare.resourceName);
      
      return NextResponse.json({
        success: true,
        share: mockShare,
        images,
        message: '降级模式 - 示例数据',
      });
    }
    
    // 后端可用，调用后端 API
    const response = await backendFetch(`/share/access/${shareCode}?password=${encodeURIComponent(password)}`, {
      method: 'GET',
    });

    const data = await response.json();
    
    // 确保返回数据格式正确
    // 后端可能返回 requirePassword，前端页面需要这个字段
    if (data.requirePassword && !data.error) {
      data.error = data.error || '请输入访问密码';
    }
    
    return NextResponse.json(data, { status: response.status });
  } catch (error) {
    console.error('Access share error:', error);
    return NextResponse.json({ error: '访问分享失败' }, { status: 500 });
  }
}

// 验证分享密码
export async function POST(
  request: NextRequest,
  { params }: { params: Promise<{ shareCode: string }> }
) {
  try {
    const { shareCode } = await params;
    const body = await request.json();
    const { password } = body;
    
    // 检查后端是否可用
    const backendAvailable = await isBackendAvailable();
    
    if (!backendAvailable) {
      // 降级模式：模拟验证成功
      return NextResponse.json({
        success: true,
        message: '验证成功（降级模式）',
      });
    }
    
    // 后端可用，调用后端 API
    // 后端路由: POST /api/share/access，请求体包含 shareCode 和 password
    const response = await backendFetch('/share/access', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ shareCode, password }),
    });

    const data = await response.json();
    return NextResponse.json(data, { status: response.status });
  } catch (error) {
    console.error('Verify share password error:', error);
    return NextResponse.json({ error: '验证失败' }, { status: 500 });
  }
}

// 删除分享链接
export async function DELETE(
  request: NextRequest,
  { params }: { params: Promise<{ shareCode: string }> }
) {
  try {
    const { shareCode } = await params;
    
    // 检查后端是否可用
    const backendAvailable = await isBackendAvailable();
    
    if (!backendAvailable) {
      // 降级模式：模拟删除成功
      return NextResponse.json({
        success: true,
        message: '删除成功（降级模式）',
      });
    }
    
    // 后端可用，调用后端 API
    // 后端路由: DELETE /api/share/code/{shareCode}
    const response = await backendFetch(`/share/code/${shareCode}`, {
      method: 'DELETE',
    });

    // 后端返回 204 No Content，需要转换为 JSON
    if (response.status === 204) {
      return NextResponse.json({ success: true, message: '删除成功' });
    }
    
    const data = await response.json();
    return NextResponse.json(data, { status: response.status });
  } catch (error) {
    console.error('Delete share error:', error);
    return NextResponse.json({ error: '删除分享失败' }, { status: 500 });
  }
}
