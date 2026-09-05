'use client';

import React from 'react';
import { cn } from '@/lib/utils';
import { Button } from '@/components/ui/button';
import { 
  X, Calendar, FileImage, Tag, FolderOpen,
  ChevronDown, Check
} from 'lucide-react';

export interface FilterState {
  dateFilter: 'all' | 'today' | 'week' | 'month';
  typeFilter: 'all' | 'jpg' | 'png' | 'gif';
  albumFilter: string;
  tagFilter: string[];
  keyword?: string;
}

interface Album {
  id: string;
  name: string;
  parentId?: string;
}

interface FilterPanelProps {
  isOpen: boolean;
  onClose: () => void;
  filters: FilterState;
  onFilterChange: (filters: FilterState) => void;
  albums: Album[];
  tags: { name: string; count: number }[];
}

export default function FilterPanel({
  isOpen,
  onClose,
  filters,
  onFilterChange,
  albums,
  tags,
}: FilterPanelProps) {
  const [expandedSection, setExpandedSection] = React.useState<string | null>('date');

  // 只显示父相册（parentId 为空的相册）
  const parentAlbums = React.useMemo(() => {
    return albums.filter(album => !album.parentId);
  }, [albums]);

  const toggleSection = (section: string) => {
    setExpandedSection(expandedSection === section ? null : section);
  };

  const updateFilter = <K extends keyof FilterState>(key: K, value: FilterState[K]) => {
    onFilterChange({ ...filters, [key]: value });
  };

  // 切换标签选中状态
  const toggleTag = (tagName: string) => {
    const currentTags = filters.tagFilter || [];
    if (currentTags.includes(tagName)) {
      updateFilter('tagFilter', currentTags.filter(t => t !== tagName));
    } else {
      updateFilter('tagFilter', [...currentTags, tagName]);
    }
  };

  const clearFilters = () => {
    onFilterChange({
      dateFilter: 'all',
      typeFilter: 'all',
      albumFilter: 'all',
      tagFilter: [],
    });
  };

  const hasActiveFilters = 
    filters.dateFilter !== 'all' || 
    filters.typeFilter !== 'all' || 
    filters.albumFilter !== 'all' || 
    (filters.tagFilter && filters.tagFilter.length > 0);

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-start justify-end">
      {/* 背景遮罩 */}
      <div 
        className="absolute inset-0 bg-black/10 backdrop-blur-sm"
        onClick={onClose}
      />
      
      {/* 筛选面板 */}
      <div className="relative w-80 bg-white h-full shadow-2xl border-l border-[rgba(229,229,234,0.6)] overflow-y-auto animate-in slide-in-from-right duration-300">
        {/* 头部 */}
        <div className="sticky top-0 bg-white border-b border-[#e5e5ea] p-4 flex items-center justify-between z-10">
          <h2 className="text-lg font-semibold text-[#8e8e93]">筛选条件</h2>
          <div className="flex items-center gap-2">
            {hasActiveFilters && (
              <Button 
                variant="ghost" 
                size="sm" 
                onClick={clearFilters}
                className="text-[#007aff] hover:text-[#007aff] hover:bg-[rgba(0,122,255,0.1)]"
              >
                清除全部
              </Button>
            )}
            <button 
              onClick={onClose}
              className="p-2 hover:bg-[rgba(118,118,128,0.08)] rounded-lg transition-colors"
            >
              <X className="w-5 h-5 text-[#8e8e93]" />
            </button>
          </div>
        </div>

        <div className="p-4 space-y-4">
          {/* 日期筛选 */}
          <div className="border border-[#e5e5ea] rounded-xl overflow-hidden">
            <button
              onClick={() => toggleSection('date')}
              className="w-full flex items-center justify-between p-4 hover:bg-[#f2f2f7] transition-colors"
            >
              <div className="flex items-center gap-3">
                <Calendar className="w-5 h-5 text-[#007aff]" />
                <span className="font-medium text-[#8e8e93]">上传时间</span>
              </div>
              <ChevronDown className={cn(
                "w-4 h-4 text-[#8e8e93] transition-transform",
                expandedSection === 'date' && "rotate-180"
              )} />
            </button>
            {expandedSection === 'date' && (
              <div className="px-4 pb-4 space-y-2">
                {[
                  { value: 'all', label: '全部时间' },
                  { value: 'today', label: '今天' },
                  { value: 'week', label: '最近7天' },
                  { value: 'month', label: '最近30天' },
                ].map((option) => (
                  <button
                    key={option.value}
                    onClick={() => updateFilter('dateFilter', option.value as FilterState['dateFilter'])}
                    className={cn(
                      "w-full px-4 py-2.5 rounded-lg text-left text-sm transition-colors",
                      filters.dateFilter === option.value
                        ? "bg-[rgba(0,122,255,0.1)] text-[#007aff] font-medium"
                        : "hover:bg-[rgba(118,118,128,0.08)] text-[#8e8e93]"
                    )}
                  >
                    {option.label}
                  </button>
                ))}
              </div>
            )}
          </div>

          {/* 文件类型筛选 */}
          <div className="border border-[#e5e5ea] rounded-xl overflow-hidden">
            <button
              onClick={() => toggleSection('type')}
              className="w-full flex items-center justify-between p-4 hover:bg-[#f2f2f7] transition-colors"
            >
              <div className="flex items-center gap-3">
                <FileImage className="w-5 h-5 text-[#007aff]" />
                <span className="font-medium text-[#8e8e93]">文件类型</span>
              </div>
              <ChevronDown className={cn(
                "w-4 h-4 text-[#8e8e93] transition-transform",
                expandedSection === 'type' && "rotate-180"
              )} />
            </button>
            {expandedSection === 'type' && (
              <div className="px-4 pb-4 space-y-2">
                {[
                  { value: 'all', label: '全部类型' },
                  { value: 'jpg', label: 'JPG / JPEG' },
                  { value: 'png', label: 'PNG' },
                  { value: 'gif', label: 'GIF' },
                ].map((option) => (
                  <button
                    key={option.value}
                    onClick={() => updateFilter('typeFilter', option.value as FilterState['typeFilter'])}
                    className={cn(
                      "w-full px-4 py-2.5 rounded-lg text-left text-sm transition-colors",
                      filters.typeFilter === option.value
                        ? "bg-[rgba(0,122,255,0.1)] text-[#007aff] font-medium"
                        : "hover:bg-[rgba(118,118,128,0.08)] text-[#8e8e93]"
                    )}
                  >
                    {option.label}
                  </button>
                ))}
              </div>
            )}
          </div>

          {/* 相册筛选 */}
          <div className="border border-[#e5e5ea] rounded-xl overflow-hidden">
            <button
              onClick={() => toggleSection('album')}
              className="w-full flex items-center justify-between p-4 hover:bg-[#f2f2f7] transition-colors"
            >
              <div className="flex items-center gap-3">
                <FolderOpen className="w-5 h-5 text-[#ff9500]" />
                <span className="font-medium text-[#8e8e93]">相册分类</span>
              </div>
              <ChevronDown className={cn(
                "w-4 h-4 text-[#8e8e93] transition-transform",
                expandedSection === 'album' && "rotate-180"
              )} />
            </button>
            {expandedSection === 'album' && (
              <div className="px-4 pb-4 space-y-2">
                <button
                  onClick={() => updateFilter('albumFilter', 'all')}
                  className={cn(
                    "w-full px-4 py-2.5 rounded-lg text-left text-sm transition-colors",
                    filters.albumFilter === 'all'
                      ? "bg-[rgba(255,149,0,0.1)] text-[#ff9500] font-medium"
                      : "hover:bg-[rgba(118,118,128,0.08)] text-[#8e8e93]"
                  )}
                >
                  全部相册
                </button>
                {parentAlbums.map((album) => (
                  <button
                    key={album.id}
                    onClick={() => updateFilter('albumFilter', album.id)}
                    className={cn(
                      "w-full px-4 py-2.5 rounded-lg text-left text-sm transition-colors",
                      filters.albumFilter === album.id
                        ? "bg-[rgba(255,149,0,0.1)] text-[#ff9500] font-medium"
                        : "hover:bg-[rgba(118,118,128,0.08)] text-[#8e8e93]"
                    )}
                  >
                    {album.name}
                  </button>
                ))}
              </div>
            )}
          </div>

          {/* 标签筛选 */}
          <div className="border border-[#e5e5ea] rounded-xl overflow-hidden">
            <button
              onClick={() => toggleSection('tag')}
              className="w-full flex items-center justify-between p-4 hover:bg-[#f2f2f7] transition-colors"
            >
              <div className="flex items-center gap-3">
                <Tag className="w-5 h-5 text-[#34c759]" />
                <span className="font-medium text-[#8e8e93]">标签</span>
                {filters.tagFilter && filters.tagFilter.length > 0 && (
                  <span className="px-2 py-0.5 bg-[rgba(52,199,89,0.1)] text-[#34c759] text-xs rounded-full">
                    {filters.tagFilter.length}
                  </span>
                )}
              </div>
              <ChevronDown className={cn(
                "w-4 h-4 text-[#8e8e93] transition-transform",
                expandedSection === 'tag' && "rotate-180"
              )} />
            </button>
            {expandedSection === 'tag' && (
              <div className="px-4 pb-4">
                {tags.length === 0 ? (
                  <p className="text-sm text-[#8e8e93] text-center py-4">暂无标签</p>
                ) : (
                  <div className="flex flex-wrap gap-2">
                    {tags.slice(0, 30).map((tag) => {
                      const isSelected = filters.tagFilter && filters.tagFilter.includes(tag.name);
                      return (
                        <button
                          key={tag.name}
                          onClick={() => toggleTag(tag.name)}
                          className={cn(
                            "px-3 py-1.5 rounded-full text-sm transition-colors flex items-center gap-1.5",
                            isSelected
                              ? "bg-[rgba(52,199,89,0.1)] text-[#34c759] font-medium border border-[#34c759]"
                              : "bg-[rgba(118,118,128,0.08)] text-[#8e8e93] hover:bg-[rgba(0,0,0,0.06)]"
                          )}
                        >
                          {isSelected && <Check className="w-3 h-3" />}
                          {tag.name}
                          <span className="text-xs opacity-60">({tag.count})</span>
                        </button>
                      );
                    })}
                  </div>
                )}
              </div>
            )}
          </div>
        </div>

        {/* 底部操作按钮 */}
        <div className="sticky bottom-0 bg-white border-t border-[#e5e5ea] p-4 flex gap-3">
          <Button
            variant="outline"
            onClick={clearFilters}
            className="flex-1"
          >
            重置
          </Button>
          <Button
            onClick={onClose}
            className="flex-1 bg-[#007aff] hover:bg-[#007aff]"
          >
            应用筛选
          </Button>
        </div>
      </div>
    </div>
  );
}
