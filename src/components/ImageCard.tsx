'use client';

import React from 'react';
import Image from 'next/image';
import { createPortal } from 'react-dom';
import { rewriteStaticUrl } from '@/lib/backend-proxy';
import { useRouter } from 'next/navigation';
import { cn } from '@/lib/utils';
import { getSessionId } from '@/lib/auth-client';
import { 
  Heart, Download, MoreVertical, Check, Trash2, Move, Copy, ExternalLink, Share2,
  Twitter, Facebook, Linkedin, CheckCheck, ArrowLeft, ChevronRight, FolderOpen, Edit3, Star
} from 'lucide-react';
import { toast } from 'sonner';
import HighlightedText, { HighlightedTags } from './HighlightedText';
import dynamic from 'next/dynamic';

// 动态导入 ShareDialog 避免 SSR 问题
const ShareDialog = dynamic(() => import('./ShareDialog'), { ssr: false });

// 后端静态资源 URL（用于图片等静态文件，统一走代理）
const BACKEND_STATIC_URL = '/api/proxy';

// 获取完整的图片 URL（将 localhost:8080 的完整URL改写为代理路径）
function getFullImageUrl(url: string | undefined): string {
  if (!url) return '/placeholder.svg';
  // 如果是包含 localhost:8080 的完整URL，改写为代理路径
  // 这样无论本地还是映射访问都能正常加载，且没有CORS问题
  if (url.includes('localhost:8080')) {
    // http://localhost:8080/api/uploads/images/xxx.jpg → /api/proxy/uploads/images/xxx.jpg
    // http://localhost:8080/uploads/images/xxx.jpg → /api/proxy/uploads/images/xxx.jpg
    const pathWithoutHost = url.replace(/^https?:\/\/localhost:8080/, '');
    const pathWithoutApiPrefix = pathWithoutHost.replace(/^\/api/, '');
    return `${BACKEND_STATIC_URL}${pathWithoutApiPrefix}`;
  }
  // 其他完整URL（非localhost，如CDN等）
  if (url.startsWith('http://') || url.startsWith('https://') || url.startsWith('//')) {
    return rewriteStaticUrl(url);
  }
  // 如果是相对路径，添加后端地址
  return `${BACKEND_STATIC_URL}${url.startsWith('/') ? url : '/' + url}`;
}

export interface ImageItem {
  id: string;
  url: string;
  title: string;
  description?: string;
  size: string;
  resolution: string;
  date: string;
  favorite: boolean;
  tags?: string[];
  albumId?: string;
  albumName?: string;
  fileType?: string;  // 图片格式，如 jpg, png, gif, webp, bmp 等
  userId?: string;    // 上传用户ID（用于数据隔离）
  deleted?: boolean;
  deletedAt?: string;
  createdAt?: string; // 后端返回的日期字段
  productId?: string; // 商品ID（用于关联主图和详情图）
  isMainImage?: boolean; // 是否为主图
  // 浏览和下载统计
  viewCount?: number;
  downloadCount?: number;
}

export interface AlbumItem {
  id: string;
  name: string;
  coverUrl?: string;
  imageCount?: number;
}

interface ImageCardProps {
  image: ImageItem;
  viewMode: 'grid' | 'masonry' | 'list';
  isSelected: boolean;
  onSelect: (id: string) => void;
  onPreview: (image: ImageItem) => void;
  onToggleFavorite: (id: string) => void;
  onDelete?: (id: string) => void;
  onMove?: (id: string) => void;
  onMoveToAlbum?: (imageId: string, albumId: string) => void;
  albums?: AlbumItem[];
  onRestore?: (id: string) => void;
  onPermanentDelete?: (id: string) => void;
  isTrash?: boolean;
  showFileInfo?: boolean;
  searchQuery?: string;
}

