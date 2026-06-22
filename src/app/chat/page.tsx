'use client';

import { useState, useRef, useCallback, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import {
  MessageSquare, Send, Plus, Trash2, ArrowLeft,
  Bot, User, BookOpen, Brain, Loader2, Sparkles
} from 'lucide-react';
import MarkdownRenderer from '@/components/MarkdownRenderer';

// ===== 类型定义 =====
interface ChatImage {
  id: string;
  title: string;
  url: string;
  thumbnailUrl?: string;
  isMainImage?: boolean;
  fileType?: string;
  width?: number;
  height?: number;
  productId?: string;
  albumName?: string;
}

interface ChatMessage {
  role: 'user' | 'assistant' | 'system';
  content: string;
  sources?: Array<{
    source: 'memory' | 'knowledge';
    title?: string;
    domain?: string;
    content?: string;
    score: number;
  }>;
  images?: ChatImage[];
  isStreaming?: boolean;
}

interface ChatSession {
  id: string;
  title: string;
  lastMessage: string;
  timestamp: number;
}

// ===== 工具函数 =====
function getLoginSessionId(): string | null {
  if (typeof window === 'undefined') return null;
  try {
    return localStorage.getItem('session_id');
  } catch {
    return null;
  }
}

function formatTime(ts: number): string {
  try {
    const d = new Date(ts);
    const now = new Date();
    const diffMs = now.getTime() - d.getTime();
    if (diffMs < 60000) return '刚刚';
    if (diffMs < 3600000) return `${Math.floor(diffMs / 60000)}分钟前`;
    if (diffMs < 86400000) return `${Math.floor(diffMs / 3600000)}小时前`;
    return d.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
  } catch {
    return '';
  }
}

// 生成UUID（兼容不支持crypto.randomUUID的环境）
function generateId(): string {
  try {
    if (typeof crypto !== 'undefined' && crypto.randomUUID) {
      return crypto.randomUUID();
    }
  } catch { /* fallback */ }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = Math.random() * 16 | 0;
    const v = c === 'x' ? r : (r & 0x3 | 0x8);
    return v.toString(16);
  });
}

