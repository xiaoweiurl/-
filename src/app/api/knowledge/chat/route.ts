/**
 * /api/knowledge/chat - 代理到 Java 后端 /memory/chat
 * MiniMax 流式AI对话（双库检索）
 */
import { NextRequest } from 'next/server';

function getSessionId(request: NextRequest): string | null {
  const header = request.headers.get('x-session-id');
  if (header) return header;
  const cookie = request.headers.get('cookie') || '';
  const match = cookie.match(/session_id=([^;]+)/);
  return match ? match[1] : null;
}

function getBackendUrl(): string {
  const envUrl = process.env.NEXT_PUBLIC_BACKEND_API_URL;
  return envUrl ? envUrl.replace(/\/$/, '') : 'http://localhost:8080/api';
}

export async function POST(request: NextRequest) {
  try {
    const { message, sessionId: chatSessionId, history } = await request.json();

    if (!message) {
      return new Response(JSON.stringify({ error: '请提供 message 参数' }), {
        status: 400,
        headers: { 'Content-Type': 'application/json' },
      });
    }

    const sessionId = getSessionId(request);
    const headers: Record<string, string> = {
      'Accept': 'text/event-stream',
    };
    if (sessionId) headers['X-Session-Id'] = sessionId;

    const backendUrl = getBackendUrl();
    const params = new URLSearchParams({ message });
    if (chatSessionId) params.set('sessionId', chatSessionId);

    const backendRes = await fetch(`${backendUrl}/memory/chat?${params.toString()}`, {
      method: 'GET',
      headers,
      signal: AbortSignal.timeout(180000),
    });

    if (!backendRes.ok) {
      const errText = await backendRes.text();
      return new Response(JSON.stringify({ error: `后端返回 ${backendRes.status}`, detail: errText }), {
        status: backendRes.status,
        headers: { 'Content-Type': 'application/json' },
      });
    }

    // SSE流式透传
    const reader = backendRes.body?.getReader();
    if (!reader) {
      return new Response(JSON.stringify({ error: 'SSE流为空' }), {
        status: 500,
        headers: { 'Content-Type': 'application/json' },
      });
    }

    const encoder = new TextEncoder();
    const stream = new ReadableStream({
      async start(controller) {
        try {
          while (true) {
            const { done, value } = await reader.read();
            if (done) { controller.close(); return; }
            const text = new TextDecoder().decode(value);

            // Java后端SSE格式转换为前端格式
            const lines = text.split('\n');
            for (const line of lines) {
              if (line.startsWith('data:')) {
                const data = line.slice(5).trim();
                if (data === '[DONE]') {
                  controller.enqueue(encoder.encode('data: [DONE]\n\n'));
                  continue;
                }
                try {
                  const parsed = JSON.parse(data);
                  // 转换为前端期望的 { content } 格式
                  if (parsed.content) {
                    controller.enqueue(encoder.encode(`data: ${JSON.stringify({ content: parsed.content })}\n\n`));
                  } else if (parsed.type === 'done') {
                    controller.enqueue(encoder.encode('data: [DONE]\n\n'));
                  }
                } catch {
                  // 非JSON数据，原样透传
                  controller.enqueue(encoder.encode(`${line}\n\n`));
                }
              } else if (line.startsWith('event:')) {
                // 透传事件类型
                controller.enqueue(encoder.encode(`${line}\n`));
              }
            }
          }
        } catch (e) {
          console.error('[Knowledge Chat] SSE流中断:', e);
          controller.close();
        }
      },
    });

    return new Response(stream, {
      headers: {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache',
        'Connection': 'keep-alive',
      },
    });
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '未知错误';
    console.error('[Knowledge Chat] 对话失败:', msg);
    return new Response(JSON.stringify({ error: '对话失败', details: msg }), {
      status: 500,
      headers: { 'Content-Type': 'application/json' },
    });
  }
}
