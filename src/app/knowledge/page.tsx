'use client';

import { useState, useEffect, useRef, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import { backendFetch } from '@/lib/backend-proxy';
import {
  BookOpen,
  Upload,
  Search,
  Plus,
  FileText,
  Trash2,
  FolderOpen,
  Loader2,
  ChevronDown,
  ChevronUp,
  X,
  File,
  FileSpreadsheet,
  FileType,
  FolderPlus,
  CheckCircle,
  AlertCircle,
  Clock,
  SkipForward,
  MoreVertical,
  Download,
  Eye,
  Tag,
  ArrowRight,
  ArrowLeft,
  RefreshCw,
  Database,
  Briefcase,
  Info,
} from 'lucide-react';
import KnowledgeCardForm from '@/components/KnowledgeCardForm';
import KnowledgeCardList, { KnowledgeCardListHandle } from '@/components/KnowledgeCardList';

// 文件类型图标
const FILE_ICONS: Record<string, React.ReactNode> = {
  pdf: <FileText className="w-8 h-8 text-red-500" />,
  doc: <File className="w-8 h-8 text-blue-500" />,
  docx: <File className="w-8 h-8 text-blue-500" />,
  xls: <FileSpreadsheet className="w-8 h-8 text-green-500" />,
  xlsx: <FileSpreadsheet className="w-8 h-8 text-green-500" />,
  csv: <FileSpreadsheet className="w-8 h-8 text-green-500" />,
  txt: <FileType className="w-8 h-8 text-slate-500" />,
  md: <FileType className="w-8 h-8 text-amber-500" />,
  url: <Eye className="w-8 h-8 text-blue-500" />,
  text: <FileText className="w-8 h-8 text-indigo-500" />,
};

// 向量化状态配置
const EMBEDDING_STATUS: Record<string, { label: string; color: string; icon: React.ReactNode }> = {
  COMPLETED: { label: '已向量化', color: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/30', icon: <CheckCircle className="w-3 h-3" /> },
  PROCESSING: { label: '处理中', color: 'bg-amber-500/10 text-amber-400 border-amber-500/30', icon: <Loader2 className="w-3 h-3 animate-spin" /> },
  FAILED: { label: '向量化失败', color: 'bg-red-500/10 text-red-400 border-red-500/30', icon: <AlertCircle className="w-3 h-3" /> },
  SKIPPED: { label: '无需向量化', color: 'bg-slate-800/500/10 text-slate-400 border-slate-500/30', icon: <SkipForward className="w-3 h-3" /> },
  PENDING: { label: '待向量化', color: 'bg-blue-500/10 text-blue-400 border-blue-500/30', icon: <Clock className="w-3 h-3" /> },
};

interface DocEntry {
  id: string;
  title: string;
  type: string;
  fileName?: string;
  fileSize?: number;
  category?: string;
  categoryName?: string;
  content?: string;
  embeddingStatus?: string;
  chunkCount?: number;
  fileContent?: string;
  createdAt: string;
}

interface Category {
  id: string;
  name: string;
  description?: string;
  docCount?: number;
}

// 统一 API 调用
function getSessionId(): string | null {
  if (typeof window === 'undefined') return null;
  return localStorage.getItem('session_id');
}

const knowledgeApi = {
  get: (path: string) => {
    const sid = getSessionId();
    return fetch(`/api/knowledge${path}`, {
      credentials: 'include',
      headers: sid ? { 'X-Session-Id': sid } : undefined,
    });
  },
  post: (path: string, body?: unknown) => {
    const sid = getSessionId();
    const headers: Record<string, string> = {};
    if (sid) headers['X-Session-Id'] = sid;
    if (body && !(body instanceof FormData)) headers['Content-Type'] = 'application/json';
    return fetch(`/api/knowledge${path}`, {
      method: 'POST',
      headers: Object.keys(headers).length > 0 ? headers : undefined,
      body: body instanceof FormData ? body : body ? JSON.stringify(body) : undefined,
      credentials: 'include',
    });
  },
  del: (path: string) => {
    const sid = getSessionId();
    return fetch(`/api/knowledge${path}`, {
      method: 'DELETE',
      credentials: 'include',
      headers: sid ? { 'X-Session-Id': sid } : undefined,
    });
  },
  // 批量导入 API
  import: {
    submitPath: (path: string) => {
      const sid = getSessionId();
      return fetch('/api/knowledge/import/path', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(sid ? { 'X-Session-Id': sid } : {}),
        },
        body: JSON.stringify({ path }),
        credentials: 'include',
      });
    },
    submitUpload: (file: File) => {
      const sid = getSessionId();
      const formData = new FormData();
      formData.append('file', file);
      return fetch('/api/knowledge/import/upload', {
        method: 'POST',
        headers: sid ? { 'X-Session-Id': sid } : undefined,
        body: formData,
        credentials: 'include',
      });
    },
    getProgress: (taskId: number) => {
      const sid = getSessionId();
      return fetch(`/api/knowledge/import/progress/${taskId}`, {
        credentials: 'include',
        headers: sid ? { 'X-Session-Id': sid } : undefined,
      });
    },
    cancel: (taskId: number) => {
      const sid = getSessionId();
      return fetch(`/api/knowledge/import/cancel/${taskId}`, {
        method: 'POST',
        credentials: 'include',
        headers: sid ? { 'X-Session-Id': sid } : undefined,
      });
    },
    getTasks: (limit = 20) => {
      const sid = getSessionId();
      return fetch(`/api/knowledge/import/tasks?limit=${limit}`, {
        credentials: 'include',
        headers: sid ? { 'X-Session-Id': sid } : undefined,
      });
    },
  },
};

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B';
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
}

function getFileExtension(fileName: string): string {
  const parts = fileName.split('.');
  return parts.length > 1 ? parts[parts.length - 1].toLowerCase() : 'text';
}

