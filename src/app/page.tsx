'use client';

import React from 'react';
import { useRouter } from 'next/navigation';
import { cn } from '@/lib/utils';

import Sidebar from '@/components/Sidebar';
import Header, { type CurrentUser } from '@/components/Header';
import ImageGrid from '@/components/ImageGrid';
import ImagePreview from '@/components/ImagePreview';
import FilterPanel from '@/components/FilterPanel';
import BulkActions from '@/components/BulkActions';
import MoveToAlbumDialog, { type Album } from '@/components/MoveToAlbumDialog';
import SmartAlbumEditor from '@/components/SmartAlbumEditor';
import UploadDialog from '@/components/UploadDialog';
import DocumentUploadDialog from '@/components/DocumentUploadDialog';
import DocumentManager from '@/components/DocumentManager';
import ExcelBatchUpload from '@/components/ExcelBatchUpload';
import ExportDialog from '@/components/ExportDialog';
import UserMenu from '@/components/UserMenu';
import { BatchReplaceMainImageDialog } from '@/components/BatchReplaceMainImageDialog';
import type { SmartAlbumInfo } from '@/components/Sidebar';
import { PRESET_SMART_ALBUMS, type MatchingConfig } from '@/lib/api/types';
import { filterImagesBySmartAlbum, getSmartAlbumImageCount } from '@/lib/smart-album-engine';
import { getCurrentBrand, BRANDS } from '@/lib/brand';
import { Toaster } from '@/components/ui/sonner';
import { toast } from 'sonner';
import { Loader2, Search as SearchIcon, Filter } from 'lucide-react';
import { Button } from '@/components/ui/button';
import type { ImageItem } from '@/components/ImageCard';
import { useSettings } from '@/contexts/SettingsContext';
import { useNotifications } from '@/contexts/NotificationContext';

// 后端 API 基础 URL - 统一走 Next.js 代理
const BACKEND_API_URL = '/api/proxy';

// 获取完整的图片 URL
// 后端代理层已根据请求来源自动替换 localhost:8080，前端直接透传即可
const getFullImageUrl = (url: string) => url;

// 获取 sessionId（从 localStorage）
function getSessionId(): string | null {
  if (typeof window === 'undefined') return null;
  return localStorage.getItem('session_id');
}

// 判断 API 响应是否成功（兼容 { success: true } 和 { code: 200 } 格式）
function isApiSuccess(result: Record<string, unknown>): boolean {
  return result.success === true || result.code === 200 || result.code === 201;
}

// 直接调用后端 API
async function backendFetch(endpoint: string, options: RequestInit = {}): Promise<Response> {
  const sessionId = getSessionId();
  const url = `${BACKEND_API_URL}${endpoint}`;
  
  const headers: Record<string, string> = {
    ...(options.headers as Record<string, string> || {}),
  };
  
  // 添加 sessionId 到请求头
  if (sessionId) {
    headers['X-Session-Id'] = sessionId;
    console.log('[Backend] X-Session-Id:', sessionId.substring(0, 8) + '...');
  }
  
  console.log(`[Backend] ${options.method || 'GET'} ${url}`);
  
  return fetch(url, {
    ...options,
    headers,
  });
}

// 用户信息类型
interface UserInfo {
  id: string;
  username: string;
  email: string;
  role: 'admin' | 'user';
  avatar?: string;
}

// 模拟相册数据 - 根据服装类型细分
const mockAlbums: Album[] = [
  {
    id: 'album-tshirt',
    name: 'T恤',
    description: '短袖/长袖T恤、速干衣',
    imageCount: 1,
    coverUrl: '/assets/【经典款】HELLYHANSEN_HH 男款吸湿速干轻户外都市休闲长袖T恤_372.png',
  },
  {
    id: 'album-underwear',
    name: '内衣',
    description: '贴身内衣、打底衣',
    imageCount: 1,
    coverUrl: '/assets/【单依纯同款】icebreaker美利奴羊毛女200 Oasis吸湿长袖T恤徒步_4.png',
  },
  {
    id: 'album-fleece',
    name: '抓绒衣',
    description: '抓绒衣、保暖中层',
    imageCount: 1,
    coverUrl: '/assets/「折扣」patagonia巴塔R1AIR抓绒衣男女户外透气排汗保暖速干圆领_619.png',
  },
  {
    id: 'album-jacket',
    name: '冲锋衣',
    description: '防风防雨冲锋衣、硬壳外套',
    imageCount: 1,
    coverUrl: '/assets/【王一博同款】HELLY HANSEN_HH 专业Ⅰ级登山3L防风防雨冲锋衣_371.png',
  },
  {
    id: 'album-softshell',
    name: '软壳',
    description: '软壳外套、防泼水外套',
    imageCount: 1,
    coverUrl: '/assets/【经典CREW】 HELLY HANSEN_HH男款户外软壳防泼水保暖登山服抓绒_98.png',
  },
];

// 相册名称与标签的映射关系
const albumTagMapping: Record<string, string[]> = {
  'T恤': ['T恤', '短袖', '长袖', '速干'],
  '内衣': ['内衣', '打底', '美利奴羊毛', '贴身'],
  '抓绒衣': ['抓绒', '抓绒衣', '保暖', '中层'],
  '冲锋衣': ['冲锋衣', '防风', '防雨', '硬壳', '登山'],
  '软壳': ['软壳', '防泼水', 'softshell'],
};

// 模拟图片数据 - 户外服装产品图（根据名称自动分类）
const mockImages: ImageItem[] = [
  // 抓绒衣
  {
    id: '1',
    url: '/assets/「折扣」patagonia巴塔R1AIR抓绒衣男女户外透气排汗保暖速干圆领_619.png',
    title: 'Patagonia R1 AIR 抓绒衣',
    size: '2.4 MB',
    resolution: '800×800',
    date: '2024-01-15',
    favorite: true,
    tags: ['抓绒衣', '保暖', '户外', '透气', '速干'],
    albumId: 'album-fleece',
    albumName: '抓绒衣',
    fileType: 'png',
    isMainImage: true,
    productId: 'product-1',
  },
  // 内衣/打底
  {
    id: '2',
    url: '/assets/【单依纯同款】icebreaker美利奴羊毛女200 Oasis吸湿长袖T恤徒步_4.png',
    title: 'Icebreaker 美利奴羊毛内衣',
    size: '3.1 MB',
    resolution: '800×800',
    date: '2024-01-14',
    favorite: false,
    tags: ['内衣', '美利奴羊毛', '保暖'],
    albumId: 'album-underwear',
    albumName: '内衣',
    fileType: 'png',
    isMainImage: true,
    productId: 'product-2',
  },
  // 软壳外套
  {
    id: '3',
    url: '/assets/【经典CREW】 HELLY HANSEN_HH男款户外软壳防泼水保暖登山服抓绒_98.png',
    title: 'HELLY HANSEN 软壳外套',
    size: '2.8 MB',
    resolution: '800×800',
    date: '2024-01-13',
    favorite: true,
    tags: ['软壳', '防泼水', '保暖', '户外'],
    albumId: 'album-softshell',
    albumName: '软壳',
    fileType: 'png',
    isMainImage: true,
    productId: 'product-3',
  },
  // T恤
  {
    id: '4',
    url: '/assets/【经典款】HELLYHANSEN_HH 男款吸湿速干轻户外都市休闲长袖T恤_372.png',
    title: 'HELLY HANSEN 长袖T恤',
    size: '1.9 MB',
    resolution: '800×800',
    date: '2024-01-12',
    favorite: false,
    tags: ['T恤', '速干', '休闲', '户外'],
    albumId: 'album-tshirt',
    albumName: 'T恤',
    fileType: 'png',
    isMainImage: true,
    productId: 'product-4',
  },
  // 冲锋衣
  {
    id: '5',
    url: '/assets/【王一博同款】HELLY HANSEN_HH 专业Ⅰ级登山3L防风防雨冲锋衣_371.png',
    title: 'HELLY HANSEN 专业冲锋衣',
    size: '4.2 MB',
    resolution: '800×800',
    date: '2024-01-11',
    favorite: true,
    tags: ['冲锋衣', '防风', '防雨', '专业', '登山'],
    albumId: 'album-jacket',
    albumName: '冲锋衣',
    fileType: 'png',
    isMainImage: true,
    productId: 'product-5',
  },
];

