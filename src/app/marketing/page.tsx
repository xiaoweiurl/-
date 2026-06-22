'use client';

import { useState, useRef, useEffect, useCallback } from 'react';
import { Send, Trash2, Bot, User, Sparkles, Loader2, ArrowLeft, Scissors, Cloud } from 'lucide-react';
import { getCurrentBrand } from '@/lib/brand';
import { cn } from '@/lib/utils';

interface Message {
  id: string;
  role: 'user' | 'assistant' | 'system';
  content: string;
  timestamp: Date;
}

const SYSTEM_PROMPT = `你是一位深耕无缝针织行业的市场营销专家AI助手。你具备以下专业能力：

1. **行业洞察**：熟悉无缝针织行业的上下游产业链，包括原材料（棉、莫代尔、锦纶、氨纶、涤纶等）、设备（以意大利圣东尼电子无缝针织机为代表）、工艺技术等。

2. **市场分析**：擅长无缝针织产品的市场趋势分析，涵盖内衣、运动服饰、泳装、塑身衣、医疗绷带、瑜伽服等细分领域。

3. **品牌策略**：能够为无缝针织品牌提供定位、差异化、渠道策略等品牌营销建议。

4. **产品推广**：熟悉线上线下营销渠道，擅长社交媒体营销、内容营销、KOL合作等推广方式。

5. **数据分析**：能基于市场数据提供销售预测、定价策略、库存优化等数据驱动的建议。

请用专业、务实、有洞察力的方式回答用户的问题，优先给出可落地的行动建议。`;

