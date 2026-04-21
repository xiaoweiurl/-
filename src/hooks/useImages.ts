/**
 * 图片数据管理 Hook
 * 
 * 提供图片数据的增删改查、收藏、批量操作等功能
 */

import { useState, useCallback, useEffect } from 'react';
import type { ImageItem } from '@/components/ImageCard';

interface UseImagesOptions {
  initialImages?: ImageItem[];
  useApi?: boolean; // 是否使用后端 API
}

interface UseImagesReturn {
  images: ImageItem[];
  loading: boolean;
  error: string | null;
  
  // 查询
  queryImages: (params: {
    keyword?: string;
    albumId?: string;
    favorite?: boolean;
    sortBy?: 'date' | 'name' | 'size';
    sortOrder?: 'asc' | 'desc';
  }) => void;
  
  // 收藏相关
  toggleFavorite: (id: string) => Promise<void>;
  batchFavorite: (ids: string[]) => Promise<void>;
  
  // 删除相关
  deleteImage: (id: string) => Promise<void>;
  batchDelete: (ids: string[]) => Promise<void>;
  restoreImage: (id: string) => Promise<void>;
  
  // 移动相册
  moveToAlbum: (ids: string[], albumId: string) => Promise<void>;
  
  // 更新
  updateImage: (id: string, data: Partial<ImageItem>) => Promise<void>;
  
  // 刷新
  refresh: () => void;
}

/**
 * 图片数据管理 Hook
 */
