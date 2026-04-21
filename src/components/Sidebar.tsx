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
} from 'lucide-react';
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
  count: number;
}

interface MenuItem {
  id: string;
  label: string;
  icon: any;
  count?: number;
  children?: Array<{
    id: string;
    label: string;
    count: number;
  }>;
  showAddButton?: boolean;
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
  recentCount?: number;
  favoritesCount?: number;
  trashCount?: number;
  documentStats?: Record<string, number>;
  onAlbumCreated?: () => void;
  onCreateSmartAlbum?: () => void;
}

export default function Sidebar({
  activeItem,
  onItemClick,
  collapsed = false,
  albums = [],
  smartAlbums = [],
  allImagesCount = 0,
  recentCount = 0,
  favoritesCount = 0,
  trashCount = 0,
  documentStats = { all: 0, pdf: 0, word: 0, excel: 0, ppt: 0, zip: 0, other: 0 },
  onAlbumCreated,
  onCreateSmartAlbum,
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

  const toggleExpand = (itemId: string) => {
    setExpandedItems((prev) =>
      prev.includes(itemId) ? prev.filter((id) => id !== itemId) : [...prev, itemId]
    );
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

  const openDeleteDialog = (album: AlbumInfo, e: React.MouseEvent) => {
    e.stopPropagation();
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
    {
      id: 'albums',
      label: '知识分类',
      icon: FolderOpen,
      children: albums.map(album => ({
        id: album.id,
        label: album.name,
        count: album.count,
      })),
      showAddButton: true,
    },
    // 文档中心 - 替换原智能相册
    {
      id: 'documents',
      label: '文档中心',
      icon: FolderOpen,
      children: [
        { id: 'doc-all', label: '全部文档', count: documentStats?.all || 0 },
        { id: 'doc-pdf', label: 'PDF文档', count: documentStats?.pdf || 0 },
        { id: 'doc-word', label: 'Word文档', count: documentStats?.word || 0 },
        { id: 'doc-excel', label: 'Excel表格', count: documentStats?.excel || 0 },
        { id: 'doc-ppt', label: 'PPT演示', count: documentStats?.ppt || 0 },
        { id: 'doc-zip', label: '压缩包', count: documentStats?.zip || 0 },
        { id: 'doc-other', label: '其他文件', count: documentStats?.other || 0 },
      ],
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
  ];

  return (
    <aside
      className={cn(
        'h-screen bg-gradient-to-b from-slate-50/95 to-slate-100/90 backdrop-blur-xl border-r border-slate-200/60 flex flex-col transition-all duration-300 ease-in-out',
        collapsed ? 'w-20' : 'w-72'
      )}
    >
      {/* Logo区域 */}
      <div className="h-16 flex items-center justify-center border-b border-slate-200/60 px-4">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-emerald-500 to-teal-600 flex items-center justify-center shadow-lg shadow-emerald-500/25">
            <BookOpen className="w-5 h-5 text-white" />
          </div>
          {!collapsed && (
            <div className="flex flex-col">
              <span className="text-lg font-bold bg-gradient-to-r from-slate-800 to-slate-600 bg-clip-text text-transparent">
                数字知识库
              </span>
              <span className="text-xs text-slate-400">Digital Knowledge Base</span>
            </div>
          )}
        </div>
      </div>

      {/* 主菜单区域 */}
      <div className="flex-1 overflow-y-auto py-4 px-3">
        <div className="space-y-1">
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
                      'flex items-center gap-3 px-3 py-2.5 rounded-xl transition-all duration-200 group relative',
                      isActive
                        ? 'bg-gradient-to-r from-violet-500/10 to-purple-500/10 text-violet-700 shadow-sm'
                        : 'text-slate-600 hover:bg-slate-200/50 hover:text-slate-800'
                    )}
                  >
                    {isActive && (
                      <div className="absolute left-0 top-1/2 -translate-y-1/2 w-1 h-6 bg-gradient-to-b from-violet-500 to-purple-500 rounded-r-full" />
                    )}
                    <button
                      onClick={() => {
                        if (item.children) {
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
                          'w-5 h-5 flex-shrink-0 transition-transform group-hover:scale-110',
                          isActive ? 'text-violet-600' : 'text-slate-400'
                        )}
                      />
                      {!collapsed && (
                        <>
                          <span className="flex-1 text-sm font-medium text-left truncate">{item.label}</span>
                          {item.count !== undefined && item.count > 0 && (
                            <span
                              className={cn(
                                'px-2 py-0.5 text-xs rounded-full font-medium flex-shrink-0',
                                isActive
                                  ? 'bg-violet-100 text-violet-600'
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
                      <Dialog open={isCreateDialogOpen} onOpenChange={setIsCreateDialogOpen}>
                        <DialogTrigger asChild>
                          <button
                            onClick={(e) => {
                              e.stopPropagation();
                              // 如果是智能相册菜单，调用外部回调
                              if (item.id === 'smart-albums' && onCreateSmartAlbum) {
                                onCreateSmartAlbum();
                              }
                            }}
                            className="p-1 hover:bg-violet-100 rounded-lg transition-colors text-violet-600 hover:text-violet-700 flex-shrink-0"
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
                    )}
                    {/* 批量重置按钮 */}
                    {!collapsed && item.showAddButton && (
                      <Dialog open={isResetDialogOpen} onOpenChange={setIsResetDialogOpen}>
                        <DialogTrigger asChild>
                          <button
                            onClick={(e) => {
                              e.stopPropagation();
                            }}
                            className="p-1 hover:bg-violet-100 rounded-lg transition-colors text-violet-600 hover:text-violet-700 flex-shrink-0"
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
                      'w-full flex items-center gap-3 px-3 py-2.5 rounded-xl transition-all duration-200 group relative',
                      isActive
                        ? 'bg-gradient-to-r from-violet-500/10 to-purple-500/10 text-violet-700 shadow-sm'
                        : 'text-slate-600 hover:bg-slate-200/50 hover:text-slate-800'
                    )}
                  >
                    {isActive && (
                      <div className="absolute left-0 top-1/2 -translate-y-1/2 w-1 h-6 bg-gradient-to-b from-violet-500 to-purple-500 rounded-r-full" />
                    )}
                    <Icon
                      className={cn(
                        'w-5 h-5 flex-shrink-0 transition-transform group-hover:scale-110',
                        isActive ? 'text-violet-600' : 'text-slate-400'
                      )}
                    />
                    {!collapsed && (
                      <>
                        <span className="flex-1 text-sm font-medium text-left">{item.label}</span>
                        {item.count !== undefined && item.count > 0 && (
                          <span
                            className={cn(
                              'px-2 py-0.5 text-xs rounded-full font-medium',
                              isActive
                                ? 'bg-violet-100 text-violet-600'
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

                {/* 子菜单 */}
                {item.children && isExpanded && !collapsed && (
                  <div className="ml-8 mt-1 space-y-1">
                    {item.children.map((child) => (
                      <div
                        key={child.id}
                        className={cn(
                          'group flex items-center gap-2 px-3 py-2 rounded-lg text-sm transition-all duration-200',
                          activeItem === child.id
                            ? 'bg-violet-50 text-violet-700 font-medium'
                            : 'text-slate-500 hover:bg-slate-100 hover:text-slate-700'
                        )}
                      >
                        <button
                          onClick={() => onItemClick(child.id)}
                          className="flex items-center gap-2 flex-1 min-w-0"
                        >
                          <div className="w-1.5 h-1.5 rounded-full bg-slate-300" />
                          <span className="flex-1 text-left truncate">{child.label}</span>
                          {child.count !== undefined && child.count > 0 && (
                            <span className="text-xs text-slate-400">{child.count}</span>
                          )}
                        </button>
                        <button
                          onClick={(e) => {
                            const album = albums.find(a => a.id === child.id);
                            if (album) openDeleteDialog(album, e);
                          }}
                          className="p-1 rounded hover:bg-red-100 transition-colors opacity-0 group-hover:opacity-100"
                          aria-label="删除相册"
                        >
                          <Trash2 className="w-3.5 h-3.5 text-red-500" />
                        </button>
                        <button
                          onClick={(e) => openEditDialog(child.id, e)}
                          className="p-1 rounded hover:bg-violet-100 transition-colors opacity-0 group-hover:opacity-100"
                          aria-label="编辑相册配置"
                        >
                          <Settings2 className="w-3.5 h-3.5 text-violet-600" />
                        </button>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      </div>

      {/* 底部菜单区域 */}
      <div className="border-t border-slate-200/60 py-4 px-3 space-y-1">
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
                'w-full flex items-center gap-3 px-3 py-2.5 rounded-xl transition-all duration-200 group',
                isActive
                  ? 'bg-gradient-to-r from-violet-500/10 to-purple-500/10 text-violet-700 shadow-sm'
                  : item.id === 'upload'
                  ? 'bg-gradient-to-r from-violet-500 to-purple-600 text-white hover:shadow-lg hover:shadow-purple-500/25'
                  : 'text-slate-600 hover:bg-slate-200/50 hover:text-slate-800'
              )}
            >
              <Icon
                className={cn(
                  'w-5 h-5 flex-shrink-0 transition-transform group-hover:scale-110',
                  isActive ? 'text-violet-600' : item.id === 'upload' ? 'text-white' : 'text-slate-400'
                )}
              />
              {!collapsed && (
                <>
                  <span className="flex-1 text-sm font-medium text-left">{item.label}</span>
                  {item.count !== undefined && item.count > 0 && (
                    <span
                      className={cn(
                        'px-2 py-0.5 text-xs rounded-full font-medium',
                        isActive
                          ? 'bg-violet-100 text-violet-600'
                          : item.id === 'upload'
                          ? 'bg-white/20 text-white'
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
