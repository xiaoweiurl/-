'use client';

import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import {
  Brain, Plus, Search, Send, Package, FlaskConical, Users, Swords,
  Truck, ShieldCheck, Calculator, Gavel, ChevronRight,
  X, Eye, Clock, CheckCircle, Sparkles,
  BookOpen, Loader2, Upload, FileText, Trash2, RotateCcw, File,
  FileType, FileSpreadsheet, LogOut, Settings, User, Bell,
  LayoutGrid, List, SlidersHorizontal,
} from 'lucide-react';
import Sidebar from '@/components/Sidebar';
import { CurrentUser } from '@/components/Header';
import { cn } from '@/lib/utils';
import { Button } from '@/components/ui/button';

// 知识域图标映射
const DOMAIN_ICONS: Record<string, React.ReactNode> = {
  product: <Package className="w-4 h-4" />,
  rd: <FlaskConical className="w-4 h-4" />,
  customer: <Users className="w-4 h-4" />,
  competitive: <Swords className="w-4 h-4" />,
  supply_chain: <Truck className="w-4 h-4" />,
  quality: <ShieldCheck className="w-4 h-4" />,
  finance: <Calculator className="w-4 h-4" />,
  governance: <Gavel className="w-4 h-4" />,
};

const DOMAIN_COLORS: Record<string, string> = {
  product: 'from-violet-500 to-purple-600',
  rd: 'from-blue-500 to-cyan-600',
  customer: 'from-green-500 to-emerald-600',
  competitive: 'from-red-500 to-rose-600',
  supply_chain: 'from-orange-500 to-amber-600',
  quality: 'from-emerald-500 to-teal-600',
  finance: 'from-yellow-500 to-orange-600',
  governance: 'from-slate-500 to-gray-600',
};

interface Domain {
  code: string;
  name: string;
  description: string;
  icon: string;
  color: string;
  sort_order: number;
}

interface KnowledgeCard {
  id: string;
  domainCode: string;
  domain_code: string;
  domain_name: string;
  domain_icon: string;
  domain_color: string;
  title: string;
  content: string;
  tags: string[];
  product_code: string | null;
  source: string | null;
  confidence: string;
  status: string;
  created_by: string;
  user_id: string;
  created_at: string;
}

interface KnowledgeDocument {
  id: string;
  userId: string;
  fileName: string;
  fileType: string;
  fileSize: number;
  domainCode: string;
  status: string;
  chunkCount: number;
  errorMessage: string | null;
  createdAt: string;
  updatedAt: string;
}

interface ChatMessage {
  role: 'user' | 'assistant' | 'system';
  content: string;
  sources?: Array<{ id: string; title: string; domain: string; score: number }>;
  isLoading?: boolean;
}

const FILE_ICONS: Record<string, React.ReactNode> = {
  pdf: <FileText className="w-5 h-5 text-red-500" />,
  doc: <File className="w-5 h-5 text-blue-500" />,
  docx: <File className="w-5 h-5 text-blue-500" />,
  xls: <FileSpreadsheet className="w-5 h-5 text-green-500" />,
  xlsx: <FileSpreadsheet className="w-5 h-5 text-green-500" />,
  csv: <FileSpreadsheet className="w-5 h-5 text-green-500" />,
  txt: <FileType className="w-5 h-5 text-slate-500" />,
};

// 统一使用 Next.js API 代理
function getSessionId(): string | null {
  if (typeof window === 'undefined') return null;
  return localStorage.getItem('session_id');
}

const memoryApi = {
  get: (path: string) => {
    const sid = getSessionId();
    return fetch(`/api/proxy/memory${path}`, {
      credentials: 'include',
      headers: sid ? { 'X-Session-Id': sid } : undefined,
    });
  },
  post: (path: string, body?: unknown) => {
    const sid = getSessionId();
    const headers: Record<string, string> = {};
    if (sid) headers['X-Session-Id'] = sid;
    if (body && !(body instanceof FormData)) headers['Content-Type'] = 'application/json';
    return fetch(`/api/proxy/memory${path}`, {
      method: 'POST',
      headers: Object.keys(headers).length > 0 ? headers : undefined,
      body: body instanceof FormData ? body : body ? JSON.stringify(body) : undefined,
      credentials: 'include',
    });
  },
  put: (path: string, body: unknown) => {
    const sid = getSessionId();
    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (sid) headers['X-Session-Id'] = sid;
    return fetch(`/api/proxy/memory${path}`, {
      method: 'PUT',
      headers,
      body: JSON.stringify(body),
      credentials: 'include',
    });
  },
  del: (path: string) => {
    const sid = getSessionId();
    return fetch(`/api/proxy/memory${path}`, {
      method: 'DELETE',
      credentials: 'include',
      headers: sid ? { 'X-Session-Id': sid } : undefined,
    });
  },
};

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B';
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
}

