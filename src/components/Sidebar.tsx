'use client';

import React from 'react';
import { useRouter } from 'next/navigation';
import { cn } from '@/lib/utils';
import {
  Image,
  FolderOpen,
  Upload,
  Trash2,
  Settings,
  Heart,
  Clock,
  BookOpen,
  UserCog,
  Plus,
  RotateCcw,
  Settings2,
  FileText,
  Folder,
  Bookmark,
  Search,
  BookType,
  Wand2,
  Sparkles,
  LayoutDashboard,
  FileIcon,
  FileSpreadsheet,
  Presentation,
  Archive,
  File,
  ChevronRight,
  Square,
  CheckSquare,
  Trash,
  Library,
  Brain,
  MessageSquare,
  Scissors,
  Cloud,
} from 'lucide-react';
import { type BrandConfig } from '@/lib/brand';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { toast } from 'sonner';

export interface AlbumInfo {
  id: string;
  name: string;
  fullName?: string;
  parentId?: string;
  path?: string;
  count: number;
  isSystem?: boolean;
  children?: AlbumInfo[];
}

interface MenuItem {
  id: string;
  label: string;
  icon: any;
  count?: number;
  children?: MenuItem[];
  showAddButton?: boolean;
  isSystem?: boolean;
}

/**
 * 将扁平相册数组构建为层级结构
 */
function buildAlbumHierarchy(albums: AlbumInfo[]): MenuItem[] {
  const albumMap = new Map<string, AlbumInfo>();
  const rootAlbums: AlbumInfo[] = [];
  
  // 先将所有相册放入 map
  albums.forEach(album => {
    albumMap.set(album.id, { ...album, children: [] });
  });
  
  // 构建层级关系
  albums.forEach(album => {
    const currentAlbum = albumMap.get(album.id)!;
    if (album.parentId && albumMap.has(album.parentId)) {
      // 有父相册，加入父相册的 children
      albumMap.get(album.parentId)!.children!.push(currentAlbum);
    } else {
      // 顶级相册
      rootAlbums.push(currentAlbum);
    }
  });
  
  // 转换为 MenuItem 格式
  function albumToMenuItem(album: AlbumInfo): MenuItem {
    // 从 name 中提取最后一部分作为显示名称（如 "松野湃/速干T恤" -> "速干T恤"）
    const displayName = album.name.includes('/') 
      ? album.name.split('/').pop() || album.name 
      : album.name;
    return {
      id: album.id, // 数据库中存储的就是带 album- 前缀的格式
      label: displayName,
      icon: Image as React.ElementType, // 图标组件
      count: album.count,
      isSystem: album.isSystem,
      children: album.children?.map(child => albumToMenuItem(child)),
    };
  }
  
  return rootAlbums.map(album => albumToMenuItem(album));
}

/**
 * 递归渲染子菜单项
 */
function SubMenuItem({
  item,
  activeItem,
  onItemClick,
  onDelete,
  level = 1,
  batchSelectMode = false,
  selectedIds = new Set<string>(),
  onToggleSelect,
  selectable = true,
  brandAccent = 'violet',
}: {
  item: MenuItem;
  activeItem: string;
  onItemClick: (id: string) => void;
  onDelete?: (item: MenuItem, e: React.MouseEvent) => void;
  level?: number;
  batchSelectMode?: boolean;
  selectedIds?: Set<string>;
  onToggleSelect?: (id: string) => void;
  selectable?: boolean;
  brandAccent?: string;
}) {
  const hasChildren = item.children && item.children.length > 0;
  const [isExpanded, setIsExpanded] = React.useState(level <= 1);
  const isActive = activeItem === item.id;
  const [showActions, setShowActions] = React.useState(false);
  const isSelected = selectedIds.has(item.id);
  const isSelectable = selectable && !item.isSystem;

  return (
    <div>
      <div
        className={cn(
          'group flex items-center gap-2 rounded-lg text-[13px] transition-all duration-150',
          level > 0 ? 'px-3 py-1.5' : 'px-3 py-2',
          isActive
            ? cn(brandAccent === 'rose' ? 'bg-rose-50 text-rose-700' : 'bg-violet-50 text-violet-700')
            : 'text-slate-500 hover:bg-slate-50 hover:text-slate-700',
          batchSelectMode && isSelectable && 'cursor-pointer'
        )}
        style={{ paddingLeft: `${level > 0 ? level * 12 + 12 : 12}px` }}
        onMouseEnter={() => setShowActions(true)}
        onMouseLeave={() => setShowActions(false)}
      >
        {/* 批量选择复选框 */}
        {batchSelectMode && isSelectable && (
          <button
            onClick={(e) => {
              e.stopPropagation();
              onToggleSelect?.(item.id);
            }}
            className={cn(
              'flex-shrink-0 w-4 h-4 rounded border-2 flex items-center justify-center transition-colors',
              isSelected
                ? cn(brandAccent === 'rose' ? 'bg-rose-500 border-rose-500' : 'bg-violet-500 border-violet-500')
                : 'border-slate-300 hover:border-slate-400'
            )}
          >
            {isSelected && (
              <svg className="w-3 h-3 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={3} d="M5 13l4 4L19 7" />
              </svg>
            )}
          </button>
        )}
        
        {/* 展开/折叠按钮 */}
        {hasChildren && !batchSelectMode && (
          <button
            onClick={() => setIsExpanded(!isExpanded)}
            className="flex-shrink-0 w-4 h-4 flex items-center justify-center hover:bg-slate-100 rounded"
          >
            <ChevronRight
              className={cn(
                'w-3 h-3 transition-transform',
                isExpanded && 'rotate-90'
              )}
            />
          </button>
        )}
        {!hasChildren && !batchSelectMode && <div className="w-1 h-1 rounded-full bg-slate-300 flex-shrink-0" />}

        <button
          onClick={() => onItemClick(item.id)}
          className="flex items-center gap-2 flex-1 min-w-0"
        >
          <span className="flex-1 text-left truncate">{item.label}</span>
          {item.count !== undefined && item.count > 0 && (
            <span className="text-[11px] text-slate-400">{item.count}</span>
          )}
        </button>

        {/* 删除按钮 */}
        {!batchSelectMode && showActions && onDelete && (
          <button
            onClick={(e) => {
              e.stopPropagation();
              onDelete(item, e);
            }}
            className="flex-shrink-0 w-6 h-6 flex items-center justify-center text-slate-300 hover:text-red-400 hover:bg-red-50 rounded transition-colors"
            title="删除相册"
          >
            <Trash2 className="w-3.5 h-3.5" />
          </button>
        )}
      </div>

      {/* 递归渲染子项 */}
      {hasChildren && isExpanded && !batchSelectMode && (
        <div className="mt-0.5">
          {item.children!.map(child => (
            <SubMenuItem
              key={child.id}
              item={child}
              activeItem={activeItem}
              onItemClick={onItemClick}
              onDelete={onDelete}
              level={level + 1}
              batchSelectMode={batchSelectMode}
              selectedIds={selectedIds}
              onToggleSelect={onToggleSelect}
              selectable={selectable}
              brandAccent={brandAccent}
            />
          ))}
        </div>
      )}
    </div>
  );
}

