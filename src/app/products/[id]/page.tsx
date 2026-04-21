'use client';

import React from 'react';
import { useRouter, useParams } from 'next/navigation';
import Image from 'next/image';
import { ArrowLeft, Heart, Share2, Download, Grid3X3, ChevronLeft, ChevronRight, DownloadCloud, Image as ImageIcon } from 'lucide-react';
import { toast } from 'sonner';
import { cn } from '@/lib/utils';
import { downloadSingleFile, downloadProductImages } from './download-utils';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
  DropdownMenuSeparator,
} from '@/components/ui/dropdown-menu';

interface Product {
  id: string;
  name: string;
  description: string;
  category: string;
  imageCount: number;
  coverImageId: string;
  createdAt: string;
}

interface ProductImage {
  id: string;
  title: string;
  url: string;
  thumbnailUrl: string;
  size: string;
  resolution: string;
  fileType: string;
  favorite: boolean;
  tags?: string[];
  isMainImage?: boolean;
  displayOrder: number;
  description?: string;
}

export default function ProductDetailPage() {
  const router = useRouter();
  const params = useParams();
  const productId = params.id as string;

  const [loading, setLoading] = React.useState(true);
  const [product, setProduct] = React.useState<Product | null>(null);
  const [images, setImages] = React.useState<ProductImage[]>([]);
  const [selectedImageIndex, setSelectedImageIndex] = React.useState(0);
  const [viewMode, setViewMode] = React.useState<'single' | 'grid'>('single');

  // 获取商品详情
  React.useEffect(() => {
    const fetchProductDetail = async () => {
      try {
        const response = await fetch(`/api/products/${productId}`);
        const result = await response.json();

        if (result.success && result.data) {
          setProduct(result.data.product);
          setImages(result.data.images || []);
          // 默认选中第一张图片（通常是主图）
          if (result.data.images && result.data.images.length > 0) {
            const mainImageIndex = result.data.images.findIndex((img: ProductImage) => img.isMainImage);
            setSelectedImageIndex(mainImageIndex >= 0 ? mainImageIndex : 0);
          }
        } else {
          toast.error('获取商品详情失败');
        }
      } catch (error) {
        console.error('获取商品详情失败:', error);
        toast.error('获取商品详情失败');
      } finally {
        setLoading(false);
      }
    };

    if (productId) {
      fetchProductDetail();
    }
  }, [productId]);

  const handleImageClick = (index: number) => {
    setSelectedImageIndex(index);
  };

  const handleFavoriteToggle = async (imageId: string) => {
    try {
      const response = await fetch(`/api/images/${imageId}/favorite`, {
        method: 'POST',
      });

      if (response.ok) {
        setImages(images.map(img =>
          img.id === imageId ? { ...img, favorite: !img.favorite } : img
        ));
        toast.success('收藏状态已更新');
      }
    } catch (error) {
      console.error('更新收藏状态失败:', error);
      toast.error('更新收藏状态失败');
    }
  };

  // 下载单个图片
  const handleDownload = async (imageUrl: string, fileName: string) => {
    const success = await downloadSingleFile(imageUrl, fileName);
    if (success) {
      toast.success('开始下载', {
        description: fileName,
      });
    } else {
      toast.error('下载失败', {
        description: fileName,
      });
    }
  };

  // 批量下载主图和详情图
  const handleBatchDownload = async () => {
    if (images.length === 0) {
      toast.error('没有可下载的图片');
      return;
    }

    const mainImages = images.filter(img => img.isMainImage);
    const detailImages = images.filter(img => !img.isMainImage);

    toast.loading('正在下载...', {
      id: 'batch-download',
      description: `准备下载 ${mainImages.length} 张主图和 ${detailImages.length} 张详情图`,
    });

    const result = await downloadProductImages(images, (current, total, fileName) => {
      toast.loading('正在下载...', {
        id: 'batch-download',
        description: `正在下载 (${current}/${total}): ${fileName}`,
      });
    });

    toast.success('下载完成', {
      id: 'batch-download',
      description: `成功下载 ${result.success} 张图片${result.failed > 0 ? `，失败 ${result.failed} 张` : ''}`,
    });
  };

  // 下载所有主图
  const handleDownloadMainImages = async () => {
    const mainImages = images.filter(img => img.isMainImage);
    if (mainImages.length === 0) {
      toast.error('没有主图可下载');
      return;
    }

    toast.loading('正在下载主图...', {
      id: 'download-main',
      description: `准备下载 ${mainImages.length} 张主图`,
    });

    const result = await downloadProductImages(mainImages, (current, total, fileName) => {
      toast.loading('正在下载主图...', {
        id: 'download-main',
        description: `正在下载 (${current}/${total}): ${fileName}`,
      });
    });

    toast.success('主图下载完成', {
      id: 'download-main',
      description: `成功下载 ${result.success} 张主图${result.failed > 0 ? `，失败 ${result.failed} 张` : ''}`,
    });
  };

  // 下载所有详情图
  const handleDownloadDetailImages = async () => {
    const detailImages = images.filter(img => !img.isMainImage);
    if (detailImages.length === 0) {
      toast.error('没有详情图可下载');
      return;
    }

    toast.loading('正在下载详情图...', {
      id: 'download-detail',
      description: `准备下载 ${detailImages.length} 张详情图`,
    });

    const result = await downloadProductImages(detailImages, (current, total, fileName) => {
      toast.loading('正在下载详情图...', {
        id: 'download-detail',
        description: `正在下载 (${current}/${total}): ${fileName}`,
      });
    });

    toast.success('详情图下载完成', {
      id: 'download-detail',
      description: `成功下载 ${result.success} 张详情图${result.failed > 0 ? `，失败 ${result.failed} 张` : ''}`,
    });
  };

  const handleShare = () => {
    const shareUrl = `${window.location.origin}/products/${productId}`;
    navigator.clipboard.writeText(shareUrl);
    toast.success('链接已复制到剪贴板');
  };

  const selectedImage = images[selectedImageIndex];
  const mainImages = images.filter(img => img.isMainImage);
  const detailImages = images.filter(img => !img.isMainImage);

  if (loading) {
    return (
      <div className="min-h-screen bg-slate-50 flex items-center justify-center">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-violet-600 mx-auto mb-4"></div>
          <p className="text-slate-600">加载中...</p>
        </div>
      </div>
    );
  }

  if (!product) {
    return (
      <div className="min-h-screen bg-slate-50 flex items-center justify-center">
        <div className="text-center">
          <p className="text-slate-600 mb-4">商品不存在</p>
          <button
            onClick={() => router.back()}
            className="px-4 py-2 bg-violet-600 text-white rounded-lg hover:bg-violet-700 transition-colors"
          >
            返回
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-50">
      {/* 顶部导航 */}
      <header className="bg-white border-b border-slate-200 sticky top-0 z-10">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between">
          <button
            onClick={() => router.back()}
            className="flex items-center gap-2 text-slate-600 hover:text-violet-600 transition-colors"
          >
            <ArrowLeft className="w-5 h-5" />
            <span className="font-medium">返回</span>
          </button>
          <div className="flex items-center gap-2">
            <button
              onClick={handleShare}
              className="p-2 text-slate-600 hover:text-violet-600 hover:bg-violet-50 rounded-lg transition-all"
            >
              <Share2 className="w-5 h-5" />
            </button>
          </div>
        </div>
      </header>

      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {/* 视图切换按钮 */}
        <div className="mb-6 flex items-center gap-2 bg-white rounded-xl p-1 shadow-sm w-fit">
          <button
            onClick={() => setViewMode('single')}
            className={cn(
              'px-4 py-2 rounded-lg text-sm font-medium transition-all',
              viewMode === 'single'
                ? 'bg-violet-600 text-white shadow-sm'
                : 'text-slate-600 hover:text-violet-600 hover:bg-violet-50'
            )}
          >
            单图浏览
          </button>
          <button
            onClick={() => setViewMode('grid')}
            className={cn(
              'px-4 py-2 rounded-lg text-sm font-medium transition-all flex items-center gap-2',
              viewMode === 'grid'
                ? 'bg-violet-600 text-white shadow-sm'
                : 'text-slate-600 hover:text-violet-600 hover:bg-violet-50'
            )}
          >
            <Grid3X3 className="w-4 h-4" />
            详情图
          </button>
        </div>

        {viewMode === 'single' ? (
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
            {/* 左侧图片展示区 */}
            <div className="space-y-4">
              {/* 主图展示 */}
              <div className="relative bg-white rounded-2xl shadow-lg overflow-hidden aspect-square">
                {selectedImage && (
                  <>
                    <div className="relative w-full h-full">
                      <img
                        src={selectedImage.url}
                        alt={selectedImage.title}
                        className="w-full h-full object-contain"
                      />

                      {/* 左右导航按钮 */}
                      {images.length > 1 && (
                        <>
                          {/* 上一张按钮 */}
                          <button
                            onClick={() => handleImageClick(selectedImageIndex === 0 ? images.length - 1 : selectedImageIndex - 1)}
                            className="absolute left-4 top-1/2 -translate-y-1/2 p-3 bg-white/90 backdrop-blur-sm rounded-full shadow-lg text-slate-700 hover:text-violet-600 hover:bg-white transition-all hover:scale-110 active:scale-95"
                            aria-label="上一张"
                          >
                            <ChevronLeft className="w-6 h-6" />
                          </button>

                          {/* 下一张按钮 */}
                          <button
                            onClick={() => handleImageClick(selectedImageIndex === images.length - 1 ? 0 : selectedImageIndex + 1)}
                            className="absolute right-4 top-1/2 -translate-y-1/2 p-3 bg-white/90 backdrop-blur-sm rounded-full shadow-lg text-slate-700 hover:text-violet-600 hover:bg-white transition-all hover:scale-110 active:scale-95"
                            aria-label="下一张"
                          >
                            <ChevronRight className="w-6 h-6" />
                          </button>
                        </>
                      )}

                      {/* 右上角操作按钮 */}
                      <div className="absolute top-4 right-4 flex gap-2 z-10">
                        <button
                          onClick={() => handleFavoriteToggle(selectedImage.id)}
                          className={cn(
                            'p-2 rounded-lg shadow-lg transition-all',
                            selectedImage.favorite
                              ? 'bg-red-50 text-red-500'
                              : 'bg-white text-slate-600 hover:bg-red-50 hover:text-red-500'
                          )}
                        >
                          <Heart className={cn('w-5 h-5', selectedImage.favorite && 'fill-current')} />
                        </button>
                        <DropdownMenu>
                          <DropdownMenuTrigger asChild>
                            <button className="p-2 bg-white rounded-lg shadow-lg text-slate-600 hover:bg-slate-50 transition-all">
                              <Download className="w-5 h-5" />
                            </button>
                          </DropdownMenuTrigger>
                          <DropdownMenuContent align="end" className="w-48">
                            <DropdownMenuItem onClick={() => handleDownload(selectedImage.url, selectedImage.title)}>
                              <Download className="w-4 h-4 mr-2" />
                              下载当前图片
                            </DropdownMenuItem>
                            <DropdownMenuSeparator />
                            <DropdownMenuItem onClick={handleDownloadMainImages}>
                              <ImageIcon className="w-4 h-4 mr-2 text-violet-600" />
                              下载所有主图
                              <span className="ml-auto text-xs text-slate-400">
                                {mainImages.length}
                              </span>
                            </DropdownMenuItem>
                            <DropdownMenuItem onClick={handleDownloadDetailImages}>
                              <Grid3X3 className="w-4 h-4 mr-2 text-purple-600" />
                              下载所有详情图
                              <span className="ml-auto text-xs text-slate-400">
                                {detailImages.length}
                              </span>
                            </DropdownMenuItem>
                            <DropdownMenuSeparator />
                            <DropdownMenuItem onClick={handleBatchDownload}>
                              <DownloadCloud className="w-4 h-4 mr-2 text-blue-600" />
                              下载全部
                            </DropdownMenuItem>
                          </DropdownMenuContent>
                        </DropdownMenu>
                      </div>
                    </div>
                  </>
                )}
              </div>

              {/* 缩略图列表 */}
              {images.length > 1 && (
                <div className="flex gap-3 overflow-x-auto pb-2">
                  {images.map((image, index) => (
                    <button
                      key={image.id}
                      onClick={() => handleImageClick(index)}
                      className={cn(
                        'relative flex-shrink-0 w-20 h-20 rounded-xl overflow-hidden transition-all border-2',
                        selectedImageIndex === index
                          ? 'border-violet-600 shadow-lg scale-105'
                          : 'border-transparent hover:border-slate-300'
                      )}
                    >
                      <img
                        src={image.thumbnailUrl || image.url}
                        alt={image.title}
                        className="w-full h-full object-cover"
                      />
                      {image.isMainImage && (
                        <div className="absolute bottom-0 left-0 right-0 bg-violet-600 text-white text-xs px-2 py-1">
                          主图
                        </div>
                      )}
                    </button>
                  ))}
                </div>
              )}
            </div>

            {/* 右侧商品信息区 */}
            <div className="space-y-6">
              {/* 商品基本信息 */}
              <div className="bg-white rounded-2xl p-6 shadow-sm">
                <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-4 mb-4">
                  <div className="flex-1 min-w-0">
                    <span className="inline-block px-3 py-1 bg-violet-100 text-violet-700 text-sm font-medium rounded-full mb-3">
                      {product.category}
                    </span>
                    <h1 className="text-2xl sm:text-3xl font-bold text-slate-900 mb-2 truncate">{product.name}</h1>
                    <p className="text-slate-500 text-sm">
                      共 {product.imageCount} 张图片
                    </p>
                  </div>
                  <button
                    onClick={handleBatchDownload}
                    className="group relative flex items-center justify-center gap-2.5 px-5 py-2.5 bg-gradient-to-r from-violet-500 to-purple-600 text-white rounded-xl hover:from-violet-600 hover:to-purple-700 transition-all duration-300 shadow-lg shadow-violet-500/30 hover:shadow-xl hover:shadow-violet-500/40 hover:-translate-y-0.5 active:translate-y-0 active:shadow-md sm:self-center whitespace-nowrap w-full sm:w-auto"
                  >
                    <div className="absolute inset-0 rounded-xl bg-gradient-to-r from-white/20 to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-300" />
                    <Download className="w-4.5 h-4.5 relative z-10 group-hover:animate-bounce" style={{ animationDuration: '1s' }} />
                    <span className="text-sm font-semibold relative z-10">下载全部</span>
                    <div className="absolute inset-0 rounded-xl ring-2 ring-white/20 ring-offset-2 ring-offset-transparent group-hover:ring-violet-300/50 transition-all duration-300" />
                  </button>
                </div>

                <div className="border-t border-slate-100 pt-4 mb-4">
                  <div className="flex items-center justify-between text-sm">
                    <span className="text-slate-500">图片统计</span>
                    <div className="flex gap-4">
                      <span className="text-slate-600">主图: <strong className="text-violet-600">{mainImages.length}</strong></span>
                      <span className="text-slate-600">详情图: <strong className="text-violet-600">{detailImages.length}</strong></span>
                    </div>
                  </div>
                </div>

                {product.description && (
                  <div className="mb-6">
                    <h3 className="text-sm font-medium text-slate-900 mb-2">商品描述</h3>
                    <p className="text-slate-600 leading-relaxed">{product.description}</p>
                  </div>
                )}

                {/* 标签 */}
                {selectedImage?.tags && selectedImage.tags.length > 0 && (
                  <div>
                    <h3 className="text-sm font-medium text-slate-900 mb-2">商品标签</h3>
                    <div className="flex flex-wrap gap-2">
                      {selectedImage.tags.map((tag, index) => (
                        <span
                          key={index}
                          className="px-3 py-1 bg-slate-100 text-slate-700 text-sm rounded-full hover:bg-slate-200 transition-colors"
                        >
                          {tag}
                        </span>
                      ))}
                    </div>
                  </div>
                )}
              </div>

              {/* 当前选中图片信息 */}
              {selectedImage && (
                <div className="bg-white rounded-2xl p-6 shadow-sm">
                  <h3 className="text-sm font-medium text-slate-900 mb-3">图片信息</h3>
                  <div className="space-y-2 text-sm">
                    <div className="flex justify-between">
                      <span className="text-slate-500">文件名</span>
                      <span className="text-slate-900 font-medium">{selectedImage.title}</span>
                    </div>
                    {selectedImage.resolution && (
                      <div className="flex justify-between">
                        <span className="text-slate-500">分辨率</span>
                        <span className="text-slate-900 font-medium">{selectedImage.resolution}</span>
                      </div>
                    )}
                    {selectedImage.size && (
                      <div className="flex justify-between">
                        <span className="text-slate-500">文件大小</span>
                        <span className="text-slate-900 font-medium">{selectedImage.size}</span>
                      </div>
                    )}
                    {selectedImage.fileType && (
                      <div className="flex justify-between">
                        <span className="text-slate-500">文件类型</span>
                        <span className="text-slate-900 font-medium">{selectedImage.fileType.toUpperCase()}</span>
                      </div>
                    )}
                  </div>
                </div>
              )}
            </div>
          </div>
        ) : (
          /* 详情图网格视图 */
          <div className="space-y-6">
            <div className="bg-white rounded-2xl p-6 shadow-sm">
              <h2 className="text-2xl font-bold text-slate-900 mb-2">{product.name}</h2>
              <p className="text-slate-500">{product.description}</p>
            </div>

            {mainImages.length > 0 && (
              <div>
                <h3 className="text-lg font-semibold text-slate-900 mb-4 flex items-center gap-2">
                  <span className="w-2 h-2 rounded-full bg-violet-600"></span>
                  主图
                </h3>
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                  {mainImages.map((image) => (
                    <div
                      key={image.id}
                      className="relative bg-white rounded-xl shadow-sm overflow-hidden group cursor-pointer"
                      onClick={() => {
                        setViewMode('single');
                        setSelectedImageIndex(images.findIndex(img => img.id === image.id));
                      }}
                    >
                      <div className="aspect-square relative">
                        <img
                          src={image.url}
                          alt={image.title}
                          className="w-full h-full object-cover"
                        />
                        <div className="absolute inset-0 bg-black/0 group-hover:bg-black/10 transition-colors" />
                        <div className="absolute top-2 right-2 opacity-0 group-hover:opacity-100 transition-opacity">
                          <button
                            onClick={(e) => {
                              e.stopPropagation();
                              handleFavoriteToggle(image.id);
                            }}
                            className="p-2 bg-white rounded-lg shadow-lg text-slate-600 hover:text-red-500 transition-colors"
                          >
                            <Heart className={cn('w-4 h-4', image.favorite && 'fill-current text-red-500')} />
                          </button>
                        </div>
                      </div>
                      <div className="p-3">
                        <p className="text-sm font-medium text-slate-900 truncate">{image.title}</p>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {detailImages.length > 0 && (
              <div>
                <h3 className="text-lg font-semibold text-slate-900 mb-4 flex items-center gap-2">
                  <span className="w-2 h-2 rounded-full bg-slate-400"></span>
                  详情图 ({detailImages.length})
                </h3>
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
                  {detailImages.map((image) => (
                    <div
                      key={image.id}
                      className="relative bg-white rounded-xl shadow-sm overflow-hidden group cursor-pointer"
                      onClick={() => {
                        setViewMode('single');
                        setSelectedImageIndex(images.findIndex(img => img.id === image.id));
                      }}
                    >
                      <div className="aspect-square relative">
                        <img
                          src={image.url}
                          alt={image.title}
                          className="w-full h-full object-cover"
                        />
                        <div className="absolute inset-0 bg-black/0 group-hover:bg-black/10 transition-colors" />
                        <div className="absolute top-2 right-2 opacity-0 group-hover:opacity-100 transition-opacity">
                          <button
                            onClick={(e) => {
                              e.stopPropagation();
                              handleFavoriteToggle(image.id);
                            }}
                            className="p-2 bg-white rounded-lg shadow-lg text-slate-600 hover:text-red-500 transition-colors"
                          >
                            <Heart className={cn('w-4 h-4', image.favorite && 'fill-current text-red-500')} />
                          </button>
                        </div>
                      </div>
                      <div className="p-3">
                        <p className="text-sm font-medium text-slate-900 truncate">{image.title}</p>
                        <p className="text-xs text-slate-500 mt-1">顺序: {image.displayOrder}</p>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {images.length === 0 && (
              <div className="bg-white rounded-2xl p-12 shadow-sm text-center">
                <p className="text-slate-500">暂无图片</p>
              </div>
            )}
          </div>
        )}
      </main>
    </div>
  );
}
