'use client';

import { useState, useRef, useEffect, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import { backendFetch } from '@/lib/backend-proxy';
import { Send, Trash2, Bot, User, Loader2, ArrowLeft, Scissors, Cloud } from 'lucide-react';
import { getCurrentBrand, BRANDS } from '@/lib/brand';
import { cn } from '@/lib/utils';
import MarkdownRenderer from '@/components/MarkdownRenderer';

interface Message {
  id: string;
  role: 'user' | 'assistant' | 'system';
  content: string;
  timestamp: Date;
}

export default function MarketingChatPage() {
  const router = useRouter();
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
      // 后端不可用时降级，不强制跳转
      backendFetch('/auth/session').then(res => {
        if (res.status === 502) {
          setCompany('宝娜斯集团');
          setUserId('local');
        } else {
          window.location.href = '/login';
        }
      }).catch(() => {
        setCompany('宝娜斯集团');
        setUserId('local');
      });
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
    } catch {
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

  const [brand, setBrand] = useState(BRANDS.yingyun);
  const BrandIcon = brand.key === 'bonasi' ? Scissors : Cloud;

  useEffect(() => {
    setBrand(getCurrentBrand());
  }, []);

  return (
    <div className="h-screen flex flex-col bg-[#f8f9fc]">
      {/* Header */}
      <div className="flex-shrink-0 border-b border-[rgba(229,229,234,0.5)] bg-white/95 backdrop-blur-xl">
        <div className="max-w-4xl mx-auto px-5 py-2.5 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <button
              onClick={() => { localStorage.setItem('back_to_portal', 'true'); router.push('/login'); }}
              className="flex items-center gap-1 p-1.5 rounded-lg hover:bg-[rgba(118,118,128,0.08)] text-[#8e8e93] hover:text-[#8e8e93] transition-colors"
              title="返回主页"
            >
              <ArrowLeft className="w-4 h-4" />
              <span className="text-xs">返回</span>
            </button>
            <div className="flex items-center gap-2.5">
              <div className={cn(
                "w-8 h-8 rounded-lg flex items-center justify-center shadow-sm",
                brand.primaryBg
              )}>
                <BrandIcon className="w-4 h-4 text-[#1C1C1E]" />
              </div>
              <div>
                <h1 className="text-[14px] font-semibold text-[#8e8e93] leading-tight">{brand.name}市场营销AI助手</h1>
                <p className="text-[11px] text-[#8e8e93]">无缝针织行业专属</p>
              </div>
            </div>
          </div>
          <button
            onClick={handleClear}
            className="flex items-center gap-1.5 px-2.5 py-1 text-[12px] text-[#8e8e93] hover:text-[#ff3b30] hover:bg-[rgba(255,59,48,0.1)] rounded-lg transition-colors"
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
                brand.primaryLight
              )}>
                <BrandIcon className={cn("w-10 h-10", brand.primarySolid)} />
              </div>
              <h2 className="text-lg font-semibold text-[#1C1C1E] mb-1.5">{brand.name}市场营销AI助手</h2>
              <p className="text-[13px] text-[#8e8e93] mb-6 max-w-md">
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
                    className="px-3 py-2.5 text-[13px] text-left text-[#3A3A3C] bg-white border border-[rgba(229,229,234,0.6)] rounded-lg hover:border-[#ff9500] hover:bg-[rgba(255,149,0,0.03)] hover:text-[#1C1C1E] transition-all"
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
                <div className={cn("flex-shrink-0 w-7 h-7 rounded-lg flex items-center justify-center shadow-sm", brand.primaryBg)}>
                  <Bot className="w-3.5 h-3.5 text-white" />
                </div>
              )}
              <div className={`max-w-[80%] rounded-xl px-3.5 py-2.5 text-[13px] leading-relaxed ${
                msg.role === 'user'
                  ? cn('text-white shadow-sm', brand.primaryBg)
                  : 'bg-white border border-[rgba(229,229,234,0.6)] text-[#3A3A3C] shadow-sm'
              }`}>
                <div className={msg.role === 'user' ? 'whitespace-pre-wrap break-words' : ''}>
                  {msg.role === 'user' ? (
                    msg.content || (
                      <span className="inline-flex items-center gap-1 text-white/70">
                        <Loader2 className="w-3 h-3 animate-spin" />
                        发送中...
                      </span>
                    )
                  ) : (
                    <MarkdownRenderer content={msg.content || ''} />
                  )}
                </div>
              </div>
              {msg.role === 'user' && (
                <div className="flex-shrink-0 w-7 h-7 rounded-lg bg-[rgba(0,0,0,0.048)] flex items-center justify-center">
                  <User className="w-3.5 h-3.5 text-[#8e8e93]" />
                </div>
              )}
            </div>
          ))}
          <div ref={messagesEndRef} />
        </div>
      </div>

      {/* Input */}
      <div className="flex-shrink-0 border-t border-[rgba(229,229,234,0.5)] bg-white/95 backdrop-blur-xl">
        <div className="max-w-3xl mx-auto px-4 py-3">
          <div className="flex gap-2 items-end">
            <textarea
              ref={inputRef}
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder="输入您的市场营销问题..."
              rows={1}
              className="flex-1 resize-none rounded-lg border border-[rgba(229,229,234,0.6)] bg-[rgba(242,242,247,0.5)] px-3.5 py-2.5 text-[13px] text-[#8e8e93] placeholder:text-[#8e8e93] focus:outline-none focus:ring-2 focus:ring-[rgba(255,149,0,0.15)] focus:border-[#ff9500] focus:bg-white transition-all"
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
                "flex-shrink-0 w-9 h-9 rounded-lg text-white flex items-center justify-center shadow-sm",
                "hover:shadow-md disabled:opacity-40 disabled:shadow-none transition-all",
                brand.primaryBg
              )}
            >
              {isLoading ? <Loader2 className="w-4 h-4 animate-spin" /> : <Send className="w-4 h-4" />}
            </button>
          </div>
          <p className="text-xs text-[#8e8e93] mt-2 text-center">
            基于MiniMax大模型 · 专注无缝针织行业市场营销
          </p>
        </div>
      </div>
    </div>
  );
}
