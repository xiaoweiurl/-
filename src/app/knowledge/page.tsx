'use client';

import { useState, useRef, useEffect, useCallback } from 'react';
import {
  MessageSquare,
  Upload,
  Search,
  BookOpen,
  Send,
  Plus,
  FileText,
  Trash2,
  Bot,
  User,
  Loader2,
  ChevronDown,
  ChevronUp,
  Sparkles,
  FileUp,
  File,
  FileSpreadsheet,
  X,
  RefreshCw,
} from 'lucide-react';
import { backendFetch, backendFetchFormData, getBackendUrl, getSessionId } from '@/lib/backend-proxy';

interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  sources?: Array<{ content: string; score: number; docId?: string }>;
  timestamp: Date;
}

interface DocumentEntry {
  id: string;
  fileName: string;
  fileType: string;
  fileSize: number;
  domainCode: string;
  status: string;
  chunkCount: number;
  errorMessage: string | null;
  createdAt: string;
}

interface DomainEntry {
  code: string;
  name: string;
  icon: string;
  color: string;
}

export default function KnowledgePage() {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [sessionId, setSessionId] = useState<string>(crypto.randomUUID());
  const chatEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);

  // 知识库管理
  const [documents, setDocuments] = useState<DocumentEntry[]>([]);
  const [domains, setDomains] = useState<DomainEntry[]>([]);
  const [selectedDomain, setSelectedDomain] = useState<string>('');
  const [showUploadPanel, setShowUploadPanel] = useState(false);
  const [uploadFiles, setUploadFiles] = useState<File[]>([]);
  const [isUploading, setIsUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState<string>('');
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState<Array<{ content: string; score: number; title?: string }>>([]);
  const [isSearching, setIsSearching] = useState(false);
  const [showSearch, setShowSearch] = useState(false);
  const [isLoadingDocs, setIsLoadingDocs] = useState(false);

  // 拖拽上传
  const [isDragOver, setIsDragOver] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  // 登录检查
  useEffect(() => {
    const sid = localStorage.getItem('session_id');
    if (!sid) {
      window.location.href = '/login';
    }
  }, []);

  // 浏览器后退防护
  useEffect(() => {
    const handlePageShow = (e: PageTransitionEvent) => {
      if (e.persisted) {
        const sid = localStorage.getItem('session_id');
        if (!sid) {
          window.location.href = '/login';
        }
      }
    };
    window.addEventListener('pageshow', handlePageShow);
    return () => window.removeEventListener('pageshow', handlePageShow);
  }, []);

  // 加载知识域
  useEffect(() => {
    loadDomains();
  }, []);

  // 加载文档列表
  useEffect(() => {
    loadDocuments();
  }, []);

  // 自动滚动
  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const loadDomains = async () => {
    try {
      const res = await backendFetch('/memory/domains');
      const data = await res.json();
      if (data.code === 200 && data.data) {
        setDomains(data.data);
      }
    } catch (error) {
      console.error('Load domains error:', error);
    }
  };

  const loadDocuments = async () => {
    setIsLoadingDocs(true);
    try {
      const res = await backendFetch('/memory/documents');
      const data = await res.json();
      if (data.code === 200 && data.data) {
        setDocuments(data.data);
      }
    } catch (error) {
      console.error('Load documents error:', error);
    } finally {
      setIsLoadingDocs(false);
    }
  };

  // 文件拖拽处理
  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragOver(true);
  };

  const handleDragLeave = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragOver(false);
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragOver(false);
    const files = Array.from(e.dataTransfer.files).filter(
      (f) =>
        f.type === 'application/pdf' ||
        f.type.includes('word') ||
        f.type.includes('spreadsheet') ||
        f.type.includes('excel') ||
        f.type.includes('presentation') ||
        f.type === 'text/plain' ||
        f.name.endsWith('.doc') ||
        f.name.endsWith('.docx') ||
        f.name.endsWith('.xls') ||
        f.name.endsWith('.xlsx') ||
        f.name.endsWith('.ppt') ||
        f.name.endsWith('.pptx') ||
        f.name.endsWith('.txt')
    );
    if (files.length > 0) {
      setUploadFiles((prev) => [...prev, ...files]);
      setShowUploadPanel(true);
    }
  };

  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(e.target.files || []);
    if (files.length > 0) {
      setUploadFiles((prev) => [...prev, ...files]);
      setShowUploadPanel(true);
    }
    if (fileInputRef.current) fileInputRef.current.value = '';
  };

  const removeFile = (index: number) => {
    setUploadFiles((prev) => prev.filter((_, i) => i !== index));
  };

  // 上传文件到后端
  const handleUpload = async () => {
    if (uploadFiles.length === 0) return;
    setIsUploading(true);
    setUploadProgress('准备上传...');

    let successCount = 0;
    let failCount = 0;

    for (let i = 0; i < uploadFiles.length; i++) {
      const file = uploadFiles[i];
      setUploadProgress(`上传中 (${i + 1}/${uploadFiles.length}): ${file.name}`);

      try {
        const formData = new FormData();
        formData.append('file', file);
        if (selectedDomain) {
          formData.append('domainCode', selectedDomain);
        }

        const res = await backendFetchFormData('/memory/upload', formData);
        const data = await res.json();

        if (data.code === 200) {
          successCount++;
        } else {
          failCount++;
          console.error(`Upload failed for ${file.name}:`, data.message);
        }
      } catch (error) {
        failCount++;
        console.error(`Upload error for ${file.name}:`, error);
      }
    }

    setUploadProgress(`上传完成: 成功 ${successCount} 个${failCount > 0 ? `, 失败 ${failCount} 个` : ''}`);
    setUploadFiles([]);
    loadDocuments();

    setTimeout(() => {
      setIsUploading(false);
      setUploadProgress('');
      setShowUploadPanel(false);
    }, 2000);
  };

  // 删除文档
  const handleDeleteDoc = async (docId: string) => {
    if (!confirm('确定删除此文档？关联的知识卡片也会一并删除。')) return;
    try {
      await backendFetch(`/memory/documents/${docId}`, { method: 'DELETE' });
      setDocuments((prev) => prev.filter((d) => d.id !== docId));
    } catch (error) {
      console.error('Delete doc error:', error);
    }
  };

  // 搜索知识库
  const handleSearch = async () => {
    if (!searchQuery.trim()) return;
    setIsSearching(true);
    try {
      const res = await backendFetch(`/memory/search?query=${encodeURIComponent(searchQuery)}&minScore=0.3`);
      const data = await res.json();
      if (data.code === 200 && data.data) {
        setSearchResults(data.data);
      }
    } catch (error) {
      console.error('Search error:', error);
    } finally {
      setIsSearching(false);
    }
  };

  // 发送消息（RAG对话，SSE流式）
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
      const params = new URLSearchParams({
        message: userMessage.content,
        sessionId: sessionId,
      });

      const backendUrl = getBackendUrl();
      const sid = getSessionId();
      const headers: Record<string, string> = {};
      if (sid) headers['X-Session-Id'] = sid;

      const response = await fetch(`${backendUrl}/memory/chat?${params}`, {
        method: 'GET',
        headers,
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
          if (line.startsWith('data:')) {
            const data = line.slice(5).trim();
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
              // skip non-JSON lines
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
  }, [input, isLoading, sessionId]);

  // 新建对话
  const handleNewChat = () => {
    setMessages([]);
    setSessionId(crypto.randomUUID());
  };

  // 键盘快捷键
  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  // 文件图标
  const getFileIcon = (type: string) => {
    if (type === 'pdf') return <FileText className="w-3.5 h-3.5 text-red-500" />;
    if (type === 'word' || type === 'doc' || type === 'docx') return <FileText className="w-3.5 h-3.5 text-blue-500" />;
    if (type === 'excel' || type === 'xls' || type === 'xlsx') return <FileSpreadsheet className="w-3.5 h-3.5 text-green-500" />;
    return <File className="w-3.5 h-3.5 text-slate-500" />;
  };

  // 文件大小格式化
  const formatSize = (bytes: number) => {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
  };

  // 状态颜色
  const getStatusColor = (status: string) => {
    switch (status) {
      case 'COMPLETED': return 'text-green-500';
      case 'PROCESSING': return 'text-yellow-500';
      case 'FAILED': return 'text-red-500';
      default: return 'text-slate-400';
    }
  };

  const getStatusLabel = (status: string) => {
    switch (status) {
      case 'COMPLETED': return '已完成';
      case 'PROCESSING': return '处理中';
      case 'FAILED': return '失败';
      case 'PENDING': return '等待中';
      default: return status;
    }
  };

  return (
    <div
      className="h-screen flex flex-col bg-gradient-to-br from-slate-50 via-violet-50/30 to-purple-50/20"
      onDragOver={handleDragOver}
      onDragLeave={handleDragLeave}
      onDrop={handleDrop}
    >
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

      {/* Drag overlay */}
      {isDragOver && (
        <div className="fixed inset-0 bg-violet-500/20 backdrop-blur-sm z-50 flex items-center justify-center pointer-events-none">
          <div className="bg-white rounded-2xl p-8 shadow-2xl border-2 border-dashed border-violet-400 flex flex-col items-center gap-3">
            <FileUp className="w-12 h-12 text-violet-500" />
            <p className="text-lg font-semibold text-slate-700">释放文件以上传到知识库</p>
            <p className="text-sm text-slate-400">支持 PDF、Word、Excel、TXT 文件</p>
          </div>
        </div>
      )}

      {/* Main Content */}
      <div className="flex flex-1 min-h-0">
        {/* Left Panel - Knowledge Management */}
        <div className="w-80 border-r border-slate-200/60 bg-white/50 flex flex-col shrink-0">
          {/* Upload Section */}
          <div className="p-4 border-b border-slate-200/40">
            <div className="flex items-center justify-between mb-3">
              <h2 className="text-sm font-semibold text-slate-700 flex items-center gap-1.5">
                <Sparkles className="w-4 h-4 text-violet-500" />
                知识库管理
              </h2>
              <div className="flex items-center gap-1">
                <button
                  onClick={loadDocuments}
                  className="p-1.5 hover:bg-violet-100 rounded-lg transition-colors"
                  title="刷新"
                >
                  <RefreshCw className="w-3.5 h-3.5 text-slate-400" />
                </button>
                <button
                  onClick={() => setShowUploadPanel(!showUploadPanel)}
                  className="p-1.5 hover:bg-violet-100 rounded-lg transition-colors"
                >
                  <Plus className="w-4 h-4 text-violet-500" />
                </button>
              </div>
            </div>

            {/* Upload Panel */}
            {showUploadPanel && (
              <div className="space-y-3 p-3 bg-violet-50/50 rounded-xl border border-violet-100">
                {/* Domain selector */}
                {domains.length > 0 && (
                  <select
                    value={selectedDomain}
                    onChange={(e) => setSelectedDomain(e.target.value)}
                    className="w-full px-3 py-2 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-violet-300 bg-white"
                  >
                    <option value="">自动分类</option>
                    {domains.map((d) => (
                      <option key={d.code} value={d.code}>{d.name}</option>
                    ))}
                  </select>
                )}

                {/* File drop zone */}
                <div
                  onClick={() => fileInputRef.current?.click()}
                  className="border-2 border-dashed border-violet-200 rounded-lg p-4 text-center cursor-pointer hover:border-violet-400 hover:bg-violet-50 transition-colors"
                >
                  <FileUp className="w-8 h-8 text-violet-400 mx-auto mb-2" />
                  <p className="text-xs text-slate-500">点击或拖拽文件到此处</p>
                  <p className="text-[10px] text-slate-400 mt-1">PDF / Word / Excel / TXT</p>
                </div>
                <input
                  ref={fileInputRef}
                  type="file"
                  multiple
                  accept=".pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt"
                  onChange={handleFileSelect}
                  className="hidden"
                />

                {/* Selected files */}
                {uploadFiles.length > 0 && (
                  <div className="space-y-1.5 max-h-32 overflow-y-auto">
                    {uploadFiles.map((file, i) => (
                      <div key={i} className="flex items-center gap-2 p-1.5 bg-white rounded-lg border border-slate-100">
                        <FileText className="w-3.5 h-3.5 text-violet-500 shrink-0" />
                        <span className="text-xs text-slate-600 truncate flex-1">{file.name}</span>
                        <button onClick={() => removeFile(i)} className="p-0.5 hover:bg-red-50 rounded">
                          <X className="w-3 h-3 text-red-400" />
                        </button>
                      </div>
                    ))}
                  </div>
                )}

                {/* Upload progress */}
                {uploadProgress && (
                  <p className="text-xs text-violet-600 text-center">{uploadProgress}</p>
                )}

                {/* Upload button */}
                {uploadFiles.length > 0 && !isUploading && (
                  <button
                    onClick={handleUpload}
                    className="w-full py-2 bg-gradient-to-r from-violet-500 to-purple-600 text-white rounded-lg text-sm font-medium hover:from-violet-600 hover:to-purple-700 transition-all flex items-center justify-center gap-1.5"
                  >
                    <Upload className="w-4 h-4" />
                    上传到知识库
                  </button>
                )}
                {isUploading && (
                  <div className="w-full py-2 bg-violet-100 text-violet-700 rounded-lg text-sm font-medium flex items-center justify-center gap-1.5">
                    <Loader2 className="w-4 h-4 animate-spin" />
                    上传中...
                  </div>
                )}
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
                          {result.title && (
                            <span className="text-[10px] text-slate-400 truncate">{result.title}</span>
                          )}
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
            <div className="flex items-center justify-between mb-2">
              <h3 className="text-sm font-semibold text-slate-700 flex items-center gap-1.5">
                <FileText className="w-4 h-4 text-violet-500" />
                已上传文档 ({documents.length})
              </h3>
            </div>

            {isLoadingDocs ? (
              <div className="flex items-center justify-center py-8">
                <Loader2 className="w-5 h-5 text-violet-400 animate-spin" />
              </div>
            ) : documents.length === 0 ? (
              <div className="text-center py-8">
                <BookOpen className="w-10 h-10 text-slate-300 mx-auto mb-2" />
                <p className="text-xs text-slate-400">暂无文档，点击上方 + 上传</p>
                <p className="text-[10px] text-slate-300 mt-1">支持拖拽文件到页面任意位置</p>
              </div>
            ) : (
              <div className="space-y-2">
                {documents.map((doc) => (
                  <div
                    key={doc.id}
                    className="group flex items-start gap-2 p-2.5 bg-white rounded-lg border border-slate-100 hover:border-violet-200 transition-colors"
                  >
                    <div className="w-7 h-7 bg-violet-100 rounded-lg flex items-center justify-center shrink-0 mt-0.5">
                      {getFileIcon(doc.fileType)}
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="text-xs font-medium text-slate-700 truncate">{doc.fileName}</p>
                      <div className="flex items-center gap-2 mt-0.5">
                        <span className={`text-[10px] ${getStatusColor(doc.status)}`}>
                          {getStatusLabel(doc.status)}
                        </span>
                        <span className="text-[10px] text-slate-400">{formatSize(doc.fileSize)}</span>
                        {doc.chunkCount > 0 && (
                          <span className="text-[10px] text-slate-400">{doc.chunkCount} 切片</span>
                        )}
                      </div>
                      {doc.errorMessage && (
                        <p className="text-[10px] text-red-400 mt-0.5 truncate">{doc.errorMessage}</p>
                      )}
                    </div>
                    <button
                      onClick={() => handleDeleteDoc(doc.id)}
                      className="opacity-0 group-hover:opacity-100 p-1 hover:bg-red-50 rounded transition-all"
                    >
                      <Trash2 className="w-3 h-3 text-red-400" />
                    </button>
                  </div>
                ))}
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
                  基于知识库的智能问答，先上传知识文档，AI 回答时会自动检索知识库中的内容
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
            <div className="max-w-4xl mx-auto">
              <div className="flex gap-3 items-end">
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
                <button
                  onClick={handleNewChat}
                  disabled={messages.length === 0}
                  className="p-3 bg-white border border-slate-200 text-slate-500 rounded-xl hover:bg-slate-50 transition-all disabled:opacity-30 disabled:cursor-not-allowed shrink-0"
                  title="新建对话"
                >
                  <Plus className="w-5 h-5" />
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
