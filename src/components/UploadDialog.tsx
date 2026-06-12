'use client';

import React from 'react';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import { useNotifications } from '@/contexts/NotificationContext';
import { Upload, X, Image as ImageIcon, Loader2, CheckCircle2 } from 'lucide-react';
import { getSessionId } from '@/lib/auth-client';

// 后端 API 基础 URL
const BACKEND_API_URL = '/api/proxy';

interface UploadDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onUploadSuccess: () => void;
}

interface UploadingFile {
  id: string;
  file: File;
  progress: number;
  status: 'pending' | 'uploading' | 'success' | 'error';
  error?: string;
}

export default function UploadDialog({
  open,
  onOpenChange,
  onUploadSuccess,
}: UploadDialogProps) {
  const [isDragging, setIsDragging] = React.useState(false);
  const [uploadingFiles, setUploadingFiles] = React.useState<UploadingFile[]>([]);
  const [isUploading, setIsUploading] = React.useState(false);
  const fileInputRef = React.useRef<HTMLInputElement>(null);
  const { addNotification } = useNotifications();

  // 处理文件选择
  const handleFileSelect = (files: FileList | null) => {
    if (!files) return;

    const validFiles: UploadingFile[] = [];
    const allowedTypes = ['image/jpeg', 'image/png', 'image/gif', 'image/webp'];
    const maxSize = 10 * 1024 * 1024; // 10MB

    for (let i = 0; i < files.length; i++) {
      const file = files[i];

      if (!allowedTypes.includes(file.type)) {
        continue;
      }

      if (file.size > maxSize) {
        continue;
      }

      validFiles.push({
        id: `${Date.now()}_${i}`,
        file,
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

    for (const uploadingFile of uploadingFiles) {
      // 更新状态为上传中
      setUploadingFiles(prev =>
        prev.map(f =>
          f.id === uploadingFile.id ? { ...f, status: 'uploading' as const, progress: 30 } : f
        )
      );

      try {
        const formData = new FormData();
        formData.append('file', uploadingFile.file); // 后端 @RequestParam("file")

        // 通过 Next.js 代理上传
        const BACKEND_URL = '/api/proxy';
        const response = await fetch(`${BACKEND_URL}/images/upload`, {
          method: 'POST',
          mode: 'cors',
          credentials: 'include',
          headers: {
            'X-Session-Id': getSessionId() || '',
          },
          body: formData,
        });

        const result = await response.json();

        if (result.success || result.code === 200) {
          // 更新状态为成功
          setUploadingFiles(prev =>
            prev.map(f =>
              f.id === uploadingFile.id
                ? { ...f, status: 'success' as const, progress: 100 }
                : f
            )
          );
          successCount++;
        } else {
          // 更新状态为错误
          setUploadingFiles(prev =>
            prev.map(f =>
              f.id === uploadingFile.id
                ? { ...f, status: 'error' as const, error: result.error || '上传失败' }
                : f
            )
          );
        }
      } catch (error) {
        setUploadingFiles(prev =>
          prev.map(f =>
            f.id === uploadingFile.id
              ? { ...f, status: 'error' as const, error: '网络错误' }
              : f
          )
        );
      }
    }

    setIsUploading(false);

    // 如果有任意成功，关闭对话框并刷新列表
    if (successCount > 0) {
      console.log('[UploadDialog] 上传成功，准备关闭对话框并刷新');
      
      // 创建上传成功通知
      addNotification({
        type: 'upload',
        title: '图片上传成功',
        message: `${successCount}张图片已成功上传到图库${successCount > 1 ? '，AI正在自动识别分类' : ''}。`,
      });
      
      // 重置状态
      setUploadingFiles([]);
      
      // 关闭对话框
      onOpenChange(false);
      
      // 调用刷新回调
      console.log('[UploadDialog] 调用 onUploadSuccess');
      onUploadSuccess();
    }
  };

  // 关闭时清理
  const handleClose = () => {
    if (!isUploading) {
      setUploadingFiles([]);
      onOpenChange(false);
    }
  };

  // 格式化文件大小
  const formatFileSize = (bytes: number): string => {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  };

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="sm:max-w-[600px]">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Upload className="w-5 h-5" />
            上传知识
          </DialogTitle>
        </DialogHeader>

        <div className="space-y-4">
          {/* 拖拽区域 */}
          <div
            className={cn(
              'border-2 border-dashed rounded-xl p-8 text-center transition-all cursor-pointer',
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
              accept="image/jpeg,image/png,image/gif,image/webp"
              multiple
              className="hidden"
              onChange={e => handleFileSelect(e.target.files)}
            />
            <div className="flex flex-col items-center gap-3">
              <div className="w-16 h-16 rounded-full bg-emerald-100 flex items-center justify-center">
                <ImageIcon className="w-8 h-8 text-emerald-600" />
              </div>
              <div>
                <p className="text-lg font-medium text-slate-700">
                  拖拽知识文件到这里上传
                </p>
                <p className="text-sm text-slate-500 mt-1">
                  或点击选择文件
                </p>
              </div>
              <p className="text-xs text-slate-400">
                支持 JPG、PNG、GIF、WebP 格式，单张最大 10MB
              </p>
            </div>
          </div>

          {/* 文件列表 */}
          {uploadingFiles.length > 0 && (
            <div className="space-y-2 max-h-[300px] overflow-y-auto">
              {uploadingFiles.map(file => (
                <div
                  key={file.id}
                  className="flex items-center gap-3 p-3 bg-slate-50 rounded-lg"
                >
                  <div className="w-10 h-10 rounded-lg bg-slate-200 flex items-center justify-center overflow-hidden flex-shrink-0">
                    {file.file.type.startsWith('image/') ? (
                      <img
                        src={URL.createObjectURL(file.file)}
                        alt={file.file.name}
                        className="w-full h-full object-cover"
                      />
                    ) : (
                      <ImageIcon className="w-5 h-5 text-slate-400" />
                    )}
                  </div>

                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium text-slate-700 truncate">
                      {file.file.name}
                    </p>
                    <p className="text-xs text-slate-400">
                      {formatFileSize(file.file.size)}
                    </p>

                    {/* 进度条 */}
                    {file.status === 'uploading' && (
                      <div className="mt-1.5 h-1.5 bg-slate-200 rounded-full overflow-hidden">
                        <div
                          className="h-full bg-violet-500 transition-all duration-300"
                          style={{ width: `${file.progress}%` }}
                        />
                      </div>
                    )}

                    {/* 状态 */}
                    {file.status === 'success' && (
                      <p className="text-xs text-green-600 flex items-center gap-1 mt-1">
                        <CheckCircle2 className="w-3 h-3" />
                        上传成功
                      </p>
                    )}
                    {file.status === 'error' && (
                      <p className="text-xs text-red-500 mt-1">{file.error}</p>
                    )}
                  </div>

                  {/* 删除按钮 */}
                  {file.status !== 'uploading' && (
                    <button
                      onClick={e => {
                        e.stopPropagation();
                        removeFile(file.id);
                      }}
                      className="p-1.5 hover:bg-slate-200 rounded-lg transition-colors"
                      disabled={isUploading}
                    >
                      <X className="w-4 h-4 text-slate-400" />
                    </button>
                  )}
                </div>
              ))}
            </div>
          )}

          {/* 操作按钮 */}
          <div className="flex justify-end gap-3">
            <Button variant="outline" onClick={handleClose} disabled={isUploading}>
              取消
            </Button>
            <Button
              onClick={uploadFiles}
              disabled={uploadingFiles.length === 0 || isUploading}
              className="bg-violet-600 hover:bg-violet-700"
            >
              {isUploading ? (
                <>
                  <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                  上传中...
                </>
              ) : (
                <>
                  <Upload className="w-4 h-4 mr-2" />
                  上传 {uploadingFiles.length > 0 ? `(${uploadingFiles.length})` : ''}
                </>
              )}
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}
