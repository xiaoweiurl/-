'use client';

import React from 'react';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import { FolderOpen, Check, Image } from 'lucide-react';

export interface Album {
  id: string;
  name: string;
  fullName?: string;      // 完整显示名称，如 "松野湃-速干T恤"
  parentId?: string;       // 父相册ID，null 表示顶级
  path?: string;           // 层级路径，如 "松野湃/速干T恤"
  description?: string;
  coverUrl?: string;
  imageCount?: number;
}

interface MoveToAlbumDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  albums: Album[];
  selectedAlbumId: string | null;
  onSelectAlbum: (albumId: string) => void;
  onConfirm: () => void;
  imageCount: number;
}

export default function MoveToAlbumDialog({
  open,
  onOpenChange,
  albums,
  selectedAlbumId,
  onSelectAlbum,
  onConfirm,
  imageCount,
}: MoveToAlbumDialogProps) {
  const handleConfirm = () => {
    if (selectedAlbumId) {
      onConfirm();
      onOpenChange(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <FolderOpen className="w-5 h-5 text-violet-600" />
            移动到相册
          </DialogTitle>
          <DialogDescription>
            选择目标相册，将 {imageCount} 张图片移动到该相册
          </DialogDescription>
        </DialogHeader>

        <div className="py-4">
          <div className="grid grid-cols-2 gap-3 max-h-[400px] overflow-y-auto pr-2">
            {albums.map((album) => (
              <button
                key={album.id}
                onClick={() => onSelectAlbum(album.id)}
                className={cn(
                  'relative flex items-center gap-3 p-3 rounded-xl border-2 transition-all duration-200 text-left',
                  selectedAlbumId === album.id
                    ? 'border-violet-500 bg-violet-50 ring-2 ring-violet-500/20'
                    : 'border-slate-200 hover:border-violet-300 hover:bg-slate-50'
                )}
              >
                {/* 选中标记 */}
                {selectedAlbumId === album.id && (
                  <div className="absolute top-2 right-2 w-5 h-5 rounded-full bg-violet-500 flex items-center justify-center">
                    <Check className="w-3 h-3 text-white" />
                  </div>
                )}

                {/* 相册封面 */}
                <div className="w-12 h-12 rounded-lg bg-gradient-to-br from-violet-100 to-purple-100 flex items-center justify-center flex-shrink-0 overflow-hidden">
                  {album.coverUrl ? (
                    <img
                      src={album.coverUrl}
                      alt={album.name}
                      className="w-full h-full object-cover"
                    />
                  ) : (
                    <Image className="w-6 h-6 text-violet-400" />
                  )}
                </div>

                {/* 相册信息 */}
                <div className="flex-1 min-w-0">
                  <h3 className="font-medium text-slate-800 truncate">
                    {album.name}
                  </h3>
                  <p className="text-xs text-slate-500 mt-0.5">
                    {album.imageCount || 0} 张图片
                  </p>
                </div>
              </button>
            ))}
          </div>

          {albums.length === 0 && (
            <div className="flex flex-col items-center justify-center py-12 text-center">
              <div className="w-16 h-16 rounded-full bg-slate-100 flex items-center justify-center mb-3">
                <FolderOpen className="w-8 h-8 text-slate-300" />
              </div>
              <p className="text-slate-500 mb-2">暂无相册</p>
              <p className="text-sm text-slate-400">请先创建相册</p>
            </div>
          )}
        </div>

        <DialogFooter className="gap-2 sm:gap-0">
          <Button
            variant="outline"
            onClick={() => onOpenChange(false)}
          >
            取消
          </Button>
          <Button
            onClick={handleConfirm}
            disabled={!selectedAlbumId}
            className="bg-violet-600 hover:bg-violet-700"
          >
            移动到相册
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
