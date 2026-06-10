import { NextRequest } from 'next/server';
import { KnowledgeClient, Config, LLMClient, HeaderUtils } from 'coze-coding-dev-sdk';

export async function POST(request: NextRequest) {
  try {
    const { message, history = [] } = await request.json();

    if (!message) {
      return new Response(JSON.stringify({ error: '请提供 message 参数' }), {
        status: 400,
        headers: { 'Content-Type': 'application/json' },
      });
    }

    // 1. 先从知识库搜索相关内容
    const knowledgeConfig = new Config();
    const knowledgeClient = new KnowledgeClient(knowledgeConfig);
    const searchResponse = await knowledgeClient.search(message, undefined, 5, 0.3);

    let knowledgeContext = '';
    if (searchResponse.code === 0 && searchResponse.chunks.length > 0) {
      knowledgeContext = searchResponse.chunks
        .map((chunk: { content: string; score: number }, i: number) => `[${i + 1}] (相关度: ${(chunk.score * 100).toFixed(1)}%) ${chunk.content}`)
        .join('\n\n');
    }

    // 2. 构建系统提示词
    const systemPrompt = `你是盈云产品智能中台的AI助手，专注于供应链、工厂管理和产品知识领域。
${knowledgeContext ? `以下是从知识库中检索到的相关参考资料，请优先基于这些资料回答用户问题：

---知识库参考资料---
${knowledgeContext}
---参考资料结束---

请基于以上参考资料回答，如果参考资料中没有相关信息，可以结合你的通用知识回答，但要明确说明哪些来自知识库，哪些是你的补充。` : '知识库中暂无与该问题相关的资料，请根据你的通用知识回答。'}
回答要求：
1. 优先使用知识库中的资料
2. 回答要准确、简洁、专业
3. 如果涉及具体数据，请标注来源
4. 如果知识库资料不足，明确说明并补充你的建议`;

    // 3. 构建消息列表
    const messages: Array<{ role: 'system' | 'user' | 'assistant'; content: string }> = [
      { role: 'system', content: systemPrompt },
    ];

    // 添加历史对话
    for (const msg of history) {
      if (msg.role === 'user' || msg.role === 'assistant') {
        messages.push({ role: msg.role, content: msg.content });
      }
    }

    messages.push({ role: 'user', content: message });

    // 4. 调用 LLM 流式输出
    const customHeaders = HeaderUtils.extractForwardHeaders(request.headers);
    const llmConfig = new Config();
    const llmClient = new LLMClient(llmConfig, customHeaders);

    const stream = llmClient.stream(messages, {
      model: 'doubao-seed-2-0-lite-260215',
      temperature: 0.7,
    });

    const encoder = new TextEncoder();
    const readableStream = new ReadableStream({
      async start(controller) {
        try {
          for await (const chunk of stream) {
            if (chunk.content) {
              const text = chunk.content.toString();
              controller.enqueue(encoder.encode(`data: ${JSON.stringify({ content: text })}\n\n`));
            }
          }
          controller.enqueue(encoder.encode('data: [DONE]\n\n'));
          controller.close();
        } catch (err) {
          console.error('[Knowledge Chat] 流式输出错误:', err);
          controller.enqueue(encoder.encode(`data: ${JSON.stringify({ error: '生成失败' })}\n\n`));
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
    console.error('[Knowledge Chat] 对话失败:', msg);
    return new Response(JSON.stringify({ error: '对话失败', details: msg }), {
      status: 500,
      headers: { 'Content-Type': 'application/json' },
    });
  }
}