export default function MemoryPage() {
  const router = useRouter();
  const [currentUser, setCurrentUser] = useState<CurrentUser | null>(null);
  const [domains, setDomains] = useState<Domain[]>([]);
  const [activeDomain, setActiveDomain] = useState<string>('');
  const [cards, setCards] = useState<KnowledgeCard[]>([]);
  const [cardsTotal, setCardsTotal] = useState(0);
  const [searchKeyword, setSearchKeyword] = useState('');
  const [viewingCard, setViewingCard] = useState<KnowledgeCard | null>(null);

  // 文档上传
  const [documents, setDocuments] = useState<KnowledgeDocument[]>([]);
  const [uploading, setUploading] = useState(false);
  const [uploadDomain, setUploadDomain] = useState<string>('');
  const fileInputRef = useRef<HTMLInputElement>(null);

  // AI问答
  const [chatMessages, setChatMessages] = useState<ChatMessage[]>([]);
  const [chatInput, setChatInput] = useState('');
  const [isChatting, setIsChatting] = useState(false);
  const [sessionId, setSessionId] = useState<string>('');
  const chatEndRef = useRef<HTMLDivElement>(null);
  const [showChat, setShowChat] = useState(false);

  // 新建卡片表单
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [newCard, setNewCard] = useState({
    domainCode: '',
    title: '',
    content: '',
    tags: '',
    productCode: '',
    source: '',
    confidence: 'medium',
  });
  const [isCreating, setIsCreating] = useState(false);

  // 左侧面板模式
  const [leftPanel, setLeftPanel] = useState<'domains' | 'documents' | 'upload'>('domains');

  // 检查登录状态
  useEffect(() => {
    fetch('/api/auth/login').then(res => res.json()).then(data => {
      if (data.loggedIn) {
        setCurrentUser(data.user);
      } else {
        router.push('/login');
      }
    }).catch(() => {
      router.push('/login');
    });
  }, [router]);

  // 加载知识域
  useEffect(() => {
    fetchDomains();
  }, []);

  // 切换知识域时加载卡片
  useEffect(() => {
    if (activeDomain) fetchCards();
  }, [activeDomain]);

  // 自动刷新文档状态
  useEffect(() => {
    if (leftPanel === 'documents') fetchDocuments();
    const interval = setInterval(() => {
      if (leftPanel === 'documents') fetchDocuments();
    }, 5000);
    return () => clearInterval(interval);
  }, [leftPanel]);

  const fetchDomains = async () => {
    try {
      const res = await memoryApi.get('/domains');
      const data = await res.json();
      if (data.success) {
        setDomains(data.domains);
        if (data.domains.length > 0 && !activeDomain) {
          setActiveDomain(data.domains[0].code);
          setUploadDomain(data.domains[0].code);
          setNewCard(prev => ({ ...prev, domainCode: data.domains[0].code }));
        }
      }
    } catch (err) {
      console.error('加载知识域失败:', err);
    }
  };

  const fetchCards = async () => {
    try {
      const params = new URLSearchParams({ domainCode: activeDomain });
      if (searchKeyword) params.set('keyword', searchKeyword);
      const res = await memoryApi.get(`/cards?${params}`);
      const data = await res.json();
      if (data.success) {
        setCards(data.cards || []);
        setCardsTotal(data.total || 0);
      }
    } catch (err) {
      console.error('加载卡片失败:', err);
    }
  };

  const fetchDocuments = async () => {
    try {
      const res = await memoryApi.get('/documents');
      const data = await res.json();
      if (data.success) {
        setDocuments(data.documents || []);
      }
    } catch (err) {
      console.error('加载文档列表失败:', err);
    }
  };

  // 文档上传
  const handleFileUpload = async (files: FileList | null) => {
    if (!files || files.length === 0) return;
    setUploading(true);

    try {
      for (let i = 0; i < files.length; i++) {
        const file = files[i];
        const formData = new FormData();
        formData.append('file', file);
        formData.append('domainCode', uploadDomain || 'product');

        const res = await memoryApi.post('/upload', formData);
        const data = await res.json();
        if (!data.success) {
          alert(`上传 ${file.name} 失败: ${data.message || '未知错误'}`);
        }
      }
      fetchDocuments();
      setLeftPanel('documents');
    } catch (err) {
      console.error('上传失败:', err);
      alert('上传失败，请重试');
    } finally {
      setUploading(false);
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  };

  const handleDeleteDocument = async (docId: string) => {
    if (!confirm('确定删除此文档？关联的知识卡片也会被删除。')) return;
    try {
      await memoryApi.del(`/documents/${docId}`);
      fetchDocuments();
      fetchCards();
    } catch (err) {
      console.error('删除文档失败:', err);
    }
  };

  const handleCreateCard = async () => {
    if (!newCard.title || !newCard.content) return;
    setIsCreating(true);
    try {
      const res = await memoryApi.post('/cards', {
          domainCode: newCard.domainCode,
          title: newCard.title,
          content: newCard.content,
          tags: newCard.tags ? newCard.tags.split(',').map((t: string) => t.trim()).filter(Boolean) : [],
          productCode: newCard.productCode || undefined,
          source: newCard.source || undefined,
          confidence: newCard.confidence,
        });
      const data = await res.json();
      if (data.success) {
        setShowCreateModal(false);
        setNewCard(prev => ({ ...prev, title: '', content: '', tags: '', productCode: '', source: '' }));
        fetchCards();
      } else {
        alert(data.error || '创建失败');
      }
    } catch (err) {
      console.error('创建卡片失败:', err);
    } finally {
      setIsCreating(false);
    }
  };

  const handleDeleteCard = async (cardId: string) => {
    if (!confirm('确定删除此知识卡片？')) return;
    try {
      await memoryApi.del(`/cards/${cardId}`);
      fetchCards();
    } catch (err) {
      console.error('删除卡片失败:', err);
    }
  };

  // AI问答 (SSE流式 + 上下文)
  const handleSendChat = useCallback(async () => {
    if (!chatInput.trim() || isChatting) return;

    const currentSessionId = sessionId || crypto.randomUUID();
    if (!sessionId) setSessionId(currentSessionId);

    const userMsg: ChatMessage = { role: 'user', content: chatInput.trim() };
    setChatMessages(prev => [...prev, userMsg]);
    setChatInput('');
    setIsChatting(true);

    const assistantMsg: ChatMessage = {
      role: 'assistant',
      content: '',
      sources: [],
      isLoading: true,
    };
    setChatMessages(prev => [...prev, assistantMsg]);

    try {
      const params = new URLSearchParams({
        message: userMsg.content,
        sessionId: currentSessionId,
      });
      const chatSid = getSessionId();
      const chatHeaders: Record<string, string> = { 'Accept': 'text/event-stream' };
      if (chatSid) chatHeaders['X-Session-Id'] = chatSid;
      const res = await fetch(`/api/proxy/memory/chat?${params}`, {
        method: 'GET',
        headers: chatHeaders,
        credentials: 'include',
      });

      if (!res.ok) throw new Error('请求失败');
      if (!res.body) throw new Error('无法读取响应');

      const reader = res.body.getReader();
      const decoder = new TextDecoder();
      let fullContent = '';
      let sources: ChatMessage['sources'] = [];

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        const text = decoder.decode(value, { stream: true });
        const lines = text.split('\n');
        let currentEvent = '';

        for (const line of lines) {
          if (line.startsWith('event:')) {
            currentEvent = line.substring(6).trim();
          } else if (line.startsWith('data:')) {
            const dataStr = line.substring(5).trim();
            if (!dataStr) continue;
            try {
              const parsed = JSON.parse(dataStr);
              if (parsed.type === 'sources') {
                sources = parsed.sources;
                setChatMessages(prev => {
                  const updated = [...prev];
                  updated[updated.length - 1] = {
                    role: 'assistant',
                    content: fullContent,
                    sources,
                    isLoading: true,
                  };
                  return updated;
                });
              } else if (parsed.type === 'content') {
                fullContent += parsed.content;
                setChatMessages(prev => {
                  const updated = [...prev];
                  updated[updated.length - 1] = {
                    role: 'assistant',
                    content: fullContent,
                    sources,
                    isLoading: false,
                  };
                  return updated;
                });
              } else if (parsed.type === 'done') {
                setChatMessages(prev => {
                  const updated = [...prev];
                  updated[updated.length - 1] = {
                    role: 'assistant',
                    content: fullContent,
                    sources,
                    isLoading: false,
                  };
                  return updated;
                });
              } else if (parsed.type === 'error') {
                fullContent += `\n\n错误: ${parsed.content || parsed.error || '未知错误'}`;
              }
            } catch {
              // ignore parse errors
            }
          }
        }
      }

      setChatMessages(prev => {
        const updated = [...prev];
        updated[updated.length - 1] = {
          role: 'assistant',
          content: fullContent,
          sources,
          isLoading: false,
        };
        return updated;
      });
    } catch (err) {
      console.error('对话失败:', err);
      setChatMessages(prev => {
        const updated = [...prev];
        updated[updated.length - 1] = {
          role: 'assistant',
          content: '抱歉，对话出现错误，请稍后重试。',
          isLoading: false,
        };
        return updated;
      });
    } finally {
      setIsChatting(false);
    }
  }, [chatInput, isChatting, sessionId]);

  // 新建对话
  const handleNewChat = () => {
    const newSid = crypto.randomUUID();
    setSessionId(newSid);
    setChatMessages([]);
  };

  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [chatMessages]);

  const confidenceLabel: Record<string, { text: string; color: string }> = {
    high: { text: '高', color: 'text-green-600 bg-green-50' },
    medium: { text: '中', color: 'text-yellow-600 bg-yellow-50' },
    low: { text: '低', color: 'text-red-600 bg-red-50' },
  };

  const docStatusMap: Record<string, { text: string; color: string }> = {
    processing: { text: '处理中', color: 'text-blue-600 bg-blue-50' },
    completed: { text: '已完成', color: 'text-green-600 bg-green-50' },
    failed: { text: '失败', color: 'text-red-600 bg-red-50' },
    empty: { text: '内容为空', color: 'text-slate-600 bg-slate-50' },
  };

  // 拖拽上传
  const [dragOver, setDragOver] = useState(false);

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault();
    setDragOver(true);
  };

  const handleDragLeave = () => {
    setDragOver(false);
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setDragOver(false);
    handleFileUpload(e.dataTransfer.files);
  };

  // 侧边栏导航
  const handleMenuItemClick = (item: string) => {
    if (item === 'memory') return; // 当前页面
    if (item === 'knowledge') {
      router.push('/knowledge');
      return;
    }
    // 其他菜单项返回主页
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
        activeItem="memory"
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
        {/* 顶部栏 - 与主页面风格统一 */}
        <header className="h-14 bg-white/80 backdrop-blur-sm border-b border-slate-200/60 flex items-center justify-between px-6">
          <div className="flex items-center gap-3">
            <div className="flex items-center gap-2">
              <Brain className="w-5 h-5 text-violet-600" />
              <h1 className="text-lg font-semibold text-slate-800">记忆库</h1>
            </div>
            <span className="text-xs px-2 py-0.5 bg-violet-50 text-violet-600 rounded-lg font-medium">
              {domains.length} 个知识域
            </span>
          </div>
          <div className="flex items-center gap-3">
            {/* 搜索框 */}
            <div className="relative">
              <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
              <input
                type="text"
                placeholder="搜索知识卡片..."
                value={searchKeyword}
                onChange={(e) => setSearchKeyword(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && fetchCards()}
                className="pl-9 pr-4 py-1.5 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-violet-300 focus:border-violet-300 w-56 bg-slate-50/50"
              />
            </div>
            {/* 新建按钮 */}
            <button
              onClick={() => {
                setNewCard(prev => ({ ...prev, domainCode: activeDomain }));
                setShowCreateModal(true);
              }}
              className="flex items-center gap-1.5 px-3 py-1.5 bg-gradient-to-r from-violet-500 to-purple-600 text-white rounded-xl text-sm font-medium hover:shadow-lg transition-all"
            >
              <Plus className="w-4 h-4" />
              新建卡片
            </button>
            {/* AI问答切换 */}
            <button
              onClick={() => setShowChat(!showChat)}
              className={cn(
                "flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-sm font-medium transition-all",
                showChat
                  ? "bg-gradient-to-r from-violet-500 to-purple-600 text-white shadow-lg"
                  : "bg-violet-50 text-violet-700 hover:bg-violet-100"
              )}
            >
              <Sparkles className="w-4 h-4" />
              AI 问答
            </button>
            {/* 用户菜单 */}
            {currentUser && (
              <div className="flex items-center gap-2 ml-2 pl-3 border-l border-slate-200">
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
        <div className="flex-1 flex overflow-hidden">
          {/* 左侧 - 知识域/文档/上传 (窄面板) */}
          <div className="w-64 bg-white border-r border-slate-200/60 flex flex-col flex-shrink-0">
            {/* Tab切换 */}
            <div className="flex border-b border-slate-200/60">
              {[
                { key: 'domains' as const, label: '知识域', icon: <BookOpen className="w-3.5 h-3.5" /> },
                { key: 'documents' as const, label: '文档', icon: <FileText className="w-3.5 h-3.5" /> },
                { key: 'upload' as const, label: '上传', icon: <Upload className="w-3.5 h-3.5" /> },
              ].map(tab => (
                <button
                  key={tab.key}
                  onClick={() => setLeftPanel(tab.key)}
                  className={cn(
                    "flex-1 flex items-center justify-center gap-1 py-2.5 text-xs font-medium transition-all",
                    leftPanel === tab.key
                      ? 'text-violet-700 border-b-2 border-violet-600 bg-violet-50/50'
                      : 'text-slate-500 hover:text-slate-700 hover:bg-slate-50'
                  )}
                >
                  {tab.icon}
                  {tab.label}
                </button>
              ))}
            </div>

            <div className="flex-1 overflow-y-auto">
              {/* 知识域列表 */}
              {leftPanel === 'domains' && (
                <div className="p-2">
                  {domains.map((domain) => {
                    const isActive = activeDomain === domain.code;
                    return (
                      <button
                        key={domain.code}
                        onClick={() => setActiveDomain(domain.code)}
                        className={cn(
                          "w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-left transition-all duration-200 mb-1",
                          isActive
                            ? 'bg-gradient-to-r from-violet-50 to-purple-50 text-violet-700 shadow-sm'
                            : 'text-slate-600 hover:bg-slate-50'
                        )}
                      >
                        <div className={cn(
                          "w-8 h-8 rounded-lg flex items-center justify-center",
                          isActive
                            ? `bg-gradient-to-br ${DOMAIN_COLORS[domain.code] || 'from-slate-400 to-slate-500'} text-white`
                            : 'bg-slate-100 text-slate-500'
                        )}>
                          {DOMAIN_ICONS[domain.code] || <BookOpen className="w-4 h-4" />}
                        </div>
                        <div className="flex-1 min-w-0">
                          <div className="text-sm font-medium truncate">{domain.name}</div>
                          <div className="text-xs text-slate-400 truncate">{domain.description?.slice(0, 20)}</div>
                        </div>
                        {isActive && <ChevronRight className="w-4 h-4 text-violet-400" />}
                      </button>
                    );
                  })}
                </div>
              )}

              {/* 文档列表 */}
              {leftPanel === 'documents' && (
                <div className="p-2 space-y-2">
                  {documents.length === 0 ? (
                    <div className="text-center py-8">
                      <FileText className="w-10 h-10 mx-auto text-slate-300 mb-2" />
                      <p className="text-sm text-slate-400">暂无上传文档</p>
                      <button
                        onClick={() => setLeftPanel('upload')}
                        className="mt-2 text-xs text-violet-600 hover:underline"
                      >
                        点击上传
                      </button>
                    </div>
                  ) : (
                    documents.map((doc) => (
                      <div
                        key={doc.id}
                        className="bg-slate-50 rounded-xl p-3 group hover:bg-slate-100 transition-colors"
                      >
                        <div className="flex items-start gap-2">
                          {FILE_ICONS[doc.fileType] || <File className="w-5 h-5 text-slate-400" />}
                          <div className="flex-1 min-w-0">
                            <div className="text-sm font-medium text-slate-700 truncate" title={doc.fileName}>
                              {doc.fileName}
                            </div>
                            <div className="flex items-center gap-2 mt-1">
                              <span className="text-xs text-slate-400">{formatFileSize(doc.fileSize)}</span>
                              <span className={cn("text-xs px-1.5 py-0.5 rounded-md font-medium", docStatusMap[doc.status]?.color || 'text-slate-600 bg-slate-50')}>
                                {docStatusMap[doc.status]?.text || doc.status}
                              </span>
                              {doc.chunkCount > 0 && (
                                <span className="text-xs text-slate-400">{doc.chunkCount} 片段</span>
                              )}
                            </div>
                            {doc.errorMessage && (
                              <div className="text-xs text-red-500 mt-1 truncate" title={doc.errorMessage}>
                                {doc.errorMessage}
                              </div>
                            )}
                          </div>
                          <button
                            onClick={(e) => { e.stopPropagation(); handleDeleteDocument(doc.id); }}
                            className="opacity-0 group-hover:opacity-100 p-1 hover:bg-red-50 rounded-lg transition-opacity"
                            title="删除文档"
                          >
                            <Trash2 className="w-3.5 h-3.5 text-red-400" />
                          </button>
                        </div>
                      </div>
                    ))
                  )}
                </div>
              )}

              {/* 上传区域 */}
              {leftPanel === 'upload' && (
                <div className="p-4">
                  <div
                    onDragOver={handleDragOver}
                    onDragLeave={handleDragLeave}
                    onDrop={handleDrop}
                    className={cn(
                      "border-2 border-dashed rounded-2xl p-6 text-center transition-all",
                      dragOver
                        ? 'border-violet-400 bg-violet-50'
                        : 'border-slate-300 hover:border-violet-300 hover:bg-slate-50'
                    )}
                  >
                    <Upload className={cn("w-8 h-8 mx-auto mb-2", dragOver ? 'text-violet-500' : 'text-slate-300')} />
                    <p className="text-sm font-medium text-slate-600 mb-1">
                      拖拽文件到此处
                    </p>
                    <p className="text-xs text-slate-400 mb-3">
                      PDF / Word / Excel / TXT
                    </p>
                    <button
                      onClick={() => fileInputRef.current?.click()}
                      disabled={uploading}
                      className="px-4 py-2 bg-gradient-to-r from-violet-500 to-purple-600 text-white rounded-xl text-sm font-medium hover:shadow-lg transition-all disabled:opacity-50"
                    >
                      {uploading ? (
                        <span className="flex items-center gap-1.5">
                          <Loader2 className="w-4 h-4 animate-spin" />
                          上传中...
                        </span>
                      ) : '选择文件'}
                    </button>
                    <input
                      ref={fileInputRef}
                      type="file"
                      multiple
                      accept=".pdf,.doc,.docx,.xls,.xlsx,.csv,.txt,.text"
                      className="hidden"
                      onChange={(e) => handleFileUpload(e.target.files)}
                    />
                  </div>

                  <div className="mt-4">
                    <label className="text-sm font-medium text-slate-700">目标知识域</label>
                    <select
                      value={uploadDomain}
                      onChange={(e) => setUploadDomain(e.target.value)}
                      className="mt-1 w-full px-3 py-2 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-violet-300"
                    >
                      {domains.map(d => (
                        <option key={d.code} value={d.code}>{d.name}</option>
                      ))}
                    </select>
                  </div>

                  <div className="mt-4 p-3 bg-violet-50 rounded-xl">
                    <p className="text-xs text-violet-700 font-medium mb-1">上传流程</p>
                    <ol className="text-xs text-violet-600 space-y-0.5 list-decimal list-inside">
                      <li>选择目标知识域</li>
                      <li>上传文件</li>
                      <li>自动解析切片(500字/片)</li>
                      <li>向量化存入数据库</li>
                      <li>AI对话时自动检索</li>
                    </ol>
                  </div>
                </div>
              )}
            </div>

            {/* 底部 - AI问答快捷入口 */}
            {showChat && sessionId && chatMessages.length > 0 && (
              <div className="p-3 border-t border-slate-200/60">
                <button
                  onClick={handleNewChat}
                  className="w-full flex items-center gap-2 px-3 py-2 rounded-xl text-xs text-slate-500 hover:text-violet-600 hover:bg-violet-50 transition-colors"
                >
                  <RotateCcw className="w-3 h-3" />
                  新建对话
                </button>
              </div>
            )}
          </div>

          {/* 中间 - 卡片列表 */}
          <div className="flex-1 flex flex-col min-w-0 overflow-y-auto bg-gradient-to-br from-slate-50/30 to-white">
            {/* 当前知识域标题 */}
            <div className="px-6 pt-6 pb-2">
              <div className="flex items-center gap-3">
                {activeDomain && DOMAIN_ICONS[activeDomain] && (
                  <div className={cn("w-8 h-8 rounded-lg flex items-center justify-center bg-gradient-to-br text-white", DOMAIN_COLORS[activeDomain] || 'from-slate-400 to-slate-500')}>
                    {DOMAIN_ICONS[activeDomain]}
                  </div>
                )}
                <div>
                  <h2 className="text-lg font-semibold text-slate-800">
                    {domains.find(d => d.code === activeDomain)?.name || '知识卡片'}
                  </h2>
                  {activeDomain && (
                    <p className="text-xs text-slate-500">
                      {domains.find(d => d.code === activeDomain)?.description}
                    </p>
                  )}
                </div>
                <span className="text-sm text-slate-400 ml-auto">{cardsTotal} 条</span>
              </div>
            </div>

            {/* 卡片网格 */}
            <div className="flex-1 overflow-y-auto p-6 pt-2">
              {cards.length === 0 ? (
                <div className="flex flex-col items-center justify-center h-full text-slate-400">
                  <BookOpen className="w-16 h-16 mb-4 text-slate-300" />
                  <p className="text-lg font-medium">暂无知识卡片</p>
                  <p className="text-sm mt-1">上传文档或点击「新建卡片」开始沉淀知识</p>
                  <div className="flex gap-3 mt-4">
                    <button
                      onClick={() => setLeftPanel('upload')}
                      className="flex items-center gap-1.5 px-4 py-2 bg-violet-50 text-violet-700 rounded-xl text-sm font-medium hover:bg-violet-100 transition-colors"
                    >
                      <Upload className="w-4 h-4" />
                      上传文档
                    </button>
                    <button
                      onClick={() => {
                        setNewCard(prev => ({ ...prev, domainCode: activeDomain }));
                        setShowCreateModal(true);
                      }}
                      className="flex items-center gap-1.5 px-4 py-2 bg-slate-100 text-slate-600 rounded-xl text-sm font-medium hover:bg-slate-200 transition-colors"
                    >
                      <Plus className="w-4 h-4" />
                      新建卡片
                    </button>
                  </div>
                </div>
              ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
                  {cards.map((card) => {
                    const domainCode = card.domainCode || card.domain_code;
                    return (
                      <div
                        key={card.id}
                        onClick={() => setViewingCard(card)}
                        className="bg-white rounded-2xl border border-slate-200 p-4 hover:shadow-lg hover:-translate-y-0.5 transition-all duration-200 cursor-pointer group relative"
                      >
                        <button
                          onClick={(e) => { e.stopPropagation(); handleDeleteCard(card.id); }}
                          className="absolute top-2 right-2 opacity-0 group-hover:opacity-100 p-1 hover:bg-red-50 rounded-lg transition-opacity"
                          title="删除卡片"
                        >
                          <Trash2 className="w-3.5 h-3.5 text-red-400" />
                        </button>
                        <div className="flex items-start justify-between mb-2">
                          <div className="flex items-center gap-2">
                            <div className={cn("w-6 h-6 rounded-md flex items-center justify-center bg-gradient-to-br text-white", DOMAIN_COLORS[domainCode] || 'from-slate-400 to-slate-500')}>
                              {DOMAIN_ICONS[domainCode] || <BookOpen className="w-3 h-3" />}
                            </div>
                            <span className="text-xs text-slate-400">{card.domain_name || domains.find(d => d.code === domainCode)?.name}</span>
                          </div>
                          {card.confidence && confidenceLabel[card.confidence] && (
                            <span className={cn("text-xs px-1.5 py-0.5 rounded-md font-medium", confidenceLabel[card.confidence].color)}>
                              {confidenceLabel[card.confidence].text}
                            </span>
                          )}
                        </div>
                        <h3 className="font-semibold text-slate-800 text-sm mb-2 line-clamp-2 group-hover:text-violet-700 transition-colors">
                          {card.title}
                        </h3>
                        <p className="text-xs text-slate-500 line-clamp-3 mb-3">{card.content}</p>
                        <div className="flex items-center justify-between">
                          <div className="flex items-center gap-1 flex-wrap">
                            {(card.tags || []).slice(0, 3).map((tag, i) => (
                              <span key={i} className="text-xs px-1.5 py-0.5 bg-slate-100 text-slate-500 rounded">
                                {tag}
                              </span>
                            ))}
                            {(card.tags || []).length > 3 && (
                              <span className="text-xs text-slate-400">+{card.tags.length - 3}</span>
                            )}
                          </div>
                          <div className="flex items-center gap-1 text-slate-400">
                            <Clock className="w-3 h-3" />
                            <span className="text-xs">{new Date(card.created_at).toLocaleDateString()}</span>
                          </div>
                        </div>
                        {card.source && (
                          <div className="mt-2 text-xs text-slate-400 truncate">
                            来源: {card.source}
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          </div>

          {/* 右侧 - AI问答面板 */}
          {showChat && (
            <div className="w-[380px] bg-white border-l border-slate-200/60 flex flex-col flex-shrink-0">
              <div className="p-4 border-b border-slate-200/60">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <Sparkles className="w-5 h-5 text-violet-600" />
                    <h3 className="font-semibold text-slate-800">AI 问答</h3>
                  </div>
                  <div className="flex items-center gap-1">
                    {chatMessages.length > 0 && (
                      <button
                        onClick={handleNewChat}
                        className="p-1.5 hover:bg-slate-100 rounded-lg"
                        title="新建对话"
                      >
                        <RotateCcw className="w-4 h-4 text-slate-500" />
                      </button>
                    )}
                    <button
                      onClick={() => setShowChat(false)}
                      className="p-1.5 hover:bg-slate-100 rounded-lg"
                    >
                      <X className="w-4 h-4 text-slate-500" />
                    </button>
                  </div>
                </div>
                <p className="text-xs text-slate-500 mt-1">
                  基于知识库语义检索 · 支持上下文连续对话
                </p>
              </div>

              <div className="flex-1 overflow-y-auto p-4 space-y-4">
                {chatMessages.length === 0 && (
                  <div className="text-center py-8">
                    <Brain className="w-12 h-12 mx-auto text-violet-300 mb-3" />
                    <p className="text-sm text-slate-500">向AI助手提问，获取基于知识库的专业解答</p>
                    <p className="text-xs text-slate-400 mt-1">对话自动保留上下文，支持连续追问</p>
                    <div className="mt-4 space-y-2">
                      {['帮我分析一下这款产品的成本构成', '我们有哪些供应商的报价信息？', '这款面料的织造工艺参数是什么？'].map((q, i) => (
                        <button
                          key={i}
                          onClick={() => setChatInput(q)}
                          className="block w-full text-left text-xs px-3 py-2.5 bg-slate-50 rounded-lg hover:bg-violet-50 hover:text-violet-700 transition-colors"
                        >
                          {q}
                        </button>
                      ))}
                    </div>
                  </div>
                )}

                {chatMessages.map((msg, i) => (
                  <div key={i} className={msg.role === 'user' ? 'text-right' : 'text-left'}>
                    {msg.role === 'user' ? (
                      <div className="inline-block max-w-[85%] px-4 py-2.5 rounded-2xl text-sm bg-gradient-to-r from-violet-500 to-purple-600 text-white">
                        <div className="whitespace-pre-wrap">{msg.content}</div>
                      </div>
                    ) : (
                      <div className="inline-block max-w-[90%]">
                        <div className="px-4 py-2.5 rounded-2xl text-sm bg-slate-100 text-slate-800">
                          {msg.isLoading && !msg.content ? (
                            <div className="flex items-center gap-1.5 text-slate-400">
                              <Loader2 className="w-4 h-4 animate-spin" />
                              <span>思考中...</span>
                            </div>
                          ) : (
                            <div className="whitespace-pre-wrap leading-relaxed">{msg.content}</div>
                          )}
                        </div>
                        {msg.sources && msg.sources.length > 0 && (
                          <div className="mt-2 p-2 bg-violet-50 rounded-xl">
                            <span className="text-xs text-violet-600 font-medium">参考来源:</span>
                            <div className="mt-1 space-y-1">
                              {msg.sources.map((s, j) => (
                                <div key={j} className="flex items-center gap-1.5 text-xs">
                                  <CheckCircle className="w-3 h-3 text-violet-500" />
                                  <span className="text-violet-700">{s.title}</span>
                                  <span className="text-slate-400">({s.domain}, {(s.score * 100).toFixed(0)}%)</span>
                                </div>
                              ))}
                            </div>
                          </div>
                        )}
                      </div>
                    )}
                  </div>
                ))}
                <div ref={chatEndRef} />
              </div>

              <div className="p-4 border-t border-slate-200/60">
                <div className="flex items-center gap-2">
                  <input
                    type="text"
                    placeholder="输入问题，支持上下文连续对话..."
                    value={chatInput}
                    onChange={(e) => setChatInput(e.target.value)}
                    onKeyDown={(e) => e.key === 'Enter' && !e.shiftKey && handleSendChat()}
                    className="flex-1 px-4 py-2.5 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-violet-300"
                    disabled={isChatting}
                  />
                  <button
                    onClick={handleSendChat}
                    disabled={isChatting || !chatInput.trim()}
                    className="p-2.5 bg-gradient-to-r from-violet-500 to-purple-600 text-white rounded-xl hover:shadow-lg transition-all disabled:opacity-50"
                  >
                    {isChatting ? <Loader2 className="w-4 h-4 animate-spin" /> : <Send className="w-4 h-4" />}
                  </button>
                </div>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* 新建卡片弹窗 */}
      {showCreateModal && (
        <div className="fixed inset-0 bg-black/30 flex items-center justify-center z-50">
          <div className="bg-white rounded-2xl shadow-2xl w-[600px] max-h-[80vh] overflow-y-auto">
            <div className="p-6 border-b border-slate-200">
              <div className="flex items-center justify-between">
                <h3 className="text-lg font-semibold text-slate-800">新建知识卡片</h3>
                <button onClick={() => setShowCreateModal(false)} className="p-1 hover:bg-slate-100 rounded-lg">
                  <X className="w-5 h-5 text-slate-500" />
                </button>
              </div>
            </div>
            <div className="p-6 space-y-4">
              <div>
                <label className="text-sm font-medium text-slate-700">知识域 *</label>
                <select
                  value={newCard.domainCode}
                  onChange={(e) => setNewCard(prev => ({ ...prev, domainCode: e.target.value }))}
                  className="mt-1 w-full px-3 py-2 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-violet-300"
                >
                  {domains.map(d => (
                    <option key={d.code} value={d.code}>{d.name}</option>
                  ))}
                </select>
              </div>
              <div>
                <label className="text-sm font-medium text-slate-700">标题 *</label>
                <input
                  type="text"
                  value={newCard.title}
                  onChange={(e) => setNewCard(prev => ({ ...prev, title: e.target.value }))}
                  placeholder="一句话概括这张卡片的核心判断"
                  className="mt-1 w-full px-3 py-2 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-violet-300"
                />
              </div>
              <div>
                <label className="text-sm font-medium text-slate-700">内容 *</label>
                <textarea
                  value={newCard.content}
                  onChange={(e) => setNewCard(prev => ({ ...prev, content: e.target.value }))}
                  placeholder="详细描述经验、判断、数据依据..."
                  rows={6}
                  className="mt-1 w-full px-3 py-2 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-violet-300 resize-none"
                />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="text-sm font-medium text-slate-700">标签</label>
                  <input
                    type="text"
                    value={newCard.tags}
                    onChange={(e) => setNewCard(prev => ({ ...prev, tags: e.target.value }))}
                    placeholder="用逗号分隔，如：锦纶,40D,丝袜"
                    className="mt-1 w-full px-3 py-2 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-violet-300"
                  />
                </div>
                <div>
                  <label className="text-sm font-medium text-slate-700">关联产品编码</label>
                  <input
                    type="text"
                    value={newCard.productCode}
                    onChange={(e) => setNewCard(prev => ({ ...prev, productCode: e.target.value }))}
                    placeholder="如：HT01"
                    className="mt-1 w-full px-3 py-2 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-violet-300"
                  />
                </div>
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="text-sm font-medium text-slate-700">来源</label>
                  <input
                    type="text"
                    value={newCard.source}
                    onChange={(e) => setNewCard(prev => ({ ...prev, source: e.target.value }))}
                    placeholder="如：设计师A/行业报告"
                    className="mt-1 w-full px-3 py-2 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-violet-300"
                  />
                </div>
                <div>
                  <label className="text-sm font-medium text-slate-700">置信度</label>
                  <select
                    value={newCard.confidence}
                    onChange={(e) => setNewCard(prev => ({ ...prev, confidence: e.target.value }))}
                    className="mt-1 w-full px-3 py-2 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-violet-300"
                  >
                    <option value="high">高 - 已验证事实</option>
                    <option value="medium">中 - 经验判断</option>
                    <option value="low">低 - 待验证</option>
                  </select>
                </div>
              </div>
            </div>
            <div className="p-6 border-t border-slate-200 flex justify-end gap-3">
              <button
                onClick={() => setShowCreateModal(false)}
                className="px-4 py-2 rounded-xl border border-slate-200 text-sm text-slate-600 hover:bg-slate-50"
              >
                取消
              </button>
              <button
                onClick={handleCreateCard}
                disabled={isCreating || !newCard.title || !newCard.content}
                className="px-6 py-2 bg-gradient-to-r from-violet-500 to-purple-600 text-white rounded-xl text-sm font-medium hover:shadow-lg disabled:opacity-50"
              >
                {isCreating ? '创建中...' : '创建卡片'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 查看卡片弹窗 */}
      {viewingCard && (
        <div className="fixed inset-0 bg-black/30 flex items-center justify-center z-50">
          <div className="bg-white rounded-2xl shadow-2xl w-[600px] max-h-[80vh] overflow-y-auto">
            <div className="p-6 border-b border-slate-200">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <div className={cn("w-8 h-8 rounded-lg flex items-center justify-center bg-gradient-to-br text-white", DOMAIN_COLORS[viewingCard.domainCode || viewingCard.domain_code] || 'from-slate-400 to-slate-500')}>
                    {DOMAIN_ICONS[viewingCard.domainCode || viewingCard.domain_code] || <BookOpen className="w-4 h-4" />}
                  </div>
                  <div>
                    <h3 className="font-semibold text-slate-800">{viewingCard.title}</h3>
                    <span className="text-xs text-slate-400">{viewingCard.domain_name || domains.find(d => d.code === (viewingCard.domainCode || viewingCard.domain_code))?.name}</span>
                  </div>
                </div>
                <button onClick={() => setViewingCard(null)} className="p-1 hover:bg-slate-100 rounded-lg">
                  <X className="w-5 h-5 text-slate-500" />
                </button>
              </div>
            </div>
            <div className="p-6 space-y-4">
              <div className="text-sm text-slate-700 whitespace-pre-wrap leading-relaxed">{viewingCard.content}</div>
              <div className="flex flex-wrap gap-1.5">
                {(viewingCard.tags || []).map((tag, i) => (
                  <span key={i} className="text-xs px-2 py-1 bg-violet-50 text-violet-600 rounded-lg">{tag}</span>
                ))}
              </div>
              <div className="grid grid-cols-2 gap-3 text-xs text-slate-500">
                {(viewingCard.product_code) && (
                  <div className="flex items-center gap-1">
                    <Package className="w-3 h-3" />
                    <span>产品: {viewingCard.product_code}</span>
                  </div>
                )}
                {viewingCard.source && (
                  <div className="flex items-center gap-1">
                    <Eye className="w-3 h-3" />
                    <span>来源: {viewingCard.source}</span>
                  </div>
                )}
                <div className="flex items-center gap-1">
                  <Clock className="w-3 h-3" />
                  <span>创建: {new Date(viewingCard.created_at).toLocaleString()}</span>
                </div>
                {confidenceLabel[viewingCard.confidence] && (
                  <span className={cn("px-1.5 py-0.5 rounded-md font-medium", confidenceLabel[viewingCard.confidence].color)}>
                    置信度: {confidenceLabel[viewingCard.confidence].text}
                  </span>
                )}
              </div>
            </div>
            <div className="p-6 border-t border-slate-200 flex justify-between">
              <button
                onClick={() => { handleDeleteCard(viewingCard.id); setViewingCard(null); }}
                className="flex items-center gap-1.5 px-4 py-2 text-red-600 hover:bg-red-50 rounded-xl text-sm"
              >
                <Trash2 className="w-4 h-4" />
                删除卡片
              </button>
              <button
                onClick={() => setViewingCard(null)}
                className="px-4 py-2 rounded-xl border border-slate-200 text-sm text-slate-600 hover:bg-slate-50"
              >
                关闭
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