export default function KnowledgePage() {
  const router = useRouter();
  // 文档列表
  const [documents, setDocuments] = useState<DocEntry[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [activeCategory, setActiveCategory] = useState<string>('');
  const [loading, setLoading] = useState(true);

  // 上传
  const [showUploadPanel, setShowUploadPanel] = useState(false);
  const [uploadFiles, setUploadFiles] = useState<FileList | null>(null);
  const [isUploading, setIsUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [dragOver, setDragOver] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const searchTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // 批量导入
  const [showImportPanel, setShowImportPanel] = useState(false);
  const [importMode, setImportMode] = useState<'upload' | 'path'>('upload');
  const [importZipFile, setImportZipFile] = useState<File | null>(null);
  const [importPath, setImportPath] = useState('');
  const [importDragOver, setImportDragOver] = useState(false);
  const [isImporting, setIsImporting] = useState(false);
  const [currentImportTaskId, setCurrentImportTaskId] = useState<number | null>(null);
  const [importProgress, setImportProgress] = useState<any>(null);
  const [importTasks, setImportTasks] = useState<any[]>([]);
  const [showTaskHistory, setShowTaskHistory] = useState(false);
  // 导入弹窗内嵌提示条（替代原生 alert，符合项目深色科技风格）
  const [importNotice, setImportNotice] = useState<{ type: 'error' | 'info' | 'success'; text: string } | null>(null);
  const importFileInputRef = useRef<HTMLInputElement>(null);
  const importProgressTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  // 新建分类
  const [showNewCategory, setShowNewCategory] = useState(false);
  const [newCategoryName, setNewCategoryName] = useState('');
  const [isCreatingCategory, setIsCreatingCategory] = useState(false);

  // 搜索
  const [searchQuery, setSearchQuery] = useState('');
  const [activeTab, setActiveTab] = useState<'docs' | 'cards'>('docs');
  const [showCardForm, setShowCardForm] = useState(false);
  const [editingCard, setEditingCard] = useState<any>(null);
  const cardListRef = useRef<KnowledgeCardListHandle>(null);

  // 新建文本文档
  const [showAddText, setShowAddText] = useState(false);
  const [addTitle, setAddTitle] = useState('');
  const [addContent, setAddContent] = useState('');
  const [isAddingText, setIsAddingText] = useState(false);

  // 文档详情
  const [viewingDoc, setViewingDoc] = useState<DocEntry | null>(null);

  // 视图模式
  const [viewMode, setViewMode] = useState<'grid' | 'list'>('grid');

  // 登录检查 - 后端不可用时进入降级模式，不强制跳转
  useEffect(() => {
    const sessionId = localStorage.getItem('session_id');
    if (!sessionId) {
      // 先检测后端是否可用，不可用则留在页面（降级模式）
      backendFetch('/albums?pageSize=1').then(res => {
        if (res.status === 502) {
          console.log('[Knowledge] 后端不可用，进入降级模式');
        } else {
          window.location.href = '/login';
        }
      }).catch(() => {
        console.log('[Knowledge] 后端不可用，进入降级模式');
      });
    }
  }, []);

  // 浏览器后退防护 - 不自动跳转，让正常流程处理
  useEffect(() => {
    const handlePageShow = (e: PageTransitionEvent) => {
      if (e.persisted) {
        console.log('[Knowledge] 从 bfcache 恢复');
      }
    };
    window.addEventListener('pageshow', handlePageShow);
    return () => window.removeEventListener('pageshow', handlePageShow);
  }, []);

  // 加载分类
  const fetchCategories = useCallback(async () => {
    try {
      const res = await knowledgeApi.get('/categories');
      const data = await res.json();
      if (data.success) {
        setCategories(data.categories || []);
      }
    } catch (err) {
      console.error('加载分类失败:', err);
    }
  }, []);

  // 加载文档
  const fetchDocuments = useCallback(async () => {
    setLoading(true);
    try {
      const params = new URLSearchParams();
      if (activeCategory) params.set('categoryId', activeCategory);
      if (searchQuery) params.set('keyword', searchQuery);
      params.set('page', '0');
      params.set('size', '100');

      const res = await knowledgeApi.get(`/docs?${params}`);
      const data = await res.json();
      if (data.success) {
        const docs = (data.documents || data.docs || []).map((doc: Record<string, unknown>) => ({
          id: String(doc.id),
          title: String(doc.title || doc.fileName || '未命名文档'),
          type: String(doc.type || getFileExtension(String(doc.fileName || doc.title || 'txt'))),
          fileName: String(doc.fileName || ''),
          fileSize: Number(doc.fileSize || 0),
          category: String(doc.categoryId || doc.category || ''),
          categoryName: String(doc.categoryName || ''),
          content: String(doc.fileContent || doc.content || ''),
          embeddingStatus: String(doc.embeddingStatus || 'PENDING'),
          chunkCount: Number(doc.chunkCount || 0),
          createdAt: String(doc.createdAt || new Date().toISOString()),
        }));
        setDocuments(docs);
      }
    } catch (err) {
      console.error('加载文档失败:', err);
    } finally {
      setLoading(false);
    }
  }, [activeCategory, searchQuery]);

  useEffect(() => {
    fetchCategories();
  }, [fetchCategories]);

  useEffect(() => {
    fetchDocuments();
  }, [fetchDocuments]);

  // 自动刷新向量化状态
  useEffect(() => {
    const hasProcessing = documents.some((d) => d.embeddingStatus === 'PROCESSING' || d.embeddingStatus === 'PENDING');
    if (!hasProcessing) return;
    const timer = setInterval(() => fetchDocuments(), 8000);
    return () => clearInterval(timer);
  }, [documents, fetchDocuments]);

  // 上传文件
  const handleFileUpload = async () => {
    if (!uploadFiles || uploadFiles.length === 0) return;
    setIsUploading(true);
    setUploadProgress(0);

    try {
      for (let i = 0; i < uploadFiles.length; i++) {
        const file = uploadFiles[i];
        const formData = new FormData();
        formData.append('file', file);
        if (activeCategory) formData.append('categoryId', activeCategory);

        const res = await knowledgeApi.post('/upload', formData);
        const data = await res.json();
        if (!data.success) {
          alert(`上传 ${file.name} 失败: ${data.error || data.message || '未知错误'}`);
        }
        setUploadProgress(Math.round(((i + 1) / uploadFiles.length) * 100));
      }
      setUploadFiles(null);
      setShowUploadPanel(false);
      if (fileInputRef.current) fileInputRef.current.value = '';
      await fetchDocuments();
    } catch (err) {
      console.error('上传失败:', err);
      alert('上传失败，请重试');
    } finally {
      setIsUploading(false);
      setUploadProgress(0);
    }
  };

  // 批量导入 - 提交
  const handleImportSubmit = async () => {
    setIsImporting(true);
    try {
      let res: Response;
      if (importMode === 'upload') {
        if (!importZipFile) {
          setImportNotice({ type: 'error', text: '请选择 zip 压缩包文件' });
          setIsImporting(false);
          return;
        }
        res = await knowledgeApi.import.submitUpload(importZipFile);
      } else {
        if (!importPath.trim()) {
          setImportNotice({ type: 'error', text: '请输入服务器上的 zip 或文件夹路径' });
          setIsImporting(false);
          return;
        }
        res = await knowledgeApi.import.submitPath(importPath.trim());
      }

      const data = await res.json();
      if (!data.success) {
        setImportNotice({ type: 'error', text: `提交导入失败: ${data.error || data.message || '未知错误'}` });
        setIsImporting(false);
        return;
      }

      const taskId = data.taskId;
      setCurrentImportTaskId(taskId);
      setImportProgress({ status: 'RUNNING', processedFiles: 0, totalFiles: 0, totalChunks: 0 });
      setImportNotice({ type: 'info', text: '导入任务已提交，后台流式处理中' });

      // 开始轮询进度
      startProgressPolling(taskId);
    } catch (err) {
      console.error('提交导入失败:', err);
      setImportNotice({ type: 'error', text: '提交导入失败，请检查后端服务是否可用' });
      setIsImporting(false);
    }
  };

  // 轮询进度
  const startProgressPolling = (taskId: number) => {
    if (importProgressTimerRef.current) {
      clearInterval(importProgressTimerRef.current);
    }

    const poll = async () => {
      try {
        const res = await knowledgeApi.import.getProgress(taskId);
        const data = await res.json();
        if (data.success) {
          setImportProgress(data.progress);
          if (data.progress.status === 'COMPLETED' || data.progress.status === 'FAILED' || data.progress.status === 'CANCELLED') {
            // 任务结束，停止轮询
            if (importProgressTimerRef.current) {
              clearInterval(importProgressTimerRef.current);
              importProgressTimerRef.current = null;
            }
            setIsImporting(false);
            if (data.progress.status === 'COMPLETED') {
              setImportNotice({ type: 'success', text: `导入完成：已处理 ${data.progress.processedFiles ?? 0} 个文件，向量化 ${data.progress.totalChunks ?? 0} 个切片` });
              await fetchDocuments();
            } else if (data.progress.status === 'FAILED') {
              setImportNotice({ type: 'error', text: `导入失败: ${data.progress.errorMsg || '部分文件处理失败，详见任务历史'}` });
            } else {
              setImportNotice({ type: 'info', text: '导入已取消' });
            }
          }
        }
      } catch (err) {
        console.error('查询进度失败:', err);
      }
    };

    poll(); // 立即查询一次
    importProgressTimerRef.current = setInterval(poll, 2000); // 每 2 秒查询
  };

  // 取消导入
  const handleCancelImport = async () => {
    if (!currentImportTaskId) return;
    try {
      const res = await knowledgeApi.import.cancel(currentImportTaskId);
      const data = await res.json();
      if (data.success) {
        if (importProgressTimerRef.current) {
          clearInterval(importProgressTimerRef.current);
          importProgressTimerRef.current = null;
        }
        setIsImporting(false);
        setCurrentImportTaskId(null);
        setImportProgress(null);
        setImportNotice({ type: 'info', text: '已发送取消指令，任务将停止' });
      } else {
        setImportNotice({ type: 'error', text: `取消失败: ${data.error || '未知错误'}` });
      }
    } catch (err) {
      console.error('取消导入失败:', err);
    }
  };

  // 加载任务历史
  const loadTaskHistory = async () => {
    try {
      const res = await knowledgeApi.import.getTasks(20);
      const data = await res.json();
      if (data.success) {
        setImportTasks(data.tasks || []);
        setShowTaskHistory(true);
      }
    } catch (err) {
      console.error('加载任务历史失败:', err);
    }
  };

  // 关闭导入面板
  const closeImportPanel = () => {
    if (isImporting) return;
    setShowImportPanel(false);
    setImportZipFile(null);
    setImportPath('');
    setImportProgress(null);
    setCurrentImportTaskId(null);
    setImportNotice(null);
    if (importProgressTimerRef.current) {
      clearInterval(importProgressTimerRef.current);
      importProgressTimerRef.current = null;
    }
    if (importFileInputRef.current) importFileInputRef.current.value = '';
  };

  // 新建文本文档
  const handleAddText = async () => {
    if (!addTitle.trim() || !addContent.trim()) return;
    setIsAddingText(true);
    try {
      const res = await knowledgeApi.post('/docs', {
        title: addTitle,
        content: addContent,
        categoryId: activeCategory || undefined,
      });
      const data = await res.json();
      if (data.success) {
        setAddTitle('');
        setAddContent('');
        setShowAddText(false);
        await fetchDocuments();
      } else {
        alert(data.error || '创建失败');
      }
    } catch (err) {
      console.error('创建文档失败:', err);
      alert('创建失败');
    } finally {
      setIsAddingText(false);
    }
  };

  // 新建分类
  const handleCreateCategory = async () => {
    if (!newCategoryName.trim()) return;
    setIsCreatingCategory(true);
    try {
      const res = await knowledgeApi.post('/categories', { name: newCategoryName });
      const data = await res.json();
      if (data.success) {
        setNewCategoryName('');
        setShowNewCategory(false);
        await fetchCategories();
      } else {
        alert(data.error || '创建分类失败');
      }
    } catch (err) {
      console.error('创建分类失败:', err);
      alert('创建分类失败');
    } finally {
      setIsCreatingCategory(false);
    }
  };

  // 删除文档
  const handleDeleteDoc = async (id: string) => {
    if (!confirm('确定删除此文档？相关向量数据也会被清除。')) return;
    try {
      const res = await knowledgeApi.del(`/docs/${id}`);
      const data = await res.json();
      if (data.success) {
        await fetchDocuments();
        if (viewingDoc?.id === id) setViewingDoc(null);
      } else {
        alert(data.error || '删除失败');
      }
    } catch (err) {
      console.error('删除失败:', err);
    }
  };

  // 下载文档
  const handleDownloadDoc = async (doc: DocEntry) => {
    try {
      const res = await knowledgeApi.get(`/docs/${doc.id}/download`);
      const data = await res.json();
      if (data.url) {
        const response = await fetch(data.url);
        const blob = await response.blob();
        const blobUrl = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = blobUrl;
        link.download = doc.fileName || doc.title;
        link.click();
        window.URL.revokeObjectURL(blobUrl);
      } else if (data.success === false) {
        alert(data.error || '下载失败');
      }
    } catch (err) {
      console.error('下载失败:', err);
      alert('下载失败');
    }
  };

  // 重新向量化失败文档
  const handleRetryEmbedding = async (id: string) => {
    try {
      const res = await knowledgeApi.post(`/docs/${id}/reembed`);
      const data = await res.json();
      if (data.success) {
        await fetchDocuments();
      } else {
        alert(data.error || '重新处理失败');
      }
    } catch (err) {
      console.error('重新处理失败:', err);
      alert('重新处理失败');
    }
  };

  // 搜索
  const handleSearch = () => {
    // fetchDocuments already includes searchQuery in its dependency,
    // so just triggering a refetch is enough
    fetchDocuments();
  };

  // 退出登录
  const handleLogout = () => {
    localStorage.removeItem('session_id');
    localStorage.removeItem('portal_type');
    window.location.href = '/login';
  };

  const displayDocs = documents;

  // 统计
  const totalDocs = documents.length;
  const vectorizedDocs = documents.filter((d) => d.embeddingStatus === 'COMPLETED').length;
  const processingDocs = documents.filter((d) => d.embeddingStatus === 'PROCESSING' || d.embeddingStatus === 'PENDING').length;

  return (
    <div className="h-screen flex flex-col bg-slate-950">
      {/* Header */}
      <header className="h-12 flex items-center justify-between px-5 bg-slate-900/80 backdrop-blur-xl border-b border-slate-700/50 shrink-0">
        <div className="flex items-center gap-2.5">
          <button
            onClick={() => router.push('/')}
            className="flex items-center gap-1 text-sm text-slate-400 hover:text-blue-400 transition-colors shrink-0"
          >
            <ArrowLeft className="w-4 h-4" />
            <span>返回</span>
          </button>
          <span className="text-slate-500">|</span>
          <div className="w-7 h-7 bg-gradient-to-br from-blue-500 to-cyan-500 rounded-lg flex items-center justify-center shadow-[0_0_10px_rgba(59,130,246,0.3)]">
            <BookOpen className="w-3.5 h-3.5 text-white" />
          </div>
          <h1 className="text-sm font-semibold text-slate-200">知识库</h1>
          {/* Tab Switch */}
          <div className="flex bg-slate-800/80 rounded-lg p-0.5 border border-slate-700/50">
            <button
              onClick={() => setActiveTab('docs')}
              className={`text-[11px] px-2.5 py-1 rounded-md font-medium transition-all ${
                activeTab === 'docs'
                  ? 'bg-blue-500/20 text-blue-400 shadow-sm'
                  : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              <span className="flex items-center gap-1"><FileText className="w-3 h-3" />文档</span>
            </button>
            <button
              onClick={() => setActiveTab('cards')}
              className={`text-[11px] px-2.5 py-1 rounded-md font-medium transition-all ${
                activeTab === 'cards'
                  ? 'bg-blue-500/20 text-blue-400 shadow-sm'
                  : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              <span className="flex items-center gap-1"><Briefcase className="w-3 h-3" />岗位卡片</span>
            </button>
          </div>
        </div>

        <div className="flex items-center gap-3">
          {/* 搜索框 */}
          <div className="flex items-center gap-2">
            <div className="relative">
              <Search className="w-4 h-4 text-slate-500 absolute left-3 top-1/2 -translate-y-1/2" />
              <input
                value={searchQuery}
                onChange={(e) => {
                  setSearchQuery(e.target.value);
                  if (searchTimerRef.current) clearTimeout(searchTimerRef.current);
                  searchTimerRef.current = setTimeout(() => fetchDocuments(), 400);
                }}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') {
                    if (searchTimerRef.current) clearTimeout(searchTimerRef.current);
                    fetchDocuments();
                  }
                }}
                placeholder="搜索文档..."
                className="pl-9 pr-3 py-1.5 text-sm border border-slate-700 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500/30 bg-slate-800 text-slate-200 placeholder:text-slate-500 w-56"
              />
            </div>
            <button
              onClick={handleSearch}
              className="px-3 py-1.5 bg-blue-500 text-white rounded-lg hover:bg-blue-600 transition-colors text-sm"
            >
              搜索
            </button>
            {searchQuery && (
              <button
                onClick={() => {
                  setSearchQuery('');
                  fetchDocuments();
                }}
                className="text-xs text-slate-400 hover:text-slate-200"
              >
                清除搜索
              </button>
            )}
          </div>

          {/* 视图切换 */}
          <div className="flex items-center border border-slate-700 rounded-lg overflow-hidden">
            <button
              onClick={() => setViewMode('grid')}
              className={`p-1.5 text-xs ${viewMode === 'grid' ? 'bg-blue-500/20 text-blue-400' : 'text-slate-500 hover:text-slate-300'}`}
            >
              网格
            </button>
            <button
              onClick={() => setViewMode('list')}
              className={`p-1.5 text-xs ${viewMode === 'list' ? 'bg-blue-500/20 text-blue-400' : 'text-slate-500 hover:text-slate-300'}`}
            >
              列表
            </button>
          </div>

          <button
            onClick={handleLogout}
            className="text-sm text-slate-400 hover:text-slate-200 transition-colors"
          >
            退出登录
          </button>
        </div>
      </header>

      {/* Main Content */}
      {activeTab === 'cards' ? (
        /* Knowledge Cards Tab */
        <div className="flex-1 flex flex-col min-h-0">
          {showCardForm ? (
            <KnowledgeCardForm
              editCard={editingCard}
              onSaved={async () => {
                setShowCardForm(false);
                setEditingCard(null);
                if (cardListRef.current) cardListRef.current.reload();
              }}
              onClose={() => { setShowCardForm(false); setEditingCard(null); }}
            />
          ) : (
            <KnowledgeCardList
              ref={cardListRef}
              onCreateNew={() => { setEditingCard(null); setShowCardForm(true); }}
              onEdit={(card: any) => { setEditingCard(card); setShowCardForm(true); }}
            />
          )}
        </div>
      ) : (
      <>
      <div className="flex flex-1 min-h-0">
        {/* Left Sidebar - Categories */}
        <div className="w-56 border-r border-slate-700/50 bg-slate-900/50 backdrop-blur-xl flex flex-col shrink-0">
          {/* Category Header */}
          <div className="p-3 border-b border-slate-700/40">
            <div className="flex items-center justify-between">
              <h2 className="text-sm font-semibold text-slate-200 flex items-center gap-1.5">
                <FolderOpen className="w-4 h-4 text-blue-400" />
                文档分类
              </h2>
              <button
                onClick={() => setShowNewCategory(!showNewCategory)}
                className="p-1 hover:bg-blue-500/10 rounded transition-colors"
                title="新建分类"
              >
                <FolderPlus className="w-3.5 h-3.5 text-blue-400" />
              </button>
            </div>

            {/* New Category Input */}
            {showNewCategory && (
              <div className="mt-2 flex gap-1.5">
                <input
                  value={newCategoryName}
                  onChange={(e) => setNewCategoryName(e.target.value)}
                  onKeyDown={(e) => e.key === 'Enter' && handleCreateCategory()}
                  placeholder="分类名称"
                  className="flex-1 px-2 py-1 text-xs border border-slate-700 rounded focus:outline-none focus:ring-1 focus:ring-blue-500/30 bg-slate-800 text-slate-200 placeholder:text-slate-500"
                />
                <button
                  onClick={handleCreateCategory}
                  disabled={isCreatingCategory || !newCategoryName.trim()}
                  className="px-2 py-1 text-xs bg-blue-500 text-white rounded hover:bg-blue-600 disabled:opacity-50"
                >
                  {isCreatingCategory ? <Loader2 className="w-3 h-3 animate-spin" /> : '添加'}
                </button>
              </div>
            )}
          </div>

          {/* Category List */}
          <div className="flex-1 overflow-y-auto p-2 space-y-0.5">
            <button
              onClick={() => setActiveCategory('')}
              className={`w-full flex items-center gap-2 px-3 py-2 rounded-lg text-sm transition-colors ${
                !activeCategory
                  ? 'bg-gradient-to-r from-blue-500/15 to-cyan-500/15 text-blue-400 font-medium'
                  : 'text-slate-400 hover:bg-slate-800/60'
              }`}
            >
              <BookOpen className="w-4 h-4" />
              <span>全部文档</span>
              <span className="ml-auto text-xs text-slate-500">{totalDocs}</span>
            </button>

            {categories.map((cat) => (
              <button
                key={cat.id}
                onClick={() => setActiveCategory(cat.id)}
                className={`w-full flex items-center gap-2 px-3 py-2 rounded-lg text-sm transition-colors ${
                  activeCategory === cat.id
                    ? 'bg-gradient-to-r from-blue-500/15 to-cyan-500/15 text-blue-400 font-medium'
                    : 'text-slate-400 hover:bg-slate-800/60'
                }`}
              >
                <FolderOpen className="w-4 h-4" />
                <span className="truncate">{cat.name}</span>
                {cat.docCount !== undefined && (
                  <span className="ml-auto text-xs text-slate-500">{cat.docCount}</span>
                )}
              </button>
            ))}

            {categories.length === 0 && !showNewCategory && (
              <div className="text-center py-6 px-2">
                <FolderOpen className="w-8 h-8 text-slate-500 mx-auto mb-2" />
                <p className="text-xs text-slate-500">暂无分类</p>
                <button
                  onClick={() => setShowNewCategory(true)}
                  className="text-xs text-blue-400 hover:text-blue-300 mt-1"
                >
                  创建分类
                </button>
              </div>
            )}
          </div>

          {/* Stats */}
          <div className="p-3 border-t border-slate-700/40 space-y-2">
            <div className="flex items-center gap-2 text-xs text-slate-400">
              <Database className="w-3.5 h-3.5 text-blue-400" />
              <span>已向量化 {vectorizedDocs}/{totalDocs}</span>
            </div>
            {processingDocs > 0 && (
              <div className="flex items-center gap-2 text-xs text-amber-400">
                <Loader2 className="w-3.5 h-3.5 animate-spin" />
                <span>{processingDocs} 个文档处理中</span>
              </div>
            )}
          </div>
        </div>

        {/* Main Area */}
        <div className="flex-1 flex flex-col min-w-0">
          {/* Toolbar */}
          <div className="flex items-center justify-between px-6 py-3 border-b border-slate-700/40 bg-slate-900/30">
            <div className="flex items-center gap-2">
              <h2 className="text-sm font-semibold text-slate-200">
                {activeCategory
                  ? categories.find((c) => c.id === activeCategory)?.name || '文档列表'
                  : '全部文档'}
              </h2>
              <span className="text-xs text-slate-500">({displayDocs.length})</span>
            </div>
            <div className="flex items-center gap-2">
              <button
                onClick={() => fetchDocuments()}
                className="p-1.5 hover:bg-slate-800 rounded-lg transition-colors"
                title="刷新"
              >
                <RefreshCw className="w-4 h-4 text-slate-400" />
              </button>
              <button
                onClick={() => setShowAddText(true)}
                className="flex items-center gap-1.5 px-3 py-1.5 text-xs bg-slate-800 border border-slate-700 rounded-lg hover:bg-slate-700 transition-colors text-slate-300"
              >
                <FileText className="w-3.5 h-3.5" />
                新建文本
              </button>
              <button
                onClick={() => setShowUploadPanel(true)}
                className="flex items-center gap-1.5 px-3 py-1.5 text-xs bg-gradient-to-r from-blue-500 to-cyan-500 text-white rounded-lg hover:from-blue-600 hover:to-cyan-600 transition-all shadow-[0_0_10px_rgba(59,130,246,0.2)]"
              >
                <Upload className="w-3.5 h-3.5" />
                上传文件
              </button>
              <button
                onClick={() => setShowImportPanel(true)}
                className="flex items-center gap-1.5 px-3 py-1.5 text-xs bg-gradient-to-r from-blue-500 to-cyan-500 text-white rounded-lg hover:from-blue-600 hover:to-cyan-600 transition-all shadow-[0_0_10px_rgba(59,130,246,0.25)]"
              >
                <Database className="w-3.5 h-3.5" />
                批量导入
              </button>
              <button
                onClick={loadTaskHistory}
                className="flex items-center gap-1.5 px-3 py-1.5 text-xs bg-slate-800 border border-slate-700 rounded-lg hover:bg-slate-700 transition-colors text-slate-300"
              >
                <Clock className="w-3.5 h-3.5" />
                导入历史
              </button>
            </div>
          </div>

          {/* Document Grid/List */}
          <div className="flex-1 overflow-y-auto p-6">
            {loading ? (
              <div className="flex items-center justify-center h-64">
                <Loader2 className="w-8 h-8 text-blue-400 animate-spin" />
                <span className="ml-3 text-slate-400">加载中...</span>
              </div>
            ) : displayDocs.length === 0 ? (
              <div className="flex flex-col items-center justify-center h-64 text-center">
                <div className="w-20 h-20 bg-gradient-to-br from-blue-500/10 to-cyan-500/10 rounded-2xl flex items-center justify-center mb-4 border border-slate-700/50">
                  <BookOpen className="w-10 h-10 text-blue-400" />
                </div>
                <h3 className="text-lg font-semibold text-slate-200 mb-2">暂无文档</h3>
                <p className="text-sm text-slate-500 max-w-sm mb-4">
                  上传文件或新建文本，系统将自动进行向量化处理，支持 AI 语义检索
                </p>
                <div className="flex gap-3">
                  <button
                    onClick={() => setShowAddText(true)}
                    className="flex items-center gap-1.5 px-4 py-2 text-sm border border-slate-700 text-slate-300 rounded-lg hover:bg-slate-800 transition-colors"
                  >
                    <FileText className="w-4 h-4" />
                    新建文本
                  </button>
                  <button
                    onClick={() => setShowUploadPanel(true)}
                    className="flex items-center gap-1.5 px-4 py-2 text-sm bg-gradient-to-r from-blue-500 to-cyan-500 text-white rounded-lg hover:from-blue-600 hover:to-cyan-600 transition-all shadow-[0_0_10px_rgba(59,130,246,0.2)]"
                  >
                    <Upload className="w-4 h-4" />
                    上传文件
                  </button>
                </div>
              </div>
            ) : viewMode === 'grid' ? (
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
                {displayDocs.map((doc) => {
                  const ext = doc.type || 'text';
                  const statusInfo = EMBEDDING_STATUS[doc.embeddingStatus || 'PENDING'] || EMBEDDING_STATUS.PENDING;
                  return (
                    <div
                      key={doc.id}
                      className="group bg-slate-900/80 rounded-xl border border-slate-700/50 hover:border-blue-500/40 shadow-sm hover:shadow-[0_0_15px_rgba(59,130,246,0.1)] transition-all cursor-pointer overflow-hidden"
                      onClick={() => setViewingDoc(doc)}
                    >
                      {/* File type header */}
                      <div className="h-28 bg-gradient-to-br from-slate-800/80 to-blue-500/5 flex items-center justify-center relative">
                        {FILE_ICONS[ext] || FILE_ICONS.text}
                        {/* Vectorization badge */}
                        <div className={`absolute top-2 right-2 flex items-center gap-1 px-1.5 py-0.5 rounded text-[10px] border ${statusInfo.color}`}>
                          {statusInfo.icon}
                          {statusInfo.label}
                        </div>
                      </div>
                      {/* Info */}
                      <div className="p-3">
                        <h3 className="text-sm font-medium text-slate-200 truncate" title={doc.title}>
                          {doc.title}
                        </h3>
                        <div className="flex items-center gap-2 mt-1.5">
                          <span className="text-[10px] text-slate-500 uppercase font-mono">{ext}</span>
                          {doc.fileSize ? (
                            <span className="text-[10px] text-slate-500">{formatFileSize(doc.fileSize)}</span>
                          ) : null}
                          {(doc.chunkCount ?? 0) > 0 && (
                            <span className="text-[10px] text-blue-400">{doc.chunkCount} 切片</span>
                          )}
                        </div>
                        {doc.categoryName && (
                          <div className="mt-1.5">
                            <span className="text-[10px] px-1.5 py-0.5 bg-blue-500/10 text-blue-400 rounded">
                              {doc.categoryName}
                            </span>
                          </div>
                        )}
                        <div className="flex items-center justify-between mt-2">
                          <span className="text-[10px] text-slate-500">
                            {new Date(doc.createdAt).toLocaleDateString()}
                          </span>
                          <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                            <button
                              onClick={(e) => {
                                e.stopPropagation();
                                handleDeleteDoc(doc.id);
                              }}
                              className="p-1 hover:bg-red-50 rounded transition-colors"
                              title="删除"
                            >
                              <Trash2 className="w-3 h-3 text-red-400" />
                            </button>
                          </div>
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            ) : (
              /* List view */
              <div className="space-y-2">
                {displayDocs.map((doc) => {
                  const ext = doc.type || 'text';
                  const statusInfo = EMBEDDING_STATUS[doc.embeddingStatus || 'PENDING'] || EMBEDDING_STATUS.PENDING;
                  return (
                    <div
                      key={doc.id}
                      className="group flex items-center gap-4 p-3 bg-slate-900/80 rounded-lg border border-slate-700/50 hover:border-blue-500/40 shadow-sm hover:shadow-[0_0_15px_rgba(59,130,246,0.1)] transition-all cursor-pointer"
                      onClick={() => setViewingDoc(doc)}
                    >
                      <div className="w-10 h-10 bg-gradient-to-br from-slate-800 to-blue-500/10 rounded-lg flex items-center justify-center shrink-0 border border-slate-700/50">
                        {FILE_ICONS[ext] ? (
                          <span className="scale-75 origin-center">{FILE_ICONS[ext]}</span>
                        ) : (
                          <FileText className="w-5 h-5 text-slate-500" />
                        )}
                      </div>
                      <div className="flex-1 min-w-0">
                        <h3 className="text-sm font-medium text-slate-200 truncate">{doc.title}</h3>
                        <div className="flex items-center gap-2 mt-0.5">
                          <span className="text-[10px] text-slate-500 uppercase font-mono">{ext}</span>
                          {doc.fileSize ? (
                            <span className="text-[10px] text-slate-500">{formatFileSize(doc.fileSize)}</span>
                          ) : null}
                          {doc.categoryName && (
                            <span className="text-[10px] px-1.5 py-0.5 bg-blue-500/10 text-blue-400 rounded">
                              {doc.categoryName}
                            </span>
                          )}
                        </div>
                      </div>
                      <div className={`flex items-center gap-1 px-2 py-0.5 rounded text-[10px] border ${statusInfo.color}`}>
                        {statusInfo.icon}
                        {statusInfo.label}
                        {(doc.chunkCount ?? 0) > 0 && <span className="ml-1">({doc.chunkCount}片)</span>}
                      </div>
                      <span className="text-[10px] text-slate-500 w-20 text-right">
                        {new Date(doc.createdAt).toLocaleDateString()}
                      </span>
                      <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            handleDeleteDoc(doc.id);
                          }}
                          className="p-1 hover:bg-red-500/10 rounded transition-colors"
                          title="删除"
                        >
                          <Trash2 className="w-3.5 h-3.5 text-red-400" />
                        </button>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Upload Modal */}
      {showUploadPanel && (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center z-50" onClick={() => !isUploading && setShowUploadPanel(false)}>
          <div className="bg-slate-900 rounded-2xl shadow-xl w-full max-w-lg p-6 border border-slate-700/50" onClick={(e) => e.stopPropagation()}>
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-lg font-semibold text-slate-100">上传文件</h3>
              {!isUploading && (
                <button onClick={() => setShowUploadPanel(false)} className="p-1 hover:bg-slate-800 rounded">
                  <X className="w-5 h-5 text-slate-400" />
                </button>
              )}
            </div>

            <div
              className={`border-2 border-dashed rounded-xl p-8 text-center cursor-pointer transition-colors mb-4 ${
                dragOver ? 'border-blue-400 bg-blue-500/10' : 'border-slate-700 hover:border-blue-500/50'
              }`}
              onClick={() => fileInputRef.current?.click()}
              onDragOver={(e) => { e.preventDefault(); setDragOver(true); }}
              onDragLeave={() => setDragOver(false)}
              onDrop={(e) => {
                e.preventDefault();
                setDragOver(false);
                if (e.dataTransfer.files.length > 0) setUploadFiles(e.dataTransfer.files);
              }}
            >
              <Upload className={`w-10 h-10 mx-auto mb-3 ${dragOver ? 'text-blue-400' : 'text-slate-500'}`} />
              <p className="text-sm text-slate-400 mb-1">{dragOver ? '松开以上传文件' : '点击选择文件或拖拽到此处'}</p>
              <p className="text-xs text-slate-500">支持 PDF、Word、Excel、TXT、Markdown</p>
              <input
                ref={fileInputRef}
                type="file"
                accept=".pdf,.doc,.docx,.xls,.xlsx,.txt,.md,.csv"
                multiple
                className="hidden"
                onChange={(e) => setUploadFiles(e.target.files)}
              />
            </div>

            {uploadFiles && uploadFiles.length > 0 && (
              <div className="space-y-1.5 mb-4 max-h-32 overflow-y-auto">
                {Array.from(uploadFiles).map((file, i) => (
                  <div key={i} className="flex items-center gap-2 px-3 py-1.5 bg-slate-800 rounded-lg text-xs">
                    <FileText className="w-3.5 h-3.5 text-slate-400" />
                    <span className="flex-1 truncate text-slate-300">{file.name}</span>
                    <span className="text-slate-500">{formatFileSize(file.size)}</span>
                  </div>
                ))}
              </div>
            )}

            {isUploading && (
              <div className="space-y-2 mb-4">
                <div className="h-2 bg-slate-800 rounded-full overflow-hidden">
                  <div
                    className="h-full bg-gradient-to-r from-blue-500 to-cyan-500 rounded-full transition-all duration-300"
                    style={{ width: `${uploadProgress}%` }}
                  />
                </div>
                <p className="text-xs text-slate-400 text-center">上传并处理中... {uploadProgress}%</p>
              </div>
            )}

            <div className="flex gap-3">
              <button
                onClick={() => setShowUploadPanel(false)}
                disabled={isUploading}
                className="flex-1 py-2 border border-slate-700 rounded-lg text-sm text-slate-500 hover:bg-slate-800/50 transition-colors disabled:opacity-50"
              >
                取消
              </button>
              <button
                onClick={handleFileUpload}
                disabled={!uploadFiles || uploadFiles.length === 0 || isUploading}
                className="flex-1 py-2 bg-gradient-to-r from-indigo-500 to-blue-600 text-white rounded-lg text-sm font-medium hover:from-indigo-600 hover:to-blue-700 transition-all disabled:opacity-50"
              >
                {isUploading ? '上传中...' : '开始上传'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Batch Import Modal */}
      {showImportPanel && (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center z-50" onClick={() => !isImporting && closeImportPanel()}>
          <div className="bg-slate-900 rounded-2xl w-full max-w-2xl p-6 border border-blue-500/20 shadow-[0_0_25px_rgba(59,130,246,0.15)]" onClick={(e) => e.stopPropagation()}>
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-lg font-semibold text-slate-100">批量导入</h3>
              {!isImporting && (
                <button onClick={closeImportPanel} className="p-1 hover:bg-slate-800 rounded">
                  <X className="w-5 h-5 text-slate-400" />
                </button>
              )}
            </div>

            {/* 内嵌提示条（替代原生 alert） */}
            {importNotice && (
              <div className={`mb-4 flex items-start gap-2.5 rounded-lg border px-3.5 py-2.5 text-sm ${
                importNotice.type === 'error'
                  ? 'border-red-500/30 bg-red-500/10 text-red-300'
                  : importNotice.type === 'success'
                    ? 'border-emerald-500/30 bg-emerald-500/10 text-emerald-300'
                    : 'border-blue-500/30 bg-blue-500/10 text-blue-300'
              }`}>
                {importNotice.type === 'error' ? (
                  <AlertCircle className="w-4 h-4 mt-0.5 shrink-0" />
                ) : importNotice.type === 'success' ? (
                  <CheckCircle className="w-4 h-4 mt-0.5 shrink-0" />
                ) : (
                  <Info className="w-4 h-4 mt-0.5 shrink-0" />
                )}
                <span className="flex-1 leading-5">{importNotice.text}</span>
                <button
                  onClick={() => setImportNotice(null)}
                  className="shrink-0 opacity-60 hover:opacity-100 transition-opacity"
                >
                  <X className="w-3.5 h-3.5" />
                </button>
              </div>
            )}

            {/* Mode Tabs */}
            {!isImporting && !importProgress && (
              <div className="flex gap-2 mb-4">
                <button
                  onClick={() => setImportMode('upload')}
                  className={`flex-1 py-2 text-sm rounded-lg transition-colors ${
                    importMode === 'upload'
                      ? 'bg-gradient-to-r from-blue-500 to-cyan-500 text-white'
                      : 'bg-slate-800 text-slate-400 hover:bg-slate-700'
                  }`}
                >
                  上传 Zip 文件
                </button>
                <button
                  onClick={() => setImportMode('path')}
                  className={`flex-1 py-2 text-sm rounded-lg transition-colors ${
                    importMode === 'path'
                      ? 'bg-gradient-to-r from-blue-500 to-cyan-500 text-white'
                      : 'bg-slate-800 text-slate-400 hover:bg-slate-700'
                  }`}
                >
                  服务器路径
                </button>
              </div>
            )}

            {/* Upload Mode */}
            {importMode === 'upload' && !isImporting && !importProgress && (
              <>
                <div
                  className={`border-2 border-dashed rounded-xl p-8 text-center cursor-pointer transition-colors mb-4 ${
                    importDragOver ? 'border-blue-400 bg-blue-500/10' : 'border-slate-700 hover:border-blue-500/50'
                  }`}
                  onClick={() => importFileInputRef.current?.click()}
                  onDragOver={(e) => { e.preventDefault(); setImportDragOver(true); }}
                  onDragLeave={() => setImportDragOver(false)}
                  onDrop={(e) => {
                    e.preventDefault();
                    setImportDragOver(false);
                    if (e.dataTransfer.files.length > 0) {
                      const file = e.dataTransfer.files[0];
                      if (file.name.endsWith('.zip')) {
                        setImportZipFile(file);
                      } else {
                        alert('请选择 zip 文件');
                      }
                    }
                  }}
                >
                  <Database className={`w-10 h-10 mx-auto mb-3 ${importDragOver ? 'text-blue-400' : 'text-slate-500'}`} />
                  <p className="text-sm text-slate-400 mb-1">{importDragOver ? '松开以上传文件' : '点击选择 Zip 文件或拖拽到此处'}</p>
                  <p className="text-xs text-slate-500">支持 200GB+ 大文件，流式处理不占内存</p>
                  <input
                    ref={importFileInputRef}
                    type="file"
                    accept=".zip"
                    className="hidden"
                    onChange={(e) => {
                      const file = e.target.files?.[0];
                      if (file && file.name.endsWith('.zip')) {
                        setImportZipFile(file);
                      } else {
                        alert('请选择 zip 文件');
                      }
                    }}
                  />
                </div>

                {importZipFile && (
                  <div className="flex items-center gap-2 px-3 py-2 bg-slate-800 rounded-lg text-sm mb-4">
                    <Database className="w-4 h-4 text-blue-400" />
                    <span className="flex-1 truncate text-slate-300">{importZipFile.name}</span>
                    <span className="text-slate-500">{formatFileSize(importZipFile.size)}</span>
                    <button onClick={() => setImportZipFile(null)} className="p-1 hover:bg-slate-700 rounded">
                      <X className="w-3.5 h-3.5 text-slate-400" />
                    </button>
                  </div>
                )}
              </>
            )}

            {/* Path Mode */}
            {importMode === 'path' && !isImporting && !importProgress && (
              <div className="space-y-4 mb-4">
                <div>
                  <label className="block text-sm text-slate-400 mb-2">服务器路径（Zip 文件或文件夹）</label>
                  <input
                    type="text"
                    value={importPath}
                    onChange={(e) => setImportPath(e.target.value)}
                    placeholder="/data/salesperson.zip 或 /data/salesperson/"
                    className="w-full px-4 py-2 bg-slate-800 border border-slate-700 rounded-lg text-sm text-slate-200 placeholder-slate-500 focus:outline-none focus:border-blue-500"
                  />
                  <p className="text-xs text-slate-500 mt-1">支持 zip 压缩包或文件夹，自动递归处理所有子目录</p>
                </div>
              </div>
            )}

            {/* Progress Display */}
            {importProgress && (
              <div className="space-y-3 mb-4">
                <div className="flex items-center justify-between text-sm">
                  <span className="text-slate-400">状态</span>
                  <span className={`font-medium ${
                    importProgress.status === 'COMPLETED' ? 'text-emerald-400' :
                    importProgress.status === 'FAILED' ? 'text-red-400' :
                    importProgress.status === 'CANCELLED' ? 'text-amber-400' :
                    'text-blue-400'
                  }`}>
                    {importProgress.status === 'RUNNING' ? '处理中...' :
                     importProgress.status === 'COMPLETED' ? '已完成' :
                     importProgress.status === 'FAILED' ? '失败' :
                     importProgress.status === 'CANCELLED' ? '已取消' : importProgress.status}
                  </span>
                </div>

                <div className="flex items-center justify-between text-sm">
                  <span className="text-slate-400">文件进度</span>
                  <span className="text-slate-200">
                    {importProgress.processedFiles || 0} / {importProgress.totalFiles || 0}
                  </span>
                </div>

                <div className="flex items-center justify-between text-sm">
                  <span className="text-slate-400">切片数</span>
                  <span className="text-slate-200">{importProgress.totalChunks || 0}</span>
                </div>

                {importProgress.failedFiles > 0 && (
                  <div className="flex items-center justify-between text-sm">
                    <span className="text-slate-400">失败文件</span>
                    <span className="text-red-400">{importProgress.failedFiles}</span>
                  </div>
                )}

                {importProgress.status === 'RUNNING' && importProgress.totalFiles > 0 && (
                  <div className="h-2 bg-slate-800 rounded-full overflow-hidden">
                    <div
                      className="h-full bg-gradient-to-r from-blue-500 to-cyan-500 rounded-full transition-all duration-300"
                      style={{ width: `${Math.round(((importProgress.processedFiles || 0) / importProgress.totalFiles) * 100)}%` }}
                    />
                  </div>
                )}

                {importProgress.recentErrors && importProgress.recentErrors.length > 0 && (
                  <div className="mt-3">
                    <p className="text-xs text-slate-500 mb-1">最近错误：</p>
                    <div className="max-h-24 overflow-y-auto space-y-1">
                      {importProgress.recentErrors.slice(0, 5).map((err: string, i: number) => (
                        <div key={i} className="text-xs text-red-400/80 bg-red-500/5 px-2 py-1 rounded">
                          {err}
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            )}

            {/* Action Buttons */}
            <div className="flex gap-3">
              <button
                onClick={closeImportPanel}
                disabled={isImporting}
                className="flex-1 py-2 border border-slate-700 rounded-lg text-sm text-slate-500 hover:bg-slate-800/50 transition-colors disabled:opacity-50"
              >
                {importProgress?.status === 'COMPLETED' || importProgress?.status === 'FAILED' || importProgress?.status === 'CANCELLED' ? '关闭' : '取消'}
              </button>
              {isImporting && importProgress?.status === 'RUNNING' ? (
                <button
                  onClick={handleCancelImport}
                  className="flex-1 py-2 bg-red-500 text-white rounded-lg text-sm font-medium hover:bg-red-600 transition-all"
                >
                  取消导入
                </button>
              ) : !importProgress ? (
                <button
                  onClick={handleImportSubmit}
                  disabled={(importMode === 'upload' && !importZipFile) || (importMode === 'path' && !importPath.trim()) || isImporting}
                  className="flex-1 py-2 bg-gradient-to-r from-blue-500 to-cyan-500 text-white rounded-lg text-sm font-medium hover:from-blue-600 hover:to-cyan-600 transition-all disabled:opacity-50"
                >
                  {isImporting ? '提交中...' : '开始导入'}
                </button>
              ) : null}
            </div>
          </div>
        </div>
      )}

      {/* Task History Modal */}
      {showTaskHistory && (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center z-50" onClick={() => setShowTaskHistory(false)}>
          <div className="bg-slate-900 rounded-2xl shadow-xl w-full max-w-3xl p-6 border border-slate-700/50 max-h-[80vh] flex flex-col" onClick={(e) => e.stopPropagation()}>
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-lg font-semibold text-slate-100">导入任务历史</h3>
              <button onClick={() => setShowTaskHistory(false)} className="p-1 hover:bg-slate-800 rounded">
                <X className="w-5 h-5 text-slate-400" />
              </button>
            </div>

            <div className="flex-1 overflow-y-auto space-y-2">
              {importTasks.length === 0 ? (
                <div className="text-center py-12 text-slate-500">
                  <Clock className="w-12 h-12 mx-auto mb-3 opacity-50" />
                  <p>暂无导入任务</p>
                </div>
              ) : (
                importTasks.map((task: any) => (
                  <div key={task.taskId} className="bg-slate-800 rounded-lg p-4 border border-slate-700/50">
                    <div className="flex items-center justify-between mb-2">
                      <span className="text-sm font-medium text-slate-200 truncate flex-1">{task.source}</span>
                      <span className={`text-xs px-2 py-0.5 rounded ${
                        task.status === 'COMPLETED' ? 'bg-emerald-500/10 text-emerald-400' :
                        task.status === 'FAILED' ? 'bg-red-500/10 text-red-400' :
                        task.status === 'CANCELLED' ? 'bg-amber-500/10 text-amber-400' :
                        'bg-blue-500/10 text-blue-400'
                      }`}>
                        {task.status === 'RUNNING' ? '处理中' :
                         task.status === 'COMPLETED' ? '已完成' :
                         task.status === 'FAILED' ? '失败' :
                         task.status === 'CANCELLED' ? '已取消' : task.status}
                      </span>
                    </div>
                    <div className="flex items-center gap-4 text-xs text-slate-400">
                      <span>文件: {task.processedFiles}/{task.totalFiles}</span>
                      <span>切片: {task.totalChunks}</span>
                      {task.failedFiles > 0 && <span className="text-red-400">失败: {task.failedFiles}</span>}
                      <span className="ml-auto">{new Date(task.startTime).toLocaleString('zh-CN')}</span>
                    </div>
                  </div>
                ))
              )}
            </div>
          </div>
        </div>
      )}

      {/* Add Text Document Modal */}
      {showAddText && (
        <div className="fixed inset-0 bg-black/30 backdrop-blur-sm flex items-center justify-center z-50" onClick={() => !isAddingText && setShowAddText(false)}>
          <div className="bg-slate-800 rounded-2xl shadow-xl w-full max-w-lg p-6" onClick={(e) => e.stopPropagation()}>
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-lg font-semibold text-slate-800">新建文本文档</h3>
              <button onClick={() => setShowAddText(false)} className="p-1 hover:bg-slate-100 rounded">
                <X className="w-5 h-5 text-slate-400" />
              </button>
            </div>

            <div className="space-y-3">
              <input
                value={addTitle}
                onChange={(e) => setAddTitle(e.target.value)}
                placeholder="文档标题"
                className="w-full px-3 py-2 text-sm border border-slate-700 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-300 bg-slate-800"
              />
              <textarea
                value={addContent}
                onChange={(e) => setAddContent(e.target.value)}
                placeholder="输入文档内容，系统会自动进行切片和向量化处理..."
                rows={8}
                className="w-full px-3 py-2 text-sm border border-slate-700 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-300 bg-slate-800 resize-none"
              />
            </div>

            <div className="flex gap-3 mt-4">
              <button
                onClick={() => setShowAddText(false)}
                className="flex-1 py-2 border border-slate-700 rounded-lg text-sm text-slate-500 hover:bg-slate-800/50 transition-colors"
              >
                取消
              </button>
              <button
                onClick={handleAddText}
                disabled={!addTitle.trim() || !addContent.trim() || isAddingText}
                className="flex-1 py-2 bg-gradient-to-r from-indigo-500 to-blue-600 text-white rounded-lg text-sm font-medium hover:from-indigo-600 hover:to-blue-700 transition-all disabled:opacity-50 flex items-center justify-center gap-1.5"
              >
                {isAddingText ? <Loader2 className="w-4 h-4 animate-spin" /> : <Plus className="w-4 h-4" />}
                {isAddingText ? '创建中...' : '创建文档'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Document Detail Modal */}
      {viewingDoc && (
        <div className="fixed inset-0 bg-black/30 backdrop-blur-sm flex items-center justify-center z-50" onClick={() => setViewingDoc(null)}>
          <div className="bg-slate-800 rounded-2xl shadow-xl w-full max-w-2xl max-h-[80vh] overflow-hidden" onClick={(e) => e.stopPropagation()}>
            <div className="flex items-center justify-between p-5 border-b border-slate-700/60">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 bg-gradient-to-br from-slate-50 to-indigo-50 rounded-lg flex items-center justify-center">
                  {FILE_ICONS[viewingDoc.type] || FILE_ICONS.text}
                </div>
                <div>
                  <h3 className="text-base font-semibold text-slate-800">{viewingDoc.title}</h3>
                  <div className="flex items-center gap-2 mt-0.5">
                    <span className="text-xs text-slate-400 uppercase font-mono">{viewingDoc.type}</span>
                    {viewingDoc.fileSize ? (
                      <span className="text-xs text-slate-400">{formatFileSize(viewingDoc.fileSize)}</span>
                    ) : null}
                    <span className="text-xs text-slate-300">{new Date(viewingDoc.createdAt).toLocaleString()}</span>
                  </div>
                </div>
              </div>
              <button onClick={() => setViewingDoc(null)} className="p-1 hover:bg-slate-100 rounded">
                <X className="w-5 h-5 text-slate-400" />
              </button>
            </div>

            <div className="p-5 overflow-y-auto max-h-[60vh] space-y-4">
              {/* 向量化状态 */}
              <div className="flex items-center gap-3 p-3 bg-slate-800/50 rounded-lg">
                <Database className="w-5 h-5 text-indigo-400" />
                <div className="flex-1">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-medium text-slate-300">向量化状态</span>
                    {(() => {
                      const s = EMBEDDING_STATUS[viewingDoc.embeddingStatus || 'PENDING'] || EMBEDDING_STATUS.PENDING;
                      return (
                        <span className={`flex items-center gap-1 px-2 py-0.5 rounded text-xs border ${s.color}`}>
                          {s.icon} {s.label}
                        </span>
                      );
                    })()}
                  </div>
                  {(viewingDoc.chunkCount ?? 0) > 0 && (
                    <p className="text-xs text-slate-400 mt-1">已切分为 {viewingDoc.chunkCount} 个片段并完成向量化</p>
                  )}
                </div>
              </div>

              {/* 分类 */}
              {viewingDoc.categoryName && (
                <div className="flex items-center gap-2">
                  <Tag className="w-4 h-4 text-slate-400" />
                  <span className="text-sm text-slate-500">分类:</span>
                  <span className="text-sm px-2 py-0.5 bg-blue-500/20 text-blue-400 rounded">{viewingDoc.categoryName}</span>
                </div>
              )}

              {/* 文本内容 */}
              {viewingDoc.content && (
                <div>
                  <h4 className="text-sm font-medium text-slate-300 mb-2">文档内容</h4>
                  <div className="p-3 bg-slate-800/50 rounded-lg text-sm text-slate-500 whitespace-pre-wrap max-h-64 overflow-y-auto leading-relaxed">
                    {viewingDoc.content.length > 3000
                      ? viewingDoc.content.slice(0, 3000) + '...'
                      : viewingDoc.content}
                  </div>
                </div>
              )}
            </div>

            <div className="flex items-center justify-end gap-2 p-4 border-t border-slate-700/60">
              {viewingDoc.embeddingStatus === 'FAILED' && (
                <button
                  onClick={() => { handleRetryEmbedding(viewingDoc.id); setViewingDoc(null); }}
                  className="flex items-center gap-1.5 px-3 py-1.5 text-sm text-amber-600 hover:bg-amber-50 rounded-lg transition-colors"
                >
                  <RefreshCw className="w-4 h-4" />
                  重新向量化
                </button>
              )}
              {viewingDoc.fileName && (
                <button
                  onClick={() => handleDownloadDoc(viewingDoc)}
                  className="flex items-center gap-1.5 px-3 py-1.5 text-sm text-blue-400 hover:bg-blue-500/10 rounded-lg transition-colors"
                >
                  <Download className="w-4 h-4" />
                  下载
                </button>
              )}
              <button
                onClick={() => {
                  handleDeleteDoc(viewingDoc.id);
                  setViewingDoc(null);
                }}
                className="flex items-center gap-1.5 px-3 py-1.5 text-sm text-red-500 hover:bg-red-50 rounded-lg transition-colors"
              >
                <Trash2 className="w-4 h-4" />
                删除文档
              </button>
              <button
                onClick={() => setViewingDoc(null)}
                className="px-4 py-1.5 text-sm bg-gradient-to-r from-indigo-500 to-blue-600 text-white rounded-lg hover:from-indigo-600 hover:to-blue-700 transition-all"
              >
                关闭
              </button>
            </div>
          </div>
        </div>
      )}
      </>
      )}
    </div>
  );
}
