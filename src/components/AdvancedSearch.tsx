'use client';

import React, { useState, useEffect, useCallback } from 'react';
import { Search, Calendar, Tag, Folder, FileType, X, Clock, Filter, ChevronDown, ChevronUp } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import { Checkbox } from '@/components/ui/checkbox';
import { Separator } from '@/components/ui/separator';
import { Badge } from '@/components/ui/badge';
import { ScrollArea } from '@/components/ui/scroll-area';

export interface AdvancedSearchFilters {
  keyword: string;
  dateRange: {
    start: Date | null;
    end: Date | null;
  };
  tags: string[];
  albums: string[];
  fileTypes: string[];
}

interface AdvancedSearchProps {
  filters: AdvancedSearchFilters;
  onFiltersChange: (filters: AdvancedSearchFilters) => void;
  onSearch: (filters: AdvancedSearchFilters) => void;
  availableTags?: { name: string; count: number }[];
  availableAlbums?: { id: string; name: string }[];
  availableFileTypes?: string[];
  className?: string;
}

const DEFAULT_FILTERS: AdvancedSearchFilters = {
  keyword: '',
  dateRange: { start: null, end: null },
  tags: [],
  albums: [],
  fileTypes: [],
};

const FILE_TYPES = [
  { value: 'jpg', label: 'JPG' },
  { value: 'jpeg', label: 'JPEG' },
  { value: 'png', label: 'PNG' },
  { value: 'gif', label: 'GIF' },
  { value: 'webp', label: 'WebP' },
  { value: 'svg', label: 'SVG' },
];

const DATE_PRESETS = [
  { label: '今天', getValue: () => { const d = new Date(); d.setHours(0, 0, 0, 0); return { start: d, end: new Date() }; } },
  { label: '昨天', getValue: () => { const d = new Date(); d.setDate(d.getDate() - 1); d.setHours(0, 0, 0, 0); const e = new Date(d); e.setHours(23, 59, 59, 999); return { start: d, end: e }; } },
  { label: '本周', getValue: () => { const d = new Date(); const day = d.getDay(); const diff = d.getDate() - day + (day === 0 ? -6 : 1); d.setDate(diff); d.setHours(0, 0, 0, 0); return { start: d, end: new Date() }; } },
  { label: '本月', getValue: () => { const d = new Date(); d.setDate(1); d.setHours(0, 0, 0, 0); return { start: d, end: new Date() }; } },
  { label: '最近7天', getValue: () => { const d = new Date(); d.setDate(d.getDate() - 7); d.setHours(0, 0, 0, 0); return { start: d, end: new Date() }; } },
  { label: '最近30天', getValue: () => { const d = new Date(); d.setDate(d.getDate() - 30); d.setHours(0, 0, 0, 0); return { start: d, end: new Date() }; } },
];

const SEARCH_HISTORY_KEY = 'advanced_search_history';
const MAX_HISTORY = 10;