// ===== 主组件 =====
export default function ChatPage() {
  const router = useRouter();
  const [mounted, setMounted] = useState(false);
  const [authChecked, setAuthChecked] = useState(false);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState('');
  const [isChatting, setIsChatting] = useState(false);
  const [sessions, setSessions] = useState<ChatSession[]>([]);
  const [showSidebar, setShowSidebar] = useState(true);

  const messagesEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);

  // 客户端挂载标记 - 防止hydration不匹配
  useEffect(() => {
    setMounted(true);
  }, []);

  // 检查登录状态
  useEffect(() => {
    if (!mounted) return;

    const checkAuth = () => {
      try {
        const sid = localStorage.getItem('session_id');
        const expires = localStorage.getItem('session_expires');

        if (!sid) {
          window.location.href = '/login';
          return;
        }

        if (expires && Date.now() > parseInt(expires, 10)) {
          localStorage.removeItem('session_id');
          localStorage.removeItem('session_expires');
          localStorage.removeItem('portal_type');
          window.location.href = '/login';
          return;
        }

        setAuthChecked(true);
      } catch {
        // localStorage不可用时仍然允许访问
        setAuthChecked(true);
      }
    };

    checkAuth();
  }, [mounted]);

  // 加载对话历史（按userId+company绑定，不需要sessionId）
  useEffect(() => {
    if (!authChecked) return;
    const sid = getLoginSessionId();
    fetch('/api/chat/history', {
      credentials: 'include',
      headers: sid ? { 'X-Session-Id': sid } : {},
    })
      .then(res => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        return res.json();
      })
      .then(data => {
        if (data.success && data.history?.length > 0) {
          setMessages(data.history.map((m: { role: string; content: string }) => ({
            role: m.role as ChatMessage['role'],
            content: m.content,
          })));
        }
      })
      .catch(() => {});
  }, [authChecked]);

  // 自动滚动
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  // 发送消息
  const handleSend = useCallback(async () => {
    if (!input.trim() || isChatting) return;

    const userMsg: ChatMessage = { role: 'user', content: input.trim() };
    setMessages(prev => [...prev, userMsg]);
    setInput('');
    setIsChatting(true);

    const assistantMsg: ChatMessage = {
      role: 'assistant',
      content: '',
      isStreaming: true,
    };
    setMessages(prev => [...prev, assistantMsg]);

    try {
      const loginSid = getLoginSessionId();
      const params = new URLSearchParams({ message: input.trim() });
      const headers: Record<string, string> = { 'Accept': 'text/event-stream' };
      if (loginSid) headers['X-Session-Id'] = loginSid;

      const res = await fetch(`/api/chat/smart?${params}`, {
        credentials: 'include',
        headers,
      });

      if (!res.ok) {
        const err = await res.json().catch(() => ({ error: '请求失败' }));
        throw new Error(err.error || `HTTP ${res.status}`);
      }

      const reader = res.body?.getReader();
      if (!reader) throw new Error('SSE流为空');

      let sources: ChatMessage['sources'] = [];
      let images: ChatImage[] = [];
      const decoder = new TextDecoder();
      let sseBuffer = ''; // SSE 行缓冲区，防止跨 chunk 断行

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        sseBuffer += decoder.decode(value, { stream: true });
        // 按双换行分割SSE事件，保留不完整的末尾
        const parts = sseBuffer.split('\n');
        sseBuffer = parts.pop() || ''; // 最后一个元素可能不完整，留到下次

        for (const line of parts) {
          if (!line.startsWith('data:')) continue;
          const data = line.substring(5).trim();
          if (!data) continue;

          try {
            const event = JSON.parse(data);

            if (event.type === 'sources') {
              sources = event.sources || [];
              setMessages(prev => {
                const updated = [...prev];
                const last = updated[updated.length - 1];
                if (last?.isStreaming) {
                  updated[updated.length - 1] = { ...last, sources };
                }
                return updated;
              });
            } else if (event.type === 'images') {
              images = event.images || [];
              setMessages(prev => {
                const updated = [...prev];
                const last = updated[updated.length - 1];
                if (last?.isStreaming) {
                  updated[updated.length - 1] = { ...last, images };
                }
                return updated;
              });
            } else if (event.type === 'content') {
              setMessages(prev => {
                const updated = [...prev];
                const last = updated[updated.length - 1];
                if (last?.isStreaming) {
                  updated[updated.length - 1] = {
                    ...last,
                    content: last.content + event.content,
                  };
                }
                return updated;
              });
            } else if (event.type === 'done') {
              setMessages(prev => {
                const updated = [...prev];
                const last = updated[updated.length - 1];
                if (last) {
                  updated[updated.length - 1] = { ...last, isStreaming: false };
                }
                return updated;
              });
            } else if (event.type === 'error') {
              setMessages(prev => {
                const updated = [...prev];
                const last = updated[updated.length - 1];
                if (last?.isStreaming) {
                  updated[updated.length - 1] = {
                    ...last,
                    content: `错误: ${event.content}`,
                    isStreaming: false,
                  };
                }
                return updated;
              });
            }
          } catch { /* ignore parse errors */ }
        }
      }

      // 处理缓冲区中可能残留的最后一条数据
      if (sseBuffer.startsWith('data:')) {
        const data = sseBuffer.substring(5).trim();
        if (data) {
          try {
            const event = JSON.parse(data);
            if (event.type === 'done') {
              setMessages(prev => {
                const updated = [...prev];
                const last = updated[updated.length - 1];
                if (last) {
                  updated[updated.length - 1] = { ...last, isStreaming: false };
                }
                return updated;
              });
            } else if (event.type === 'content') {
              setMessages(prev => {
                const updated = [...prev];
                const last = updated[updated.length - 1];
                if (last?.isStreaming) {
                  updated[updated.length - 1] = {
                    ...last,
                    content: last.content + event.content,
                  };
                }
                return updated;
              });
            }
          } catch { /* ignore */ }
        }
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : '未知错误';
      setMessages(prev => {
        const updated = [...prev];
        const last = updated[updated.length - 1];
        if (last?.isStreaming) {
          updated[updated.length - 1] = {
            ...last,
            content: `请求失败: ${msg}`,
            isStreaming: false,
          };
        }
        return updated;
      });
    } finally {
      setIsChatting(false);
    }
  }, [input, isChatting]);

  // 新建对话
  const handleNewChat = () => {
    setMessages([]);
    setSessions(prev => [{
      id: generateId(),
      title: '新对话',
      lastMessage: '',
      timestamp: Date.now(),
    }, ...prev]);
  };

  // 清空对话
  const handleClearChat = async () => {
    const sid = getLoginSessionId();
    try {
      await fetch('/api/chat/history', {
        method: 'DELETE',
        credentials: 'include',
        headers: sid ? { 'X-Session-Id': sid } : {},
      });
    } catch { /* ignore */ }
    setMessages([]);
  };

  // 快捷问题
  const quickQuestions = [
    '帮我查询HT01-S产品的报价详情',
    '最近有哪些供应商的原料价格变动？',
    '对比一下涤纶DTY的供应商报价',
    '织造环节的成本占比是多少？',
  ];

  // 未挂载或未认证时显示加载状态（避免hydration问题）
  if (!mounted || !authChecked) {
    return (
      <div className="flex h-screen items-center justify-center bg-slate-50">
        <div className="flex flex-col items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-gradient-to-r from-violet-500 to-purple-600
            flex items-center justify-center shadow-md animate-pulse">
            <Sparkles className="w-5 h-5 text-white" />
          </div>
          <p className="text-xs text-slate-400">加载中...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="flex h-screen bg-slate-50">
      {/* 左侧边栏 */}
      {showSidebar && (
        <div className="w-64 border-r border-slate-200/50 bg-white flex flex-col">
          {/* 侧边栏头部 */}
          <div className="p-3 border-b border-slate-100">
            <button
              onClick={handleNewChat}
              className="w-full flex items-center justify-center gap-1.5 px-3 py-2 rounded-lg
                bg-gradient-to-r from-violet-500 to-purple-600 text-white text-sm font-medium
                hover:from-violet-600 hover:to-purple-700 transition-all shadow-sm"
            >
              <Plus className="w-3.5 h-3.5" />
              新建对话
            </button>
          </div>

          {/* 对话列表 */}
          <div className="flex-1 overflow-y-auto p-2 space-y-0.5">
            {sessions.length === 0 ? (
              <div className="text-center py-8 text-slate-300 text-xs">
                <MessageSquare className="w-6 h-6 mx-auto mb-2 opacity-40" />
                暂无对话记录
              </div>
            ) : (
              sessions.map(s => (
                <button
                  key={s.id}
                  className="w-full text-left px-3 py-2 rounded-lg transition-all bg-violet-50/80 text-violet-700 border border-violet-100/60"
                >
                  <div className="text-xs font-medium truncate">{s.title}</div>
                  <div className="text-[10px] text-slate-400 mt-0.5">{formatTime(s.timestamp)}</div>
                </button>
              ))
            )}
          </div>

          {/* 底部操作 */}
          <div className="p-2 border-t border-slate-100">
            <button
              onClick={() => router.push('/')}
              className="w-full flex items-center gap-2 px-3 py-1.5 rounded-lg text-xs text-slate-400
                hover:bg-slate-50 hover:text-slate-600 transition-colors"
            >
              <ArrowLeft className="w-3.5 h-3.5" />
              返回主页
            </button>
          </div>
        </div>
      )}

      {/* 主聊天区域 */}
      <div className="flex-1 flex flex-col min-w-0">
        {/* 顶部栏 */}
        <div className="h-12 border-b border-slate-200/50 bg-white
          flex items-center justify-between px-4 shrink-0">
          <div className="flex items-center gap-2.5">
            <button
              onClick={() => setShowSidebar(!showSidebar)}
              className="p-1.5 rounded-lg hover:bg-slate-100 transition-colors lg:hidden"
            >
              <MessageSquare className="w-4 h-4 text-slate-400" />
            </button>
            <div className="flex items-center gap-2">
              <div className="w-7 h-7 rounded-lg bg-gradient-to-r from-violet-500 to-purple-600
                flex items-center justify-center shadow-sm">
                <Sparkles className="w-3.5 h-3.5 text-white" />
              </div>
              <div>
                <h1 className="text-xs font-semibold text-slate-700">AI 智能对话</h1>
                <p className="text-[10px] text-slate-400">记忆库 + 知识库 双库检索</p>
              </div>
            </div>
          </div>
          <div className="flex items-center gap-0.5">
            <button
              onClick={handleClearChat}
              className="p-1.5 rounded-lg text-slate-300 hover:text-red-500 hover:bg-red-50 transition-colors"
              title="清空对话"
            >
              <Trash2 className="w-3.5 h-3.5" />
            </button>
            <button
              onClick={() => setShowSidebar(!showSidebar)}
              className="p-1.5 rounded-lg text-slate-300 hover:bg-slate-100 transition-colors hidden lg:block"
              title="切换侧边栏"
            >
              <MessageSquare className="w-3.5 h-3.5" />
            </button>
          </div>
        </div>

        {/* 消息列表 */}
        <div className="flex-1 overflow-y-auto">
          <div className="max-w-3xl mx-auto px-4 py-6 space-y-6">
            {messages.length === 0 ? (
              /* 空状态 */
              <div className="flex flex-col items-center justify-center py-20">
                <div className="w-12 h-12 rounded-xl bg-gradient-to-r from-violet-500 to-purple-600
                  flex items-center justify-center mb-4 shadow-md">
                  <Bot className="w-6 h-6 text-white" />
                </div>
                <h2 className="text-base font-semibold text-slate-700 mb-1">AI 智能对话</h2>
                <p className="text-xs text-slate-400 mb-6 text-center max-w-sm">
                  基于记忆库知识卡片和知识库文档的智能检索，为您提供精准的专业问答
                </p>

                {/* 双库标识 */}
                <div className="flex items-center gap-3 mb-6">
                  <div className="flex items-center gap-1.5 px-2.5 py-1 rounded-md bg-blue-50 text-blue-600 text-[10px] font-medium">
                    <Brain className="w-3 h-3" />
                    记忆库检索
                  </div>
                  <span className="text-slate-200 text-xs">+</span>
                  <div className="flex items-center gap-1.5 px-2.5 py-1 rounded-md bg-emerald-50 text-emerald-600 text-[10px] font-medium">
                    <BookOpen className="w-3 h-3" />
                    知识库检索
                  </div>
                </div>

                {/* 快捷问题 */}
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 w-full max-w-md">
                  {quickQuestions.map((q, i) => (
                    <button
                      key={i}
                      onClick={() => setInput(q)}
                      className="text-left px-3 py-2 rounded-lg border border-slate-200/60
                        text-xs text-slate-500 hover:border-violet-200 hover:bg-violet-50/30
                        hover:text-violet-600 transition-all"
                    >
                      {q}
                    </button>
                  ))}
                </div>
              </div>
            ) : (
              /* 消息列表 */
              messages.map((msg, i) => (
                <div key={i} className={`flex gap-2.5 ${msg.role === 'user' ? 'justify-end' : 'justify-start'}`}>
                  {msg.role === 'assistant' && (
                    <div className="w-7 h-7 rounded-lg bg-gradient-to-r from-violet-500 to-purple-600
                      flex items-center justify-center shrink-0 shadow-sm mt-0.5">
                      <Bot className="w-3.5 h-3.5 text-white" />
                    </div>
                  )}
                  <div className={`max-w-[80%] ${msg.role === 'user' ? 'order-first' : ''}`}>
                    {/* 来源标签 */}
                    {msg.sources && msg.sources.length > 0 && (
                      <div className="flex flex-wrap gap-1 mb-1.5">
                        {msg.sources.map((s, j) => (
                          <span
                            key={j}
                            className={`inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-[10px] font-medium
                              ${s.source === 'memory'
                                ? 'bg-blue-50 text-blue-500'
                                : 'bg-emerald-50 text-emerald-500'}`}
                          >
                            {s.source === 'memory' ? <Brain className="w-2.5 h-2.5" /> : <BookOpen className="w-2.5 h-2.5" />}
                            {s.source === 'memory' ? s.title || s.domain || '记忆库' : '知识库'}
                            <span className="opacity-60">{(s.score * 100).toFixed(0)}%</span>
                          </span>
                        ))}
                      </div>
                    )}
                    {/* 消息内容 */}
                    <div className={`px-3.5 py-2.5 rounded-xl text-[13px] leading-relaxed
                      ${msg.role === 'user'
                        ? 'bg-gradient-to-r from-violet-500 to-purple-600 text-white rounded-tr-sm'
                        : 'bg-white border border-slate-200/60 text-slate-600 shadow-sm rounded-tl-sm'}`}
                    >
                      {msg.role === 'user' ? (
                        <div className="whitespace-pre-wrap">{msg.content}</div>
                      ) : (
                        <MarkdownRenderer content={msg.content || ''} />
                      )}
                      {msg.isStreaming && (
                        <span className="inline-block w-1.5 h-3.5 bg-violet-500 animate-pulse ml-0.5 align-middle" />
                      )}
                    </div>

                    {/* 图片结果 - 按产品分组展示(主图+详情图) */}
                    {msg.images && msg.images.length > 0 && (
                      <div className="mt-3 space-y-3">
                        <div className="text-xs text-slate-400 mb-2 flex items-center gap-1">
                          <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                              d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
                          </svg>
                          为您找到 {msg.images.length} 个相关产品
                        </div>
                        {msg.images.map((product: ChatImage & { mainImage?: ChatImage; detailImages?: ChatImage[]; productName?: string; albumName?: string }, pIdx: number) => (
                          <div key={pIdx} className="rounded-xl border border-slate-200/80 bg-white shadow-sm overflow-hidden">
                            {/* 产品标题 */}
                            {(product.productName || product.albumName) && (
                              <div className="px-3 py-2 border-b border-slate-100 bg-slate-50/50">
                                <p className="text-xs font-medium text-slate-700 truncate">
                                  {product.productName || ''}
                                </p>
                                {product.albumName && (
                                  <p className="text-[10px] text-slate-400 truncate">{product.albumName}</p>
                                )}
                              </div>
                            )}
                            {/* 主图 */}
                            {product.mainImage && (
                              <div className="p-2">
                                <a
                                  href={product.mainImage.url}
                                  target="_blank"
                                  rel="noopener noreferrer"
                                  className="group block rounded-lg overflow-hidden border border-violet-200/60 bg-violet-50/30 relative"
                                >
                                  <div className="aspect-[4/3] relative">
                                    {/* eslint-disable-next-line @next/next/no-img-element */}
                                    <img
                                      src={product.mainImage.thumbnailUrl || product.mainImage.url}
                                      alt={product.mainImage.title || '主图'}
                                      className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
                                      loading="lazy"
                                      onError={(e) => {
                                        (e.target as HTMLImageElement).src = '/file.svg';
                                      }}
                                    />
                                    <span className="absolute top-2 left-2 px-2 py-0.5 rounded-md bg-violet-500 text-white text-[10px] font-medium">
                                      主图
                                    </span>
                                  </div>
                                  {product.mainImage.title && (
                                    <div className="px-2 py-1.5">
                                      <p className="text-xs text-slate-600 truncate">{product.mainImage.title}</p>
                                    </div>
                                  )}
                                </a>
                              </div>
                            )}
                            {/* 详情图 */}
                            {product.detailImages && product.detailImages.length > 0 && (
                              <div className="px-2 pb-2">
                                <p className="text-[10px] text-slate-400 mb-1.5">详情图 ({product.detailImages.length}张)</p>
                                <div className="grid grid-cols-4 sm:grid-cols-5 gap-1.5">
                                  {product.detailImages.map((img: ChatImage, dIdx: number) => (
                                    <a
                                      key={dIdx}
                                      href={img.url}
                                      target="_blank"
                                      rel="noopener noreferrer"
                                      className="group block rounded-lg overflow-hidden border border-slate-200/60 bg-white"
                                      title={img.title || '详情图'}
                                    >
                                      <div className="aspect-square relative">
                                        {/* eslint-disable-next-line @next/next/no-img-element */}
                                        <img
                                          src={img.thumbnailUrl || img.url}
                                          alt={img.title || '详情图'}
                                          className="w-full h-full object-cover group-hover:scale-110 transition-transform duration-300"
                                          loading="lazy"
                                          onError={(e) => {
                                            (e.target as HTMLImageElement).src = '/file.svg';
                                          }}
                                        />
                                      </div>
                                    </a>
                                  ))}
                                </div>
                              </div>
                            )}
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                  {msg.role === 'user' && (
                    <div className="w-8 h-8 rounded-xl bg-slate-100
                      flex items-center justify-center shrink-0 mt-1">
                      <User className="w-4 h-4 text-slate-500" />
                    </div>
                  )}
                </div>
              ))
            )}
            <div ref={messagesEndRef} />
          </div>
        </div>

        {/* 输入框 */}
        <div className="border-t border-slate-200/50 bg-white p-3 shrink-0">
          <div className="max-w-3xl mx-auto">
            <div className="flex items-end gap-2">
              <div className="flex-1 relative">
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
                  placeholder="输入您的问题，AI将基于记忆库和知识库检索回答..."
                  rows={1}
                  className="w-full px-3 py-2.5 rounded-lg border border-slate-200/60
                    bg-slate-50/50 text-slate-700 text-[13px] resize-none
                    focus:outline-none focus:ring-2 focus:ring-violet-500/15 focus:border-violet-300 focus:bg-white
                    placeholder:text-slate-400 transition-all"
                  style={{ maxHeight: '120px', minHeight: '38px', color: '#334155' }}
                  disabled={isChatting}
                />
              </div>
              <button
                onClick={handleSend}
                disabled={isChatting || !input.trim()}
                className={`shrink-0 w-9 h-9 rounded-lg flex items-center justify-center transition-all
                  ${isChatting || !input.trim()
                    ? 'bg-slate-100 text-slate-300 cursor-not-allowed'
                    : 'bg-gradient-to-r from-violet-500 to-purple-600 text-white shadow-sm hover:shadow-md'}`}
              >
                {isChatting ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Send className="w-3.5 h-3.5" />}
              </button>
            </div>
            <p className="text-[10px] text-slate-300 mt-1.5 text-center">
              记忆库 + 知识库 双库检索 · MiniMax AI 驱动
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