export default function Home() {
  const router = useRouter();
  const { settings } = useSettings();
  const { addNotification } = useNotifications();
  const [brand, setBrand] = React.useState(BRANDS.yingyun);
  const [isLoading, setIsLoading] = React.useState(true);
  const [currentUser, setCurrentUser] = React.useState<CurrentUser | null>(null);
  const authCheckedRef = React.useRef(false);
  const [sidebarCollapsed, setSidebarCollapsed] = React.useState(false);
  const [activeMenuItem, setActiveMenuItem] = React.useState('all');
  const [searchQuery, setSearchQuery] = React.useState('');

  const [viewMode, setViewMode] = React.useState<'grid' | 'masonry' | 'list'>('masonry');
  const [selectedImages, setSelectedImages] = React.useState<string[]>([]);
  const [previewImage, setPreviewImage] = React.useState<ImageItem | null>(null);
  const [images, setImages] = React.useState<ImageItem[]>([]);
  const [allImages, setAllImages] = React.useState<ImageItem[]>([]); // 用于统计的完整数据
  const [albums, setAlbums] = React.useState<Album[]>([]);
  const [smartAlbums, setSmartAlbums] = React.useState<SmartAlbumInfo[]>([]);
  const [isSmartAlbumEditorOpen, setIsSmartAlbumEditorOpen] = React.useState(false);
  const [editingSmartAlbum, setEditingSmartAlbum] = React.useState<SmartAlbumInfo | null>(null);
  const [isLoadingSmartAlbums, setIsLoadingSmartAlbums] = React.useState(false);
  const [trashCount, setTrashCount] = React.useState(0); // 回收站主图数量（从后端获取）
  const [dynamicTableCount, setDynamicTableCount] = React.useState(0); // 动态表图片数量（我的知识库/二创中心）
  const [selectedAlbumIds, setSelectedAlbumIds] = React.useState<string[]>([]); // 当前选中的相册及其子相册 ID
  const [showBatchReplaceDialog, setShowBatchReplaceDialog] = React.useState(false); // 批量替换主图弹窗

  // 获取智能相册列表
  const fetchSmartAlbums = React.useCallback(async () => {
    setIsLoadingSmartAlbums(true);
    try {
      const response = await fetch('/api/smart-albums', {
        credentials: 'include',
      });
      const result = await response.json();

      if (result.success) {
        // 合并后端返回的相册（包含预置的）
        const albums: SmartAlbumInfo[] = result.data.map((album: any) => ({
          ...album,
          type: 'smart' as const,
          count: 0, // 稍后更新
        }));
        setSmartAlbums(albums);
      } else {
        console.error('获取智能相册失败:', result.message);
      }
    } catch (error) {
      console.error('获取智能相册失败:', error);
    } finally {
      setIsLoadingSmartAlbums(false);
    }
  }, []);

  // 初始加载智能相册
  React.useEffect(() => {
    setBrand(getCurrentBrand());
  }, []);

  React.useEffect(() => {
    fetchSmartAlbums();
  }, [fetchSmartAlbums]);

  // 文档统计状态
  const [documentStats, setDocumentStats] = React.useState<Record<string, number>>({
    all: 0, pdf: 0, word: 0, excel: 0, ppt: 0, zip: 0, other: 0,
  });

  // 获取文档统计
  const fetchDocumentStats = React.useCallback(async () => {
    try {
      const sessionId = getSessionId();
      const response = await fetch(`${BACKEND_API_URL}/documents/stats`, {
        headers: {
          'X-Session-Id': sessionId || '',
        },
        credentials: 'include',
      });
      
      if (response.ok) {
        const result = await response.json();
        if (result.success || result.code === 200) {
          setDocumentStats({
            all: result.data?.all || 0,
            pdf: result.data?.pdf || 0,
            word: result.data?.word || 0,
            excel: result.data?.excel || 0,
            ppt: result.data?.ppt || 0,
            zip: result.data?.zip || 0,
            other: result.data?.other || 0,
          });
        }
      }
    } catch (error) {
      console.error('获取文档统计失败:', error);
    }
  }, []);

  // 页面加载时获取文档统计
  React.useEffect(() => {
    fetchDocumentStats();
  }, [fetchDocumentStats]);

  // 更新智能相册的统计数量
  React.useEffect(() => {
    if (allImages.length === 0 || smartAlbums.length === 0) return;

    setSmartAlbums(prev => prev.map(album => {
      if (!album.matchingConfig && !album.isSystem) return album;
      // 系统相册或具有 matchingConfig 的相册
      const count = getSmartAlbumImageCount(allImages, album as import('@/lib/api/types').SmartAlbum);
      return { ...album, count };
    }));
  }, [allImages, smartAlbums.length]);

  // 创建/编辑智能相册
  const handleSaveSmartAlbum = React.useCallback(async (data: {
    name: string;
    description: string;
    matchingConfig: MatchingConfig;
  }) => {
    try {
      if (editingSmartAlbum) {
        // 编辑模式
        const response = await fetch(`/api/smart-albums/${editingSmartAlbum.id}`, {
          method: 'PUT',
          credentials: 'include',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            name: data.name,
            description: data.description,
            matchingConfig: data.matchingConfig,
          }),
        });

        const result = await response.json();

        if (result.success) {
          await fetchSmartAlbums(); // 重新获取列表
          toast.success('智能相册已更新');
        } else {
          toast.error(result.message || '更新失败');
        }
      } else {
        // 创建模式
        const response = await fetch('/api/smart-albums', {
          method: 'POST',
          credentials: 'include',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            name: data.name,
            description: data.description,
            matchingConfig: data.matchingConfig,
          }),
        });

        const result = await response.json();

        if (result.success) {
          await fetchSmartAlbums(); // 重新获取列表
          toast.success('智能相册创建成功');
        } else {
          toast.error(result.message || '创建失败');
        }
      }
      setEditingSmartAlbum(null);
    } catch (error) {
      console.error('保存智能相册失败:', error);
      toast.error('保存失败，请稍后重试');
    }
  }, [editingSmartAlbum, fetchSmartAlbums]);

  // 删除智能相册
  const handleDeleteSmartAlbum = React.useCallback(async (albumId: string) => {
    try {
      const response = await fetch(`/api/smart-albums/${albumId}`, {
        method: 'DELETE',
        credentials: 'include',
      });

      const result = await response.json();

      if (result.success) {
        await fetchSmartAlbums(); // 重新获取列表
        toast.success('智能相册已删除');
      } else {
        toast.error(result.message || '删除失败');
      }
    } catch (error) {
      console.error('删除智能相册失败:', error);
      toast.error('删除失败，请稍后重试');
    }
  }, [fetchSmartAlbums]);

  // 使用智能相册规则筛选图片（通过 API）
  const executeSmartAlbumRules = React.useCallback(async (
    albumId: string,
    page = 1,
    pageSize = 50
  ) => {
    try {
      const response = await fetch('/api/smart-albums/execute', {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          albumId,
          page,
          pageSize,
        }),
      });

      const result = await response.json();

      if (result.success) {
        return {
          images: result.data as ImageItem[],
          total: result.total as number,
          page: result.page as number,
          pageSize: result.pageSize as number,
          totalPages: result.totalPages as number,
        };
      } else {
        console.error('执行智能相册规则失败:', result.message);
        return null;
      }
    } catch (error) {
      console.error('执行智能相册规则失败:', error);
      return null;
    }
  }, []);

  // 获取相册列表
  const fetchAlbums = React.useCallback(async () => {
    try {
      // 直接调用后端 API
      const response = await backendFetch('/albums');
      const result = await response.json();

      if (result.success || result.code === 200) {
        const albumList = Array.isArray(result.data) ? result.data : [];
        console.log('[Home] 获取相册列表成功:', albumList.length, '个相册');
        setAlbums(albumList);
      } else {
        console.warn('[Home] 获取相册列表失败，使用静态数据');
        setAlbums(mockAlbums);
      }
    } catch (error) {
      console.error('获取相册列表失败:', error);
      setAlbums(mockAlbums);
    }
  }, []);
  const [sortBy, setSortBy] = React.useState<'date' | 'name' | 'size'>('date');
  const [sortOrder, setSortOrder] = React.useState<'asc' | 'desc'>('desc');
  
  // 分页状态 - 从设置中获取默认值
  const [currentPage, setCurrentPage] = React.useState(1);
  const [pageSize, setPageSize] = React.useState(settings?.pageSize || 40);
  const [hasMore, setHasMore] = React.useState(false);
  const [loadingMore, setLoadingMore] = React.useState(false);
  const [totalCount, setTotalCount] = React.useState(0);
  const [totalPages, setTotalPages] = React.useState(0);
  
  // 当设置变化时更新状态
  React.useEffect(() => {
    if (settings) {
      setPageSize(settings.pageSize);
      setViewMode(settings.defaultView);
    }
  }, [settings?.pageSize, settings?.defaultView]);
  
  // 统一筛选状态
  const [filterState, setFilterState] = React.useState<import('@/components/FilterPanel').FilterState & { keyword?: string }>({
    dateFilter: 'all',
    typeFilter: 'all',
    albumFilter: 'all',
    tagFilter: [],
    keyword: '',
  });
  
  // 处理搜索框变化
  const handleSearchChange = (query: string) => {
    setSearchQuery(query);
    setFilterState(prev => ({ ...prev, keyword: query }));
  };
  
  // 处理搜索提交（按回车键）
  const handleSearchSubmit = () => {
    console.log('[Home] 执行搜索:', searchQuery);
    setFilterState(prev => ({ ...prev, keyword: searchQuery }));
    setCurrentPage(1);
    fetchImages(1, false, searchQuery);

  };
  
  // 移动相册对话框状态
  const [moveDialogOpen, setMoveDialogOpen] = React.useState(false);
  const [selectedAlbumId, setSelectedAlbumId] = React.useState<string | null>(null);
  
  // 上传对话框状态
  const [uploadDialogOpen, setUploadDialogOpen] = React.useState(false);
  
  // Excel批量上传对话框状态
  const [excelUploadDialogOpen, setExcelUploadDialogOpen] = React.useState(false);
  
  // 文档上传对话框状态
  const [documentUploadDialogOpen, setDocumentUploadDialogOpen] = React.useState(false);
  
  // 批量导出对话框状态
  const [exportDialogOpen, setExportDialogOpen] = React.useState(false);
  
  // 筛选面板状态
  const [filterPanelOpen, setFilterPanelOpen] = React.useState(false);
  const [tags, setTags] = React.useState<{ name: string; count: number }[]>([]);

  // 从API获取图片数据（用于统计，获取所有图片包含已删除的）
  const fetchAllImages = React.useCallback(async () => {
    try {
      const params = new URLSearchParams({
        page: '1',
        pageSize: '1000', // 获取足够多的数据用于统计
        includeDeleted: 'true',
      });

      // 直接调用后端 API
      const response = await backendFetch(`/images?${params}`);
      const result = await response.json();

      if (result.success || result.code === 200) {
        const imageList = result.data?.list || result.data || [];
        console.log('[fetchAllImages] 获取图片数据:', imageList.length, '张');
        setAllImages(imageList);
        
        // 同时从后端 API 获取回收站主图数量（更高效）
        fetchTrashCount();
      } else {
        console.warn('[fetchAllImages] API 返回失败，不使用模拟数据');
        setAllImages([]);
      }
    } catch (error) {
      console.error('获取完整图片数据失败:', error);
      console.warn('[fetchAllImages] 不使用模拟数据，清空统计数据');
      setAllImages([]);
    }
  }, []);

  // 从后端 API 获取回收站主图数量（更高效）
  const fetchTrashCount = React.useCallback(async () => {
    try {
      const response = await backendFetch('/images/trash/count');
      const result = await response.json();
      
      if (result.success || result.code === 200) {
        const count = result.data || 0;
        console.log('[fetchTrashCount] 回收站主图数量:', count);
        setTrashCount(count);
      }
    } catch (error) {
      console.error('获取回收站数量失败:', error);
      // 降级：从前端 allImages 计算
      const count = allImages.filter((img: any) => img.deleted && img.isMainImage).length;
      setTrashCount(count);
    }
  }, [allImages]);

  // 从API获取图片数据（支持分页，用于当前视图显示）
  const fetchImages = React.useCallback(async (page: number = 1, append: boolean = false, keywordOverride?: string) => {
    console.log('[Home] fetchImages 调用:', { page, append, activeMenuItem, filterState });
    if (append) {
      setLoadingMore(true);
    }

    // 使用传入的 keywordOverride 或当前搜索框内容
    const currentKeyword = keywordOverride !== undefined ? keywordOverride : searchQuery;

    try {
      let apiUrl = '';
      const params = new URLSearchParams({
        page: String(page),
        pageSize: String(pageSize),
      });

      // 根据当前菜单项选择不同的API端点（直接调用后端 API）
      // 关键词搜索 - 仅全部知识页面支持
      if (activeMenuItem === 'all' && currentKeyword && currentKeyword.trim()) {
        params.append('keyword', currentKeyword.trim());
      }

      if (activeMenuItem === 'trash') {
        // 回收站
        apiUrl = `/images/trash?${params}`;
      } else if (activeMenuItem === 'recent') {
        // 最近上传
        apiUrl = `/images/recent?${params}`;
      } else if (activeMenuItem === 'favorites') {
        // 收藏 - 使用主图片API加筛选
        params.append('favorite', 'true');
        apiUrl = `/images?${params}`;
      } else if (activeMenuItem === 'my-images') {
        // 我的二创 - 只显示当前用户的二创图片（动态表）
        params.append('onlyMine', 'true');
        params.append('includeDeleted', 'false');
        params.append('source', 'creative');
        apiUrl = `/images?${params}`;
      } else if (activeMenuItem === 'creative-center') {
        // 二创中心 - 查看同公司所有用户的二创图片
        params.append('otherUsers', 'true');
        params.append('includeDeleted', 'false');
        params.append('source', 'creative');
        apiUrl = `/images?${params}`;
      } else {
        // 全部知识 - 查询主表（共享数据，所有分类）
        params.append('includeDeleted', 'false');
        
        // 添加日期筛选 - 转换为 startDate 和 endDate
        if (filterState.dateFilter && filterState.dateFilter !== 'all') {
          const now = new Date();
          let startDate: Date | null = null;
          
          switch (filterState.dateFilter) {
            case 'today':
              startDate = new Date(now.getFullYear(), now.getMonth(), now.getDate());
              break;
            case 'week':
              startDate = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);
              break;
            case 'month':
              startDate = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000);
              break;
          }
          
          if (startDate) {
            params.append('startDate', startDate.toISOString().split('T')[0]);
            params.append('endDate', now.toISOString().split('T')[0]);
            console.log('[Home] 添加日期筛选:', startDate.toISOString().split('T')[0], '至', now.toISOString().split('T')[0]);
          }
        }
        
        // 添加文件类型筛选
        if (filterState.typeFilter && filterState.typeFilter !== 'all') {
          params.append('fileType', filterState.typeFilter);
          console.log('[Home] 添加文件类型筛选:', filterState.typeFilter);
        }
        
        // 添加相册筛选 - 支持从侧边栏点击或从筛选面板选择
        let albumId: string | null = null;
        
        // 优先从筛选面板获取
        if (filterState.albumFilter && filterState.albumFilter !== 'all') {
          // 筛选面板的 ID 可能带或不带 album- 前缀，统一处理
          let filterAlbumId = filterState.albumFilter;
          if (filterAlbumId.startsWith('album-')) {
            // 已经是带前缀格式，直接使用
            albumId = filterAlbumId;
          } else {
            // 尝试匹配完整 ID（可能带前缀）
            const matchingAlbum = albums.find(a => a.id === filterAlbumId || a.id === `album-${filterAlbumId}` || a.id.startsWith(filterAlbumId));
            albumId = matchingAlbum ? matchingAlbum.id : filterAlbumId;
          }
        }
        // 其次从侧边栏获取
        else if (activeMenuItem) {
          // 数据库中存储的是 album-{id} 格式，直接使用不截断
          if (activeMenuItem.startsWith('album-') || albums.some(a => a.id === activeMenuItem)) {
            albumId = activeMenuItem;
          } else {
            // 尝试匹配截断ID
            const matchingAlbum = albums.find(a => a.id.startsWith(activeMenuItem) || a.id === `album-${activeMenuItem}` || a.id === activeMenuItem);
            if (matchingAlbum) {
              albumId = matchingAlbum.id;
            }
          }
        }
        
        if (albumId) {
          params.append('albumId', albumId);
          params.append('onlyMainImage', 'true');
          console.log('[Home] 添加相册筛选:', albumId);
        }
        
        // 添加标签筛选（多标签支持）
        if (filterState.tagFilter && filterState.tagFilter.length > 0) {
          filterState.tagFilter.forEach(tag => params.append('tags', tag));
          console.log('[Home] 添加标签筛选:', filterState.tagFilter);
        }
        
        // 如果有相册筛选，使用 images API；否则也使用 images API（统一筛选逻辑）
        apiUrl = `/images?${params}`;
      }

      console.log('[Home] 请求URL:', apiUrl);
      
      // 直接调用后端 API（绕过 Next.js API Route）
      const response = await backendFetch(apiUrl);
      const setCookieHeader = response.headers.get('set-cookie');
      console.log('[Home] 响应 Set-Cookie:', setCookieHeader);
      
      // 安全解析 JSON，避免空响应导致错误
      let result;
      try {
        const text = await response.text();
        result = text ? JSON.parse(text) : { success: false, message: '响应为空' };
      } catch {
        result = { success: false, message: 'JSON 解析失败' };
      }

      console.log('[Home] API 响应:', result);

      // 兼容两种响应格式: { success: true } 或 { code: 200 }
      const isSuccess = result.success === true || result.code === 200;
      if (isSuccess) {
        // API 返回格式: { success: true, data: { list: [...], total: ..., page: ..., pageSize: ... } }
        const imageList = result.data?.list || result.data || [];
        // 去重处理，避免重复 key 警告
        const deduplicatedList = imageList.filter((img: { id: string }, index: number, arr: { id: string }[]) =>
          arr.findIndex((i: { id: string }) => i.id === img.id) === index
        );

        if (append) {
          setImages(prev => {
            const existingIds = new Set(prev.map(img => img.id));
            const newImages = deduplicatedList.filter((img: { id: string }) => !existingIds.has(img.id));
            return [...prev, ...newImages];
          });
        } else {
          setImages(deduplicatedList);
        }
        // 设置分页信息
        setHasMore(result.pagination?.hasMore || result.data?.hasNext || false);
        setCurrentPage(page);
        setTotalCount(result.data?.total || result.pagination?.total || 0);
        setTotalPages(result.data?.totalPages || result.pagination?.totalPages || 0);

        // 动态更新侧边栏"我的知识库/二创中心"的数量
        if (activeMenuItem === 'my-images' || activeMenuItem === 'creative-center') {
          const total = result.data?.total || result.pagination?.total || 0;
          setDynamicTableCount(total);
        }
      } else {
        console.error('[Home] API 返回失败:', result);
      }
    } catch (error) {
      // 后端不可用时静默降级，不打印错误到控制台
      if (!append) {
        // 降级：显示空数据
        setImages([]);
        setTotalCount(0);
        setHasMore(false);
      }
    } finally {
      setLoadingMore(false);
    }
  }, [pageSize, activeMenuItem, filterState.albumFilter, filterState.tagFilter, filterState.dateFilter, filterState.typeFilter, searchQuery]);

  // 获取动态表图片数量（我的知识库/二创中心）
  const fetchDynamicTableCount = React.useCallback(async () => {
    if (!currentUser?.id) return;
    try {
      const isAdmin = currentUser?.role?.toLowerCase() === 'admin';
      const params = isAdmin
        ? `otherUsers=true&pageSize=1`
        : `onlyMine=true&pageSize=1`;
      const response = await backendFetch(`/images?${params}`);
      const result = await response.json();
      if (result.success || result.data) {
        const total = result.data?.total || result.pagination?.total || 0;
        setDynamicTableCount(total);
      }
    } catch {
      // 静默处理
    }
  }, [currentUser]);

  // 获取标签列表
  const fetchTags = React.useCallback(async () => {
    try {
      // 直接调用后端 API
      const response = await backendFetch('/images/tags');
      const result = await response.json();

      if (result.success || result.code === 200) {
        // 确保 tags 是数组
        const tagList = Array.isArray(result.data) ? result.data : [];
        setTags(tagList);
      }
    } catch (error) {
      // 后端不可用时静默降级
    }
  }, []);

  // 监听筛选条件变化，自动重新加载数据
  React.useEffect(() => {
    if (activeMenuItem !== 'trash' && activeMenuItem !== 'recent' && activeMenuItem !== 'favorites') {
      console.log('[Home] 筛选条件变化，重新加载数据:', filterState);
      fetchImages(1, false);
    }
  }, [filterState.dateFilter, filterState.typeFilter, filterState.albumFilter, filterState.tagFilter, filterState.keyword]);

  // 获取商品的所有图片
  // 加载更多图片
  const handleLoadMore = React.useCallback(() => {
    if (!loadingMore && hasMore) {
      fetchImages(currentPage + 1, true);
    }
  }, [loadingMore, hasMore, currentPage, fetchImages]);

  // 检查用户登录状态（只在组件挂载时执行一次）
  React.useEffect(() => {
    let cancelled = false;

    const checkAuth = async () => {
      try {
        // 第一步：检测后端是否可用
        let backendUp = false;
        try {
          const probeRes = await backendFetch('/albums?pageSize=1', {
            headers: { 'X-Session-Id': localStorage.getItem('session_id') || '' },
          });
          backendUp = probeRes.status !== 502;
        } catch {
          backendUp = false;
        }

        if (!backendUp) {
          console.log('[Home] 后端不可用，进入降级模式');
          if (!cancelled) {
            const localUser = localStorage.getItem('user_id');
            const localUsername = localStorage.getItem('user_name');
            setCurrentUser({
              id: localUser || 'local',
              username: localUsername || '本地用户',
              email: '',
              role: 'user',
            });
            authCheckedRef.current = true;
          }
          return;
        }

        // 第二步：后端可用，验证 session
        const sessionId = localStorage.getItem('session_id');
        const expires = localStorage.getItem('session_expires');

        if (!sessionId) {
          console.log('[Home] 无session_id，跳转登录');
          if (!cancelled) window.location.href = '/login';
          return;
        }

        if (!expires || Date.now() > parseInt(expires, 10)) {
          console.log('[Home] Session 已过期，跳转登录');
          if (!cancelled) {
            localStorage.removeItem('session_id');
            localStorage.removeItem('session_expires');
            localStorage.removeItem('portal_type');
            window.location.href = '/login';
          }
          return;
        }

        // 验证后端 session
        try {
          const response = await backendFetch('/auth/session', {
            headers: { 'X-Session-Id': sessionId },
          });

          if (!response.ok) {
            console.log('[Home] 后端session验证失败, status:', response.status);
            if (!cancelled) {
              localStorage.removeItem('session_id');
              localStorage.removeItem('session_expires');
              localStorage.removeItem('portal_type');
              window.location.href = '/login';
            }
            return;
          }

          const result = await response.json();
          console.log('[Home] 会话验证结果:', result);

          if (result.code === 200 && result.data) {
            if (!cancelled) {
              setCurrentUser(result.data);
              authCheckedRef.current = true;
              await fetchAllImages();
              await fetchImages();
              await fetchTags();
              await fetchAlbums();
              fetchDynamicTableCount();
            }
          } else {
            console.log('[Home] 会话验证失败, result:', result);
            if (!cancelled) {
              localStorage.removeItem('session_id');
              localStorage.removeItem('session_expires');
              localStorage.removeItem('portal_type');
              window.location.href = '/login';
            }
          }
        } catch (error) {
          console.error('[Home] 检查登录状态失败:', error);
          // 网络错误时进入降级模式，不强制跳转登录页
          if (!cancelled) {
            setCurrentUser({ id: 'local', username: '本地用户', email: '', role: 'user' });
            authCheckedRef.current = true;
          }
        }
      } catch (error) {
        console.error('[Home] 登录检查异常:', error);
        // 异常时进入降级模式
        if (!cancelled) {
          setCurrentUser({ id: 'local', username: '本地用户', email: '', role: 'user' });
          authCheckedRef.current = true;
        }
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    };

    checkAuth();

    // 监听浏览器后退/前进，确保登出后无法通过后退回到页面
    const handlePageShow = (e: PageTransitionEvent) => {
      if (e.persisted) {
        // 从 bfcache 恢复时不自动跳转，让正常流程处理
        console.log('[Home] 从 bfcache 恢复');
      }
    };
    window.addEventListener('pageshow', handlePageShow);
    return () => {
      cancelled = true;
      window.removeEventListener('pageshow', handlePageShow);
    };
  }, []); // 空依赖，只在挂载时执行一次

  // 监听菜单项变化，重新获取数据
  React.useEffect(() => {
    // 排除初始化时（auth check 会自动获取）
    if (currentUser && authCheckedRef.current) {
      console.log('[Home] 菜单项变化:', activeMenuItem, '重新获取数据...');
      fetchImages(1, false);
    }
  }, [activeMenuItem]); // 只依赖 activeMenuItem，不依赖 currentUser

  // 批量替换主图 - 打开选择弹窗
  const handleBatchReplaceMainImage = async () => {
    if (selectedImages.length === 0) {
      toast.error('请先选择要操作的图片');
      return;
    }
    // 打开弹窗让用户选择要替换的图片
    setShowBatchReplaceDialog(true);
  };

  // 登出
  const handleLogout = async () => {
    try {
      await backendFetch('/auth/login', { method: 'DELETE' });
      // 清除 localStorage 中的 session
      localStorage.removeItem('session_id');
      localStorage.removeItem('session_expires');
      localStorage.removeItem('portal_type');
      // 清除 Cookie 中的 session
      document.cookie = 'session_id=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT; SameSite=Lax';
      toast.success('已退出登录');
      window.location.href = '/login';
    } catch (error) {
      console.error('登出失败:', error);
      toast.error('登出失败');
    }
  };

  // 检查权限
  const isAdmin = currentUser?.role === 'admin';

  const handleSelectImage = (id: string) => {
    setSelectedImages((prev) =>
      prev.includes(id) ? prev.filter((imgId) => imgId !== id) : [...prev, id]
    );
  };

  const handleToggleFavorite = async (id: string) => {
    const image = images.find(img => img.id === id);
    if (!image) return;
    
    try {
      // 调用后端API切换收藏状态
      const response = await backendFetch(`/images/${id}/favorite`, {
        method: 'POST',
        credentials: 'include',
      });
      
      const result = await response.json();
      
      if (isApiSuccess(result)) {
        // 更新本地状态
        const newFavoriteState = !image.favorite;
        
        setImages((prev) =>
          prev.map((img) => (img.id === id ? { ...img, favorite: newFavoriteState } : img))
        );
        
        // 同时更新 allImages 用于统计
        setAllImages((prev) =>
          prev.map((img) => (img.id === id ? { ...img, favorite: newFavoriteState } : img))
        );
        
        // 显示提示
        if (image.favorite) {
          toast.success('已取消收藏', {
            description: `"${image.title}" 已从收藏夹移除`
          });
        } else {
          toast.success('收藏成功', {
            description: `"${image.title}" 已添加到收藏夹`
          });
          // 添加收藏通知
          addNotification({
            type: 'like',
            title: '图片已收藏',
            message: `"${image.title}" 已成功添加到收藏夹。`,
          });
        }
      } else {
        toast.error('操作失败', {
          description: result.error || result.message || '请重试'
        });
      }
    } catch (error) {
      console.error('切换收藏状态失败:', error);
      toast.error('操作失败', {
        description: '网络错误，请重试'
      });
    }
  };

  // 更新图片标签
  const handleUpdateTags = async (id: string, newTags: string[]) => {
    const image = images.find(img => img.id === id);
    if (!image) return;
    
    try {
      const response = await backendFetch(`/images/${id}`, {
        method: 'PUT',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          title: image.title,
          description: image.description,
          albumId: image.albumId,
          tags: newTags,
        }),
      });
      
      const result = await response.json();
      
      if (isApiSuccess(result)) {
        // 更新本地状态
        setImages((prev) =>
          prev.map((img) => (img.id === id ? { ...img, tags: newTags } : img))
        );
        
        // 同时更新 allImages
        setAllImages((prev) =>
          prev.map((img) => (img.id === id ? { ...img, tags: newTags } : img))
        );
        
        // 刷新标签列表
        fetchTags();
        
        toast.success('标签更新成功', {
          description: `图片 "${image.title}" 的标签已更新`
        });
      } else {
        toast.error('更新失败', {
          description: result.error || result.message || '请重试'
        });
      }
    } catch (error) {
      console.error('更新标签失败:', error);
      toast.error('更新失败', {
        description: '网络错误，请重试'
      });
    }
  };

  // 批量更新标签
  const handleBatchUpdateTags = async (newTags: string[]) => {
    const imageIds = selectedImages;
    if (imageIds.length === 0) return;
    
    try {
      // 逐个更新图片标签
      const promises = imageIds.map(id => 
        backendFetch(`/images/${id}`, {
          method: 'PUT',
          credentials: 'include',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({
            tags: newTags,
          }),
        })
      );
      
      const results = await Promise.all(promises);
      const allSuccess = results.every(r => r.ok);
      
      if (allSuccess) {
        // 更新本地状态
        setImages((prev) =>
          prev.map((img) => (imageIds.includes(img.id) ? { ...img, tags: newTags } : img))
        );
        
        // 同时更新 allImages
        setAllImages((prev) =>
          prev.map((img) => (imageIds.includes(img.id) ? { ...img, tags: newTags } : img))
        );
        
        // 刷新标签列表
        fetchTags();
        
        toast.success('批量标签更新成功', {
          description: `已为 ${imageIds.length} 张图片设置标签`
        });
        
        // 清除选中
        setSelectedImages([]);
      } else {
        toast.error('批量更新失败', {
          description: '部分图片标签更新失败，请重试'
        });
      }
    } catch (error) {
      console.error('批量更新标签失败:', error);
      toast.error('批量更新失败', {
        description: '网络错误，请重试'
      });
    }
  };

  // 删除单个图片
  const handleDeleteImage = async (id: string) => {
    const image = images.find(img => img.id === id);
    if (!image) return;
    
    try {
      // 调用API删除图片（移至回收站）
      const response = await backendFetch(`/images/${id}`, {
        method: 'DELETE',
        credentials: 'include',
      });

      const result = await response.json();

      if (isApiSuccess(result)) {
        // 重新获取图片数据
        await fetchImages();
        await fetchAllImages();
        fetchDynamicTableCount();
        
        toast.success('已移至回收站', {
          description: `"${image.title}" 已移至回收站`
        });
        
        // 添加删除通知
        addNotification({
          type: 'warning',
          title: '图片已删除',
          message: `"${image.title}" 已移至回收站，30天内可恢复。`,
        });
      } else {
        toast.error('删除失败', {
          description: result.error || result.message || '请重试'
        });
      }
    } catch (error) {
      console.error('删除图片失败:', error);
      toast.error('删除失败', {
        description: '网络错误，请重试'
      });
    }
  };

  // 移动单个图片到相册
  const handleMoveImage = (id: string) => {
    setSelectedImages([id]);
    setMoveDialogOpen(true);
  };

  // 移动单张图片到指定相册（从子菜单）
  const handleMoveToAlbum = async (imageId: string, albumId: string) => {
    const image = images.find(img => img.id === imageId);
    const targetAlbum = albums.find(a => a.id === albumId);
    
    if (!image || !targetAlbum) return;
    
    try {
      const response = await backendFetch('/images/move', {
        method: 'POST',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          imageIds: [imageId],
          targetAlbumId: albumId,
        }),
      });
      
      const result = await response.json();
      
      // 兼容两种响应格式：{ success: true } 或 { code: 200 }
      const isSuccess = result.success === true || result.code === 200;
      
      if (isSuccess) {
        await fetchImages();
        await fetchAllImages();
        fetchDynamicTableCount();
        
        toast.success('图片移动成功', {
          description: `已将"${image.title}"移动到"${targetAlbum.name}"相册`
        });
        
        // 添加移动通知
        addNotification({
          type: 'album',
          title: '图片已移动',
          message: `"${image.title}" 已成功移动到"${targetAlbum.name}"相册。`,
        });
      } else {
        toast.error('移动失败', {
          description: result.error || result.message || '请重试'
        });
      }
    } catch (error) {
      console.error('移动图片失败:', error);
      toast.error('移动失败', {
        description: '网络错误，请重试'
      });
    }
  };

  // 恢复单个图片（从回收站）
  const handleRestoreImage = async (id: string) => {
    const image = images.find(img => img.id === id);
    if (!image) return;
    
    try {
      const response = await backendFetch('/images/trash/restore', {
        method: 'POST',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          imageIds: [id],
        }),
      });

      const result = await response.json();

      if (isApiSuccess(result)) {
        await fetchImages();
        await fetchAllImages();
        fetchDynamicTableCount();
        
        // 获取恢复的图片数量（主图+详情图）
        const restoredCount = result.data || 1;
        const countText = restoredCount > 1 ? `（含 ${restoredCount - 1} 张详情图）` : '';
        
        toast.success('恢复成功', {
          description: `"${image.title}" 已恢复 ${countText}`
        });
        
        // 添加恢复通知
        addNotification({
          type: 'success',
          title: '图片已恢复',
          message: `"${image.title}" 已成功从回收站恢复 ${countText}。`,
        });
      } else {
        toast.error('恢复失败', {
          description: result.error || result.message || '请重试'
        });
      }
    } catch (error) {
      console.error('恢复图片失败:', error);
      toast.error('恢复失败', {
        description: '网络错误，请重试'
      });
    }
  };

  // 永久删除单个图片
  const handlePermanentDeleteImage = async (id: string) => {
    const image = images.find(img => img.id === id);
    if (!image) return;
    
    if (!confirm(`确定永久删除 "${image.title}"？此操作无法撤销！`)) {
      return;
    }
    
    try {
      const response = await backendFetch(`/images/${id}/permanent`, {
        method: 'DELETE',
        credentials: 'include',
      });

      const result = await response.json();

      if (isApiSuccess(result)) {
        await fetchImages();
        await fetchAllImages();
        fetchDynamicTableCount();
        
        // 获取删除的图片数量（主图+详情图）
        const deletedCount = result.data || 1;
        const countText = deletedCount > 1 ? `（含 ${deletedCount - 1} 张详情图）` : '';
        
        toast.success('已永久删除', {
          description: `"${image.title}" 已被永久删除 ${countText}`
        });
        
        // 添加永久删除通知
        addNotification({
          type: 'delete',
          title: '图片已永久删除',
          message: `"${image.title}" 已被永久删除 ${countText}，无法恢复。`,
        });
      } else {
        toast.error('删除失败', {
          description: result.error || result.message || '请重试'
        });
      }
    } catch (error) {
      console.error('永久删除图片失败:', error);
      toast.error('删除失败', {
        description: '网络错误，请重试'
      });
    }
  };

  const handleSortChange = (newSortBy: 'date' | 'name' | 'size', newSortOrder: 'asc' | 'desc') => {
    setSortBy(newSortBy);
    setSortOrder(newSortOrder);
  };

  // 批量移动图片到相册 - 调用API（从对话框）
  const handleBulkMoveToAlbum = async () => {
    if (!selectedAlbumId || selectedImages.length === 0) return;
    
    const targetAlbum = albums.find(a => a.id === selectedAlbumId);
    const count = selectedImages.length;
    
    try {
      // 调用API移动图片
      const response = await backendFetch('/images/move', {
        method: 'POST',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          imageIds: selectedImages,
          targetAlbumId: selectedAlbumId,
        }),
      });
      
      const result = await response.json();
      
      if (isApiSuccess(result)) {
        // 刷新数据
        await fetchImages();
        await fetchAllImages();
        fetchDynamicTableCount();
        
        toast.success('批量移动成功', {
          description: `已将 ${count} 张图片移动到"${targetAlbum?.name}"相册`
        });
        
        // 添加批量移动通知
        addNotification({
          type: 'album',
          title: '批量移动完成',
          message: `已成功将 ${count} 张图片移动到"${targetAlbum?.name}"相册。`,
        });
        
        // 清空选择并关闭对话框
        setSelectedImages([]);
        setSelectedAlbumId(null);
        setMoveDialogOpen(false);
      } else {
        toast.error('移动失败', {
          description: result.error || result.message || '操作失败，请重试'
        });
      }
    } catch (error) {
      console.error('移动图片失败:', error);
      toast.error('移动失败', {
        description: '网络错误，请重试'
      });
    }
  };

  // 处理侧边栏菜单点击
  const handleMenuItemClick = async (item: string) => {
    console.log('[Home] 菜单项点击:', item);

    // 如果点击知识库，跳转到知识库页面
    if (item === 'knowledge') {
      router.push('/knowledge');
      return;
    }

    // 如果点击AI对话，跳转到聊天页面
    if (item === 'chat') {
      router.push('/chat');
      return;
    }

    // 如果点击AI生图，跳转到生图页面
    if (item === 'ai-image') {
      router.push('/ai-image');
      return;
    }

    // 如果点击数据仪表盘，跳转到仪表盘页面
    if (item === 'dashboard') {
      router.push('/dashboard');
      return;
    }

    // 如果点击AI能力中心，跳转到AI能力中心页面
    if (item === 'ai-center') {
      router.push('/ai-center');
      return;
    }
    if (item === 'data-assets') {
      router.push('/data-assets');
      return;
    }

    // 如果点击系统设置，跳转到设置页面
    if (item === 'settings') {
      router.push('/settings');
      return;
    }

    // 如果点击用户设置，跳转到用户设置页面
    if (item === 'user-settings') {
      router.push('/user-settings');
      return;
    }

    // 检查是否点击了智能相册
    const smartAlbum = smartAlbums.find(a => a.id === item);
    if (smartAlbum) {
      setActiveMenuItem(item);
      setSelectedImages([]);
      setCurrentPage(1);
      setHasMore(false);
      setIsLoading(true);
      setSearchQuery('');

      // 使用 API 执行匹配筛选
      executeSmartAlbumRules(item, 1, pageSize).then(result => {
        if (result) {
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          setImages((result.images as any[]).map((img: any) => {
            const c = { ...img };
            if (typeof c.url === 'string') c.url = getFullImageUrl(c.url);
            if (typeof c.thumbnail === 'string') c.thumbnail = getFullImageUrl(c.thumbnail);
            if (typeof c.originalUrl === 'string') c.originalUrl = getFullImageUrl(c.originalUrl);
            if (typeof c.mainImageUrl === 'string') c.mainImageUrl = getFullImageUrl(c.mainImageUrl);
            return c;
          }));
          setHasMore(result.page < result.totalPages);
        } else {
          // API 失败时回退到前端筛选
          const filtered = filterImagesBySmartAlbum(allImages, smartAlbum as import('@/lib/api/types').SmartAlbum);
          setImages(filtered);
        }
        setIsLoading(false);
      });
      return;
    }

    setActiveMenuItem(item);
    // 切换菜单时清空选中状态和重置分页
    setSelectedImages([]);
    setCurrentPage(1);
    setHasMore(false);
    
    // 非知识分类页面清空搜索
    const isAlbumPage = item.startsWith('album-') || albums.some(a => a.id === item);
    if (item !== 'all' && !isAlbumPage) {
      setSearchQuery('');
    }

    // 如果点击上传图片，打开上传对话框
    if (item === 'upload') {
      setUploadDialogOpen(true);
    } else if (item === 'documents') {
      // 如果点击文档中心，打开文档上传对话框
      setDocumentUploadDialogOpen(true);
    } else {
      // 对于其他菜单项，立即获取数据
      try {
        await fetchImages(1, false);
      } catch (error) {
        console.error('[Home] 获取数据失败:', error);
      }
    }
  };

  // 上传成功后刷新图片列表
  const handleUploadSuccess = async () => {
    console.log('[Home] 上传成功，开始刷新图片列表...');

    try {
      // 重置分页状态
      setCurrentPage(1);

      // 构建请求参数（只用于当前视图显示）
      const params = new URLSearchParams({
        page: '1',
        pageSize: String(pageSize),
        includeDeleted: 'true',
      });

      console.log('[Home] 请求图片列表...');

      // 获取图片列表（用于当前视图显示）
      const response = await backendFetch(`/images?${params}`);
      const result = await response.json();

      console.log('[Home] 图片列表响应:', result);

      if (isApiSuccess(result)) {
        const imageList = result.data?.list || result.data || [];
        console.log('[Home] 获取到图片列表，数量:', imageList.length);
        setImages(imageList.map((img: Record<string, unknown>) => {
          const c = { ...img };
          if (typeof c.url === 'string') c.url = getFullImageUrl(c.url);
          if (typeof c.thumbnail === 'string') c.thumbnail = getFullImageUrl(c.thumbnail);
          if (typeof c.originalUrl === 'string') c.originalUrl = getFullImageUrl(c.originalUrl);
          if (typeof c.mainImageUrl === 'string') c.mainImageUrl = getFullImageUrl(c.mainImageUrl);
          return c;
        }));
        setHasMore(false);

        // 获取完整数据用于统计（不使用当前视图的部分数据）
        await fetchAllImages();
        fetchDynamicTableCount();
      }

      // 获取标签列表
      const tagsResponse = await backendFetch('/images/tags');
      const tagsResult = await tagsResponse.json();

      if (tagsResult.success) {
        const tagList = Array.isArray(tagsResult.data) ? tagsResult.data : [];
        setTags(tagList);
      }

      toast.success('上传成功', {
        description: '图片已成功上传，AI已自动识别并分类'
      });
    } catch (error) {
      console.error('刷新图片列表失败:', error);
      toast.error('刷新失败', {
        description: '请手动刷新页面查看新上传的图片'
      });
    }
  };

  // Excel批量上传成功后刷新图片列表
  const handleExcelUploadSuccess = async () => {
    console.log('[Home] Excel批量上传成功，开始刷新图片列表...');
    
    try {
      // 重置分页状态
      setCurrentPage(1);
      
      // 构建请求参数
      const params = new URLSearchParams({
        page: '1',
        pageSize: String(pageSize),
        includeDeleted: 'true',
      });
      
      console.log('[Home] 请求图片列表...');
      
      // 获取图片列表
      const response = await backendFetch(`/images?${params}`);
      const result = await response.json();
      
      console.log('[Home] 图片列表响应:', result);
      
      if (isApiSuccess(result)) {
        const imageList = result.data?.list || result.data || [];
        console.log('[Home] 获取到图片列表，数量:', imageList.length);
        setImages(imageList.map((img: Record<string, unknown>) => {
          const c = { ...img };
          if (typeof c.url === 'string') c.url = getFullImageUrl(c.url);
          if (typeof c.thumbnail === 'string') c.thumbnail = getFullImageUrl(c.thumbnail);
          if (typeof c.originalUrl === 'string') c.originalUrl = getFullImageUrl(c.originalUrl);
          if (typeof c.mainImageUrl === 'string') c.mainImageUrl = getFullImageUrl(c.mainImageUrl);
          return c;
        }));
        setHasMore(false);

        // 获取完整数据用于统计（不使用当前视图的部分数据）
        await fetchAllImages();
        fetchDynamicTableCount();
      }
      
      // 获取标签列表
      const tagsResponse = await backendFetch('/images/tags');
      const tagsResult = await tagsResponse.json();
      
      if (tagsResult.success) {
        const tagList = Array.isArray(tagsResult.data) ? tagsResult.data : [];
        setTags(tagList);
      }
      
      toast.success('批量下载成功', {
        description: '图片已成功下载并保存到系统'
      });
    } catch (error) {
      console.error('刷新图片列表失败:', error);
      toast.error('刷新失败', {
        description: '请手动刷新页面查看新下载的图片'
      });
    }
  };

  const filteredImages = React.useMemo(() => {
    // 防御性代码：确保 images 是数组
    if (!Array.isArray(images)) {
      console.warn('[Home] images 不是数组:', typeof images, images);
      return [];
    }
    
    console.log('[Home] 直接使用后端返回的数据，不进行前端二次筛选');
    console.log('[Home] images 数量:', images.length);
    
    // 直接使用后端返回的数据，只做排序
    const result = [...images];
    
    // 排序
    result.sort((a, b) => {
      let comparison = 0;
      if (sortBy === 'date') {
        const dateA = a.date || a.createdAt || '';
        const dateB = b.date || b.createdAt || '';
        comparison = new Date(dateA).getTime() - new Date(dateB).getTime();
      } else if (sortBy === 'name') {
        comparison = a.title.localeCompare(b.title);
      } else if (sortBy === 'size') {
        const sizeA = parseFloat(a.size);
        const sizeB = parseFloat(b.size);
        comparison = sizeA - sizeB;
      }
      return sortOrder === 'asc' ? comparison : -comparison;
    });

    return result;
  }, [images, sortBy, sortOrder]);

  // 全选所有图片
  const handleSelectAll = () => {
    const allImageIds = filteredImages.map(img => img.id);
    setSelectedImages(allImageIds);
  };

  // 取消全选
  const handleDeselectAll = () => {
    setSelectedImages([]);
  };

  // 判断是否全选（当前页所有图片都被选中）
  const isAllSelected = filteredImages.length > 0 && filteredImages.every(img => selectedImages.includes(img.id));

  // 计算动态统计数据（使用完整数据源 allImages）
  const statistics = React.useMemo(() => {
    // 只统计主图（商品），不统计详情图
    const mainImages = allImages.filter(img => img.isMainImage && !img.deleted);

    // 全部图片数（只统计主图）
    const allCount = mainImages.length;

    // 收藏图片数（只统计主图）
    const favoritesCount = mainImages.filter(img => img.favorite).length;

    // 最近上传（7天内，只统计主图）
    const sevenDaysAgo = new Date();
    sevenDaysAgo.setDate(sevenDaysAgo.getDate() - 7);
    const recentCount = mainImages.filter(img => {
      // 兼容 date 和 createdAt 两种字段名
      const dateStr = img.date || img.createdAt;
      if (!dateStr) return false;
      const imgDate = new Date(dateStr);
      return imgDate >= sevenDaysAgo;
    }).length;

    // 今日上传（只统计主图）
    const todayStart = new Date();
    todayStart.setHours(0, 0, 0, 0);
    const todayImages = mainImages.filter(img => {
      const dateStr = img.date || img.createdAt;
      if (!dateStr) return false;
      const imgDate = new Date(dateStr);
      return imgDate >= todayStart;
    });

    // 回收站数量（使用后端获取的数量，更准确）
    // 注意：trashCount 现在由后端 API 直接提供，不再从 allImages 计算

    // 我的图片数（只统计当前用户上传的主图）
    const myImagesCount = currentUser?.id 
      ? mainImages.filter(img => img.userId === currentUser.id).length 
      : 0;

    // 各相册图片数量 - 只统计主图
    const albumStats = albums.map(album => {
      const count = mainImages.filter(img => img.albumId === album.id).length;
      return {
        id: album.id,
        name: album.name,
        fullName: album.fullName || album.name,
        parentId: album.parentId || undefined,
        path: album.path || album.name,
        count,
      };
    });

    // 调试日志
    console.log('[Statistics] 统计数据:', {
      allImagesTotal: allImages.length,
      mainImagesCount: mainImages.length,
      allCount,
      myImagesCount,
      favoritesCount,
      recentCount,
      trashCount,
      albumStats,
      currentUserId: currentUser?.id,
    });

    return {
      allCount,
      myImagesCount,
      favoritesCount,
      recentCount,
      albumStats,
      // 今日统计
      todayUploads: todayImages.length,
      todayViews: todayImages.reduce((sum, img) => sum + (img.viewCount || 0), 0),
      todayDownloads: todayImages.reduce((sum, img) => sum + (img.downloadCount || 0), 0),
      todayFavorites: todayImages.filter(img => img.favorite).length,
    };
  }, [allImages, albums, currentUser?.id]);

  // 加载中状态
  if (isLoading) {
    return (
      <div className="min-h-screen bg-gradient-to-br from-[#0a0e1a] via-[#0f172a] to-[#0a1628] flex items-center justify-center">
        <div className="flex flex-col items-center gap-4">
          <Loader2 className="w-12 h-12 text-violet-500 animate-spin" />
          <p className="text-slate-500">加载中...</p>
        </div>
      </div>
    );
  }

  // 未登录状态
  if (!currentUser) {
    return null;
  }

  return (
    <div className="flex h-screen bg-[#f8f9fc] overflow-hidden">
      {/* Toast 提示 */}
      <Toaster position="top-center" richColors closeButton />
      
      {/* 侧边栏 */}
      <Sidebar
        activeItem={activeMenuItem}
        onItemClick={handleMenuItemClick}
        collapsed={sidebarCollapsed}
        albums={statistics.albumStats}
        smartAlbums={smartAlbums}
        allImagesCount={statistics.allCount}
        myImagesCount={dynamicTableCount}
        favoritesCount={statistics.favoritesCount}
        recentCount={statistics.recentCount}
        trashCount={trashCount}
        isAdmin={isAdmin}
        documentStats={documentStats}
        brand={brand}
        onAlbumCreated={fetchAlbums}
        onCreateSmartAlbum={() => {
          setEditingSmartAlbum(null);
          setIsSmartAlbumEditorOpen(true);
        }}
        onAlbumClick={(albumId, allAlbumIds) => {
          // 设置选中的相册及其所有子相册 ID
          setSelectedAlbumIds(allAlbumIds);
          handleMenuItemClick(albumId);
        }}
      />

      {/* 智能相册编辑器 */}
      <SmartAlbumEditor
        open={isSmartAlbumEditorOpen}
        onOpenChange={setIsSmartAlbumEditorOpen}
        onConfirm={handleSaveSmartAlbum}
        initialData={editingSmartAlbum ? {
          name: editingSmartAlbum.name,
          description: '',
          matchingConfig: editingSmartAlbum.matchingConfig,
        } : undefined}
        mode={editingSmartAlbum ? 'edit' : 'create'}
      />

      {/* 主内容区 */}
      <div className="flex-1 flex flex-col overflow-hidden">
        {/* 顶部栏 - 仅在知识分类页面显示搜索 */}
        <Header
          searchQuery={searchQuery}
          onSearchChange={handleSearchChange}
          onSearchSubmit={handleSearchSubmit}
          viewMode={viewMode}
          onViewModeChange={setViewMode}
          selectedCount={selectedImages.length}
          onBulkAction={() => {}}
          currentUser={currentUser}
          onLogout={handleLogout}
          onFilterClick={() => setFilterPanelOpen(true)}
          onExcelUploadClick={() => setExcelUploadDialogOpen(true)}
          onExportClick={() => setExportDialogOpen(true)}
          onBatchReplaceMainImage={handleBatchReplaceMainImage}
          hasAlbums={albums.length > 0}
          showSearch={activeMenuItem === 'all' || activeMenuItem.startsWith('album-') || albums.some(a => a.id === activeMenuItem)}
          brand={brand}
        />

        {/* 主内容 */}
        <main className="flex-1 overflow-y-auto bg-gradient-to-br from-slate-50/30 to-white">
          {/* 文档中心 */}
          {(activeMenuItem === 'documents' || activeMenuItem.startsWith('doc-')) && (
            <DocumentManager 
              initialCategory={activeMenuItem === 'documents' ? 'all' : activeMenuItem.replace('doc-', '') as 'pdf' | 'word' | 'excel' | 'ppt' | 'zip' | 'other'}
              onStatsUpdate={(stats) => setDocumentStats(stats)}
            />
          )}

          {/* 知识分类页面内容 - 仅在知识分类页面显示 */}
          {!activeMenuItem.startsWith('documents') && !activeMenuItem.startsWith('doc-') && (
            <div className="p-6">
              {/* 标题栏 */}
              <div className="mb-4 flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <h1 className="text-2xl font-bold text-slate-800">
                    {activeMenuItem === 'all'
                      ? '全部图片'
                      : activeMenuItem === 'my-images'
                      ? '我的二创'
                      : activeMenuItem === 'creative-center'
                      ? '二创中心'
                      : activeMenuItem === 'favorites'
                      ? '我的收藏'
                      : activeMenuItem === 'recent'
                      ? '最近上传'
                      : activeMenuItem === 'trash'
                      ? '回收站'
                      : (activeMenuItem.startsWith('album-') || albums.some(a => a.id === activeMenuItem))
                      ? (activeMenuItem.startsWith('album-') 
                          ? albums.find(a => a.id === activeMenuItem.replace('album-', ''))?.name 
                          : albums.find(a => a.id === activeMenuItem)?.name) || '相册'
                      : '图片'}
                  </h1>
                  <div className="text-sm text-slate-500">
                    ({filteredImages.length} 张图片)
                  </div>
                </div>

                {/* 全选按钮 - 有图片时显示 */}
                {filteredImages.length > 0 && (
                  <label className="flex items-center gap-2 cursor-pointer select-none">
                    <input
                      type="checkbox"
                      checked={isAllSelected}
                      onChange={(e) => {
                        if (e.target.checked) {
                          handleSelectAll();
                        } else {
                          handleDeselectAll();
                        }
                      }}
                      className={cn(
                        "w-4 h-4 rounded border focus:ring cursor-pointer",
                        activeMenuItem === 'trash'
                          ? "border-red-300 text-red-600 focus:ring-red-500"
                          : "border-slate-300 text-violet-600 focus:ring-violet-500"
                      )}
                    />
                    <span className={cn(
                      "text-sm",
                      activeMenuItem === 'trash' ? "text-red-600" : "text-slate-600"
                    )}>
                      全选
                    </span>
                  </label>
                )}
              </div>

              {/* 筛选面板 */}
              <FilterPanel
                isOpen={filterPanelOpen}
                onClose={() => setFilterPanelOpen(false)}
                filters={filterState}
                onFilterChange={setFilterState}
                albums={albums}
                tags={tags}
              />

              {/* 图片网格 - 转换图片 URL 为完整路径 */}
              <ImageGrid
              images={filteredImages.map(img => ({
                ...img,
                url: getFullImageUrl(img.url),
              }))}
              viewMode={viewMode}
              selectedImages={selectedImages}
              onSelectImage={handleSelectImage}
              onPreviewImage={(image) => {
                // 如果图片有productId，跳转到商品详情页
                if (image.productId) {
                  router.push(`/products/${image.productId}`);
                } else {
                  setPreviewImage(image);
                }
              }}
              onToggleFavorite={handleToggleFavorite}
              onDeleteImage={handleDeleteImage}
              onMoveImage={handleMoveImage}
              onMoveToAlbum={handleMoveToAlbum}
              albums={albums.map(album => {
                const stat = statistics.albumStats.find(s => s.id === album.id);
                return {
                  ...album,
                  imageCount: stat?.count || 0,
                };
              })}
              onRestoreImage={handleRestoreImage}
              onPermanentDeleteImage={handlePermanentDeleteImage}
              isTrash={activeMenuItem === 'trash'}
              hasMore={hasMore}
              onLoadMore={handleLoadMore}
              loading={loadingMore}
              compactMode={settings?.compactMode}
              showFileInfo={settings?.showFileInfo}
              searchQuery={activeMenuItem === 'all' || activeMenuItem.startsWith('album-') || albums.some(a => a.id === activeMenuItem)
                ? searchQuery 
                : ''}
            />

            {/* 分页组件 */}
            {totalPages > 1 && (
              <div className="flex items-center justify-center gap-2 mt-6 mb-4">
                {/* 上一页 */}
                <button
                  onClick={() => {
                    if (currentPage > 1) {
                      setCurrentPage(currentPage - 1);
                      fetchImages(currentPage - 1, false, searchQuery);
                    }
                  }}
                  disabled={currentPage === 1}
                  className="px-3 py-1.5 rounded-lg text-sm font-medium transition-colors disabled:opacity-50 disabled:cursor-not-allowed bg-slate-100 hover:bg-slate-200 text-slate-700"
                >
                  上一页
                </button>

                {/* 页码按钮 */}
                <div className="flex items-center gap-1">
                  {Array.from({ length: Math.min(5, totalPages) }, (_, i) => {
                    let pageNum: number;
                    if (totalPages <= 5) {
                      pageNum = i + 1;
                    } else if (currentPage <= 3) {
                      pageNum = i + 1;
                    } else if (currentPage >= totalPages - 2) {
                      pageNum = totalPages - 4 + i;
                    } else {
                      pageNum = currentPage - 2 + i;
                    }
                    return (
                      <button
                        key={pageNum}
                        onClick={() => {
                          setCurrentPage(pageNum);
                          fetchImages(pageNum, false, searchQuery);
                        }}
                        className={`w-8 h-8 rounded-lg text-sm font-medium transition-colors ${
                          currentPage === pageNum
                            ? 'bg-violet-500 text-white'
                            : 'bg-slate-100 hover:bg-slate-200 text-slate-700'
                        }`}
                      >
                        {pageNum}
                      </button>
                    );
                  })}
                </div>

                {/* 下一页 */}
                <button
                  onClick={() => {
                    if (currentPage < totalPages) {
                      setCurrentPage(currentPage + 1);
                      fetchImages(currentPage + 1, false, searchQuery);
                    }
                  }}
                  disabled={currentPage === totalPages}
                  className="px-3 py-1.5 rounded-lg text-sm font-medium transition-colors disabled:opacity-50 disabled:cursor-not-allowed bg-slate-100 hover:bg-slate-200 text-slate-700"
                >
                  下一页
                </button>

                {/* 分页信息 */}
                <span className="text-sm text-slate-500 ml-4">
                  共 {totalCount} 条，第 {currentPage}/{totalPages} 页
                </span>
              </div>
            )}
            </div>
          )}
        </main>
      </div>

      {/* 图片预览 - 转换图片 URL 为完整路径 */}
      {previewImage && (
        <ImagePreview
          image={{ ...previewImage, url: getFullImageUrl(previewImage.url) }}
          images={filteredImages.map(img => ({ ...img, url: getFullImageUrl(img.url) }))}
          productId={previewImage?.productId}
          onClose={() => setPreviewImage(null)}
          onNavigate={(img) => setPreviewImage(img && typeof img === 'object' ? img : previewImage)}
          onToggleFavorite={handleToggleFavorite}
        />
      )}

      {/* 批量操作 */}
      <BulkActions
        selectedCount={selectedImages.length}
        onClearSelection={() => setSelectedImages([])}
        isTrash={activeMenuItem === 'trash'}
        onRestore={async () => {
          const count = selectedImages.length;
          if (confirm(`确定恢复 ${count} 张图片?`)) {
            try {
              // 调用API批量恢复图片
              const response = await backendFetch('/images/trash/restore', {
                method: 'POST',
                credentials: 'include',
                headers: {
                  'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                  imageIds: selectedImages,
                }),
              });

              const result = await response.json();

              if (isApiSuccess(result)) {
                // 重新获取图片数据
                await fetchImages();
                await fetchAllImages();
        fetchDynamicTableCount();
                
                // 获取恢复的图片数量（主图+详情图）
                const restoredCount = result.data || count;
                const countText = restoredCount !== count ? `（共 ${restoredCount} 张，含详情图）` : '';
                
                toast.success('恢复成功', {
                  description: `${count} 张图片已从回收站恢复 ${countText}`
                });
                
                // 添加恢复通知
                addNotification({
                  type: 'success',
                  title: '批量恢复完成',
                  message: `${count} 张图片已成功从回收站恢复 ${countText}。`,
                });
                
                setSelectedImages([]);
              } else {
                toast.error('恢复失败', {
                  description: result.error || result.message || '请重试'
                });
              }
            } catch (error) {
              console.error('批量恢复失败:', error);
              toast.error('恢复失败', {
                description: '网络错误，请重试'
              });
            }
          }
        }}
        onPermanentDelete={async () => {
          const count = selectedImages.length;
          if (confirm(`⚠️ 确定永久删除 ${count} 张图片？\n\n此操作不可撤销，图片将被彻底删除！`)) {
            try {
              // 调用API批量永久删除
              const response = await backendFetch('/images/delete', {
                method: 'POST',
                credentials: 'include',
                headers: {
                  'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                  imageIds: selectedImages,
                  permanent: true, // 永久删除标志
                }),
              });

              const result = await response.json();

              if (isApiSuccess(result)) {
                // 重新获取图片数据
                await fetchImages();
                await fetchAllImages();
        fetchDynamicTableCount();
                
                // 获取删除的图片数量（主图+详情图）
                const deletedCount = result.data || count;
                const countText = deletedCount !== count ? `（共 ${deletedCount} 张，含详情图）` : '';
                
                toast.success('已永久删除', {
                  description: `${count} 张图片已被永久删除 ${countText}`
                });
                
                // 添加永久删除通知
                addNotification({
                  type: 'delete',
                  title: '批量永久删除完成',
                  message: `${count} 张图片已被永久删除 ${countText}，无法恢复。`,
                });
                
                setSelectedImages([]);
              } else {
                toast.error('删除失败', {
                  description: result.error || result.message || '请重试'
                });
              }
            } catch (error) {
              console.error('批量永久删除失败:', error);
              toast.error('删除失败', {
                description: '网络错误，请重试'
              });
            }
          }
        }}
        onDownload={() => {
          const count = selectedImages.length;
          toast.success('开始下载', {
            description: `正在下载 ${count} 张图片...`
          });
          
          // 添加下载通知
          addNotification({
            type: 'download',
            title: '批量下载开始',
            message: `正在下载 ${count} 张图片到本地。`,
          });
        }}
        onDelete={async () => {
          const count = selectedImages.length;
          if (confirm(`确定将 ${count} 张图片移至回收站?`)) {
            try {
              // 调用API批量删除
              const response = await backendFetch('/images/delete', {
                method: 'POST',
                credentials: 'include',
                headers: {
                  'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                  imageIds: selectedImages,
                }),
              });

              const result = await response.json();

              if (isApiSuccess(result)) {
                // 重新获取图片数据
                await fetchImages();
                await fetchAllImages();
        fetchDynamicTableCount();
                
                toast.success('已移至回收站', {
                  description: `${count} 张图片已移至回收站`
                });
                
                // 添加批量删除通知
                addNotification({
                  type: 'warning',
                  title: '批量删除完成',
                  message: `${count} 张图片已移至回收站，30天内可恢复。`,
                });
                
                setSelectedImages([]);
              } else {
                toast.error('删除失败', {
                  description: result.error || result.message || '请重试'
                });
              }
            } catch (error) {
              console.error('批量删除失败:', error);
              toast.error('删除失败', {
                description: '网络错误，请重试'
              });
            }
          }
        }}
        onMove={() => {
          setMoveDialogOpen(true);
        }}
        onFavorite={async () => {
          const count = selectedImages.length;
          try {
            // 调用API批量收藏
            const response = await backendFetch('/images/batch', {
              method: 'POST',
              credentials: 'include',
              headers: {
                'Content-Type': 'application/json',
              },
              body: JSON.stringify({
                operation: 'favorite',
                imageIds: selectedImages,
              }),
            });

            const result = await response.json();

            if (isApiSuccess(result)) {
              // 重新获取图片数据
              await fetchImages();
              await fetchAllImages();
        fetchDynamicTableCount();
              
              toast.success('批量收藏成功', {
                description: `已将 ${count} 张图片添加到收藏夹`
              });
              
              // 添加批量收藏通知
              addNotification({
                type: 'like',
                title: '批量收藏完成',
                message: `${count} 张图片已成功添加到收藏夹。`,
              });
              
              setSelectedImages([]);
            } else {
              toast.error('收藏失败', {
                description: result.error || result.message || '请重试'
              });
            }
          } catch (error) {
            console.error('批量收藏失败:', error);
            toast.error('收藏失败', {
              description: '网络错误，请重试'
            });
          }
        }}
        onBatchUpdateTags={handleBatchUpdateTags}
        availableTags={tags}
      />

      {/* 移动到相册对话框 */}
      <MoveToAlbumDialog
        open={moveDialogOpen}
        onOpenChange={setMoveDialogOpen}
        albums={albums.map(album => {
          const stat = statistics.albumStats.find(s => s.id === album.id);
          return {
            ...album,
            imageCount: stat?.count || 0,
          };
        })}
        selectedAlbumId={selectedAlbumId}
        onSelectAlbum={setSelectedAlbumId}
        onConfirm={handleBulkMoveToAlbum}
        imageCount={selectedImages.length}
      />

      {/* 上传图片对话框 */}
      <UploadDialog
        open={uploadDialogOpen}
        onOpenChange={setUploadDialogOpen}
        onUploadSuccess={handleUploadSuccess}
      />
      
      {/* 上传文档对话框 */}
      <DocumentUploadDialog
        open={documentUploadDialogOpen}
        onOpenChange={setDocumentUploadDialogOpen}
        onUploadSuccess={() => {
          console.log('[Home] 文档上传成功，刷新页面');
          fetchImages();
        }}
      />
      
      <ExcelBatchUpload
        open={excelUploadDialogOpen}
        onOpenChange={setExcelUploadDialogOpen}
        onUploadSuccess={handleExcelUploadSuccess}
      />
      
      {/* 批量导出对话框 */}
      <ExportDialog
        albums={albums}
        open={exportDialogOpen}
        onOpenChange={setExportDialogOpen}
      />
      
      {/* 批量替换主图弹窗 */}
      <BatchReplaceMainImageDialog
        open={showBatchReplaceDialog}
        onOpenChange={setShowBatchReplaceDialog}
        selectedImageIds={selectedImages}
        onSuccess={() => {
          setSelectedImages([]);
          fetchImages(1, false);
        }}
      />
    </div>
  );
}
