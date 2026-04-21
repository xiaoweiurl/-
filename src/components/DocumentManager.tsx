'use client';

import React from 'react';
import { 
  FolderOpen, 
  Upload, 
  FileIcon, 
  Download, 
  Trash2, 
  Copy, 
  Eye,
  X,
  Search,
  Plus,
  MoreVertical,
  FileText,
  FileSpreadsheet,
  Presentation,
  Archive,
  File,
  Image as ImageIcon,
  CheckCircle2,
  AlertCircle,
  Loader2,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { cn } from '@/lib/utils';
import { toast } from 'sonner';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { useNotifications } from '@/contexts/NotificationContext';

// 后端 API 基础 URL
const BACKEND_API_URL = process.env.NEXT_PUBLIC_BACKEND_API_URL || 'http://localhost:8080/api';

// 文档分类类型
type DocumentCategory = 'pdf' | 'word' | 'excel' | 'ppt' | 'zip' | 'other' | 'all';

// 文档分类配置
const DOCUMENT_CATEGORIES: Record<DocumentCategory, { 
  label: string; 
  icon: React.ElementType;
  color: string;
}> = {
  all: { label: '全部', icon: FolderOpen, color: 'text-slate-600' },
  pdf: { label: 'PDF', icon: FileText, color: 'text-red-500' },
  word: { label: 'Word', icon: FileIcon, color: 'text-blue-500' },
  excel: { label: 'Excel', icon: FileSpreadsheet, color: 'text-green-500' },
  ppt: { label: 'PPT', icon: Presentation, color: 'text-orange-500' },
  zip: { label: '压缩包', icon: Archive, color: 'text-purple-500' },
  other: { label: '其他', icon: File, color: 'text-slate-500' },
};

// 文档类型定义
interface Document {
  id: string;
  name: string;
  originalName: string;
  url: string;
  size: number;
  contentType: string;
  extension: string;
  category: string;
  uploadTime: string;
}

// 格式化文件大小
const formatFileSize = (bytes: number): string => {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;
};

// 格式化日期
const formatDate = (dateStr: string): string => {
  const date = new Date(dateStr);
  return date.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  });
};

// 根据扩展名获取分类
const getCategoryFromExtension = (ext: string): DocumentCategory => {
  const extension = ext.toLowerCase();
  if (extension === 'pdf') return 'pdf';
  if (['doc', 'docx'].includes(extension)) return 'word';
  if (['xls', 'xlsx', 'csv'].includes(extension)) return 'excel';
  if (['ppt', 'pptx'].includes(extension)) return 'ppt';
  if (['zip', 'rar', '7z', 'tar', 'gz'].includes(extension)) return 'zip';
  return 'other';
};

// 获取文件图标
const getFileIcon = (ext: string) => {
  const category = getCategoryFromExtension(ext);
  return DOCUMENT_CATEGORIES[category].icon;
};

interface DocumentManagerProps {
  onClose?: () => void;
  initialCategory?: DocumentCategory;
  onStatsUpdate?: (stats: Record<string, number>) => void;
}

