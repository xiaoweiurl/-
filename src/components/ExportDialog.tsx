'use client';

import React from 'react';
import { cn } from '@/lib/utils';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Download, FolderOpen, Loader2 } from 'lucide-react';
import { toast } from 'sonner';
import { type Album } from '@/components/MoveToAlbumDialog';

interface ExportDialogProps {
  albums: Album[];
  trigger?: React.ReactNode;
  open?: boolean;
  onOpenChange?: (open: boolean) => void;
}

export default function ExportDialog({ albums, trigger, open, onOpenChange }: ExportDialogProps) {
  const [internalOpen, setInternalOpen] = React.useState(false);
  const [selectedAlbums, setSelectedAlbums] = React.useState<string[]>([]);
  const [isExporting, setIsExporting] = React.useState(false);
  
  const isControlled = open !== undefined;
  const isOpen = isControlled ? open : internalOpen;
  const setIsOpen = isControlled ? (onOpenChange || (() => {})) : setInternalOpen;

  const handleToggleAlbum = (albumId: string) => {
    setSelectedAlbums((prev) =>
      prev.includes(albumId)
        ? prev.filter((id) => id !== albumId)
        : [...prev, albumId]
    );
  };

  const handleSelectAll = () => {
    if (selectedAlbums.length === albums.length) {
      setSelectedAlbums([]);
    } else {
      setSelectedAlbums(albums.map((a) => a.id));
    }
  };

  const handleExport = async () => {
    if (selectedAlbums.length === 0) {
      toast.error('请选择要导出的相册');
      return;
    }

    setIsExporting(true);
    try {
      let blob: Blob;
      let filename: string;

      if (selectedAlbums.length === 1) {
        // 单个相册导出
        const albumId = selectedAlbums[0];
        const response = await fetch(`/api/images/export/${albumId}`, {
          credentials: 'include',
        });
        
        if (!response.ok) {
          const error = await response.json().catch(() => ({ message: '导出失败' }));
          throw new Error(error.message || '导出失败');
        }
        
        blob = await response.blob();
        const album = albums.find((a) => a.id === albumId);
        filename = `${album?.name || 'album'}_export.zip`;
      } else {
        // 多个相册导出
        const response = await fetch('/api/images/export/batch', {
          method: 'POST',
          credentials: 'include',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify(selectedAlbums),
        });
        
        if (!response.ok) {
          const error = await response.json().catch(() => ({ message: '导出失败' }));
          throw new Error(error.message || '导出失败');
        }
        
        blob = await response.blob();
        filename = 'albums_export.zip';
      }

      // 创建下载链接
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = filename;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.URL.revokeObjectURL(url);

      toast.success(`成功导出 ${selectedAlbums.length} 个相册`);
      setIsOpen(false);
      setSelectedAlbums([]);
    } catch (error) {
      console.error('导出失败:', error);
      toast.error(error instanceof Error ? error.message : '导出失败，请稍后重试');
    } finally {
      setIsExporting(false);
    }
  };

  return (
    <Dialog open={isOpen} onOpenChange={setIsOpen}>
      <DialogTrigger asChild>
        {trigger}
      </DialogTrigger>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>批量导出相册</DialogTitle>
          <DialogDescription>
            选择要导出的相册，导出的 ZIP 文件将按商品分组存放
          </DialogDescription>
        </DialogHeader>
        
        <div className="space-y-4 py-4">
          {/* 相册列表 */}
          <div className="max-h-64 overflow-y-auto space-y-2 border rounded-lg p-2">
            {albums.map((album) => (
              <label
                key={album.id}
                className={cn(
                  "flex items-center gap-3 p-3 rounded-lg cursor-pointer transition-colors",
                  selectedAlbums.includes(album.id)
                    ? "bg-violet-50 border border-violet-200"
                    : "hover:bg-slate-50 border border-transparent"
                )}
              >
                <Checkbox
                  checked={selectedAlbums.includes(album.id)}
                  onCheckedChange={() => handleToggleAlbum(album.id)}
                  className={cn(
                    selectedAlbums.includes(album.id)
                      ? "bg-violet-600 border-violet-600"
                      : ""
                  )}
                />
                <FolderOpen className="w-4 h-4 text-slate-400" />
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-slate-800 truncate">
                    {album.name}
                  </p>
                  <p className="text-xs text-slate-400">
                    {album.imageCount || 0} 张图片
                  </p>
                </div>
              </label>
            ))}
          </div>

          {albums.length === 0 && (
            <p className="text-center text-sm text-slate-500 py-4">
              暂无分类，请先创建分类并上传知识
            </p>
          )}
        </div>

        <DialogFooter>
          <Button
            variant="outline"
            onClick={() => setIsOpen(false)}
          >
            取消
          </Button>
          <Button
            onClick={handleExport}
            disabled={selectedAlbums.length === 0 || isExporting}
            className="gap-2"
          >
            {isExporting && <Loader2 className="w-4 h-4 animate-spin" />}
            {isExporting ? '导出中...' : `导出${selectedAlbums.length > 0 ? `(${selectedAlbums.length}个)` : ''}`}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
