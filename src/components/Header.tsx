'use client';

import React from 'react';
import { useRouter } from 'next/navigation';
import { cn } from '@/lib/utils';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { useNotifications } from '@/contexts/NotificationContext';
import {
  Search,
  Bell,
  Moon,
  Sun,
  Grid3x3,
  LayoutGrid,
  List,
  SlidersHorizontal,
  CheckSquare,
  User,
  ChevronDown,
  LogOut,
  Settings,
  UserCog,
  Shield,
  X,
  FileSpreadsheet,
  Upload,
  Download,
  Filter,
  RefreshCw,
  ImagePlus,
  Sparkles,
} from 'lucide-react';

export interface CurrentUser {
  id: string;
  username: string;
  email: string;
  role: 'admin' | 'user';
}

interface HeaderProps {
  searchQuery: string;
  onSearchChange: (query: string) => void;
  onSearchSubmit?: () => void;
  viewMode: 'grid' | 'masonry' | 'list';
  onViewModeChange: (mode: 'grid' | 'masonry' | 'list') => void;
  selectedCount: number;
  onBulkAction: () => void;
  currentUser?: CurrentUser | null;
  onLogout?: () => void;
  onFilterClick?: () => void;
  onExcelUploadClick?: () => void;
  onExportClick?: () => void;
  hasAlbums?: boolean;

  showSearch?: boolean; // 是否显示搜索栏
  onBatchReplaceMainImage?: () => void; // 批量替换主图
}

