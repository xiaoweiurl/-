'use client';

import React from 'react';
import Image from 'next/image';
import { useRouter } from 'next/navigation';

import { cn } from '@/lib/utils';
import { Button } from '@/components/ui/button';
import {
  X,
  ChevronLeft,
  ChevronRight,
  Heart,
  Download,
  Share2,
  Info,
  ZoomIn,
  ZoomOut,
  RotateCw,
  Trash2,
  Edit3,
  Star,
} from 'lucide-react';
import { toast } from 'sonner';
import type { ImageItem } from './ImageCard';
import { getSessionId } from '@/lib/auth-client';

interface ImagePreviewProps {
  image: ImageItem | null;
  images: ImageItem[];
  onClose: () => void;
  onNavigate: (image: ImageItem) => void;
  onToggleFavorite: (id: string) => void;
  productId?: string; // 商品ID，用于加载该商品的所有图片
}

export default function ImagePreview({
  image,
  images,
  onClose,
  onNavigate,
  onToggleFavorite,
  productId,
}: ImagePreviewProps) {
  const router = useRouter();
  const [showInfo, setShowInfo] = React.useState(false);
  const [zoom, setZoom] = React.useState(1);
  const [rotation, setRotation] = React.useState(0);
  const [productImages, setProductImages] = React.useState<ImageItem[]>([]); // 该商品的所有图片
  const [loading, setLoading] = React.useState(false);

  // 调试：打印当前图片信息
  React.useEffect(() => {
    if (image) {
      console.log('[ImagePreview] 当前图片信息:', {
        title: image.title,
        productId: image.productId,
        isMainImage: image.isMainImage,
        id: image.id
      });
    }
  }, [image]);

  // 记录预览次数
  React.useEffect(() => {
    if (image?.id) {
      fetch(`http://localhost:8080/images/${image.id}/view`, { 
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
      }).catch(err => console.error('[ImagePreview] 记录预览失败:', err));
    }
  }, [image?.id]);

  // 获取完整的图片 URL（静态资源直接用 localhost:8080）
  const getFullImageUrl = (url: string): string => {
    if (url.startsWith('http://') || url.startsWith('https://')) {
      return url;
    }
    if (url.startsWith('/')) {
      return `http://localhost:8080${url}`;
    }
    return url;
  };

  // 下载图片
  const handleDownload = async () => {
    if (!image) return;
    
    try {
      const fullUrl = getFullImageUrl(image.url);
      const sessionId = getSessionId();
      
      // 如果是旧格式的沙箱 URL，提示用户重新上传
      if (fullUrl.includes('sandbox/coze_coding/file/proxy')) {
        toast.error('旧格式图片不支持下载，请删除后重新上传');
        return;
      }
      
      // 如果是相对路径，使用 API 代理
      let downloadUrl = fullUrl;
      if (fullUrl.includes('/uploads/') && !fullUrl.startsWith('http')) {
        downloadUrl = `/api/images/${image.id}/file`;
      }
      
      const response = await fetch(downloadUrl, {
        headers: {
          'X-Session-Id': sessionId || '',
        },
        credentials: 'include',
      });
      
      if (!response.ok) {
        if (response.status === 410) {
          toast.error('图片路径格式不支持，请删除后重新上传');
          return;
        }
        toast.error(`下载失败: ${response.status}`);
        return;
      }
      
      const contentType = response.headers.get('content-type') || '';
      if (contentType.includes('application/json') || contentType.includes('text/plain')) {
        toast.error('下载失败: 文件不存在或路径错误');
        return;
      }
      
      const blob = await response.blob();
      
      // 下载文件
      let fileExtension = image.fileType || 'jpg';
      const urlExtension = image.url?.split('.').pop()?.toLowerCase();
      if (urlExtension && ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp'].includes(urlExtension)) {
        fileExtension = urlExtension;
      }
      
      const blobUrl = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = blobUrl;
      link.download = image.title || `image-${image.id}.${fileExtension}`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.URL.revokeObjectURL(blobUrl);
      toast.success('下载成功');
    } catch (error) {
      console.error('[ImagePreview] 下载失败:', error);
      toast.error('下载失败');
    }
  };

  // 设为主图
  const handleSetAsMainImage = async () => {
    console.log('[ImagePreview] 设为主图点击 - image:', JSON.stringify(image, null, 2));
    if (!image?.productId) {
      toast.error('该图片没有关联商品ID，无法设为主图');
      return;
    }
    if (image?.isMainImage) {
      toast.error('当前图片已经是主图');
      return;
    }

    try {
      const sessionId = getSessionId();
      const response = await fetch(`/api/images/${image.id}/set-main`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-Session-Id': sessionId || '',
        },
        credentials: 'include',
      });

      const result = await response.json();

      if (result.success || result.code === 200) {
        toast.success('已设为主图');
        // 刷新页面
        router.refresh();
      } else {
        toast.error(result.message || '设置失败');
      }
    } catch (error) {
      console.error('[ImagePreview] 设为主图失败:', error);
      toast.error('设置失败');
    }
  };

  // 如果有 productId，加载该商品的所有图片
  React.useEffect(() => {
    console.log('[ImagePreview] useEffect productId:', productId, 'image:', image);
    if (productId) {
      loadProductImages(productId);
    }
  }, [productId]);

  // 加载商品的所有图片
  const loadProductImages = async (pid: string) => {
    try {
      setLoading(true);
      console.log('[ImagePreview] 加载商品图片，productId:', pid);
      const response = await fetch(`/api/products/${pid}/images`, {
        credentials: 'include',
      });
      const result = await response.json();

      if (result.success && result.data) {
        console.log('[ImagePreview] 加载到商品图片:', result.data.length, '张');
        // 转换为 ImageItem 格式
        const items: ImageItem[] = result.data.map((img: any) => ({
          id: img.id,
          url: img.url,
          title: img.title,
          size: img.sizeFormatted,
          resolution: img.resolution,
          date: img.createdAt,
          favorite: img.favorite,
          tags: img.tags || [],
          albumId: img.albumId,
          albumName: img.albumName,
          fileType: img.fileType,
          isMainImage: img.isMainImage,
          productId: img.productId,
        }));
        setProductImages(items);
      } else {
        console.warn('[ImagePreview] 加载商品图片失败，使用传入的图片列表');
        setProductImages([]);
      }
    } catch (error) {
      console.error('[ImagePreview] 加载商品图片失败:', error);
      setProductImages([]);
    } finally {
      setLoading(false);
    }
  };

  // 如果有 productId 且已加载成功，使用 productImages；否则使用传入的 images
  const displayImages = productId && productImages.length > 0 ? productImages : images;

  if (!image) return null;

  const currentIndex = displayImages.findIndex((img) => img.id === image.id);
  const hasPrev = currentIndex > 0;
  const hasNext = currentIndex < displayImages.length - 1;

  const handlePrev = () => {
    if (hasPrev) {
      onNavigate(displayImages[currentIndex - 1]);
      setZoom(1);
      setRotation(0);
    }
  };

  const handleNext = () => {
    if (hasNext) {
      onNavigate(displayImages[currentIndex + 1]);
      setZoom(1);
      setRotation(0);
    }
  };

  const handleZoomIn = () => setZoom((prev) => Math.min(prev + 0.25, 3));
  const handleZoomOut = () => setZoom((prev) => Math.max(prev - 0.25, 0.5));
  const handleRotate = () => setRotation((prev) => prev + 90);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/95 backdrop-blur-xl animate-in fade-in duration-200">
      {/* 关闭按钮 */}
      <button
        onClick={onClose}
        className="absolute top-4 right-4 z-10 p-2 text-white/60 hover:text-white bg-white/10 hover:bg-white/20 rounded-lg transition-colors"
      >
        <X className="w-6 h-6" />
      </button>

      {/* 左右导航 */}
      {hasPrev && (
        <button
          onClick={handlePrev}
          className="absolute left-4 top-1/2 -translate-y-1/2 z-10 p-3 text-white/60 hover:text-white bg-white/10 hover:bg-white/20 rounded-full transition-colors"
        >
          <ChevronLeft className="w-8 h-8" />
        </button>
      )}
      {hasNext && (
        <button
          onClick={handleNext}
          className="absolute right-4 top-1/2 -translate-y-1/2 z-10 p-3 text-white/60 hover:text-white bg-white/10 hover:bg-white/20 rounded-full transition-colors"
        >
          <ChevronRight className="w-8 h-8" />
        </button>
      )}

      {/* 图片区域 */}
      <div className="relative w-full h-full flex items-center justify-center p-16">
        <div
          className="relative transition-transform duration-300"
          style={{
            transform: `scale(${zoom}) rotate(${rotation}deg)`,
          }}
        >
          <Image
            src={getFullImageUrl(image.url)}
            alt={image.title}
            width={1200}
            height={800}
            unoptimized  // 跳过 Next.js 图片优化，直接使用原始 URL
            className="max-w-full max-h-[80vh] object-contain rounded-lg shadow-2xl"
          />
        </div>
      </div>

      {/* 底部工具栏 */}
      <div className="absolute bottom-0 left-0 right-0 bg-gradient-to-t from-black/80 to-transparent pt-16 pb-6 px-6">
        <div className="max-w-7xl mx-auto flex items-center justify-between">
          {/* 图片信息 */}
          <div className="flex items-center gap-4">
            <div>
              <h2 className="text-white font-medium text-lg">{image.title}</h2>
              <div className="flex items-center gap-3 text-white/60 text-sm mt-1">
                <span>{image.resolution}</span>
                <span>•</span>
                <span>{image.size}</span>
                <span>•</span>
                <span>{image.date}</span>
              </div>
            </div>
          </div>

          {/* 操作按钮 */}
          <div className="flex items-center gap-2">
            {/* 缩放控制 */}
            <div className="flex items-center gap-1 bg-white/10 rounded-lg p-1 mr-2">
              <button
                onClick={handleZoomOut}
                className="p-2 text-white/60 hover:text-white hover:bg-white/10 rounded transition-colors"
              >
                <ZoomOut className="w-5 h-5" />
              </button>
              <span className="text-white text-sm px-2 min-w-[4rem] text-center">
                {Math.round(zoom * 100)}%
              </span>
              <button
                onClick={handleZoomIn}
                className="p-2 text-white/60 hover:text-white hover:bg-white/10 rounded transition-colors"
              >
                <ZoomIn className="w-5 h-5" />
              </button>
            </div>

            {/* 旋转 */}
            <Button
              variant="ghost"
              size="sm"
              onClick={handleRotate}
              className="text-white/60 hover:text-white hover:bg-white/10"
            >
              <RotateCw className="w-5 h-5" />
            </Button>

            {/* 编辑 */}
            <Button
              variant="ghost"
              size="sm"
              onClick={() => image && router.push(`/edit/${image.id}`)}
              className="text-white/60 hover:text-white hover:bg-white/10"
            >
              <Edit3 className="w-5 h-5" />
            </Button>

            {/* 信息 */}
            <Button
              variant="ghost"
              size="sm"
              onClick={() => setShowInfo(!showInfo)}
              className="text-white/60 hover:text-white hover:bg-white/10"
            >
              <Info className="w-5 h-5" />
            </Button>

            <div className="w-px h-6 bg-white/20 mx-2" />

            {/* 收藏 */}
            <Button
              variant="ghost"
              size="sm"
              onClick={() => onToggleFavorite(image.id)}
              className="text-white/60 hover:text-white hover:bg-white/10"
            >
              <Heart
                className={cn('w-5 h-5', image.favorite && 'fill-red-500 text-red-500')}
              />
            </Button>

            {/* 分享 */}
            <Button
              variant="ghost"
              size="sm"
              className="text-white/60 hover:text-white hover:bg-white/10"
            >
              <Share2 className="w-5 h-5" />
            </Button>

            {/* 下载 */}
            <Button
              variant="ghost"
              size="sm"
              className="text-white/60 hover:text-white hover:bg-white/10"
              onClick={handleDownload}
            >
              <Download className="w-5 h-5" />
            </Button>

            {/* 设为主图按钮 */}
            <Button
              variant="ghost"
              size="sm"
              className="text-white/60 hover:text-yellow-400 hover:bg-white/10"
              onClick={handleSetAsMainImage}
              title="设为主图"
            >
              <Star className="w-5 h-5" />
            </Button>

            {/* 删除 */}
            <Button
              variant="ghost"
              size="sm"
              className="text-white/60 hover:text-red-400 hover:bg-white/10"
            >
              <Trash2 className="w-5 h-5" />
            </Button>
          </div>
        </div>
      </div>

      {/* 图片计数 */}
      <div className="absolute top-4 left-1/2 -translate-x-1/2 px-4 py-2 bg-white/10 backdrop-blur-sm rounded-full text-white/60 text-sm">
        {currentIndex + 1} / {images.length}
      </div>

    </div>
  );
}
