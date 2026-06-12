'use client';

import { useState, useRef, useEffect, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import {
  MessageSquare,
  Upload,
  Search,
  BookOpen,
  Send,
  Plus,
  Link,
  FileText,
  Trash2,
  Bot,
  User,
  Loader2,
  ChevronDown,
  ChevronUp,
  Sparkles,
  LogOut,
} from 'lucide-react';
import Sidebar from '@/components/Sidebar';
import { CurrentUser } from '@/components/Header';
import { cn } from '@/lib/utils';

interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  sources?: Array<{ content: string; score: number; docId?: string }>;
  timestamp: Date;
}

interface DocEntry {
  id: string;
  title: string;
  type: 'text' | 'url';
  content?: string;
  createdAt: Date;
}

export default function KnowledgePage() {
  const router = useRouter();
  const [currentUser, setCurrentUser] = useState<CurrentUser | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const chatEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);

  // 知识库管理
  const [documents, setDocuments] = useState<DocEntry[]>([]);
  const [showAddPanel, setShowAddPanel] = useState(false);
  const [addType, setAddType] = useState<'text' | 'url'>('text');
  const [addContent, setAddContent] = useState('');
  const [addTitle, setAddTitle] = useState('');
  const [addUrl, setAddUrl] = useState('');
  const [isAdding, setIsAdding] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState<Array<{ content: string; score: number; docId?: string }>>([]);
  const [isSearching, setIsSearching] = useState(false);
  const [showSearch, setShowSearch] = useState(false);
  const [showDocs, setShowDocs] = useState(true);

  // 登录检查
  useEffect(() => {
    const localSessionId = localStorage.getItem('session_id');
    if (!localSessionId) {
      router.push('/login');
      return;
    }
    fetch('/api/auth/login').then(res => {
      if (res.ok) return res.json();
      // 后端不可用时走降级模式
      return null;
    }).then(data => {
      if (data === null) {
        const username = localStorage.getItem('username') || '用户';
        setCurrentUser({ id: 'local', username, role: 'admin', displayName: username });
        return;
      }
      if (data.success && data.data?.user) {
        setCurrentUser(data.data.user);
      } else if (data.loggedIn) {
        setCurrentUser(data.user);
      } else {
        localStorage.removeItem('session_id');
        localStorage.removeItem('session_expires');
        router.push('/login');
      }
    }).catch(() => {
      const username = localStorage.getItem('username') || '用户';
      setCurrentUser({ id: 'local', username, role: 'admin', displayName: username });
    });
  }, [router]);

  // 自动滚动
  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  // 发送消息（RAG对话）
  const handleSend = useCallback(async () => {
    if (!input.trim() || isLoading) return;

    const userMessage: ChatMessage = {
      id: Date.now().toString(),
      role: 'user',
      content: input.trim(),
      timestamp: new Date(),
    };

    setMessages((prev) => [...prev, userMessage]);
    setInput('');
    setIsLoading(true);

    const assistantId = (Date.now() + 1).toString();
    setMessages((prev) => [
      ...prev,
      { id: assistantId, role: 'assistant', content: '', timestamp: new Date() },
    ]);

    try {
      const history = messages.slice(-10).map((m) => ({
        role: m.role,
        content: m.content,
      }));

      const sid = typeof window !== 'undefined' ? localStorage.getItem('session_id') : null;
      const chatHeaders: Record<string, string> = { 'Content-Type': 'application/json' };
      if (sid) chatHeaders['X-Session-Id'] = sid;
      const response = await fetch('/api/knowledge/chat', {
        method: 'POST',
        headers: chatHeaders,
        body: JSON.stringify({ message: userMessage.content, history }),
      });

      if (!response.ok) {
        throw new Error('请求失败');
      }

      const reader = response.body?.getReader();
      if (!reader) throw new Error('无法读取响应流');

      const decoder = new TextDecoder();
      let accumulated = '';

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        const text = decoder.decode(value, { stream: true });
        const lines = text.split('\n');

        for (const line of lines) {
          if (line.startsWith('data: ')) {
            const data = line.slice(6);
            if (data === '[DONE]') break;
            try {
              const parsed = JSON.parse(data);
              if (parsed.content) {
                accumulated += parsed.content;
                setMessages((prev) =>
                  prev.map((m) => (m.id === assistantId ? { ...m, content: accumulated } : m)),
                );
              }
            } catch {
              // skip
            }
          }
        }
      }
    } catch (error) {
      console.error('Chat error:', error);
      setMessages((prev) =>
        prev.map((m) =>
          m.id === assistantId ? { ...m, content: '抱歉，请求出错，请稍后重试。' } : m,
        ),
      );
    } finally {
      setIsLoading(false);
    }
  }, [input, isLoading, messages]);

  // 导入文档
  const handleAddDoc = async () => {
    if (addType === 'text' && (!addTitle.trim() || !addContent.trim())) return;
    if (addType === 'url' && !addUrl.trim()) return;

    setIsAdding(true);
    try {
      const sid = typeof window !== 'undefined' ? localStorage.getItem('session_id') : null;
      const addHeaders: Record<string, string> = { 'Content-Type': 'application/json' };
      if (sid) addHeaders['X-Session-Id'] = sid;
      const res = await fetch('/api/knowledge/add', {
        method: 'POST',
        headers: addHeaders,
        body: JSON.stringify(
          addType === 'text'
            ? { content: `标题: ${addTitle}\n\n${addContent}`, datasetName: 'coze_doc_knowledge' }
            : { url: addUrl, datasetName: 'coze_doc_knowledge' },
        ),
      });

      const data = await res.json();
      if (data.success) {
        const newDoc: DocEntry = {
          id: data.doc_ids?.[0] || Date.now().toString(),
          title: addType === 'text' ? addTitle : addUrl,
          type: addType,
          content: addType === 'text' ? addContent : undefined,
          createdAt: new Date(),
        };
        setDocuments((prev) => [newDoc, ...prev]);
        setAddTitle('');
        setAddContent('');
        setAddUrl('');
        setShowAddPanel(false);
      } else {
        alert(data.error || '导入失败');
      }
    } catch (error) {
      console.error('Add doc error:', error);
      alert('导入失败');
    } finally {
      setIsAdding(false);
    }
  };

  // 搜索知识库
  const handleSearch = async () => {
    if (!searchQuery.trim()) return;
    setIsSearching(true);
    try {
      const sid = typeof window !== 'undefined' ? localStorage.getItem('session_id') : null;
      const searchHeaders: Record<string, string> = {};
      if (sid) searchHeaders['X-Session-Id'] = sid;
      const res = await fetch(`/api/knowledge/search?query=${encodeURIComponent(searchQuery)}&topK=5&minScore=0.3`, {
        headers: searchHeaders,
      });
      const data = await res.json();
      if (data.success) {
        setSearchResults(data.chunks || []);
      }
    } catch (error) {
      console.error('Search error:', error);
    } finally {
      setIsSearching(false);
    }
  };

  // 删除文档
  const handleDeleteDoc = (id: string) => {
    setDocuments((prev) => prev.filter((d) => d.id !== id));
  };

  // 键盘快捷键
  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  // 侧边栏导航
  const handleMenuItemClick = (item: string) => {
    if (item === 'knowledge') return; // 当前页面
    if (item === 'memory') {
      router.push('/memory');
      return;
    }
    router.push('/');
  };

  const handleLogout = async () => {
    await fetch('/api/auth/login', { method: 'DELETE' });
    localStorage.removeItem('session_id');
    router.push('/login');
  };

  return (
    <div className="flex h-screen bg-slate-50/50 overflow-hidden">
      {/* 共享侧边栏 */}
      <Sidebar
        activeItem="knowledge"
        onItemClick={handleMenuItemClick}
        collapsed={false}
        albums={[]}
        smartAlbums={[]}
        allImagesCount={0}
        myImagesCount={0}
        favoritesCount={0}
        recentCount={0}
        trashCount={0}
        isAdmin={currentUser?.role === 'admin'}
        documentStats={{ all: 0, pdf: 0, word: 0, excel: 0, ppt: 0, zip: 0, other: 0 }}
      />

      {/* 主内容区 */}
      <div className="flex-1 flex flex-col overflow-hidden">
        {/* 顶部栏 */}
        <header className="h-14 bg-white/80 backdrop-blur-sm border-b border-slate-200/60 flex items-center justify-between px-6">
          <div className="flex items-center gap-3">
            <div className="flex items-center gap-2">
              <Sparkles className="w-5 h-5 text-violet-600" />
              <h1 className="text-lg font-semibold text-slate-800">RAG 知识库</h1>
            </div>
            <span className="text-xs px-2 py-0.5 bg-violet-50 text-violet-600 rounded-lg font-medium">AI增强</span>
          </div>
          <div className="flex items-center gap-3">
            {currentUser && (
              <div className="flex items-center gap-2 pl-3 border-l border-slate-200">
                <div className="w-8 h-8 rounded-full bg-gradient-to-br from-violet-500 to-purple-600 flex items-center justify-center text-white text-sm font-medium">
                  {currentUser.username?.charAt(0).toUpperCase()}
                </div>
                <span className="text-sm text-slate-600">{currentUser.username}</span>
                <button onClick={handleLogout} className="p-1 hover:bg-slate-100 rounded-lg" title="退出登录">
                  <LogOut className="w-4 h-4 text-slate-400" />
                </button>
              </div>
            )}
          </div>
        </header>

        {/* 主内容 */}
        <div className="flex-1 flex min-h-0">
          {/* 左侧面板 - 知识管理 */}
          <div className="w-72 border-r border-slate-200/60 bg-white/50 flex flex-col shrink-0">
            {/* 知识库操作 */}
            <div className="p-4 border-b border-slate-200/40">
              <div className="flex items-center justify-between mb-3">
                <h2 className="text-sm font-semibold text-slate-700 flex items-center gap-1.5">
                  <Sparkles className="w-4 h-4 text-violet-500" />
                  知识库管理
                </h2>
                <button
                  onClick={() => setShowAddPanel(!showAddPanel)}
                  className="p-1.5 hover:bg-violet-100 rounded-lg transition-colors"
                >
                  {showAddPanel ? <ChevronUp className="w-4 h-4 text-slate-500" /> : <Plus className="w-4 h-4 text-violet-500" />}
                </button>
              </div>

              {/* 添加文档面板 */}
              {showAddPanel && (
                <div className="space-y-3 p-3 bg-violet-50/50 rounded-xl border border-violet-100">
                  <div className="flex gap-2">
                    <button
                      onClick={() => setAddType('text')}
                      className={cn(
                        "flex-1 flex items-center justify-center gap-1.5 py-2 rounded-lg text-xs font-medium transition-colors",
                        addType === 'text' ? 'bg-violet-500 text-white' : 'bg-white text-slate-600 hover:bg-slate-100'
                      )}
                    >
                      <FileText className="w-3.5 h-3.5" />
                      文本
                    </button>
                    <button
                      onClick={() => setAddType('url')}
                      className={cn(
                        "flex-1 flex items-center justify-center gap-1.5 py-2 rounded-lg text-xs font-medium transition-colors",
                        addType === 'url' ? 'bg-violet-500 text-white' : 'bg-white text-slate-600 hover:bg-slate-100'
                      )}
                    >
                      <Link className="w-3.5 h-3.5" />
                      网址
                    </button>
                  </div>

                  {addType === 'text' ? (
                    <>
                      <input
                        value={addTitle}
                        onChange={(e) => setAddTitle(e.target.value)}
                        placeholder="文档标题"
                        className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-violet-300 bg-white"
                      />
                      <textarea
                        value={addContent}
                        onChange={(e) => setAddContent(e.target.value)}
                        placeholder="输入知识内容..."
                        rows={5}
                        className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-violet-300 bg-white resize-none"
                      />
                    </>
                  ) : (
                    <input
                      value={addUrl}
                      onChange={(e) => setAddUrl(e.target.value)}
                      placeholder="输入网址 https://..."
                      className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-violet-300 bg-white"
                    />
                  )}

                  <button
                    onClick={handleAddDoc}
                    disabled={isAdding}
                    className="w-full py-2 bg-gradient-to-r from-violet-500 to-purple-600 text-white rounded-lg text-sm font-medium hover:from-violet-600 hover:to-purple-700 transition-all disabled:opacity-50 flex items-center justify-center gap-1.5"
                  >
                    {isAdding ? <Loader2 className="w-4 h-4 animate-spin" /> : <Upload className="w-4 h-4" />}
                    {isAdding ? '导入中...' : '导入知识库'}
                  </button>
                </div>
              )}
            </div>

            {/* 搜索 */}
            <div className="p-4 border-b border-slate-200/40">
              <button
                onClick={() => setShowSearch(!showSearch)}
                className="flex items-center justify-between w-full mb-2"
              >
                <h3 className="text-sm font-semibold text-slate-700 flex items-center gap-1.5">
                  <Search className="w-4 h-4 text-violet-500" />
                  知识检索
                </h3>
                {showSearch ? <ChevronUp className="w-4 h-4 text-slate-400" /> : <ChevronDown className="w-4 h-4 text-slate-400" />}
              </button>

              {showSearch && (
                <div className="space-y-2">
                  <div className="flex gap-2">
                    <input
                      value={searchQuery}
                      onChange={(e) => setSearchQuery(e.target.value)}
                      onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
                      placeholder="搜索知识..."
                      className="flex-1 px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-violet-300 bg-white"
                    />
                    <button
                      onClick={handleSearch}
                      disabled={isSearching}
                      className="px-3 py-2 bg-violet-500 text-white rounded-lg hover:bg-violet-600 transition-colors disabled:opacity-50"
                    >
                      {isSearching ? <Loader2 className="w-4 h-4 animate-spin" /> : <Search className="w-4 h-4" />}
                    </button>
                  </div>

                  {searchResults.length > 0 && (
                    <div className="space-y-2 max-h-48 overflow-y-auto">
                      {searchResults.map((result, i) => (
                        <div key={i} className="p-2 bg-violet-50/50 rounded-lg border border-violet-100">
                          <div className="flex items-center gap-1 mb-1">
                            <span className="text-[10px] px-1.5 py-0.5 bg-violet-200/60 text-violet-700 rounded">
                              相关度 {(result.score * 100).toFixed(0)}%
                            </span>
                          </div>
                          <p className="text-xs text-slate-600 line-clamp-3">{result.content}</p>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )}
            </div>

            {/* 文档列表 */}
            <div className="flex-1 min-h-0 overflow-y-auto p-4">
              <button
                onClick={() => setShowDocs(!showDocs)}
                className="flex items-center justify-between w-full mb-2"
              >
                <h3 className="text-sm font-semibold text-slate-700 flex items-center gap-1.5">
                  <FileText className="w-4 h-4 text-violet-500" />
                  已导入文档 ({documents.length})
                </h3>
                {showDocs ? <ChevronUp className="w-4 h-4 text-slate-400" /> : <ChevronDown className="w-4 h-4 text-slate-400" />}
              </button>

              {showDocs && (
                <div className="space-y-2">
                  {documents.length === 0 ? (
                    <div className="text-center py-8">
                      <BookOpen className="w-10 h-10 text-slate-300 mx-auto mb-2" />
                      <p className="text-xs text-slate-400">暂无文档，点击上方 + 导入</p>
                    </div>
                  ) : (
                    documents.map((doc) => (
                      <div
                        key={doc.id}
                        className="group flex items-start gap-2 p-2.5 bg-white rounded-lg border border-slate-100 hover:border-violet-200 transition-colors"
                      >
                        <div className="w-7 h-7 bg-violet-100 rounded-lg flex items-center justify-center shrink-0 mt-0.5">
                          {doc.type === 'url' ? (
                            <Link className="w-3.5 h-3.5 text-violet-600" />
                          ) : (
                            <FileText className="w-3.5 h-3.5 text-violet-600" />
                          )}
                        </div>
                        <div className="flex-1 min-w-0">
                          <p className="text-xs font-medium text-slate-700 truncate">{doc.title}</p>
                          <p className="text-[10px] text-slate-400 mt-0.5">
                            {doc.createdAt.toLocaleDateString()} {doc.type === 'url' ? 'URL' : '文本'}
                          </p>
                        </div>
                        <button
                          onClick={() => handleDeleteDoc(doc.id)}
                          className="opacity-0 group-hover:opacity-100 p-1 hover:bg-red-50 rounded transition-all"
                        >
                          <Trash2 className="w-3 h-3 text-red-400" />
                        </button>
                      </div>
                    ))
                  )}
                </div>
              )}
            </div>
          </div>

          {/* 右侧 - 对话区 */}
          <div className="flex-1 flex flex-col min-w-0 bg-gradient-to-br from-slate-50/30 to-white">
            {/* 对话消息 */}
            <div className="flex-1 min-h-0 overflow-y-auto p-6 space-y-4">
              {messages.length === 0 && (
                <div className="flex flex-col items-center justify-center h-full text-center">
                  <div className="w-16 h-16 bg-gradient-to-br from-violet-500 to-purple-600 rounded-2xl flex items-center justify-center mb-4 shadow-lg shadow-violet-200">
                    <MessageSquare className="w-8 h-8 text-white" />
                  </div>
                  <h2 className="text-xl font-bold text-slate-800 mb-2">RAG 知识库助手</h2>
                  <p className="text-sm text-slate-500 max-w-md">
                    基于知识库的智能问答，先导入知识文档，AI 回答时会优先参考知识库中的内容
                  </p>
                  <div className="mt-6 flex flex-wrap gap-2 justify-center">
                    {['查询产品报价', '原料采购价格', '供应商信息', '生产计划安排'].map((q) => (
                      <button
                        key={q}
                        onClick={() => setInput(q)}
                        className="px-3 py-1.5 text-xs bg-violet-50 text-violet-600 rounded-full hover:bg-violet-100 transition-colors border border-violet-100"
                      >
                        {q}
                      </button>
                    ))}
                  </div>
                </div>
              )}

              {messages.map((msg) => (
                <div
                  key={msg.id}
                  className={cn("flex gap-3", msg.role === 'user' ? 'justify-end' : 'justify-start')}
                >
                  {msg.role === 'assistant' && (
                    <div className="w-8 h-8 bg-gradient-to-br from-violet-500 to-purple-600 rounded-xl flex items-center justify-center shrink-0 shadow-sm">
                      <Bot className="w-4 h-4 text-white" />
                    </div>
                  )}
                  <div
                    className={cn(
                      "max-w-[70%] rounded-2xl px-4 py-3 text-sm leading-relaxed",
                      msg.role === 'user'
                        ? 'bg-gradient-to-r from-violet-500 to-purple-600 text-white shadow-md'
                        : 'bg-white border border-slate-200 text-slate-700 shadow-sm'
                    )}
                  >
                    {msg.content ? (
                      <div className="whitespace-pre-wrap">{msg.content}</div>
                    ) : (
                      <div className="flex items-center gap-2 text-slate-400">
                        <Loader2 className="w-4 h-4 animate-spin" />
                        <span>正在思考...</span>
                      </div>
                    )}
                  </div>
                  {msg.role === 'user' && (
                    <div className="w-8 h-8 bg-slate-200 rounded-xl flex items-center justify-center shrink-0">
                      <User className="w-4 h-4 text-slate-600" />
                    </div>
                  )}
                </div>
              ))}
              <div ref={chatEndRef} />
            </div>

            {/* 输入区 */}
            <div className="border-t border-slate-200/60 bg-white/80 backdrop-blur-md p-4">
              <div className="max-w-4xl mx-auto flex gap-3 items-end">
                <div className="flex-1 relative">
                  <textarea
                    ref={inputRef}
                    value={input}
                    onChange={(e) => setInput(e.target.value)}
                    onKeyDown={handleKeyDown}
                    placeholder="输入问题，AI 将基于知识库回答..."
                    rows={1}
                    className="w-full px-4 py-3 pr-12 text-sm border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-violet-300 bg-white resize-none max-h-32"
                    style={{ minHeight: '44px' }}
                  />
                </div>
                <button
                  onClick={handleSend}
                  disabled={!input.trim() || isLoading}
                  className="p-3 bg-gradient-to-r from-violet-500 to-purple-600 text-white rounded-xl hover:from-violet-600 hover:to-purple-700 transition-all disabled:opacity-50 disabled:cursor-not-allowed shadow-md shadow-violet-200 shrink-0"
                >
                  {isLoading ? <Loader2 className="w-5 h-5 animate-spin" /> : <Send className="w-5 h-5" />}
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
