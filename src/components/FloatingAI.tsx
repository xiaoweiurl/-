'use client';

import { useState, useRef, useEffect, useCallback } from 'react';
import { usePathname } from 'next/navigation';
import {
  MessageSquare, X, Send, Bot, User, ChevronDown,
  Copy, Check, Sparkles, Loader2, Lightbulb, Globe
} from 'lucide-react';
import MarkdownRenderer from './MarkdownRenderer';

// ===== 类型定义 =====
interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  timestamp: Date;
  isStreaming?: boolean;
  thinkingChain?: string[];
  searchResults?: { title: string; url: string; snippet: string }[];
}

// ===== 复制按钮 =====
function CopyButton({ text }: { text: string }) {
  const [copied, setCopied] = useState(false);
  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch { /* ignore */ }
  };
  return (
    <button
      onClick={handleCopy}
      className="p-1 rounded text-slate-400 hover:text-slate-200 hover:bg-white/10 transition-all"
      title="复制"
    >
      {copied ? <Check className="w-3 h-3 text-green-400" /> : <Copy className="w-3 h-3" />}
    </button>
  );
}

// ===== 获取当前板块的 mode =====
function getModeFromPath(pathname: string): { mode: string; label: string; icon: string } {
  if (pathname.startsWith('/supply-chain')) {
    return { mode: 'factory', label: '工厂供应链助手', icon: '🏭' };
  }
  if (pathname.startsWith('/market')) {
    return { mode: 'marketing', label: '市场营销助手', icon: '📊' };
  }
  return { mode: 'designer', label: '设计师AI助手', icon: '🎨' };
}

