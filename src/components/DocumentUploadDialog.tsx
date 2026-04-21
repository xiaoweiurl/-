'use client';

import React from 'react';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { cn } from '@/lib/utils';
import { useNotifications } from '@/contexts/NotificationContext';
import { getSessionId } from '@/lib/auth-client';
import { 
  Upload, 
  X, 
  FileIcon, 
  Loader2, 
  CheckCircle2, 
  AlertCircle, 
  File, 
  Copy, 
  Download, 
  Eye,
  FolderOpen,
  FileText,
  FileSpreadsheet,
  Presentation,
  Archive,
  Image as ImageIcon,
} from 'lucide-react';
import { toast } from 'sonner';

interface DocumentUploadDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onUploadSuccess?: () => void;
}

// 后端 API 基础 URL
const BACKEND_API_URL = process.env.NEXT_PUBLIC_BACKEND_API_URL || 'http://localhost:8080/api';

// 文档分类类型
export type DocumentCategory = 'pdf' | 'word' | 'excel' | 'ppt' | 'zip' | 'other';

// 文档分类配置
export const DOCUMENT_CATEGORIES: Record<DocumentCategory, { 
  label: string; 
  icon: React.ElementType;
  extensions: string[];
}> = {
  pdf: { 
    label: 'PDF文档', 
    icon: FileText,
    extensions: ['pdf']
  },
  word: { 
    label: 'Word文档', 
    icon: FileIcon,
    extensions: ['doc', 'docx']
  },
  excel: { 
    label: 'Excel表格', 
    icon: FileSpreadsheet,
    extensions: ['xls', 'xlsx', 'csv']
  },
  ppt: { 
    label: 'PPT演示', 
    icon: Presentation,
    extensions: ['ppt', 'pptx']
  },
  zip: { 
    label: '压缩文件', 
    icon: Archive,
    extensions: ['zip', 'rar', '7z', 'tar', 'gz']
  },
  other: { 
    label: '其他文件', 
    icon: File,
    extensions: []
  },
};

// 根据文件名获取分类
const getCategoryFromFileName = (fileName: string): DocumentCategory => {
  const ext = fileName.split('.').pop()?.toLowerCase() || '';
  for (const [category, config] of Object.entries(DOCUMENT_CATEGORIES)) {
    if (config.extensions.includes(ext)) {
      return category as DocumentCategory;
    }
  }
  return 'other';
};

// 获取文件图标
const getFileIcon = (fileName: string) => {
  const category = getCategoryFromFileName(fileName);
  return DOCUMENT_CATEGORIES[category].icon;
};

interface UploadingFile {
  id: string;
  file: File;
  category: DocumentCategory;
  progress: number;
  status: 'pending' | 'uploading' | 'success' | 'error';
  url?: string;
  error?: string;
}

// 格式化文件大小
const formatFileSize = (bytes: number): string => {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;
};

