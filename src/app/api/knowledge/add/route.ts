import { NextRequest, NextResponse } from 'next/server';
import { KnowledgeClient, Config, KnowledgeDocument, DataSourceType } from 'coze-coding-dev-sdk';

export async function POST(request: NextRequest) {
  try {
    const body = await request.json();
    const { content, url, uri, datasetName = 'coze_doc_knowledge' } = body;

    if (!content && !url && !uri) {
      return NextResponse.json({ error: '请提供 content、url 或 uri' }, { status: 400 });
    }

    const config = new Config();
    const client = new KnowledgeClient(config);

    const docs: KnowledgeDocument[] = [];

    if (content) {
      docs.push({ source: DataSourceType.TEXT, raw_data: content });
    }
    if (url) {
      docs.push({ source: DataSourceType.URL, url });
    }
    if (uri) {
      docs.push({ source: DataSourceType.URI, uri });
    }

    const response = await client.addDocuments(docs, datasetName, {
      separator: '\n\n',
      max_tokens: 2000,
      remove_extra_spaces: true,
    });

    if (response.code === 0) {
      return NextResponse.json({
        success: true,
        doc_ids: response.doc_ids,
        message: `成功导入 ${response.doc_ids?.length || 0} 篇文档`,
      });
    } else {
      return NextResponse.json({ error: response.msg || '导入失败' }, { status: 500 });
    }
  } catch (error: unknown) {
    const msg = error instanceof Error ? error.message : '未知错误';
    console.error('[Knowledge Add] 导入失败:', msg);
    return NextResponse.json({ error: '导入失败', details: msg }, { status: 500 });
  }
}