// ===== 主组件 =====
export default function FloatingAI() {
  const pathname = usePathname();
  const [isOpen, setIsOpen] = useState(false);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState('');
  const [isChatting, setIsChatting] = useState(false);
  const [thinkingExpanded, setThinkingExpanded] = useState(true);
  const [searchExpanded, setSearchExpanded] = useState(true);
  const [conversationId, setConversationId] = useState<string | null>(null);
  const [isLoadingHistory, setIsLoadingHistory] = useState(false);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const isUserScrollingRef = useRef(false);
  const chatContainerRef = useRef<HTMLDivElement>(null);

  const { mode, label, icon } = getModeFromPath(pathname);

  // 智能自动滚动
  const scrollToBottom = useCallback((force = false) => {
    if (force || !isUserScrollingRef.current) {
      messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }
  }, []);

  // 发送消息
  const handleSend = async () => {
    const text = input.trim();
    if (!text || isChatting) return;

    const userMsg: ChatMessage = {
      id: Date.now().toString(),
      role: 'user',
      content: text,
      timestamp: new Date(),
    };

    const assistantMsg: ChatMessage = {
      id: (Date.now() + 1).toString(),
      role: 'assistant',
      content: '',
      timestamp: new Date(),
      isStreaming: true,
      thinkingChain: [],
      searchResults: [],
    };

    setMessages(prev => [...prev, userMsg, assistantMsg]);
    setInput('');
    setIsChatting(true);
    isUserScrollingRef.current = false;

    setTimeout(() => {
      inputRef.current?.focus();
      scrollToBottom(true);
    }, 100);

    try {
      const res = await fetch('/api/chat/smart', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message: text, mode, conversationId }),
      });

      if (!res.ok) throw new Error(`请求失败: ${res.status}`);
      if (!res.body) throw new Error('无响应体');

      const reader = res.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';
      let fullContent = '';

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop() || '';

        for (const line of lines) {
          if (!line.trim() || !line.startsWith('data:')) continue;
          const dataStr = line.startsWith('data: ') ? line.slice(6) : line.slice(5);
          if (dataStr.trim() === '[DONE]') continue;

          try {
            const data = JSON.parse(dataStr);
            // 保存conversationId用于上下文对话
            if (data.conversationId) {
              setConversationId(data.conversationId);
            }
            if (data.content) {
              fullContent += data.content;
              setMessages(prev => prev.map(m =>
                m.id === assistantMsg.id
                  ? { ...m, content: fullContent }
                  : m
              ));
              scrollToBottom();
            }
            if (data.thinking) {
              setMessages(prev => prev.map(m =>
                m.id === assistantMsg.id
                  ? { ...m, thinkingChain: [...(m.thinkingChain || []), data.thinking] }
                  : m
              ));
            }
            if (data.searchResults) {
              setMessages(prev => prev.map(m =>
                m.id === assistantMsg.id
                  ? { ...m, searchResults: data.searchResults }
                  : m
              ));
            }
          } catch { /* skip invalid JSON */ }
        }
      }

      setMessages(prev => prev.map(m =>
        m.id === assistantMsg.id ? { ...m, isStreaming: false } : m
      ));
    } catch (err) {
      setMessages(prev => prev.map(m =>
        m.id === assistantMsg.id
          ? { ...m, content: `抱歉，请求出错：${err instanceof Error ? err.message : '未知错误'}`, isStreaming: false }
          : m
      ));
    } finally {
      setIsChatting(false);
    }
  };

  // 处理滚动
  const handleScroll = () => {
    const el = chatContainerRef.current;
    if (!el) return;
    const isNearBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 80;
    isUserScrollingRef.current = !isNearBottom;
  };

  // 切换面板时聚焦输入框
  useEffect(() => {
    if (isOpen) {
      setTimeout(() => inputRef.current?.focus(), 200);
    }
  }, [isOpen]);

  // 加载对话历史
  const loadHistory = useCallback(async (targetMode: string) => {
    try {
      setIsLoadingHistory(true);
      const res = await fetch(`/api/chat/history?mode=${targetMode}`);
      if (!res.ok) { setIsLoadingHistory(false); return; }
      const data = await res.json();
      if (data.history && data.history.length > 0) {
        const historyMessages: ChatMessage[] = data.history.map((h: { role: string; content: string; thinkingChain?: string[]; searchResults?: { title: string; url: string; snippet: string }[] }) => ({
          id: crypto.randomUUID(),
          role: h.role as 'user' | 'assistant',
          content: h.content,
          thinkingChain: h.thinkingChain || [],
          searchResults: h.searchResults || [],
          isStreaming: false,
        }));
        setMessages(historyMessages);
        setConversationId(data.conversationId || null);
        setTimeout(() => scrollToBottom(true), 100);
      } else {
        setMessages([]);
        setConversationId(null);
      }
    } catch {
      setMessages([]);
      setConversationId(null);
    } finally {
      setIsLoadingHistory(false);
    }
  }, []);

  // mode变化时加载对应历史
  useEffect(() => {
    setMessages([]);
    setConversationId(null);
    if (isOpen) {
      loadHistory(mode);
    }
  }, [mode, isOpen, loadHistory]);

  // 面板打开时加载历史
  useEffect(() => {
    if (isOpen && messages.length === 0) {
      loadHistory(mode);
    }
  }, [isOpen]);

  // 登录页不显示悬浮助手
  if (pathname === '/login') return null;

  return (
    <>
      {/* 悬浮按钮 */}
      <button
        onClick={() => setIsOpen(!isOpen)}
        className={`fixed bottom-6 right-6 z-[9999] w-14 h-14 rounded-full shadow-2xl flex items-center justify-center transition-all duration-300 group ${
          isOpen
            ? 'bg-slate-700 hover:bg-slate-600'
            : 'bg-gradient-to-r from-blue-600 to-cyan-500 hover:shadow-[0_0_30px_rgba(59,130,246,0.4)]'
        }`}
      >
        {isOpen ? (
          <X className="w-6 h-6 text-white" />
        ) : (
          <div className="relative">
            <Sparkles className="w-6 h-6 text-white" />
            <span className="absolute -top-1 -right-1 w-2.5 h-2.5 bg-green-400 rounded-full animate-pulse" />
          </div>
        )}
      </button>

      {/* 聊天面板 */}
      {isOpen && (
        <div className="fixed bottom-24 right-6 z-[9999] w-[420px] h-[600px] rounded-2xl overflow-hidden shadow-2xl border border-blue-500/20 flex flex-col"
          style={{ background: 'linear-gradient(180deg, #0f172a 0%, #1e293b 100%)' }}
        >
          {/* 头部 */}
          <div className="flex items-center justify-between px-4 py-3 border-b border-blue-500/20 bg-slate-900/80 backdrop-blur-xl">
            <div className="flex items-center gap-2">
              <span className="text-lg">{icon}</span>
              <div>
                <h3 className="text-sm font-semibold text-white">{label}</h3>
                <div className="flex items-center gap-1.5">
                  <span className="w-1.5 h-1.5 bg-green-400 rounded-full animate-pulse" />
                  <span className="text-[10px] text-slate-400">在线</span>
                </div>
              </div>
            </div>
            <div className="flex items-center gap-1">
              <span className="px-2 py-0.5 rounded-full text-[10px] font-medium bg-blue-500/20 text-blue-300 border border-blue-500/30">
                DeepSeek V4 Pro
              </span>
              <button
                onClick={() => { setMessages([]); }}
                className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-white/10 transition-all"
                title="新对话"
              >
                <MessageSquare className="w-3.5 h-3.5" />
              </button>
            </div>
          </div>

          {/* 消息区域 */}
          <div
            ref={chatContainerRef}
            onScroll={handleScroll}
            className="flex-1 overflow-y-auto p-4 space-y-3"
            style={{ scrollbarWidth: 'thin', scrollbarColor: '#334155 transparent' }}
          >
            {messages.length === 0 && (
              <div className="flex flex-col items-center justify-center h-full text-center">
                <div className="w-16 h-16 rounded-2xl bg-gradient-to-br from-blue-500/20 to-cyan-500/20 border border-blue-500/20 flex items-center justify-center mb-4">
                  <Bot className="w-8 h-8 text-blue-400" />
                </div>
                <h4 className="text-white font-medium mb-1">{label}</h4>
                <p className="text-xs text-slate-400 mb-4 px-4">
                  {mode === 'factory'
                    ? '我可以帮您查询供应链数据、计算成本、对比供应商报价'
                    : mode === 'marketing'
                    ? '我可以帮您分析市场趋势、生成营销方案、搜索行业资料'
                    : '我可以帮您管理知识库、识别图片、搜索设计资料'}
                </p>
                <div className="flex flex-wrap gap-2 justify-center">
                  {(mode === 'factory'
                    ? ['查询HT01-S报价详情', '对比涤纶DTY供应商', '计算产品成本']
                    : mode === 'marketing'
                    ? ['分析行业趋势', '生成营销方案', '竞品分析']
                    : ['整理知识库文档分类', '搜索设计趋势资料', '分析图片风格元素']
                  ).map((q, i) => (
                    <button
                      key={i}
                      onClick={() => { setInput(q); }}
                      className="px-3 py-1.5 rounded-lg text-xs bg-slate-800/80 text-slate-300 border border-slate-700/50 hover:bg-blue-500/20 hover:text-blue-300 hover:border-blue-500/30 transition-all"
                    >
                      {q}
                    </button>
                  ))}
                </div>
              </div>
            )}

            {messages.map((msg) => (
              <div key={msg.id} className={`group flex gap-2 ${msg.role === 'user' ? 'justify-end' : 'justify-start'}`}>
                {msg.role === 'assistant' && (
                  <div className="w-7 h-7 rounded-lg bg-gradient-to-br from-blue-500/30 to-cyan-500/30 border border-blue-500/20 flex items-center justify-center flex-shrink-0 mt-1">
                    <Bot className="w-4 h-4 text-blue-400" />
                  </div>
                )}
                <div className={`max-w-[85%] relative ${msg.role === 'user' ? 'order-1' : ''}`}>
                  {/* 思维链 */}
                  {msg.role === 'assistant' && msg.thinkingChain && msg.thinkingChain.length > 0 && (
                    <div className="mb-2">
                      <button
                        onClick={() => setThinkingExpanded(!thinkingExpanded)}
                        className="flex items-center gap-1.5 text-[10px] text-slate-500 hover:text-slate-400 transition-colors"
                      >
                        <Lightbulb className="w-3 h-3" />
                        <span>思考过程</span>
                        <ChevronDown className={`w-3 h-3 transition-transform ${thinkingExpanded ? 'rotate-180' : ''}`} />
                      </button>
                      {thinkingExpanded && (
                        <div className="mt-1 p-2.5 rounded-lg bg-slate-800/50 border border-slate-700/30 text-[11px] text-slate-400 space-y-1">
                          {msg.thinkingChain.map((t, i) => (
                            <p key={i} className="flex items-start gap-1.5">
                              <span className="text-blue-400/60 mt-0.5">›</span>
                              <span>{t}</span>
                            </p>
                          ))}
                          {msg.isStreaming && !msg.content && (
                            <p className="flex items-center gap-1.5 text-slate-500">
                              <Loader2 className="w-3 h-3 animate-spin" />
                              <span>正在思考...</span>
                            </p>
                          )}
                        </div>
                      )}
                    </div>
                  )}

                  {/* 搜索结果 */}
                  {msg.role === 'assistant' && msg.searchResults && msg.searchResults.length > 0 && (
                    <div className="mb-2">
                      <button
                        onClick={() => setSearchExpanded(!searchExpanded)}
                        className="flex items-center gap-1.5 text-[10px] text-slate-500 hover:text-slate-400 transition-colors"
                      >
                        <Globe className="w-3 h-3" />
                        <span>联网搜索 ({msg.searchResults.length}条)</span>
                        <ChevronDown className={`w-3 h-3 transition-transform ${searchExpanded ? 'rotate-180' : ''}`} />
                      </button>
                      {searchExpanded && (
                        <div className="mt-1 p-2.5 rounded-lg bg-sky-950/30 border border-sky-500/10 space-y-2">
                          {msg.searchResults.map((r, i) => (
                            <div key={i} className="text-[11px]">
                              <a href={r.url} target="_blank" rel="noopener" className="text-sky-400 hover:text-sky-300 font-medium">
                                {r.title}
                              </a>
                              <p className="text-slate-500 mt-0.5 line-clamp-2">{r.snippet}</p>
                            </div>
                          ))}
                        </div>
                      )}
                    </div>
                  )}

                  {/* 消息内容 */}
                  <div className={`rounded-2xl px-3.5 py-2.5 ${
                    msg.role === 'user'
                      ? 'bg-gradient-to-r from-blue-600 to-cyan-500 text-white'
                      : 'bg-slate-800/80 border border-slate-700/30 text-slate-200'
                  }`}>
                    {msg.role === 'assistant' ? (
                      <div className="text-[13px] leading-relaxed">
                        <MarkdownRenderer content={msg.content || (msg.isStreaming ? '' : '...')} darkMode />
                        {msg.isStreaming && msg.content && (
                          <span className="inline-block w-1.5 h-4 bg-blue-400 ml-0.5 animate-pulse rounded-sm" />
                        )}
                      </div>
                    ) : (
                      <p className="text-[13px] leading-relaxed whitespace-pre-wrap">{msg.content}</p>
                    )}
                  </div>

                  {/* 复制按钮 */}
                  {msg.role === 'assistant' && !msg.isStreaming && msg.content && (
                    <div className="absolute -top-1 -right-1 opacity-0 group-hover:opacity-100 transition-opacity">
                      <CopyButton text={msg.content} />
                    </div>
                  )}
                </div>

                {msg.role === 'user' && (
                  <div className="w-7 h-7 rounded-lg bg-slate-700 border border-slate-600 flex items-center justify-center flex-shrink-0 mt-1">
                    <User className="w-4 h-4 text-slate-300" />
                  </div>
                )}
              </div>
            ))}
            <div ref={messagesEndRef} />
          </div>

          {/* 输入区域 */}
          <div className="p-3 border-t border-blue-500/20 bg-slate-900/60 backdrop-blur-xl">
            <div className="flex items-end gap-2">
              <textarea
                ref={inputRef}
                value={input}
                onChange={(e) => setInput(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && !e.shiftKey) {
                    e.preventDefault();
                    handleSend();
                  }
                }}
                placeholder={`问${label}任何问题...`}
                rows={1}
                className="flex-1 resize-none rounded-xl bg-slate-800/80 border border-slate-700/50 px-3 py-2 text-sm text-white placeholder-slate-500 focus:outline-none focus:border-blue-500/50 focus:ring-1 focus:ring-blue-500/20 transition-all"
                style={{ maxHeight: '100px' }}
              />
              <button
                onClick={handleSend}
                disabled={isChatting || !input.trim()}
                className={`p-2.5 rounded-xl transition-all flex-shrink-0 ${
                  isChatting || !input.trim()
                    ? 'bg-slate-700 text-slate-500 cursor-not-allowed'
                    : 'bg-gradient-to-r from-blue-600 to-cyan-500 text-white hover:shadow-[0_0_15px_rgba(59,130,246,0.3)]'
                }`}
              >
                {isChatting ? <Loader2 className="w-4 h-4 animate-spin" /> : <Send className="w-4 h-4" />}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
