import { NextRequest } from 'next/server';
import { getPool } from '@/lib/db';
import { EmbeddingClient, LLMClient, HeaderUtils, Config } from 'coze-coding-dev-sdk';

export async function POST(request: NextRequest) {
  try {
    const { message, history = [], domainCode } = await request.json();

    if (!message) {
      return new Response(JSON.stringify({ error: '请提供 message 参数' }), {
        status: 400,
        headers: { 'Content-Type': 'application/json' },
      });
    }

    const customHeaders = HeaderUtils.extractForwardHeaders(request.headers);

    // 1. 生成查询向量
    const embeddingClient = new EmbeddingClient(undefined, customHeaders);
    const queryEmbedding = await embeddingClient.embedText(message, { dimensions: 1024 });

    // 2. 在 pgvector 中做语义检索
    const dbClient = await getPool().connect();
    let knowledgeContext = '';
    let sources: Array<{ id: string; title: string; domain: string; score: number }> = [];

    try {
      const queryVector = `[${queryEmbedding.join(',')}]`;
      let domainFilter = '';
      const params: string[] = [];
      let paramIndex = 1;

      if (domainCode) {
        domainFilter = ` AND kc.domain_code = $${paramIndex++}`;
        params.push(domainCode);
      }

      const result = await dbClient.query(
        `SELECT 
          kc.id, kc.title, kc.domain_code, kc.content,
          kd.name as domain_name,
          1 - (ke.embedding <=> $${paramIndex}::vector) as similarity
        FROM knowledge_embeddings ke
        JOIN knowledge_cards kc ON ke.card_id = kc.id
        LEFT JOIN knowledge_domains kd ON kc.domain_code = kd.code
        WHERE kc.status = 'published'${domainFilter}
        ORDER BY ke.embedding <=> $${paramIndex}::vector
        LIMIT 8`,
        [...params, queryVector]
      );

      const relevantResults = result.rows.filter(
        (row: Record<string, unknown>) => parseFloat(row.similarity as string) >= 0.3
      );

      if (relevantResults.length > 0) {
        knowledgeContext = relevantResults
          .map((row: { title: string; content: string; domain_name: string; similarity: string }, i: number) =>
            `[${i + 1}] 【${row.domain_name}】${row.title}\n${row.content}`
          )
          .join('\n\n');

        sources = relevantResults.map((row: { id: string; title: string; domain_name: string; similarity: string }) => ({
          id: row.id,
          title: row.title,
          domain: row.domain_name,
          score: parseFloat(parseFloat(row.similarity).toFixed(4)),
        }));
      }
    } finally {
      dbClient.release();
    }

    // 3. 构建系统提示词
    const systemPrompt = `你是盈云产品智能中台的AI助手，基于本地向量数据库和记忆库为用户提供专业解答。

你的职责：
1. 优先基于知识库中检索到的参考资料回答
2. 回答要准确、简洁、专业
3. 如果涉及具体数据，请标注来源
4. 如果知识库资料不足，明确说明并补充你的建议
5. 所有回答需标注来源（知识库参考 / 通用知识补充）

${knowledgeContext ? `以下是从记忆库中检索到的相关参考资料：

---记忆库参考资料---
${knowledgeContext}
---参考资料结束---

请基于以上参考资料回答，引用时标注[序号]。` : '记忆库中暂无与该问题相关的资料，请根据你的通用知识回答，并明确说明来源。'}`;

    // 4. 构建消息列表
    const messages: Array<{ role: 'system' | 'user' | 'assistant'; content: string }> = [
      { role: 'system', content: systemPrompt },
    ];

    for (const msg of history) {
      if (msg.role === 'user' || msg.role === 'assistant') {
        messages.push({ role: msg.role, content: msg.content });
      }
    }
    messages.push({ role: 'user', content: message });

    // 5. 调用 LLM 流式输出
    const llmClient = new LLMClient(new Config(), customHeaders);

    const stream = llmClient.stream(messages, {
      model: 'doubao-seed-2-0-lite-260215',
      temperature: 0.7,
    });

    // 6. 流式输出 + 附加来源信息
    const encoder = new TextEncoder();
    const readableStream = new ReadableStream({
      async start(controller) {
        try {
          // 先发送来源信息
          if (sources.length > 0) {
            controller.enqueue(encoder.encode(
              `data: ${JSON.stringify({ type: 'sources', sources })}\n\n`
            ));
          }

          for await (const chunk of stream) {
            if (chunk.content) {
              const text = chunk.content.toString();
              controller.enqueue(encoder.encode(
                `data: ${JSON.stringify({ type: 'content', content: text })}\n\n`
              ));
            }
          }
          controller.enqueue(encoder.encode('data: [DONE]\n\n'));
          controller.close();
        } catch (err) {
          console.error('[Memory Chat] 流式输出错误:', err);
          controller.enqueue(encoder.encode(
            `data: ${JSON.stringify({ type: 'error', error: '生成失败' })}\n\n`
          ));
          controller.close();
        }
      },
    });

    return new Response(readableStream, {
      headers: {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache',
        Connection: 'keep-alive',
      },
    });
  } catch (error: unknown) {
    const msg = error instanceof Error ? error.message : '未知错误';
    console.error('[Memory Chat] 对话失败:', msg);
    return new Response(JSON.stringify({ error: '对话失败', details: msg }), {
      status: 500,
      headers: { 'Content-Type': 'application/json' },
    });
  }
}
