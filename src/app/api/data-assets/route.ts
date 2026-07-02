import { NextRequest, NextResponse } from 'next/server';

function getBackendUrl(): string {
  if (process.env.BACKEND_API_URL) return process.env.BACKEND_API_URL;
  if (process.env.NEXT_PUBLIC_BACKEND_API_URL) return process.env.NEXT_PUBLIC_BACKEND_API_URL;
  return 'http://localhost:8080/api';
}

async function fetchBackend(
  path: string,
  sessionId: string | undefined,
  cookie: string | undefined
) {
  const backendUrl = getBackendUrl();
  const targetUrl = `${backendUrl}${path}`;
  const headers: Record<string, string> = {};
  if (sessionId) {
    headers['X-Session-Id'] = sessionId;
    headers['Cookie'] = `session_id=${sessionId}`;
  } else if (cookie) {
    headers['Cookie'] = cookie;
  }
  try {
    const res = await fetch(targetUrl, {
      headers,
      signal: AbortSignal.timeout(15000),
    });
    const text = await res.text();
    if (!text) return null;
    try { return JSON.parse(text); } catch { return null; }
  } catch {
    return null;
  }
}

export async function GET(request: NextRequest) {
  const sessionId = request.cookies.get('session_id')?.value;
  const cookieHeader = request.headers.get('cookie') || '';

  const [docsRes, knowledgeRes, imagesRes] = await Promise.all([
    fetchBackend('/documents?category=all&pageSize=500', sessionId, cookieHeader),
    fetchBackend('/knowledge/docs?page=0&size=500', sessionId, cookieHeader),
    fetchBackend('/images?pageSize=500', sessionId, cookieHeader),
  ]);

  // 通用：提取数组，兼容 { data: { content: [...] } }, { data: [...] }, { content: [...] }, 直接数组
  function extractList(res: any): any[] {
    if (!res) return [];
    if (res.success === false) return [];
    const d = res.data ?? res;
    if (Array.isArray(d)) return d;
    if (d && typeof d === 'object') {
      if (Array.isArray(d.content)) return d.content;
      if (Array.isArray(d.documents)) return d.documents;
      if (Array.isArray(d.images)) return d.images;
      if (Array.isArray(d.list)) return d.list;
    }
    return [];
  }
  const documents = extractList(docsRes);
  const knowledgeDocs = extractList(knowledgeRes);
  const images = extractList(imagesRes);

  const assets: any[] = [];

  // 文档中心 -> document
  documents.forEach((d: any) => {
    assets.push({
      id: `doc-${d.id}`,
      name: d.name || d.originalName || '未命名文档',
      type: 'document',
      category: d.category === 'pdf' ? 'PDF文档' : d.category === 'word' ? 'Word文档' : d.category === 'excel' ? 'Excel表格' : d.category === 'ppt' ? 'PPT演示' : d.category === 'zip' ? '压缩包' : '其他文档',
      tags: [...new Set([d.extension, d.category].filter(Boolean))],
      quality: d.size > 0 ? 'high' : 'medium',
      qualityScore: d.size > 5 * 1024 * 1024 ? 85 : d.size > 1024 * 1024 ? 78 : 65,
      size: d.size || 0,
      createdAt: d.createdAt || d.created_at || new Date().toISOString(),
      updatedAt: d.updatedAt || d.updated_at || new Date().toISOString(),
      lastAccessedAt: d.updatedAt || d.updated_at || new Date().toISOString(),
      accessCount: 0,
      lineage: { sources: ['用户上传', '文档中心'], targets: ['文档中心', '知识库检索'] },
      owner: d.userId ? String(d.userId) : '系统',
      status: d.deleted ? 'archived' : 'active',
      format: (d.extension || d.category || 'unknown').toUpperCase(),
      vectorized: false,
      embeddingStatus: 'SKIPPED',
      sourceUrl: d.url,
    });
  });

  // 知识库 -> knowledge
  knowledgeDocs.forEach((d: any) => {
    assets.push({
      id: `kb-${d.id}`,
      name: d.title || d.name || '未命名知识',
      type: 'knowledge',
      category: d.categoryName || d.category?.name || '知识库',
      tags: [...new Set(d.tags || [])],
      quality: d.qualityScore > 80 ? 'high' : d.qualityScore > 50 ? 'medium' : 'low',
      qualityScore: d.qualityScore || 75,
      size: d.fileSize || d.size || 0,
      createdAt: d.createdAt || d.created_at || new Date().toISOString(),
      updatedAt: d.updatedAt || d.updated_at || new Date().toISOString(),
      lastAccessedAt: d.updatedAt || d.updated_at || new Date().toISOString(),
      accessCount: d.accessCount || 0,
      lineage: { sources: ['文档上传', '知识库录入'], targets: ['RAG检索', 'AI对话', '知识库搜索'] },
      owner: d.createdBy || d.userId ? String(d.userId) : '系统',
      status: d.embeddingStatus === 'COMPLETED' ? 'active' : d.embeddingStatus === 'PROCESSING' ? 'processing' : 'active',
      format: d.fileType || d.format || 'TXT',
      vectorized: d.embeddingStatus === 'COMPLETED',
      embeddingStatus: d.embeddingStatus || 'PENDING',
      sourceUrl: d.sourceUrl,
    });
  });

  // 图片/知识 -> image
  images.forEach((img: any) => {
    assets.push({
      id: `img-${img.id}`,
      name: img.title || img.name || img.originalName || '未命名图片',
      type: 'image',
      category: img.albumName || img.album?.name || '图片库',
      tags: [...new Set(img.tags || [])],
      quality: img.size > 2 * 1024 * 1024 ? 'high' : 'medium',
      qualityScore: img.size > 5 * 1024 * 1024 ? 90 : img.size > 1024 * 1024 ? 80 : 70,
      size: img.size || img.fileSize || 0,
      createdAt: img.createdAt || img.created_at || new Date().toISOString(),
      updatedAt: img.updatedAt || img.updated_at || new Date().toISOString(),
      lastAccessedAt: img.updatedAt || img.updated_at || new Date().toISOString(),
      accessCount: img.viewCount || img.accessCount || 0,
      lineage: { sources: ['用户上传', '商品图片采集'], targets: ['商品管理', 'AI识别', '图片预览'] },
      owner: img.userId ? String(img.userId) : '系统',
      status: img.deleted ? 'archived' : 'active',
      format: (img.extension || img.format || 'JPG').toUpperCase(),
      vectorized: false,
      embeddingStatus: 'SKIPPED',
      sourceUrl: img.url,
    });
  });

  return NextResponse.json({
    success: true,
    data: assets,
    stats: {
      total: assets.length,
      byType: {
        knowledge: assets.filter((a) => a.type === 'knowledge').length,
        document: assets.filter((a) => a.type === 'document').length,
        image: assets.filter((a) => a.type === 'image').length,
      },
    },
  });
}
