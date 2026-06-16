'use client';

import { useState, useRef, useEffect, useCallback } from 'react';
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
  X,
} from 'lucide-react';

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
  type: 'text' | 'url' | 'file';
  content?: string;
  status?: string;
  embeddingStatus?: string;
  chunkCount?: number;
  createdAt: Date;
}

// 统一处理 API 响应，401 时自动跳转登录页
async function handleApiResponse(res: Response): Promise<unknown> {
  const data = await res.json();
  if (res.status === 401 || (data && typeof data === 'object' && 'code' in data && data.code === 401)) {
    localStorage.removeItem('session_id');
    document.cookie = 'session_id=; Path=/; Expires=Thu, 01 Jan 1970 00:00:00 GMT';
    window.location.href = '/login';
    throw new Error('会话已过期，请重新登录');
  }
  return data;
}

export default function KnowledgePage() {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const chatEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);

  // 知识库管理
  const [documents, setDocuments] = useState<DocEntry[]>([]);
  const [showAddPanel, setShowAddPanel] = useState(false);
  const [addType, setAddType] = useState<'text' | 'url' | 'file'>('text');
  const [uploadFile, setUploadFile] = useState<File | null>(null);
  const [isUploading, setIsUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [addContent, setAddContent] = useState('');
  const [addTitle, setAddTitle] = useState('');
  const [addUrl, setAddUrl] = useState('');
  const [addTags, setAddTags] = useState<string[]>([]);
  const [isAdding, setIsAdding] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState<Array<{ content: string; score: number; docId?: string }>>([]);
  const [isSearching, setIsSearching] = useState(false);
  const [showSearch, setShowSearch] = useState(false);
  const [showDocs, setShowDocs] = useState(true);

  // 加载文档列表
  const loadDocuments = useCallback(async () => {
    try {
      const sid = typeof window !== 'undefined' ? localStorage.getItem('session_id') : null;
      const headers: Record<string, string> = {};
      if (sid) headers['X-Session-Id'] = sid;
      const res = await fetch('/api/knowledge/docs', {
        credentials: 'include',
        headers,
      });
      const data = await handleApiResponse(res) as { success: boolean; documents?: Record<string, unknown>[] };
      if (data.success && data.documents) {
        setDocuments(
          data.documents.map((doc: Record<string, unknown>) => ({
            id: String(doc.id),
            title: String(doc.title || doc.fileName || '未命名'),
            type: 'file' as const,
            status: String(doc.status || 'active'),
            embeddingStatus: String(doc.embeddingStatus || 'PENDING'),
            chunkCount: Number(doc.chunkCount || 0),
            createdAt: new Date(String(doc.createdAt || Date.now())),
          }))
        );
      }
    } catch (err) {
      console.error('加载文档失败:', err);
    }
  }, []);

  useEffect(() => {
    loadDocuments();
  }, [loadDocuments]);

  // 登录检查
  useEffect(() => {
    const sessionId = localStorage.getItem('session_id');
    if (!sessionId) {
      window.location.href = '/login';
    }
  }, []);

  // 浏览器后退防护
  useEffect(() => {
    const handlePageShow = (e: PageTransitionEvent) => {
      if (e.persisted) {
        const sessionId = localStorage.getItem('session_id');
        if (!sessionId) {
          window.location.href = '/login';
        }
      }
    };
    window.addEventListener('pageshow', handlePageShow);
    return () => window.removeEventListener('pageshow', handlePageShow);
  }, []);

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
      const response = await fetch('/api/memory/chat', {
        method: 'POST',
        credentials: 'include',
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

  // 文件上传
  const handleFileUpload = async () => {
    if (!uploadFile) return;
    setIsUploading(true);
    setUploadProgress(0);
    try {
      const sid = typeof window !== 'undefined' ? localStorage.getItem('session_id') : null;
      const formData = new FormData();
      formData.append('file', uploadFile);
      formData.append('domain', 'default');
      if (sid) formData.append('sessionId', sid);

      const res = await fetch('/api/knowledge/upload', {
        method: 'POST',
        credentials: 'include',
        headers: sid ? { 'X-Session-Id': sid } : undefined,
        body: formData,
      });
      const data = await handleApiResponse(res) as { success: boolean; doc?: Record<string, unknown>; document?: Record<string, unknown>; error?: string };
      if (data.success) {
        setUploadFile(null);
        setShowAddPanel(false);
        await loadDocuments();
      } else {
        alert(data.error || '上传失败');
      }
    } catch (error) {
      console.error('Upload error:', error);
      alert('上传失败');
    } finally {
      setIsUploading(false);
      setUploadProgress(0);
    }
  };

  // 导入文档
  const handleAddDoc = async () => {
    if (addType === 'text' && (!addTitle.trim() || !addContent.trim())) return;
    if (addType === 'url' && !addUrl.trim()) return;
    if (addType === 'file') {
      await handleFileUpload();
      return;
    }

    setIsAdding(true);
    try {
      const sid = typeof window !== 'undefined' ? localStorage.getItem('session_id') : null;
      const addHeaders: Record<string, string> = { 'Content-Type': 'application/json' };
      if (sid) addHeaders['X-Session-Id'] = sid;
      const res = await fetch('/api/knowledge/docs', {
        method: 'POST',
        credentials: 'include',
        headers: addHeaders,
        body: JSON.stringify(
          addType === 'text'
            ? { title: addTitle, content: addContent, tags: addTags }
            : { title: addUrl, content: addUrl, tags: ['url', '导入'] },
        ),
      });

      const data = await handleApiResponse(res) as { success: boolean; doc_ids?: string[]; error?: string };
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
      const res = await fetch(`/api/knowledge/docs/search?q=${encodeURIComponent(searchQuery)}`, {
        credentials: 'include',
        headers: searchHeaders,
      });
      const data = await handleApiResponse(res) as { success: boolean; chunks?: Array<{ content: string; score: number; docId?: string }> };
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
  const handleDeleteDoc = async (id: string) => {
    if (!confirm('确定删除此文档？')) return;
    try {
      const sid = typeof window !== 'undefined' ? localStorage.getItem('session_id') : null;
      const headers: Record<string, string> = {};
      if (sid) headers['X-Session-Id'] = sid;
      const res = await fetch(`/api/knowledge/docs/${id}`, {
        method: 'DELETE',
        credentials: 'include',
        headers,
      });
      await handleApiResponse(res);
      await loadDocuments();
    } catch (err) {
      console.error('删除文档失败:', err);
    }
  };

  // 键盘快捷键
  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <div className="h-screen flex flex-col bg-gradient-to-br from-slate-50 via-violet-50/30 to-purple-50/20">
      {/* Header */}
      <header className="h-14 flex items-center justify-between px-6 bg-white/80 backdrop-blur-md border-b border-slate-200/60 shrink-0">
        <div className="flex items-center gap-2">
          <div className="w-8 h-8 bg-gradient-to-br from-violet-500 to-purple-600 rounded-lg flex items-center justify-center">
            <BookOpen className="w-4 h-4 text-white" />
          </div>
          <h1 className="text-lg font-bold text-slate-800">RAG 知识库</h1>
          <span className="text-xs px-2 py-0.5 bg-violet-100 text-violet-700 rounded-full">AI增强</span>
        </div>
        <button
          onClick={() => {
            localStorage.removeItem('session_id');
            localStorage.removeItem('portal_type');
            window.location.href = '/login';
          }}
          className="text-sm text-slate-500 hover:text-slate-700 transition-colors"
        >
          退出登录
        </button>
      </header>

      {/* Main Content */}
      <div className="flex flex-1 min-h-0">
        {/* Left Panel - Knowledge Management */}
        <div className="w-80 border-r border-slate-200/60 bg-white/50 flex flex-col shrink-0">
          {/* Knowledge Actions */}
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

            {/* Add Document Panel */}
            {showAddPanel && (
              <div className="space-y-3 p-3 bg-violet-50/50 rounded-xl border border-violet-100">
                <div className="flex gap-2">
                  <button
                    onClick={() => setAddType('text')}
                    className={`flex-1 flex items-center justify-center gap-1.5 py-2 rounded-lg text-xs font-medium transition-colors ${
                      addType === 'text'
                        ? 'bg-violet-500 text-white'
                        : 'bg-white text-slate-600 hover:bg-slate-100'
                    }`}
                  >
                    <FileText className="w-3.5 h-3.5" />
                    文本
                  </button>
                  <button
                    onClick={() => setAddType('file')}
                    className={`flex-1 flex items-center justify-center gap-1.5 py-2 rounded-lg text-xs font-medium transition-colors ${
                      addType === 'file'
                        ? 'bg-violet-500 text-white'
                        : 'bg-white text-slate-600 hover:bg-slate-100'
                    }`}
                  >
                    <Upload className="w-3.5 h-3.5" />
                    文件
                  </button>
                  <button
                    onClick={() => setAddType('url')}
                    className={`flex-1 flex items-center justify-center gap-1.5 py-2 rounded-lg text-xs font-medium transition-colors ${
                      addType === 'url'
                        ? 'bg-violet-500 text-white'
                        : 'bg-white text-slate-600 hover:bg-slate-100'
                    }`}
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
                ) : addType === 'file' ? (
                  <div className="space-y-2">
                    <label className="flex flex-col items-center justify-center w-full h-24 border-2 border-dashed border-slate-300 rounded-lg cursor-pointer hover:border-violet-400 hover:bg-violet-50/30 transition-colors">
                      <Upload className="w-6 h-6 text-slate-400 mb-1" />
                      <span className="text-xs text-slate-500">
                        {uploadFile ? uploadFile.name : '点击选择文件'}
                      </span>
                      <span className="text-[10px] text-slate-400 mt-0.5">支持 PDF、Word、Excel、TXT</span>
                      <input
                        type="file"
                        accept=".pdf,.doc,.docx,.xls,.xlsx,.txt"
                        className="hidden"
                        onChange={(e) => setUploadFile(e.target.files?.[0] || null)}
                      />
                    </label>
                    {uploadFile && (
                      <div className="flex items-center justify-between px-2 py-1.5 bg-slate-50 rounded-lg">
                        <span className="text-xs text-slate-600 truncate max-w-[180px]">{uploadFile.name}</span>
                        <button onClick={() => setUploadFile(null)} className="p-0.5 hover:bg-slate-200 rounded">
                          <X className="w-3 h-3 text-slate-400" />
                        </button>
                      </div>
                    )}
                    {isUploading && (
                      <div className="space-y-1">
                        <div className="h-1.5 bg-slate-100 rounded-full overflow-hidden">
                          <div
                            className="h-full bg-gradient-to-r from-violet-500 to-purple-600 rounded-full transition-all duration-300"
                            style={{ width: `${uploadProgress}%` }}
                          />
                        </div>
                        <p className="text-[10px] text-slate-400 text-center">上传并处理中...</p>
                      </div>
                    )}
                  </div>
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
                  disabled={isAdding || isUploading || (addType === 'file' && !uploadFile)}
                  className="w-full py-2 bg-gradient-to-r from-violet-500 to-purple-600 text-white rounded-lg text-sm font-medium hover:from-violet-600 hover:to-purple-700 transition-all disabled:opacity-50 flex items-center justify-center gap-1.5"
                >
                  {isAdding || isUploading ? <Loader2 className="w-4 h-4 animate-spin" /> : <Upload className="w-4 h-4" />}
                  {isAdding ? '导入中...' : isUploading ? '上传中...' : addType === 'file' ? '上传文件' : '导入知识库'}
                </button>
              </div>
            )}
          </div>

          {/* Search */}
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
                      <div
                        key={i}
                        className="p-2 bg-violet-50/50 rounded-lg border border-violet-100"
                      >
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

          {/* Document List */}
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
                        ) : doc.type === 'file' ? (
                          <Upload className="w-3.5 h-3.5 text-violet-600" />
                        ) : (
                          <FileText className="w-3.5 h-3.5 text-violet-600" />
                        )}
                      </div>
                      <div className="flex-1 min-w-0">
                        <p className="text-xs font-medium text-slate-700 truncate">{doc.title}</p>
                        <div className="flex items-center gap-1.5 mt-0.5">
                          <span className="text-[10px] text-slate-400">
                            {doc.createdAt.toLocaleDateString()}
                          </span>
                          {doc.embeddingStatus === 'COMPLETED' ? (
                            <span className="text-[10px] px-1 py-0.5 bg-green-50 text-green-600 rounded">
                              已向量化 {doc.chunkCount ? `${doc.chunkCount}片` : ''}
                            </span>
                          ) : doc.embeddingStatus === 'PROCESSING' ? (
                            <span className="text-[10px] px-1 py-0.5 bg-amber-50 text-amber-600 rounded flex items-center gap-0.5">
                              <Loader2 className="w-2.5 h-2.5 animate-spin" />
                              处理中
                            </span>
                          ) : doc.embeddingStatus === 'FAILED' ? (
                            <span className="text-[10px] px-1 py-0.5 bg-red-50 text-red-500 rounded">
                              向量化失败
                            </span>
                          ) : doc.embeddingStatus === 'SKIPPED' ? (
                            <span className="text-[10px] px-1 py-0.5 bg-slate-50 text-slate-400 rounded">
                              无需向量化
                            </span>
                          ) : (
                            <span className="text-[10px] px-1 py-0.5 bg-slate-50 text-slate-400 rounded">
                              等待处理
                            </span>
                          )}
                        </div>
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

        {/* Right Panel - Chat */}
        <div className="flex-1 flex flex-col min-w-0">
          {/* Chat Messages */}
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
                className={`flex gap-3 ${msg.role === 'user' ? 'justify-end' : 'justify-start'}`}
              >
                {msg.role === 'assistant' && (
                  <div className="w-8 h-8 bg-gradient-to-br from-violet-500 to-purple-600 rounded-xl flex items-center justify-center shrink-0 shadow-sm">
                    <Bot className="w-4 h-4 text-white" />
                  </div>
                )}
                <div
                  className={`max-w-[70%] rounded-2xl px-4 py-3 text-sm leading-relaxed ${
                    msg.role === 'user'
                      ? 'bg-gradient-to-r from-violet-500 to-purple-600 text-white shadow-md'
                      : 'bg-white border border-slate-200 text-slate-700 shadow-sm'
                  }`}
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

          {/* Input Area */}
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
  );
}