export default function MarketingChatPage() {
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [company, setCompany] = useState('');
  const [userId, setUserId] = useState('');
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    const storedCompany = localStorage.getItem('user_company') || '';
    const storedUserId = localStorage.getItem('user_id') || '';
    if (!storedCompany || !storedUserId) {
      window.location.href = '/login';
      return;
    }
    setCompany(storedCompany);
    setUserId(storedUserId);
    loadHistory(storedUserId, storedCompany);
  }, []);

  const loadHistory = async (uid: string, comp: string) => {
    try {
      const sessionId = localStorage.getItem('session_id');
      const res = await fetch(`/api/marketing/chat/history?userId=${encodeURIComponent(uid)}&company=${encodeURIComponent(comp)}`, {
        headers: { 'X-Session-Id': sessionId || '' }
      });
      const data = await res.json();
      if (data.success && data.history?.length > 0) {
        const historyMessages: Message[] = data.history.map((h: { role: string; content: string; created_at: string }) => ({
          id: Math.random().toString(36).slice(2),
          role: h.role as 'user' | 'assistant',
          content: h.content,
          timestamp: new Date(h.created_at)
        }));
        setMessages(historyMessages);
      }
    } catch {
      // silent
    }
  };

  const scrollToBottom = useCallback(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, []);

  useEffect(() => {
    scrollToBottom();
  }, [messages, scrollToBottom]);

  const handleSend = async () => {
    const trimmed = input.trim();
    if (!trimmed || isLoading) return;

    const userMsg: Message = {
      id: Math.random().toString(36).slice(2),
      role: 'user',
      content: trimmed,
      timestamp: new Date()
    };

    setMessages(prev => [...prev, userMsg]);
    setInput('');
    setIsLoading(true);

    const assistantMsg: Message = {
      id: Math.random().toString(36).slice(2),
      role: 'assistant',
      content: '',
      timestamp: new Date()
    };
    setMessages(prev => [...prev, assistantMsg]);

    try {
      const sessionId = localStorage.getItem('session_id');
      const res = await fetch('/api/marketing/chat', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-Session-Id': sessionId || ''
        },
        body: JSON.stringify({ message: trimmed, userId, company })
      });

      if (!res.ok) throw new Error('请求失败');

      const reader = res.body?.getReader();
      if (!reader) throw new Error('无法读取响应');

      const decoder = new TextDecoder();
      let fullContent = '';
      let buffer = '';

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        // Keep the last incomplete line in buffer
        buffer = lines.pop() || '';

        for (const line of lines) {
          const trimmed = line.trim();
          // Skip event type lines and empty lines
          if (trimmed.startsWith('event:') || trimmed === '') continue;

          if (trimmed.startsWith('data:')) {
            const data = trimmed.substring(5).trim();
            if (!data || data === '[DONE]') continue;
            try {
              const parsed = JSON.parse(data);
              if (parsed.type === 'content' && parsed.content) {
                // Backend wrapped format: {"type":"content","content":"xxx"}
                fullContent += parsed.content;
                setMessages(prev => {
                  const updated = [...prev];
                  updated[updated.length - 1] = {
                    ...updated[updated.length - 1],
                    content: fullContent
                  };
                  return updated;
                });
              } else if (parsed.type === 'error') {
                fullContent += parsed.content || '对话出错';
                setMessages(prev => {
                  const updated = [...prev];
                  updated[updated.length - 1] = {
                    ...updated[updated.length - 1],
                    content: fullContent
                  };
                  return updated;
                });
              } else if (parsed.type === 'done') {
                // Stream completed
              } else if (parsed.choices?.[0]?.delta?.content) {
                // Fallback: raw MiniMax v2 format
                fullContent += parsed.choices[0].delta.content;
                setMessages(prev => {
                  const updated = [...prev];
                  updated[updated.length - 1] = {
                    ...updated[updated.length - 1],
                    content: fullContent
                  };
                  return updated;
                });
              }
            } catch {
              // skip non-json
            }
          }
        }
      }

      if (!fullContent) {
        setMessages(prev => {
          const updated = [...prev];
          updated[updated.length - 1] = {
            ...updated[updated.length - 1],
            content: '抱歉，我暂时无法回答这个问题，请稍后重试。'
          };
          return updated;
        });
      }
    } catch (err) {
      setMessages(prev => {
        const updated = [...prev];
        updated[updated.length - 1] = {
          ...updated[updated.length - 1],
          content: '网络错误，请检查连接后重试。'
        };
        return updated;
      });
    } finally {
      setIsLoading(false);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const handleClear = async () => {
    try {
      const sessionId = localStorage.getItem('session_id');
      await fetch('/api/marketing/chat/history?userId=' + encodeURIComponent(userId) + '&company=' + encodeURIComponent(company), {
        method: 'DELETE',
        headers: { 'X-Session-Id': sessionId || '' }
      });
    } catch {
      // silent
    }
    setMessages([]);
  };

  const handleLogout = () => {
    localStorage.removeItem('user_company');
    localStorage.removeItem('user_id');
    localStorage.removeItem('session_id');
    localStorage.removeItem('portal_type');
    window.location.href = '/login';
  };

  const brand = getCurrentBrand();
  const BrandIcon = brand.key === 'bonasi' ? Scissors : Cloud;

  return (
    <div className="h-screen flex flex-col bg-[#f8f9fc]">
      {/* Header */}
      <div className="flex-shrink-0 border-b border-slate-200/50 bg-white/[0.97] backdrop-blur-xl">
        <div className="max-w-4xl mx-auto px-5 py-2.5 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <button
              onClick={handleLogout}
              className="p-1.5 rounded-lg hover:bg-slate-100 text-slate-400 hover:text-slate-600 transition-colors"
              title="返回登录"
            >
              <ArrowLeft className="w-4 h-4" />
            </button>
            <div className="flex items-center gap-2.5">
              <div className={cn(
                "w-8 h-8 rounded-lg flex items-center justify-center shadow-sm",
                "bg-gradient-to-br", brand.primaryFrom, brand.primaryTo
              )}>
                <BrandIcon className="w-4 h-4 text-white" />
              </div>
              <div>
                <h1 className="text-[14px] font-semibold text-slate-800 leading-tight">{brand.name}市场营销AI助手</h1>
                <p className="text-[11px] text-slate-400">无缝针织行业专属</p>
              </div>
            </div>
          </div>
          <button
            onClick={handleClear}
            className="flex items-center gap-1.5 px-2.5 py-1 text-[12px] text-slate-400 hover:text-red-500 hover:bg-red-50 rounded-lg transition-colors"
          >
            <Trash2 className="w-3.5 h-3.5" />
            清空
          </button>
        </div>
      </div>

      {/* Messages */}
      <div className="flex-1 overflow-y-auto">
        <div className="max-w-3xl mx-auto px-4 py-6">
          {messages.length === 0 && (
            <div className="flex flex-col items-center justify-center h-full min-h-[60vh] text-center">
              <div className={cn(
                "w-20 h-20 rounded-2xl flex items-center justify-center mb-6",
                "bg-gradient-to-br", brand.primaryFrom + '/10', brand.primaryTo + '/10'
              )}>
                <BrandIcon className={cn("w-10 h-10", brand.primarySolid)} />
              </div>
              <h2 className="text-lg font-semibold text-slate-700 mb-1.5">{brand.name}市场营销AI助手</h2>
              <p className="text-[13px] text-slate-400 mb-6 max-w-md">
                专注于无缝针织行业的市场营销专家，为您提供市场分析、品牌策略、产品推广等专业建议
              </p>
              <div className="grid grid-cols-2 gap-2 w-full max-w-lg">
                {[
                  '内衣市场最新趋势分析',
                  '如何打造差异化品牌定位',
                  '瑜伽服品类推广策略',
                  '线上线下渠道如何协同',
                ].map((suggestion) => (
                  <button
                    key={suggestion}
                    onClick={() => { setInput(suggestion); }}
                    className="px-3 py-2.5 text-[13px] text-left text-slate-500 bg-white border border-slate-200/60 rounded-lg hover:border-amber-300 hover:bg-amber-50/30 hover:text-slate-700 transition-all"
                  >
                    {suggestion}
                  </button>
                ))}
              </div>
            </div>
          )}

          {messages.map((msg) => (
            <div key={msg.id} className={`flex gap-2.5 mb-4 ${msg.role === 'user' ? 'justify-end' : 'justify-start'}`}>
              {msg.role === 'assistant' && (
                <div className={cn("flex-shrink-0 w-7 h-7 rounded-lg bg-gradient-to-br flex items-center justify-center shadow-sm", brand.primaryFrom, brand.primaryTo)}>
                  <Bot className="w-3.5 h-3.5 text-white" />
                </div>
              )}
              <div className={`max-w-[80%] rounded-xl px-3.5 py-2.5 text-[13px] leading-relaxed ${
                msg.role === 'user'
                  ? cn('bg-gradient-to-r text-white shadow-sm', brand.primaryFrom, brand.primaryTo)
                  : 'bg-white border border-slate-200/60 text-slate-600 shadow-sm'
              }`}>
                <div className="whitespace-pre-wrap break-words">{msg.content || (
                  <span className="inline-flex items-center gap-1 text-slate-400">
                    <Loader2 className="w-3 h-3 animate-spin" />
                    思考中...
                  </span>
                )}</div>
              </div>
              {msg.role === 'user' && (
                <div className="flex-shrink-0 w-7 h-7 rounded-lg bg-slate-200/80 flex items-center justify-center">
                  <User className="w-3.5 h-3.5 text-slate-500" />
                </div>
              )}
            </div>
          ))}
          <div ref={messagesEndRef} />
        </div>
      </div>

      {/* Input */}
      <div className="flex-shrink-0 border-t border-slate-200/50 bg-white/[0.97] backdrop-blur-xl">
        <div className="max-w-3xl mx-auto px-4 py-3">
          <div className="flex gap-2 items-end">
            <textarea
              ref={inputRef}
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder="输入您的市场营销问题..."
              rows={1}
              className="flex-1 resize-none rounded-lg border border-slate-200/60 bg-slate-50/50 px-3.5 py-2.5 text-[13px] text-slate-700 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-amber-500/15 focus:border-amber-300 focus:bg-white transition-all"
              style={{ minHeight: '40px', maxHeight: '120px' }}
              onInput={(e) => {
                const target = e.target as HTMLTextAreaElement;
                target.style.height = 'auto';
                target.style.height = Math.min(target.scrollHeight, 120) + 'px';
              }}
            />
            <button
              onClick={handleSend}
              disabled={!input.trim() || isLoading}
              className={cn(
                "flex-shrink-0 w-9 h-9 rounded-lg bg-gradient-to-r text-white flex items-center justify-center shadow-sm",
                "hover:shadow-md disabled:opacity-40 disabled:shadow-none transition-all",
                brand.primaryFrom, brand.primaryTo
              )}
            >
              {isLoading ? <Loader2 className="w-4 h-4 animate-spin" /> : <Send className="w-4 h-4" />}
            </button>
          </div>
          <p className="text-xs text-slate-400 mt-2 text-center">
            基于MiniMax大模型 · 专注无缝针织行业市场营销
          </p>
        </div>
      </div>
    </div>
  );
}