export default function AdvancedSearch({
  filters,
  onFiltersChange,
  onSearch,
  availableTags = [],
  availableAlbums = [],
  availableFileTypes,
  className,
}: AdvancedSearchProps) {
  const [searchHistory, setSearchHistory] = useState<AdvancedSearchFilters[]>([]);
  const [showHistory, setShowHistory] = useState(false);
  const [expandedSections, setExpandedSections] = useState({
    date: true,
    tags: true,
    albums: true,
    fileTypes: true,
  });

  // 加载搜索历史
  useEffect(() => {
    try {
      const saved = localStorage.getItem(SEARCH_HISTORY_KEY);
      if (saved) {
        const parsed = JSON.parse(saved);
        setSearchHistory(parsed.map((item: any) => ({
          ...item,
          dateRange: {
            start: item.dateRange?.start ? new Date(item.dateRange.start) : null,
            end: item.dateRange?.end ? new Date(item.dateRange.end) : null,
          },
        })));
      }
    } catch (e) {
      console.error('加载搜索历史失败:', e);
    }
  }, []);

  // 保存搜索历史
  const saveToHistory = useCallback((newFilters: AdvancedSearchFilters) => {
    try {
      const newHistory = [
        { ...newFilters, timestamp: Date.now() },
        ...searchHistory.filter(item => 
          item.keyword !== newFilters.keyword || 
          JSON.stringify(item.tags) !== JSON.stringify(newFilters.tags)
        ),
      ].slice(0, MAX_HISTORY);
      
      setSearchHistory(newHistory);
      localStorage.setItem(SEARCH_HISTORY_KEY, JSON.stringify(newHistory));
    } catch (e) {
      console.error('保存搜索历史失败:', e);
    }
  }, [searchHistory]);

  // 处理搜索
  const handleSearch = useCallback(() => {
    saveToHistory(filters);
    onSearch(filters);
  }, [filters, onSearch, saveToHistory]);

  // 应用历史搜索
  const applyHistory = useCallback((historyItem: AdvancedSearchFilters) => {
    onFiltersChange(historyItem);
    setShowHistory(false);
  }, [onFiltersChange]);

  // 清除历史
  const clearHistory = useCallback(() => {
    setSearchHistory([]);
    localStorage.removeItem(SEARCH_HISTORY_KEY);
  }, []);

  // 重置筛选
  const resetFilters = useCallback(() => {
    onFiltersChange(DEFAULT_FILTERS);
  }, [onFiltersChange]);

  // 切换标签
  const toggleTag = useCallback((tag: string) => {
    onFiltersChange({
      ...filters,
      tags: filters.tags.includes(tag)
        ? filters.tags.filter(t => t !== tag)
        : [...filters.tags, tag],
    });
  }, [filters, onFiltersChange]);

  // 切换相册
  const toggleAlbum = useCallback((albumId: string) => {
    onFiltersChange({
      ...filters,
      albums: filters.albums.includes(albumId)
        ? filters.albums.filter(a => a !== albumId)
        : [...filters.albums, albumId],
    });
  }, [filters, onFiltersChange]);

  // 切换文件类型
  const toggleFileType = useCallback((fileType: string) => {
    onFiltersChange({
      ...filters,
      fileTypes: filters.fileTypes.includes(fileType)
        ? filters.fileTypes.filter(t => t !== fileType)
        : [...filters.fileTypes, fileType],
    });
  }, [filters, onFiltersChange]);

  // 应用日期预设
  const applyDatePreset = useCallback((preset: typeof DATE_PRESETS[0]) => {
    const { start, end } = preset.getValue();
    onFiltersChange({
      ...filters,
      dateRange: { start, end },
    });
  }, [filters, onFiltersChange]);

  // 切换区域展开
  const toggleSection = useCallback((section: keyof typeof expandedSections) => {
    setExpandedSections(prev => ({
      ...prev,
      [section]: !prev[section],
    }));
  }, []);

  // 计算活跃筛选数量
  const activeFilterCount = 
    (filters.keyword ? 1 : 0) +
    (filters.dateRange.start || filters.dateRange.end ? 1 : 0) +
    filters.tags.length +
    filters.albums.length +
    filters.fileTypes.length;

  // 格式化历史显示
  const formatHistoryLabel = (item: AdvancedSearchFilters) => {
    const parts: string[] = [];
    if (item.keyword) parts.push(`"${item.keyword}"`);
    if (item.tags.length > 0) parts.push(`${item.tags.length}个标签`);
    if (item.albums.length > 0) parts.push(`${item.albums.length}个相册`);
    if (item.fileTypes.length > 0) parts.push(`${item.fileTypes.length}种类型`);
    return parts.length > 0 ? parts.join(' · ') : '空搜索';
  };

  return (
    <div className={className}>
      {/* 主搜索栏 */}
      <div className="relative">
        <Search className="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-400" />
        <Input
          type="text"
          placeholder="输入关键词搜索图片..."
          value={filters.keyword}
          onChange={(e) => onFiltersChange({ ...filters, keyword: e.target.value })}
          onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
          className="pl-12 pr-24 h-12 bg-white border-slate-200 focus:border-violet-300 focus:ring-violet-500/20 text-base"
        />
        <div className="absolute right-2 top-1/2 -translate-y-1/2 flex items-center gap-2">
          {activeFilterCount > 0 && (
            <Badge variant="secondary" className="h-7 bg-violet-100 text-violet-700">
              {activeFilterCount}
            </Badge>
          )}
          <Popover open={showHistory} onOpenChange={setShowHistory}>
            <PopoverTrigger asChild>
              <Button variant="ghost" size="icon" className="h-8 w-8">
                <Clock className="w-4 h-4 text-slate-500" />
              </Button>
            </PopoverTrigger>
            <PopoverContent className="w-80 p-0" align="end">
              <div className="p-3 border-b border-slate-100 flex items-center justify-between">
                <h3 className="font-medium text-slate-800">搜索历史</h3>
                {searchHistory.length > 0 && (
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={clearHistory}
                    className="h-7 text-xs text-slate-500 hover:text-red-600"
                  >
                    清空
                  </Button>
                )}
              </div>
              {searchHistory.length === 0 ? (
                <div className="p-8 text-center">
                  <Clock className="w-10 h-10 text-slate-200 mx-auto mb-2" />
                  <p className="text-sm text-slate-500">暂无搜索历史</p>
                </div>
              ) : (
                <ScrollArea className="max-h-80">
                  {searchHistory.map((item, index) => (
                    <button
                      key={index}
                      onClick={() => applyHistory(item)}
                      className="w-full text-left p-3 hover:bg-slate-50 border-b border-slate-50 last:border-0 transition-colors"
                    >
                      <p className="text-sm text-slate-700 truncate">{formatHistoryLabel(item)}</p>
                    </button>
                  ))}
                </ScrollArea>
              )}
            </PopoverContent>
          </Popover>
          <Button
            onClick={handleSearch}
            className="h-8 bg-violet-600 hover:bg-violet-700 text-white"
          >
            搜索
          </Button>
        </div>
      </div>

      {/* 高级筛选面板 */}
      <div className="mt-4 bg-white rounded-xl border border-slate-200 p-4 space-y-4">
        {/* 操作栏 */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Filter className="w-4 h-4 text-slate-500" />
            <span className="font-medium text-slate-700">高级筛选</span>
          </div>
          <Button
            variant="ghost"
            size="sm"
            onClick={resetFilters}
            className="h-8 text-sm text-slate-500 hover:text-slate-700"
          >
            <X className="w-4 h-4 mr-1" />
            重置
          </Button>
        </div>

        <Separator />

        {/* 日期范围 */}
        <div>
          <button
            onClick={() => toggleSection('date')}
            className="w-full flex items-center justify-between py-1"
          >
            <div className="flex items-center gap-2">
              <Calendar className="w-4 h-4 text-slate-500" />
              <span className="text-sm font-medium text-slate-700">日期范围</span>
            </div>
            {expandedSections.date ? (
              <ChevronUp className="w-4 h-4 text-slate-400" />
            ) : (
              <ChevronDown className="w-4 h-4 text-slate-400" />
            )}
          </button>
          {expandedSections.date && (
            <div className="mt-3 space-y-3">
              <div className="flex flex-wrap gap-2">
                {DATE_PRESETS.map((preset) => (
                  <Button
                    key={preset.label}
                    variant="secondary"
                    size="sm"
                    onClick={() => applyDatePreset(preset)}
                    className="h-7 text-xs"
                  >
                    {preset.label}
                  </Button>
                ))}
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-xs text-slate-500 mb-1 block">开始日期</label>
                  <Input
                    type="date"
                    value={filters.dateRange.start ? filters.dateRange.start.toISOString().split('T')[0] : ''}
                    onChange={(e) => onFiltersChange({
                      ...filters,
                      dateRange: {
                        ...filters.dateRange,
                        start: e.target.value ? new Date(e.target.value) : null,
                      },
                    })}
                    className="h-8 text-sm"
                  />
                </div>
                <div>
                  <label className="text-xs text-slate-500 mb-1 block">结束日期</label>
                  <Input
                    type="date"
                    value={filters.dateRange.end ? filters.dateRange.end.toISOString().split('T')[0] : ''}
                    onChange={(e) => onFiltersChange({
                      ...filters,
                      dateRange: {
                        ...filters.dateRange,
                        end: e.target.value ? new Date(e.target.value) : null,
                      },
                    })}
                    className="h-8 text-sm"
                  />
                </div>
              </div>
            </div>
          )}
        </div>

        <Separator />

        {/* 标签筛选 */}
        <div>
          <button
            onClick={() => toggleSection('tags')}
            className="w-full flex items-center justify-between py-1"
          >
            <div className="flex items-center gap-2">
              <Tag className="w-4 h-4 text-slate-500" />
              <span className="text-sm font-medium text-slate-700">标签</span>
              {filters.tags.length > 0 && (
                <Badge variant="secondary" className="h-5 text-xs">
                  {filters.tags.length}
                </Badge>
              )}
            </div>
            {expandedSections.tags ? (
              <ChevronUp className="w-4 h-4 text-slate-400" />
            ) : (
              <ChevronDown className="w-4 h-4 text-slate-400" />
            )}
          </button>
          {expandedSections.tags && availableTags.length > 0 && (
            <div className="mt-3">
              <div className="flex flex-wrap gap-2">
                {availableTags.map((tag) => (
                  <button
                    key={tag.name}
                    onClick={() => toggleTag(tag.name)}
                    className={`
                      px-3 py-1.5 rounded-full text-sm transition-all
                      ${filters.tags.includes(tag.name)
                        ? 'bg-violet-600 text-white'
                        : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                      }
                    `}
                  >
                    {tag.name}
                    <span className="ml-1 opacity-70">({tag.count})</span>
                  </button>
                ))}
              </div>
            </div>
          )}
        </div>

        <Separator />

        {/* 相册筛选 */}
        <div>
          <button
            onClick={() => toggleSection('albums')}
            className="w-full flex items-center justify-between py-1"
          >
            <div className="flex items-center gap-2">
              <Folder className="w-4 h-4 text-slate-500" />
              <span className="text-sm font-medium text-slate-700">相册</span>
              {filters.albums.length > 0 && (
                <Badge variant="secondary" className="h-5 text-xs">
                  {filters.albums.length}
                </Badge>
              )}
            </div>
            {expandedSections.albums ? (
              <ChevronUp className="w-4 h-4 text-slate-400" />
            ) : (
              <ChevronDown className="w-4 h-4 text-slate-400" />
            )}
          </button>
          {expandedSections.albums && availableAlbums.length > 0 && (
            <div className="mt-3">
              <div className="grid grid-cols-2 gap-2">
                {availableAlbums.map((album) => (
                  <label key={album.id} className="flex items-center gap-2 p-2 rounded-lg hover:bg-slate-50 cursor-pointer">
                    <Checkbox
                      checked={filters.albums.includes(album.id)}
                      onCheckedChange={() => toggleAlbum(album.id)}
                    />
                    <span className="text-sm text-slate-700 truncate">{album.name}</span>
                  </label>
                ))}
              </div>
            </div>
          )}
        </div>

        <Separator />

        {/* 文件类型筛选 */}
        <div>
          <button
            onClick={() => toggleSection('fileTypes')}
            className="w-full flex items-center justify-between py-1"
          >
            <div className="flex items-center gap-2">
              <FileType className="w-4 h-4 text-slate-500" />
              <span className="text-sm font-medium text-slate-700">文件类型</span>
              {filters.fileTypes.length > 0 && (
                <Badge variant="secondary" className="h-5 text-xs">
                  {filters.fileTypes.length}
                </Badge>
              )}
            </div>
            {expandedSections.fileTypes ? (
              <ChevronUp className="w-4 h-4 text-slate-400" />
            ) : (
              <ChevronDown className="w-4 h-4 text-slate-400" />
            )}
          </button>
          {expandedSections.fileTypes && (
            <div className="mt-3">
              <div className="flex flex-wrap gap-2">
                {FILE_TYPES.map((type) => (
                  <button
                    key={type.value}
                    onClick={() => toggleFileType(type.value)}
                    className={`
                      px-3 py-1.5 rounded-lg text-sm transition-all
                      ${filters.fileTypes.includes(type.value)
                        ? 'bg-violet-600 text-white'
                        : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                      }
                    `}
                  >
                    {type.label}
                  </button>
                ))}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

export { DEFAULT_FILTERS };