export default function Header({
  searchQuery,
  onSearchChange,
  onSearchSubmit,
  viewMode,
  onViewModeChange,
  selectedCount,
  onBulkAction,
  currentUser,
  onLogout,
  onFilterClick,
  onExcelUploadClick,
  onExportClick,
  hasAlbums = false,
  showSearch = true, // 默认显示搜索
  onBatchReplaceMainImage,
}: HeaderProps) {
  const router = useRouter();
  const [showNotifications, setShowNotifications] = React.useState(false);
  const [isDark, setIsDark] = React.useState(false);
  const [showUserMenu, setShowUserMenu] = React.useState(false);
  
  // 使用通知上下文
  const { 
    notifications, 
    unreadCount, 
    markAsRead, 
    markAllAsRead,
    clearNewFlag 
  } = useNotifications();

  // 格式化时间
  const formatTime = (dateString: string) => {
    const date = new Date(dateString);
    const now = new Date();
    const diff = now.getTime() - date.getTime();
    const minutes = Math.floor(diff / (1000 * 60));
    const hours = Math.floor(diff / (1000 * 60 * 60));
    const days = Math.floor(diff / (1000 * 60 * 60 * 24));
    
    if (minutes < 1) return '刚刚';
    if (minutes < 60) return `${minutes}分钟前`;
    if (hours < 24) return `${hours}小时前`;
    if (days < 7) return `${days}天前`;
    return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' });
  };

  // 通知图标
  const getNotificationIcon = (type: string) => {
    const icons: Record<string, string> = {
      system: '📢',
      upload: '📤',
      album: '📁',
      share: '🔗',
      comment: '💬',
      like: '❤️',
      warning: '⚠️',
      document: '📄',
      delete: '🗑️',
      download: '📥',
    };
    return icons[type] || '📢';
  };

  // 显示通知时清除新通知标记
  const handleNotificationClick = (id: string, read: boolean) => {
    if (!read) {
      markAsRead(id);
    }
    clearNewFlag(id);
  };

  return (
    <header className="h-16 bg-white/80 backdrop-blur-xl border-b border-slate-200/60 px-6 flex items-center justify-between sticky top-0 z-10">
      {/* 搜索栏 - 仅在 showSearch 为 true 时显示 */}
      {showSearch ? (
      <div className="flex-1 max-w-2xl">
        <div className="relative group flex items-center gap-2">
          <div className="relative flex-1">
            <Search className="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-400 group-focus-within:text-violet-500 transition-colors" />
            <Input
              type="text"
              placeholder="搜索图片、相册、标签..."
              value={searchQuery}
              onChange={(e) => onSearchChange(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && onSearchSubmit) {
                  onSearchSubmit();
                }
              }}
              className="pl-12 pr-4 h-11 bg-slate-50/50 border-slate-200/60 focus:bg-white focus:border-violet-300 focus:ring-violet-500/20 transition-all duration-200"
            />
          </div>
        </div>
      </div>
      ) : (
        <div className="flex-1" />
      )}

      {/* 右侧工具栏 */}
      <div className="flex items-center gap-3 ml-6">
        {/* 批量替换主图按钮 - 仅管理员可见 */}
        {onBatchReplaceMainImage && currentUser?.role === 'admin' && (
          <Button
            variant="outline"
            size="sm"
            onClick={onBatchReplaceMainImage}
            className="gap-2 bg-orange-50 border-orange-200 text-orange-700 hover:bg-orange-100"
          >
            <RefreshCw className="w-4 h-4" />
            <span>批量替换主图</span>
          </Button>
        )}

        {/* 批量操作按钮 */}
        {selectedCount > 0 && (
          <Button
            variant="outline"
            size="sm"
            onClick={onBulkAction}
            className="gap-2 bg-violet-50 border-violet-200 text-violet-700 hover:bg-violet-100"
          >
            <CheckSquare className="w-4 h-4" />
            <span>{selectedCount} 已选择</span>
          </Button>
        )}

        {/* 视图切换 */}
        <div className="flex items-center bg-slate-100 rounded-lg p-1">
          <button
            onClick={() => onViewModeChange('grid')}
            className={cn(
              'p-2 rounded-md transition-all duration-200',
              viewMode === 'grid'
                ? 'bg-white shadow-sm text-violet-600'
                : 'text-slate-500 hover:text-slate-700'
            )}
            title="网格视图"
          >
            <Grid3x3 className="w-4 h-4" />
          </button>
          <button
            onClick={() => onViewModeChange('masonry')}
            className={cn(
              'p-2 rounded-md transition-all duration-200',
              viewMode === 'masonry'
                ? 'bg-white shadow-sm text-violet-600'
                : 'text-slate-500 hover:text-slate-700'
            )}
            title="瀑布流视图"
          >
            <LayoutGrid className="w-4 h-4" />
          </button>
          <button
            onClick={() => onViewModeChange('list')}
            className={cn(
              'p-2 rounded-md transition-all duration-200',
              viewMode === 'list'
                ? 'bg-white shadow-sm text-violet-600'
                : 'text-slate-500 hover:text-slate-700'
            )}
            title="列表视图"
          >
            <List className="w-4 h-4" />
          </button>
        </div>

        {/* 筛选按钮 */}
        <Button 
          variant="ghost" 
          size="sm" 
          className="gap-2 text-slate-600 hover:text-slate-800"
          onClick={onFilterClick}
        >
          <SlidersHorizontal className="w-4 h-4" />
          <span className="hidden sm:inline">筛选</span>
        </Button>

        {/* Excel批量上传按钮 - 仅管理员可见 */}
        {currentUser?.role === 'admin' && (
        <Button 
          variant="ghost" 
          size="sm" 
          className="gap-2 text-violet-600 hover:text-violet-700 hover:bg-violet-50"
          onClick={onExcelUploadClick}
          title="通过Excel批量导入知识"
        >
          <FileSpreadsheet className="w-4 h-4" />
          <span className="hidden sm:inline">Excel导入</span>
        </Button>
        )}

        {/* 批量导出按钮 - 仅管理员可见 */}
        {currentUser?.role === 'admin' && (
        <Button 
          variant="ghost" 
          size="sm" 
          className={cn(
            "gap-2 hover:bg-violet-50",
            hasAlbums 
              ? "text-violet-600 hover:text-violet-700" 
              : "text-slate-400 cursor-not-allowed"
          )}
          onClick={onExportClick}
          disabled={!hasAlbums}
          title={hasAlbums ? "批量导出分类知识" : "暂无分类可导出"}
        >
          <Download className="w-4 h-4" />
          <span className="hidden sm:inline">批量导出</span>
        </Button>
        )}

        {/* 盈云AI入口 */}
        <a
          href="https://www.yingyunai.com/login?redirect=/generate/text-to-image/new"
          target="_blank"
          rel="noopener noreferrer"
          className={cn(
            "inline-flex items-center gap-2 px-4 py-2 rounded-lg",
            "bg-gradient-to-r from-violet-500 to-purple-600",
            "text-white text-sm font-medium",
            "hover:from-violet-600 hover:to-purple-700",
            "shadow-md shadow-purple-500/20 hover:shadow-lg hover:shadow-purple-500/30",
            "transition-all duration-200"
          )}
        >
          <Sparkles className="w-4 h-4" />
          <span>盈云AI</span>
        </a>

        {/* 通知 */}
        <div className="relative">
          <button
            onClick={() => setShowNotifications(!showNotifications)}
            className="relative p-2 text-slate-500 hover:text-slate-700 hover:bg-slate-100 rounded-lg transition-colors"
          >
            <Bell className={cn("w-5 h-5", unreadCount > 0 && "animate-bell-shake")} />
            {unreadCount > 0 && (
              <span className="absolute top-0 right-0 min-w-[18px] h-[18px] px-1 bg-red-500 rounded-full text-white text-xs flex items-center justify-center unread-badge">
                {unreadCount > 99 ? '99+' : unreadCount}
              </span>
            )}
          </button>

          {showNotifications && (
            <div className="absolute right-0 top-12 w-96 bg-white rounded-xl shadow-2xl border border-slate-200/60 overflow-hidden animate-in fade-in slide-in-from-top-2 duration-200">
              <div className="p-4 border-b border-slate-100 flex items-center justify-between">
                <h3 className="font-semibold text-slate-800">通知</h3>
                <div className="flex items-center gap-2">
                  {unreadCount > 0 && (
                    <button
                      onClick={markAllAsRead}
                      className="text-sm text-violet-600 hover:text-violet-700"
                    >
                      全部已读
                    </button>
                  )}
                  <button
                    onClick={() => setShowNotifications(false)}
                    className="p-1 text-slate-400 hover:text-slate-600 hover:bg-slate-100 rounded-lg transition-colors"
                  >
                    <X className="w-4 h-4" />
                  </button>
                </div>
              </div>
              <div className="max-h-96 overflow-y-auto">
                {notifications.length === 0 ? (
                  <div className="p-8 text-center">
                    <Bell className="w-12 h-12 text-slate-200 mx-auto mb-3" />
                    <p className="text-slate-500">暂无通知</p>
                  </div>
                ) : (
                  notifications.slice(0, 5).map((notification) => (
                    <div
                      key={notification.id}
                      onClick={() => handleNotificationClick(notification.id, notification.read)}
                      className={cn(
                        'p-4 border-b border-slate-50 hover:bg-slate-50 transition-all cursor-pointer',
                        !notification.read && 'bg-violet-50/50',
                        notification.isNew && 'animate-pulse-once'
                      )}
                    >
                      <div className="flex gap-3">
                        <span className="text-xl">{getNotificationIcon(notification.type)}</span>
                        <div className="flex-1 min-w-0">
                          <p className="text-sm font-medium text-slate-800">{notification.title}</p>
                          <p className="text-sm text-slate-600 truncate">{notification.message}</p>
                          <span className="text-xs text-slate-400 mt-1">{formatTime(notification.createdAt)}</span>
                        </div>
                        {!notification.read && (
                          <span className="w-2 h-2 bg-violet-500 rounded-full mt-2 animate-pulse" />
                        )}
                      </div>
                    </div>
                  ))
                )}
              </div>
              <div className="p-3 bg-slate-50/50 border-t border-slate-100">
                <button 
                  onClick={() => {
                    setShowNotifications(false);
                    router.push('/notifications');
                  }}
                  className="w-full text-sm text-violet-600 hover:text-violet-700 font-medium"
                >
                  查看全部通知
                </button>
              </div>
            </div>
          )}
        </div>

        {/* 主题切换 */}
        <button
          onClick={() => setIsDark(!isDark)}
          className="p-2 text-slate-500 hover:text-slate-700 hover:bg-slate-100 rounded-lg transition-colors"
        >
          {isDark ? <Sun className="w-5 h-5" /> : <Moon className="w-5 h-5" />}
        </button>

        {/* 用户菜单 */}
        <div className="relative">
          <button 
            onClick={() => setShowUserMenu(!showUserMenu)}
            className="flex items-center gap-2 pl-3 pr-2 py-1.5 rounded-lg hover:bg-slate-100 transition-colors group"
          >
            <div className="text-right hidden sm:block">
              <p className="text-sm font-medium text-slate-700">{currentUser?.username || '未登录'}</p>
              <p className="text-xs text-slate-400 flex items-center gap-1">
                {currentUser?.role === 'admin' && <Shield className="w-3 h-3 text-violet-500" />}
                {currentUser?.role === 'admin' ? '管理员' : '普通用户'}
              </p>
            </div>
            <div className="relative">
              <div className={cn(
                "w-9 h-9 rounded-lg flex items-center justify-center shadow-lg transition-shadow",
                currentUser?.role === 'admin'
                  ? "bg-gradient-to-br from-violet-500 to-purple-600 shadow-purple-500/20 group-hover:shadow-purple-500/30"
                  : "bg-gradient-to-br from-slate-400 to-slate-500 shadow-slate-500/20 group-hover:shadow-slate-500/30"
              )}>
                <User className="w-5 h-5 text-white" />
              </div>
              <div className="absolute -bottom-0.5 -right-0.5 w-3 h-3 bg-green-400 rounded-full border-2 border-white" />
            </div>
            <ChevronDown className={cn("w-4 h-4 text-slate-400 transition-transform", showUserMenu && "rotate-180")} />
          </button>

          {/* 用户下拉菜单 */}
          {showUserMenu && (
            <div className="absolute right-0 top-14 w-64 bg-white rounded-xl shadow-2xl border border-slate-200/60 overflow-hidden animate-in fade-in slide-in-from-top-2 duration-200">
              <div className="p-4 border-b border-slate-100 bg-gradient-to-r from-violet-50 to-purple-50">
                <div className="flex items-center gap-3">
                  <div className={cn(
                    "w-12 h-12 rounded-xl flex items-center justify-center shadow-lg",
                    currentUser?.role === 'admin'
                      ? "bg-gradient-to-br from-violet-500 to-purple-600 shadow-purple-500/20"
                      : "bg-gradient-to-br from-slate-400 to-slate-500 shadow-slate-500/20"
                  )}>
                    <User className="w-6 h-6 text-white" />
                  </div>
                  <div>
                    <p className="font-semibold text-slate-800">{currentUser?.username}</p>
                    <p className="text-sm text-slate-500">{currentUser?.email}</p>
                    {currentUser?.role === 'admin' && (
                      <span className="inline-flex items-center gap-1 px-2 py-0.5 mt-1 text-xs bg-violet-100 text-violet-700 rounded-full">
                        <Shield className="w-3 h-3" />
                        管理员
                      </span>
                    )}
                  </div>
                </div>
              </div>
              
              <div className="p-2">
                {currentUser?.role === 'admin' && (
                  <button 
                    onClick={() => {
                      setShowUserMenu(false);
                      router.push('/user-settings');
                    }}
                    className="w-full flex items-center gap-3 px-3 py-2.5 rounded-lg hover:bg-slate-50 transition-colors text-left"
                  >
                    <UserCog className="w-5 h-5 text-slate-500" />
                    <span className="text-sm text-slate-700">用户管理</span>
                  </button>
                )}
                <button 
                  onClick={() => {
                    setShowUserMenu(false);
                    router.push('/settings');
                  }}
                  className="w-full flex items-center gap-3 px-3 py-2.5 rounded-lg hover:bg-slate-50 transition-colors text-left"
                >
                  <Settings className="w-5 h-5 text-slate-500" />
                  <span className="text-sm text-slate-700">账户设置</span>
                </button>
              </div>

              <div className="p-2 border-t border-slate-100">
                <button 
                  onClick={() => {
                    setShowUserMenu(false);
                    onLogout?.();
                  }}
                  className="w-full flex items-center gap-3 px-3 py-2.5 rounded-lg hover:bg-red-50 transition-colors text-left group"
                >
                  <LogOut className="w-5 h-5 text-slate-500 group-hover:text-red-500" />
                  <span className="text-sm text-slate-700 group-hover:text-red-600">退出登录</span>
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </header>
  );
}
