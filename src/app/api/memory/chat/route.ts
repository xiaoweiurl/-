import { NextRequest } from 'next/server';
import { LLMClient, Config, HeaderUtils } from 'coze-coding-dev-sdk';

/**
 * 记忆库 AI 对话接口
 * 
 * 使用 coze-coding-dev-sdk 内置的 MiniMax 模型，无需额外 API Key
 * 从 Java 后端获取知识卡片上下文，在 Next.js 端调用 LLM 流式生成
 */

const SYSTEM_PROMPT = `你是盈云产品智能中台的AI助手。请基于提供的知识卡片回答用户问题，标注引用来源。如果知识卡片中没有相关信息，请明确说明。回答使用中文。保持对话连贯性，参考上下文历史。`;

function getSessionId(request: NextRequest): string | null {
  const headerSession = request.headers.get('x-session-id');
  if (headerSession) return headerSession;
  const cookies = request.headers.get('cookie');
  if (cookies) {
    const match = cookies.match(/session_id=([^;]+)/);
    if (match) return match[1];
  }
  return null;
}

const getBackendUrl = () => process.env.NEXT_PUBLIC_BACKEND_API_URL || 'http://localhost:8080/api';

/**
 * 从 Java 后端获取知识卡片搜索结果
 */
async function fetchKnowledgeContext(message: string, sessionId: string | null): Promise<{
  sources: Array<{ id: string; title: string; domain: string; score: number }>;
  context: string;
}> {
  try {
    const headers: Record<string, string> = { 'Accept': 'application/json' };
    if (sessionId) headers['X-Session-Id'] = sessionId;

    const searchUrl = `${getBackendUrl()}/memory/search?query=${encodeURIComponent(message)}&topK=5&threshold=0.3`;
    const res = await fetch(searchUrl, { headers, signal: AbortSignal.timeout(10000) });

    if (!res.ok) {
      console.log('[Memory Chat] 知识检索失败:', res.status);
      return { sources: [], context: '' };
    }

    const data = await res.json();
    const results = data.results || data.data || [];

    if (!Array.isArray(results) || results.length === 0) {
      return { sources: [], context: '' };
    }

    const sources = results.map((r: Record<string, unknown>) => ({
      id: String(r.id || ''),
      title: String(r.title || ''),
      domain: String(r.domainName || r.domain || ''),
      score: Number(r.score || 0),
    }));

    const contextParts: string[] = ['## 相关知识卡片：\n'];
    results.forEach((r: Record<string, unknown>, i: number) => {
      contextParts.push(
        `### 卡片${i + 1} [${r.domainName || r.domain || ''}] ${r.title || ''}\n` +
        `${r.content || ''}\n` +
        `置信度: ${r.confidence || ''} | 来源: ${r.source || '未知'}\n\n`
      );
    });

    return { sources, context: contextParts.join('') };
  } catch (error) {
    console.error('[Memory Chat] 知识检索异常:', error);
    return { sources: [], context: '' };
  }
}

/**
 * 从 Java 后端获取对话历史
 */
async function fetchChatHistory(chatSessionId: string, userSessionId: string | null): Promise<Array<{ role: string; content: string }>> {
  try {
    const headers: Record<string, string> = { 'Accept': 'application/json' };
    if (userSessionId) headers['X-Session-Id'] = userSessionId;

    const historyUrl = `${getBackendUrl()}/memory/chat/history?sessionId=${encodeURIComponent(chatSessionId)}`;
    const res = await fetch(historyUrl, { headers, signal: AbortSignal.timeout(5000) });

    if (!res.ok) return [];

    const data = await res.json();
    const history = data.history || data.data || [];
    return Array.isArray(history) ? history : [];
  } catch {
    return [];
  }
}

/**
 * 保存对话消息到 Java 后端
 */