export function useImages(options: UseImagesOptions = {}): UseImagesReturn {
  const { initialImages = [], useApi = false } = options;
  
  const [images, setImages] = useState<ImageItem[]>(initialImages);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  
  // 初始化加载图片
  useEffect(() => {
    if (initialImages.length > 0) {
      setImages(initialImages);
    }
  }, [initialImages]);
  
  /**
   * 查询图片
   */
  const queryImages = useCallback((params: {
    keyword?: string;
    albumId?: string;
    favorite?: boolean;
    sortBy?: 'date' | 'name' | 'size';
    sortOrder?: 'asc' | 'desc';
  }) => {
    setLoading(true);
    setError(null);
    
    try {
      let filtered = [...images];
      
      // 关键词搜索
      if (params.keyword) {
        filtered = filtered.filter(img =>
          img.title.toLowerCase().includes(params.keyword!.toLowerCase())
        );
      }
      
      // 相册筛选
      if (params.albumId) {
        filtered = filtered.filter(img => img.albumId === params.albumId);
      }
      
      // 收藏筛选
      if (params.favorite !== undefined) {
        filtered = filtered.filter(img => img.favorite === params.favorite);
      }
      
      // 排序
      const sortBy = params.sortBy || 'date';
      const sortOrder = params.sortOrder || 'desc';
      
      filtered.sort((a, b) => {
        let comparison = 0;
        
        if (sortBy === 'date') {
          comparison = new Date(a.date).getTime() - new Date(b.date).getTime();
        } else if (sortBy === 'name') {
          comparison = a.title.localeCompare(b.title);
        } else if (sortBy === 'size') {
          const sizeA = parseFloat(a.size);
          const sizeB = parseFloat(b.size);
          comparison = sizeA - sizeB;
        }
        
        return sortOrder === 'asc' ? comparison : -comparison;
      });
      
      setImages(filtered);
    } catch (err) {
      setError(err instanceof Error ? err.message : '查询失败');
    } finally {
      setLoading(false);
    }
  }, [images]);
  
  /**
   * 切换收藏状态
   */
  const toggleFavorite = useCallback(async (id: string) => {
    try {
      setError(null);
      
      // 更新本地状态
      setImages(prev => prev.map(img => {
        if (img.id === id) {
          return { ...img, favorite: !img.favorite };
        }
        return img;
      }));
      
      // 如果使用 API，调用后端
      if (useApi) {
        const { imageApi } = await import('@/lib/api');
        await imageApi.toggleFavorite(id);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : '操作失败');
      // 回滚状态
      setImages(prev => prev.map(img => {
        if (img.id === id) {
          return { ...img, favorite: !img.favorite };
        }
        return img;
      }));
    }
  }, [useApi]);
  
  /**
   * 批量收藏
   */
  const batchFavorite = useCallback(async (ids: string[]) => {
    try {
      setError(null);
      
      // 更新本地状态
      setImages(prev => prev.map(img => {
        if (ids.includes(img.id)) {
          return { ...img, favorite: true };
        }
        return img;
      }));
      
      // 如果使用 API，调用后端
      if (useApi) {
        const { imageApi } = await import('@/lib/api');
        await imageApi.batchOperation({
          imageIds: ids,
          operation: 'favorite'
        });
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : '操作失败');
    }
  }, [useApi]);
  
  /**
   * 删除图片
   */
  const deleteImage = useCallback(async (id: string) => {
    try {
      setError(null);
      
      // 更新本地状态
      setImages(prev => prev.filter(img => img.id !== id));
      
      // 如果使用 API，调用后端
      if (useApi) {
        const { imageApi } = await import('@/lib/api');
        await imageApi.deleteImage(id);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : '删除失败');
    }
  }, [useApi]);
  
  /**
   * 批量删除
   */
  const batchDelete = useCallback(async (ids: string[]) => {
    try {
      setError(null);
      
      // 更新本地状态
      setImages(prev => prev.filter(img => !ids.includes(img.id)));
      
      // 如果使用 API，调用后端
      if (useApi) {
        const { imageApi } = await import('@/lib/api');
        await imageApi.batchOperation({
          imageIds: ids,
          operation: 'delete'
        });
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : '删除失败');
    }
  }, [useApi]);
  
  /**
   * 恢复图片
   */
  const restoreImage = useCallback(async (id: string) => {
    try {
      setError(null);
      
      // 如果使用 API，调用后端
      if (useApi) {
        const { imageApi } = await import('@/lib/api');
        await imageApi.restoreImage(id);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : '恢复失败');
    }
  }, [useApi]);
  
  /**
   * 移动到相册
   */
  const moveToAlbum = useCallback(async (ids: string[], albumId: string) => {
    try {
      setError(null);
      
      // 更新本地状态
      setImages(prev => prev.map(img => {
        if (ids.includes(img.id)) {
          return { ...img, albumId };
        }
        return img;
      }));
      
      // 如果使用 API，调用后端
      if (useApi) {
        const { imageApi } = await import('@/lib/api');
        await imageApi.batchOperation({
          imageIds: ids,
          targetAlbumId: albumId,
          operation: 'move'
        });
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : '移动失败');
    }
  }, [useApi]);
  
  /**
   * 更新图片信息
   */
  const updateImage = useCallback(async (id: string, data: Partial<ImageItem>) => {
    try {
      setError(null);
      
      // 更新本地状态
      setImages(prev => prev.map(img => {
        if (img.id === id) {
          return { ...img, ...data };
        }
        return img;
      }));
      
      // 如果使用 API，调用后端
      if (useApi) {
        const { imageApi } = await import('@/lib/api');
        await imageApi.updateImage(id, data);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : '更新失败');
    }
  }, [useApi]);
  
  /**
   * 刷新数据
   */
  const refresh = useCallback(() => {
    // 如果使用 API，重新加载数据
    if (useApi) {
      setLoading(true);
      import('@/lib/api').then(({ imageApi }) => {
        return imageApi.getImages({});
      }).then(data => {
        // 转换 API 数据格式为本地 ImageItem 格式
        const convertedList: ImageItem[] = (data.list || []).map((img: {
          id: string;
          url: string;
          title: string;
          size?: number;
          sizeFormatted?: string;
          resolution?: string;
          width?: number;
          height?: number;
          createdAt?: string;
          favorite?: boolean;
          tags?: string[];
          albumId?: string;
          albumName?: string;
        }) => ({
          id: img.id,
          url: img.url,
          title: img.title,
          size: img.sizeFormatted || `${img.size}`,
          resolution: img.resolution || `${img.width}×${img.height}`,
          date: img.createdAt || new Date().toISOString(),
          favorite: img.favorite || false,
          tags: img.tags || [],
          albumId: img.albumId,
          albumName: img.albumName,
        }));
        setImages(convertedList);
      }).catch(err => {
        setError(err instanceof Error ? err.message : '刷新失败');
      }).finally(() => {
        setLoading(false);
      });
    }
  }, [useApi]);
  
  return {
    images,
    loading,
    error,
    queryImages,
    toggleFavorite,
    batchFavorite,
    deleteImage,
    batchDelete,
    restoreImage,
    moveToAlbum,
    updateImage,
    refresh,
  };
}
