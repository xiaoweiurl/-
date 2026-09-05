'use client';

import React from 'react';
import { cn } from '@/lib/utils';
import { ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight } from 'lucide-react';

interface PaginationProps {
  currentPage: number;
  totalPages: number;
  totalCount: number;
  pageSize: number;
  onPageChange: (page: number) => void;
  onPageSizeChange?: (size: number) => void;
  pageSizeOptions?: number[];
  className?: string;
}

export default function Pagination({
  currentPage,
  totalPages,
  totalCount,
  pageSize,
  onPageChange,
  onPageSizeChange,
  pageSizeOptions = [20, 40, 60, 100],
  className,
}: PaginationProps) {
  // 生成页码数组
  const getPageNumbers = () => {
    const pages: (number | string)[] = [];
    const showEllipsisStart = currentPage > 3;
    const showEllipsisEnd = currentPage < totalPages - 2;

    if (totalPages <= 7) {
      // 7页以内全部显示
      for (let i = 1; i <= totalPages; i++) {
        pages.push(i);
      }
    } else {
      pages.push(1);
      
      if (showEllipsisStart) {
        pages.push('...');
      }
      
      // 当前页附近的页码
      const start = Math.max(2, currentPage - 1);
      const end = Math.min(totalPages - 1, currentPage + 1);
      
      for (let i = start; i <= end; i++) {
        pages.push(i);
      }
      
      if (showEllipsisEnd) {
        pages.push('...');
      }
      
      pages.push(totalPages);
    }
    
    return pages;
  };

  if (totalPages <= 1) return null;

  const startItem = (currentPage - 1) * pageSize + 1;
  const endItem = Math.min(currentPage * pageSize, totalCount);

  return (
    <div className={cn("flex flex-col sm:flex-row items-center justify-between gap-4", className)}>
      {/* 显示信息 */}
      <div className="text-sm text-[#8e8e93]">
        显示第 <span className="font-medium text-[#8e8e93]">{startItem}</span> - <span className="font-medium text-[#8e8e93]">{endItem}</span> 项，共 <span className="font-medium text-[#8e8e93]">{totalCount}</span> 项
      </div>

      <div className="flex items-center gap-4">
        {/* 每页数量选择 */}
        {onPageSizeChange && (
          <div className="flex items-center gap-2">
            <span className="text-sm text-[#8e8e93]">每页</span>
            <select
              value={pageSize}
              onChange={(e) => onPageSizeChange(Number(e.target.value))}
              className="px-3 py-1.5 text-sm border border-[#e5e5ea] rounded-lg bg-white focus:outline-none focus:ring-2 focus:ring-[rgba(0,122,255,0.2)] focus:border-[#007aff]"
            >
              {pageSizeOptions.map((size) => (
                <option key={size} value={size}>{size}</option>
              ))}
            </select>
            <span className="text-sm text-[#8e8e93]">条</span>
          </div>
        )}

        {/* 页码导航 */}
        <div className="flex items-center gap-1">
          {/* 首页 */}
          <button
            onClick={() => onPageChange(1)}
            disabled={currentPage === 1}
            className="p-2 text-[#8e8e93] hover:text-[#8e8e93] hover:bg-[rgba(118,118,128,0.08)] rounded-lg transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
            title="首页"
          >
            <ChevronsLeft className="w-4 h-4" />
          </button>

          {/* 上一页 */}
          <button
            onClick={() => onPageChange(currentPage - 1)}
            disabled={currentPage === 1}
            className="p-2 text-[#8e8e93] hover:text-[#8e8e93] hover:bg-[rgba(118,118,128,0.08)] rounded-lg transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
            title="上一页"
          >
            <ChevronLeft className="w-4 h-4" />
          </button>

          {/* 页码 */}
          <div className="flex items-center gap-1">
            {getPageNumbers().map((page, index) => (
              page === '...' ? (
                <span key={`ellipsis-${index}`} className="px-2 text-[#8e8e93]">...</span>
              ) : (
                <button
                  key={page}
                  onClick={() => onPageChange(page as number)}
                  className={cn(
                    "min-w-[36px] h-9 px-3 text-sm font-medium rounded-lg transition-colors",
                    currentPage === page
                      ? "bg-[#007aff] text-white"
                      : "text-[#8e8e93] hover:bg-[rgba(118,118,128,0.08)]"
                  )}
                >
                  {page}
                </button>
              )
            ))}
          </div>

          {/* 下一页 */}
          <button
            onClick={() => onPageChange(currentPage + 1)}
            disabled={currentPage === totalPages}
            className="p-2 text-[#8e8e93] hover:text-[#8e8e93] hover:bg-[rgba(118,118,128,0.08)] rounded-lg transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
            title="下一页"
          >
            <ChevronRight className="w-4 h-4" />
          </button>

          {/* 末页 */}
          <button
            onClick={() => onPageChange(totalPages)}
            disabled={currentPage === totalPages}
            className="p-2 text-[#8e8e93] hover:text-[#8e8e93] hover:bg-[rgba(118,118,128,0.08)] rounded-lg transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
            title="末页"
          >
            <ChevronsRight className="w-4 h-4" />
          </button>
        </div>
      </div>
    </div>
  );
}
