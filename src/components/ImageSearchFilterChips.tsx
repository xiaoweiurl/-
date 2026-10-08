'use client';

import React from 'react';
import { X } from 'lucide-react';

export interface ImageSearchFilterChip {
  id: string;
  kind?: string;
  label: string;
  applied?: boolean;
  relaxed?: boolean;
}

/**
 * 解析出来的条件。点掉以后由调用方重新检索或从对话卡片上拿掉。
 */
export default function ImageSearchFilterChips({
  filters,
  onRemove,
}: {
  filters: ImageSearchFilterChip[];
  onRemove?: (id: string) => void;
}) {
  const visible = filters.filter(filter => filter && filter.label);
  if (visible.length === 0) return null;
  return (
    <div className="flex flex-wrap gap-2" data-testid="image-search-filter-chips">
      {visible.map(filter => (
        <button
          key={filter.id}
          type="button"
          data-testid="image-search-filter-chip"
          data-filter-id={filter.id}
          data-relaxed={filter.relaxed ? 'true' : 'false'}
          onClick={() => onRemove?.(filter.id)}
          className={`inline-flex items-center gap-1 min-h-9 max-w-full px-2.5 rounded-full text-[13px] ${
            filter.relaxed
              ? 'bg-[#FF9500]/12 text-[#C93400]'
              : 'bg-[#007AFF]/10 text-[#007AFF]'
          }`}
        >
          <span className="truncate">{filter.label}</span>
          <X className="w-3.5 h-3.5 shrink-0" aria-hidden />
          <span className="sr-only">移除{filter.label}</span>
        </button>
      ))}
    </div>
  );
}