async function saveChatMessage(chatSessionId: string, role: string, content: string, userSessionId: string | null): Promise<void> {
  try {
    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (userSessionId) headers['X-Session-Id'] = userSessionId;

    await fetch(`${getBackendUrl()}/memory/chat/messages`, {
      method: 'POST',
      headers,
      body: JSON.stringify({ sessionId: chatSessionId, role, content }),
      signal: AbortSignal.timeout(5000),
    });
  } catch {
    // 保存失败不影响对话
  }
}

export async function GET(request: NextRequest) {
  const userSessionId = getSessionId(request);

  const url = new URL(request.url);
  const message = url.searchParams.get('message') || '';
  const chatSessionId = url.searchParams.get('sessionId') || crypto.randomUUID();

  if (!message.trim()) {
    return new Response(JSON.stringify({ error: '消息不能为空' }), {
      status: 400,
      headers: { 'Content-Type': 'application/json' },
    });
  }

  // 1. 获取知识卡片上下文
  const { sources, context } = await fetchKnowledgeContext(message, userSessionId);

  // 2. 获取对话历史
  const history = await fetchChatHistory(chatSessionId, userSessionId);

  // 3. 构建消息列表
  const messages: Array<{ role: 'system' | 'user' | 'assistant'; content: string }> = [
    { role: 'system', content: SYSTEM_PROMPT },
  ];

  // 加入历史对话（最近10轮）
  const recentHistory = history.slice(-20);
  for (const msg of recentHistory) {
    if (msg.role === 'user' || msg.role === 'assistant') {
      messages.push({ role: msg.role, content: msg.content });
    }
  }

  // 当前用户消息（带知识上下文）
  let userContent = message;
  if (context) {
    userContent = context + '\n---\n用户问题: ' + message +
      '\n\n请基于以上知识回答用户问题。如果知识卡片中没有相关信息，请说明。回答时标注引用来源。';
  }
  messages.push({ role: 'user', content: userContent });

  // 4. 保存用户消息
  saveChatMessage(chatSessionId, 'user', message, userSessionId);

  // 5. 创建 SSE 流式响应
  const encoder = new TextEncoder();
  const customHeaders = HeaderUtils.extractForwardHeaders(request.headers);
  const config = new Config();
  const client = new LLMClient(config, customHeaders);

  const stream = new ReadableStream({
    async start(controller) {
      try {
        // 先发送 sources 事件
        if (sources.length > 0) {
          controller.enqueue(encoder.encode(
            `event: message\ndata: ${JSON.stringify({ type: 'sources', sources })}\n\n`
          ));
        }

        // 流式调用 LLM
        const llmStream = client.stream(messages, {
          model: 'minimax-m2-5-260212',
          temperature: 0.7,
        });

        let fullResponse = '';

        for await (const chunk of llmStream) {
          if (chunk.content) {
            const text = chunk.content.toString();
            fullResponse += text;
            controller.enqueue(encoder.encode(
              `event: message\ndata: ${JSON.stringify({ type: 'content', content: text })}\n\n`
            ));
          }
        }

        // 发送完成事件
        controller.enqueue(encoder.encode(
          `event: message\ndata: ${JSON.stringify({ type: 'done' })}\n\n`
        ));

        // 保存 AI 回复
        saveChatMessage(chatSessionId, 'assistant', fullResponse, userSessionId);

      } catch (error) {
        const errMsg = error instanceof Error ? error.message : 'AI对话失败';
        console.error('[Memory Chat] LLM 调用失败:', errMsg);
        controller.enqueue(encoder.encode(
          `event: message\ndata: ${JSON.stringify({ type: 'error', content: 'AI对话失败: ' + errMsg })}\n\n`
        ));
      } finally {
        controller.close();
      }
    },
  });

  return new Response(stream, {
    headers: {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache',
      'Connection': 'keep-alive',
      'Access-Control-Allow-Origin': request.headers.get('origin') || '*',
      'Access-Control-Allow-Credentials': 'true',
    },
  });
}
