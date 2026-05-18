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
import { Download, FolderOpen, FolderTree, Loader2 } from 'lucide-react';
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

  // 构建相册树结构
  const buildAlbumTree = (albums: Album[]) => {
    const albumMap = new Map<string, Album & { children: Album[] }>();
    const rootAlbums: (Album & { children: Album[] })[] = [];

    // 初始化所有相册
    albums.forEach(album => {
      albumMap.set(album.id, { ...album, children: [] });
    });

    // 构建树结构
    albums.forEach(album => {
      const node = albumMap.get(album.id)!;
      if (album.parentId && albumMap.has(album.parentId)) {
        albumMap.get(album.parentId)!.children.push(node);
      } else {
        rootAlbums.push(node);
      }
    });

    return rootAlbums;
  };

  // 获取相册及其所有子相册的ID
  const getAlbumWithDescendants = (albumId: string): string[] => {
    const result: string[] = [albumId];
    const albumTree = buildAlbumTree(albums);
    
    const findChildren = (nodes: (Album & { children: Album[] })[]) => {
      for (const node of nodes) {
        if (node.id === albumId) {
          const collectChildren = (n: (Album & { children: Album[] })[]) => {
            for (const child of n) {
              result.push(child.id);
              if (child.children.length > 0) {
                collectChildren(child.children as (Album & { children: Album[] })[]);
              }
            }
          };
          collectChildren(node.children as (Album & { children: Album[] })[]);
          return;
        }
        findChildren(node.children as (Album & { children: Album[] })[]);
      }
    };
    
    findChildren(albumTree);
    return result;
  };

  const handleToggleAlbum = (albumId: string) => {
    const albumIds = getAlbumWithDescendants(albumId);
    
    setSelectedAlbums((prev) => {
      // 如果已经选中，则取消选中该相册及其所有子相册
      if (prev.includes(albumId)) {
        return prev.filter((id) => !albumIds.includes(id));
      }
      // 否则选中该相册及其所有子相册
      return [...new Set([...prev, ...albumIds])];
    });
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
        filename = `${album?.name || 'album'}.zip`;
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
        
        // 找到父相册名称作为文件名
        // 父相册是那些 parentId 为空或者 parentId 不在选中列表中的相册
        const parentAlbums = selectedAlbums
          .map(id => albums.find(a => a.id === id))
          .filter((album): album is Album => album !== undefined)
          .filter(album => !album.parentId || !selectedAlbums.includes(album.parentId));
        
        // 使用第一个父相册的名称
        if (parentAlbums.length > 0 && parentAlbums[0].name) {
          filename = `${parentAlbums[0].name}.zip`;
        } else {
          filename = 'albums_export.zip';
        }
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

  // 渲染相册树
  const renderAlbumTree = (nodes: (Album & { children: Album[] })[], level = 0) => {
    return nodes.map((album) => {
      const hasChildren = album.children && album.children.length > 0;
      const isParent = hasChildren;
      
      return (
        <React.Fragment key={album.id}>
          <label
            className={cn(
              "flex items-center gap-3 p-3 rounded-lg cursor-pointer transition-colors",
              selectedAlbums.includes(album.id)
                ? "bg-violet-50 border border-violet-200"
                : "hover:bg-slate-50 border border-transparent"
            )}
            style={{ paddingLeft: `${12 + level * 24}px` }}
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
            {isParent ? (
              <FolderTree className="w-4 h-4 text-violet-500" />
            ) : (
              <FolderOpen className="w-4 h-4 text-slate-400" />
            )}
            <div className="flex-1 min-w-0">
              <p className="text-sm font-medium text-slate-800 truncate">
                {album.name}
                {isParent && (
                  <span className="ml-2 text-xs text-violet-500 font-normal">
                    (含 {album.children!.length} 个子相册)
                  </span>
                )}
              </p>
              <p className="text-xs text-slate-400">
                {album.imageCount || 0} 张图片
              </p>
            </div>
          </label>
          {hasChildren && renderAlbumTree(album.children as (Album & { children: Album[] })[], level + 1)}
        </React.Fragment>
      );
    });
  };

  const albumTree = buildAlbumTree(albums);

  return (
    <Dialog open={isOpen} onOpenChange={setIsOpen}>
      <DialogTrigger asChild>
        {trigger}
      </DialogTrigger>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>批量导出相册</DialogTitle>
          <DialogDescription>
            选择要导出的相册，选中父相册将自动包含所有子相册。导出结构：父相册/子相册/商品名称/主图+详情图
          </DialogDescription>
        </DialogHeader>
        
        <div className="space-y-4 py-4">
          {/* 相册列表 - 树形结构 */}
          <div className="max-h-64 overflow-y-auto space-y-1 border rounded-lg p-2">
            {renderAlbumTree(albumTree)}
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