export default function DocumentUploadDialog({
  open,
  onOpenChange,
  onUploadSuccess,
}: DocumentUploadDialogProps) {
  const [isDragging, setIsDragging] = React.useState(false);
  const [uploadingFiles, setUploadingFiles] = React.useState<UploadingFile[]>([]);
  const [isUploading, setIsUploading] = React.useState(false);
  const [previewFile, setPreviewFile] = React.useState<UploadingFile | null>(null);
  const [selectedCategory, setSelectedCategory] = React.useState<DocumentCategory | 'all'>('all');
  const fileInputRef = React.useRef<HTMLInputElement>(null);
  const { addNotification } = useNotifications();

  // 获取完整的文件 URL（处理相对路径）
  const getFullFileUrl = (url: string): string => {
    // 如果已经是完整 URL（包含协议），直接返回
    if (url.startsWith('http://') || url.startsWith('https://')) {
      return url;
    }
    
    // 如果是相对路径（/uploads/xxx），拼接后端 API 地址
    if (url.startsWith('/uploads/')) {
      const baseUrl = BACKEND_API_URL.replace('/api', '');
      return `${baseUrl}${url}`;
    }
    
    // 其他相对路径
    if (url.startsWith('/')) {
      const baseUrl = BACKEND_API_URL.replace('/api', '');
      return `${baseUrl}${url}`;
    }
    
    return url;
  };

  // 根据选择的分类过滤文件
  const filteredFiles = React.useMemo(() => {
    if (selectedCategory === 'all') return uploadingFiles;
    return uploadingFiles.filter(f => f.category === selectedCategory);
  }, [uploadingFiles, selectedCategory]);

  // 各分类的文件统计
  const categoryStats = React.useMemo(() => {
    const stats: Record<string, number> = { all: uploadingFiles.length };
    uploadingFiles.forEach(f => {
      stats[f.category] = (stats[f.category] || 0) + 1;
    });
    return stats;
  }, [uploadingFiles]);

  // 处理文件选择
  const handleFileSelect = (files: FileList | null) => {
    if (!files) return;

    const validFiles: UploadingFile[] = [];
    const maxSize = 5 * 1024 * 1024 * 1024; // 5GB

    for (let i = 0; i < files.length; i++) {
      const file = files[i];

      // 跳过图片文件
      if (file.type.startsWith('image/')) {
        toast.error(`${file.name} 是图片文件，请使用图片上传功能`);
        continue;
      }

      if (file.size > maxSize) {
        toast.error(`${file.name} 超过大小限制（最大 5GB）`);
        continue;
      }

      const category = getCategoryFromFileName(file.name);
      validFiles.push({
        id: `${Date.now()}_${i}`,
        file,
        category,
        progress: 0,
        status: 'pending',
      });
    }

    if (validFiles.length > 0) {
      setUploadingFiles(prev => [...prev, ...validFiles]);
    }
  };

  // 处理拖拽
  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(true);
  };

  const handleDragLeave = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(false);
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(false);
    handleFileSelect(e.dataTransfer.files);
  };

  // 移除文件
  const removeFile = (id: string) => {
    setUploadingFiles(prev => prev.filter(f => f.id !== id));
  };

  // 上传文件
  const uploadFiles = async () => {
    if (uploadingFiles.length === 0) return;

    setIsUploading(true);
    let successCount = 0;
    let failCount = 0;

    for (const uploadingFile of uploadingFiles) {
      if (uploadingFile.status !== 'pending') continue;

      // 更新状态为上传中
      setUploadingFiles(prev =>
        prev.map(f =>
          f.id === uploadingFile.id ? { ...f, status: 'uploading' as const, progress: 0 } : f
        )
      );

      try {
        const formData = new FormData();
        formData.append('file', uploadingFile.file);
        formData.append('fileName', uploadingFile.file.name);
        formData.append('category', uploadingFile.category); // 传递分类

        // 直接调用 Java 后端 API，添加 session
        const sessionId = getSessionId();
        const response = await fetch(`${BACKEND_API_URL}/documents/upload`, {
          method: 'POST',
          credentials: 'include',
          headers: {
            'X-Session-Id': sessionId || '',
          },
          body: formData,
        });

        const result = await response.json();

        if (result.success || result.code === 200) {
          // 更新状态为成功
          const url = result.data?.url || result.url;
          setUploadingFiles(prev =>
            prev.map(f =>
              f.id === uploadingFile.id
                ? { ...f, status: 'success' as const, progress: 100, url }
                : f
            )
          );
          successCount++;
        } else {
          // 更新状态为错误
          setUploadingFiles(prev =>
            prev.map(f =>
              f.id === uploadingFile.id
                ? { ...f, status: 'error' as const, error: result.message || '上传失败' }
                : f
            )
          );
          failCount++;
        }
      } catch (error) {
        console.error('[DocumentUpload] 上传失败:', error);
        setUploadingFiles(prev =>
          prev.map(f =>
            f.id === uploadingFile.id
              ? { ...f, status: 'error' as const, error: '网络错误' }
              : f
            )
        );
        failCount++;
      }
    }

    setIsUploading(false);

    // 关闭对话框并刷新列表
    if (successCount > 0) {
      console.log('[DocumentUpload] 上传成功，准备关闭对话框并刷新');
      
      // 创建上传成功通知
      addNotification({
        type: 'upload',
        title: '文档上传成功',
        message: `${successCount}个文档已成功上传${failCount > 0 ? `，${failCount}个失败` : ''}。`,
      });
      
      // 调用刷新回调
      onUploadSuccess?.();
    }

    if (failCount > 0 && successCount === 0) {
      toast.error(`${failCount}个文档上传失败`);
    }
  };

  // 复制链接
  const copyLink = (url: string) => {
    const fullUrl = getFullFileUrl(url);
    navigator.clipboard.writeText(fullUrl);
    toast.success('链接已复制');
  };

  // 下载文件
  const downloadFile = async (uploadingFile: UploadingFile) => {
    if (!uploadingFile.url) return;
    
    try {
      const fullUrl = getFullFileUrl(uploadingFile.url);
      const sessionId = getSessionId();
      console.log('[DocumentUploadDialog] 下载文件，完整URL:', fullUrl, 'sessionId:', sessionId ? sessionId.substring(0, 8) + '...' : 'null');
      
      const response = await fetch(fullUrl, {
        headers: {
          'X-Session-Id': sessionId || '',
        },
        credentials: 'include',
      });
      
      // 检查响应状态
      if (!response.ok) {
        const errorText = await response.text();
        console.error('[DocumentUploadDialog] 下载失败，HTTP状态:', response.status, '响应:', errorText);
        toast.error(`下载失败: ${response.status}`);
        return;
      }
      
      // 获取 Content-Type
      const contentType = response.headers.get('content-type') || '';
      
      // 如果响应是 JSON（错误信息），直接抛出错误
      if (contentType.includes('application/json') || contentType.includes('text/plain')) {
        const text = await response.text();
        console.error('[DocumentUploadDialog] 收到错误响应:', text);
        toast.error('下载失败: 文件不存在或路径错误');
        return;
      }
      
      const blob = await response.blob();
      const blobUrl = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = blobUrl;
      link.download = uploadingFile.file.name;
      link.click();
      window.URL.revokeObjectURL(blobUrl);
    } catch (error) {
      console.error('下载失败:', error);
      toast.error('下载失败');
    }
  };

  // 关闭时清理
  const handleClose = () => {
    if (!isUploading) {
      setUploadingFiles([]);
      setPreviewFile(null);
      setSelectedCategory('all');
      onOpenChange(false);
    }
  };

  // 检查文件是否可预览
  const isPreviewable = (uploadingFile: UploadingFile): boolean => {
    return uploadingFile.category === 'pdf';
  };

  return (
    <>
      <Dialog open={open} onOpenChange={handleClose}>
        <DialogContent className="sm:max-w-[700px] max-h-[85vh] overflow-hidden flex flex-col">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <FolderOpen className="w-5 h-5 text-emerald-600" />
              文档中心
            </DialogTitle>
          </DialogHeader>

          {/* 分类标签 */}
          <div className="flex items-center gap-2 flex-wrap">
            <Button
              variant={selectedCategory === 'all' ? 'default' : 'outline'}
              size="sm"
              onClick={() => setSelectedCategory('all')}
              className={cn(
                "h-8 text-xs",
                selectedCategory === 'all' ? "bg-emerald-600 hover:bg-emerald-700" : ""
              )}
            >
              全部 ({categoryStats.all || 0})
            </Button>
            {Object.entries(DOCUMENT_CATEGORIES).map(([key, config]) => {
              const Icon = config.icon;
              const count = categoryStats[key] || 0;
              if (count === 0 && selectedCategory !== key) return null; // 隐藏空分类
              return (
                <Button
                  key={key}
                  variant={selectedCategory === key ? 'default' : 'outline'}
                  size="sm"
                  onClick={() => setSelectedCategory(key as DocumentCategory)}
                  className={cn(
                    "h-8 text-xs gap-1",
                    selectedCategory === key ? "bg-emerald-600 hover:bg-emerald-700" : ""
                  )}
                >
                  <Icon className="w-3 h-3" />
                  {config.label} ({count})
                </Button>
              );
            })}
          </div>

          <div className="flex-1 overflow-y-auto space-y-4">
            {/* 拖拽区域 */}
            <div
              className={cn(
                'border-2 border-dashed rounded-xl p-6 text-center transition-all cursor-pointer',
                isDragging
                  ? 'border-emerald-500 bg-emerald-50'
                  : 'border-slate-200 hover:border-emerald-300 hover:bg-slate-50'
              )}
              onDragOver={handleDragOver}
              onDragLeave={handleDragLeave}
              onDrop={handleDrop}
              onClick={() => fileInputRef.current?.click()}
            >
              <input
                ref={fileInputRef}
                type="file"
                multiple
                className="hidden"
                onChange={e => handleFileSelect(e.target.files)}
              />
              <div className="flex flex-col items-center gap-3">
                <div className="w-14 h-14 rounded-full bg-emerald-100 flex items-center justify-center">
                  <Upload className="w-7 h-7 text-emerald-600" />
                </div>
                <div>
                  <p className="text-base font-medium text-slate-700">
                    拖拽文档到这里上传
                  </p>
                  <p className="text-sm text-slate-500 mt-1">
                    或点击选择文件
                  </p>
                </div>
                <p className="text-xs text-slate-400">
                  支持 PDF、Word、Excel、PPT、ZIP 等格式，单个最大 5GB
                </p>
              </div>
            </div>

            {/* 文件列表 */}
            {filteredFiles.length > 0 && (
              <div className="space-y-2 max-h-[350px] overflow-y-auto">
                {filteredFiles.map(file => {
                  const Icon = getFileIcon(file.file.name);
                  const categoryConfig = DOCUMENT_CATEGORIES[file.category];
                  
                  return (
                    <div
                      key={file.id}
                      className="flex items-center gap-3 p-3 bg-slate-50 rounded-lg hover:bg-slate-100 transition-colors"
                    >
                      <div className="w-10 h-10 rounded-lg bg-emerald-100 flex items-center justify-center flex-shrink-0">
                        <Icon className="w-5 h-5 text-emerald-600" />
                      </div>
                      
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2">
                          <p className="text-sm font-medium text-slate-700 truncate flex-1">
                            {file.file.name}
                          </p>
                          <span className={cn(
                            "px-1.5 py-0.5 text-xs rounded flex-shrink-0",
                            categoryConfig.label === 'PDF文档' ? 'bg-red-100 text-red-600' :
                            categoryConfig.label === 'Word文档' ? 'bg-blue-100 text-blue-600' :
                            categoryConfig.label === 'Excel表格' ? 'bg-green-100 text-green-600' :
                            categoryConfig.label === 'PPT演示' ? 'bg-orange-100 text-orange-600' :
                            categoryConfig.label === '压缩文件' ? 'bg-purple-100 text-purple-600' :
                            'bg-slate-100 text-slate-600'
                          )}>
                            {categoryConfig.label}
                          </span>
                        </div>
                        <div className="flex items-center gap-2 mt-0.5">
                          <span className="text-xs text-slate-500">
                            {formatFileSize(file.file.size)}
                          </span>
                          
                          {file.status === 'uploading' && (
                            <span className="text-xs text-emerald-600">
                              {file.progress}%
                            </span>
                          )}
                          
                          {file.status === 'success' && (
                            <span className="flex items-center gap-1 text-xs text-green-600">
                              <CheckCircle2 className="w-3 h-3" />
                              已上传
                            </span>
                          )}
                          
                          {file.status === 'error' && (
                            <span className="flex items-center gap-1 text-xs text-red-500">
                              <AlertCircle className="w-3 h-3" />
                              {file.error}
                            </span>
                          )}
                        </div>
                        
                        {/* 进度条 */}
                        {file.status === 'uploading' && (
                          <div className="mt-1.5 h-1 bg-slate-200 rounded-full overflow-hidden">
                            <div
                              className="h-full bg-emerald-500 transition-all duration-300"
                              style={{ width: `${file.progress}%` }}
                            />
                          </div>
                        )}
                      </div>

                      <div className="flex items-center gap-1 flex-shrink-0">
                        {file.status === 'success' && (
                          <>
                            {isPreviewable(file) && (
                              <button
                                onClick={() => setPreviewFile(file)}
                                className="p-1.5 rounded-lg hover:bg-slate-200 text-slate-500"
                                title="预览"
                              >
                                <Eye className="w-4 h-4" />
                              </button>
                            )}
                            {file.url && (
                              <>
                                <button
                                  onClick={() => copyLink(file.url!)}
                                  className="p-1.5 rounded-lg hover:bg-slate-200 text-slate-500"
                                  title="复制链接"
                                >
                                  <Copy className="w-4 h-4" />
                                </button>
                                <button
                                  onClick={() => downloadFile(file)}
                                  className="p-1.5 rounded-lg hover:bg-slate-200 text-slate-500"
                                  title="下载"
                                >
                                  <Download className="w-4 h-4" />
                                </button>
                              </>
                            )}
                          </>
                        )}
                        
                        {(file.status === 'pending' || file.status === 'uploading') && (
                          <button
                            onClick={() => removeFile(file.id)}
                            className="p-1.5 rounded-lg hover:bg-red-50 text-slate-500 hover:text-red-500"
                            title="移除"
                          >
                            <X className="w-4 h-4" />
                          </button>
                        )}
                        
                        {file.status === 'error' && (
                          <button
                            onClick={() => removeFile(file.id)}
                            className="p-1.5 rounded-lg hover:bg-slate-200 text-slate-500"
                            title="移除"
                          >
                            <X className="w-4 h-4" />
                          </button>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>

          {/* 底部按钮 */}
          <div className="flex items-center justify-between pt-4 border-t">
            <p className="text-sm text-slate-500">
              {uploadingFiles.filter(f => f.status === 'pending').length} 个文件待上传
              {selectedCategory !== 'all' && ` (${filteredFiles.length} 个已选中)`}
            </p>
            <div className="flex gap-2">
              <Button
                variant="outline"
                onClick={handleClose}
                disabled={isUploading}
              >
                取消
              </Button>
              <Button
                onClick={uploadFiles}
                disabled={filteredFiles.filter(f => f.status === 'pending').length === 0 || isUploading}
                className="bg-emerald-600 hover:bg-emerald-700"
              >
                {isUploading ? (
                  <>
                    <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                    上传中...
                  </>
                ) : (
                  <>
                    <Upload className="w-4 h-4 mr-2" />
                    开始上传
                  </>
                )}
              </Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>

      {/* 预览对话框 */}
      {previewFile && (
        <div
          className="fixed inset-0 z-50 bg-black/80 flex items-center justify-center p-4"
          onClick={() => setPreviewFile(null)}
        >
          <div
            className="relative max-w-5xl max-h-full bg-white rounded-2xl overflow-hidden"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between p-4 border-b border-slate-200">
              <p className="text-sm font-medium text-slate-700 truncate">
                {previewFile.file.name}
              </p>
              <button
                onClick={() => setPreviewFile(null)}
                className="p-2 rounded-lg hover:bg-slate-100 text-slate-500"
              >
                <X className="w-5 h-5" />
              </button>
            </div>
            
            <div className="p-4 max-h-[calc(100vh-200px)] overflow-auto bg-slate-50">
              {previewFile.url && (
                <iframe
                  src={getFullFileUrl(previewFile.url)}
                  className="w-full h-[calc(100vh-280px)] rounded-lg"
                />
              )}
            </div>
          </div>
        </div>
      )}
    </>
  );
}
