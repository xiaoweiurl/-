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

interface FilterPanelProps {
  isOpen: boolean;
  onClose: () => void;
  filters: FilterState;
  onFilterChange: (filters: FilterState) => void;
  albums: { id: string; name: string }[];
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
        className="absolute inset-0 bg-black/20 backdrop-blur-sm"
        onClick={onClose}
      />
      
      {/* 筛选面板 */}
      <div className="relative w-80 bg-white h-full shadow-2xl border-l border-slate-200/60 overflow-y-auto animate-in slide-in-from-right duration-300">
        {/* 头部 */}
        <div className="sticky top-0 bg-white border-b border-slate-100 p-4 flex items-center justify-between z-10">
          <h2 className="text-lg font-semibold text-slate-800">筛选条件</h2>
          <div className="flex items-center gap-2">
            {hasActiveFilters && (
              <Button 
                variant="ghost" 
                size="sm" 
                onClick={clearFilters}
                className="text-violet-600 hover:text-violet-700 hover:bg-violet-50"
              >
                清除全部
              </Button>
            )}
            <button 
              onClick={onClose}
              className="p-2 hover:bg-slate-100 rounded-lg transition-colors"
            >
              <X className="w-5 h-5 text-slate-500" />
            </button>
          </div>
        </div>

        <div className="p-4 space-y-4">
          {/* 日期筛选 */}
          <div className="border border-slate-200 rounded-xl overflow-hidden">
            <button
              onClick={() => toggleSection('date')}
              className="w-full flex items-center justify-between p-4 hover:bg-slate-50 transition-colors"
            >
              <div className="flex items-center gap-3">
                <Calendar className="w-5 h-5 text-violet-500" />
                <span className="font-medium text-slate-700">上传时间</span>
              </div>
              <ChevronDown className={cn(
                "w-4 h-4 text-slate-400 transition-transform",
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
                        ? "bg-violet-100 text-violet-700 font-medium"
                        : "hover:bg-slate-100 text-slate-600"
                    )}
                  >
                    {option.label}
                  </button>
                ))}
              </div>
            )}
          </div>

          {/* 文件类型筛选 */}
          <div className="border border-slate-200 rounded-xl overflow-hidden">
            <button
              onClick={() => toggleSection('type')}
              className="w-full flex items-center justify-between p-4 hover:bg-slate-50 transition-colors"
            >
              <div className="flex items-center gap-3">
                <FileImage className="w-5 h-5 text-blue-500" />
                <span className="font-medium text-slate-700">文件类型</span>
              </div>
              <ChevronDown className={cn(
                "w-4 h-4 text-slate-400 transition-transform",
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
                        ? "bg-blue-100 text-blue-700 font-medium"
                        : "hover:bg-slate-100 text-slate-600"
                    )}
                  >
                    {option.label}
                  </button>
                ))}
              </div>
            )}
          </div>

          {/* 相册筛选 */}
          <div className="border border-slate-200 rounded-xl overflow-hidden">
            <button
              onClick={() => toggleSection('album')}
              className="w-full flex items-center justify-between p-4 hover:bg-slate-50 transition-colors"
            >
              <div className="flex items-center gap-3">
                <FolderOpen className="w-5 h-5 text-amber-500" />
                <span className="font-medium text-slate-700">相册分类</span>
              </div>
              <ChevronDown className={cn(
                "w-4 h-4 text-slate-400 transition-transform",
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
                      ? "bg-amber-100 text-amber-700 font-medium"
                      : "hover:bg-slate-100 text-slate-600"
                  )}
                >
                  全部相册
                </button>
                {albums.map((album) => (
                  <button
                    key={album.id}
                    onClick={() => updateFilter('albumFilter', album.id)}
                    className={cn(
                      "w-full px-4 py-2.5 rounded-lg text-left text-sm transition-colors",
                      filters.albumFilter === album.id
                        ? "bg-amber-100 text-amber-700 font-medium"
                        : "hover:bg-slate-100 text-slate-600"
                    )}
                  >
                    {album.name}
                  </button>
                ))}
              </div>
            )}
          </div>

          {/* 标签筛选 */}
          <div className="border border-slate-200 rounded-xl overflow-hidden">
            <button
              onClick={() => toggleSection('tag')}
              className="w-full flex items-center justify-between p-4 hover:bg-slate-50 transition-colors"
            >
              <div className="flex items-center gap-3">
                <Tag className="w-5 h-5 text-green-500" />
                <span className="font-medium text-slate-700">标签</span>
                {filters.tagFilter && filters.tagFilter.length > 0 && (
                  <span className="px-2 py-0.5 bg-green-100 text-green-700 text-xs rounded-full">
                    {filters.tagFilter.length}
                  </span>
                )}
              </div>
              <ChevronDown className={cn(
                "w-4 h-4 text-slate-400 transition-transform",
                expandedSection === 'tag' && "rotate-180"
              )} />
            </button>
            {expandedSection === 'tag' && (
              <div className="px-4 pb-4">
                {tags.length === 0 ? (
                  <p className="text-sm text-slate-500 text-center py-4">暂无标签</p>
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
                              ? "bg-green-100 text-green-700 font-medium border border-green-300"
                              : "bg-slate-100 text-slate-600 hover:bg-slate-200"
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
        <div className="sticky bottom-0 bg-white border-t border-slate-100 p-4 flex gap-3">
          <Button
            variant="outline"
            onClick={clearFilters}
            className="flex-1"
          >
            重置
          </Button>
          <Button
            onClick={onClose}
            className="flex-1 bg-violet-600 hover:bg-violet-700"
          >
            应用筛选
          </Button>
        </div>
      </div>
    </div>
  );
}
