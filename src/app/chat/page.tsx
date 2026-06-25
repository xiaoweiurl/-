'use client';

import { useState, useRef, useCallback, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { backendFetch } from '@/lib/backend-proxy';
import {
  MessageSquare, Send, Plus, Trash2, ArrowLeft,
  Bot, User, BookOpen, Brain, Loader2, Sparkles,
  Globe, ChevronRight, Lightbulb, Copy, Check, Zap
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
  reasoning?: string;
  searchResults?: string;
  sources?: Array<{
    source: 'memory' | 'knowledge';
    title?: string;
    domain?: string;
    content?: string;
    score: number;
  }>;
  images?: ChatImage[];
  isStreaming?: boolean;
  isThinking?: boolean;
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

// ===== 复制按钮组件 =====
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
      className="p-1 rounded-md text-slate-300 hover:text-slate-500 hover:bg-slate-100 transition-all"
      title="复制内容"
    >
      {copied ? <Check className="w-3.5 h-3.5 text-green-500" /> : <Copy className="w-3.5 h-3.5" />}
    </button>
  );
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
  const [activeSessionId, setActiveSessionId] = useState<string | null>(null);
  const [showSidebar, setShowSidebar] = useState(true);

  const messagesEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const scrollContainerRef = useRef<HTMLDivElement>(null);
  const isUserScrollingRef = useRef(false);

  // 客户端挂载标记
  useEffect(() => {
    setMounted(true);
  }, []);

  // 监听用户滚动行为：判断用户是否主动上滑
  useEffect(() => {
    const container = scrollContainerRef.current;
    if (!container) return;

    const handleScroll = () => {
      const { scrollTop, scrollHeight, clientHeight } = container;
      // 距离底部不超过120px时认为用户在底部，可以自动滚动
      const nearBottom = scrollHeight - scrollTop - clientHeight < 120;
      isUserScrollingRef.current = !nearBottom;
    };

    container.addEventListener('scroll', handleScroll, { passive: true });
    return () => container.removeEventListener('scroll', handleScroll);
  }, [mounted]);

  // 检查登录状态
  useEffect(() => {
    if (!mounted) return;

    const checkAuth = async () => {
      try {
        const sid = localStorage.getItem('session_id');
        const expires = localStorage.getItem('session_expires');

        if (!sid) {
          try {
            const probeRes = await backendFetch('/albums?pageSize=1');
            if (probeRes.status === 502) {
              console.log('[Chat] 后端不可用，进入降级模式');
              setAuthChecked(true);
              return;
            }
          } catch {
            console.log('[Chat] 后端不可用，进入降级模式');
            setAuthChecked(true);
            return;
          }
          window.location.href = '/login';
          return;
        }

        if (expires && Date.now() > parseInt(expires, 10)) {
          try {
            const probeRes = await backendFetch('/albums?pageSize=1');
            if (probeRes.status === 502) {
              console.log('[Chat] 后端不可用，过期session忽略');
              setAuthChecked(true);
              return;
            }
          } catch {
            setAuthChecked(true);
            return;
          }
          localStorage.removeItem('session_id');
          localStorage.removeItem('session_expires');
          localStorage.removeItem('portal_type');
          window.location.href = '/login';
          return;
        }

        setAuthChecked(true);
      } catch {
        setAuthChecked(true);
      }
    };

    checkAuth();
  }, [mounted]);

  // 加载对话列表
  const loadConversations = useCallback(async () => {
    const sid = getLoginSessionId();
    try {
      const res = await fetch('/api/chat/conversations?mode=designer', {
        credentials: 'include',
        headers: sid ? { 'X-Session-Id': sid } : {},
      });
      const data = await res.json();
      if (data.success && data.conversations) {
        const convs: ChatSession[] = data.conversations.map((c: { id: string; title: string; updatedAt?: string; createdAt?: string }) => ({
          id: c.id,
          title: c.title || '新对话',
          lastMessage: '',
          timestamp: new Date(c.updatedAt || c.createdAt || Date.now()).getTime(),
        }));
        setSessions(convs);
        return convs;
      }
    } catch { /* ignore */ }
    return [];
  }, []);

  // 加载指定对话的消息历史
  const loadChatHistory = useCallback(async (conversationId: string) => {
    const sid = getLoginSessionId();
    try {
      const params = new URLSearchParams();
      params.set('mode', 'designer');
      if (conversationId) params.set('conversationId', conversationId);
      const res = await fetch(`/api/chat/history?${params.toString()}`, {
        credentials: 'include',
        headers: sid ? { 'X-Session-Id': sid } : {},
      });
      const data = await res.json();
      if (data.success && data.history?.length > 0) {
        return data.history.map((m: { role: string; content: string; reasoning?: string }) => {
          const msg: ChatMessage = {
            role: m.role as ChatMessage['role'],
            content: m.content,
          };
          if (m.reasoning) {
            msg.reasoning = m.reasoning;
          }
          return msg;
        });
      }
    } catch { /* ignore */ }
    return [];
  }, []);

  // 初始化：加载对话列表
  useEffect(() => {
    if (!authChecked) return;
    (async () => {
      const convs = await loadConversations();
      if (convs.length > 0) {
        const latestConv = convs[0];
        setActiveSessionId(latestConv.id);
        const history = await loadChatHistory(latestConv.id);
        setMessages(history);
      }
    })();
  }, [authChecked, loadConversations, loadChatHistory]);

  // 智能自动滚动：仅在用户未主动上滑时自动滚到底部
  const scrollToBottom = useCallback((behavior: ScrollBehavior = 'smooth') => {
    if (!isUserScrollingRef.current) {
      messagesEndRef.current?.scrollIntoView({ behavior });
    }
  }, []);

  useEffect(() => {
    scrollToBottom(messages.length > 0 && messages[messages.length - 1]?.isStreaming ? 'auto' : 'smooth');
  }, [messages, scrollToBottom]);

  // 发送消息
  const handleSend = useCallback(async () => {
    if (!input.trim() || isChatting) return;

    // 用户发送新消息时，重置滚动状态，确保自动滚到底部
    isUserScrollingRef.current = false;

    const userMsg: ChatMessage = { role: 'user', content: input.trim() };
    setMessages(prev => [...prev, userMsg]);
    setInput('');
    setIsChatting(true);

    const assistantMsg: ChatMessage = {
      role: 'assistant',
      content: '',
      reasoning: '',
      isStreaming: true,
      isThinking: false,
    };
    setMessages(prev => [...prev, assistantMsg]);

    try {
      const loginSid = getLoginSessionId();
      const params = new URLSearchParams({ message: input.trim(), mode: 'designer' });
      if (activeSessionId) params.set('conversationId', activeSessionId);
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
      let sseBuffer = '';

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        sseBuffer += decoder.decode(value, { stream: true });
        const parts = sseBuffer.split('\n');
        sseBuffer = parts.pop() || '';

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
            } else if (event.type === 'reasoning_delta') {
              setMessages(prev => {
                const updated = [...prev];
                const last = updated[updated.length - 1];
                if (last?.isStreaming) {
                  updated[updated.length - 1] = {
                    ...last,
                    reasoning: (last.reasoning || '') + event.content,
                    isThinking: true,
                  };
                }
                return updated;
              });
            } else if (event.type === 'reasoning') {
              setMessages(prev => {
                const updated = [...prev];
                const last = updated[updated.length - 1];
                if (last?.isStreaming) {
                  updated[updated.length - 1] = {
                    ...last,
                    reasoning: event.content || last.reasoning,
                    isThinking: false,
                  };
                }
                return updated;
              });
            } else if (event.type === 'web_search_result') {
              setMessages(prev => {
                const updated = [...prev];
                const last = updated[updated.length - 1];
                if (last?.isStreaming) {
                  updated[updated.length - 1] = {
                    ...last,
                    searchResults: event.content || '',
                  };
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
                  updated[updated.length - 1] = { ...last, isStreaming: false, isThinking: false };
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

      // 处理缓冲区
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
  }, [input, isChatting, activeSessionId]);

  // 新建对话
  const handleNewChat = async () => {
    const sid = getLoginSessionId();
    try {
      const res = await fetch('/api/chat/conversations', {
        method: 'POST',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/json',
          ...(sid ? { 'X-Session-Id': sid } : {}),
        },
        body: JSON.stringify({ title: '新对话', mode: 'designer' }),
      });
      const data = await res.json();
      if (data.success && data.conversation) {
        const conv = data.conversation;
        const newSession: ChatSession = {
          id: conv.id,
          title: conv.title || '新对话',
          lastMessage: '',
          timestamp: Date.now(),
        };
        setActiveSessionId(conv.id);
        setMessages([]);
        setSessions(prev => [newSession, ...prev]);
      }
    } catch { /* ignore */ }
  };

  // 切换对话
  const handleSwitchChat = async (sessionId: string) => {
    setActiveSessionId(sessionId);
    const history = await loadChatHistory(sessionId);
    setMessages(history);
  };

  // 删除对话
  const handleDeleteChat = async (sessionId: string, e: React.MouseEvent) => {
    e.stopPropagation();
    const sid = getLoginSessionId();
    try {
      await fetch(`/api/chat/conversations/${sessionId}`, {
        method: 'DELETE',
        credentials: 'include',
        headers: sid ? { 'X-Session-Id': sid } : {},
      });
    } catch { /* ignore */ }

    setSessions(prev => prev.filter(s => s.id !== sessionId));
    if (activeSessionId === sessionId) {
      const remaining = sessions.filter(s => s.id !== sessionId);
      if (remaining.length > 0) {
        setActiveSessionId(remaining[0].id);
        const history = await loadChatHistory(remaining[0].id);
        setMessages(history);
      } else {
        setActiveSessionId(null);
        setMessages([]);
      }
    }
  };

  // 清空当前对话
  const handleClearChat = async () => {
    if (!activeSessionId) return;
    const sid = getLoginSessionId();
    try {
      const params = new URLSearchParams();
      params.set('mode', 'designer');
      params.set('conversationId', activeSessionId);
      await fetch(`/api/chat/history?${params.toString()}`, {
        method: 'DELETE',
        credentials: 'include',
        headers: sid ? { 'X-Session-Id': sid } : {},
      });
    } catch { /* ignore */ }
    setMessages([]);
  };

  // 快捷问题
  const quickQuestions = [
    '帮我整理一下知识库中的文档分类',
    '分析一下这张图片的风格和元素',
    '如何高效管理大规模图片资源？',
    '帮我搜索关于设计趋势的资料',
  ];

  if (!mounted || !authChecked) {
    return (
      <div className="flex h-screen items-center justify-center bg-slate-50">
        <div className="flex flex-col items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-slate-700
            flex items-center justify-center shadow-md animate-pulse">
            <Sparkles className="w-5 h-5 text-white" />
          </div>
          <p className="text-xs text-slate-400">加载中...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="flex h-screen bg-gradient-to-br from-slate-50 via-white to-slate-50/50">
      {/* 左侧边栏 */}
      {showSidebar && (
        <div className="w-72 border-r border-slate-200/50 bg-white/80 backdrop-blur-sm flex flex-col shadow-sm">
          {/* 侧边栏头部 */}
          <div className="p-4 border-b border-slate-100/80">
            <button
              onClick={handleNewChat}
              className="w-full flex items-center justify-center gap-2 px-4 py-2.5 rounded-xl
                bg-slate-700 text-white text-sm font-medium
                hover:bg-slate-800 transition-all shadow-sm hover:shadow-md
                active:scale-[0.98]"
            >
              <Plus className="w-4 h-4" />
              新建对话
            </button>
          </div>

          {/* 对话列表 */}
          <div className="flex-1 overflow-y-auto p-3 space-y-1">
            {sessions.length === 0 ? (
              <div className="text-center py-12 text-slate-300 text-xs">
                <MessageSquare className="w-8 h-8 mx-auto mb-3 opacity-30" />
                <p>暂无对话记录</p>
                <p className="text-[10px] mt-1 text-slate-300/60">点击上方按钮开始新对话</p>
              </div>
            ) : (
              sessions.map(s => (
                <div key={s.id} className="group relative">
                  <button
                    onClick={() => handleSwitchChat(s.id)}
                    className={`w-full text-left px-3 py-2.5 rounded-xl transition-all border
                      ${s.id === activeSessionId
                        ? 'bg-slate-100/80 text-slate-700 border-slate-200/80 shadow-sm'
                        : 'text-slate-500 border-transparent hover:bg-slate-50/80 hover:text-slate-700'}`}
                  >
                    <div className="flex items-center gap-2">
                      <div className={`w-2 h-2 rounded-full shrink-0 ${s.id === activeSessionId ? 'bg-slate-600' : 'bg-slate-300'}`} />
                      <div className="text-xs font-medium truncate pr-6 flex-1">{s.title}</div>
                    </div>
                    <div className="text-[10px] text-slate-400 mt-1 ml-4">{formatTime(s.timestamp)}</div>
                  </button>
                  <button
                    onClick={(e) => handleDeleteChat(s.id, e)}
                    className="absolute right-2 top-1/2 -translate-y-1/2 p-1.5 rounded-lg
                      text-slate-300 hover:text-red-500 hover:bg-red-50
                      opacity-0 group-hover:opacity-100 transition-all"
                    title="删除对话"
                  >
                    <Trash2 className="w-3 h-3" />
                  </button>
                </div>
              ))
            )}
          </div>

          {/* 底部操作 */}
          <div className="p-3 border-t border-slate-100/80">
            <button
              onClick={() => router.push('/')}
              className="w-full flex items-center gap-2.5 px-3 py-2 rounded-xl text-xs text-slate-400
                hover:bg-slate-50 hover:text-slate-600 transition-all"
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
        <div className="h-14 border-b border-slate-200/50 bg-white/80 backdrop-blur-sm
          flex items-center justify-between px-5 shrink-0">
          <div className="flex items-center gap-3">
            <button
              onClick={() => setShowSidebar(!showSidebar)}
              className="p-1.5 rounded-lg hover:bg-slate-100 transition-colors lg:hidden"
            >
              <MessageSquare className="w-4 h-4 text-slate-400" />
            </button>
            <div className="flex items-center gap-2.5">
              <div className="w-8 h-8 rounded-xl bg-slate-700
                flex items-center justify-center shadow-sm">
                <Sparkles className="w-4 h-4 text-white" />
              </div>
              <div>
                <h1 className="text-sm font-semibold text-slate-700">AI 智能对话</h1>
                <div className="flex items-center gap-1.5">
                  <span className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded-full bg-blue-500/10 text-blue-600 text-[10px] font-medium border border-blue-200/40">
                    <Brain className="w-2.5 h-2.5" />
                    记忆库
                  </span>
                  <span className="text-slate-200 text-[10px]">+</span>
                  <span className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded-full bg-emerald-500/10 text-emerald-600 text-[10px] font-medium border border-emerald-200/40">
                    <BookOpen className="w-2.5 h-2.5" />
                    知识库
                  </span>
                  <span className="text-slate-200 text-[10px]">+</span>
                  <span className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded-full bg-sky-500/10 text-sky-600 text-[10px] font-medium border border-sky-200/40">
                    <Globe className="w-2.5 h-2.5" />
                    联网搜索
                  </span>
                </div>
              </div>
            </div>
          </div>
          <div className="flex items-center gap-1">
            <button
              onClick={handleClearChat}
              className="p-2 rounded-lg text-slate-300 hover:text-red-500 hover:bg-red-50 transition-colors"
              title="清空对话"
            >
              <Trash2 className="w-4 h-4" />
            </button>
            <button
              onClick={() => setShowSidebar(!showSidebar)}
              className="p-2 rounded-lg text-slate-300 hover:bg-slate-100 transition-colors hidden lg:block"
              title="切换侧边栏"
            >
              <MessageSquare className="w-4 h-4" />
            </button>
          </div>
        </div>

        {/* 消息列表 */}
        <div ref={scrollContainerRef} className="flex-1 overflow-y-auto scroll-smooth">
          <div className="max-w-3xl mx-auto px-4 py-6 space-y-5">
            {messages.length === 0 ? (
              /* 空状态 - 精美引导页 */
              <div className="flex flex-col items-center justify-center py-16">
                {/* Logo */}
                <div className="relative mb-6">
                  <div className="w-16 h-16 rounded-2xl bg-slate-800
                    flex items-center justify-center shadow-lg shadow-slate-200/50">
                    <Bot className="w-8 h-8 text-white" />
                  </div>
                  <div className="absolute -top-1 -right-1 w-5 h-5 rounded-full bg-gradient-to-br from-slate-500 to-slate-600
                    flex items-center justify-center shadow-sm">
                    <Sparkles className="w-2.5 h-2.5 text-white" />
                  </div>
                </div>

                <h2 className="text-lg font-bold text-slate-800 mb-1.5">AI 智能对话</h2>
                <p className="text-sm text-slate-400 mb-8 text-center max-w-sm leading-relaxed">
                  融合记忆库、知识库与全网搜索，为您提供精准、专业的智能问答
                </p>

                {/* 三大能力标签 */}
                <div className="flex items-center gap-3 mb-8">
                  <div className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-blue-50 text-blue-600 text-xs font-medium border border-blue-100/60">
                    <Brain className="w-3.5 h-3.5" />
                    记忆库检索
                  </div>
                  <div className="w-px h-4 bg-slate-200" />
                  <div className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-emerald-50 text-emerald-600 text-xs font-medium border border-emerald-100/60">
                    <BookOpen className="w-3.5 h-3.5" />
                    知识库检索
                  </div>
                  <div className="w-px h-4 bg-slate-200" />
                  <div className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-sky-50 text-sky-600 text-xs font-medium border border-sky-100/60">
                    <Globe className="w-3.5 h-3.5" />
                    联网搜索
                  </div>
                </div>

                {/* 快捷问题 */}
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5 w-full max-w-lg">
                  {quickQuestions.map((q, i) => (
                    <button
                      key={i}
                      onClick={() => setInput(q)}
                      className="group text-left px-4 py-3 rounded-xl border border-slate-200/60 bg-white
                        text-sm text-slate-500 hover:border-slate-300 hover:bg-slate-50/80
                        hover:text-slate-700 hover:shadow-sm transition-all active:scale-[0.98]"
                    >
                      <div className="flex items-center gap-2.5">
                        <ChevronRight className="w-3.5 h-3.5 text-slate-300 group-hover:text-slate-500 shrink-0 transition-colors" />
                        <span className="text-[13px] leading-snug">{q}</span>
                      </div>
                    </button>
                  ))}
                </div>
              </div>
            ) : (
              /* 消息列表 */
              messages.map((msg, i) => (
                <div
                  key={i}
                  className={`flex gap-3 ${msg.role === 'user' ? 'justify-end' : 'justify-start'} chat-msg-enter`}
                >
                  {msg.role === 'assistant' && (
                    <div className="w-8 h-8 rounded-xl bg-slate-700
                      flex items-center justify-center shrink-0 shadow-sm mt-0.5">
                      <Bot className="w-4 h-4 text-white" />
                    </div>
                  )}
                  <div className={`max-w-[80%] min-w-0 ${msg.role === 'user' ? '' : ''}`}>
                    {/* 来源标签 */}
                    {msg.sources && msg.sources.length > 0 && (
                      <div className="flex flex-wrap gap-1.5 mb-2">
                        {msg.sources.map((s, j) => (
                          <span
                            key={j}
                            className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-md text-[10px] font-medium border
                              ${s.source === 'memory'
                                ? 'bg-blue-50/80 text-blue-500 border-blue-100/50'
                                : 'bg-emerald-50/80 text-emerald-500 border-emerald-100/50'}`}
                          >
                            {s.source === 'memory' ? <Brain className="w-2.5 h-2.5" /> : <BookOpen className="w-2.5 h-2.5" />}
                            {s.source === 'memory' ? s.title || s.domain || '记忆库' : '知识库'}
                            <span className="opacity-50 ml-0.5">{(s.score * 100).toFixed(0)}%</span>
                          </span>
                        ))}
                      </div>
                    )}

                    {/* 思维链（DeepSeek思考模式） */}
                    {msg.role === 'assistant' && msg.reasoning && (
                      <details className="mb-2.5 group">
                        <summary className="flex items-center gap-2 text-[11px] text-slate-500 cursor-pointer hover:text-slate-700 transition-colors select-none py-1">
                          <svg className="w-3 h-3 transition-transform group-open:rotate-90 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                          </svg>
                          <Lightbulb className="w-3 h-3 shrink-0" />
                          {msg.isThinking ? (
                            <span className="flex items-center gap-1.5">
                              <span className="inline-block w-1.5 h-1.5 rounded-full bg-slate-400 animate-pulse" />
                              正在深度思考...
                            </span>
                          ) : (
                            <span>思考过程</span>
                          )}
                        </summary>
                        <div className="mt-1.5 p-3 bg-gradient-to-br from-slate-50 to-slate-100/60 border border-slate-200/60 rounded-xl text-[11.5px] text-slate-500 leading-relaxed max-h-52 overflow-y-auto whitespace-pre-wrap shadow-sm">
                          {msg.reasoning}
                        </div>
                      </details>
                    )}

                    {/* 联网搜索结果 */}
                    {msg.role === 'assistant' && msg.searchResults && (
                      <details className="mb-2.5 group" open>
                        <summary className="flex items-center gap-2 text-[11px] text-sky-500 cursor-pointer hover:text-sky-600 transition-colors select-none py-1">
                          <svg className="w-3 h-3 transition-transform group-open:rotate-90 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                          </svg>
                          <Globe className="w-3 h-3 shrink-0" />
                          <span>联网搜索结果</span>
                        </summary>
                        <div className="mt-1.5 text-[11.5px] text-sky-600/70 bg-gradient-to-br from-sky-50/50 to-blue-50/30 rounded-xl p-3 whitespace-pre-wrap border border-sky-100/50 shadow-sm leading-relaxed">
                          {msg.searchResults}
                        </div>
                      </details>
                    )}

                    {/* 消息内容 */}
                    <div className={`relative group/msg
                      ${msg.role === 'user'
                        ? 'px-4 py-3 rounded-2xl rounded-tr-md bg-slate-700 text-white shadow-md shadow-slate-200/30'
                        : 'px-4 py-3 rounded-2xl rounded-tl-md bg-white border border-slate-200/60 text-slate-600 shadow-sm'}`}
                    >
                      {msg.role === 'user' ? (
                        <div className="whitespace-pre-wrap text-[13px] leading-relaxed">{msg.content}</div>
                      ) : (
                        <MarkdownRenderer content={msg.content || ''} />
                      )}
                      {msg.isStreaming && (
                        <span className={`inline-block w-1.5 h-4 ml-0.5 align-middle animate-pulse rounded-full
                          ${msg.isThinking ? 'bg-slate-400' : 'bg-slate-500'}`} />
                      )}

                      {/* 复制按钮 - 仅assistant消息完成时显示 */}
                      {msg.role === 'assistant' && !msg.isStreaming && msg.content && (
                        <div className="absolute -right-1 -top-1 opacity-0 group-hover/msg:opacity-100 transition-opacity">
                          <CopyButton text={msg.content} />
                        </div>
                      )}
                    </div>

                    {/* 图片结果 */}
                    {msg.images && msg.images.length > 0 && (
                      <div className="mt-3 space-y-3">
                        <div className="text-xs text-slate-400 mb-2 flex items-center gap-1.5">
                          <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                              d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
                          </svg>
                          为您找到 {msg.images.length} 个相关产品
                        </div>
                        {msg.images.map((product: ChatImage & { mainImage?: ChatImage; detailImages?: ChatImage[]; productName?: string; albumName?: string }, pIdx: number) => (
                          <div key={pIdx} className="rounded-xl border border-slate-200/80 bg-white shadow-sm overflow-hidden">
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
                            {product.mainImage && (
                              <div className="p-2">
                                <a
                                  href={product.mainImage.url}
                                  target="_blank"
                                  rel="noopener noreferrer"
                                  className="group block rounded-lg overflow-hidden border border-slate-200/60 bg-slate-50/30 relative"
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
                                    <span className="absolute top-2 left-2 px-2 py-0.5 rounded-md bg-slate-700 text-white text-[10px] font-medium">
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
                      flex items-center justify-center shrink-0 mt-0.5">
                      <User className="w-4 h-4 text-slate-500" />
                    </div>
                  )}
                </div>
              ))
            )}
            <div ref={messagesEndRef} />
          </div>
        </div>

        {/* 输入框区域 */}
        <div className="border-t border-slate-200/50 bg-white/80 backdrop-blur-sm p-4 shrink-0">
          <div className="max-w-3xl mx-auto">
            <div className="flex items-end gap-3">
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
                  placeholder="输入您的问题，AI 将智能检索并回答..."
                  rows={1}
                  className="w-full px-4 py-3 rounded-xl border border-slate-200/60
                    bg-slate-50/50 text-slate-700 text-[13px] resize-none
                    focus:outline-none focus:ring-2 focus:ring-slate-500/15 focus:border-slate-300 focus:bg-white
                    placeholder:text-slate-400 transition-all shadow-sm"
                  style={{ maxHeight: '120px', minHeight: '44px', color: '#334155' }}
                  disabled={isChatting}
                />
              </div>
              <button
                onClick={handleSend}
                disabled={isChatting || !input.trim()}
                className={`shrink-0 w-11 h-11 rounded-xl flex items-center justify-center transition-all
                  ${isChatting || !input.trim()
                    ? 'bg-slate-100 text-slate-300 cursor-not-allowed'
                    : 'bg-gradient-to-r from-slate-700 to-slate-900 text-white shadow-md shadow-slate-200/40 hover:shadow-lg hover:shadow-slate-300/40 active:scale-95'}`}
              >
                {isChatting ? <Loader2 className="w-4 h-4 animate-spin" /> : <Send className="w-4 h-4" />}
              </button>
            </div>
            <div className="mt-2 flex items-center justify-center gap-2">
              <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-blue-500/10 text-blue-600 text-[10px] font-medium border border-blue-200/40">
                <Brain className="w-2.5 h-2.5" />
                记忆库
              </span>
              <span className="text-slate-300 text-[10px]">+</span>
              <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-emerald-500/10 text-emerald-600 text-[10px] font-medium border border-emerald-200/40">
                <BookOpen className="w-2.5 h-2.5" />
                知识库
              </span>
              <span className="text-slate-300 text-[10px]">+</span>
              <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-sky-500/10 text-sky-600 text-[10px] font-medium border border-sky-200/40">
                <Globe className="w-2.5 h-2.5" />
                联网搜索
              </span>
              <span className="text-slate-300 text-[10px]">|</span>
              <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-slate-700/60 text-white text-[10px] font-medium tracking-wide">
                <Zap className="w-2.5 h-2.5 text-yellow-400" />
                DeepSeek V4 Pro
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
