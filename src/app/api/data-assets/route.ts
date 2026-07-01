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
    if (!res.ok) return null;
    return await res.json();
  } catch {
    return null;
  }
}

export async function GET(request: NextRequest) {
  const sessionId = request.cookies.get('session_id')?.value;
  const cookieHeader = request.headers.get('cookie') || '';

  const [docsRes, knowledgeRes, imagesRes, memoryRes] = await Promise.all([
    fetchBackend('/documents?category=all&pageSize=500', sessionId, cookieHeader),
    fetchBackend('/knowledge/docs?page=0&size=500', sessionId, cookieHeader),
    fetchBackend('/images?pageSize=500', sessionId, cookieHeader),
    fetchBackend('/memory/documents', sessionId, cookieHeader),
  ]);

  const documents = Array.isArray(docsRes?.data) ? docsRes.data : Array.isArray(docsRes) ? docsRes : [];
  const knowledgeDocs = Array.isArray(knowledgeRes?.data?.content)
    ? knowledgeRes.data.content
    : Array.isArray(knowledgeRes?.data)
      ? knowledgeRes.data
      : Array.isArray(knowledgeRes)
        ? knowledgeRes
        : [];
  const images = Array.isArray(imagesRes?.data) ? imagesRes.data : Array.isArray(imagesRes) ? imagesRes : [];
  const memoryDocs = Array.isArray(memoryRes?.data) ? memoryRes.data : Array.isArray(memoryRes) ? memoryRes : [];

  const assets: any[] = [];

  // 文档中心 -> document
  documents.forEach((d: any) => {
    assets.push({
      id: `doc-${d.id}`,
      name: d.name || d.originalName || '未命名文档',
      type: 'document',
      category: d.category === 'pdf' ? 'PDF文档' : d.category === 'word' ? 'Word文档' : d.category === 'excel' ? 'Excel表格' : d.category === 'ppt' ? 'PPT演示' : d.category === 'zip' ? '压缩包' : '其他文档',
      tags: [d.extension, d.category].filter(Boolean),
      quality: d.size > 0 ? 'high' : 'medium',
      qualityScore: d.size > 5 * 1024 * 1024 ? 85 : d.size > 1024 * 1024 ? 78 : 65,
      size: d.size || 0,
      createdAt: d.createdAt || d.created_at || new Date().toISOString(),
      updatedAt: d.updatedAt || d.updated_at || new Date().toISOString(),
      lastAccessedAt: d.updatedAt || d.updated_at || new Date().toISOString(),
      accessCount: 0,
      lineage: { sources: [], targets: [] },
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
      tags: d.tags || [],
      quality: d.qualityScore > 80 ? 'high' : d.qualityScore > 50 ? 'medium' : 'low',
      qualityScore: d.qualityScore || 75,
      size: d.fileSize || d.size || 0,
      createdAt: d.createdAt || d.created_at || new Date().toISOString(),
      updatedAt: d.updatedAt || d.updated_at || new Date().toISOString(),
      lastAccessedAt: d.updatedAt || d.updated_at || new Date().toISOString(),
      accessCount: d.accessCount || 0,
      lineage: { sources: d.sourceUrl ? [d.sourceUrl] : [], targets: [] },
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
      tags: img.tags || [],
      quality: img.size > 2 * 1024 * 1024 ? 'high' : 'medium',
      qualityScore: img.size > 5 * 1024 * 1024 ? 90 : img.size > 1024 * 1024 ? 80 : 70,
      size: img.size || img.fileSize || 0,
      createdAt: img.createdAt || img.created_at || new Date().toISOString(),
      updatedAt: img.updatedAt || img.updated_at || new Date().toISOString(),
      lastAccessedAt: img.updatedAt || img.updated_at || new Date().toISOString(),
      accessCount: img.viewCount || img.accessCount || 0,
      lineage: { sources: [], targets: [] },
      owner: img.userId ? String(img.userId) : '系统',
      status: img.deleted ? 'archived' : 'active',
      format: (img.extension || img.format || 'JPG').toUpperCase(),
      vectorized: false,
      embeddingStatus: 'SKIPPED',
      sourceUrl: img.url,
    });
  });

  // 记忆库 -> memory
  memoryDocs.forEach((d: any) => {
    assets.push({
      id: `mem-${d.id}`,
      name: d.title || d.name || '未命名记忆',
      type: 'memory',
      category: d.domainName || d.domain?.name || '记忆库',
      tags: d.tags || [],
      quality: 'high',
      qualityScore: 88,
      size: d.content?.length * 2 || d.size || 0,
      createdAt: d.createdAt || d.created_at || new Date().toISOString(),
      updatedAt: d.updatedAt || d.updated_at || new Date().toISOString(),
      lastAccessedAt: d.updatedAt || d.updated_at || new Date().toISOString(),
      accessCount: d.accessCount || 0,
      lineage: { sources: d.sourceDocument ? [d.sourceDocument] : [], targets: ['AI对话'] },
      owner: d.createdBy || '系统',
      status: 'active',
      format: 'TXT',
      vectorized: true,
      embeddingStatus: 'COMPLETED',
      sourceUrl: d.sourceUrl,
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
        memory: assets.filter((a) => a.type === 'memory').length,
      },
    },
  });
}