export default function DocumentManager({ onClose, initialCategory, onStatsUpdate }: DocumentManagerProps) {
  const [documents, setDocuments] = React.useState<Document[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [selectedCategory, setSelectedCategory] = React.useState<DocumentCategory>(initialCategory || 'all');
  const [searchQuery, setSearchQuery] = React.useState('');
  const [previewDoc, setPreviewDoc] = React.useState<Document | null>(null);
  const [isUploading, setIsUploading] = React.useState(false);
  const [uploadProgress, setUploadProgress] = React.useState(0);
  const fileInputRef = React.useRef<HTMLInputElement>(null);
  const { fetchNotifications } = useNotifications();

  // 文档分类统计
  const [categoryStats, setCategoryStats] = React.useState<Record<string, number>>({
    all: 0, pdf: 0, word: 0, excel: 0, ppt: 0, zip: 0, other: 0,
  });

  // 监听 initialCategory 变化，更新选中的分类
  React.useEffect(() => {
    if (initialCategory && initialCategory !== selectedCategory) {
      setSelectedCategory(initialCategory);
    }
  }, [initialCategory]);

  // ESC 键关闭预览
  React.useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && previewDoc) {
        setPreviewDoc(null);
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [previewDoc]);

  // 获取 sessionId
  const getSessionId = () => {
    if (typeof window === 'undefined') return null;
    return localStorage.getItem('session_id');
  };

  // 获取完整的文档 URL（处理相对路径）
  const getFullDocUrl = (url: string): string => {
    // 如果是旧格式的沙箱 URL，尝试转换
    if (url.includes('sandbox/coze_coding/file/proxy')) {
      console.warn('[DocumentManager] 检测到旧格式沙箱 URL，需要通过 API 代理:', url);
      // 旧格式 URL 无法直接使用，返回空或提示用户
      return '';
    }
    
    // 如果已经是完整 URL（包含协议），直接返回
    if (url.startsWith('http://') || url.startsWith('https://')) {
      return url;
    }
    
    // 如果是相对路径（/uploads/xxx 或 assets/xxx），拼接后端 API 地址
    if (url.startsWith('/uploads/') || url.startsWith('uploads/')) {
      return `${BACKEND_API_URL}${url.startsWith('/') ? '' : '/'}${url}`;
    }
    
    // 其他相对路径
    if (url.startsWith('/')) {
      return `${BACKEND_API_URL}${url}`;
    }
    
    return url;
  };

  // 监听 categoryStats 变化，通知父组件更新
  React.useEffect(() => {
    if (onStatsUpdate) {
      onStatsUpdate(categoryStats);
    }
  }, [categoryStats, onStatsUpdate]);

  // 获取文档分类统计
  const fetchDocumentStats = React.useCallback(async () => {
    try {
      const sessionId = getSessionId();
      const response = await fetch(`${BACKEND_API_URL}/documents/stats`, {
        headers: {
          'X-Session-Id': sessionId || '',
        },
        credentials: 'include',
      });
      
      if (response.ok) {
        const result = await response.json();
        if (result.success || result.code === 200) {
          const newStats = {
            all: result.data?.all || 0,
            pdf: result.data?.pdf || 0,
            word: result.data?.word || 0,
            excel: result.data?.excel || 0,
            ppt: result.data?.ppt || 0,
            zip: result.data?.zip || 0,
            other: result.data?.other || 0,
          };
          setCategoryStats(newStats);
        }
      }
    } catch (error) {
      console.error('获取文档统计失败:', error);
    }
  }, []);

  // 获取文档列表（按分类）
  const fetchDocuments = React.useCallback(async (category?: string) => {
    setLoading(true);
    try {
      const sessionId = getSessionId();
      // 构建查询参数
      const params = new URLSearchParams();
      params.append('page', '0');
      params.append('pageSize', '100');
      if (category && category !== 'all') {
        params.append('category', category);
      }
      
      const response = await fetch(`${BACKEND_API_URL}/documents?${params}`, {
        headers: {
          'X-Session-Id': sessionId || '',
        },
        credentials: 'include',
      });
      
      if (response.ok) {
        const result = await response.json();
        if (result.success || result.code === 200) {
          // 转换 URL 为前端代理地址（兼容旧数据）
          const docs = (result.data?.documents || []).map((doc: Document) => {
            // 如果 URL 包含 sandbox/coze_coding/file/proxy，转换为前端代理格式
            let correctUrl = doc.url;
            if (doc.url && doc.url.includes('sandbox/coze_coding/file/proxy')) {
              // 从旧 URL 中提取 file_path 参数
              const urlMatch = doc.url.match(/file_path=([^&]+)/);
              if (urlMatch) {
                const filePath = decodeURIComponent(urlMatch[1]);
                // 使用前端代理格式
                correctUrl = `${window.location.origin}/api/documents/${doc.id}/file`;
              }
            } else if (doc.url && !doc.url.includes('/api/documents/')) {
              // 其他情况也使用前端代理
              correctUrl = `${window.location.origin}/api/documents/${doc.id}/file`;
            }
            return { ...doc, url: correctUrl };
          });
          setDocuments(docs);
        }
      }
    } catch (error) {
      console.error('获取文档列表失败:', error);
      toast.error('获取文档列表失败');
    } finally {
      setLoading(false);
    }
  }, []);

  // 切换分类时重新获取文档
  React.useEffect(() => {
    const category = selectedCategory === 'all' ? undefined : selectedCategory;
    fetchDocuments(category);
    fetchDocumentStats();
  }, [selectedCategory, fetchDocuments, fetchDocumentStats]);

  // 过滤文档（仅用于前端搜索）
  const filteredDocuments = React.useMemo(() => {
    if (!searchQuery) return documents;
    const query = searchQuery.toLowerCase();
    return documents.filter(doc => 
      doc.name.toLowerCase().includes(query) || 
      doc.originalName?.toLowerCase().includes(query)
    );
  }, [documents, searchQuery]);

  // 上传文档
  const handleUpload = async (files: FileList | null) => {
    if (!files || files.length === 0) return;

    setIsUploading(true);
    setUploadProgress(0);
    
    let successCount = 0;
    let failCount = 0;

    for (let i = 0; i < files.length; i++) {
      const file = files[i];
      
      // 跳过图片
      if (file.type.startsWith('image/')) {
        toast.error(`${file.name} 是图片文件`);
        failCount++;
        continue;
      }

      try {
        const formData = new FormData();
        formData.append('file', file);
        formData.append('fileName', file.name);

        const sessionId = getSessionId();
        const response = await fetch(`${BACKEND_API_URL}/documents/upload`, {
          method: 'POST',
          headers: {
            'X-Session-Id': sessionId || '',
          },
          credentials: 'include',
          body: formData,
        });

        const result = await response.json();
        
        if (result.success || result.code === 200) {
          successCount++;
          // 添加到列表
          const newDoc: Document = {
            id: result.data?.id || Date.now().toString(),
            name: file.name,
            originalName: file.name,
            url: result.data?.url,
            size: file.size,
            contentType: file.type,
            extension: file.name.split('.').pop() || '',
            category: getCategoryFromExtension(file.name.split('.').pop() || ''),
            uploadTime: new Date().toISOString(),
          };
          setDocuments(prev => [newDoc, ...prev]);
        } else {
          failCount++;
        }
      } catch (error) {
        console.error('上传失败:', error);
        failCount++;
      }

      setUploadProgress(Math.round(((i + 1) / files.length) * 100));
    }

    setIsUploading(false);
    
    if (successCount > 0) {
      toast.success(`${successCount} 个文档上传成功`);
      // 刷新文档列表和分类统计
      fetchDocuments(selectedCategory === 'all' ? undefined : selectedCategory);
      fetchDocumentStats();
      // 刷新通知
      fetchNotifications();
    }
    if (failCount > 0) {
      toast.error(`${failCount} 个文档上传失败`);
    }
  };

  // 删除文档（直接永久删除）
  const handleDelete = async (doc: Document) => {
    if (!confirm(`确定要永久删除 "${doc.name}" 吗？此操作无法撤销！`)) return;

    try {
      const sessionId = getSessionId();
      // 调用永久删除接口
      const response = await fetch(`${BACKEND_API_URL}/documents/${doc.id}/permanent`, {
        method: 'DELETE',
        headers: {
          'X-Session-Id': sessionId || '',
        },
        credentials: 'include',
      });

      if (response.ok) {
        setDocuments(prev => prev.filter(d => d.id !== doc.id));
        toast.success('文档已永久删除');
        // 刷新分类统计
        fetchDocumentStats();
        // 刷新通知
        fetchNotifications();
      }
    } catch (error) {
      console.error('删除失败:', error);
      toast.error('删除失败');
    }
  };

  // 复制链接
  const handleCopyLink = (url: string) => {
    const fullUrl = getFullDocUrl(url);
    navigator.clipboard.writeText(fullUrl);
    toast.success('链接已复制');
  };

  // 下载文档
  const handleDownload = async (doc: Document) => {
    if (!doc.url) return;
    
    try {
      const sessionId = getSessionId();
      const fullUrl = getFullDocUrl(doc.url);
      console.log('[DocumentManager] 下载文档，完整URL:', fullUrl);
      
      const response = await fetch(fullUrl, {
        headers: {
          'X-Session-Id': sessionId || '',
        },
        credentials: 'include',
      });
      
      // 检查响应状态
      if (!response.ok) {
        const errorText = await response.text();
        console.error('[DocumentManager] 下载失败，HTTP状态:', response.status, '响应:', errorText);
        toast.error(`下载失败: ${response.status}`);
        return;
      }
      
      // 获取 Content-Type
      const contentType = response.headers.get('content-type') || '';
      console.log('[DocumentManager] 响应Content-Type:', contentType);
      
      // 如果响应是 JSON（错误信息），直接抛出错误
      if (contentType.includes('application/json') || contentType.includes('text/plain')) {
        const text = await response.text();
        console.error('[DocumentManager] 收到错误响应:', text);
        toast.error('下载失败: 文件不存在或路径错误');
        return;
      }
      
      const blob = await response.blob();
      const blobUrl = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = blobUrl;
      link.download = doc.name;
      link.click();
      window.URL.revokeObjectURL(blobUrl);
    } catch (error) {
      console.error('下载失败:', error);
      toast.error('下载失败');
    }
  };

  // 获取文档预览URL（带认证）
  const getPreviewUrl = (doc: Document): string => {
    // 使用文档 ID 构建预览 URL，走前端 API 代理
    // 格式：http://localhost:5000/api/documents/{id}/file
    // 这样可以正确处理认证和跨域问题
    return `/api/documents/${doc.id}/file`;
  };

  // 检查是否可预览
  const isPreviewable = (doc: Document): boolean => {
    return doc.extension.toLowerCase() === 'pdf';
  };

  return (
    <div className="flex flex-col h-full bg-slate-50">
      {/* 顶部操作栏 */}
      <div className="bg-white border-b border-slate-200 px-4 py-3">
        <div className="flex items-center justify-between mb-3">
          <div className="flex items-center gap-3">
            <FolderOpen className="w-6 h-6 text-emerald-600" />
            <h1 className="text-lg font-semibold text-slate-800">文档中心</h1>
            <span className="text-sm text-slate-500">({documents.length} 个文档)</span>
          </div>
          <div className="flex items-center gap-2">
            <Button
              onClick={() => fileInputRef.current?.click()}
              disabled={isUploading}
              className="bg-emerald-600 hover:bg-emerald-700"
            >
              {isUploading ? (
                <>
                  <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                  上传中 {uploadProgress}%
                </>
              ) : (
                <>
                  <Upload className="w-4 h-4 mr-2" />
                  上传文档
                </>
              )}
            </Button>
            <input
              ref={fileInputRef}
              type="file"
              multiple
              className="hidden"
              onChange={(e) => handleUpload(e.target.files)}
            />
          </div>
        </div>

        {/* 搜索框 */}
        <div className="relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
          <Input
            type="text"
            placeholder="搜索文档..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="pl-10"
          />
        </div>
      </div>

      {/* 分类标签 */}
      <div className="bg-white border-b border-slate-200 px-4 py-2 flex items-center gap-2 overflow-x-auto">
        {Object.entries(DOCUMENT_CATEGORIES).map(([key, config]) => {
          if (key === 'all' || (categoryStats[key] || 0) > 0 || selectedCategory === key) {
            const Icon = config.icon;
            const count = categoryStats[key] || 0;
            return (
              <button
                key={key}
                onClick={() => setSelectedCategory(key as DocumentCategory)}
                className={cn(
                  "flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-medium transition-colors whitespace-nowrap",
                  selectedCategory === key
                    ? "bg-emerald-100 text-emerald-700"
                    : "bg-slate-100 text-slate-600 hover:bg-slate-200"
                )}
              >
                <Icon className={cn("w-4 h-4", config.color, selectedCategory === key ? "" : "")} />
                {config.label}
                {count > 0 && (
                  <span className={cn(
                    "px-1.5 py-0.5 text-xs rounded-full",
                    selectedCategory === key ? "bg-emerald-200" : "bg-slate-200"
                  )}>
                    {count}
                  </span>
                )}
              </button>
            );
          }
          return null;
        })}
      </div>

      {/* 文档列表 */}
      <div className="flex-1 overflow-auto p-4">
        {loading ? (
          <div className="flex items-center justify-center h-64">
            <Loader2 className="w-8 h-8 animate-spin text-emerald-600" />
          </div>
        ) : filteredDocuments.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-64 text-slate-500">
            <FolderOpen className="w-16 h-16 mb-4 text-slate-300" />
            <p className="text-lg font-medium">暂无文档</p>
            <p className="text-sm">点击上方"上传文档"按钮开始上传</p>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
            {filteredDocuments.map((doc) => {
              const Icon = getFileIcon(doc.extension);
              const category = getCategoryFromExtension(doc.extension);
              const categoryConfig = DOCUMENT_CATEGORIES[category];
              
              return (
                <div
                  key={doc.id}
                  className="bg-white rounded-xl border border-slate-200 p-4 hover:border-slate-300 hover:shadow-md transition-all group"
                >
                  {/* 文件图标和名称 */}
                  <div className="flex items-start gap-3 mb-3">
                    <div className={cn(
                      "w-12 h-12 rounded-lg flex items-center justify-center flex-shrink-0",
                      category === 'pdf' ? 'bg-red-100' :
                      category === 'word' ? 'bg-blue-100' :
                      category === 'excel' ? 'bg-green-100' :
                      category === 'ppt' ? 'bg-orange-100' :
                      category === 'zip' ? 'bg-purple-100' :
                      'bg-slate-100'
                    )}>
                      <Icon className={cn("w-6 h-6", categoryConfig.color)} />
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-medium text-slate-800 truncate" title={doc.name}>
                        {doc.name}
                      </p>
                      <p className="text-xs text-slate-500 mt-0.5">
                        {formatFileSize(doc.size)}
                      </p>
                    </div>
                  </div>

                  {/* 元信息 */}
                  <div className="flex items-center justify-between text-xs text-slate-500 mb-3">
                    <span className={cn(
                      "px-2 py-0.5 rounded",
                      categoryConfig.label === 'PDF' ? 'bg-red-100 text-red-600' :
                      categoryConfig.label === 'Word' ? 'bg-blue-100 text-blue-600' :
                      categoryConfig.label === 'Excel' ? 'bg-green-100 text-green-600' :
                      categoryConfig.label === 'PPT' ? 'bg-orange-100 text-orange-600' :
                      categoryConfig.label === '压缩包' ? 'bg-purple-100 text-purple-600' :
                      'bg-slate-100 text-slate-600'
                    )}>
                      {categoryConfig.label}
                    </span>
                    <span>{formatDate(doc.uploadTime)}</span>
                  </div>

                  {/* 操作按钮 */}
                  <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                    {isPreviewable(doc) && (
                      <button
                        onClick={() => setPreviewDoc(doc)}
                        className="flex-1 flex items-center justify-center gap-1 px-2 py-1.5 text-xs text-slate-600 hover:bg-slate-100 rounded-lg transition-colors"
                      >
                        <Eye className="w-3.5 h-3.5" />
                        预览
                      </button>
                    )}
                    <button
                      onClick={() => handleCopyLink(doc.url)}
                      className="flex-1 flex items-center justify-center gap-1 px-2 py-1.5 text-xs text-slate-600 hover:bg-slate-100 rounded-lg transition-colors"
                    >
                      <Copy className="w-3.5 h-3.5" />
                      复制
                    </button>
                    <button
                      onClick={() => handleDelete(doc)}
                      className="flex-1 flex items-center justify-center gap-1 px-2 py-1.5 text-xs text-red-600 hover:bg-red-50 rounded-lg transition-colors"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                      删除
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* PDF 预览对话框 */}
      {previewDoc && (
        <div
          className="fixed inset-0 z-50 bg-black/60 backdrop-blur-sm flex items-center justify-center p-6"
          onClick={() => setPreviewDoc(null)}
        >
          <div
            className="relative w-full max-w-6xl h-[90vh] bg-white rounded-2xl shadow-2xl flex flex-col overflow-hidden animate-in fade-in zoom-in-95 duration-200"
            onClick={(e) => e.stopPropagation()}
          >
            {/* 顶部工具栏 */}
            <div className="flex items-center justify-between px-5 py-3 border-b border-slate-200 bg-gradient-to-r from-slate-50 to-white">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-xl bg-emerald-100 flex items-center justify-center">
                  {React.createElement(getFileIcon(previewDoc.extension), { className: "w-5 h-5 text-emerald-600" })}
                </div>
                <div>
                  <p className="text-sm font-semibold text-slate-800 max-w-md truncate">
                    {previewDoc.name}
                  </p>
                  <p className="text-xs text-slate-500">
                    {formatFileSize(previewDoc.size)} · {formatDate(previewDoc.uploadTime)}
                  </p>
                </div>
              </div>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => handleDownload(previewDoc)}
                  className="flex items-center gap-2 px-4 py-2 rounded-xl bg-emerald-600 hover:bg-emerald-700 text-white text-sm font-medium transition-colors"
                  title="下载"
                >
                  <Download className="w-4 h-4" />
                  下载
                </button>
                <button
                  onClick={() => handleCopyLink(previewDoc.url)}
                  className="flex items-center gap-2 px-4 py-2 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-700 text-sm font-medium transition-colors"
                  title="复制链接"
                >
                  <Copy className="w-4 h-4" />
                  复制
                </button>
                <button
                  onClick={() => setPreviewDoc(null)}
                  className="p-2.5 rounded-xl hover:bg-slate-100 text-slate-500 transition-colors"
                >
                  <X className="w-5 h-5" />
                </button>
              </div>
            </div>
            
            {/* 预览内容区 */}
            <div className="flex-1 p-4 bg-slate-100 overflow-hidden">
              <div className="w-full h-full rounded-xl bg-white shadow-lg overflow-hidden">
                <iframe
                  src={getPreviewUrl(previewDoc)}
                  className="w-full h-full"
                  title={previewDoc.name}
                />
              </div>
            </div>
            
            {/* 底部状态栏 */}
            <div className="flex items-center justify-between px-5 py-2 border-t border-slate-100 bg-slate-50 text-xs text-slate-500">
              <div className="flex items-center gap-4">
                <span>类型：{previewDoc.extension.toUpperCase()}</span>
                <span>大小：{formatFileSize(previewDoc.size)}</span>
              </div>
              <span>按 ESC 键关闭预览</span>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
