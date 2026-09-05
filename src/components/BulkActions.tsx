'use client';

import React from 'react';
import { cn } from '@/lib/utils';
import { Button } from '@/components/ui/button';
import { Download, Trash2, FolderOpen, Heart, X, RotateCcw, AlertTriangle, Tag, Plus } from 'lucide-react';
import TagEditor from './TagEditor';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';

interface BulkActionsProps {
  selectedCount: number;
  onClearSelection: () => void;
  onDownload?: () => void;
  onDelete?: () => void;
  onMove?: () => void;
  onFavorite?: () => void;
  onRestore?: () => void;
  onPermanentDelete?: () => void;
  onBatchUpdateTags?: (newTags: string[]) => void;
  availableTags?: { name: string; count: number }[];
  isTrash?: boolean;
}

export default function BulkActions({
  selectedCount,
  onClearSelection,
  onDownload,
  onDelete,
  onMove,
  onFavorite,
  onRestore,
  onPermanentDelete,
  onBatchUpdateTags,
  availableTags = [],
  isTrash = false,
}: BulkActionsProps) {
  const [showTagDialog, setShowTagDialog] = React.useState(false);
  const [selectedTags, setSelectedTags] = React.useState<string[]>([]);

  if (selectedCount === 0) return null;

  // 处理批量标签更新
  const handleBatchUpdateTags = () => {
    if (onBatchUpdateTags && selectedTags.length > 0) {
      onBatchUpdateTags(selectedTags);
    }
  };

  return (
    <>
      <div className="fixed bottom-6 left-1/2 -translate-x-1/2 z-20 bg-white rounded-2xl shadow-2xl border border-[rgba(229,229,234,0.6)] p-2 flex items-center gap-2 animate-in slide-in-from-bottom-4 duration-300">
        {/* 已选择计数 */}
        <div className={cn(
          "flex items-center gap-2 px-3 py-2 rounded-lg",
          isTrash ? "bg-[rgba(255,59,48,0.1)]" : "bg-[rgba(0,122,255,0.1)]"
        )}>
          <span className={cn(
            "text-sm font-medium",
            isTrash ? "text-[#ff3b30]" : "text-[#007aff]"
          )}>
            已选择 {selectedCount} 张图片
          </span>
          <button
            onClick={onClearSelection}
            className={cn(
              "p-1 rounded transition-colors",
              isTrash ? "hover:bg-[rgba(255,59,48,0.1)] text-[#ff3b30]" : "hover:bg-[rgba(0,122,255,0.1)] text-[#007aff]"
            )}
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        <div className="w-px h-8 bg-[rgba(0,0,0,0.06)]" />

        {/* 操作按钮 */}
        <div className="flex items-center gap-1">
          {isTrash ? (
            // 回收站模式：显示恢复和永久删除按钮
            <>
              {onRestore && (
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={onRestore}
                  className="gap-2 text-[#34c759] hover:text-[#34c759] hover:bg-[rgba(52,199,89,0.1)]"
                >
                  <RotateCcw className="w-4 h-4" />
                  <span>恢复</span>
                </Button>
              )}
              {onPermanentDelete && (
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={onPermanentDelete}
                  className="gap-2 text-[#ff3b30] hover:text-[#ff3b30] hover:bg-[rgba(255,59,48,0.1)]"
                >
                  <AlertTriangle className="w-4 h-4" />
                  <span>永久删除</span>
                </Button>
              )}
            </>
          ) : (
            // 正常模式：显示移动、收藏、下载、删除、标签按钮
            <>
              {onBatchUpdateTags && (
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => setShowTagDialog(true)}
                  className="gap-2 text-[#8e8e93] hover:text-[#8e8e93] hover:bg-[rgba(118,118,128,0.08)]"
                >
                  <Tag className="w-4 h-4" />
                  <span>标签</span>
                </Button>
              )}

              {onMove && (
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={onMove}
                  className="gap-2 text-[#8e8e93] hover:text-[#8e8e93] hover:bg-[rgba(118,118,128,0.08)]"
                >
                  <FolderOpen className="w-4 h-4" />
                  <span>移动</span>
                </Button>
              )}

              {onFavorite && (
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={onFavorite}
                  className="gap-2 text-[#8e8e93] hover:text-[#8e8e93] hover:bg-[rgba(118,118,128,0.08)]"
                >
                  <Heart className="w-4 h-4" />
                  <span>收藏</span>
                </Button>
              )}

              {onDownload && (
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={onDownload}
                  className="gap-2 text-[#8e8e93] hover:text-[#8e8e93] hover:bg-[rgba(118,118,128,0.08)]"
                >
                  <Download className="w-4 h-4" />
                  <span>下载</span>
                </Button>
              )}

              {onDelete && (
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={onDelete}
                  className="gap-2 text-[#ff3b30] hover:text-[#ff3b30] hover:bg-[rgba(255,59,48,0.1)]"
                >
                  <Trash2 className="w-4 h-4" />
                  <span>删除</span>
                </Button>
              )}
            </>
          )}
        </div>
      </div>

      {/* 批量标签编辑对话框 */}
      <Dialog open={showTagDialog} onOpenChange={setShowTagDialog}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <Tag className="w-5 h-5 text-[#007aff]" />
              为 {selectedCount} 张图片设置标签
            </DialogTitle>
          </DialogHeader>
          <div className="py-4">
            <TagEditor
              currentTags={selectedTags}
              availableTags={availableTags}
              onUpdateTags={setSelectedTags}
            />
          </div>
          <div className="flex justify-end gap-2">
            <Button variant="outline" onClick={() => { setShowTagDialog(false); setSelectedTags([]); }}>
              取消
            </Button>
            <Button 
              onClick={handleBatchUpdateTags} 
              disabled={selectedTags.length === 0}
              className="bg-[#007aff] hover:bg-[#007aff]"
            >
              <Plus className="w-4 h-4 mr-2" />
              应用标签
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </>
  );
}
