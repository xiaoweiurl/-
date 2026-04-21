'use client';

import React from 'react';
import { cn } from '@/lib/utils';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { X, Plus, Tag } from 'lucide-react';
import { toast } from 'sonner';

interface TagEditorProps {
  currentTags: string[];
  availableTags: { name: string; count: number }[];
  onUpdateTags: (tags: string[]) => void;
  className?: string;
}

export default function TagEditor({
  currentTags = [],
  availableTags = [],
  onUpdateTags,
  className,
}: TagEditorProps) {
  const [newTag, setNewTag] = React.useState('');
  const [isAdding, setIsAdding] = React.useState(false);

  // 添加标签
  const handleAddTag = () => {
    const tagToAdd = newTag.trim();
    if (!tagToAdd) return;
    
    if (currentTags.includes(tagToAdd)) {
      toast.warning('标签已存在');
      setNewTag('');
      return;
    }
    
    onUpdateTags([...currentTags, tagToAdd]);
    setNewTag('');
    setIsAdding(false);
  };

  // 删除标签
  const handleRemoveTag = (tagToRemove: string) => {
    onUpdateTags(currentTags.filter(t => t !== tagToRemove));
  };

  // 从已有标签中选择
  const handleSelectTag = (tagName: string) => {
    if (currentTags.includes(tagName)) {
      // 已选中，移除
      handleRemoveTag(tagName);
    } else {
      // 未选中，添加
      onUpdateTags([...currentTags, tagName]);
    }
  };

  // 按回车添加标签
  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      handleAddTag();
    }
  };

  return (
    <div className={cn('space-y-3', className)}>
      {/* 当前标签 */}
      {currentTags.length > 0 && (
        <div className="space-y-2">
          <label className="text-sm font-medium text-slate-700">当前标签</label>
          <div className="flex flex-wrap gap-2">
            {currentTags.map((tag) => (
              <span
                key={tag}
                className="inline-flex items-center gap-1 px-3 py-1.5 bg-violet-100 text-violet-700 rounded-full text-sm font-medium"
              >
                <Tag className="w-3 h-3" />
                {tag}
                <button
                  onClick={() => handleRemoveTag(tag)}
                  className="ml-1 hover:bg-violet-200 rounded-full p-0.5 transition-colors"
                >
                  <X className="w-3 h-3" />
                </button>
              </span>
            ))}
          </div>
        </div>
      )}

      {/* 添加新标签 */}
      {isAdding ? (
        <div className="flex gap-2">
          <Input
            value={newTag}
            onChange={(e) => setNewTag(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="输入标签名称"
            className="flex-1 h-9"
            autoFocus
          />
          <Button size="sm" onClick={handleAddTag} className="h-9">
            添加
          </Button>
          <Button size="sm" variant="outline" onClick={() => { setIsAdding(false); setNewTag(''); }} className="h-9">
            取消
          </Button>
        </div>
      ) : (
        <Button
          variant="outline"
          size="sm"
          onClick={() => setIsAdding(true)}
          className="w-full justify-start text-slate-600 border-dashed"
        >
          <Plus className="w-4 h-4 mr-2" />
          添加标签
        </Button>
      )}

      {/* 已有标签选择 */}
      {availableTags.length > 0 && (
        <div className="space-y-2">
          <label className="text-sm font-medium text-slate-700">从已有标签选择</label>
          <div className="flex flex-wrap gap-2 max-h-40 overflow-y-auto">
            {availableTags
              .filter(t => !currentTags.includes(t.name))
              .slice(0, 20)
              .map((tag) => (
                <button
                  key={tag.name}
                  onClick={() => handleSelectTag(tag.name)}
                  className="inline-flex items-center gap-1 px-3 py-1.5 bg-slate-100 hover:bg-slate-200 text-slate-600 hover:text-slate-800 rounded-full text-sm transition-colors"
                >
                  <Plus className="w-3 h-3" />
                  {tag.name}
                  <span className="text-xs text-slate-400 ml-1">({tag.count})</span>
                </button>
              ))}
          </div>
        </div>
      )}
    </div>
  );
}
