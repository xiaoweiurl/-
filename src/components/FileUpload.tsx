'use client';

import React, { useState, useCallback, useRef, useEffect } from 'react';
import { 
  Upload, 
  X, 
  File, 
  FileText, 
  Film, 
  Music, 
  Image,
  ImageIcon,
  FileIcon,
  CheckCircle2,
  AlertCircle,
  Loader2,
  Eye,
  Trash2,
  Download,
  Copy,
  FolderOpen
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import { toast } from 'sonner';
import { getSessionId } from '@/lib/auth-client';

interface UploadedFile {
  id: string;
  name: string;
  size: number;
  type: string;
  category: 'image' | 'document';
  url?: string;
  status: 'pending' | 'uploading' | 'completed' | 'failed';
  progress: number;
  error?: string;
  createdAt: string;
}

// 图片类型
const IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/gif', 'image/webp', 'image/bmp', 'image/svg+xml'];
// 文档类型
const DOCUMENT_TYPES = [
  'application/pdf',
  'application/msword',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  'application/vnd.ms-excel',
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  'application/vnd.ms-powerpoint',
  'application/vnd.openxmlformats-officedocument.presentationml.presentation',
  'text/plain',
  'text/html',
  'text/css',
  'text/javascript',
  'application/json',
  'application/xml',
  'application/zip',
  'application/x-rar-compressed',
  'application/x-7z-compressed',
];

// 判断文件类型
const getFileCategory = (type: string): 'image' | 'document' => {
  if (IMAGE_TYPES.includes(type) || type.startsWith('image/')) {
    return 'image';
  }
  return 'document';
};

// 获取文件图标
const getFileIcon = (type: string) => {
  if (type.startsWith('image/')) return Image;
  if (type.startsWith('video/')) return Film;
  if (type.startsWith('audio/')) return Music;
  if (type.includes('pdf') || type.includes('document') || type.includes('word')) return FileText;
  if (type.includes('zip') || type.includes('rar') || type.includes('7z') || type.includes('tar')) return File;
  return FileIcon;
};

// 格式化文件大小
const formatFileSize = (bytes: number): string => {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
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

// 判断文件类型是否可预览
const isPreviewable = (type: string): boolean => {
  return type.startsWith('image/') || 
         type.startsWith('video/') || 
         type.startsWith('audio/') ||
         type.includes('pdf');
};

interface FileUploadProps {
  onFilesUploaded?: (files: UploadedFile[]) => void;
  maxImageSize?: number; // 图片默认 100MB
  maxDocumentSize?: number; // 文档默认 5GB
  maxFiles?: number;
  className?: string;
}

export default function FileUpload({
  onFilesUploaded,
  maxImageSize = 100 * 1024 * 1024, // 100MB
  maxDocumentSize = 5 * 1024 * 1024 * 1024, // 5GB
  maxFiles = 100,
  className
}: FileUploadProps) {
  const [isDraggingImage, setIsDraggingImage] = useState(false);
  const [isDraggingDocument, setIsDraggingDocument] = useState(false);
  const [uploadingFiles, setUploadingFiles] = useState<UploadedFile[]>([]);
  const [uploadHistory, setUploadHistory] = useState<UploadedFile[]>([]);
  const [viewingFile, setViewingFile] = useState<UploadedFile | null>(null);
  const [activeTab, setActiveTab] = useState<'upload' | 'history'>('upload');
  const imageInputRef = useRef<HTMLInputElement>(null);
  const documentInputRef = useRef<HTMLInputElement>(null);
  const abortControllersRef = useRef<Map<string, AbortController>>(new Map());

  // 获取后端 API 地址
  const getApiUrl = () => '';

  // 加载上传历史
  useEffect(() => {
    const savedHistory = localStorage.getItem('file_upload_history');
    if (savedHistory) {
      try {
        const history = JSON.parse(savedHistory);
        setUploadHistory(history);
      } catch (e) {
        console.error('加载上传历史失败:', e);
      }
    }
  }, []);

  // 保存上传历史
  const saveUploadHistory = useCallback((files: UploadedFile[]) => {
    const updated = [...files, ...uploadHistory].slice(0, 100);
    setUploadHistory(updated);
    localStorage.setItem('file_upload_history', JSON.stringify(updated));
  }, [uploadHistory]);

  // 上传图片 - 调用 Java 后端 API
  const uploadImage = useCallback(async (file: File, fileId: string) => {
    const controller = new AbortController();
    abortControllersRef.current.set(fileId, controller);

    // 验证文件大小
    if (file.size > maxImageSize) {
      setUploadingFiles(prev => prev.map(f => 
        f.id === fileId 
          ? { ...f, status: 'failed', error: `图片大小超过限制 (${formatFileSize(maxImageSize)})` }
          : f
      ));
      return;
    }

    setUploadingFiles(prev => prev.map(f => 
      f.id === fileId ? { ...f, status: 'uploading', progress: 0 } : f
    ));

    try {
      const formData = new FormData();
      formData.append('file', file);
      formData.append('enableAI', 'true');

      await new Promise<void>((resolve, reject) => {
        const xhr = new XMLHttpRequest();
        
        xhr.upload.onprogress = (e) => {
          if (e.lengthComputable) {
            const progress = Math.round((e.loaded / e.total) * 100);
            setUploadingFiles(prev => prev.map(f => 
              f.id === fileId ? { ...f, progress } : f
            ));
          }
        };

        xhr.onload = () => {
          if (xhr.status >= 200 && xhr.status < 300) {
            try {
              const response = JSON.parse(xhr.responseText);
              if (response.success || response.code === 200) {
                const uploadedFile: UploadedFile = {
                  id: fileId,
                  name: file.name,
                  size: file.size,
                  type: file.type,
                  category: 'image',
                  url: response.data?.url || response.data?.[0]?.url,
                  status: 'completed',
                  progress: 100,
                  createdAt: new Date().toISOString()
                };
                
                setUploadingFiles(prev => prev.map(f => 
                  f.id === fileId ? uploadedFile : f
                ));
                
                saveUploadHistory([uploadedFile]);
                onFilesUploaded?.([uploadedFile]);
              } else {
                reject(new Error(response.message || '图片上传失败'));
              }
            } catch {
              reject(new Error('解析响应失败'));
            }
          } else {
            reject(new Error(`HTTP ${xhr.status}`));
          }
        };

        xhr.onerror = () => reject(new Error('网络错误'));
        xhr.onabort = () => reject(new Error('上传已取消'));

        // 调用图片上传 API，添加 session
        const sessionId = getSessionId();
        xhr.open('POST', `${getApiUrl()}/api/images/upload`);
        xhr.setRequestHeader('Accept', 'application/json');
        xhr.setRequestHeader('X-Session-Id', sessionId || '');
        xhr.withCredentials = true;
        xhr.send(formData);

        controller.signal.addEventListener('abort', () => xhr.abort());
      });

    } catch (error: any) {
      if (error.name === 'AbortError') {
        setUploadingFiles(prev => prev.map(f => 
          f.id === fileId ? { ...f, status: 'failed', error: '上传已取消' } : f
        ));
      } else {
        setUploadingFiles(prev => prev.map(f => 
          f.id === fileId ? { ...f, status: 'failed', error: error.message || '上传失败' } : f
        ));
      }
    } finally {
      abortControllersRef.current.delete(fileId);
    }
  }, [maxImageSize, saveUploadHistory, onFilesUploaded]);

  // 上传文档 - 调用 Java 后端 API
  const uploadDocument = useCallback(async (file: File, fileId: string) => {
    const controller = new AbortController();
    abortControllersRef.current.set(fileId, controller);

    // 验证文件大小
    if (file.size > maxDocumentSize) {
      setUploadingFiles(prev => prev.map(f => 
        f.id === fileId 
          ? { ...f, status: 'failed', error: `文件大小超过限制 (${formatFileSize(maxDocumentSize)})` }
          : f
      ));
      return;
    }

    setUploadingFiles(prev => prev.map(f => 
      f.id === fileId ? { ...f, status: 'uploading', progress: 0 } : f
    ));

    try {
      const formData = new FormData();
      formData.append('file', file);
      formData.append('fileName', file.name);

      await new Promise<void>((resolve, reject) => {
        const xhr = new XMLHttpRequest();
        
        xhr.upload.onprogress = (e) => {
          if (e.lengthComputable) {
            const progress = Math.round((e.loaded / e.total) * 100);
            setUploadingFiles(prev => prev.map(f => 
              f.id === fileId ? { ...f, progress } : f
            ));
          }
        };

        xhr.onload = () => {
          if (xhr.status >= 200 && xhr.status < 300) {
            try {
              const response = JSON.parse(xhr.responseText);
              if (response.success || response.code === 200) {
                const uploadedFile: UploadedFile = {
                  id: fileId,
                  name: file.name,
                  size: file.size,
                  type: file.type,
                  category: 'document',
                  url: response.data?.url || response.url,
                  status: 'completed',
                  progress: 100,
                  createdAt: new Date().toISOString()
                };
                
                setUploadingFiles(prev => prev.map(f => 
                  f.id === fileId ? uploadedFile : f
                ));
                
                saveUploadHistory([uploadedFile]);
                onFilesUploaded?.([uploadedFile]);
              } else {
                reject(new Error(response.message || '文档上传失败'));
              }
            } catch {
              reject(new Error('解析响应失败'));
            }
          } else {
            reject(new Error(`HTTP ${xhr.status}`));
          }
        };

        xhr.onerror = () => reject(new Error('网络错误'));
        xhr.onabort = () => reject(new Error('上传已取消'));

        // 调用文档上传 API，添加 session
        const sessionId = getSessionId();
        xhr.open('POST', `${getApiUrl()}/api/documents/upload`);
        xhr.setRequestHeader('Accept', 'application/json');
        xhr.setRequestHeader('X-Session-Id', sessionId || '');
        xhr.withCredentials = true;
        xhr.send(formData);

        controller.signal.addEventListener('abort', () => xhr.abort());
      });

    } catch (error: any) {
      if (error.name === 'AbortError') {
        setUploadingFiles(prev => prev.map(f => 
          f.id === fileId ? { ...f, status: 'failed', error: '上传已取消' } : f
        ));
      } else {
        setUploadingFiles(prev => prev.map(f => 
          f.id === fileId ? { ...f, status: 'failed', error: error.message || '上传失败' } : f
        ));
      }
    } finally {
      abortControllersRef.current.delete(fileId);
    }
  }, [maxDocumentSize, saveUploadHistory, onFilesUploaded]);

  // 处理文件上传
  const uploadFile = useCallback(async (file: File, fileId: string) => {
    const category = getFileCategory(file.type);
    if (category === 'image') {
      await uploadImage(file, fileId);
    } else {
      await uploadDocument(file, fileId);
    }
  }, [uploadImage, uploadDocument]);

  // 处理文件选择
  const handleFiles = useCallback((files: FileList | File[]) => {
    const fileArray = Array.from(files);
    const newFiles: UploadedFile[] = fileArray.slice(0, maxFiles).map(file => ({
      id: `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
      name: file.name,
      size: file.size,
      type: file.type,
      category: getFileCategory(file.type),
      status: 'pending' as const,
      progress: 0,
      createdAt: new Date().toISOString()
    }));

    setUploadingFiles(prev => [...prev, ...newFiles]);

    newFiles.forEach(file => {
      const originalFile = fileArray.find(f => f.name === file.name);
      if (originalFile) {
        uploadFile(originalFile, file.id);
      }
    });
  }, [maxFiles, uploadFile]);

  // 图片拖拽处理
  const handleImageDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    setIsDraggingImage(true);
  }, []);

  const handleImageDragLeave = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    setIsDraggingImage(false);
  }, []);

  const handleImageDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    setIsDraggingImage(false);
    
    const files = Array.from(e.dataTransfer.files).filter(file => {
      const category = getFileCategory(file.type);
      return category === 'image';
    });
    
    if (files.length > 0) {
      const dataTransfer = new DataTransfer();
      files.forEach(f => dataTransfer.items.add(f));
      handleFiles(dataTransfer.files);
    }
  }, [handleFiles]);

  // 文档拖拽处理
  const handleDocumentDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    setIsDraggingDocument(true);
  }, []);

  const handleDocumentDragLeave = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    setIsDraggingDocument(false);
  }, []);

  const handleDocumentDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    setIsDraggingDocument(false);
    
    const files = Array.from(e.dataTransfer.files).filter(file => {
      const category = getFileCategory(file.type);
      return category === 'document';
    });
    
    if (files.length > 0) {
      const dataTransfer = new DataTransfer();
      files.forEach(f => dataTransfer.items.add(f));
      handleFiles(dataTransfer.files);
    }
  }, [handleFiles]);

  // 删除文件
  const handleDelete = useCallback((fileId: string) => {
    const controller = abortControllersRef.current.get(fileId);
    if (controller) {
      controller.abort();
    }
    setUploadingFiles(prev => prev.filter(f => f.id !== fileId));
  }, []);

  // 清空已完成上传
  const handleClearCompleted = useCallback(() => {
    const completed = uploadingFiles.filter(f => f.status === 'completed');
    if (completed.length > 0) {
      saveUploadHistory(completed);
    }
    setUploadingFiles(prev => prev.filter(f => f.status !== 'completed'));
  }, [uploadingFiles, saveUploadHistory]);

  // 取消全部上传
  const handleCancelAll = useCallback(() => {
    abortControllersRef.current.forEach(controller => controller.abort());
    setUploadingFiles([]);
  }, []);

  // 获取完整的文件 URL（处理相对路径）
  const getFullFileUrl = useCallback((url: string): string => {
    // 如果已经是完整 URL（包含协议），直接返回
    if (url.startsWith('http://') || url.startsWith('https://')) {
      return url;
    }
    
    // 如果是相对路径（/uploads/xxx），拼接后端 API 地址（去掉 /api 后缀）
    if (url.startsWith('/uploads/')) {
      const backendUrl = (process.env.NEXT_PUBLIC_BACKEND_API_URL || 'http://localhost:8080').replace(/\/api$/, '');
      return `${backendUrl}${url}`;
    }
    
    // 其他相对路径
    if (url.startsWith('/')) {
      const backendUrl = (process.env.NEXT_PUBLIC_BACKEND_API_URL || 'http://localhost:8080').replace(/\/api$/, '');
      return `${backendUrl}${url}`;
    }
    
    return url;
  }, []);

  // 预览文件
  const handlePreview = useCallback((file: UploadedFile) => {
    setViewingFile(file);
  }, []);

  // 下载文件
  const handleDownload = useCallback(async (file: UploadedFile) => {
    if (!file.url) return;
    
    try {
      const fullUrl = getFullFileUrl(file.url);
      const sessionId = getSessionId();
      console.log('[FileUpload] 下载文件，完整URL:', fullUrl, 'sessionId:', sessionId ? sessionId.substring(0, 8) + '...' : 'null');
      
      const response = await fetch(fullUrl, {
        headers: {
          'X-Session-Id': sessionId || '',
        },
        credentials: 'include',
      });
      
      // 检查响应状态
      if (!response.ok) {
        const errorText = await response.text();
        console.error('[FileUpload] 下载失败，HTTP状态:', response.status, '响应:', errorText);
        toast.error(`下载失败: ${response.status}`);
        return;
      }
      
      // 获取 Content-Type
      const contentType = response.headers.get('content-type') || '';
      
      // 如果响应是 JSON（错误信息），直接抛出错误
      if (contentType.includes('application/json') || contentType.includes('text/plain')) {
        const text = await response.text();
        console.error('[FileUpload] 收到错误响应:', text);
        toast.error('下载失败: 文件不存在或路径错误');
        return;
      }
      
      const blob = await response.blob();
      const blobUrl = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = blobUrl;
      link.download = file.name;
      link.click();
      window.URL.revokeObjectURL(blobUrl);
    } catch (error) {
      console.error('下载失败:', error);
    }
  }, []);

  // 复制链接
  const handleCopyLink = useCallback((file: UploadedFile) => {
    if (file.url) {
      const fullUrl = getFullFileUrl(file.url);
      navigator.clipboard.writeText(fullUrl);
    }
  }, [getFullFileUrl]);

  const completedCount = uploadingFiles.filter(f => f.status === 'completed').length;
  const failedCount = uploadingFiles.filter(f => f.status === 'failed').length;
  const imageCount = uploadingFiles.filter(f => f.category === 'image').length;
  const documentCount = uploadingFiles.filter(f => f.category === 'document').length;
  const totalProgress = uploadingFiles.length > 0
    ? Math.round(uploadingFiles.reduce((acc, f) => acc + f.progress, 0) / uploadingFiles.length)
    : 0;

  // 过滤历史记录
  const imageHistory = uploadHistory.filter(f => f.category === 'image');
  const documentHistory = uploadHistory.filter(f => f.category === 'document');

  return (
    <>
      <div className={cn("flex flex-col h-full", className)}>
        {/* 标签页 */}
        <div className="flex border-b border-slate-200 px-4">
          <button
            onClick={() => setActiveTab('upload')}
            className={cn(
              'px-4 py-3 text-sm font-medium border-b-2 transition-colors',
              activeTab === 'upload'
                ? 'border-violet-500 text-violet-600'
                : 'border-transparent text-slate-500 hover:text-slate-700'
            )}
          >
            上传文件
          </button>
          <button
            onClick={() => setActiveTab('history')}
            className={cn(
              'px-4 py-3 text-sm font-medium border-b-2 transition-colors',
              activeTab === 'history'
                ? 'border-violet-500 text-violet-600'
                : 'border-transparent text-slate-500 hover:text-slate-700'
            )}
          >
            上传历史 ({uploadHistory.length})
          </button>
        </div>

        {activeTab === 'upload' ? (
          <div className="flex-1 overflow-auto p-4 space-y-6">
            {/* 图片上传区域 */}
            <div>
              <div className="flex items-center gap-2 mb-3">
                <ImageIcon className="w-5 h-5 text-violet-500" />
                <h3 className="text-sm font-medium text-slate-700">上传图片</h3>
                <span className="text-xs text-slate-400">（支持 JPG, PNG, GIF, WebP 等）</span>
              </div>
              <div
                onDragOver={handleImageDragOver}
                onDragLeave={handleImageDragLeave}
                onDrop={handleImageDrop}
                onClick={() => imageInputRef.current?.click()}
                className={cn(
                  'border-2 border-dashed rounded-xl p-6 text-center cursor-pointer transition-all',
                  isDraggingImage
                    ? 'border-violet-500 bg-violet-50'
                    : 'border-slate-300 hover:border-violet-400 hover:bg-slate-50'
                )}
              >
                <input
                  ref={imageInputRef}
                  type="file"
                  multiple
                  accept="image/*"
                  onChange={(e) => e.target.files && handleFiles(e.target.files)}
                  className="hidden"
                />
                <Image className={cn(
                  'w-10 h-10 mx-auto mb-3 transition-colors',
                  isDraggingImage ? 'text-violet-500' : 'text-slate-400'
                )} />
                <p className="text-sm font-medium text-slate-700 mb-1">
                  {isDraggingImage ? '松开以上传图片' : '拖拽图片到此处，或点击选择'}
                </p>
                <p className="text-xs text-slate-500">
                  最大 {formatFileSize(maxImageSize)}
                </p>
              </div>
            </div>

            {/* 文档上传区域 */}
            <div>
              <div className="flex items-center gap-2 mb-3">
                <FileIcon className="w-5 h-5 text-emerald-500" />
                <h3 className="text-sm font-medium text-slate-700">上传文档</h3>
                <span className="text-xs text-slate-400">（支持 PDF, Word, Excel, PPT, ZIP 等）</span>
              </div>
              <div
                onDragOver={handleDocumentDragOver}
                onDragLeave={handleDocumentDragLeave}
                onDrop={handleDocumentDrop}
                onClick={() => documentInputRef.current?.click()}
                className={cn(
                  'border-2 border-dashed rounded-xl p-6 text-center cursor-pointer transition-all',
                  isDraggingDocument
                    ? 'border-emerald-500 bg-emerald-50'
                    : 'border-slate-300 hover:border-emerald-400 hover:bg-slate-50'
                )}
              >
                <input
                  ref={documentInputRef}
                  type="file"
                  multiple
                  onChange={(e) => e.target.files && handleFiles(e.target.files)}
                  className="hidden"
                />
                <File className={cn(
                  'w-10 h-10 mx-auto mb-3 transition-colors',
                  isDraggingDocument ? 'text-emerald-500' : 'text-slate-400'
                )} />
                <p className="text-sm font-medium text-slate-700 mb-1">
                  {isDraggingDocument ? '松开以上传文档' : '拖拽文档到此处，或点击选择'}
                </p>
                <p className="text-xs text-slate-500">
                  最大 {formatFileSize(maxDocumentSize)}
                </p>
              </div>
            </div>

            {/* 上传进度 */}
            {uploadingFiles.length > 0 && (
              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    {totalProgress > 0 && totalProgress < 100 && (
                      <Loader2 className="w-4 h-4 animate-spin text-violet-500" />
                    )}
                    <span className="text-sm font-medium text-slate-700">
                      {completedCount}/{uploadingFiles.length} 个文件已完成
                      {totalProgress > 0 && ` (${totalProgress}%)`}
                    </span>
                    {imageCount > 0 && (
                      <span className="text-xs text-violet-500">({imageCount} 图片)</span>
                    )}
                    {documentCount > 0 && (
                      <span className="text-xs text-emerald-500">({documentCount} 文档)</span>
                    )}
                  </div>
                  <div className="flex gap-2">
                    {failedCount > 0 && (
                      <span className="text-sm text-red-500">{failedCount} 个失败</span>
                    )}
                    {completedCount > 0 && (
                      <button
                        onClick={handleClearCompleted}
                        className="text-sm text-slate-500 hover:text-slate-700"
                      >
                        清空已完成
                      </button>
                    )}
                    <button
                      onClick={handleCancelAll}
                      className="text-sm text-slate-500 hover:text-red-500"
                    >
                      取消全部
                    </button>
                  </div>
                </div>

                {/* 文件列表 */}
                <div className="space-y-2 max-h-80 overflow-auto">
                  {uploadingFiles.map((file) => {
                    const FileIcon = getFileIcon(file.type);
                    const previewable = isPreviewable(file.type);
                    
                    return (
                      <div
                        key={file.id}
                        className="flex items-center gap-3 p-3 bg-white rounded-xl border border-slate-200"
                      >
                        <div className={cn(
                          'flex-shrink-0 w-10 h-10 rounded-lg flex items-center justify-center',
                          file.category === 'image' ? 'bg-violet-100' : 'bg-emerald-100'
                        )}>
                          <FileIcon className={cn(
                            'w-5 h-5',
                            file.category === 'image' ? 'text-violet-500' : 'text-emerald-500'
                          )} />
                        </div>
                        
                        <div className="flex-1 min-w-0">
                          <p className="text-sm font-medium text-slate-700 truncate">
                            {file.name}
                          </p>
                          <div className="flex items-center gap-2 mt-0.5">
                            <span className="text-xs text-slate-500">
                              {formatFileSize(file.size)}
                            </span>
                            <span className={cn(
                              'text-xs px-1.5 py-0.5 rounded',
                              file.category === 'image' ? 'bg-violet-100 text-violet-600' : 'bg-emerald-100 text-emerald-600'
                            )}>
                              {file.category === 'image' ? '图片' : '文档'}
                            </span>
                            {file.status === 'uploading' && (
                              <span className="text-xs text-violet-500">
                                {file.progress}%
                              </span>
                            )}
                            {file.status === 'failed' && (
                              <span className="text-xs text-red-500">
                                {file.error}
                              </span>
                            )}
                            {file.status === 'completed' && (
                              <span className="text-xs text-green-500">已完成</span>
                            )}
                          </div>
                          
                          {file.status === 'uploading' && (
                            <div className="mt-1.5 h-1 bg-slate-100 rounded-full overflow-hidden">
                              <div
                                className={cn(
                                  'h-full transition-all duration-300',
                                  file.category === 'image' ? 'bg-violet-500' : 'bg-emerald-500'
                                )}
                                style={{ width: `${file.progress}%` }}
                              />
                            </div>
                          )}
                        </div>

                        <div className="flex-shrink-0 flex items-center gap-1">
                          {file.status === 'completed' && previewable && (
                            <button
                              onClick={() => handlePreview(file)}
                              className="p-1.5 rounded-lg hover:bg-slate-100 text-slate-500"
                              title="预览"
                            >
                              <Eye className="w-4 h-4" />
                            </button>
                          )}
                          {file.status === 'completed' && file.url && (
                            <>
                              <button
                                onClick={() => handleCopyLink(file)}
                                className="p-1.5 rounded-lg hover:bg-slate-100 text-slate-500"
                                title="复制链接"
                              >
                                <Copy className="w-4 h-4" />
                              </button>
                              <button
                                onClick={() => handleDownload(file)}
                                className="p-1.5 rounded-lg hover:bg-slate-100 text-slate-500"
                                title="下载"
                              >
                                <Download className="w-4 h-4" />
                              </button>
                            </>
                          )}
                          {file.status !== 'completed' && (
                            <button
                              onClick={() => handleDelete(file.id)}
                              className="p-1.5 rounded-lg hover:bg-red-50 text-slate-500 hover:text-red-500"
                              title="取消"
                            >
                              <X className="w-4 h-4" />
                            </button>
                          )}
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            )}
          </div>
        ) : (
          /* 上传历史 */
          <div className="flex-1 overflow-auto p-4 space-y-6">
            {/* 图片历史 */}
            {imageHistory.length > 0 && (
              <div>
                <div className="flex items-center gap-2 mb-3">
                  <ImageIcon className="w-5 h-5 text-violet-500" />
                  <h3 className="text-sm font-medium text-slate-700">图片 ({imageHistory.length})</h3>
                </div>
                <div className="space-y-2">
                  {imageHistory.slice(0, 10).map((file) => {
                    return (
                      <div
                        key={file.id}
                        className="flex items-center gap-3 p-3 bg-white rounded-xl border border-slate-200 hover:border-slate-300 transition-colors"
                      >
                        <div className="flex-shrink-0 w-10 h-10 rounded-lg flex items-center justify-center bg-violet-100">
                          <ImageIcon className="w-5 h-5 text-violet-500" />
                        </div>
                        
                        <div className="flex-1 min-w-0">
                          <p className="text-sm font-medium text-slate-700 truncate">
                            {file.name}
                          </p>
                          <div className="flex items-center gap-2 mt-0.5">
                            <span className="text-xs text-slate-500">
                              {formatFileSize(file.size)}
                            </span>
                            <span className="text-xs text-slate-400">·</span>
                            <span className="text-xs text-slate-500">
                              {formatDate(file.createdAt)}
                            </span>
                          </div>
                        </div>

                        <div className="flex-shrink-0 flex items-center gap-1">
                          <button
                            onClick={() => handlePreview(file)}
                            className="p-1.5 rounded-lg hover:bg-slate-100 text-slate-500"
                            title="预览"
                          >
                            <Eye className="w-4 h-4" />
                          </button>
                          {file.url && (
                            <>
                              <button
                                onClick={() => handleCopyLink(file)}
                                className="p-1.5 rounded-lg hover:bg-slate-100 text-slate-500"
                                title="复制链接"
                              >
                                <Copy className="w-4 h-4" />
                              </button>
                              <button
                                onClick={() => handleDownload(file)}
                                className="p-1.5 rounded-lg hover:bg-slate-100 text-slate-500"
                                title="下载"
                              >
                                <Download className="w-4 h-4" />
                              </button>
                            </>
                          )}
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            )}

            {/* 文档历史 */}
            {documentHistory.length > 0 && (
              <div>
                <div className="flex items-center gap-2 mb-3">
                  <FileIcon className="w-5 h-5 text-emerald-500" />
                  <h3 className="text-sm font-medium text-slate-700">文档 ({documentHistory.length})</h3>
                </div>
                <div className="space-y-2">
                  {documentHistory.slice(0, 10).map((file) => {
                    const FileIcon = getFileIcon(file.type);
                    const previewable = isPreviewable(file.type);
                    
                    return (
                      <div
                        key={file.id}
                        className="flex items-center gap-3 p-3 bg-white rounded-xl border border-slate-200 hover:border-slate-300 transition-colors"
                      >
                        <div className="flex-shrink-0 w-10 h-10 rounded-lg flex items-center justify-center bg-emerald-100">
                          <FileIcon className="w-5 h-5 text-emerald-500" />
                        </div>
                        
                        <div className="flex-1 min-w-0">
                          <p className="text-sm font-medium text-slate-700 truncate">
                            {file.name}
                          </p>
                          <div className="flex items-center gap-2 mt-0.5">
                            <span className="text-xs text-slate-500">
                              {formatFileSize(file.size)}
                            </span>
                            <span className="text-xs text-slate-400">·</span>
                            <span className="text-xs text-slate-500">
                              {formatDate(file.createdAt)}
                            </span>
                          </div>
                        </div>

                        <div className="flex-shrink-0 flex items-center gap-1">
                          {previewable && (
                            <button
                              onClick={() => handlePreview(file)}
                              className="p-1.5 rounded-lg hover:bg-slate-100 text-slate-500"
                              title="预览"
                            >
                              <Eye className="w-4 h-4" />
                            </button>
                          )}
                          {file.url && (
                            <>
                              <button
                                onClick={() => handleCopyLink(file)}
                                className="p-1.5 rounded-lg hover:bg-slate-100 text-slate-500"
                                title="复制链接"
                              >
                                <Copy className="w-4 h-4" />
                              </button>
                              <button
                                onClick={() => handleDownload(file)}
                                className="p-1.5 rounded-lg hover:bg-slate-100 text-slate-500"
                                title="下载"
                              >
                                <Download className="w-4 h-4" />
                              </button>
                            </>
                          )}
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            )}

            {uploadHistory.length === 0 && (
              <div className="text-center py-12">
                <FolderOpen className="w-12 h-12 mx-auto text-slate-300 mb-4" />
                <p className="text-slate-500">暂无上传历史</p>
              </div>
            )}
          </div>
        )}
      </div>

      {/* 预览模态框 */}
      {viewingFile && (
        <div
          className="fixed inset-0 z-50 bg-black/80 flex items-center justify-center p-4"
          onClick={() => setViewingFile(null)}
        >
          <div
            className="relative max-w-5xl max-h-full bg-white rounded-2xl overflow-hidden"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between p-4 border-b border-slate-200">
              <div className="flex-1 min-w-0 mr-4">
                <p className="text-sm font-medium text-slate-700 truncate">
                  {viewingFile.name}
                </p>
                <p className="text-xs text-slate-500">
                  {formatFileSize(viewingFile.size)}
                </p>
              </div>
              <div className="flex items-center gap-2">
                {viewingFile.url && (
                  <>
                    <button
                      onClick={() => handleCopyLink(viewingFile)}
                      className="p-2 rounded-lg hover:bg-slate-100 text-slate-500"
                      title="复制链接"
                    >
                      <Copy className="w-5 h-5" />
                    </button>
                    <button
                      onClick={() => handleDownload(viewingFile)}
                      className="p-2 rounded-lg hover:bg-slate-100 text-slate-500"
                      title="下载"
                    >
                      <Download className="w-5 h-5" />
                    </button>
                  </>
                )}
                <button
                  onClick={() => setViewingFile(null)}
                  className="p-2 rounded-lg hover:bg-slate-100 text-slate-500"
                >
                  <X className="w-5 h-5" />
                </button>
              </div>
            </div>
            
            <div className="p-4 max-h-[calc(100vh-200px)] overflow-auto bg-slate-50">
              {viewingFile.type.startsWith('image/') && viewingFile.url && (
                <img
                  src={getFullFileUrl(viewingFile.url)}
                  alt={viewingFile.name}
                  className="max-w-full max-h-[calc(100vh-280px)] mx-auto rounded-lg"
                />
              )}
              {viewingFile.type.startsWith('video/') && viewingFile.url && (
                <video
                  src={getFullFileUrl(viewingFile.url)}
                  controls
                  className="max-w-full max-h-[calc(100vh-280px)] mx-auto rounded-lg"
                />
              )}
              {viewingFile.type.startsWith('audio/') && viewingFile.url && (
                <audio
                  src={getFullFileUrl(viewingFile.url)}
                  controls
                  className="w-full"
                />
              )}
              {viewingFile.type.includes('pdf') && viewingFile.url && (
                <iframe
                  src={getFullFileUrl(viewingFile.url)}
                  className="w-full h-[calc(100vh-280px)] rounded-lg"
                />
              )}
              {!viewingFile.type.startsWith('image/') && 
               !viewingFile.type.startsWith('video/') && 
               !viewingFile.type.startsWith('audio/') && 
               !viewingFile.type.includes('pdf') && (
                <div className="text-center py-12">
                  <File className="w-16 h-16 mx-auto text-slate-300 mb-4" />
                  <p className="text-slate-500">该文件类型不支持在线预览</p>
                  {viewingFile.url && (
                    <Button
                      onClick={() => handleDownload(viewingFile)}
                      className="mt-4"
                    >
                      <Download className="w-4 h-4 mr-2" />
                      下载文件
                    </Button>
                  )}
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </>
  );
}