export default function ImageCard({
  image,
  viewMode,
  isSelected,
  onSelect,
  onPreview,
  onToggleFavorite,
  onDelete,
  onMove,
  onMoveToAlbum,
  albums = [],
  onRestore,
  onPermanentDelete,
  isTrash = false,
  showFileInfo = true,
  searchQuery = '',
}: ImageCardProps) {
  const [showActions, setShowActions] = React.useState(false);
  const [imageLoaded, setImageLoaded] = React.useState(false);
  const [isFavoriting, setIsFavoriting] = React.useState(false);
  const [showMoreMenu, setShowMoreMenu] = React.useState(false);
  const [menuView, setMenuView] = React.useState<'main' | 'share' | 'albums'>('main');
  const [menuPosition, setMenuPosition] = React.useState({ top: 0, left: 0 });
  const [showShareDialog, setShowShareDialog] = React.useState(false);
  const cardRef = React.useRef<HTMLDivElement>(null);
  const router = useRouter();
  const menuButtonRef = React.useRef<HTMLButtonElement>(null);
  const menuRef = React.useRef<HTMLDivElement>(null);

  // 点击外部关闭菜单（需要同时检查卡片和菜单本身）
  React.useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      const target = event.target as Node;
      // 如果点击的是卡片或菜单内部，不关闭
      if (
        cardRef.current?.contains(target) ||
        menuRef.current?.contains(target)
      ) {
        return;
      }
      setShowMoreMenu(false);
      setMenuView('main');
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const handleFavoriteClick = (e: React.MouseEvent) => {
    e.stopPropagation();
    setIsFavoriting(true);
    onToggleFavorite(image.id);
    
    // 动画效果持续一段时间
    setTimeout(() => setIsFavoriting(false), 600);
  };

  // 下载图片
  const handleDownload = async (e: React.MouseEvent) => {
    e.stopPropagation();
    try {
      const fullUrl = getFullImageUrl(image.url);
      const sessionId = getSessionId();
      console.log('[ImageCard] 下载图片，完整URL:', fullUrl, 'sessionId:', sessionId ? sessionId.substring(0, 8) + '...' : 'null');
      
      // 如果是旧格式的沙箱 URL，提示用户重新上传
      if (fullUrl.includes('sandbox/coze_coding/file/proxy')) {
        toast.error('旧格式图片不支持下载，请删除后重新上传');
        return;
      }
      
      // 如果是相对路径或本地存储路径，尝试使用 API 代理
      let downloadUrl = fullUrl;
      if (fullUrl.includes('/uploads/') && !fullUrl.startsWith('http')) {
        // 相对路径，使用 API 代理
        downloadUrl = `/api/images/${image.id}/file`;
      }
      
      const response = await fetch(downloadUrl, {
        headers: {
          'X-Session-Id': sessionId || '',
        },
        credentials: 'include',
      });
      
      // 检查响应状态
      if (!response.ok) {
        const errorText = await response.text();
        console.error('[ImageCard] 下载失败，HTTP状态:', response.status, '响应:', errorText);
        
        // 如果是 410 Gone，说明是旧格式 URL
        if (response.status === 410) {
          toast.error('图片路径格式不支持，请删除后重新上传');
          return;
        }
        
        toast.error(`下载失败: ${response.status}`);
        return;
      }
      
      // 获取 Content-Type
      const contentType = response.headers.get('content-type') || '';
      console.log('[ImageCard] 响应Content-Type:', contentType);
      
      // 如果响应是 JSON（错误信息），直接抛出错误
      if (contentType.includes('application/json') || contentType.includes('text/plain')) {
        const text = await response.text();
        console.error('[ImageCard] 收到错误响应:', text);
        toast.error('下载失败: 文件不存在或路径错误');
        return;
      }
      
      const blob = await response.blob();
      downloadBlob(blob, image);
    } catch (error) {
      console.error('[ImageCard] 下载失败:', error);
      toast.error('下载失败');
    }
  };
  
  // 下载 Blob 文件
  const downloadBlob = (blob: Blob, img: ImageItem) => {
    // 从 URL 或 Content-Type 中提取文件扩展名
    let fileExtension = img.fileType || 'jpg';
    const urlExtension = img.url?.split('.').pop()?.toLowerCase();
    if (urlExtension && ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp'].includes(urlExtension)) {
      fileExtension = urlExtension;
    }
    
    const blobUrl = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = blobUrl;
    link.download = img.title || `image-${img.id}.${fileExtension}`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    window.URL.revokeObjectURL(blobUrl);
    toast.success('下载成功');
  };

  // 复制图片链接
  const handleCopyLink = (e: React.MouseEvent) => {
    e.stopPropagation();
    const fullUrl = getFullImageUrl(image.url);
    navigator.clipboard.writeText(fullUrl);
    toast.success('链接已复制到剪贴板');
    setShowMoreMenu(false);
    setMenuView('main');
  };

  // 在新标签页打开
  const handleOpenInNew = (e: React.MouseEvent) => {
    e.stopPropagation();
    const fullUrl = getFullImageUrl(image.url);
    window.open(fullUrl, '_blank');
    setShowMoreMenu(false);
    setMenuView('main');
  };

  // 删除图片
  const handleDelete = (e: React.MouseEvent) => {
    e.stopPropagation();
    setShowMoreMenu(false);
    setMenuView('main');
    onDelete?.(image.id);
  };

  // 编辑图片
  const handleEdit = (e: React.MouseEvent) => {
    e.stopPropagation();
    setShowMoreMenu(false);
    setMenuView('main');
    router.push(`/edit/${image.id}`);
  };

  // 设为主图
  const handleSetMain = async (e: React.MouseEvent) => {
    e.stopPropagation();
    setShowMoreMenu(false);
    setMenuView('main');
    
    try {
      const sessionId = getSessionId();
      const response = await fetch(`/api/images/${image.id}/set-main`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(sessionId ? { 'X-Session-Id': sessionId } : {}),
        },
      });
      
      const result = await response.json();
      
      if (result.success || result.code === 200) {
        toast.success('已设为主图');
        // 刷新页面或更新状态
        window.location.reload();
      } else {
        toast.error(result.message || '操作失败');
      }
    } catch (error) {
      console.error('设为主图失败:', error);
      toast.error('操作失败，请重试');
    }
  };

  // 打开移动到相册子菜单
  const handleMoveClick = (e: React.MouseEvent) => {
    e.stopPropagation();
    setMenuView('albums');
  };

  // 移动到指定相册
  const handleMoveToAlbum = (albumId: string, e: React.MouseEvent) => {
    e.stopPropagation();
    setShowMoreMenu(false);
    setMenuView('main');
    onMoveToAlbum?.(image.id, albumId);
  };

  // 恢复图片
  const handleRestore = (e: React.MouseEvent) => {
    e.stopPropagation();
    setShowMoreMenu(false);
    onRestore?.(image.id);
  };

  // 永久删除
  const handlePermanentDelete = (e: React.MouseEvent) => {
    e.stopPropagation();
    setShowMoreMenu(false);
    if (confirm(`确定永久删除 "${image.title}" 吗？此操作不可撤销。`)) {
      onPermanentDelete?.(image.id);
    }
  };

  // 分享功能
  const shareUrl = getFullImageUrl(image.url);
  const shareTitle = image.title;

  // 打开分享菜单
  const openShareMenu = (e: React.MouseEvent) => {
    e.stopPropagation();
    setMenuView('share');
  };

  // 返回主菜单
  const backToMain = (e: React.MouseEvent) => {
    e.stopPropagation();
    setMenuView('main');
  };

  // 分享到微博
  const shareToWeibo = (e: React.MouseEvent) => {
    e.stopPropagation();
    const url = encodeURIComponent(shareUrl);
    const title = encodeURIComponent(shareTitle);
    window.open(`https://service.weibo.com/share/share.php?url=${url}&title=${title}`, '_blank', 'width=600,height=400');
    setShowMoreMenu(false);
    setMenuView('main');
  };

  // 分享到Twitter
  const shareToTwitter = (e: React.MouseEvent) => {
    e.stopPropagation();
    const url = encodeURIComponent(shareUrl);
    const text = encodeURIComponent(shareTitle);
    window.open(`https://twitter.com/intent/tweet?url=${url}&text=${text}`, '_blank', 'width=600,height=400');
    setShowMoreMenu(false);
    setMenuView('main');
  };

  // 分享到Facebook
  const shareToFacebook = (e: React.MouseEvent) => {
    e.stopPropagation();
    const url = encodeURIComponent(shareUrl);
    window.open(`https://www.facebook.com/sharer/sharer.php?u=${url}`, '_blank', 'width=600,height=400');
    setShowMoreMenu(false);
    setMenuView('main');
  };

  // 分享到LinkedIn
  const shareToLinkedIn = (e: React.MouseEvent) => {
    e.stopPropagation();
    const url = encodeURIComponent(shareUrl);
    const title = encodeURIComponent(shareTitle);
    window.open(`https://www.linkedin.com/shareArticle?mini=true&url=${url}&title=${title}`, '_blank', 'width=600,height=400');
    setShowMoreMenu(false);
    setMenuView('main');
  };

  // 复制分享链接
  const copyShareLink = (e: React.MouseEvent) => {
    e.stopPropagation();
    navigator.clipboard.writeText(shareUrl);
    toast.success('分享链接已复制到剪贴板');
    setShowMoreMenu(false);
    setMenuView('main');
  };

  // 打开创建分享链接弹窗
  const openCreateShareLink = (e: React.MouseEvent) => {
    e.stopPropagation();
    setShowMoreMenu(false);
    setMenuView('main');
    setShowShareDialog(true);
  };

  return (
    <div
      ref={cardRef}
      className={cn(
        'group relative bg-white rounded-2xl transition-all duration-300 hover:shadow-2xl hover:shadow-slate-200/80 hover:-translate-y-1',
        viewMode === 'grid' ? 'aspect-square' : viewMode === 'list' ? 'flex items-center gap-4 p-3' : 'aspect-auto',
        isSelected && 'ring-2 ring-violet-500 ring-offset-2',
        // 菜单打开时移除 overflow-hidden，允许菜单完整显示
        !showMoreMenu && 'overflow-hidden'
      )}
      onMouseEnter={() => setShowActions(true)}
      onMouseLeave={() => {
        setShowActions(false);
      }}
    >
      {/* 收藏动画效果 */}
      {isFavoriting && image.favorite && (
        <div className="absolute inset-0 z-20 flex items-center justify-center pointer-events-none">
          <Heart className="w-20 h-20 text-red-500 fill-red-500 animate-ping" />
        </div>
      )}
      
      {/* 选择框 */}
      <button
        onClick={(e) => {
          e.stopPropagation();
          onSelect(image.id);
        }}
        className={cn(
          'absolute top-3 left-3 z-10 w-6 h-6 rounded-lg border-2 flex items-center justify-center transition-all duration-200',
          isSelected
            ? 'bg-violet-500 border-violet-500'
            : 'bg-white/90 border-slate-300 opacity-0 group-hover:opacity-100'
        )}
      >
        {isSelected && <Check className="w-4 h-4 text-white" />}
      </button>

      {/* 图片 */}
      <div
        className={cn(
          'relative overflow-hidden',
          viewMode === 'grid' ? 'aspect-square' : viewMode === 'list' ? 'w-20 h-20 shrink-0 rounded-xl' : 'aspect-auto'
        )}
      >
        <div
          className={cn(
            'absolute inset-0 bg-slate-200 animate-pulse',
            imageLoaded && 'hidden'
          )}
        />
        {viewMode === 'grid' ? (
          <Image
            src={getFullImageUrl(image.url)}
            alt={image.title}
            fill
            unoptimized  // 跳过 Next.js 图片优化，直接使用原始 URL
            className={cn(
              'object-cover transition-all duration-500',
              showActions && 'scale-105',
              imageLoaded ? 'opacity-100' : 'opacity-0'
            )}
            onLoad={() => setImageLoaded(true)}
          />
        ) : viewMode === 'list' ? (
          <Image
            src={getFullImageUrl(image.url)}
            alt={image.title}
            fill
            unoptimized
            className={cn(
              'object-cover transition-all duration-300',
              imageLoaded ? 'opacity-100' : 'opacity-0'
            )}
            onLoad={() => setImageLoaded(true)}
          />
        ) : (
          <Image
            src={getFullImageUrl(image.url)}
            alt={image.title}
            width={400}
            height={300}
            unoptimized  // 跳过 Next.js 图片优化，直接使用原始 URL
            className={cn(
              'w-full h-auto object-cover transition-all duration-500',
              showActions && 'scale-105',
              imageLoaded ? 'opacity-100' : 'opacity-0'
            )}
            onLoad={() => setImageLoaded(true)}
          />
        )}
        
        {/* 遮罩层 */}
        <div
          className={cn(
            'absolute inset-0 bg-gradient-to-t from-black/60 via-transparent to-transparent transition-opacity duration-300',
            showActions ? 'opacity-100' : 'opacity-0'
          )}
        />

        {/* 操作按钮 */}
        <div
          className={cn(
            'absolute top-3 right-3 z-10 flex gap-2 transition-all duration-200',
            showActions ? 'opacity-100 translate-y-0' : 'opacity-0 -translate-y-2'
          )}
        >
          {isTrash ? (
            // 回收站模式：显示恢复和永久删除按钮
            <>
              <button
                onClick={handleRestore}
                className="p-2 bg-green-500/90 backdrop-blur-sm rounded-lg shadow-lg hover:bg-green-500 transition-colors"
                title="恢复图片"
              >
                <ArrowLeft className="w-4 h-4 text-white rotate-180" />
              </button>
              <button
                onClick={handlePermanentDelete}
                className="p-2 bg-red-500/90 backdrop-blur-sm rounded-lg shadow-lg hover:bg-red-500 transition-colors"
                title="永久删除"
              >
                <Trash2 className="w-4 h-4 text-white" />
              </button>
            </>
          ) : (
            // 正常模式：显示收藏、下载、更多操作
            <>
              <button
                onClick={handleFavoriteClick}
                className={cn(
                  'p-2 backdrop-blur-sm rounded-lg shadow-lg transition-all duration-300',
                  image.favorite 
                    ? 'bg-red-50 hover:bg-red-100 ring-2 ring-red-200' 
                    : 'bg-white/90 hover:bg-white'
                )}
                title={image.favorite ? '取消收藏' : '添加收藏'}
              >
                <Heart
                  className={cn(
                    'w-4 h-4 transition-all duration-300',
                    image.favorite 
                      ? 'fill-red-500 text-red-500 scale-110' 
                      : 'text-slate-600 hover:text-red-400'
                  )}
                />
              </button>
              <button
                onClick={handleDownload}
                className="p-2 bg-white/90 backdrop-blur-sm rounded-lg shadow-lg hover:bg-white transition-colors"
                title="下载图片"
              >
                <Download className="w-4 h-4 text-slate-600" />
              </button>
              <button
                ref={menuButtonRef}
                onClick={(e) => {
                  e.stopPropagation();
                  if (showMoreMenu) {
                    setShowMoreMenu(false);
                    setMenuView('main');
                  } else {
                    // 计算菜单位置
                    const rect = (e.target as HTMLElement).getBoundingClientRect();
                    setMenuPosition({
                      top: rect.bottom + 8,
                      left: rect.right - (menuView === 'albums' ? 224 : 192),
                    });
                    setShowMoreMenu(true);
                    setMenuView('main');
                  }
                }}
                className="p-2 bg-white/90 backdrop-blur-sm rounded-lg shadow-lg hover:bg-white transition-colors"
                title="更多操作"
              >
                <MoreVertical className="w-4 h-4 text-slate-600" />
              </button>
            </>
          )}
        </div>

        {/* 底部信息 - 非list视图 */}
        {viewMode !== 'list' && showFileInfo && (
          <div
            className={cn(
              'absolute bottom-0 left-0 right-0 p-4 transition-all duration-300',
              showActions ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-4'
            )}
          >
            <h3 className="text-white font-medium text-sm truncate mb-1">
              <HighlightedText
                text={image.title}
                query={searchQuery}
                highlightClassName="bg-yellow-300 text-yellow-900 px-0.5 rounded"
              />
            </h3>
            <div className="flex items-center gap-3 text-white/80 text-xs">
              <span>{image.resolution}</span>
              <span>•</span>
              <span>{image.size}</span>
            </div>
          </div>
        )}
      </div>

      {/* List 视图的文件信息 */}
      {viewMode === 'list' && showFileInfo && (
        <div className="flex-1 min-w-0">
          <h3 className="text-slate-800 font-medium text-sm truncate mb-1">
            <HighlightedText
              text={image.title}
              query={searchQuery}
              highlightClassName="bg-yellow-200 text-yellow-900 px-0.5 rounded"
            />
          </h3>
          <div className="flex items-center gap-3 text-slate-500 text-xs">
            <span>{image.resolution}</span>
            <span>•</span>
            <span>{image.size}</span>
            <span>•</span>
            <span>{image.date}</span>
          </div>
          {image.albumName && (
            <div className="flex items-center gap-1 mt-1 text-xs text-violet-600">
              <FolderOpen className="w-3 h-3" />
              <span>{image.albumName}</span>
            </div>
          )}
          {image.tags && image.tags.length > 0 && searchQuery && (
            <div className="mt-2">
              <HighlightedTags
                tags={image.tags}
                query={searchQuery}
              />
            </div>
          )}
        </div>
      )}

      {/* List 视图的操作按钮 */}
      {viewMode === 'list' && (
        <div className="flex items-center gap-2 shrink-0">
          {!isTrash && (
            <button
              onClick={handleFavoriteClick}
              className={cn(
                'p-2 rounded-lg transition-colors',
                image.favorite 
                  ? 'bg-red-50 text-red-500' 
                  : 'bg-slate-100 text-slate-500 hover:bg-slate-200'
              )}
            >
              <Heart className={cn('w-4 h-4', image.favorite && 'fill-current')} />
            </button>
          )}
          {!isTrash && (
            <button
              onClick={handleDownload}
              className="p-2 bg-slate-100 text-slate-500 rounded-lg hover:bg-slate-200 transition-colors"
            >
              <Download className="w-4 h-4" />
            </button>
          )}
          {isTrash ? (
            <>
              <button
                onClick={handleRestore}
                className="p-2 bg-green-100 text-green-600 rounded-lg hover:bg-green-200 transition-colors"
                title="恢复图片"
              >
                <ArrowLeft className="w-4 h-4" />
              </button>
              <button
                onClick={handlePermanentDelete}
                className="p-2 bg-red-100 text-red-500 rounded-lg hover:bg-red-200 transition-colors"
                title="永久删除"
              >
                <Trash2 className="w-4 h-4" />
              </button>
            </>
          ) : (
            <button
              onClick={handleDelete}
              className="p-2 bg-slate-100 text-slate-500 rounded-lg hover:bg-red-100 hover:text-red-500 transition-colors"
            >
              <Trash2 className="w-4 h-4" />
            </button>
          )}
        </div>
      )}

      {/* 更多操作菜单 - 使用 Portal 渲染到 body，避免被父容器限制 */}
      {!isTrash && showMoreMenu && typeof window !== 'undefined' && createPortal(
        <div 
          ref={menuRef}
          className={cn(
            "fixed bg-white rounded-xl shadow-2xl border border-slate-200/60 animate-in fade-in slide-in-from-top-2 duration-200 z-[9999]",
            menuView === 'albums' ? 'w-56' : 'w-48'
          )}
          style={{
            top: menuPosition.top,
            left: menuPosition.left,
          }}
          onClick={(e) => e.stopPropagation()}
        >
          {menuView === 'main' ? (
            <>
              <div className="p-1">
                <button
                  onClick={handleEdit}
                  className="w-full flex items-center gap-3 px-3 py-2.5 rounded-lg hover:bg-violet-50 transition-colors text-left group"
                >
                  <Edit3 className="w-4 h-4 text-slate-500 group-hover:text-violet-500" />
                  <span className="text-sm text-slate-700 group-hover:text-violet-600">编辑图片</span>
                </button>
                <button
                  onClick={handleMoveClick}
                  className="w-full flex items-center gap-3 px-3 py-2.5 rounded-lg hover:bg-slate-50 transition-colors text-left"
                >
                  <Move className="w-4 h-4 text-slate-500" />
                  <span className="text-sm text-slate-700 flex-1">移动到相册</span>
                  <ChevronRight className="w-4 h-4 text-slate-400" />
                </button>
                {/* 设为主图按钮 - 只在有productId且非主图时显示 */}
                {(() => {
                  // 调试日志
                  if (image.productId) {
                    console.log('[ImageCard] 图片:', image.title, 'productId:', image.productId, 'isMainImage:', image.isMainImage);
                  }
                  return image.productId && !image.isMainImage;
                })() && (
                  <button
                    onClick={handleSetMain}
                    className="w-full flex items-center gap-3 px-3 py-2.5 rounded-lg hover:bg-amber-50 transition-colors text-left group"
                  >
                    <Star className="w-4 h-4 text-slate-500 group-hover:text-amber-500" />
                    <span className="text-sm text-slate-700 group-hover:text-amber-600">设为主图</span>
                  </button>
                )}
                <button
                  onClick={handleCopyLink}
                  className="w-full flex items-center gap-3 px-3 py-2.5 rounded-lg hover:bg-slate-50 transition-colors text-left"
                >
                  <Copy className="w-4 h-4 text-slate-500" />
                  <span className="text-sm text-slate-700">复制链接</span>
                </button>
                <button
                  onClick={handleOpenInNew}
                  className="w-full flex items-center gap-3 px-3 py-2.5 rounded-lg hover:bg-slate-50 transition-colors text-left"
                >
                  <ExternalLink className="w-4 h-4 text-slate-500" />
                  <span className="text-sm text-slate-700">新标签页打开</span>
                </button>
                <button
                  onClick={openShareMenu}
                  className="w-full flex items-center gap-3 px-3 py-2.5 rounded-lg hover:bg-slate-50 transition-colors text-left"
                >
                  <Share2 className="w-4 h-4 text-slate-500" />
                  <span className="text-sm text-slate-700">分享</span>
                </button>
              </div>
              <div className="p-1 border-t border-slate-100">
                <button
                  onClick={handleDelete}
                  className="w-full flex items-center gap-3 px-3 py-2.5 rounded-lg hover:bg-red-50 transition-colors text-left group"
                >
                  <Trash2 className="w-4 h-4 text-slate-500 group-hover:text-red-500" />
                  <span className="text-sm text-slate-700 group-hover:text-red-600">删除</span>
                </button>
              </div>
            </>
          ) : menuView === 'share' ? (
            <>
              {/* 分享子菜单 */}
              <div className="p-2 border-b border-slate-100 flex items-center gap-2">
                <button
                  onClick={backToMain}
                  className="p-1 rounded-lg hover:bg-slate-100 transition-colors"
                >
                  <ArrowLeft className="w-4 h-4 text-slate-500" />
                </button>
                <span className="text-sm font-medium text-slate-700">分享到</span>
              </div>
              <div className="p-1">
                <button
                  onClick={shareToWeibo}
                  className="w-full flex items-center gap-3 px-3 py-2.5 rounded-lg hover:bg-orange-50 transition-colors text-left group"
                >
                  <div className="w-6 h-6 rounded-full bg-orange-500 flex items-center justify-center">
                    <span className="text-white text-xs font-bold">微</span>
                  </div>
                  <span className="text-sm text-slate-700 group-hover:text-orange-600">新浪微博</span>
                </button>
                <button
                  onClick={shareToTwitter}
                  className="w-full flex items-center gap-3 px-3 py-2.5 rounded-lg hover:bg-sky-50 transition-colors text-left group"
                >
                  <Twitter className="w-6 h-6 text-sky-500" />
                  <span className="text-sm text-slate-700 group-hover:text-sky-600">Twitter</span>
                </button>
                <button
                  onClick={shareToFacebook}
                  className="w-full flex items-center gap-3 px-3 py-2.5 rounded-lg hover:bg-blue-50 transition-colors text-left group"
                >
                  <Facebook className="w-6 h-6 text-blue-600" />
                  <span className="text-sm text-slate-700 group-hover:text-blue-600">Facebook</span>
                </button>
                <button
                  onClick={shareToLinkedIn}
                  className="w-full flex items-center gap-3 px-3 py-2.5 rounded-lg hover:bg-blue-50 transition-colors text-left group"
                >
                  <Linkedin className="w-6 h-6 text-blue-700" />
                  <span className="text-sm text-slate-700 group-hover:text-blue-700">LinkedIn</span>
                </button>
              </div>
              <div className="p-1 border-t border-slate-100">
                <button
                  onClick={copyShareLink}
                  className="w-full flex items-center gap-3 px-3 py-2.5 rounded-lg hover:bg-violet-50 transition-colors text-left group"
                >
                  <Copy className="w-6 h-6 text-slate-500" />
                  <span className="text-sm text-slate-700 group-hover:text-violet-600">复制分享链接</span>
                </button>
                <button
                  onClick={openCreateShareLink}
                  className="w-full flex items-center gap-3 px-3 py-2.5 rounded-lg hover:bg-violet-50 transition-colors text-left group"
                >
                  <Share2 className="w-6 h-6 text-violet-500" />
                  <span className="text-sm text-slate-700 group-hover:text-violet-600">创建分享链接</span>
                </button>
              </div>
            </>
          ) : menuView === 'albums' ? (
            <>
              {/* 相册子菜单 */}
              <div className="p-2 border-b border-slate-100 flex items-center gap-2 sticky top-0 bg-white z-10">
                <button
                  onClick={backToMain}
                  className="p-1 rounded-lg hover:bg-slate-100 transition-colors"
                >
                  <ArrowLeft className="w-4 h-4 text-slate-500" />
                </button>
                <span className="text-sm font-medium text-slate-700">移动到相册</span>
              </div>
              <div className="overflow-y-auto" style={{ maxHeight: '320px' }}>
                {albums.length === 0 ? (
                  <div className="px-3 py-4 text-center text-sm text-slate-500">
                    暂无相册
                  </div>
                ) : (
                  albums.map((album) => (
                    <button
                      key={album.id}
                      onClick={(e) => handleMoveToAlbum(album.id, e)}
                      className={cn(
                        "w-full flex items-center gap-3 px-3 py-2.5 rounded-lg hover:bg-violet-50 transition-colors text-left group",
                        image.albumId === album.id && "bg-violet-50"
                      )}
                    >
                      <div className="w-8 h-8 rounded-lg bg-slate-100 overflow-hidden flex items-center justify-center flex-shrink-0">
                        {album.coverUrl ? (
                          <img 
                            src={album.coverUrl} 
                            alt={album.name}
                            className="w-full h-full object-cover"
                          />
                        ) : (
                          <FolderOpen className="w-4 h-4 text-slate-400" />
                        )}
                      </div>
                      <div className="flex-1 min-w-0">
                        <div className="text-sm text-slate-700 group-hover:text-violet-600 truncate">
                          {album.name}
                        </div>
                        {album.imageCount !== undefined && (
                          <div className="text-xs text-slate-400">
                            {album.imageCount} 张图片
                          </div>
                        )}
                      </div>
                      {image.albumId === album.id && (
                        <Check className="w-4 h-4 text-violet-500 flex-shrink-0" />
                      )}
                    </button>
                  ))
                )}
              </div>
            </>
          ) : null}
        </div>,
        document.body
      )}

      {/* 点击预览 */}
      <button
        onClick={() => onPreview(image)}
        className="absolute inset-0 z-0"
        aria-label="预览图片"
      />

      {/* 标签 */}
      {image.tags && image.tags.length > 0 && (
        <div className="absolute bottom-3 left-3 right-3 flex gap-1.5 flex-wrap opacity-0 group-hover:opacity-100 transition-opacity">
          {image.tags.slice(0, 3).map((tag) => (
            <span
              key={tag}
              className="px-2 py-1 text-xs bg-white/90 backdrop-blur-sm text-slate-700 rounded-md font-medium"
            >
              {tag}
            </span>
          ))}
        </div>
      )}

      {/* 创建分享链接弹窗 */}
      {showShareDialog && (
        <ShareDialog
          open={showShareDialog}
          resourceType="image"
          resourceId={image.id}
          resourceName={image.title}
          onClose={() => setShowShareDialog(false)}
        />
      )}

    </div>
  );
}
