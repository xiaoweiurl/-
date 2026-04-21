'use client';

import React from 'react';
import { cn } from '@/lib/utils';
import { Loader2 } from 'lucide-react';
import ImageCard, { type ImageItem, type AlbumItem } from './ImageCard';

interface ImageGridProps {
  images: ImageItem[];
  viewMode: 'grid' | 'masonry' | 'list';
  selectedImages: string[];
  onSelectImage: (id: string) => void;
  onPreviewImage: (image: ImageItem) => void;
  onToggleFavorite: (id: string) => void;
  onDeleteImage?: (id: string) => void;
  onMoveImage?: (id: string) => void;
  onMoveToAlbum?: (imageId: string, albumId: string) => void;
  albums?: AlbumItem[];
  onRestoreImage?: (id: string) => void;
  onPermanentDeleteImage?: (id: string) => void;
  loading?: boolean;
  isTrash?: boolean;
  hasMore?: boolean;
  onLoadMore?: () => void;
  compactMode?: boolean;
  showFileInfo?: boolean;
  searchQuery?: string;
}

export default function ImageGrid({
  images,
  viewMode,
  selectedImages,
  onSelectImage,
  onPreviewImage,
  onToggleFavorite,
  onDeleteImage,
  onMoveImage,
  onMoveToAlbum,
  albums = [],
  onRestoreImage,
  onPermanentDeleteImage,
  loading = false,
  isTrash = false,
  hasMore = false,
  onLoadMore,
  compactMode = false,
  showFileInfo = true,
  searchQuery = '',
}: ImageGridProps) {
  // 无限滚动检测
  const observerRef = React.useRef<IntersectionObserver | null>(null);
  const loadMoreRef = React.useRef<HTMLDivElement | null>(null);

  React.useEffect(() => {
    if (!onLoadMore || !hasMore) return;

    observerRef.current = new IntersectionObserver(
      (entries) => {
        if (entries[0].isIntersecting && !loading) {
          onLoadMore();
        }
      },
      { rootMargin: '200px' }
    );

    if (loadMoreRef.current) {
      observerRef.current.observe(loadMoreRef.current);
    }

    return () => {
      observerRef.current?.disconnect();
    };
  }, [onLoadMore, hasMore, loading]);

  if (loading && images.length === 0) {
    return (
      <div
        className={cn(
          viewMode === 'grid'
            ? 'grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-4'
            : viewMode === 'list'
            ? 'flex flex-col gap-3'
            : 'columns-2 md:columns-3 lg:columns-4 xl:columns-5 gap-4 space-y-4'
        )}
      >
        {[...Array(12)].map((_, i) => (
          <div
            key={i}
            className={cn(
              'bg-slate-100 rounded-2xl animate-pulse',
              viewMode === 'grid' ? 'aspect-square' : viewMode === 'list' ? 'h-20' : 'h-64'
            )}
          />
        ))}
      </div>
    );
  }

  if (images.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-20">
        <div className="w-24 h-24 rounded-full bg-slate-100 flex items-center justify-center mb-4">
          <svg
            className="w-12 h-12 text-slate-300"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={1.5}
              d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z"
            />
          </svg>
        </div>
        <h3 className="text-lg font-medium text-slate-700 mb-2">暂无图片</h3>
        <p className="text-sm text-slate-500">上传一些图片开始管理吧</p>
      </div>
    );
  }

  return (
    <>
      <div
        className={cn(
          viewMode === 'grid'
            ? `grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 ${compactMode ? 'gap-2' : 'gap-4'}`
            : viewMode === 'list'
            ? 'flex flex-col gap-3'
            : `columns-2 md:columns-3 lg:columns-4 xl:columns-5 ${compactMode ? 'gap-2' : 'gap-4'}`
        )}
      >
        {images.map((image) => (
          <div key={image.id} className={viewMode === 'masonry' ? 'break-inside-avoid mb-4' : ''}>
            <ImageCard
              image={image}
              viewMode={viewMode}
              isSelected={selectedImages.includes(image.id)}
              onSelect={onSelectImage}
              onPreview={onPreviewImage}
              onToggleFavorite={onToggleFavorite}
              onDelete={onDeleteImage}
              onMove={onMoveImage}
              onMoveToAlbum={onMoveToAlbum}
              albums={albums}
              onRestore={onRestoreImage}
              onPermanentDelete={onPermanentDeleteImage}
              isTrash={isTrash}
              showFileInfo={showFileInfo}
              searchQuery={searchQuery}
            />
          </div>
        ))}
      </div>

      {/* 无限滚动加载触发器 */}
      {hasMore && onLoadMore && (
        <div 
          ref={loadMoreRef}
          className="flex items-center justify-center py-8"
        >
          {loading && (
            <div className="flex items-center gap-2 text-slate-500">
              <Loader2 className="w-5 h-5 animate-spin" />
              <span>加载中...</span>
            </div>
          )}
        </div>
      )}
    </>
  );
}
