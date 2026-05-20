import { NextRequest, NextResponse } from 'next/server';
import { backendFetch, isBackendAvailable } from '@/lib/backend-proxy';

// Mock 分享链接数据存储（降级模式使用）
const mockShareLinks: Map<string, unknown[]> = new Map();

// 生成随机分享码
function generateShareCode(): string {
  const chars = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
  let code = '';
  for (let i = 0; i < 8; i++) {
    code += chars.charAt(Math.floor(Math.random() * chars.length));
  }
  return code;
}

// 创建分享链接
export async function POST(request: NextRequest) {
  try {
    const body = await request.json();
    const { resourceType, resourceId, resourceName, password, expireDays } = body;
    
    // 检查后端是否可用
    const backendAvailable = await isBackendAvailable();
    
    if (!backendAvailable) {
      // 降级模式：使用 mock 数据
      const shareCode = generateShareCode();
      const now = new Date();
      const expiresAt = expireDays 
        ? new Date(now.getTime() + expireDays * 24 * 60 * 60 * 1000).toISOString()
        : null;
      
      const shareLink = {
        id: `share-${Date.now()}`,
        shareCode,
        resourceType,
        resourceId,
        resourceName: resourceName || '未命名资源',
        password: password || null,
        expiresAt,
        accessCount: 0,
        createdAt: now.toISOString(),
      };
      
      // 存储到 mock 数据中
      const key = `${resourceType}:${resourceId}`;
      const links = mockShareLinks.get(key) || [];
      links.push(shareLink);
      mockShareLinks.set(key, links);
      
      return NextResponse.json({ 
        success: true, 
        shareCode,
        shareLink,
        message: '分享链接创建成功（降级模式）'
      });
    }
    
    // 后端可用，调用后端 API
    const sessionId = request.cookies.get('session_id')?.value;
    
    const response = await backendFetch('/share', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Session-Id': sessionId || '',
      },
      body: JSON.stringify(body),
    });

    const data = await response.json();
    
    // 后端直接返回 ShareLinkDTO，包装成前端期望的格式
    // 注意：忽略后端返回的 shareUrl，使用前端域名生成正确的链接
    if (response.ok && data.shareCode) {
      // 使用环境变量或前端域名生成分享链接
      const frontendDomain = process.env.COZE_PROJECT_DOMAIN_DEFAULT || '';
      const shareUrl = frontendDomain 
        ? `https://${frontendDomain}/share/${data.shareCode}`
        : `/share/${data.shareCode}`;
      
      // 统一字段名：后端 expireAt -> 前端 expiresAt
      const shareLink = {
        ...data,
        expiresAt: data.expireAt || null,
      };
      
      return NextResponse.json({
        success: true,
        shareCode: data.shareCode,
        shareUrl,
        shareLink,
        message: '分享链接创建成功'
      });
    }
    
    return NextResponse.json(data, { status: response.status });
  } catch (error) {
    console.error('Create share error:', error);
    return NextResponse.json({ error: '创建分享失败' }, { status: 500 });
  }
}

// 获取分享链接列表
export async function GET(request: NextRequest) {
  try {
    const { searchParams } = new URL(request.url);
    const resourceType = searchParams.get('resourceType') || '';
    const resourceId = searchParams.get('resourceId') || '';
    const page = searchParams.get('page') || '1';
    const pageSize = searchParams.get('pageSize') || '20';
    
    // 检查后端是否可用
    const backendAvailable = await isBackendAvailable();
    
    if (!backendAvailable) {
      // 降级模式：从 mock 数据返回
      const key = `${resourceType}:${resourceId}`;
      let links = mockShareLinks.get(key) || [];
      
      // 如果没有找到特定资源的链接，返回空数组
      if (resourceId && links.length === 0) {
        // 生成一些模拟的分享链接用于展示
        links = [];
      }
      
      return NextResponse.json({ 
        success: true, 
        shareLinks: links,
        total: links.length,
        message: '降级模式'
      });
    }
    
    // 后端可用，调用后端 API
    const sessionId = request.cookies.get('session_id')?.value;

    const params = new URLSearchParams();
    if (resourceType) params.append('resourceType', resourceType);
    if (resourceId) params.append('resourceId', resourceId);
    params.append('page', page);
    params.append('pageSize', pageSize);

    const response = await backendFetch(`/share/my?${params.toString()}`, {
      headers: {
        'X-Session-Id': sessionId || '',
      },
    });

    const data = await response.json();
    
    // 后端返回 Page<ShareLinkDTO>，需要提取 content 数组
    let shareLinks = [];
    let total = 0;
    
    if (data.content) {
      shareLinks = data.content;
      total = data.totalElements || shareLinks.length;
    } else if (Array.isArray(data)) {
      shareLinks = data;
      total = data.length;
    }
    
    // 统一字段名：后端 expireAt -> 前端 expiresAt
    shareLinks = shareLinks.map((link: { expireAt?: string; expiresAt?: string }) => ({
      ...link,
      expiresAt: link.expireAt || link.expiresAt || null,
    }));
    
    // 如果指定了 resourceId，过滤结果
    if (resourceId) {
      shareLinks = shareLinks.filter(
        (link: { resourceId: string }) => link.resourceId === resourceId
      );
      total = shareLinks.length;
    }
    
    return NextResponse.json({ 
      success: true, 
      shareLinks, 
      total,
      message: '获取成功'
    });
  } catch (error) {
    console.error('Get shares error:', error);
    return NextResponse.json({ error: '获取分享列表失败' }, { status: 500 });
  }
}

// 删除分享链接
export async function DELETE(request: NextRequest) {
  try {
    const { searchParams } = new URL(request.url);
    const shareId = searchParams.get('shareId') || '';
    
    // 检查后端是否可用
    const backendAvailable = await isBackendAvailable();
    
    if (!backendAvailable) {
      // 降级模式：从 mock 数据中删除
      for (const [key, links] of mockShareLinks.entries()) {
        const index = links.findIndex((link: unknown) => (link as { id: string }).id === shareId);
        if (index !== -1) {
          links.splice(index, 1);
          mockShareLinks.set(key, links);
          break;
        }
      }
      return NextResponse.json({ success: true, message: '删除成功（降级模式）' });
    }
    
    // 后端可用，调用后端 API
    const sessionId = request.cookies.get('session_id')?.value;

    const response = await backendFetch(`/share/${shareId}`, {
      method: 'DELETE',
      headers: {
        'X-Session-Id': sessionId || '',
      },
    });

    const data = await response.json();
    return NextResponse.json(data, { status: response.status });
  } catch (error) {
    console.error('Delete share error:', error);
    return NextResponse.json({ error: '删除分享失败' }, { status: 500 });
  }
}