export interface SmartAlbumInfo extends AlbumInfo {
  type: 'smart';
  matchingConfig: import('@/lib/api/types').MatchingConfig;
  isSystem?: boolean;
}

interface SidebarProps {
  activeItem: string;
  onItemClick: (item: string) => void;
  collapsed?: boolean;
  albums?: AlbumInfo[];
  smartAlbums?: SmartAlbumInfo[];
  allImagesCount?: number;
  myImagesCount?: number;
  recentCount?: number;
  favoritesCount?: number;
  trashCount?: number;
  documentStats?: Record<string, number>;
  isAdmin?: boolean;
  brand?: BrandConfig;
  onAlbumCreated?: () => void;
  onCreateSmartAlbum?: () => void;
  /**
   * 当点击相册时触发（包括有子级的父相册）
   * 传递该相册及其所有子相册的 ID 列表
   */
  onAlbumClick?: (albumId: string, allAlbumIds: string[]) => void;
}

export default function Sidebar({
  activeItem,
  onItemClick,
  collapsed = false,
  albums = [],
  smartAlbums = [],
  allImagesCount = 0,
  myImagesCount = 0,
  recentCount = 0,
  favoritesCount = 0,
  trashCount = 0,
  isAdmin = false,
  documentStats = { all: 0, pdf: 0, word: 0, excel: 0, ppt: 0, zip: 0, other: 0 },
  brand,
  onAlbumCreated,
  onCreateSmartAlbum,
  onAlbumClick,
}: SidebarProps) {
  const router = useRouter();
  const [expandedItems, setExpandedItems] = React.useState<string[]>(['albums', 'smart-albums']);
  const [isCreateDialogOpen, setIsCreateDialogOpen] = React.useState(false);
  const [isResetDialogOpen, setIsResetDialogOpen] = React.useState(false);
  const [isDeleteDialogOpen, setIsDeleteDialogOpen] = React.useState(false);
  const [isEditDialogOpen, setIsEditDialogOpen] = React.useState(false);
  const [albumToDelete, setAlbumToDelete] = React.useState<AlbumInfo | null>(null);
  const [albumToEdit, setAlbumToEdit] = React.useState<{ id: string; name: string; description?: string; matchingConfig?: any } | null>(null);
  const [albumName, setAlbumName] = React.useState('');
  const [albumDescription, setAlbumDescription] = React.useState('');
  const [matchMode, setMatchMode] = React.useState<'contains' | 'exact' | 'startsWith' | 'endsWith' | 'regex' | 'fuzzy'>('contains');
  const [synonyms, setSynonyms] = React.useState(''); // 同义词，多个用逗号分隔
  const [isCreating, setIsCreating] = React.useState(false);
  const [isResetting, setIsResetting] = React.useState(false);
  const [resetTargetMode, setResetTargetMode] = React.useState<'contains' | 'exact' | 'startsWith' | 'endsWith' | 'regex' | 'fuzzy'>('contains');
  const [isDeleting, setIsDeleting] = React.useState(false);
  const [isUpdating, setIsUpdating] = React.useState(false);
  const [isBatchDeleteDialogOpen, setIsBatchDeleteDialogOpen] = React.useState(false);
  
  // 批量选择状态
  const [batchSelectMode, setBatchSelectMode] = React.useState(false);
  const [selectedAlbumIds, setSelectedAlbumIds] = React.useState<Set<string>>(new Set());
  const [isBatchDeleting, setIsBatchDeleting] = React.useState(false);
  
  // 切换批量选择模式
  const toggleBatchSelectMode = () => {
    if (batchSelectMode) {
      // 退出批量选择模式时清空选择
      setSelectedAlbumIds(new Set());
    }
    setBatchSelectMode(!batchSelectMode);
  };
  
  // 切换相册选中状态
  const toggleAlbumSelection = (albumId: string) => {
    setSelectedAlbumIds(prev => {
      const newSet = new Set(prev);
      if (newSet.has(albumId)) {
        newSet.delete(albumId);
      } else {
        newSet.add(albumId);
      }
      return newSet;
    });
  };
  
  // 全选/取消全选
  const toggleSelectAll = () => {
    if (selectedAlbumIds.size === getAllSelectableAlbumIds().length) {
      setSelectedAlbumIds(new Set());
    } else {
      setSelectedAlbumIds(new Set(getAllSelectableAlbumIds()));
    }
  };
  
  // 获取所有可选中的相册ID（排除系统相册）
  const getAllSelectableAlbumIds = (): string[] => {
    const ids: string[] = [];
    const collectIds = (items: AlbumInfo[]) => {
      for (const item of items) {
        if (!item.isSystem) {
          ids.push(item.id);
        }
        if (item.children) {
          collectIds(item.children);
        }
      }
    };
    collectIds(albums);
    return ids;
  };
  
  // 批量删除相册
  const handleBatchDelete = async () => {
    if (selectedAlbumIds.size === 0) {
      toast.error('请先选择要删除的相册');
      return;
    }
    
    setIsBatchDeleting(true);
    try {
      const response = await fetch('/api/albums/batch-delete', {
        method: 'POST',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          ids: Array.from(selectedAlbumIds),
        }),
      });
      
      const text = await response.text();
      
      if (!text) {
        toast.error('服务器返回空响应，请检查后端服务是否运行');
        return;
      }
      
      let result;
      try {
        result = JSON.parse(text);
      } catch (e) {
        toast.error(`响应解析失败: ${text.substring(0, 100)}`);
        return;
      }
      
      if (result.code === 200) {
        const successCount = result.data?.deletedAlbumCount || 0;
        // 关闭对话框
        setIsBatchDeleteDialogOpen(false);
        
        const failCount = result.data?.failCount || 0;
        
        // 显示成功提示（绿色）
        toast(`批量删除完成，成功删除 ${successCount} 个相册`, {
          style: { background: '#22c55e', color: 'white' },
        });
        
        if (failCount > 0) {
          const failedItems = result.data?.failedItems || [];
          const reasons = failedItems.slice(0, 3).map((item: any) => `${item.name || item.id}: ${item.reason}`).join('；');
          toast.error(`有 ${failCount} 个相册删除失败：${reasons}${failCount > 3 ? '...' : ''}`);
        }
        
        // 清空选择并退出批量模式
        setSelectedAlbumIds(new Set());
        setBatchSelectMode(false);
        
        // 刷新相册列表和图片数据
        if (onAlbumCreated) {
          onAlbumCreated();
        }
        // 强制刷新页面数据
        window.location.reload();
      } else {
        toast.error(result.message || '批量删除失败');
      }
    } catch (error) {
      console.error('批量删除失败:', error);
      toast.error('批量删除失败，请重试');
    } finally {
      setIsBatchDeleting(false);
    }
  };

  const toggleExpand = (itemId: string) => {
    setExpandedItems((prev) =>
      prev.includes(itemId) ? prev.filter((id) => id !== itemId) : [...prev, itemId]
    );
  };

  // 收集指定相册及其所有子相册的 ID
  const collectAllAlbumIds = (albumId: string, albumList: AlbumInfo[]): string[] => {
    const result: string[] = [albumId];
    const findAlbum = (albums: AlbumInfo[]): AlbumInfo | undefined => {
      for (const album of albums) {
        if (album.id === albumId) return album;
        if (album.children) {
          const found = findAlbum(album.children);
          if (found) return found;
        }
      }
      return undefined;
    };
    const album = findAlbum(albumList);
    if (album?.children) {
      const collectChildren = (children: AlbumInfo[]) => {
        for (const child of children) {
          result.push(child.id);
          if (child.children) {
            collectChildren(child.children);
          }
        }
      };
      collectChildren(album.children);
    }
    return result;
  };

  // 处理相册点击（包括有子级的父相册）
  const handleAlbumClick = (albumId: string) => {
    if (onAlbumClick) {
      const allIds = collectAllAlbumIds(albumId, albums);
      onAlbumClick(albumId, allIds);
    }
    onItemClick(albumId);
  };

  const handleCreateAlbum = async () => {
    if (!albumName.trim()) {
      toast.error('相册名称不能为空');
      return;
    }

    setIsCreating(true);
    try {
      // 构建匹配配置
      const matchingConfig: { mode: string; caseSensitive?: boolean; synonyms?: { keywords: string[]; targetKeyword: string }[] } = {
        mode: matchMode,
      };
      
      // 如果是 fuzzy 模式且有同义词，添加同义词配置
      if (matchMode === 'fuzzy' && synonyms.trim()) {
        const synonymList = synonyms.split(/[,，、\s]+/).filter(k => k.trim());
        if (synonymList.length > 0) {
          matchingConfig.synonyms = [{
            keywords: synonymList,
            targetKeyword: albumName.trim(),
          }];
        }
      }

      const response = await fetch('/api/albums', {
        method: 'POST',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          name: albumName.trim(),
          description: albumDescription.trim(),
          matchingConfig: JSON.stringify(matchingConfig),
        }),
      });

      const result = await response.json();
      console.log('[Sidebar] 创建相册响应:', response.status, result);

      // API 路由已确保返回 success 字段
      if (result.success) {
        console.log('[Sidebar] 创建相册成功，关闭弹窗');
        toast.success(result.message || '相册创建成功');
        setAlbumName('');
        setAlbumDescription('');
        setMatchMode('contains');
        setSynonyms('');
        setIsCreateDialogOpen(false);
        onAlbumCreated?.();
      } else {
        console.error('[Sidebar] 创建相册失败:', result.message);
        toast.error(result.message || '创建相册失败');
      }
    } catch (error) {
      console.error('创建相册失败:', error);
      toast.error('创建相册失败，请稍后重试');
    } finally {
      setIsCreating(false);
    }
  };

  const handleResetMatchingMode = async () => {
    setIsResetting(true);
    try {
      const response = await fetch('/api/albums/matching-mode/reset', {
        method: 'PUT',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          targetMode: resetTargetMode,
        }),
      });

      const result = await response.json();
      if (result.success) {
        // 从 data 中获取重置数量（data 直接是数字，如 6）
        const count = typeof result.data === 'number' ? result.data : (result.data?.updated || 0);
        toast.success(`成功重置 ${count} 个相册的匹配模式`);
        setIsResetDialogOpen(false);
        onAlbumCreated?.();
      } else {
        toast.error(result.message || '重置失败');
      }
    } catch (error) {
      console.error('重置匹配模式失败:', error);
      toast.error('重置失败，请稍后重试');
    } finally {
      setIsResetting(false);
    }
  };

  const handleDeleteAlbum = async () => {
    if (!albumToDelete) return;

    setIsDeleting(true);
    try {
      const response = await fetch(`/api/albums/${albumToDelete.id}`, {
        method: 'DELETE',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/json',
        },
      });

      const result = await response.json();
      if (result.success) {
        toast.success(`已删除相册 "${albumToDelete.name}"`);
        setIsDeleteDialogOpen(false);
        setAlbumToDelete(null);
        onAlbumCreated?.();
      } else {
        toast.error(result.message || '删除相册失败');
      }
    } catch (error) {
      console.error('删除相册失败:', error);
      toast.error('删除相册失败，请稍后重试');
    } finally {
      setIsDeleting(false);
    }
  };

  const openDeleteDialog = (menuItem: MenuItem, e: React.MouseEvent) => {
    e.stopPropagation();
    // 将 MenuItem 转换为 AlbumInfo 格式
    const album: AlbumInfo = {
      id: menuItem.id,
      name: menuItem.label,
      count: menuItem.count || 0,
      isSystem: menuItem.isSystem,
    };
    setAlbumToDelete(album);
    setIsDeleteDialogOpen(true);
  };

  const openEditDialog = (albumId: string, e: React.MouseEvent) => {
    e.stopPropagation();
    const album = albums.find(a => a.id === albumId);
    if (!album) return;
    
    // 从 albums 数据中获取更多信息（如果有的话）
    // 这里我们只传递基本信息，编辑对话框会从 API 获取完整配置
    setAlbumToEdit({ id: album.id, name: album.name });
    setAlbumName(album.name);
    setAlbumDescription('');
    setMatchMode('contains');
    setSynonyms('');
    setIsEditDialogOpen(true);
  };

  const handleUpdateAlbum = async () => {
    if (!albumToEdit || !albumName.trim()) {
      toast.error('相册名称不能为空');
      return;
    }

    setIsUpdating(true);
    try {
      // 构建匹配配置
      const matchingConfig: { mode: string; caseSensitive?: boolean; synonyms?: { keywords: string[]; targetKeyword: string }[] } = {
        mode: matchMode,
      };
      
      // 如果是 fuzzy 模式且有同义词，添加同义词配置
      if (matchMode === 'fuzzy' && synonyms.trim()) {
        const synonymList = synonyms.split(/[,，、\s]+/).filter(k => k.trim());
        if (synonymList.length > 0) {
          matchingConfig.synonyms = [{
            keywords: synonymList,
            targetKeyword: albumName.trim(),
          }];
        }
      }

      const response = await fetch(`/api/albums/${albumToEdit.id}`, {
        method: 'PUT',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          name: albumName.trim(),
          description: albumDescription.trim(),
          matchingConfig: JSON.stringify(matchingConfig),
        }),
      });

      const result = await response.json();
      console.log('[Sidebar] 更新相册响应:', response.status, result);

      if (result.success) {
        console.log('[Sidebar] 更新相册成功，关闭弹窗');
        toast.success(result.message || '相册更新成功');
        setIsEditDialogOpen(false);
        setAlbumToEdit(null);
        setAlbumName('');
        setAlbumDescription('');
        setMatchMode('contains');
        setSynonyms('');
        onAlbumCreated?.();
      } else {
        console.error('[Sidebar] 更新相册失败:', result.message);
        toast.error(result.message || '更新相册失败');
      }
    } catch (error) {
      console.error('更新相册失败:', error);
      toast.error('更新相册失败，请稍后重试');
    } finally {
      setIsUpdating(false);
    }
  };

  const menuItems: MenuItem[] = [
    {
      id: 'dashboard',
      label: '数据仪表盘',
      icon: LayoutDashboard,
    },
    {
      id: 'all',
      label: '全部知识',
      icon: BookOpen,
      count: allImagesCount,
    },
    // 管理员显示「二创中心」，普通用户显示「我的知识库」
    ...(isAdmin ? [{
      id: 'creative-center',
      label: '二创中心',
      icon: Sparkles,
      count: myImagesCount,
    }] : [{
      id: 'my-images',
      label: '我的知识库',
      icon: Library,
      count: myImagesCount,
    }]),
    {
      id: 'albums',
      label: '知识分类',
      icon: FolderOpen,
      children: buildAlbumHierarchy(albums),
      showAddButton: true,
    },
    // 文档中心 - 替换原智能相册
    {
      id: 'documents',
      label: '文档中心',
      icon: FolderOpen,
      children: [
        { id: 'doc-all', label: '全部文档', icon: FileText, count: documentStats?.all || 0 },
        { id: 'doc-pdf', label: 'PDF文档', icon: FileText, count: documentStats?.pdf || 0 },
        { id: 'doc-word', label: 'Word文档', icon: FileText, count: documentStats?.word || 0 },
        { id: 'doc-excel', label: 'Excel表格', icon: FileSpreadsheet, count: documentStats?.excel || 0 },
        { id: 'doc-ppt', label: 'PPT演示', icon: Presentation, count: documentStats?.ppt || 0 },
        { id: 'doc-zip', label: '压缩包', icon: Archive, count: documentStats?.zip || 0 },
        { id: 'doc-other', label: '其他文件', icon: File, count: documentStats?.other || 0 },
      ],
    },
    {
      id: 'knowledge',
      label: '知识库',
      icon: BookOpen,
    },
    {
      id: 'chat',
      label: 'AI 对话',
      icon: MessageSquare,
    },
    {
      id: 'recent',
      label: '最近添加',
      icon: Clock,
      count: recentCount,
    },
    {
      id: 'favorites',
      label: '收藏夹',
      icon: Heart,
      count: favoritesCount,
    },
  ];

  const bottomItems: MenuItem[] = [
    {
      id: 'upload',
      label: '上传知识',
      icon: Upload,
    },
    {
      id: 'trash',
      label: '回收站',
      icon: Trash2,
      count: trashCount,
    },
    // 以下菜单仅管理员可见
    ...(isAdmin ? [
      {
        id: 'user-settings',
        label: '用户管理',
        icon: UserCog,
      },
      {
        id: 'api-docs',
        label: 'API文档',
        icon: BookType,
      },
      {
        id: 'settings',
        label: '系统设置',
        icon: Settings,
      },
    ] : []),
  ];

  // 品牌色辅助
  const brandAccent = brand?.key === 'bonasi' ? 'rose' : 'violet';
  const accentFrom = brand?.primaryFrom || 'from-violet-500';
  const accentTo = brand?.primaryTo || 'to-purple-600';

  return (
    <aside
      className={cn(
        'h-screen flex flex-col transition-all duration-300 ease-in-out',
        'bg-white border-r border-slate-200/80',
        collapsed ? 'w-[68px]' : 'w-[260px]'
      )}
    >
      {/* Logo区域 */}
      <div className="h-[60px] flex items-center px-4 border-b border-slate-100">
        <div className="flex items-center gap-3 min-w-0">
          <div className={cn(
            'w-9 h-9 rounded-lg flex items-center justify-center flex-shrink-0',
            'bg-gradient-to-br', accentFrom, accentTo
          )}>
            {brand?.key === 'bonasi' ? <Scissors className="w-4.5 h-4.5 text-white" /> : <BookOpen className="w-4.5 h-4.5 text-white" />}
          </div>
          {!collapsed && (
            <div className="flex flex-col min-w-0">
              <span className="text-[15px] font-semibold text-slate-800 truncate">
                {brand?.name || '盈云'}
              </span>
              <span className="text-[11px] text-slate-400 truncate">企业数智中台系统</span>
            </div>
          )}
        </div>
      </div>

      {/* 主菜单区域 */}
      <div className="flex-1 overflow-y-auto py-3 px-2.5 scrollbar-thin scrollbar-thumb-slate-200">
        <div className="space-y-0.5">
          {menuItems.map((item) => {
            const Icon = item.icon;
            const isActive = activeItem === item.id;
            const isExpanded = expandedItems.includes(item.id);

            return (
              <div key={item.id}>
                {item.showAddButton ? (
                  // 对于有新增按钮的菜单项，使用 div 容器 + 两个按钮
                  <div
                    className={cn(
                      'flex items-center gap-3 px-3 py-2 rounded-lg transition-all duration-150 group relative',
                      isActive
                        ? cn(brandAccent === 'rose' ? 'bg-rose-50 text-rose-700' : 'bg-violet-50 text-violet-700')
                        : 'text-slate-600 hover:bg-slate-50 hover:text-slate-800'
                    )}
                  >
                    {isActive && (
                      <div className={cn('absolute left-0 top-1/2 -translate-y-1/2 w-[3px] h-5 bg-gradient-to-b rounded-r-full', accentFrom, accentTo)} />
                    )}
                    <button
                      onClick={() => {
                        if (item.children) {
                          handleAlbumClick(item.id);
                          toggleExpand(item.id);
                        } else {
                          onItemClick(item.id);
                        }
                      }}
                      className="flex items-center gap-3 flex-1 min-w-0"
                      aria-label={item.label}
                    >
                      <Icon
                        className={cn(
                          'w-[18px] h-[18px] flex-shrink-0',
                          isActive ? cn(brandAccent === 'rose' ? 'text-rose-500' : 'text-violet-500') : 'text-slate-400'
                        )}
                      />
                      {!collapsed && (
                        <>
                          <span className="flex-1 text-[13px] font-medium text-left truncate">{item.label}</span>
                          {item.count !== undefined && item.count > 0 && (
                            <span
                              className={cn(
                                'px-1.5 py-0.5 text-[11px] rounded-md font-medium flex-shrink-0 tabular-nums',
                                isActive
                                  ? cn(brandAccent === 'rose' ? 'bg-rose-100 text-rose-600' : 'bg-violet-100 text-violet-600')
                                  : 'bg-slate-100 text-slate-500'
                              )}
                            >
                              {item.count}
                            </span>
                          )}
                        </>
                      )}
                    </button>
                    {!collapsed && item.showAddButton && (
                      <div className="flex items-center gap-1 flex-shrink-0">
                        {/* 批量选择按钮 */}
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            toggleBatchSelectMode();
                          }}
                          className={cn(
                            'p-1 rounded transition-colors flex-shrink-0',
                            batchSelectMode
                              ? cn(brandAccent === 'rose' ? 'bg-rose-100 text-rose-600' : 'bg-violet-100 text-violet-600')
                              : 'hover:bg-slate-100 text-slate-400 hover:text-slate-600'
                          )}
                          aria-label="批量选择"
                          title="批量选择"
                        >
                          {batchSelectMode ? (
                            <CheckSquare className="w-3.5 h-3.5" />
                          ) : (
                            <Square className="w-3.5 h-3.5" />
                          )}
                        </button>
                        {/* 新建按钮 */}
                        <Dialog open={isCreateDialogOpen} onOpenChange={setIsCreateDialogOpen}>
                        <DialogTrigger asChild>
                          <button
                            onClick={(e) => {
                              e.stopPropagation();
                              if (item.id === 'smart-albums' && onCreateSmartAlbum) {
                                onCreateSmartAlbum();
                              }
                            }}
                            className={cn(
                              'p-1 rounded transition-colors flex-shrink-0',
                              brandAccent === 'rose' ? 'hover:bg-rose-50 text-rose-400 hover:text-rose-500' : 'hover:bg-violet-50 text-violet-400 hover:text-violet-500'
                            )}
                            aria-label={item.id === 'smart-albums' ? '新建智能相册' : '新建分类'}
                          >
                            <Plus className="w-3.5 h-3.5" />
                          </button>
                        </DialogTrigger>
                        <DialogContent>
                          <DialogHeader>
                            <DialogTitle>新建知识分类</DialogTitle>
                            <DialogDescription>
                              创建一个新的分类来组织您的知识，支持多种匹配模式自动归类
                            </DialogDescription>
                          </DialogHeader>
                          <div className="space-y-4 py-4">
                            <div className="space-y-2">
                              <label htmlFor="album-name" className="text-sm font-medium">
                                相册名称 <span className="text-red-500">*</span>
                              </label>
                              <Input
                                id="album-name"
                                placeholder="请输入相册名称"
                                value={albumName}
                                onChange={(e) => setAlbumName(e.target.value)}
                                maxLength={100}
                              />
                            </div>
                            <div className="space-y-2">
                              <label htmlFor="match-mode" className="text-sm font-medium">
                                匹配模式
                              </label>
                              <select
                                id="match-mode"
                                className="w-full px-3 py-2 border rounded-lg bg-white text-sm"
                                value={matchMode}
                                onChange={(e) => setMatchMode(e.target.value as typeof matchMode)}
                              >
                                <option value="contains">包含匹配 - 文件名包含相册名称即可匹配</option>
                                <option value="exact">精确匹配 - 文件名必须与相册名称完全一致</option>
                                <option value="startsWith">开头匹配 - 文件名以相册名称开头</option>
                                <option value="endsWith">结尾匹配 - 文件名以相册名称结尾</option>
                                <option value="regex">正则匹配 - 支持正则表达式</option>
                                <option value="fuzzy">模糊匹配 - 支持同义词匹配</option>
                              </select>
                            </div>
                            {matchMode === 'fuzzy' && (
                              <div className="space-y-2">
                                <label htmlFor="synonyms" className="text-sm font-medium">
                                  同义词 <span className="text-slate-400 font-normal">(可选)</span>
                                </label>
                                <Input
                                  id="synonyms"
                                  placeholder="请输入同义词，多个用逗号分隔，如：tshirt,T-shirt"
                                  value={synonyms}
                                  onChange={(e) => setSynonyms(e.target.value)}
                                  maxLength={500}
                                />
                                <p className="text-xs text-slate-400">
                                  配置同义词后，如设置"T恤"同义词为"tshirt,T-shirt"，则包含这些词的文件名也会匹配
                                </p>
                              </div>
                            )}
                            {matchMode === 'regex' && (
                              <div className="p-3 bg-amber-50 rounded-lg text-xs text-amber-700">
                                <p><strong>正则示例：</strong></p>
                                <p>• <code>.*T恤.*</code> - 包含"T恤"的任意文件名</p>
                                <p>• <code>^T恤.*</code> - 以"T恤"开头的文件名</p>
                                <p>• <code>.*T恤$</code> - 以"T恤"结尾的文件名</p>
                              </div>
                            )}
                            <div className="space-y-2">
                              <label htmlFor="album-description" className="text-sm font-medium">
                                相册描述
                              </label>
                              <Textarea
                                id="album-description"
                                placeholder="请输入相册描述（可选）"
                                value={albumDescription}
                                onChange={(e) => setAlbumDescription(e.target.value)}
                                rows={3}
                                maxLength={500}
                              />
                            </div>
                          </div>
                          <DialogFooter>
                            <Button
                              variant="outline"
                              onClick={() => {
                                setAlbumName('');
                                setAlbumDescription('');
                                setMatchMode('contains');
                                setSynonyms('');
                                setIsCreateDialogOpen(false);
                              }}
                            >
                              取消
                            </Button>
                            <Button
                              onClick={handleCreateAlbum}
                              disabled={!albumName.trim() || isCreating}
                            >
                              {isCreating ? '创建中...' : '创建'}
                            </Button>
                          </DialogFooter>
                        </DialogContent>
                      </Dialog>
                      </div>
                    )}
                    {/* 批量重置按钮 */}
                    {!collapsed && item.showAddButton && (
                      <Dialog open={isResetDialogOpen} onOpenChange={setIsResetDialogOpen}>
                        <DialogTrigger asChild>
                          <button
                            onClick={(e) => {
                              e.stopPropagation();
                            }}
                            className={cn(
                              'p-1 rounded transition-colors flex-shrink-0',
                              brandAccent === 'rose' ? 'hover:bg-rose-50 text-rose-400 hover:text-rose-500' : 'hover:bg-violet-50 text-violet-400 hover:text-violet-500'
                            )}
                            aria-label="批量重置匹配模式"
                          >
                            <RotateCcw className="w-3.5 h-3.5" />
                          </button>
                        </DialogTrigger>
                        <DialogContent>
                          <DialogHeader>
                            <DialogTitle>批量重置匹配模式</DialogTitle>
                            <DialogDescription>
                              将所有相册的匹配模式统一重置为指定的模式，这不会影响相册名称和描述
                            </DialogDescription>
                          </DialogHeader>
                          <div className="space-y-4 py-4">
                            <div className="space-y-2">
                              <label htmlFor="reset-target-mode" className="text-sm font-medium">
                                目标匹配模式
                              </label>
                              <select
                                id="reset-target-mode"
                                className="w-full px-3 py-2 border rounded-lg bg-white text-sm"
                                value={resetTargetMode}
                                onChange={(e) => setResetTargetMode(e.target.value as typeof resetTargetMode)}
                              >
                                <option value="contains">包含匹配 - 文件名包含相册名称即可匹配</option>
                                <option value="exact">精确匹配 - 文件名必须与相册名称完全一致</option>
                                <option value="startsWith">开头匹配 - 文件名以相册名称开头</option>
                                <option value="endsWith">结尾匹配 - 文件名以相册名称结尾</option>
                                <option value="regex">正则匹配 - 支持正则表达式</option>
                                <option value="fuzzy">模糊匹配 - 支持同义词匹配</option>
                              </select>
                            </div>
                            <div className="p-3 bg-amber-50 border border-amber-200 rounded-lg">
                              <p className="text-sm text-amber-800">
                                提示：此操作将影响所有 {albums.length} 个相册的匹配规则，请谨慎操作
                              </p>
                            </div>
                          </div>
                          <DialogFooter>
                            <Button
                              variant="outline"
                              onClick={() => setIsResetDialogOpen(false)}
                            >
                              取消
                            </Button>
                            <Button
                              onClick={handleResetMatchingMode}
                              disabled={isResetting}
                            >
                              {isResetting ? '重置中...' : '确认重置'}
                            </Button>
                          </DialogFooter>
                        </DialogContent>
                      </Dialog>
                    )}
                  </div>
                ) : (
                  // 对于没有新增按钮的菜单项，使用普通的 button
                  <button
                    onClick={() => {
                      if (item.children) {
                        toggleExpand(item.id);
                      } else {
                        onItemClick(item.id);
                      }
                    }}
                    className={cn(
                      'w-full flex items-center gap-3 px-3 py-2 rounded-lg transition-all duration-150 group relative',
                      isActive
                        ? cn(brandAccent === 'rose' ? 'bg-rose-50 text-rose-700' : 'bg-violet-50 text-violet-700')
                        : 'text-slate-600 hover:bg-slate-50 hover:text-slate-800'
                    )}
                  >
                    {isActive && (
                      <div className={cn('absolute left-0 top-1/2 -translate-y-1/2 w-[3px] h-5 bg-gradient-to-b rounded-r-full', accentFrom, accentTo)} />
                    )}
                    <Icon
                      className={cn(
                        'w-[18px] h-[18px] flex-shrink-0',
                        isActive ? cn(brandAccent === 'rose' ? 'text-rose-500' : 'text-violet-500') : 'text-slate-400'
                      )}
                    />
                    {!collapsed && (
                      <>
                        <span className="flex-1 text-[13px] font-medium text-left">{item.label}</span>
                        {item.count !== undefined && item.count > 0 && (
                          <span
                            className={cn(
                              'px-1.5 py-0.5 text-[11px] rounded-md font-medium tabular-nums',
                              isActive
                                ? cn(brandAccent === 'rose' ? 'bg-rose-100 text-rose-600' : 'bg-violet-100 text-violet-600')
                                : 'bg-slate-100 text-slate-500'
                            )}
                          >
                            {item.count}
                          </span>
                        )}
                      </>
                    )}
                  </button>
                )}

                {/* 子菜单 - 使用递归组件支持层级 */}
                {item.children && isExpanded && !collapsed && (
                  <div className="mt-1 space-y-0.5">
                    {item.children.map((child) => (
                      <SubMenuItem
                        key={child.id}
                        item={child}
                        activeItem={activeItem}
                        onItemClick={batchSelectMode ? () => toggleAlbumSelection(child.id) : onItemClick}
                        onDelete={openDeleteDialog}
                        level={1}
                        batchSelectMode={batchSelectMode}
                        selectedIds={selectedAlbumIds}
                        onToggleSelect={toggleAlbumSelection}
                        selectable={!child.isSystem}
                        brandAccent={brandAccent}
                      />
                    ))}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      </div>

      {/* 批量删除工具栏 */}
      {batchSelectMode && (
        <div className="border-t border-slate-100 bg-slate-50/50 py-3 px-3 space-y-2">
          <div className="flex items-center justify-between">
            <span className="text-[13px] text-slate-700 font-medium">
              已选择 {selectedAlbumIds.size} 个相册
            </span>
            <button
              onClick={toggleSelectAll}
              className={cn(
                'text-[12px] hover:underline',
                brandAccent === 'rose' ? 'text-rose-400' : 'text-violet-400'
              )}
            >
              {selectedAlbumIds.size === getAllSelectableAlbumIds().length ? '取消全选' : '全选'}
            </button>
          </div>
          <div className="flex gap-2">
            <button
              onClick={() => setBatchSelectMode(false)}
              className="flex-1 px-3 py-1.5 text-[13px] text-slate-600 bg-slate-100 border border-slate-200 rounded-lg hover:bg-slate-200 transition-colors"
            >
              取消
            </button>
            <button
              onClick={() => setIsBatchDeleteDialogOpen(true)}
              disabled={selectedAlbumIds.size === 0}
              className={cn(
                'flex-1 px-3 py-1.5 text-[13px] rounded-lg transition-colors flex items-center justify-center gap-1.5',
                selectedAlbumIds.size > 0
                  ? 'bg-red-500 text-white hover:bg-red-600'
                  : 'bg-slate-100 text-slate-300 cursor-not-allowed'
              )}
            >
              <Trash className="w-4 h-4" />
              批量删除
            </button>
          </div>
        </div>
      )}

      {/* 底部菜单区域 */}
      <div className="border-t border-slate-100 py-3 px-2.5 space-y-0.5">
        {bottomItems.map((item) => {
          const Icon = item.icon;
          const isActive = activeItem === item.id;

          return (
            <button
              key={item.id}
              onClick={() => {
                if (item.id === 'api-docs') {
                  router.push('/api-docs');
                } else {
                  onItemClick(item.id);
                }
              }}
              className={cn(
                'w-full flex items-center gap-3 px-3 py-2 rounded-lg transition-all duration-150 group',
                isActive
                  ? cn(brandAccent === 'rose' ? 'bg-rose-50 text-rose-700' : 'bg-violet-50 text-violet-700')
                  : item.id === 'upload'
                  ? cn('bg-gradient-to-r text-white hover:shadow-lg', accentFrom, accentTo, brand?.buttonShadow || 'shadow-purple-500/25')
                  : 'text-slate-600 hover:bg-slate-50 hover:text-slate-800'
              )}
            >
              <Icon
                className={cn(
                  'w-[18px] h-[18px] flex-shrink-0',
                  isActive ? cn(brandAccent === 'rose' ? 'text-rose-500' : 'text-violet-500') : item.id === 'upload' ? 'text-white' : 'text-slate-400'
                )}
              />
              {!collapsed && (
                <>
                  <span className="flex-1 text-[13px] font-medium text-left">{item.label}</span>
                  {item.count !== undefined && item.count > 0 && (
                    <span
                      className={cn(
                        'px-1.5 py-0.5 text-[11px] rounded-md font-medium tabular-nums',
                        isActive
                          ? cn(brandAccent === 'rose' ? 'bg-rose-100 text-rose-600' : 'bg-violet-100 text-violet-600')
                          : item.id === 'upload'
                          ? 'bg-white/30 text-white'
                          : 'bg-slate-100 text-slate-500'
                      )}
                    >
                      {item.count}
                    </span>
                  )}
                </>
              )}
            </button>
          );
        })}
      </div>

      {/* 删除相册确认对话框 */}
      <Dialog open={isDeleteDialogOpen} onOpenChange={setIsDeleteDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>删除相册</DialogTitle>
            <DialogDescription>
              确定要删除相册 "{albumToDelete?.name}" 吗？
              {albumToDelete && albumToDelete.count > 0 && (
                <span className="block mt-2 text-amber-600">
                  此相册下还有 {albumToDelete.count} 张图片，请先删除图片后再删除相册。
                </span>
              )}
              {albumToDelete && albumToDelete.count === 0 && (
                <span className="block mt-2 text-slate-500">
                  此操作不可恢复。
                </span>
              )}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => {
                setIsDeleteDialogOpen(false);
                setAlbumToDelete(null);
              }}
            >
              取消
            </Button>
            <Button
              variant="destructive"
              onClick={handleDeleteAlbum}
              disabled={isDeleting || (albumToDelete !== null && albumToDelete.count > 0)}
            >
              {isDeleting ? '删除中...' : '删除'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* 批量删除相册确认对话框 */}
      <Dialog open={isBatchDeleteDialogOpen} onOpenChange={setIsBatchDeleteDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>批量删除相册</DialogTitle>
            <DialogDescription className="text-left">
              你确定要删除选中的相册以及下面的子相册和图片吗？
              <span className="block mt-2 text-slate-500">
                此操作将删除所有选中的相册、其子相册以及相册中的图片，且不可恢复。
              </span>
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => {
                setIsBatchDeleteDialogOpen(false);
              }}
            >
              取消
            </Button>
            <Button
              variant="destructive"
              onClick={handleBatchDelete}
              disabled={isBatchDeleting}
            >
              {isBatchDeleting ? '删除中...' : `删除 ${selectedAlbumIds.size} 个相册`}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* 编辑相册配置对话框 */}
      <Dialog open={isEditDialogOpen} onOpenChange={setIsEditDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>编辑相册配置</DialogTitle>
            <DialogDescription>
              修改相册 "{albumToEdit?.name}" 的匹配规则和描述
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-4">
            <div className="space-y-2">
              <label htmlFor="edit-album-name" className="text-sm font-medium">
                相册名称 <span className="text-red-500">*</span>
              </label>
              <Input
                id="edit-album-name"
                placeholder="请输入相册名称"
                value={albumName}
                onChange={(e) => setAlbumName(e.target.value)}
                maxLength={100}
              />
            </div>
            <div className="space-y-2">
              <label htmlFor="edit-match-mode" className="text-sm font-medium">
                匹配模式
              </label>
              <select
                id="edit-match-mode"
                className="w-full px-3 py-2 border rounded-lg bg-white text-sm"
                value={matchMode}
                onChange={(e) => setMatchMode(e.target.value as typeof matchMode)}
              >
                <option value="contains">包含匹配 - 文件名包含相册名称即可匹配</option>
                <option value="exact">精确匹配 - 文件名必须与相册名称完全一致</option>
                <option value="startsWith">开头匹配 - 文件名以相册名称开头</option>
                <option value="endsWith">结尾匹配 - 文件名以相册名称结尾</option>
                <option value="regex">正则匹配 - 支持正则表达式</option>
                <option value="fuzzy">模糊匹配 - 支持同义词匹配</option>
              </select>
            </div>
            {matchMode === 'fuzzy' && (
              <div className="space-y-2">
                <label htmlFor="edit-synonyms" className="text-sm font-medium">
                  同义词 <span className="text-slate-400 font-normal">(可选)</span>
                </label>
                <Input
                  id="edit-synonyms"
                  placeholder="请输入同义词，多个用逗号分隔，如：tshirt,T-shirt"
                  value={synonyms}
                  onChange={(e) => setSynonyms(e.target.value)}
                  maxLength={500}
                />
                <p className="text-xs text-slate-400">
                  配置同义词后，如设置"T恤"同义词为"tshirt,T-shirt"，则包含这些词的文件名也会匹配
                </p>
              </div>
            )}
            {matchMode === 'regex' && (
              <div className="p-3 bg-amber-50 rounded-lg text-xs text-amber-700">
                <p><strong>正则示例：</strong></p>
                <p>• <code>.*T恤.*</code> - 包含"T恤"的任意文件名</p>
                <p>• <code>^T恤.*</code> - 以"T恤"开头的文件名</p>
                <p>• <code>.*T恤$</code> - 以"T恤"结尾的文件名</p>
              </div>
            )}
            <div className="space-y-2">
              <label htmlFor="edit-album-description" className="text-sm font-medium">
                相册描述
              </label>
              <Textarea
                id="edit-album-description"
                placeholder="请输入相册描述（可选）"
                value={albumDescription}
                onChange={(e) => setAlbumDescription(e.target.value)}
                rows={3}
                maxLength={500}
              />
            </div>
          </div>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => {
                setIsEditDialogOpen(false);
                setAlbumToEdit(null);
                setAlbumName('');
                setAlbumDescription('');
                setMatchMode('contains');
                setSynonyms('');
              }}
            >
              取消
            </Button>
            <Button
              onClick={handleUpdateAlbum}
              disabled={!albumName.trim() || isUpdating}
            >
              {isUpdating ? '保存中...' : '保存'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </aside>
  );
}
